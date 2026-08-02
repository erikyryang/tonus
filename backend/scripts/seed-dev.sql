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
-- É idempotente: reexecutar reaplica a senha padrão, cria login apenas para
-- quem ainda não tem e não duplica movimento. Rode de novo depois de cadastrar
-- gente nova pela API, ou depois de recriar o volume `tonus-db-data`.
--
-- Num banco zerado ele também monta o ambiente inteiro: o ADMIN, que não é
-- derivado de ninguém; um cadastro de demonstração com dois professores e
-- cinco alunos (um deles inativo, para o filtro de situação ter o que
-- mostrar); e o movimento que faz as telas nascerem com conteúdo — agenda,
-- exceções, comunicados, planos, matrículas e faturas nos três status.
--
-- Tela vazia não exercita interface nenhuma, e é para exercitá-la que o
-- ambiente local existe. Assim que houver alguém em teacher/student o script
-- para de inventar gente, e cada bloco de movimento é guardado pela própria
-- tabela estar vazia: num banco em uso ele não encosta em nada.
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

-- ---------------------------------------------------------------------------
-- Movimento de demonstração: agenda, comunicados, planos e faturas.
--
-- Existe para que as telas nasçam com conteúdo em vez de vazias — o ambiente
-- local serve para exercitar a interface, e tela vazia não exercita nada. Cada
-- bloco é guardado pela própria tabela estar vazia: reexecutar não duplica, e
-- num banco onde já existe movimento o script não encosta em nada.
--
-- Tudo se ancora em CURRENT_DATE, nunca em data literal: seed com data fixa
-- apodrece, e o ambiente montado em março mostraria uma agenda de janeiro.
-- ---------------------------------------------------------------------------
DO $movimento$
DECLARE
    beatriz bigint;
    carla   bigint;
    diego   bigint;
    helena  bigint;
    marina  bigint;
    admin_id bigint;
    -- Hoje, se for dia útil; a segunda-feira seguinte, se cair no fim de
    -- semana. A agenda só existe de segunda a sexta, e um seed rodado no
    -- sábado deixaria a tela do dia vazia justamente para quem foi conferi-la.
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

    -- Sem o cadastro de demonstração não há a quem pendurar movimento, e
    -- inventá-lo para os alunos de verdade de alguém seria sujar o ambiente
    -- de trabalho — mesma regra que vale para não inventar pessoas.
    IF beatriz IS NULL OR carla IS NULL OR diego IS NULL OR helena IS NULL THEN
        RAISE NOTICE 'cadastro de demonstração ausente: movimento não semeado';
        RETURN;
    END IF;

    -- Aluna inativa: é o que faz o filtro "Inativos" das listagens ter o que
    -- mostrar. Fica sem login de propósito — quem saiu do estúdio não entra.
    IF NOT EXISTS (SELECT 1 FROM student WHERE name = 'Sofia Ramos') THEN
        INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
        VALUES ('Sofia Ramos', '+5511988880005', marina, FALSE, localtimestamp, localtimestamp);
    END IF;

    -- Agenda: dois alunos no mesmo horário (é assim que se vê o agrupamento
    -- por hora fazer o que promete) e dois em horários próprios.
    IF NOT EXISTS (SELECT 1 FROM default_schedule) THEN
        INSERT INTO default_schedule (student_id, time_of_day, created_at, updated_at) VALUES
            (beatriz, TIME '07:00', localtimestamp, localtimestamp),
            (carla,   TIME '07:00', localtimestamp, localtimestamp),
            (diego,   TIME '18:00', localtimestamp, localtimestamp),
            (helena,  TIME '19:00', localtimestamp, localtimestamp);
        RAISE NOTICE 'agenda: 4 horários padrão criados';
    END IF;

    -- Um cancelamento e uma remarcação: são os dois estados que a tela do dia
    -- mostra fora da aula comum, e o que o aluno vê em /me/schedule.
    IF NOT EXISTS (SELECT 1 FROM schedule_exception) THEN
        INSERT INTO schedule_exception (student_id, date, type, new_date, new_time, created_at, updated_at) VALUES
            (beatriz, ref_day, 'CANCELED', NULL, NULL, localtimestamp, localtimestamp),
            (diego, ref_day, 'RESCHEDULED', ref_day + 1, TIME '20:00', localtimestamp, localtimestamp);
        RAISE NOTICE 'agenda: 1 cancelamento e 1 remarcação criados';
    END IF;

    -- Os dois tipos de comunicado, para a diferença aparecer na tela: um
    -- suspende o dia, o outro só avisa e a agenda segue.
    IF NOT EXISTS (SELECT 1 FROM announcement) THEN
        INSERT INTO announcement (date, description, suspends_classes, created_at, updated_at) VALUES
            (ref_day + 3, 'Feriado municipal', TRUE, localtimestamp, localtimestamp),
            (ref_day + 1, 'Obra na rua: entrada pelos fundos', FALSE, localtimestamp, localtimestamp);
        RAISE NOTICE 'comunicados: 1 com suspensão e 1 informativo criados';
    END IF;

    -- Catálogo com um plano inativo, para o filtro de situação ter o que
    -- filtrar, e desconto configurado, para a tela de configuração não abrir
    -- em zero.
    IF NOT EXISTS (SELECT 1 FROM plan) THEN
        INSERT INTO plan (name, monthly_price, active, created_at, updated_at) VALUES
            ('Musculação',      350.00, TRUE,  localtimestamp, localtimestamp),
            ('Pilates',         270.00, TRUE,  localtimestamp, localtimestamp),
            ('Avaliação Física', 90.00, TRUE,  localtimestamp, localtimestamp),
            ('Plano Antigo',    199.00, FALSE, localtimestamp, localtimestamp);

        SELECT id INTO plano_musculacao FROM plan WHERE name = 'Musculação';
        SELECT id INTO plano_pilates    FROM plan WHERE name = 'Pilates';

        -- Beatriz com um plano, Carla com dois (é ela quem exercita o
        -- desconto), Diego com um, Helena com nenhum — o aluno sem plano é o
        -- que o job não fatura, e a tela precisa mostrar isso também.
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

    -- Uma fatura de cada status. A ATRASADA é o que faz a tela de alunos
    -- mostrar o selo "Atrasado" e o filtro "Atrasadas" ter conteúdo.
    IF NOT EXISTS (SELECT 1 FROM invoice) THEN
        INSERT INTO invoice (student_id, reference_month, amount, due_date, status, created_at, updated_at) VALUES
            (beatriz, last_month, 350.00, last_month + 9,      'PAID',    localtimestamp, localtimestamp),
            (beatriz, this_month, 350.00, CURRENT_DATE + 5,    'OPEN',    localtimestamp, localtimestamp),
            (carla,   this_month, 558.00, CURRENT_DATE - 10,   'OVERDUE', localtimestamp, localtimestamp),
            (diego,   this_month, 270.00, CURRENT_DATE + 2,    'OPEN',    localtimestamp, localtimestamp);

        -- A fatura paga precisa da baixa: sem a linha em payment, a tela mostra
        -- "Paga" sem data, que é um estado que o sistema não produz sozinho.
        INSERT INTO payment (invoice_id, payment_date, registered_by, created_at, updated_at)
        SELECT i.id, last_month + 8, admin_id, localtimestamp, localtimestamp
          FROM invoice i
         WHERE i.student_id = beatriz AND i.reference_month = last_month;

        RAISE NOTICE 'cobrança: 4 faturas (1 paga, 2 abertas, 1 atrasada) criadas';
    END IF;
END;
$movimento$;

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

SELECT 'professores' AS tabela, count(*) FROM teacher
UNION ALL SELECT 'alunos (ativos)',  count(*) FROM student WHERE active
UNION ALL SELECT 'alunos (inativos)', count(*) FROM student WHERE NOT active
UNION ALL SELECT 'horários padrão',  count(*) FROM default_schedule
UNION ALL SELECT 'exceções de agenda', count(*) FROM schedule_exception
UNION ALL SELECT 'comunicados',      count(*) FROM announcement
UNION ALL SELECT 'planos',           count(*) FROM plan
UNION ALL SELECT 'matrículas',       count(*) FROM plan_enrollment
UNION ALL SELECT 'faturas',          count(*) FROM invoice;
