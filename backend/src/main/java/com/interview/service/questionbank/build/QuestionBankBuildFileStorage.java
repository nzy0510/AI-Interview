package com.interview.service.questionbank.build;

import com.interview.config.QuestionBankBuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class QuestionBankBuildFileStorage {
    private final Path root;

    public QuestionBankBuildFileStorage(QuestionBankBuildProperties properties) {
        Path configured = Paths.get(properties.getStorageRoot());
        this.root = configured.isAbsolute()
                ? configured.normalize()
                : Paths.get(System.getProperty("user.dir")).resolve(configured).normalize();
    }

    public StoredFile storeOriginal(Long buildId, MultipartFile file) throws IOException {
        String safeName = safeFilename(file.getOriginalFilename());
        byte[] bytes = file.getBytes();
        Path directory = buildDirectory(buildId).resolve("original");
        Files.createDirectories(directory);
        String storageKey = "builds/" + buildId + "/original/" + UUID.randomUUID() + "-" + safeName;
        Path target = resolve(storageKey);
        Files.write(target, bytes);
        return new StoredFile(storageKey, bytes.length, sha256(bytes));
    }

    public String storeText(Long buildId, Long sourceFileId, String text) throws IOException {
        String storageKey = "builds/" + buildId + "/text/" + sourceFileId + ".txt";
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text, StandardCharsets.UTF_8);
        return storageKey;
    }

    public String readText(String storageKey) throws IOException {
        return Files.readString(resolve(storageKey), StandardCharsets.UTF_8);
    }

    public byte[] readBytes(String storageKey) throws IOException {
        return Files.readAllBytes(resolve(storageKey));
    }

    public void deleteBuild(Long buildId) throws IOException {
        Path directory = buildDirectory(buildId);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new StorageRuntimeException("构建文件清理失败", e);
                }
            });
        } catch (StorageRuntimeException e) {
            throw e;
        }
    }

    private Path buildDirectory(Long buildId) {
        if (buildId == null || buildId < 1) {
            throw new IllegalArgumentException("构建 ID 无效");
        }
        return root.resolve("builds").resolve(String.valueOf(buildId)).normalize();
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法存储路径");
        }
        return resolved;
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "file" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "_").trim();
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) value = "file";
        return value.length() > 180 ? value.substring(value.length() - 180) : value;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("文件哈希计算失败", e);
        }
    }

    public record StoredFile(String storageKey, long size, String sha256) {
    }

    private static final class StorageRuntimeException extends RuntimeException {
        private StorageRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
