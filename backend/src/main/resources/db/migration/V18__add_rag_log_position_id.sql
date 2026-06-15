ALTER TABLE rag_retrieval_log ADD COLUMN position_id BIGINT NULL AFTER position;
ALTER TABLE rag_retrieval_request_log ADD COLUMN position_id BIGINT NULL AFTER position;

CREATE INDEX idx_rag_log_position_id ON rag_retrieval_log(position_id);
CREATE INDEX idx_rag_req_log_position_id ON rag_retrieval_request_log(position_id);
