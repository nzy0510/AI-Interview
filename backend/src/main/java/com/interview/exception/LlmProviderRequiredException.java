package com.interview.exception;

public class LlmProviderRequiredException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "请先配置大模型 API 后再使用该功能";

    public LlmProviderRequiredException() {
        super(DEFAULT_MESSAGE);
    }

    public LlmProviderRequiredException(String message) {
        super(message);
    }
}
