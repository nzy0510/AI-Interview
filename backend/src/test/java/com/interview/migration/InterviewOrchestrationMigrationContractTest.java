package com.interview.migration;

import com.interview.entity.InterviewTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("面试编排元数据迁移契约")
class InterviewOrchestrationMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20__add_interview_orchestration_metadata.sql");

    @Test
    @DisplayName("V20 只为结构化轮次增加可空审计字段")
    void migrationShouldOnlyAddNullableMetadataColumns() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase().replace("`", "")
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("alter table interview_turn")
                .contains("add column orchestration_mode")
                .contains("add column decision_action")
                .contains("add column decision_json")
                .doesNotContain(" drop ")
                .doesNotContain(" delete ")
                .doesNotContain(" truncate ");
    }

    @Test
    @DisplayName("InterviewTurn 暴露编排审计字段")
    void entityShouldExposeOrchestrationMetadata() {
        Set<String> fields = Stream.of(InterviewTurn.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fields).contains("orchestrationMode", "decisionAction", "decisionJson");
    }
}
