ALTER TABLE rag_retrieval_log
  ADD COLUMN context_selected TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'Whether this candidate was included in the prompt context',
  ADD INDEX idx_rag_context_selected (user_id, context_selected, retrieved_category);
