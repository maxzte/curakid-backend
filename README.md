# CuraKid Backend 🚀

Цей репозиторій містить бекенд-частину системи **CuraKid** — платформи для моніторингу розвитку дітей з використанням нечіткої логіки та RAG (Retrieval-Augmented Generation).

## 🏗 Архітектура

Система побудована за принципом **Orchestrator-Worker**:
1.  **Core Service (Spring Boot + Kotlin)**: Оркестратор бізнес-логіки, безпеки (Auth) та RAG-двигун.
2.  **Intelligence Service (FastAPI + Python)**: FIS-модуль (Fuzzy Inference System) для аналізу стану дитини та генерації ембедінгів.
3.  **Database (Supabase/PostgreSQL + pgvector)**: Зберігання реляційних даних та векторних представлень медичних протоколів.

Детальніше про архітектуру читайте в [AGENTS.md](./AGENTS.md).

---

## 🛠 Початок роботи

### 📋 Попередні вимоги
- **Docker** та **Docker Compose**
- Доступ до **Supabase** (або локальний PostgreSQL з розширенням `pgvector`)
- **Google Cloud Project** з активованим Vertex AI (для Gemini API)

### ⚙️ Налаштування оточення
1. Скопіюйте приклад файлу налаштувань:
   ```bash
   cp .env.example .env
   ```
2. Відредагуйте `.env`, вказавши ваші ключі та параметри підключення до БД.
   - **Важливо**: `JWT_SECRET` має бути довжиною не менше 32 символів.
   - `INTERNAL_API_KEY` має бути однаковим для обох сервісів.

---

### 🔑 Авторизація Google AI (Vertex AI)
Для роботи Gemini API необхідно:
1. Мати активний Google Cloud Project.
2. Встановити [Google Cloud SDK](https://cloud.google.com/sdk/docs/install).
3. Виконати команду для локальної авторизації:
   ```bash
   gcloud auth application-default login
   ```
4. У Docker-контейнері Spring Boot автоматично шукатиме облікові дані, якщо змонтувати відповідний volume або передати `GOOGLE_APPLICATION_CREDENTIALS`.

---

## 🚀 Запуск системи

### Варіант 1: Через Docker Compose (рекомендовано)
Це запустить обидва сервіси (Spring Boot та Python) в єдиній мережі.

```bash
docker-compose up --build
```

- **Spring App**: `http://localhost:8080`
- **Intelligence Service**: `http://localhost:8000` (перший запуск може тривати 2-5 хв через завантаження ML-моделі ~1GB)

### 🧪 Тестування
Для ручної перевірки Intelligence Service можна використати `curl`:
```bash
curl -X POST http://localhost:8000/embed \
     -H "X-Internal-Key: your_shared_secret" \
     -H "Content-Type: application/json" \
     -d '{"text": "Привіт, світ"}'
```

#### 1. Intelligence Service (Python)
```bash
cd intelligence
python -m venv venv
source venv/bin/activate  # або venv\Scripts\activate для Windows
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

#### 2. Core Service (Spring Boot)
Переконайтеся, що в `.env` або `application.yaml` параметр `FIS_BASE_URL` вказує на `http://localhost:8000`.

```bash
cd springapp
./gradlew bootRun
```

---

## 🗄 База даних

Система використовує **Supabase** (PostgreSQL 16).
- Hibernate автоматично створить таблиці (`ddl-auto: update`).
- Для векторного пошуку використовується розширення `pgvector`.
- При першому запуску Spring Boot виконає `schema.sql`, щоб активувати розширення:
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;
  ```

---

## 📝 Основні API Ендпоінти

### Core Service (8080)
- `POST /api/v1/auth/register` — реєстрація
- `POST /api/v1/auth/login` — логін та отримання JWT
- `GET /api/v1/profiles` — профілі дітей
- `POST /api/v1/analysis/report` — генерація інтелектуального звіту

### Intelligence Service (8000)
- `POST /analyze` — нечіткий аналіз даних (захищено `INTERNAL_API_KEY`)
- `POST /embeddings` — генерація векторів для тексту

---

## 🤝 Розробка
Перед комітом переконайтеся, що код відформатований:
- Для Kotlin: `./gradlew ktlintCheck`
---

## 🛠 Troubleshooting (Вирішення проблем)

- **Backend не підключається до Intelligence**: При першому запуску `intelligence` завантажує ML-модель (~1GB). Бекенд може впасти з помилкою з'єднання. Просто зачекайте завершення завантаження та перезапустіть бекенд: `docker-compose restart backend`.
- **Помилка JWT**: Переконайтеся, що `JWT_SECRET` у файлі `.env` має довжину не менше 32 символів (256 біт).
- **Помилка "Extension 'vector' not found"**: Переконайтеся, що ваша база даних PostgreSQL підтримує `pgvector`. У Supabase це розширення вже встановлено, але його потрібно активувати (це робить `schema.sql`).
