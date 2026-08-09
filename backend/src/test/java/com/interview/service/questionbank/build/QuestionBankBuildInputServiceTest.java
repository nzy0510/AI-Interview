package com.interview.service.questionbank.build;

import com.interview.config.QuestionBankBuildProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionBankBuildInputServiceTest {
    private final QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
    private final QuestionBankBuildInputService service = new QuestionBankBuildInputService(properties);

    @Test
    void shouldRejectMimeAndExtensionMismatch() {
        MockMultipartFile file = new MockMultipartFile("files", "notes.md", "application/pdf",
                "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.prepare(java.util.List.of(file)))
                .hasMessageContaining("MIME");
    }

    @Test
    void shouldRejectBinaryTextDisguisedAsMarkdown() {
        MockMultipartFile file = new MockMultipartFile("files", "notes.md", "text/markdown",
                new byte[]{'a', 0, 'b'});

        assertThatThrownBy(() -> service.prepare(java.util.List.of(file)))
                .hasMessageContaining("文本文件");
    }
}
