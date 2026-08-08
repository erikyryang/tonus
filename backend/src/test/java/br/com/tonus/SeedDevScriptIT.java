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
 * The development seed (`scripts/seed-dev.sql`) keeps working.
 *
 * It is the only entry point of the local environment — `POST /users` requires
 * an already-authenticated ADMIN, so without the seed nobody makes the first
 * login. And it is raw SQL: nothing in the build would force it to follow a
 * migration that changed `users`. Without this test, the breakage would appear
 * to whoever cloned the repo, at the worst possible moment.
 *
 * Runs the file through the same path documented in the README — `psql -f`,
 * inside the container — instead of dumping the content over JDBC. The
 * `\set ON_ERROR_STOP` in the header is a psql meta-command: over JDBC it would
 * not even be interpreted, and the test would end up exercising a different
 * file than the one the developer runs. The psql exit code is part of the
 * assertion for that very reason.
 *
 * Each test works on its own database, created in the container shared by the
 * suite and migrated from scratch by Flyway. It is not fussiness: the script
 * applies `UPDATE users SET password = ...` without WHERE, and pointing it at
 * the database of the other tests would change the password of all the users
 * they created — the whole suite would start failing at login, far from here.
 */
class SeedDevScriptIT {

    private static final String SEED_PASSWORD = "tonus123";
    private static final Path SCRIPT = Path.of("scripts", "seed-dev.sql");

    private static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeAll
    static void copyScriptIntoContainer() {
        // The failsafe working directory is the module's basedir (backend/).
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
     * The case of someone who just cloned the repo: a zeroed database must
     * become a usable environment in the three roles. The ADMIN is not derived
     * from anyone — it is the one that unlocks `POST /users` — and TEACHER and
     * STUDENT would have nobody to come from without a demo registration.
     */
    @Test
    void buildsAUsableEnvironmentWithAllThreeRolesOnAnEmptyDatabase() throws Exception {
        String db = freshDatabase();

        runSeed(db);

        Map<String, Row> users = users(db);
        assertThat(users.keySet()).containsExactlyInAnyOrder(
                "admin@tonus.com",
                "marina@tonus.com", "rafael@tonus.com",
                "beatriz@tonus.com", "carla@tonus.com", "diego@tonus.com", "helena@tonus.com");
        assertThat(users.values()).extracting(Row::role)
                .contains("ADMIN", "TEACHER", "STUDENT");
        assertThat(users.values()).allSatisfy(user ->
                assertThat(encoder.matches(SEED_PASSWORD, user.password())).isTrue());
        // Every demo student has a teacher: the link is NOT NULL, and the
        // seed cannot be the route that breaks a registration business rule.
        assertThat(count(db, "SELECT count(*) FROM student WHERE teacher_id IS NULL")).isZero();
    }

    @Test
    void doesNotDuplicateTheDemoRegistrationOnRerun() throws Exception {
        String db = freshDatabase();

        runSeed(db);
        runSeed(db);

        assertThat(count(db, "SELECT count(*) FROM teacher")).isEqualTo(2);
        // Five students: four active with login and the inactive one, which
        // exists so the status filter has something to show and gets no access.
        assertThat(count(db, "SELECT count(*) FROM student")).isEqualTo(5);
        assertThat(users(db)).hasSize(7);
    }

    /**
     * A zeroed database must become an environment with full screens, not just
     * people: an empty screen exercises no interface, and exercising it is
     * what the local environment exists for.
     */
    @Test
    void seedsMovementSoEveryScreenHasContent() throws Exception {
        String db = freshDatabase();

        runSeed(db);

        assertThat(count(db, "SELECT count(*) FROM default_schedule")).isEqualTo(4);
        assertThat(count(db, "SELECT count(*) FROM schedule_exception WHERE type = 'CANCELED'")).isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM schedule_exception WHERE type = 'RESCHEDULED'")).isEqualTo(1);
        // The two announcement types: without the pair, the difference between
        // suspending and notifying does not appear on any screen.
        assertThat(count(db, "SELECT count(*) FROM announcement WHERE suspends_classes")).isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM announcement WHERE NOT suspends_classes")).isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM plan WHERE active")).isEqualTo(3);
        assertThat(count(db, "SELECT count(*) FROM plan WHERE NOT active")).isEqualTo(1);
        // Someone with two plans, so the multi-plan discount leaves zero on
        // the configuration screen and applies to a real invoice.
        assertThat(count(db, """
                SELECT count(*) FROM (
                    SELECT student_id FROM plan_enrollment WHERE active
                     GROUP BY student_id HAVING count(*) > 1
                ) AS multi
                """)).isEqualTo(1);
        assertThat(count(db, "SELECT multi_plan_discount_percent FROM billing_settings")).isEqualTo(10);

        // One invoice per status: that is what gives the /invoices filter and
        // the "Atrasado" badge of /students something to show.
        assertThat(count(db, "SELECT count(*) FROM invoice WHERE status = 'PAID'")).isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM invoice WHERE status = 'OPEN'")).isEqualTo(2);
        assertThat(count(db, "SELECT count(*) FROM invoice WHERE status = 'OVERDUE'")).isEqualTo(1);
        // A paid invoice without a payment entry is a state the system does
        // not produce on its own.
        assertThat(count(db, """
                SELECT count(*) FROM invoice i
                 WHERE i.status = 'PAID'
                   AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.invoice_id = i.id)
                """)).isZero();
    }

    /** The schedule only exists Monday to Friday — including when the seed runs on Saturday. */
    @Test
    void anchorsScheduleExceptionsOnAWeekday() throws Exception {
        String db = freshDatabase();

        runSeed(db);

        assertThat(count(db, """
                SELECT count(*) FROM schedule_exception
                 WHERE extract(isodow FROM date) > 5
                """)).isZero();
    }

    @Test
    void doesNotDuplicateMovementOnRerun() throws Exception {
        String db = freshDatabase();

        runSeed(db);
        runSeed(db);

        assertThat(count(db, "SELECT count(*) FROM default_schedule")).isEqualTo(4);
        assertThat(count(db, "SELECT count(*) FROM schedule_exception")).isEqualTo(2);
        assertThat(count(db, "SELECT count(*) FROM announcement")).isEqualTo(2);
        assertThat(count(db, "SELECT count(*) FROM plan")).isEqualTo(4);
        assertThat(count(db, "SELECT count(*) FROM plan_enrollment")).isEqualTo(4);
        assertThat(count(db, "SELECT count(*) FROM invoice")).isEqualTo(4);
        assertThat(count(db, "SELECT count(*) FROM payment")).isEqualTo(1);
    }

    /**
     * On a database in use the script does not invent movement, for the same
     * reason it does not invent people: it would dirty the environment of
     * whoever is working.
     */
    @Test
    void doesNotSeedMovementWhenTheRegistrationIsSomeoneElses() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Solange Vieira", true);
        insertStudent(db, "Aluna De Verdade", teacher, true);

        runSeed(db);

        assertThat(count(db, "SELECT count(*) FROM default_schedule")).isZero();
        assertThat(count(db, "SELECT count(*) FROM plan")).isZero();
        assertThat(count(db, "SELECT count(*) FROM invoice")).isZero();
    }

    /**
     * Inventing people in a database that already has registrations would
     * dirty the work of whoever is using the environment. The trigger is the
     * empty registration, not the absence of users.
     */
    @Test
    void doesNotInventPeopleWhenTheRegistrationAlreadyHasSomeone() throws Exception {
        String db = freshDatabase();
        insertTeacher(db, "Solange Vieira", true);

        runSeed(db);

        assertThat(count(db, "SELECT count(*) FROM teacher")).isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM student")).isZero();
        assertThat(users(db).keySet()).containsExactlyInAnyOrder("admin@tonus.com", "solange@tonus.com");
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
     * Rerunning after registering new people is the intended use — the script
     * is the tool for granting access to those who joined later.
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
     * Whoever left the studio loses access through an event, and the seed is
     * not a back door for them to come back.
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
     * Revoking is automatic, regranting is manual — in production. Here the
     * promise of the script is that everyone in the studio gets in, so the
     * login of whoever came back to the registration comes back along; that of
     * whoever is still out, not.
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
     * Name is not unique in the registration, login is. Two Marias cannot
     * bring down the whole seed on the UNIQUE constraint.
     */
    @Test
    void disambiguatesLoginsDerivedFromTheSameFirstName() throws Exception {
        String db = freshDatabase();
        long teacher = insertTeacher(db, "Ana Silva", true);
        insertStudent(db, "Maria Souza", teacher, true);
        // Normalized accent: "María" and "Maria" compete for the same login.
        long second = insertStudent(db, "María Costa", teacher, true);

        runSeed(db);

        assertThat(users(db).keySet()).containsExactlyInAnyOrder(
                "admin@tonus.com",
                "ana@tonus.com",
                "maria@tonus.com",
                "maria" + second + "@tonus.com");
    }

    /**
     * Flyway runs when the application boots, not when Postgres boots. Whoever
     * runs the seed right after `docker compose up -d db` should get an
     * instruction, not a "relation does not exist".
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

    // --- test infrastructure ---

    /** Empty database migrated from scratch, isolated from the other tests of the suite. */
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

    private long count(String database, String sql) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
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
