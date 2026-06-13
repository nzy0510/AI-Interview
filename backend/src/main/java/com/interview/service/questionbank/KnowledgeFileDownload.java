package com.interview.service.questionbank;

import org.springframework.core.io.Resource;

public record KnowledgeFileDownload(Resource resource, String filename, String contentType) {
}
