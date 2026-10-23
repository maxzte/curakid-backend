-- Етап 0: Підготовка необхідних розширень PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- Всі таблиці та зв'язки генеруються і синхронізуються Hibernate автоматично
-- з Kotlin-сутностей (com.misterio.curakid.core.database.*) завдяки:
-- spring.jpa.hibernate.ddl-auto: update
