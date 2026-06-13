package com.interview.migration;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.entity.AppJob;
import com.interview.entity.InterviewPosition;
import com.interview.entity.InterviewRecord;
import com.interview.entity.InterviewReport;
import com.interview.entity.InterviewReportItem;
import com.interview.entity.InterviewTurn;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IQB-01 migration contract")
class UserOwnedQuestionBankMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V14__user_owned_question_bank_scope.sql");

    @Test
    @DisplayName("V14 preserves account data, clears legacy interview data, and seeds scoped public positions")
    void migrationShouldCaptureDestructiveScopeAndPublicSeedContract() throws Exception {
        String sql = Files.readString(MIGRATION);
        String normalized = normalize(sql);
        String executableSql = normalize(stripLineComments(sql));

        assertThat(normalized)
                .contains("preserved tables")
                .contains("user_llm_config")
                .contains("resume_profile")
                .contains("user_feedback");
        assertNoDestructiveStatement(executableSql, "user");
        assertNoDestructiveStatement(executableSql, "user_llm_config");
        assertNoDestructiveStatement(executableSql, "resume_profile");
        assertNoDestructiveStatement(executableSql, "user_feedback");

        assertThat(executableSql)
                .contains("delete from rag_retrieval_log")
                .contains("delete from rag_retrieval_request_log")
                .contains("delete from interview_record");

        assertThat(normalized)
                .contains("java 后端开发")
                .contains("web 前端开发")
                .contains("ai 大模型应用开发")
                .contains("where not exists")
                .contains("scope = 'public'")
                .contains("publication_status");
    }

    @Test
    @DisplayName("V14 creates the first user-owned question-bank tables and fields")
    void migrationShouldCreateUserOwnedTablesAndColumns() throws Exception {
        String normalized = normalize(Files.readString(MIGRATION));

        assertThat(normalized)
                .contains("create table if not exists interview_position")
                .contains("create table if not exists knowledge_base")
                .contains("create table if not exists knowledge_source_file")
                .contains("create table if not exists app_job")
                .contains("create table if not exists interview_turn")
                .contains("create table if not exists interview_report")
                .contains("create table if not exists interview_report_item");

        assertThat(normalized)
                .contains("add column role")
                .contains("add column admin_granted_by")
                .contains("add column admin_granted_at")
                .contains("add column position_id")
                .contains("add column owner_user_id")
                .contains("add column knowledge_base_id")
                .contains("add column source_file_id")
                .contains("add column current_version_no")
                .contains("add column review_status")
                .contains("add column publication_status");
    }

    @Test
    @DisplayName("entities expose the IQB-01 table and field surface")
    void entitiesShouldExposeUserOwnedQuestionBankFields() {
        assertTableName(InterviewPosition.class, "interview_position");
        assertTableName(KnowledgeBase.class, "knowledge_base");
        assertTableName(KnowledgeSourceFile.class, "knowledge_source_file");
        assertTableName(AppJob.class, "app_job");
        assertTableName(InterviewTurn.class, "interview_turn");
        assertTableName(InterviewReport.class, "interview_report");
        assertTableName(InterviewReportItem.class, "interview_report_item");

        assertFields(User.class, "role", "adminGrantedBy", "adminGrantedAt");
        assertFields(InterviewRecord.class, "positionId");
        assertFields(KnowledgeAtom.class,
                "scope",
                "ownerUserId",
                "positionId",
                "knowledgeBaseId",
                "sourceFileId",
                "currentVersionNo",
                "reviewStatus",
                "reviewReason",
                "reviewConfidence",
                "suggestedPatchJson",
                "publicationStatus",
                "publishedBy",
                "publishedAt",
                "reviewedBy",
                "reviewedAt",
                "vectorErrorMessage");
    }

    private void assertTableName(Class<?> entityClass, String tableName) {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        assertThat(annotation).as(entityClass.getSimpleName() + " @TableName").isNotNull();
        assertThat(annotation.value()).isEqualTo(tableName);
    }

    private void assertFields(Class<?> entityClass, String... names) {
        Set<String> actual = Stream.of(entityClass.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertThat(actual).contains(names);
    }

    private String normalize(String value) {
        return value.toLowerCase()
                .replace("`", "")
                .replaceAll("\\s+", " ");
    }

    private String stripLineComments(String value) {
        return value.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private void assertNoDestructiveStatement(String sql, String tableName) {
        String quoted = "`?" + Pattern.quote(tableName) + "`?";
        Pattern pattern = Pattern.compile(
                "\\b("
                        + "delete\\s+from\\s+" + quoted
                        + "|delete\\s+\\w+\\s+from\\s+" + quoted + "\\s+\\w+"
                        + "|truncate\\s+(?:table\\s+)?" + quoted
                        + "|drop\\s+table\\s+(?:if\\s+exists\\s+)?" + quoted
                        + ")\\b",
                Pattern.CASE_INSENSITIVE);
        assertThat(pattern.matcher(sql).find())
                .as("No destructive statement should target " + tableName)
                .isFalse();
    }
}
