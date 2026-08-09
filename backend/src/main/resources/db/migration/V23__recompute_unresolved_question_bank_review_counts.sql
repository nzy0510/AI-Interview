UPDATE question_bank_build b
SET b.needs_human_count = (
    SELECT COUNT(*)
    FROM question_bank_build_candidate c
    WHERE c.build_id = b.id
      AND c.machine_review_status = 'NEEDS_HUMAN'
      AND c.review_status = 'PENDING'
);
