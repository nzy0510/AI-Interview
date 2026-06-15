-- V15: Persist source evidence returned by private question-bank atom generation.

ALTER TABLE `knowledge_atom`
  ADD COLUMN `source_evidence_json` JSON DEFAULT NULL COMMENT 'Source snippets or references used to justify the atom'
  AFTER `suggested_patch_json`;

UPDATE `knowledge_atom`
SET `source_evidence_json` = JSON_ARRAY(
  CONCAT('历史来源：', COALESCE(NULLIF(`source_ref`, ''), `atom_id`, '知识原子'))
)
WHERE `source_evidence_json` IS NULL;

UPDATE `knowledge_atom`
SET `review_confidence` = 1.0
WHERE `review_confidence` IS NULL
  AND `review_status` = 'PASS';
