package com.interview.migration;

import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ControlledQuestionBankIngestionMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V22__controlled_question_bank_ingestion_pipeline.sql");
    private static final Path UNRESOLVED_COUNT_MIGRATION = Path.of(
            "src/main/resources/db/migration/V23__recompute_unresolved_question_bank_review_counts.sql");

    @Test
    void migrationShouldSeparateMachineReviewAndPersistIdempotentFinalization() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase().replace("`", "").replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("machine_review_status")
                .contains("machine_review_issues_json")
                .contains("machine_suggested_patch_json")
                .contains("finalization_status")
                .contains("final_import_batch_id")
                .contains("final_atom_ids_json")
                .contains("finalization_result_json")
                .contains("review_revision")
                .contains("when review_status in ('accepted', 'rejected') then 'skipped'")
                .contains("else 'needs_human'")
                .contains("set stage = 'ready_for_final_review'")
                .contains("update question_bank_build b")
                .contains("c.machine_review_status = 'needs_human'");
    }

    @Test
    void entitiesShouldExposeMachineReviewAndFinalizationFields() {
        assertFields(QuestionBankBuildCandidate.class,
                "machineReviewStatus",
                "machineReviewScore",
                "machineReviewIssuesJson",
                "machineSuggestedPatchJson",
                "machineReviewPromptVersion",
                "machineReviewAttempts",
                "machineReviewedAt");
        assertFields(QuestionBankBuild.class,
                "autoPassCount",
                "needsHumanCount",
                "autoRejectCount",
                "finalizationStatus",
                "finalImportBatchId",
                "finalAtomIdsJson",
                "finalizationResultJson",
                "reviewRevision",
                "finalizedBy",
                "finalizedAt");
    }

    @Test
    void unresolvedReviewCountMigrationShouldExcludeCompletedHumanDecisions() throws Exception {
        String sql = Files.readString(UNRESOLVED_COUNT_MIGRATION).toLowerCase().replace("`", "").replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("needs_human_count")
                .contains("machine_review_status = 'needs_human'")
                .contains("review_status = 'pending'");
    }

    private void assertFields(Class<?> entityClass, String... names) {
        Set<String> actual = Stream.of(entityClass.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertThat(actual).contains(names);
    }
}
