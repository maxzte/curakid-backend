INSERT INTO vaccine_templates 
(id, name, disease, dose_number, total_doses, type, scheduled_age_months, scheduled_age_days, notes) 
VALUES
-- Туберкульоз
(gen_random_uuid(), 'БЦЖ', 'Туберкульоз', 1, 1, 'VACCINATION', NULL, 2, NULL),

-- Гепатит В (Стандартна схема 0-2-6)
(gen_random_uuid(), 'Гепатит В', 'Гепатит В', 1, 3, 'VACCINATION', 2, NULL, NULL),
(gen_random_uuid(), 'Гепатит В', 'Гепатит В', 2, 3, 'VACCINATION', 4, NULL, NULL),
(gen_random_uuid(), 'Гепатит В', 'Гепатит В', 3, 3, 'VACCINATION', 6, NULL, NULL),

-- АКДП (Дифтерія, Правець, Кашлюк)
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', 1, 3, 'VACCINATION', 2, NULL, NULL),
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', 2, 3, 'VACCINATION', 4, NULL, NULL),
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', 3, 3, 'VACCINATION', 6, NULL, NULL),
(gen_random_uuid(), 'АКДП', 'Дифтерія/Правець/Кашлюк', NULL, NULL, 'REVACCINATION', 18, NULL, NULL),

-- АДП-М (Ревакцинація дорослих та старших дітей)
(gen_random_uuid(), 'АДП-М', 'Дифтерія/Правець', NULL, NULL, 'REVACCINATION', 72, NULL, '6 років'),
(gen_random_uuid(), 'АДП-М', 'Дифтерія/Правець', NULL, NULL, 'REVACCINATION', 192, NULL, '16 років, далі кожні 10 років'),

-- Поліомієліт (ІПВ/ОПВ)
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', 1, 3, 'VACCINATION', 2, NULL, 'Інактивована'),
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', 2, 3, 'VACCINATION', 4, NULL, 'Інактивована'),
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', 3, 3, 'REVACCINATION', 6, NULL, 'Жива оральна'),
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', NULL, NULL, 'REVACCINATION', 18, NULL, NULL),
(gen_random_uuid(), 'ІПВ', 'Поліомієліт', NULL, NULL, 'REVACCINATION', 72, NULL, '6 років'),

-- Hib-інфекція
(gen_random_uuid(), 'Hib', 'Hib-інфекція', 1, 3, 'VACCINATION', 2, NULL, NULL),
(gen_random_uuid(), 'Hib', 'Hib-інфекція', 2, 3, 'VACCINATION', 4, NULL, NULL),
(gen_random_uuid(), 'Hib', 'Hib-інфекція', 3, 3, 'VACCINATION', 6, NULL, NULL),
(gen_random_uuid(), 'Hib', 'Hib-інфекція', NULL, NULL, 'REVACCINATION', 18, NULL, NULL),

-- КПК (Кір, Паротит, Краснуха)
(gen_random_uuid(), 'КПК', 'Кір/Паротит/Краснуха', 1, 2, 'VACCINATION', 12, NULL, NULL),
(gen_random_uuid(), 'КПК', 'Кір/Паротит/Краснуха', 2, 2, 'VACCINATION', 48, NULL, NULL),

-- ВПЛ (Рекомендоване щеплення)
(gen_random_uuid(), 'ВПЛ', 'ВПЛ-інфекція', 1, 1, 'VACCINATION', 144, NULL, 'Тільки для дівчат (12 років)');