-- ====================================================================
-- Скрипт наповнення періоду 1 місяць (P0_1M) для бази даних PostgreSQL
-- Таблиці: life_periods, questions, answer_option
-- ====================================================================

-- 1. Створення або оновлення періоду P0_1M
INSERT INTO life_periods (id, code, label, min_months, max_months, description, is_active, created_at, updated_at)
VALUES (
    '9ef33d16-43fc-44d5-8698-cd3de233d74f',
    'P0_1M',
    '1',
    0,
    1,
    '{"ua": {"title": "Перші дні разом", "subtitle": "Час ніжності, адаптації та першого знайомства малюка з навколишнім світом.", "milestones": {"movement": "здійснює хаотичні рухи ручками й ніжками, тримає пальчики зігнутими в кулачки, на животику пробує на мить підняти голівку.", "speech": "реагує на гучні та різкі звуки (завмирає чи кліпає), плач є головним засобом вираження дискомфорту чи голоду, з''являються перші короткі горлові звуки.", "cognition": "впізнає голос мами, фіксує погляд на обличчі дорослого на відстані 20-30 см та стежить за великими контрастними предметами."}, "tips": ["Частіше практикуйте контакт «шкіра до шкіри» — це стабілізує серцебиття малюка, знижує стрес і дарує почуття повної безпеки.", "Викладайте дитину на животик на тверду поверхню на кілька хвилин під час неспання, щоб зміцнювати м''язи шиї та спини.", "Розмовляйте з малюком спокійним лагідним голосом та наближайте своє обличчя на відстань 25-30 см — це оптимальна дистанція фокусування зору новонародженого.", "Показуйте контрастні чорно-білі картинки або великі візерунки для активної стимуляції зорової кори мозку."]}}',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (code) WHERE deleted_at IS NULL DO UPDATE
SET label = EXCLUDED.label,
    min_months = EXCLUDED.min_months,
    max_months = EXCLUDED.max_months,
    description = EXCLUDED.description,
    is_active = true,
    updated_at = NOW();

-- Якщо запис існував за ID або кодом (навіть без partial index):
UPDATE life_periods
SET label = '1',
    min_months = 0,
    max_months = 1,
    description = '{"ua": {"title": "Перші дні разом", "subtitle": "Час ніжності, адаптації та першого знайомства малюка з навколишнім світом.", "milestones": {"movement": "здійснює хаотичні рухи ручками й ніжками, тримає пальчики зігнутими в кулачки, на животику пробує на мить підняти голівку.", "speech": "реагує на гучні та різкі звуки (завмирає чи кліпає), плач є головним засобом вираження дискомфорту чи голоду, з''являються перші короткі горлові звуки.", "cognition": "впізнає голос мами, фіксує погляд на обличчі дорослого на відстані 20-30 см та стежить за великими контрастними предметами."}, "tips": ["Частіше практикуйте контакт «шкіра до шкіри» — це стабілізує серцебиття малюка, знижує стрес і дарує почуття повної безпеки.", "Викладайте дитину на животик на тверду поверхню на кілька хвилин під час неспання, щоб зміцнювати м''язи шиї та спини.", "Розмовляйте з малюком спокійним лагідним голосом та наближайте своє обличчя на відстань 25-30 см — це оптимальна дистанція фокусування зору новонародженого.", "Показуйте контрастні чорно-білі картинки або великі візерунки для активної стимуляції зорової кори мозку."]}}',
    is_active = true,
    updated_at = NOW()
WHERE code = 'P0_1M' AND deleted_at IS NULL;

-- 2. Вставка запитань та варіантів відповідей (визначаємо правильну назву таблиці answer_option / answer_options)
DO $$
DECLARE
    v_period_id UUID;
    v_question_id UUID;
    v_has_answer_option BOOLEAN;
    v_has_answer_options BOOLEAN;
BEGIN
    SELECT id INTO v_period_id FROM life_periods WHERE code = 'P0_1M' AND deleted_at IS NULL LIMIT 1;
    IF v_period_id IS NULL THEN
        v_period_id := '9ef33d16-43fc-44d5-8698-cd3de233d74f'::UUID;
    END IF;

    -- Перевірка чи існує таблиця answer_option або answer_options
    SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'answer_option') INTO v_has_answer_option;
    SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'answer_options') INTO v_has_answer_options;


    -- Питання: Заспокоюється, коли ви берете дитину на руки або л...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Заспокоюється, коли ви берете дитину на руки або лагідно розмовляєте з нею?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Заспокоюється, коли ви берете дитину на руки або лагідно розмовляєте з нею?', 'SOCIAL_EMOTIONAL', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Затримує погляд на вашому обличчі, коли ви нахиляє...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Затримує погляд на вашому обличчі, коли ви нахиляєтесь над нею (на відстані 20-30 см)?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Затримує погляд на вашому обличчі, коли ви нахиляєтесь над нею (на відстані 20-30 см)?', 'SOCIAL_EMOTIONAL', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Проявляє перші ознаки емоційного задоволення (розс...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Проявляє перші ознаки емоційного задоволення (розслаблює тіло, затихає, прислухається) під час контакту шкіра-до-шкіри?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Проявляє перші ознаки емоційного задоволення (розслаблює тіло, затихає, прислухається) під час контакту шкіра-до-шкіри?', 'SOCIAL_EMOTIONAL', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Реагує на несподівані чи гучні звуки (здригається,...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Реагує на несподівані чи гучні звуки (здригається, кліпає оченятами, завмирає або плаче)?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Реагує на несподівані чи гучні звуки (здригається, кліпає оченятами, завмирає або плаче)?', 'SPEECH', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Видає тихі короткі горлові звуки (кректання, зітха...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Видає тихі короткі горлові звуки (кректання, зітхання, пирхання), окрім плачу?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Видає тихі короткі горлові звуки (кректання, зітхання, пирхання), окрім плачу?', 'SPEECH', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Затихає або повертає очі в бік знайомого голосу ба...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Затихає або повертає очі в бік знайомого голосу батьків, коли вони говорять поруч?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Затихає або повертає очі в бік знайомого голосу батьків, коли вони говорять поруч?', 'SPEECH', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Фіксує погляд на яскравому або контрастному предме...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Фіксує погляд на яскравому або контрастному предметі перед собою протягом кількох секунд?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Фіксує погляд на яскравому або контрастному предметі перед собою протягом кількох секунд?', 'COGNITIVE', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Стежить очима або плавно повертає голівку за предм...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Стежить очима або плавно повертає голівку за предметом, який повільно рухається перед нею?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Стежить очима або плавно повертає голівку за предметом, який повільно рухається перед нею?', 'COGNITIVE', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Розрізняє світло й темряву (мружиться на яскраве с...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Розрізняє світло й темряву (мружиться на яскраве світло, повертається до розсіяного джерела світла)?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Розрізняє світло й темряву (мружиться на яскраве світло, повертається до розсіяного джерела світла)?', 'COGNITIVE', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Лежачи на животику, робить спроби на 1-2 секунди п...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Лежачи на животику, робить спроби на 1-2 секунди підняти або повернути голівку в інший бік?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Лежачи на животику, робить спроби на 1-2 секунди підняти або повернути голівку в інший бік?', 'MOTOR_GROSS', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Лежачи на спинці, активно рухає обома руками та об...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Лежачи на спинці, активно рухає обома руками та обома ніжками одночасно?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Лежачи на спинці, активно рухає обома руками та обома ніжками одночасно?', 'MOTOR_GROSS', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Тримає пальчики рук переважно зігнутими в кулачки ...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Тримає пальчики рук переважно зігнутими в кулачки у стані спокою?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Тримає пальчики рук переважно зігнутими в кулачки у стані спокою?', 'MOTOR_FINE', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


    -- Питання: Міцно обхоплює і рефлекторно стискає ваш палець, я...
    SELECT id INTO v_question_id FROM questions WHERE life_period_id = v_period_id AND text = 'Міцно обхоплює і рефлекторно стискає ваш палець, якщо вкласти його в долоньку (хапальний рефлекс)?' AND deleted_at IS NULL LIMIT 1;
    IF v_question_id IS NULL THEN
        v_question_id := gen_random_uuid();
        INSERT INTO questions (id, text, domain, weight, life_period_id, is_active, created_at, updated_at)
        VALUES (v_question_id, 'Міцно обхоплює і рефлекторно стискає ваш палець, якщо вкласти його в долоньку (хапальний рефлекс)?', 'MOTOR_FINE', 1.00, v_period_id, true, NOW(), NOW());
    ELSE
        UPDATE questions SET is_active = true, updated_at = NOW() WHERE id = v_question_id;
    END IF;

    -- Варіанти відповідей
    IF v_has_answer_option THEN
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_option WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_option (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    ELSIF v_has_answer_options THEN
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Так' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Так', 100.0, 1, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Частково' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Частково', 50.0, 2, true, NOW(), NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM answer_options WHERE question_id = v_question_id AND text = 'Ще ні' AND deleted_at IS NULL) THEN
            INSERT INTO answer_options (id, question_id, text, value, display_order, is_active, created_at, updated_at)
            VALUES (gen_random_uuid(), v_question_id, 'Ще ні', 0.0, 3, true, NOW(), NOW());
        END IF;
    END IF;


END $$;
