package br.com.tonus;

import br.com.tonus.support.PostgresContainer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O seed de desenvolvimento (`scripts/seed-dev.sql`) continua funcionando.
 *
 * Ele é a única porta de entrada do ambiente local — `POST /users` exige um
 * ADMIN já autenticado, então sem o seed ninguém faz o primeiro login. E é SQL
 * cru: nada no build o obrigaria a acompanhar uma migration que mudasse
 * `users`. Sem este teste, a quebra apareceria para quem clonou o repo, no pior
 * momento possível.
 *
 * Roda o arquivo pelo mesmo caminho documentado no README — `psql -f`, dentro
 * do container — em vez de despejar o conteúdo por JDBC. O `\set ON_ERROR_STOP`
 * do cabeçalho é meta-comando de psql: por JDBC ele nem seria interpretado, e o
 * teste passaria a exercitar um arquivo diferente do que o desenvolvedor roda.
 * O código de saída do psql faz parte da asserção por isso mesmo.
 *
 * Cada teste trabalha num banco próprio, criado no container compartilhado da
 * suíte e migrado do zero pelo Flyway. Não é preciosismo: o script aplica
 * `UPDATE users SET password = ...` sem WHERE, e apontá-lo para o banco dos
 * outros testes trocaria a senha de todos os usuários que eles criaram — a
 * suíte inteira passaria a falhar no login, longe daqui.
 */
class SeedDevScriptIT {

    private static final String SEED_PASSWORD = "tonus123";
    private static final Path SCRIPT = Path.of("scripts", "seed-dev.sql");

    private static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeAll
    static void copyScriptIntoContainer() {
        // O diretório de trabalho do failsafe é o basedir do módulo (backend/).
        assertThat(SCRIPT)
                .as("o seed precisa estar em backend/%s — o teste roda a partir de backend/", SCRIPT)
                .exists();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(SCRIPT.toAbsolutePath()), "/tmp/seed-dev.sql");
    }

    @Test
    void givesEveryActivePersonALoginWithTheStandardPassword() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Professora Demo", true);
        insertStudent(db, "Aluna Demo", teacher, true);

        runSeed(db);

        Map<String, Row> users = users(db);
        assertThat(users.keySet())
                .containsExactlyInAnyOrder("admin@tonus.com", "professora@tonus.com", "aluna@tonus.com");
        assertThat(users.get("professora@tonus.com").role()).isEqualTo("TEACHER");
        assertThat(users.get("aluna@tonus.com").role()).isEqualTo("STUDENT");
        assertThat(users.values()).allSatisfy(user -> {
            assertThat(encoder.matches(SEED_PASSWORD, user.password()))
                    .as("a senha de %s tem que ser a padrão", user.login())
                    .isTrue();
            assertThat(user.active()).isTrue();
        });
    }

    /**
     * O ADMIN é o único que o script não deriva de ninguém: não está preso a
     * professor nem a aluno, e é ele que destrava o `POST /users`.
     */
    @Test
    void createsTheAdminOnAnEmptyDatabaseButDoesNotDuplicateIt() throws Exception {
        String db = freshDatabase();

        runSeed(db);
        assertThat(users(db).keySet()).containsExactly("admin@tonus.com");

        runSeed(db);
        assertThat(users(db).keySet()).containsExactly("admin@tonus.com");
    }

    @Test
    void resetsThePasswordOfUsersThatAlreadyExisted() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Professora Antiga", true);
        insertUser(db, "professora@tonus.com", "$2y$10$outrahashqualquerquenaoehadopadraoxxxxxxxxxxxxxxxxxxxxxxxxx",
                "TEACHER", null, teacher, true);

        runSeed(db);

        Map<String, Row> users = users(db);
        assertThat(users.keySet()).containsExactlyInAnyOrder("admin@tonus.com", "professora@tonus.com");
        assertThat(encoder.matches(SEED_PASSWORD, users.get("professora@tonus.com").password())).isTrue();
    }

    /**
     * Rodar de novo depois de cadastrar gente nova é o uso previsto — o script
     * é a ferramenta de dar acesso a quem entrou depois.
     */
    @Test
    void isIdempotentAndPicksUpPeopleRegisteredLater() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Professora Demo", true);

        runSeed(db);
        assertThat(users(db).keySet()).containsExactlyInAnyOrder("admin@tonus.com", "professora@tonus.com");

        runSeed(db);
        assertThat(users(db).keySet()).containsExactlyInAnyOrder("admin@tonus.com", "professora@tonus.com");

        insertStudent(db, "Beatriz Lima", teacher, true);
        runSeed(db);
        assertThat(users(db).keySet())
                .containsExactlyInAnyOrder("admin@tonus.com", "professora@tonus.com", "beatriz@tonus.com");
    }

    /**
     * Quem saiu do estúdio perde o acesso por evento, e o seed não é uma porta
     * dos fundos para ele voltar.
     */
    @Test
    void skipsPeopleDeactivatedInTheRegistration() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Professora Que Saiu", false);
        insertStudent(db, "Aluno Que Saiu", teacher, false);
        long ativa = insertTeacher(db, "Professora Que Ficou", true);
        insertStudent(db, "Aluna Que Ficou", ativa, true);

        runSeed(db);

        assertThat(users(db).keySet()).containsExactlyInAnyOrder(
                "admin@tonus.com", "professora@tonus.com", "aluna@tonus.com");
    }

    /**
     * Revogar é automático, reconceder é manual — em produção. Aqui a promessa
     * do script é que todo mundo que está no estúdio entra, então o login de
     * quem voltou ao cadastro volta junto; o de quem continua fora, não.
     */
    @Test
    void reactivatesRevokedAccessOnlyForPeopleStillActiveInTheRegistration() throws Exception {
        String db = freshDatabase();
        long back = insertTeacher(db, "Professora Que Voltou", true);
        long gone = insertTeacher(db, "Professora Que Nao Voltou", false);
        insertUser(db, "voltou@tonus.com", "$2y$10$x", "TEACHER", null, back, false);
        insertUser(db, "naovoltou@tonus.com", "$2y$10$x", "TEACHER", null, gone, false);

        runSeed(db);

        Map<String, Row> users = users(db);
        assertThat(users.get("voltou@tonus.com").active()).isTrue();
        assertThat(users.get("naovoltou@tonus.com").active()).isFalse();
    }

    /**
     * Nome não é único no cadastro, login é. Duas Marias não podem derrubar o
     * seed inteiro na UNIQUE.
     */
    @Test
    void disambiguatesLoginsDerivedFromTheSameFirstName() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Ana Silva", true);
        insertStudent(db, "Maria Souza", teacher, true);
        // Acento normalizado: "María" e "Maria" disputam o mesmo login.
        long second = insertStudent(db, "María Costa", teacher, true);

        runSeed(db);

        assertThat(users(db).keySet()).containsExactlyInAnyOrder(
                "admin@tonus.com",
                "ana@tonus.com",
                "maria@tonus.com",
                "maria" + second + "@tonus.com");
    }

    /**
     * O Flyway roda quando a aplicação sobe, não quando o Postgres sobe. Quem
     * rodar o seed logo depois do `docker compose up -d db` tem que receber uma
     * instrução, não um "relation does not exist".
     */
    @Test
    void failsWithAnActionableMessageWhenTheSchemaIsNotThere() throws Exception {
        String db = "seed_sem_schema_" + suffix();
        execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + db);

        Container.ExecResult result = psql(db);

        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr())
                .contains("as tabelas ainda não existem")
                .contains("./mvnw spring-boot:run");
    }

    // --- infraestrutura do teste ---

    /** Banco vazio e migrado do zero, isolado dos outros testes da suíte. */
    private String freshDatabase() throws Exception {
        String name = "seed_dev_" + suffix();
        execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + name);
        Flyway.configure()
                .dataSource(urlOf(name), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return name;
    }

    private void runSeed(String database) throws Exception {
        Container.ExecResult result = psql(database);
        assertThat(result.getExitCode())
                .as("psql falhou: %s", result.getStderr())
                .isZero();
    }

    private Container.ExecResult psql(String database) throws Exception {
        return POSTGRES.execInContainer(
                "psql", "-U", POSTGRES.getUsername(), "-d", database, "-f", "/tmp/seed-dev.sql");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String urlOf(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), database);
    }

    private Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(urlOf(database), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String database, String sql) throws SQLException {
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long insertTeacher(String database, String name, boolean active) throws SQLException {
        return insertPerson(database,
                "INSERT INTO teacher (name, phone, active, created_at, updated_at) "
                        + "VALUES ('%s', '+5511999990000', %b, localtimestamp, localtimestamp) RETURNING id"
                                .formatted(name, active));
    }

    private long insertStudent(String database, String name, long teacherId, boolean active) throws SQLException {
        return insertPerson(database,
                "INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at) "
                        + "VALUES ('%s', '+5511988880000', %d, %b, localtimestamp, localtimestamp) RETURNING id"
                                .formatted(name, teacherId, active));
    }

    private long insertPerson(String database, String sql) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void insertUser(String database, String login, String password, String role,
            Long studentId, Long teacherId, boolean active) throws SQLException {
        execute(database,
                "INSERT INTO users (login, password, role, student_id, teacher_id, active, created_at, updated_at) "
                        + "VALUES ('%s', '%s', '%s', %s, %s, %b, localtimestamp, localtimestamp)".formatted(
                                login, password, role,
                                studentId == null ? "NULL" : studentId,
                                teacherId == null ? "NULL" : teacherId,
                                active));
    }

    private Map<String, Row> users(String database) throws SQLException {
        Map<String, Row> byLogin = new LinkedHashMap<>();
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT login, password, role, active FROM users ORDER BY login")) {
            while (rs.next()) {
                byLogin.put(rs.getString(1),
                        new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
            }
        }
        return byLogin;
    }

    private record Row(String login, String password, String role, boolean active) {
    }
}
