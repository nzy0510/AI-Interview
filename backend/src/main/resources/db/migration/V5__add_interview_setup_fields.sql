-- V5: Persist interview setup selections from the preparation workspace

ALTER TABLE interview_record
  ADD COLUMN difficulty_level VARCHAR(16) DEFAULT 'mid' COMMENT '准备页难度倾向: junior/mid/senior/principal',
  ADD COLUMN focus_areas JSON DEFAULT NULL COMMENT '准备页重点能力 JSON 数组';
