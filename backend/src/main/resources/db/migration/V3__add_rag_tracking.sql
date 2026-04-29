-- V3: RAG retrieval tracking for knowledge domain analysis
-- Adds used_atom_ids to interview_record and creates rag_retrieval_log

ALTER TABLE interview_record
  ADD COLUMN used_atom_ids JSON DEFAULT NULL COMMENT '面试中 RAG 命中的知识原子ID列表，去重';

CREATE TABLE IF NOT EXISTS rag_retrieval_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '用户ID',
  record_id BIGINT NOT NULL COMMENT '面试记录ID',
  turn_index INT NOT NULL COMMENT '对话轮次(从1开始)',
  query_text VARCHAR(500) DEFAULT NULL COMMENT '检索时使用的query文本(用户回答)',
  position VARCHAR(64) DEFAULT NULL COMMENT '面试岗位',
  retrieved_atom_id VARCHAR(128) NOT NULL COMMENT '命中的知识原子ID',
  retrieved_category VARCHAR(64) DEFAULT NULL COMMENT '命中的知识原子分类',
  similarity_score DOUBLE DEFAULT NULL COMMENT '向量相似度分数',
  rank_index INT NOT NULL DEFAULT 1 COMMENT '召回排名(1=top1)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检索时间',
  INDEX idx_record_id (record_id),
  INDEX idx_user_id (user_id),
  INDEX idx_retrieved_category (retrieved_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG检索日志表';
