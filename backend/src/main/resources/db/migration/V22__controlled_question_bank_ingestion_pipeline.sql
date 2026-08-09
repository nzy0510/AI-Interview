-- V22: Controlled machine supervision and idempotent human final review for document ingestion.

ALTER TABLE `question_bank_build_candidate`
  ADD COLUMN `machine_review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING/RUNNING/AUTO_PASS/NEEDS_HUMAN/AUTO_REJECT/FAILED/SKIPPED'
    AFTER `duplicate_hint`,
  ADD COLUMN `machine_review_score` DECIMAL(5,4) DEFAULT NULL AFTER `machine_review_status`,
  ADD COLUMN `machine_review_issues_json` JSON DEFAULT NULL AFTER `machine_review_score`,
  ADD COLUMN `machine_suggested_patch_json` JSON DEFAULT NULL AFTER `machine_review_issues_json`,
  ADD COLUMN `machine_review_prompt_version` VARCHAR(128) DEFAULT NULL AFTER `machine_suggested_patch_json`,
  ADD COLUMN `machine_review_attempts` INT NOT NULL DEFAULT 0 AFTER `machine_review_prompt_version`,
  ADD COLUMN `machine_reviewed_at` DATETIME DEFAULT NULL AFTER `machine_review_attempts`,
  ADD INDEX `idx_qb_candidate_machine_review` (`build_id`, `machine_review_status`);

UPDATE `question_bank_build_candidate`
SET `machine_review_status` = CASE
  WHEN `review_status` IN ('ACCEPTED', 'REJECTED') THEN 'SKIPPED'
  ELSE 'NEEDS_HUMAN'
END;

ALTER TABLE `question_bank_build`
  ADD COLUMN `auto_pass_count` INT NOT NULL DEFAULT 0 AFTER `rejected_count`,
  ADD COLUMN `needs_human_count` INT NOT NULL DEFAULT 0 AFTER `auto_pass_count`,
  ADD COLUMN `auto_reject_count` INT NOT NULL DEFAULT 0 AFTER `needs_human_count`,
  ADD COLUMN `review_revision` BIGINT NOT NULL DEFAULT 0
    COMMENT 'Monotonic candidate-review revision used by finalization CAS'
    AFTER `auto_reject_count`,
  ADD COLUMN `finalization_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED'
    COMMENT 'NOT_STARTED/IMPORTING/READY_TO_PUBLISH/COMPLETED/PARTIAL_FAILED/FAILED'
    AFTER `review_revision`,
  ADD COLUMN `final_import_batch_id` VARCHAR(191) DEFAULT NULL AFTER `finalization_status`,
  ADD COLUMN `final_atom_ids_json` JSON DEFAULT NULL AFTER `final_import_batch_id`,
  ADD COLUMN `finalization_result_json` JSON DEFAULT NULL AFTER `final_atom_ids_json`,
  ADD COLUMN `finalized_by` BIGINT DEFAULT NULL AFTER `finalization_result_json`,
  ADD COLUMN `finalized_at` DATETIME DEFAULT NULL AFTER `finalized_by`;

UPDATE `question_bank_build`
SET `stage` = 'READY_FOR_FINAL_REVIEW'
WHERE `status` = 'COMPLETED' AND `stage` = 'READY_FOR_REVIEW';

UPDATE `question_bank_build` b
SET `auto_pass_count` = (
      SELECT COUNT(*) FROM `question_bank_build_candidate` c
      WHERE c.`build_id` = b.`id` AND c.`machine_review_status` = 'AUTO_PASS'
    ),
    `needs_human_count` = (
      SELECT COUNT(*) FROM `question_bank_build_candidate` c
      WHERE c.`build_id` = b.`id` AND c.`machine_review_status` = 'NEEDS_HUMAN'
    ),
    `auto_reject_count` = (
      SELECT COUNT(*) FROM `question_bank_build_candidate` c
      WHERE c.`build_id` = b.`id` AND c.`machine_review_status` = 'AUTO_REJECT'
    );
