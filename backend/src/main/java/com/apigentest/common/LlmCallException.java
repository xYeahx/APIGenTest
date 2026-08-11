package com.apigentest.common;

/**
 * LLM 调用异常（网络 / 超时 / 非 2xx 响应等）
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}