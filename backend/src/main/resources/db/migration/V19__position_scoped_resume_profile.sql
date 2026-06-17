-- V19: 岗位隔离简历画像
-- 1. resume_profile 增加 position_id，改为 (user_id, position_id) 唯一约束
-- 2. interview_record 增加 resume_profile_id 用于记录面试启动时的画像引用

ALTER TABLE resume_profile
    DROP INDEX uk_user,
    ADD COLUMN position_id BIGINT DEFAULT NULL COMMENT '结构化岗位 ID',
    ADD UNIQUE KEY uk_user_position (user_id, position_id);

ALTER TABLE interview_record
    ADD COLUMN resume_profile_id BIGINT DEFAULT NULL COMMENT '面试启动时使用的简历画像 ID';
