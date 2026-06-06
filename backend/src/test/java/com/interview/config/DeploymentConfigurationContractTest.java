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
}
