insert into vaccine_templates
(id, name, disease, dose_number, total_doses, type, scheduled_age_months, scheduled_age_days, notes, is_active, created_at, updated_at)
values
-- Туберкульоз
(gen_random_uuid(), 'БЦЖ', 'Туберкульоз', 1, 1, 'VACCINATION', null, 2, 'Введення в пологовому будинку', true, NOW(), NOW()),

-- Гепатит В (Стандартна схема 0-2-6)
(gen_random_uuid(), 'Гепатит В', 'Гепатит В', 1, 3, 'VACCINATION', 2, NULL, 'Перша доза або друга за схемою', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'Гепатит В', 'Гепатит В', 2, 3, 'VACCINATION', 4, NULL, 'Друга або третя доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'Гепатит В', 'Гепатит В', 3, 3, 'VACCINATION', 6, NULL, 'Завершення курсу вакцинації', TRUE, NOW(), NOW()),

-- АКДП (Дифтерія, Правець, Кашлюк)
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', 1, 3, 'VACCINATION', 2, NULL, 'Перша доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', 2, 3, 'VACCINATION', 4, NULL, 'Друга доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', 3, 3, 'VACCINATION', 6, NULL, 'Третя доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', NULL, NULL, 'REVACCINATION', 18, NULL, 'Перша ревакцинація (1.5 роки)', TRUE, NOW(), NOW()),

-- АДП-М (Ревакцинація дорослих та старших дітей)
(gen_random_uuid(), 'АДП-М', 'Дифтерія/Правець', NULL, NULL, 'REVACCINATION', 72, NULL, 'Ревакцинація у 6 років', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'АДП-М', 'Дифтерія/Правець', NULL, NULL, 'REVACCINATION', 192, NULL, '16 років, далі кожні 10 років', TRUE, NOW(), NOW()),

-- Поліомієліт (ІПВ/ОПВ)
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', 1, 3, 'VACCINATION', 2, NULL, 'Інактивована (інєкція)', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', 2, 3, 'VACCINATION', 4, NULL, 'Інактивована (інєкція)', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'ОПВ', 'Поліомієліт', 3, 3, 'REVACCINATION', 6, NULL, 'Жива оральна (краплі)', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'ОПВ', 'Поліомієліт', NULL, NULL, 'REVACCINATION', 18, NULL, 'Ревакцинація краплями (1.5 роки)', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'ОПВ', 'Поліомієліт', NULL, NULL, 'REVACCINATION', 72, NULL, 'Ревакцинація у 6 років', TRUE, NOW(), NOW()),

-- Hib-інфекція
(gen_random_uuid(), 'Hib', 'Hib-інфекція', 1, 3, 'VACCINATION', 2, NULL, 'Перша доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'Hib', 'Hib-інфекція', 2, 3, 'VACCINATION', 4, NULL, 'Друга доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'Hib', 'Hib-інфекція', 3, 3, 'VACCINATION', 6, NULL, 'Третя доза', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'Hib', 'Hib-інфекція', NULL, NULL, 'REVACCINATION', 18, NULL, 'Ревакцинація (1.5 роки)', TRUE, NOW(), NOW()),

-- КПК (Кір, Паротит, Краснуха)
(gen_random_uuid(), 'КПК', 'Кір/Паротит/Краснуха', 1, 2, 'VACCINATION', 12, NULL, 'Введення в 1 рік', TRUE, NOW(), NOW()),
(gen_random_uuid(), 'КПК', 'Кір/Паротит/Краснуха', 2, 2, 'VACCINATION', 48, NULL, 'Ревакцинація в 4 роки', TRUE, NOW(), NOW()),

-- ВПЛ (Рекомендоване щеплення)
(gen_random_uuid(), 'ВПЛ', 'ВПЛ-інфекція', 1, 1, 'VACCINATION', 144, NULL, 'Тільки для дівчат (12 років)', TRUE, NOW(), NOW());