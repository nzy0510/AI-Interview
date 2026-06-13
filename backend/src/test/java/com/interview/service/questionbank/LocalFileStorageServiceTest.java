package com.interview.service.questionbank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LocalFileStorageService — 本地题库文件存储")
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("存储上传文件时生成受控 storageKey 并保留文件内容")
    void shouldStoreUploadedFileUnderManagedRoot() throws Exception {
        LocalFileStorageService storageService = new LocalFileStorageService(tempDir);
        MockMultipartFile file = new MockMultipartFile(
                "file", "..\\evil.md", "text/markdown", "hello".getBytes(StandardCharsets.UTF_8));

        FileStorageService.StoredFile stored = storageService.store(file, "knowledge/originals");

        assertThat(stored.storageKey()).startsWith("knowledge/originals/");
        assertThat(stored.storageKey()).doesNotContain("..");
        assertThat(stored.filename()).isEqualTo("evil.md");
        assertThat(stored.size()).isEqualTo(5);
        assertThat(stored.sha256()).isNotBlank();
        assertThat(storageService.readText(stored.storageKey())).isEqualTo("hello");
    }

    @Test
    @DisplayName("读取 storageKey 时拒绝路径穿越")
    void shouldRejectPathTraversalStorageKey() {
        LocalFileStorageService storageService = new LocalFileStorageService(tempDir);

        assertThatThrownBy(() -> storageService.readText("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法文件路径");
    }
}
