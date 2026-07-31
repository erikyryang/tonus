-- Seed de DESENVOLVIMENTO: dá a todo mundo do banco um login com a mesma
-- senha padrão (`tonus123`).
--
-- Não é migration de propósito. Migration roda em todo ambiente, e uma senha
-- conhecida publicada em produção é conta aberta para qualquer um — este
-- arquivo mora fora de db/migration justamente para que o Flyway não o aplique
-- sozinho. Rodar à mão, e só no banco local.
--
--   cd backend && docker compose exec -T db psql -U tonus -d tonus < scripts/seed-dev.sql
--
-- É idempotente: reexecutar reaplica a senha padrão e cria login apenas para
-- quem ainda não tem. Rode de novo depois de cadastrar gente nova pela API, ou
-- depois de recriar o volume `tonus-db-data`.
--
-- Num banco zerado ele também monta o mínimo para o ambiente existir: o ADMIN,
-- que não é derivado de ninguém, e um cadastro de demonstração com dois
-- professores e quatro alunos — sem isso os papéis TEACHER e STUDENT não teriam
-- de quem sair, e quem acabou de clonar o repo entraria só como ADMIN. Assim
-- que houver alguém em teacher/student o script para de inventar gente e passa
-- a só derivar login de quem já está lá.
--
-- Quem foi desativado no cadastro fica de fora: o acesso cai por evento quando
-- a pessoa sai do estúdio, e ressuscitá-lo aqui contradiria essa regra. Quem
-- está ativo no cadastro mas com o login revogado é reativado — a reconcessão
-- é manual em produção, mas aqui a promessa do script é justamente que todo
-- mundo que está no estúdio entra com a senha padrão.
--
-- SeedDevScriptIT roda este arquivo por psql contra um Postgres de verdade a
-- cada build: se uma migration futura mudar a tabela `users` por baixo, quebra
-- lá e não no primeiro login de quem clonou o repo.

\set ON_ERROR_STOP on

DO $seed$
DECLARE
    -- bcrypt de 'tonus123', gerado com: htpasswd -bnBC 10 "" tonus123
    default_hash CONSTANT text := '$2y$10$y3WnJwQPQ8v4J12NyAP.J.Uli27vdxaxom2rOtudHobYRzl9KDjMy';
    person RECORD;
    base text;
    candidate text;
    attempt int;
    created int := 0;
    reactivated int;
BEGIN
    -- O Flyway roda quando a aplicação sobe, não quando o Postgres sobe. Sem
    -- este aviso, quem rodasse o seed logo depois do `docker compose up -d db`
    -- receberia um "relation does not exist" sem pista do que fazer.
    IF to_regclass('public.users') IS NULL THEN
        RAISE EXCEPTION 'as tabelas ainda não existem neste banco'
            USING HINT = 'suba a aplicação uma vez (cd backend && ./mvnw spring-boot:run) '
                         || 'para o Flyway criar o schema, e rode o seed depois';
    END IF;

    -- Cadastro vazio é o clone recém-feito. Aí o script só teria de onde tirar
    -- o ADMIN — os outros dois papéis ele deriva de quem está em teacher e
    -- student, e não há ninguém —, e as telas /me de professor e de aluno
    -- ficariam inalcançáveis justamente para quem está montando o ambiente
    -- pela primeira vez. Num banco que já tem gente ele não inventa ninguém:
    -- inventar seria sujar o cadastro de quem está trabalhando.
    IF NOT EXISTS (SELECT 1 FROM teacher) AND NOT EXISTS (SELECT 1 FROM student) THEN
        INSERT INTO teacher (name, phone, active, created_at, updated_at) VALUES
            ('Marina Duarte',  '+5511999990001', TRUE, localtimestamp, localtimestamp),
            ('Rafael Moreira', '+5511999990002', TRUE, localtimestamp, localtimestamp);

        -- Dois alunos por professor: a agenda do dia agrupa por professor, e
        -- com um só não dá para ver o agrupamento fazendo o que promete.
        INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
        SELECT demo.name, demo.phone, t.id, TRUE, localtimestamp, localtimestamp
          FROM (VALUES
                    ('Beatriz Lima',  '+5511988880001', 'Marina Duarte'),
                    ('Carla Nunes',   '+5511988880002', 'Marina Duarte'),
                    ('Diego Alves',   '+5511988880003', 'Rafael Moreira'),
                    ('Helena Castro', '+5511988880004', 'Rafael Moreira')
               ) AS demo(name, phone, teacher_name)
          JOIN teacher t ON t.name = demo.teacher_name;

        RAISE NOTICE 'cadastro vazio: criados 2 professores e 4 alunos de demonstração';
    END IF;

    UPDATE users SET password = default_hash, updated_at = localtimestamp;
    RAISE NOTICE 'senha padrão aplicada a % usuário(s) existente(s)', (SELECT count(*) FROM users);

    WITH revived AS (
        UPDATE users u
           SET active = TRUE, updated_at = localtimestamp
         WHERE NOT u.active
           AND (u.role = 'ADMIN'
                OR EXISTS (SELECT 1 FROM teacher t WHERE t.id = u.teacher_id AND t.active)
                OR EXISTS (SELECT 1 FROM student s WHERE s.id = u.student_id AND s.active))
        RETURNING 1
    )
    SELECT count(*) INTO reactivated FROM revived;
    IF reactivated > 0 THEN
        RAISE NOTICE 'acesso reativado para % usuário(s) que seguem ativos no cadastro', reactivated;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN') THEN
        INSERT INTO users (login, password, role, active, created_at, updated_at)
        VALUES ('admin@tonus.com', default_hash, 'ADMIN', TRUE, localtimestamp, localtimestamp);
        RAISE NOTICE 'criado ADMIN admin@tonus.com';
        created := created + 1;
    END IF;

    FOR person IN
        SELECT 'TEACHER'::text AS role, t.id, t.name
          FROM teacher t
         WHERE t.active
           AND NOT EXISTS (SELECT 1 FROM users u WHERE u.teacher_id = t.id)
        UNION ALL
        SELECT 'STUDENT'::text, s.id, s.name
          FROM student s
         WHERE s.active
           AND NOT EXISTS (SELECT 1 FROM users u WHERE u.student_id = s.id)
         ORDER BY 1, 2
    LOOP
        base := regexp_replace(
            lower(translate(split_part(person.name, ' ', 1),
                            'áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ',
                            'aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC')),
            '[^a-z0-9]', '', 'g');
        -- Nome que não sobrevive à normalização ainda precisa de login: sem
        -- isso o candidato viraria '@tonus.com' e a segunda pessoa nessa
        -- situação colidiria com a primeira.
        IF base = '' THEN
            base := lower(person.role);
        END IF;

        -- Nome não é único no cadastro e login é. O id desempata, e o laço
        -- cobre o caso de o próprio desempate já estar ocupado — por um login
        -- criado à mão pela API, por exemplo. Sem ele a UNIQUE derrubaria o
        -- seed inteiro por causa de duas Marias.
        candidate := base || '@tonus.com';
        IF EXISTS (SELECT 1 FROM users WHERE login = candidate) THEN
            candidate := base || person.id || '@tonus.com';
        END IF;
        attempt := 0;
        WHILE EXISTS (SELECT 1 FROM users WHERE login = candidate) LOOP
            attempt := attempt + 1;
            candidate := base || person.id || '-' || attempt || '@tonus.com';
        END LOOP;

        INSERT INTO users (login, password, role, student_id, teacher_id, active, created_at, updated_at)
        VALUES (candidate,
                default_hash,
                person.role,
                CASE WHEN person.role = 'STUDENT' THEN person.id END,
                CASE WHEN person.role = 'TEACHER' THEN person.id END,
                TRUE,
                localtimestamp,
                localtimestamp);
        RAISE NOTICE 'criado % % (%)', person.role, candidate, person.name;
        created := created + 1;
    END LOOP;

    RAISE NOTICE '% usuário(s) criado(s); senha de todos: tonus123', created;
END;
$seed$;

-- O que ficou no banco, para conferir sem abrir outro psql.
SELECT u.id,
       u.login,
       u.role,
       coalesce(t.name, s.name, '-') AS pessoa,
       u.active
  FROM users u
  LEFT JOIN teacher t ON t.id = u.teacher_id
  LEFT JOIN student s ON s.id = u.student_id
 ORDER BY u.role, u.login;
