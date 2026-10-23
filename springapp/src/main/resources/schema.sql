CREATE EXTENSION IF NOT EXISTS vector;

-- Performance Indexes for high-frequency queries
CREATE INDEX IF NOT EXISTS idx_ai_reports_child_created
    ON ai_reports (child_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_measurements_child_date
    ON measurements (child_id, measurement_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_child_created
    ON chat_messages (child_id, created_at ASC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_category
    ON medical_knowledge (category_id)
    WHERE deleted_at IS NULL;

-- User Consent Index
CREATE INDEX IF NOT EXISTS idx_user_consents_user_active
    ON user_consents (user_id)
    WHERE revoked_at IS NULL;

-- Event Scheduling & Calendar Query Indexes
CREATE INDEX IF NOT EXISTS idx_events_pending_reminders
    ON events (status, reminder_sent, date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_events_child_date
    ON events (child_id, date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_events_template_id
    ON events (template_id)
    WHERE template_id IS NOT NULL AND deleted_at IS NULL;

-- Soft-delete Safe Unique Constraints
CREATE UNIQUE INDEX IF NOT EXISTS uq_life_periods_code_active
    ON life_periods (code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_legal_documents_url_active
    ON legal_documents (url)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_period_summaries_child_period
    ON period_summaries (child_id, life_period_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_oauth_providers_provider_user
    ON oauth_providers (provider_name, provider_user_id)
    WHERE deleted_at IS NULL;

-- Vector Similarity Index (HNSW) for fast RAG search
CREATE INDEX IF NOT EXISTS idx_medical_knowledge_embedding_hnsw
    ON medical_knowledge USING hnsw (embedding vector_cosine_ops);

-- Full-Text Search GIN Indexes for Hybrid Search (English + Simple/Ukrainian)
CREATE INDEX IF NOT EXISTS idx_medical_knowledge_fts_en
    ON medical_knowledge USING gin (to_tsvector('english', title || ' ' || content))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_fts_simple
    ON medical_knowledge USING gin (to_tsvector('simple', title || ' ' || content))
    WHERE deleted_at IS NULL;
