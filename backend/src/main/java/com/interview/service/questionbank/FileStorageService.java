package com.interview.service.questionbank;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    StoredFile store(MultipartFile file, String namespace) throws IOException;

    StoredFile storeText(String content, String namespace, String suggestedFilename) throws IOException;

    Resource loadAsResource(String storageKey);

    String readText(String storageKey) throws IOException;

    record StoredFile(String storageKey,
                      String filename,
                      String contentType,
                      long size,
                      String sha256) {
    }
}
