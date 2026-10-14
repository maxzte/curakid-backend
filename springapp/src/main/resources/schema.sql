CREATE EXTENSION IF NOT EXISTS vector;

-- Performance Indexes for high-frequency queries
CREATE INDEX IF NOT EXISTS idx_ai_reports_child_created
    ON ai_reports (child_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_measurements_child_date
    ON measurements (child_id, measurement_date DESC);

CREATE INDEX IF NOT EXISTS idx_token_blacklist_expiry
    ON token_blacklist (expiry_date);

CREATE INDEX IF NOT EXISTS idx_chat_messages_child_created
    ON chat_messages (child_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_category
    ON medical_knowledge (category_id) WHERE deleted_at IS NULL;
