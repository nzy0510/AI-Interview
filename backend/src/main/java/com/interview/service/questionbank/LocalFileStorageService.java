package com.interview.service.questionbank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    @Autowired
    public LocalFileStorageService(@Value("${app.knowledge-storage.root:knowledge-storage}") String root) {
        this(toAbsoluteRoot(Paths.get(root)));
    }

    public LocalFileStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(MultipartFile file, String namespace) throws IOException {
        String filename = sanitizeFilename(file.getOriginalFilename());
        Path directory = resolveNamespace(namespace);
        Files.createDirectories(directory);
        Path target = directory.resolve(UUID.randomUUID() + "-" + filename).normalize();
        ensureInsideRoot(target);

        MessageDigest digest = sha256Digest();
        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            Files.copy(digestInputStream, target);
        }
        return new StoredFile(toStorageKey(target), filename, file.getContentType(), Files.size(target), hex(digest));
    }

    @Override
    public StoredFile storeText(String content, String namespace, String suggestedFilename) throws IOException {
        String filename = sanitizeFilename(suggestedFilename);
        Path directory = resolveNamespace(namespace);
        Files.createDirectories(directory);
        Path target = directory.resolve(UUID.randomUUID() + "-" + filename).normalize();
        ensureInsideRoot(target);

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(target, bytes);
        MessageDigest digest = sha256Digest();
        digest.update(bytes);
        return new StoredFile(toStorageKey(target), filename, "text/markdown", bytes.length, hex(digest));
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        Path path = resolveStorageKey(storageKey);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("文件不存在");
        }
        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("非法文件路径");
        }
    }

    @Override
    public String readText(String storageKey) throws IOException {
        return Files.readString(resolveStorageKey(storageKey), StandardCharsets.UTF_8);
    }

    private Path resolveNamespace(String namespace) {
        String normalized = normalizeKey(namespace);
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("非法文件路径");
        }
        Path path = root.resolve(normalized).normalize();
        ensureInsideRoot(path);
        return path;
    }

    private Path resolveStorageKey(String storageKey) {
        String normalized = normalizeKey(storageKey);
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("非法文件路径");
        }
        Path path = root.resolve(normalized).normalize();
        ensureInsideRoot(path);
        return path;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径");
        }
    }

    private String toStorageKey(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace("\\", "/");
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("\\", "/");
    }

    private static String sanitizeFilename(String filename) {
        String normalized = filename == null ? "file" : filename.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.trim();
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            normalized = "file";
        }
        String sanitized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.length() > 120 ? sanitized.substring(sanitized.length() - 120) : sanitized;
    }

    private static Path toAbsoluteRoot(Path root) {
        Path path = root;
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String hex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
    }
}
