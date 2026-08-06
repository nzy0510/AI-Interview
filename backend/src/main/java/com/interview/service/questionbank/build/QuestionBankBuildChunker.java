package com.interview.service.questionbank.build;

import java.util.ArrayList;
import java.util.List;

public final class QuestionBankBuildChunker {
    private QuestionBankBuildChunker() {
    }

    public static List<String> split(String text, int chunkChars, int overlapChars) {
        if (text == null || text.isBlank()) return List.of();
        if (chunkChars <= 0 || overlapChars < 0 || overlapChars >= chunkChars) {
            throw new IllegalArgumentException("分块参数无效");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkChars);
            if (end < normalized.length()) {
                int boundary = normalized.lastIndexOf('\n', end);
                if (boundary > start + chunkChars / 2) end = boundary;
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            int nextStart = Math.max(start + 1, end - overlapChars);
            start = nextStart;
        }
        return chunks;
    }
}
