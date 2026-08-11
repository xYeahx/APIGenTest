package com.apigentest.service.generation;

/**
 * LLM 输出结构化校验失败
 */
public class GenerationValidationException extends RuntimeException {

    public GenerationValidationException(String message) {
        super(message);
    }
}