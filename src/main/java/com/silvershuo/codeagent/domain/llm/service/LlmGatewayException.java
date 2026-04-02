package com.silvershuo.codeagent.domain.llm.service;

public class LlmGatewayException extends RuntimeException {

    private final boolean retryable;
    private final int attempts;

    public LlmGatewayException(String message, boolean retryable, int attempts, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.attempts = attempts;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getAttempts() {
        return attempts;
    }
}
