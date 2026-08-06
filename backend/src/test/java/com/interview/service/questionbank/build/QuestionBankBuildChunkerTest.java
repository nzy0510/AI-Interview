package com.interview.service.questionbank.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBankBuildChunkerTest {
    @Test
    void shouldSplitTextWithBoundedOverlap() {
        List<String> chunks = QuestionBankBuildChunker.split("abcdefghij", 5, 2);

        assertThat(chunks).containsExactly("abcde", "defgh", "ghij");
    }

    @Test
    void shouldPreferParagraphBoundary() {
        List<String> chunks = QuestionBankBuildChunker.split("第一段内容\n第二段内容\n第三段内容", 8, 1);

        assertThat(chunks).allMatch(item -> !item.isBlank());
        assertThat(String.join("\n", chunks)).contains("第一段内容", "第二段内容", "第三段内容");
    }
}
