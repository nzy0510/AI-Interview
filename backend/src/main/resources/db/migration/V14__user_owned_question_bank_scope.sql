-- V14: User-owned question bank scope model and destructive interview-data reset.
-- Preserved tables: user, user_llm_config, resume_profile, user_feedback.
-- Legacy interview/report data and interview-tied RAG logs are intentionally cleared below.

ALTER TABLE `user`
  ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  ADD COLUMN `admin_granted_by` BIGINT DEFAULT NULL COMMENT 'Admin user that granted ADMIN role; null for bootstrap',
  ADD COLUMN `admin_granted_at` DATETIME DEFAULT NULL COMMENT 'Time when ADMIN role was granted',
  ADD INDEX `idx_user_role` (`role`);

UPDATE `user`
SET `role` = 'ADMIN',
    `admin_granted_at` = COALESCE(`admin_granted_at`, CURRENT_TIMESTAMP)
WHERE `username` = 'nzy333'
  AND `email` = '1525764737@qq.com';

CREATE TABLE IF NOT EXISTS `interview_position` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `scope` VARCHAR(16) NOT NULL COMMENT 'PUBLIC/PRIVATE',
  `owner_user_id` BIGINT DEFAULT NULL COMMENT 'Owner for PRIVATE positions; null for PUBLIC',
  `name` VARCHAR(128) NOT NULL COMMENT 'Position name',
  `description` VARCHAR(500) DEFAULT NULL COMMENT 'Position description',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  `default_knowledge_base_id` BIGINT DEFAULT NULL COMMENT 'Default knowledge base for first version',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Creating user/admin',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_position_scope_owner` (`scope`, `owner_user_id`),
  INDEX `idx_position_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Public/private interview positions';

CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `scope` VARCHAR(16) NOT NULL COMMENT 'PUBLIC/PRIVATE',
  `owner_user_id` BIGINT DEFAULT NULL COMMENT 'Owner for PRIVATE knowledge bases; null for PUBLIC',
  `position_id` BIGINT NOT NULL COMMENT 'Owning interview_position',
  `name` VARCHAR(128) NOT NULL COMMENT 'Knowledge base name',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Creating user/admin',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_kb_position` (`position_id`),
  INDEX `idx_kb_scope_owner` (`scope`, `owner_user_id`),
  INDEX `idx_kb_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Position knowledge bases';

INSERT INTO `interview_position` (`scope`, `owner_user_id`, `name`, `description`, `status`, `created_by`)
SELECT 'PUBLIC', NULL, 'Java 后端开发', 'Built-in public Java backend interview position', 'ACTIVE', NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `interview_position` WHERE `scope` = 'PUBLIC' AND `name` = 'Java 后端开发'
);

INSERT INTO `interview_position` (`scope`, `owner_user_id`, `name`, `description`, `status`, `created_by`)
SELECT 'PUBLIC', NULL, 'Web 前端开发', 'Built-in public web frontend interview position', 'ACTIVE', NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `interview_position` WHERE `scope` = 'PUBLIC' AND `name` = 'Web 前端开发'
);

INSERT INTO `interview_position` (`scope`, `owner_user_id`, `name`, `description`, `status`, `created_by`)
SELECT 'PUBLIC', NULL, 'AI 大模型应用开发', 'Built-in public AI application interview position', 'ACTIVE', NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `interview_position` WHERE `scope` = 'PUBLIC' AND `name` = 'AI 大模型应用开发'
);

INSERT INTO `knowledge_base` (`scope`, `owner_user_id`, `position_id`, `name`, `status`, `created_by`)
SELECT `scope`, `owner_user_id`, `id`, CONCAT(`name`, ' 默认知识库'), 'ACTIVE', NULL
FROM `interview_position`
WHERE `scope` = 'PUBLIC'
  AND `name` IN ('Java 后端开发', 'Web 前端开发', 'AI 大模型应用开发')
  AND NOT EXISTS (
    SELECT 1
    FROM `knowledge_base` existing
    WHERE existing.`scope` = 'PUBLIC'
      AND existing.`position_id` = `interview_position`.`id`
      AND existing.`name` = CONCAT(`interview_position`.`name`, ' 默认知识库')
  );

UPDATE `interview_position` p
JOIN `knowledge_base` kb ON kb.`position_id` = p.`id`
SET p.`default_knowledge_base_id` = kb.`id`
WHERE p.`scope` = 'PUBLIC';

CREATE TABLE IF NOT EXISTS `knowledge_source_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `scope` VARCHAR(16) NOT NULL COMMENT 'PUBLIC/PRIVATE',
  `owner_user_id` BIGINT DEFAULT NULL COMMENT 'Owner for PRIVATE source files; null for PUBLIC',
  `position_id` BIGINT NOT NULL COMMENT 'Owning interview_position',
  `knowledge_base_id` BIGINT NOT NULL COMMENT 'Owning knowledge_base',
  `original_filename` VARCHAR(255) NOT NULL COMMENT 'Original uploaded filename',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT 'Original file content type',
  `file_size` BIGINT DEFAULT NULL COMMENT 'Original file size in bytes',
  `file_hash` VARCHAR(128) DEFAULT NULL COMMENT 'Content hash for dedupe/audit',
  `storage_key` VARCHAR(500) DEFAULT NULL COMMENT 'Original file storage key',
  `markdown_storage_key` VARCHAR(500) DEFAULT NULL COMMENT 'Converted markdown storage key',
  `domain_tags_json` JSON DEFAULT NULL COMMENT 'Domain tags as JSON array',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/CONVERTING/CONVERTED/FAILED/ARCHIVED',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized conversion/import error',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Creating user/admin',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_source_position_kb` (`position_id`, `knowledge_base_id`),
  INDEX `idx_source_scope_owner` (`scope`, `owner_user_id`),
  INDEX `idx_source_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Knowledge base source files';

CREATE TABLE IF NOT EXISTS `app_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `job_type` VARCHAR(64) NOT NULL COMMENT 'IMPORT_FILE/REGENERATE_ATOMS/REVIEW_ATOMS/PUBLISH_ATOMS/REINDEX_POSITION/GENERATE_REPORT',
  `scope` VARCHAR(16) DEFAULT NULL COMMENT 'PUBLIC/PRIVATE when applicable',
  `owner_user_id` BIGINT DEFAULT NULL COMMENT 'Owner for PRIVATE jobs',
  `position_id` BIGINT DEFAULT NULL COMMENT 'Related interview_position',
  `knowledge_base_id` BIGINT DEFAULT NULL COMMENT 'Related knowledge_base',
  `source_file_id` BIGINT DEFAULT NULL COMMENT 'Related knowledge_source_file',
  `record_id` BIGINT DEFAULT NULL COMMENT 'Related interview_record',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/FAILED/COMPLETED',
  `stage` VARCHAR(64) DEFAULT NULL COMMENT 'Current job stage',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '0-100 progress',
  `payload_json` JSON DEFAULT NULL COMMENT 'Job input payload',
  `result_json` JSON DEFAULT NULL COMMENT 'Job result payload',
  `failed_stage` VARCHAR(64) DEFAULT NULL COMMENT 'Stage where failure occurred',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized failure message',
  `retryable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether user-triggered retry is allowed',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Number of retries already requested',
  `claimed_by` VARCHAR(128) DEFAULT NULL COMMENT 'Worker that claimed this job',
  `locked_until` DATETIME DEFAULT NULL COMMENT 'Job lock expiry',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Creating user/admin',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_job_status_lock` (`status`, `locked_until`),
  INDEX `idx_job_owner_status` (`owner_user_id`, `status`),
  INDEX `idx_job_position` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Unified async job table';

ALTER TABLE `knowledge_atom`
  ADD COLUMN `scope` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE',
  ADD COLUMN `owner_user_id` BIGINT DEFAULT NULL COMMENT 'Owner for PRIVATE atoms; null for PUBLIC',
  ADD COLUMN `position_id` BIGINT DEFAULT NULL COMMENT 'Owning interview_position',
  ADD COLUMN `knowledge_base_id` BIGINT DEFAULT NULL COMMENT 'Owning knowledge_base',
  ADD COLUMN `source_file_id` BIGINT DEFAULT NULL COMMENT 'Owning knowledge_source_file',
  ADD COLUMN `current_version_no` INT NOT NULL DEFAULT 1 COMMENT 'Current searchable version number',
  ADD COLUMN `review_status` VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED' COMMENT 'PASS/NEEDS_REVIEW/REJECT/UNREVIEWED',
  ADD COLUMN `review_reason` VARCHAR(1000) DEFAULT NULL COMMENT 'LLM/manual review reason',
  ADD COLUMN `review_confidence` DOUBLE DEFAULT NULL COMMENT 'Review confidence score',
  ADD COLUMN `suggested_patch_json` JSON DEFAULT NULL COMMENT 'Suggested patch from review',
  ADD COLUMN `publication_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  ADD COLUMN `published_by` BIGINT DEFAULT NULL COMMENT 'Publishing user/admin',
  ADD COLUMN `published_at` DATETIME DEFAULT NULL COMMENT 'Publication time',
  ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL COMMENT 'Reviewing user/admin',
  ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT 'Review time',
  ADD COLUMN `vector_error_message` VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized vector sync error',
  ADD INDEX `idx_atom_scope_position` (`scope`, `position_id`, `publication_status`, `vector_status`),
  ADD INDEX `idx_atom_owner_position` (`owner_user_id`, `position_id`),
  ADD INDEX `idx_atom_kb_source` (`knowledge_base_id`, `source_file_id`);

UPDATE `knowledge_atom` ka
JOIN `interview_position` p
  ON p.`scope` = 'PUBLIC'
 AND p.`name` = CASE
      WHEN ka.`category` IN ('React', 'Vue', 'Flutter', 'HTML', 'CSS', 'JavaScript', 'NodeJS', 'Webpack', '浏览器', '前端工程化', 'TypeScript', '性能优化') THEN 'Web 前端开发'
      WHEN ka.`category` IN ('AI大模型', 'AI 大模型', '大模型', 'LLM', 'RAG') THEN 'AI 大模型应用开发'
      ELSE 'Java 后端开发'
    END
JOIN `knowledge_base` kb
  ON kb.`scope` = 'PUBLIC'
 AND kb.`position_id` = p.`id`
SET ka.`scope` = 'PUBLIC',
    ka.`owner_user_id` = NULL,
    ka.`position_id` = p.`id`,
    ka.`knowledge_base_id` = kb.`id`,
    ka.`current_version_no` = COALESCE((
      SELECT MAX(kav.`version_no`)
      FROM `knowledge_atom_version` kav
      WHERE kav.`atom_id` = ka.`atom_id`
    ), 1),
    ka.`review_status` = CASE WHEN ka.`status` = 'PUBLISHED' THEN 'PASS' ELSE 'UNREVIEWED' END,
    ka.`publication_status` = CASE
      WHEN ka.`status` = 'PUBLISHED' THEN 'PUBLISHED'
      WHEN ka.`status` = 'ARCHIVED' THEN 'ARCHIVED'
      ELSE 'DRAFT'
    END,
    ka.`published_at` = CASE WHEN ka.`status` = 'PUBLISHED' THEN COALESCE(ka.`last_indexed_at`, ka.`update_time`, ka.`create_time`) ELSE NULL END;

ALTER TABLE `interview_record`
  ADD COLUMN `position_id` BIGINT DEFAULT NULL COMMENT 'Selected interview_position for new structured interviews',
  ADD INDEX `idx_interview_record_position` (`position_id`);

DELETE FROM `rag_retrieval_log`;
DELETE FROM `rag_retrieval_request_log`;
DELETE FROM `interview_record`;

CREATE TABLE IF NOT EXISTS `interview_turn` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL COMMENT 'Owning interview_record',
  `user_id` BIGINT NOT NULL COMMENT 'Owning user',
  `position_id` BIGINT DEFAULT NULL COMMENT 'Selected interview_position',
  `turn_index` INT NOT NULL COMMENT 'Turn index starting at 1',
  `phase` VARCHAR(32) NOT NULL COMMENT 'OPENING/TECHNICAL/HR/CLOSING',
  `ai_question` LONGTEXT DEFAULT NULL COMMENT 'AI interviewer question',
  `user_answer` LONGTEXT DEFAULT NULL COMMENT 'User answer',
  `retrieved_atom_ids` JSON DEFAULT NULL COMMENT 'Retrieved atom ids for this turn',
  `context_snapshot_json` JSON DEFAULT NULL COMMENT 'Prompt context atom snapshots',
  `retrieval_strategy` VARCHAR(64) DEFAULT NULL COMMENT 'Qdrant retrieval strategy metadata',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_turn_record_index` (`record_id`, `turn_index`),
  INDEX `idx_turn_user_record` (`user_id`, `record_id`),
  INDEX `idx_turn_position` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Structured interview turns';

CREATE TABLE IF NOT EXISTS `interview_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL COMMENT 'Owning interview_record',
  `user_id` BIGINT NOT NULL COMMENT 'Owning user',
  `position_id` BIGINT DEFAULT NULL COMMENT 'Selected interview_position',
  `job_id` BIGINT DEFAULT NULL COMMENT 'GENERATE_REPORT app_job',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/FAILED/COMPLETED',
  `overall_score` INT DEFAULT NULL COMMENT 'Overall score, 0-100',
  `summary` LONGTEXT DEFAULT NULL COMMENT 'Report summary',
  `ability_json` JSON DEFAULT NULL COMMENT 'Ability dimension scores',
  `recommendation_json` JSON DEFAULT NULL COMMENT 'Improvement recommendations',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized report failure message',
  `model_provider` VARCHAR(64) DEFAULT NULL COMMENT 'Provider used for generation',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT 'Model used for generation',
  `generated_at` DATETIME DEFAULT NULL COMMENT 'Generation completion time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_report_record` (`record_id`),
  INDEX `idx_report_user_status` (`user_id`, `status`),
  INDEX `idx_report_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Asynchronous interview reports';

CREATE TABLE IF NOT EXISTS `interview_report_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `report_id` BIGINT NOT NULL COMMENT 'Owning interview_report',
  `record_id` BIGINT NOT NULL COMMENT 'Owning interview_record',
  `turn_id` BIGINT DEFAULT NULL COMMENT 'Source interview_turn',
  `item_index` INT NOT NULL COMMENT 'Report item index starting at 1',
  `phase` VARCHAR(32) NOT NULL COMMENT 'TECHNICAL/HR',
  `question` LONGTEXT NOT NULL COMMENT 'Question snapshot',
  `user_answer` LONGTEXT DEFAULT NULL COMMENT 'User answer snapshot',
  `score` DECIMAL(5,2) DEFAULT NULL COMMENT 'Per-question score, 0-10',
  `reference_answer` LONGTEXT DEFAULT NULL COMMENT 'Reference answer or direction',
  `improvement_suggestion` LONGTEXT DEFAULT NULL COMMENT 'Improvement suggestion',
  `answer_source` VARCHAR(32) DEFAULT NULL COMMENT 'KNOWLEDGE_BASE/AI_GENERATED/HR_GUIDE',
  `matched_atom_snapshot_json` JSON DEFAULT NULL COMMENT 'Matched atom snapshot for history stability',
  `model_provider` VARCHAR(64) DEFAULT NULL COMMENT 'Provider used for generation',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT 'Model used for generation',
  `generated_time` DATETIME DEFAULT NULL COMMENT 'Item generation time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_report_item_index` (`report_id`, `item_index`),
  INDEX `idx_report_item_record` (`record_id`),
  INDEX `idx_report_item_turn` (`turn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Per-question interview report items';
