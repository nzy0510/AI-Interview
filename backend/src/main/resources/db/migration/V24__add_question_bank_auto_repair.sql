-- V24: Bounded candidate repair and re-supervision inside the controlled ingestion pipeline.

ALTER TABLE `question_bank_build_candidate`
  ADD COLUMN `repair_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_NEEDED'
    COMMENT 'NOT_NEEDED/PENDING/RUNNING/REPAIRED/VERIFIED/DROPPED/FAILED/EXHAUSTED'
    AFTER `machine_reviewed_at`,
  ADD COLUMN `repair_attempts` INT NOT NULL DEFAULT 0 AFTER `repair_status`,
  ADD COLUMN `repair_round` INT NOT NULL DEFAULT 0 AFTER `repair_attempts`,
  ADD COLUMN `repair_instruction` VARCHAR(500) DEFAULT NULL AFTER `repair_round`,
  ADD COLUMN `repair_prompt_version` VARCHAR(64) DEFAULT NULL AFTER `repair_instruction`,
  ADD COLUMN `repair_history_json` JSON DEFAULT NULL AFTER `repair_prompt_version`,
  ADD COLUMN `repaired_at` DATETIME DEFAULT NULL AFTER `repair_history_json`,
  ADD INDEX `idx_qb_candidate_repair` (`build_id`, `repair_status`);

UPDATE `question_bank_build_candidate`
SET `repair_status` = CASE
  WHEN `machine_review_status` = 'NEEDS_HUMAN' THEN 'EXHAUSTED'
  WHEN `machine_review_status` = 'AUTO_REJECT' THEN 'DROPPED'
  ELSE 'NOT_NEEDED'
END;

ALTER TABLE `question_bank_build`
  ADD COLUMN `llm_runtime_fingerprint` VARCHAR(64) DEFAULT NULL AFTER `llm_model`,
  ADD COLUMN `repair_round` INT NOT NULL DEFAULT 0 AFTER `auto_reject_count`,
  ADD COLUMN `repaired_count` INT NOT NULL DEFAULT 0 AFTER `repair_round`,
  ADD COLUMN `repair_failed_count` INT NOT NULL DEFAULT 0 AFTER `repaired_count`;

UPDATE `question_bank_build`
SET `repair_failed_count` = `needs_human_count`;
