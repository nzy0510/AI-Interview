-- IQB-01 destructive cleanup boundary:
-- This migration intentionally clears interview/report/RAG-derived history for the
-- current MVP data reset. It must not delete accounts, user LLM Providers, resumes,
-- feedback, or question-bank assets.
DELETE FROM `interview_report_item`;
DELETE FROM `interview_report`;
DELETE FROM `interview_turn`;
DELETE FROM `app_job` WHERE `job_type` = 'GENERATE_REPORT';
DELETE FROM `rag_retrieval_log`;
DELETE FROM `rag_retrieval_request_log`;
DELETE FROM `interview_record`;
