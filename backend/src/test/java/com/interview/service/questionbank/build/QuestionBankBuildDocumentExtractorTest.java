package com.interview.service.questionbank.build;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionBankBuildDocumentExtractorTest {
    private final QuestionBankBuildDocumentExtractor extractor = new QuestionBankBuildDocumentExtractor();

    @Test
    void shouldExtractUtf8Markdown() {
        assertThat(extractor.extract("notes.md", "# JVM\nGC Roots".getBytes(StandardCharsets.UTF_8)))
                .contains("GC Roots");
    }

    @Test
    void shouldRejectEmptyText() {
        assertThatThrownBy(() -> extractor.extract("notes.txt", " \n\t".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("为空");
    }

    @Test
    void shouldExtractDocxTextWithoutExternalEntities() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w=\"urn:test\"><w:body><w:p><w:r><w:t>Spring</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThat(extractor.extract("notes.docx", output.toByteArray())).contains("Spring");
    }
}
