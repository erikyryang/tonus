-- DEVELOPMENT seed: gives everyone in the database a login with the same
-- default password (`tonus123`).
--
-- Not a migration on purpose. A migration runs in every environment, and a
-- known password published in production is an open account for anyone —
-- this file lives outside db/migration precisely so Flyway won't apply it on
-- its own. Run it by hand, and only against the local database.
--
--   cd backend && docker compose exec -T db psql -U tonus -d tonus < scripts/seed-dev.sql
--
-- It's idempotent: rerunning reapplies the default password, only creates a
-- login for whoever doesn't have one yet, and doesn't duplicate activity. Run
-- it again after registering new people through the API, or after recreating
-- the `tonus-db-data` volume.
--
-- On an empty database it also builds the whole environment: the ADMIN, who
-- isn't derived from anyone; demo registrations with two teachers and five
-- students (one of them inactive, so the status filter has something to
-- show); and the activity that makes screens start with content — schedule,
-- exceptions, announcements, plans, enrollments, and invoices in all three
-- statuses.
--
-- An empty screen doesn't exercise any UI, and exercising it is exactly why
-- the local environment exists. As soon as there's someone in teacher/student
-- the script stops inventing people, and each activity block is guarded by
-- its own table being empty: on a database already in use, it doesn't touch
-- anything.
--
-- Whoever was deactivated in the registry stays out: their access drops by
-- event when they leave the studio, and reviving it here would contradict
-- that rule. Whoever is active in the registry but has a revoked login gets
-- reactivated — regranting access is manual in production, but here the
-- script's promise is precisely that everyone in the studio gets in with the
-- default password.
--
-- SeedDevScriptIT runs this file via psql against a real Postgres on every
-- build: if a future migration changes the `users` table underneath it,
-- it breaks there, not on the first login of whoever cloned the repo.

\set ON_ERROR_STOP on

DO $seed$
DECLARE
    -- bcrypt of 'tonus123', generated with: htpasswd -bnBC 10 "" tonus123
    default_hash CONSTANT text := '$2y$10$y3WnJwQPQ8v4J12NyAP.J.Uli27vdxaxom2rOtudHobYRzl9KDjMy';
    person RECORD;
    base text;
    candidate text;
    attempt int;
    created int := 0;
    reactivated int;
BEGIN
    -- Flyway runs when the application starts, not when Postgres starts.
    -- Without this check, running the seed right after `docker compose up -d
    -- db` would produce a "relation does not exist" with no clue what to do.
    IF to_regclass('public.users') IS NULL THEN
        RAISE EXCEPTION 'as tabelas ainda não existem neste banco'
            USING HINT = 'suba a aplicação uma vez (cd backend && ./mvnw spring-boot:run) '
                         || 'para o Flyway criar o schema, e rode o seed depois';
    END IF;

    -- An empty registry means a fresh clone. In that case the script would
    -- only have the ADMIN to pull from — the other two roles are derived from
    -- whoever's in teacher and student, and there's nobody there — and the
    -- teacher's and student's /me screens would be unreachable for exactly
    -- whoever is setting up the environment for the first time. On a
    -- database that already has people, it invents no one: that would
    -- clutter the registry of whoever is actually working.
    IF NOT EXISTS (SELECT 1 FROM teacher) AND NOT EXISTS (SELECT 1 FROM student) THEN
        INSERT INTO teacher (name, phone, active, created_at, updated_at) VALUES
            ('Marina Duarte',  '+5511999990001', TRUE, localtimestamp, localtimestamp),
            ('Rafael Moreira', '+5511999990002', TRUE, localtimestamp, localtimestamp);

        -- Two students per teacher: the day schedule groups by teacher, and
        -- with just one you can't see the grouping do what it's supposed to.
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
        -- A name that doesn't survive normalization still needs a login:
        -- without this, the candidate would become '@tonus.com' and the
        -- second person in that situation would collide with the first.
        IF base = '' THEN
            base := lower(person.role);
        END IF;

        -- Name isn't unique in the registry, and login is. The id breaks the
        -- tie, and the loop covers the case where the tiebreak itself is
        -- already taken — by a login created by hand through the API, for
        -- example. Without it, the UNIQUE constraint would take down the
        -- whole seed over two Marias.
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

-- ---------------------------------------------------------------------------
-- Demo activity: schedule, announcements, plans, and invoices.
--
-- Exists so screens start with content instead of empty — the local
-- environment is for exercising the UI, and an empty screen exercises
-- nothing. Each block is guarded by its own table being empty: rerunning
-- doesn't duplicate, and on a database that already has activity, the script
-- doesn't touch anything.
--
-- Everything anchors to CURRENT_DATE, never a literal date: a seed with a
-- fixed date rots, and an environment built in March would show a January
-- schedule.
-- ---------------------------------------------------------------------------
DO $movimento$
DECLARE
    beatriz bigint;
    carla   bigint;
    diego   bigint;
    helena  bigint;
    marina  bigint;
    admin_id bigint;
    -- Today, if it's a weekday; the following Monday, if it falls on a
    -- weekend. The schedule only exists Monday through Friday, and a seed run
    -- on Saturday would leave the day screen empty for exactly whoever came
    -- to check it.
    ref_day date := CASE WHEN extract(isodow FROM CURRENT_DATE) <= 5
                         THEN CURRENT_DATE
                         ELSE CURRENT_DATE + (8 - extract(isodow FROM CURRENT_DATE))::int
                    END;
    this_month date := date_trunc('month', CURRENT_DATE)::date;
    last_month date := (date_trunc('month', CURRENT_DATE) - interval '1 month')::date;
    plano_musculacao bigint;
    plano_pilates    bigint;
BEGIN
    SELECT id INTO beatriz FROM student WHERE name = 'Beatriz Lima'  AND active LIMIT 1;
    SELECT id INTO carla   FROM student WHERE name = 'Carla Nunes'   AND active LIMIT 1;
    SELECT id INTO diego   FROM student WHERE name = 'Diego Alves'   AND active LIMIT 1;
    SELECT id INTO helena  FROM student WHERE name = 'Helena Castro' AND active LIMIT 1;
    SELECT id INTO marina  FROM teacher WHERE name = 'Marina Duarte' AND active LIMIT 1;
    SELECT id INTO admin_id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1;

    -- Without the demo registrations there's no one to hang activity on, and
    -- inventing it for someone's real students would clutter their working
    -- environment — the same rule that applies to not inventing people.
    IF beatriz IS NULL OR carla IS NULL OR diego IS NULL OR helena IS NULL THEN
        RAISE NOTICE 'cadastro de demonstração ausente: movimento não semeado';
        RETURN;
    END IF;

    -- Inactive student: this is what gives the "Inactive" filter in listings
    -- something to show. Left with no login on purpose — whoever left the
    -- studio doesn't get in.
    IF NOT EXISTS (SELECT 1 FROM student WHERE name = 'Sofia Ramos') THEN
        INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
        VALUES ('Sofia Ramos', '+5511988880005', marina, FALSE, localtimestamp, localtimestamp);
    END IF;

    -- Schedule: two students at the same time (that's how you see the by-hour
    -- grouping do what it promises) and two at their own times.
    IF NOT EXISTS (SELECT 1 FROM default_schedule) THEN
        INSERT INTO default_schedule (student_id, time_of_day, created_at, updated_at) VALUES
            (beatriz, TIME '07:00', localtimestamp, localtimestamp),
            (carla,   TIME '07:00', localtimestamp, localtimestamp),
            (diego,   TIME '18:00', localtimestamp, localtimestamp),
            (helena,  TIME '19:00', localtimestamp, localtimestamp);
        RAISE NOTICE 'agenda: 4 horários padrão criados';
    END IF;

    -- One cancellation and one reschedule: the two states the day screen
    -- shows besides a regular class, and what the student sees in
    -- /me/schedule.
    IF NOT EXISTS (SELECT 1 FROM schedule_exception) THEN
        INSERT INTO schedule_exception (student_id, date, type, new_date, new_time, created_at, updated_at) VALUES
            (beatriz, ref_day, 'CANCELED', NULL, NULL, localtimestamp, localtimestamp),
            (diego, ref_day, 'RESCHEDULED', ref_day + 1, TIME '20:00', localtimestamp, localtimestamp);
        RAISE NOTICE 'agenda: 1 cancelamento e 1 remarcação criados';
    END IF;

    -- The two announcement types, so the difference shows up on the screen:
    -- one suspends the day, the other is just a notice and the schedule
    -- goes on.
    IF NOT EXISTS (SELECT 1 FROM announcement) THEN
        INSERT INTO announcement (date, description, suspends_classes, created_at, updated_at) VALUES
            (ref_day + 3, 'Feriado municipal', TRUE, localtimestamp, localtimestamp),
            (ref_day + 1, 'Obra na rua: entrada pelos fundos', FALSE, localtimestamp, localtimestamp);
        RAISE NOTICE 'comunicados: 1 com suspensão e 1 informativo criados';
    END IF;

    -- A catalog with one inactive plan, so the status filter has something
    -- to filter, and a discount configured, so the settings screen doesn't
    -- open at zero.
    IF NOT EXISTS (SELECT 1 FROM plan) THEN
        INSERT INTO plan (name, monthly_price, active, created_at, updated_at) VALUES
            ('Musculação',      350.00, TRUE,  localtimestamp, localtimestamp),
            ('Pilates',         270.00, TRUE,  localtimestamp, localtimestamp),
            ('Avaliação Física', 90.00, TRUE,  localtimestamp, localtimestamp),
            ('Plano Antigo',    199.00, FALSE, localtimestamp, localtimestamp);

        SELECT id INTO plano_musculacao FROM plan WHERE name = 'Musculação';
        SELECT id INTO plano_pilates    FROM plan WHERE name = 'Pilates';

        -- Beatriz with one plan, Carla with two (she's the one exercising the
        -- discount), Diego with one, Helena with none — the student with no
        -- plan is the one the job doesn't invoice, and the screen needs to
        -- show that too.
        INSERT INTO plan_enrollment (student_id, plan_id, active, created_at, updated_at) VALUES
            (beatriz, plano_musculacao, TRUE, localtimestamp, localtimestamp),
            (carla,   plano_musculacao, TRUE, localtimestamp, localtimestamp),
            (carla,   plano_pilates,    TRUE, localtimestamp, localtimestamp),
            (diego,   plano_pilates,    TRUE, localtimestamp, localtimestamp);

        UPDATE billing_settings
           SET multi_plan_discount_percent = 10.00, updated_at = localtimestamp
         WHERE id = 1 AND multi_plan_discount_percent = 0;

        RAISE NOTICE 'cobrança: 4 planos, 4 matrículas e desconto de 10%% configurados';
    END IF;

    -- One invoice per status. The OVERDUE one is what makes the students
    -- screen show the "Overdue" badge and the "Overdue" filter have content.
    IF NOT EXISTS (SELECT 1 FROM invoice) THEN
        INSERT INTO invoice (student_id, reference_month, amount, due_date, status, created_at, updated_at) VALUES
            (beatriz, last_month, 350.00, last_month + 9,      'PAID',    localtimestamp, localtimestamp),
            (beatriz, this_month, 350.00, CURRENT_DATE + 5,    'OPEN',    localtimestamp, localtimestamp),
            (carla,   this_month, 558.00, CURRENT_DATE - 10,   'OVERDUE', localtimestamp, localtimestamp),
            (diego,   this_month, 270.00, CURRENT_DATE + 2,    'OPEN',    localtimestamp, localtimestamp);

        -- The paid invoice needs its payment record: without a row in
        -- payment, the screen shows "Paid" with no date, a state the system
        -- never produces on its own.
        INSERT INTO payment (invoice_id, payment_date, registered_by, created_at, updated_at)
        SELECT i.id, last_month + 8, admin_id, localtimestamp, localtimestamp
          FROM invoice i
         WHERE i.student_id = beatriz AND i.reference_month = last_month;

        RAISE NOTICE 'cobrança: 4 faturas (1 paga, 2 abertas, 1 atrasada) criadas';
    END IF;
END;
$movimento$;

-- What's left in the database, to check without opening another psql.
SELECT u.id,
       u.login,
       u.role,
       coalesce(t.name, s.name, '-') AS pessoa,
       u.active
  FROM users u
  LEFT JOIN teacher t ON t.id = u.teacher_id
  LEFT JOIN student s ON s.id = u.student_id
 ORDER BY u.role, u.login;

SELECT 'professores' AS tabela, count(*) FROM teacher
UNION ALL SELECT 'alunos (ativos)',  count(*) FROM student WHERE active
UNION ALL SELECT 'alunos (inativos)', count(*) FROM student WHERE NOT active
UNION ALL SELECT 'horários padrão',  count(*) FROM default_schedule
UNION ALL SELECT 'exceções de agenda', count(*) FROM schedule_exception
UNION ALL SELECT 'comunicados',      count(*) FROM announcement
UNION ALL SELECT 'planos',           count(*) FROM plan
UNION ALL SELECT 'matrículas',       count(*) FROM plan_enrollment
UNION ALL SELECT 'faturas',          count(*) FROM invoice;
