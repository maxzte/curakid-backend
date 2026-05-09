Поетапний план реалізації бекенду CuraKid
Етап 0 — Підготовка інфраструктури

Додати .env до .gitignore
Створити docker-compose.yml з двома сервісами: backend (Spring Boot) та intelligence (Python FastAPI)
Підключитись до Supabase, активувати розширення pgvector та uuid-ossp
Виконати SQL-скрипт створення всіх таблиць (з попередніх кроків)
Створити векторний індекс HNSW на таблиці medical_knowledge

Етап 1 — Базова структура Spring Boot проєкту

Створити Spring Boot 4.x проєкт зі залежностями:

spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation
spring-ai-core + spring-ai-vertex-ai-gemini
postgresql driver
pgvector-hibernate

Налаштувати application.yml з підключенням до Supabase
Створити базову структуру пакетів:

com.misterio.curakid
├── auth/
├── children/
├── measurements/
├── assessments/
├── reports/
├── knowledge/
├── admin/
├── fis/
└── common/

Створити базовий BaseEntity з полями id, createdAt, updatedAt

Етап 2 — Модуль автентифікації (Auth Module)

Створити entity: User, RefreshToken, TokenBlacklist, PasswordResetToken, EmailVerificationToken, OAuthProvider
Створити репозиторії для кожної entity
Реалізувати JwtService:

генерація access token (TTL 15 хв) з полем jti
генерація refresh token
валідація токена
витягування jti з токена

Реалізувати TokenBlacklistService:

додавання jti до blacklist при logout
перевірка чи токен у blacklist
фоновий @Scheduled job для очищення протермінованих записів

Налаштувати Spring Security:

JwtAuthenticationFilter — перевірка токена + перевірка blacklist при кожному запиті
публічні endpoints: /api/auth/\*\*
захищені endpoints: все інше

Реалізувати AuthService з методами:

register(email, password, fullName) → надсилає email верифікації
login(email, password) → повертає access + refresh token
refreshToken(refreshToken) → повертає новий access token
logout(jti, refreshToken) → blacklist + revoke
forgotPassword(email) → надсилає reset token на пошту
resetPassword(token, newPassword) → валідує token, оновлює пароль
verifyEmail(token) → підтверджує пошту

Реалізувати OAuthService:

loginWithGoogle(idToken) → верифікація через Google, створення або пошук user

Створити AuthController з endpoints:

POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/verify-email
POST /api/auth/oauth/google

Написати unit тести для JwtService та AuthService

Етап 3 — Модуль дітей та вимірювань (Resource Module)

Створити entity: Child, Measurement, SkillAssessment
Реалізувати ChildService:

createChild(userId, dto) → прив'язка до поточного user
getChildren(userId) → список дітей користувача
updateChild(childId, dto)
deleteChild(childId)

Реалізувати MeasurementService:

addMeasurement(childId, dto) → збереження + розрахунок z-scores
getMeasurements(childId) → список в хронологічному порядку
Розрахунок wfa_zscore, hfa_zscore, bmi_zscore за таблицями ВООЗ

Реалізувати SkillAssessmentService:

saveAssessment(childId, dto) → збереження результату квізу
getAssessments(childId, domain) → фільтрація за доменом

Створити контролери:

ChildController: GET/POST/PUT/DELETE /api/children
MeasurementController: GET/POST /api/children/{childId}/measurements
AssessmentController: GET/POST /api/children/{childId}/assessments

Додати валідацію вхідних DTO через @Valid
Написати unit тести для сервісів

Етап 4 — FIS клієнт (Integration Layer)

Створити FisRequest та FisResponse DTO
Реалізувати FisClient через WebClient:

додавання заголовку X-Internal-Key до кожного запиту
timeout 5 секунд
fallback при недоступності сервісу (urgencyScore = 0.5, isFallback = true)

Написати інтеграційний тест з mock Python сервісом

Етап 5 — Модуль звітів та RAG (Reports Module)

Створити entity: PeriodSummary, AiReport
Реалізувати PeriodService:

checkAndClosePeriod(childId) → перевірка чи дитина перейшла до нового вікового періоду
closePeriod(summaryId) → позначити is_closed = true, запустити генерацію звіту

Реалізувати ReportService — головний оркестратор:

Завантажити дані дитини + останні вимірювання + assessments з БД
Викликати FisClient.analyze() → отримати urgencyScore
Викликати KnowledgeService.search() → отримати релевантні медичні чанки
Сформувати промпт для Gemini
Викликати Gemini через SpringAI
Зберегти звіт в ai_reports

Реалізувати KnowledgeService:

search(query, urgencyScore, limit) → векторний пошук у pgvector через нативний SQL
генерація embedding запиту через multilingual-e5-large

Налаштувати SpringAI для роботи з Gemini 1.5 Flash
Створити ReportController:

GET /api/children/{childId}/reports → список звітів
GET /api/reports/{reportId} → конкретний звіт
POST /api/reports/{summaryId}/regenerate → повторна генерація

Етап 6 — Адмін модуль (Admin Module)

Додати роль ROLE_ADMIN до системи Spring Security
Реалізувати AdminKnowledgeService:

uploadArticle(file, title, category) → парсинг тексту → chunking → генерація embeddings → збереження в medical_knowledge
deleteArticle(id) → видалення з БД
getArticles() → список з статусом індексації

Реалізувати AdminQuizService:

getQuestions() → список питань з вагами
createQuestion(dto) → нове питання з доменом та вагою
updateQuestionWeight(id, weight) → оновлення ваги
deleteQuestion(id)

Реалізувати AdminFuzzyService:

getRules() → завантаження правил з БД (JSONB)
updateRules(rules) → оновлення правил без редеплою
getThresholds() → порогові значення z-score
updateThresholds(thresholds)

Реалізувати AdminUserService:

getUsers() → список користувачів
blockUser(userId) → is_active = false
unblockUser(userId) → is_active = true

Створити AdminController з префіксом /api/admin/\*\*

Етап 7 — Python FIS сервіс

Створити FastAPI проєкт зі структурою:

intelligence/
├── main.py
├── routers/
│ └── analyze.py
├── services/
│ └── fuzzy_engine.py
├── models/
│ └── schemas.py
├── config.py
└── Dockerfile

Реалізувати FuzzyEngine:

динамічна побудова fuzzy системи на основі правил з запиту
підтримка доменів: weight, height, motor, speech, cognitive, social
дефазифікація методом центру мас
повернення urgencyScore + детальний breakdown по доменах

Реалізувати middleware для перевірки X-Internal-Key
Створити endpoint POST /analyze
Написати тести через pytest
Створити Dockerfile для Python сервісу

Етап 8 — Фінальна інтеграція та тестування

Оновити docker-compose.yml з обома сервісами та спільною мережею
Перевірити повний Data Flow end-to-end:

реєстрація → верифікація пошти → логін → додавання дитини → вимірювання → квіз → закриття періоду → генерація звіту

Написати інтеграційні тести для критичних сценаріїв
Налаштувати CI через GitHub Actions:

запуск unit тестів при кожному push
автодеплой на Render при merge в main

Перевірити fallback сценарій (вимкнути FIS контейнер)
Перевірити розгортання на Render + Supabase у продакшн конфігурації
