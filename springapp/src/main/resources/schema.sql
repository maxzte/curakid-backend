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
