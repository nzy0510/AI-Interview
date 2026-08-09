package com.interview.service.questionbank.build;

import com.interview.service.UserLlmRuntimeConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

final class QuestionBankBuildRuntimeSnapshot {
    private QuestionBankBuildRuntimeSnapshot() {
    }

    static String fingerprint(UserLlmRuntimeConfig runtime) {
        if (runtime == null) return null;
        String raw = normalized(runtime.provider()).toLowerCase(Locale.ROOT) + "|"
                + normalized(runtime.baseUrl()) + "|"
                + normalized(runtime.modelName()) + "|"
                + (runtime.temperature() == null ? "" : runtime.temperature().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("模型运行快照生成失败", e);
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
