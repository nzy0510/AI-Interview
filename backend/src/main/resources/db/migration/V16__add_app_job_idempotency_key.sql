-- V16: Prevent duplicate async generation jobs for the same source file.

ALTER TABLE `app_job`
  ADD COLUMN `idempotency_key` VARCHAR(191) DEFAULT NULL COMMENT 'Stable key for idempotent job creation'
  AFTER `job_type`,
  ADD UNIQUE KEY `uk_app_job_idempotency_key` (`idempotency_key`);
