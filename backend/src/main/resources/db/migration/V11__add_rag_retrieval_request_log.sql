-- V11: Record every interview RAG retrieval request, including zero-hit attempts.

CREATE TABLE IF NOT EXISTS rag_retrieval_request_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL COMMENT 'Stable request identifier shared with hit rows',
  user_id BIGINT NOT NULL COMMENT 'User that owns the interview',
  record_id BIGINT NOT NULL COMMENT 'Interview record ID',
  turn_index INT NOT NULL COMMENT 'Conversation turn, starting at 1',
  position VARCHAR(64) DEFAULT NULL COMMENT 'Interview position',
  phase VARCHAR(32) DEFAULT NULL COMMENT 'Interview phase',
  query_text VARCHAR(500) DEFAULT NULL COMMENT 'Production retrieval query, access restricted',
  requested_limit INT NOT NULL DEFAULT 3 COMMENT 'Requested result limit',
  candidate_count INT NOT NULL DEFAULT 0 COMMENT 'Returned candidate count, including zero',
  retrieval_strategy VARCHAR(32) NOT NULL COMMENT 'QDRANT_VECTOR, MYSQL_FALLBACK, SKIPPED, or FAILED',
  latency_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Question-bank search latency in milliseconds',
  status VARCHAR(16) NOT NULL COMMENT 'SUCCESS or FAILED',
  error_message VARCHAR(500) DEFAULT NULL COMMENT 'Sanitized failure message',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rag_retrieval_request_id (request_id),
  INDEX idx_rag_request_record_turn (record_id, turn_index),
  INDEX idx_rag_request_position_time (position, create_time),
  INDEX idx_rag_request_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One row per interview RAG retrieval request';

ALTER TABLE rag_retrieval_log
  ADD COLUMN request_id VARCHAR(36) DEFAULT NULL COMMENT 'Links hit row to rag_retrieval_request_log',
  ADD INDEX idx_rag_retrieval_request_id (request_id);
