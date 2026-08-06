-- V21: In-app private question-bank build candidates and source ownership.

CREATE TABLE IF NOT EXISTS `question_bank_build` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `scope` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT 'Only PRIVATE is enabled in v1',
  `owner_user_id` BIGINT NOT NULL,
  `position_id` BIGINT NOT NULL,
  `knowledge_base_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED/DELETING',
  `stage` VARCHAR(64) DEFAULT NULL,
  `progress` INT NOT NULL DEFAULT 0,
  `categories_json` JSON DEFAULT NULL,
  `llm_config_id` BIGINT DEFAULT NULL COMMENT 'Snapshot id; API key is never stored here',
  `llm_provider` VARCHAR(64) DEFAULT NULL,
  `llm_model` VARCHAR(128) DEFAULT NULL,
  `prompt_version` VARCHAR(128) DEFAULT NULL,
  `chunk_count` INT NOT NULL DEFAULT 0,
  `completed_chunk_count` INT NOT NULL DEFAULT 0,
  `checkpoint_json` JSON DEFAULT NULL COMMENT 'Completed chunk indexes for idempotent retry',
  `candidate_count` INT NOT NULL DEFAULT 0,
  `accepted_count` INT NOT NULL DEFAULT 0,
  `rejected_count` INT NOT NULL DEFAULT 0,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_qb_build_owner_kb` (`owner_user_id`, `knowledge_base_id`, `create_time`),
  INDEX `idx_qb_build_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Private question-bank document build batches';

CREATE TABLE IF NOT EXISTS `question_bank_build_candidate` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `build_id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `position_id` BIGINT NOT NULL,
  `knowledge_base_id` BIGINT NOT NULL,
  `source_file_id` BIGINT DEFAULT NULL,
  `chunk_index` INT NOT NULL,
  `stable_atom_id` VARCHAR(191) NOT NULL,
  `source_ref` VARCHAR(500) DEFAULT NULL,
  `subject` VARCHAR(500) NOT NULL,
  `category` VARCHAR(128) NOT NULL,
  `difficulty` VARCHAR(32) NOT NULL,
  `tags_json` JSON DEFAULT NULL,
  `principles` LONGTEXT NOT NULL,
  `pitfalls` LONGTEXT DEFAULT NULL,
  `follow_up_paths_json` JSON DEFAULT NULL,
  `source_evidence_json` JSON DEFAULT NULL,
  `self_check_json` JSON DEFAULT NULL,
  `duplicate_hint` VARCHAR(1000) DEFAULT NULL,
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  `review_reason` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_qb_candidate_stable` (`build_id`, `stable_atom_id`),
  INDEX `idx_qb_candidate_build_review` (`build_id`, `review_status`),
  INDEX `idx_qb_candidate_scope` (`owner_user_id`, `knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Reviewable generated question-bank candidates';

ALTER TABLE `knowledge_source_file`
  ADD COLUMN `build_id` BIGINT DEFAULT NULL COMMENT 'Owning question_bank_build',
  ADD INDEX `idx_source_build` (`build_id`);

ALTER TABLE `app_job`
  ADD COLUMN `build_id` BIGINT DEFAULT NULL COMMENT 'Related question_bank_build',
  ADD INDEX `idx_job_build` (`build_id`);
