package com.interview.exception;

public class QuotaExceededException extends RuntimeException {
    private final String quotaType;
    private final int limit;

    public QuotaExceededException(String quotaType, int limit, String message) {
        super(message);
        this.quotaType = quotaType;
        this.limit = limit;
    }

    public String getQuotaType() {
        return quotaType;
    }

    public int getLimit() {
        return limit;
    }
}
