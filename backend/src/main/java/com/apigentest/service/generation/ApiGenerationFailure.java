package com.apigentest.service.generation;

import lombok.Data;

@Data
public class ApiGenerationFailure {

    private Long apiId;

    private String error;

    /** 实际 LLM 调用次数（失败时为 maxRetry + 1） */
    private int attempts;
}