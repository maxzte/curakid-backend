🏗 Архітектурний паттерн: Модульний моноліт з аналітичним мікросервісом

Система побудована за принципом Orchestrator-Worker. Основний сервіс на Spring Boot виступає
оркестратором бізнес-логіки та безпеки, а сервіс на Python — спеціалізованим інструментом для
інтелектуального аналізу.

1. Core Service (Оркестратор)

   Стек: Kotlin + Spring Boot 4.x.

   Функції:

        Auth Module: Керування доступом (JWT, Refresh Tokens, Blacklist). Реалізовано GDPR-сумісне видалення (Soft Delete з анонімізацією даних).

        Resource Module: CRUD операції для профілів дітей, антропометрії та квізів. Впроваджено валідацію циклічних залежностей та інтелектуальне пропускання питань.

        RAG Engine: Використання SpringAI для гібридного пошуку (pgvector + Full-Text Search). Впроваджено асинхронну оцінку якості звітів (LLM-as-a-judge: Faithfulness, Relevance).

        Integration Layer: Клієнт для взаємодії з FIS-модулем через REST. Захист реалізовано через Shared Secret.

2. Intelligence Service (FIS Модуль)

   Стек: Python + FastAPI + scikit-fuzzy.

   Функції:

        Fuzzy Logic Inference: Отримання числових показників (Z-score, Skill-score) та перетворення їх у лінгвістичні терм-множини.

        Rule Engine: Виконання правил нечіткої логіки. Правила передаються безпосередньо зі Spring Boot.

        Defuzzification: Розрахунок Urgency Score.

3. Data Storage Layer (Віддалений рівень даних)

   СУБД: Supabase (Remote PostgreSQL 16).

   Vector Engine: pgvector для семантичного пошуку.

   Search Engine: Гібридний пошук (Vector + FTS). Використовується алгоритм Rank Reciprocal Fusion (
   RRF) для об'єднання результатів (конфігурації `simple` та `english`).

   ORM: Hibernate (JPA). Використовується глобальний фільтр `@SQLRestriction("deleted_at IS NULL")`
   для реалізації Soft Delete.

🔄 Життєвий цикл запиту на аналіз (Data Flow)

    Request: Клієнт надсилає запит на генерацію звіту.

    Aggregation: Spring Boot збирає останні дані дитини та нечіткі правила.

    Analysis: Python FIS повертає Urgency Score.

    Retrieval: Гібридний пошук (Vector + FTS) знаходить релевантні медичні чанки.

    Generation: LLM (Google AI Studio) генерує звіт.

    Response: Згенерований звіт зберігається в БД та повертається користувачу.

    Evaluation: Система асинхронно запускає аудит звіту через ШІ, зберігаючи метрики Faithfulness та Relevance.

🔄 Бізнес-логіка Опитувань (Quiz Logic)

    Prerequisite Validation: Перевірка на циклічні залежності (DFS) під час редагування питань.

    Dynamic Skipping: Автоматичне зарахування передумов при виконанні складної навички (score >= 100).

🔄 Життєвий цикл видалення (GDPR & Integrity Flow)

    User Deletion: Емейл анонімізується, дані затираються, запис отримує мітку `deleted_at`. Це звільняє емейл для нової реєстрації.

    Content Deletion: М'яке видалення (Soft Delete). Для періодів з історією відповідей видалення заборонено для збереження цілісності медичної карти.

🛠 Інфраструктура та Деплой

    Containerization: Docker Compose (backend + intelligence).

    Communication: Внутрішня мережа Docker.

    CI/CD Friendly: Незалежне оновлення бази знань, логіки FIS або правил аналізу.
