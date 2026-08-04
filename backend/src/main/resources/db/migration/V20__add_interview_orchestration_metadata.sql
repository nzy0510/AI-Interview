ALTER TABLE `interview_turn`
  ADD COLUMN `orchestration_mode` VARCHAR(32) DEFAULT NULL COMMENT 'AGENT/RULE/RULE_FALLBACK' AFTER `retrieval_strategy`,
  ADD COLUMN `decision_action` VARCHAR(32) DEFAULT NULL COMMENT 'Bounded next-turn action' AFTER `orchestration_mode`,
  ADD COLUMN `decision_json` JSON DEFAULT NULL COMMENT 'Sanitized tool and decision metadata without chain-of-thought' AFTER `decision_action`;
