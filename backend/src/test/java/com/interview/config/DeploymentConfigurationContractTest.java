package com.interview.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Multilingual embedding deployment configuration")
class DeploymentConfigurationContractTest {

    @Test
    @DisplayName("uses the multilingual E5 collection and vector size in production")
    void shouldUseMultilingualE5DefaultsInProduction() throws IOException {
        assertDefaults(Path.of("..", ".env.prod.example"));
    }

    @Test
    @DisplayName("keeps bare Java defaults compatible with the local AllMiniLM model")
    void shouldKeepBareJavaDefaultsCompatible() throws IOException {
        assertLocalDefaults(Path.of("src", "main", "resources", "application.yml"));
        assertLocalDefaults(Path.of("src", "main", "resources", "application.yml.example"));
    }

    @Test
    @DisplayName("passes external service timeout settings into every Compose backend")
    void shouldPassTimeoutsThroughCompose() throws IOException {
        assertTimeoutPassThrough(Path.of("..", "docker-compose.example.yml"));
        assertTimeoutPassThrough(Path.of("..", "docker-compose.prod.yml"));
    }

    @Test
    @DisplayName("比赛功能开关在运行配置与部署示例中保持一致")
    void shouldKeepCompetitionFeatureFlagsAligned() throws IOException {
        assertCompetitionFeatureFlags(Path.of("src", "main", "resources", "application.yml"));
        assertCompetitionFeatureFlags(Path.of("src", "main", "resources", "application.yml.example"));
        assertCompetitionFeatureFlags(Path.of("..", "docker-compose.example.yml"));
        assertCompetitionFeatureFlags(Path.of("..", "docker-compose.prod.yml"));
    }

    @Test
    @DisplayName("Agent 单轮规划预算默认保持为 35 秒")
    void shouldKeepAgentPlanningTimeoutBudgetAligned() throws IOException {
        assertThat(new InterviewAgentProperties().getPlanningTimeoutSeconds()).isEqualTo(35);
        assertAgentPlanningTimeout(Path.of("src", "main", "resources", "application.yml"),
                "APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS:35");
        assertAgentPlanningTimeout(Path.of("src", "main", "resources", "application.yml.example"),
                "APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS:35");
        assertAgentPlanningTimeout(Path.of("..", ".env.example"),
                "APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS=35");
        assertAgentPlanningTimeout(Path.of("..", ".env.prod.example"),
                "APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS=35");
        assertAgentPlanningTimeout(Path.of("..", "docker-compose.example.yml"),
                "APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS:-35");
        assertAgentPlanningTimeout(Path.of("..", "docker-compose.prod.yml"),
                "APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS:-35");
    }

    @Test
    @DisplayName("keeps Nginx upload body limits compatible with application uploads")
    void shouldKeepNginxUploadLimitCompatibleWithApplicationUploads() throws IOException {
        assertNginxUploadLimit(Path.of("..", "frontend", "nginx.conf"));
        assertNginxUploadLimit(Path.of("..", "frontend", "nginx.local.conf"));
    }

    @Test
    @DisplayName("keeps Spring multipart limits compatible with application uploads")
    void shouldKeepSpringMultipartLimitCompatibleWithApplicationUploads() throws IOException {
        assertSpringMultipartLimit(Path.of("src", "main", "resources", "application.yml"));
        assertSpringMultipartLimit(Path.of("src", "main", "resources", "application.yml.example"));
    }

    @Test
    @DisplayName("本地 Compose 使用固定管理员且只监听回环地址")
    void shouldKeepLocalDockerAuthenticationAndPortsSafe() throws IOException {
        assertLocalDockerContract(Path.of("..", "docker-compose.example.yml"));
        assertThat(Files.readString(Path.of("..", ".env.example")))
                .contains("APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED=true");
    }

    @Test
    @DisplayName("生产 Compose 保持邮箱验证认证模式")
    void shouldKeepProductionEmailAuthentication() throws IOException {
        assertThat(Files.readString(Path.of("..", "docker-compose.prod.yml")))
                .contains("APP_AUTH_MODE: email-verified")
                .contains("APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED: ${APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED:-false}");
        assertThat(Files.readString(Path.of("..", ".env.prod.example")))
                .contains("APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED=false");
    }

    private void assertDefaults(Path path) throws IOException {
        String content = Files.readString(path);

        assertThat(content)
                .as(path.toString())
                .contains("interview_atoms_e5_base")
                .contains("768")
                .doesNotContain("QDRANT_COLLECTION=interview_atoms\n")
                .doesNotContain("QDRANT_VECTOR_SIZE=384")
                .doesNotContain("collection: ${QDRANT_COLLECTION:interview_atoms}")
                .doesNotContain("vector-size: ${QDRANT_VECTOR_SIZE:384}");
    }

    private void assertTimeoutPassThrough(Path path) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains("QDRANT_CONNECT_TIMEOUT_MS")
                .contains("QDRANT_READ_TIMEOUT_MS")
                .contains("APP_EMBEDDING_CONNECT_TIMEOUT_MS")
                .contains("APP_EMBEDDING_READ_TIMEOUT_MS");
    }

    private void assertLocalDefaults(Path path) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains("provider: ${APP_EMBEDDING_PROVIDER:all-minilm}")
                .contains("collection: ${QDRANT_COLLECTION:interview_atoms}")
                .contains("vector-size: ${QDRANT_VECTOR_SIZE:384}");
    }

    private void assertCompetitionFeatureFlags(Path path) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains("APP_INTERVIEW_AGENT_ENABLED")
                .contains("APP_INTERVIEW_AGENT_PLANNING_TIMEOUT_SECONDS")
                .contains("APP_INTERVIEW_AGENT_MAX_TOOL_CALLS")
                .contains("APP_INTERVIEW_AGENT_FALLBACK_ENABLED")
                .contains("APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED");
    }

    private void assertAgentPlanningTimeout(Path path, String expectedDefault) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains(expectedDefault);
    }

    private void assertNginxUploadLimit(Path path) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains("client_max_body_size 21m;");
    }

    private void assertSpringMultipartLimit(Path path) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains("max-file-size: ${APP_MULTIPART_MAX_FILE_SIZE:20MB}")
                .contains("max-request-size: ${APP_MULTIPART_MAX_REQUEST_SIZE:21MB}");
    }

    private void assertLocalDockerContract(Path path) throws IOException {
        assertThat(Files.readString(path))
                .as(path.toString())
                .contains("APP_AUTH_MODE: ${APP_AUTH_MODE:-local-admin}")
                .contains("APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED: ${APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED:-true}")
                .contains("MAIL_USERNAME: ${MAIL_USERNAME:-}")
                .contains("MAIL_PASSWORD: ${MAIL_PASSWORD:-}")
                .contains("127.0.0.1:80:80")
                .contains("127.0.0.1:8080:8080")
                .contains("127.0.0.1:3307:3306")
                .contains("127.0.0.1:6379:6379")
                .contains("127.0.0.1:6333:6333");
    }
}
