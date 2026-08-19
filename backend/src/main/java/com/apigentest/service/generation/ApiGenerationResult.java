package com.apigentest.service.generation;

import lombok.Data;

import java.util.List;

@Data
public class ApiGenerationResult {

    private Long apiId;

    private List<GeneratedCase> cases;

    /** 生成模型（P2-2 埋点） */
    private String model;

    /** 生成温度（P2-2 埋点） */
    private double temperature;

    /** Prompt 版本（P2-2 埋点） */
    private String promptVersion;

    /** 实际 LLM 调用次数（重试次数 = attempts - 1） */
    private int attempts;
}