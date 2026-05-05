-- V6: Database-backed question bank with import/review tracking.

CREATE TABLE IF NOT EXISTS knowledge_atom (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  atom_id VARCHAR(128) NOT NULL COMMENT 'Stable public atom id',
  subject VARCHAR(255) NOT NULL COMMENT 'Interview topic',
  category VARCHAR(64) NOT NULL COMMENT 'Question bank category',
  difficulty VARCHAR(32) DEFAULT NULL COMMENT 'Difficulty label',
  tags_json JSON DEFAULT NULL COMMENT 'Tags as JSON array',
  principles LONGTEXT NOT NULL COMMENT 'Core principles / reference answer',
  pitfalls LONGTEXT DEFAULT NULL COMMENT 'Common pitfalls',
  follow_up_paths_json JSON DEFAULT NULL COMMENT 'Follow-up questions as JSON array',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  source_ref VARCHAR(255) DEFAULT NULL COMMENT 'Source document or import batch',
  checksum VARCHAR(64) NOT NULL COMMENT 'Content checksum for sync detection',
  vector_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SYNCED/FAILED',
  last_indexed_at DATETIME DEFAULT NULL COMMENT 'Last Qdrant sync time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_atom_id (atom_id),
  INDEX idx_category_status (category, status),
  INDEX idx_status_vector (status, vector_status),
  INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Knowledge atom question bank';

CREATE TABLE IF NOT EXISTS knowledge_atom_version (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  atom_id VARCHAR(128) NOT NULL COMMENT 'Stable public atom id',
  version_no INT NOT NULL COMMENT 'Version number starting from 1',
  snapshot_json LONGTEXT NOT NULL COMMENT 'Full atom snapshot',
  change_reason VARCHAR(255) DEFAULT NULL COMMENT 'Import/review reason',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_atom_version (atom_id, version_no),
  INDEX idx_atom_id (atom_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Knowledge atom version history';

CREATE TABLE IF NOT EXISTS knowledge_atom_import_batch (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL COMMENT 'External batch id',
  source_ref VARCHAR(255) DEFAULT NULL COMMENT 'Source path/document',
  target_category VARCHAR(64) DEFAULT NULL COMMENT 'Default category',
  mode VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRY_RUN/DRAFT/AUTO_PUBLISH',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/IMPORTED/FAILED',
  atom_count INT NOT NULL DEFAULT 0,
  validation_report LONGTEXT DEFAULT NULL,
  review_report LONGTEXT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_batch_id (batch_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Question bank import batches';

CREATE TABLE IF NOT EXISTS knowledge_atom_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id VARCHAR(64) DEFAULT NULL,
  atom_id VARCHAR(128) NOT NULL,
  verdict VARCHAR(32) NOT NULL COMMENT 'PASS/NEEDS_REVISION/REJECT',
  score INT DEFAULT NULL,
  issues_json JSON DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_batch_id (batch_id),
  INDEX idx_atom_id (atom_id),
  INDEX idx_verdict (verdict)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Knowledge atom review results';
