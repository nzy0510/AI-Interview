package com.interview.service.questionbank.build;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class QuestionBankBuildDocumentExtractor {
    private static final int MAX_DOCX_XML_BYTES = 10 * 1024 * 1024;

    public String extract(String filename, byte[] bytes) {
        String extension = extensionOf(filename);
        return switch (extension) {
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "txt", "md", "markdown" -> decodeText(bytes);
            default -> throw new IllegalArgumentException("仅支持 PDF、DOCX、Markdown/MD 和 TXT 文件");
        };
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(
                new ByteArrayInputStream(bytes), MemoryUsageSetting.setupTempFileOnly())) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("加密 PDF 暂不支持，请上传未加密文档");
            }
            String text = new PDFTextStripper().getText(document);
            return requireText(text, "PDF 未提取到有效文本，可能是扫描件，请先进行 OCR");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            String message = e.getMessage() == null ? "PDF 解析失败" : e.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("password") || message.contains("encrypted")) {
                throw new IllegalArgumentException("加密 PDF 暂不支持，请上传未加密文档");
            }
            throw new IllegalArgumentException("PDF 解析失败，请确认文件未损坏");
        }
    }

    private String extractDocx(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) continue;
                byte[] xml = zip.readNBytes(MAX_DOCX_XML_BYTES + 1);
                if (xml.length > MAX_DOCX_XML_BYTES) {
                    throw new IllegalArgumentException("DOCX 正文过大，已拒绝解析");
                }
                var factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                try {
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                } catch (IllegalArgumentException ignored) {
                    // Xerces versions used by some JDKs expose these controls as features only.
                }
                var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
                var texts = document.getElementsByTagNameNS("*", "t");
                StringBuilder output = new StringBuilder();
                for (int i = 0; i < texts.getLength(); i++) {
                    output.append(texts.item(i).getTextContent());
                    output.append('\n');
                }
                return requireText(output.toString(), "DOCX 未提取到有效文本");
            }
            throw new IllegalArgumentException("DOCX 文件缺少正文内容");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("DOCX 解析失败，请确认文件未损坏");
        }
    }

    private String decodeText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return requireText(text, "文本文件为空，无法生成题库原子");
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("文本文件必须使用 UTF-8 编码");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.replaceAll("\\s+", "").isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.replace("\u0000", "").trim();
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        String normalized = filename.replace('\\', '/');
        int dot = normalized.lastIndexOf('.');
        return dot < 0 ? "" : normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
