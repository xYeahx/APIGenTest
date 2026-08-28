package com.apigentest.common;

import lombok.Getter;

/**
 * LLM 调用异常，携带业务错误码（50001 调用失败 / 50002 输出校验失败 / 50003 超时）
 */
@Getter
public class LlmCallException extends RuntimeException {

    private final ErrorCode errorCode;

    public LlmCallException(String message) {
        super(message);
        this.errorCode = ErrorCode.LLM_CALL_FAILED;
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.LLM_CALL_FAILED;
    }

    public LlmCallException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LlmCallException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}