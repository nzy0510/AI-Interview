package com.interview.service.questionbank.build;

import com.interview.config.QuestionBankBuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class QuestionBankBuildInputService {
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown");
    private final QuestionBankBuildProperties properties;

    public QuestionBankBuildInputService(QuestionBankBuildProperties properties) {
        this.properties = properties;
    }

    public List<PreparedFile> prepare(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("至少上传一个文档");
        if (files.size() > properties.getMaxFiles()) throw new IllegalArgumentException("单次最多上传 " + properties.getMaxFiles() + " 个文件");
        List<PreparedFile> prepared = new ArrayList<>();
        for (MultipartFile file : files) {
            validate(file);
            try {
                byte[] bytes = file.getBytes();
                prepared.add(new PreparedFile(file, bytes));
            } catch (IOException e) { throw new IllegalArgumentException("文件读取失败"); }
        }
        return prepared;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        if (file.getSize() > properties.getMaxFileSizeBytes()) throw new IllegalArgumentException("单个文件不能超过 " + properties.getMaxFileSizeBytes() + " 字节");
        String extension = extensionOf(file.getOriginalFilename());
        if (!EXTENSIONS.contains(extension)) throw new IllegalArgumentException("仅支持 PDF、DOCX、Markdown/MD 和 TXT 文件");
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !allowedMime(extension, contentType)) throw new IllegalArgumentException("文件 MIME 类型与扩展名不匹配");
        try {
            byte[] bytes = file.getBytes();
            if ("pdf".equals(extension) && !startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("PDF 文件内容与扩展名不匹配");
            if ("docx".equals(extension) && !isDocx(bytes)) throw new IllegalArgumentException("DOCX 文件内容与扩展名不匹配");
            if (Set.of("txt", "md", "markdown").contains(extension)) for (byte value : bytes) if (value == 0) throw new IllegalArgumentException("文本文件内容与扩展名不匹配");
        } catch (IOException e) { throw new IllegalArgumentException("文件读取失败"); }
    }

    private boolean allowedMime(String extension, String contentType) {
        String mime = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return switch (extension) { case "pdf" -> "application/pdf".equals(mime); case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime); case "txt" -> "text/plain".equals(mime); case "md", "markdown" -> "text/markdown".equals(mime) || "text/plain".equals(mime); default -> false; };
    }

    private boolean isDocx(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') return false;
        boolean contentTypes = false, document = false;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry; while ((entry = zip.getNextEntry()) != null) { contentTypes |= "[Content_Types].xml".equals(entry.getName()); document |= "word/document.xml".equals(entry.getName()); if (contentTypes && document) return true; }
        } catch (IOException ignored) { return false; }
        return false;
    }

    private String extensionOf(String filename) { if (filename == null) return ""; String value = filename.replace('\\', '/'); int dot = value.lastIndexOf('.'); return dot < 0 ? "" : value.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private boolean startsWith(byte[] bytes, byte[] prefix) { if (bytes.length < prefix.length) return false; for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false; return true; }

    public record PreparedFile(MultipartFile file, byte[] bytes) { }
}
