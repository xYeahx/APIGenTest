package com.apigentest.common;

import lombok.Getter;

/**
 * 业务异常，配合全局异常处理器返回统一错误格式
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}