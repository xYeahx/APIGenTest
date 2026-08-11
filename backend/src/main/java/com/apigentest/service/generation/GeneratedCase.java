package com.apigentest.service.generation;

import lombok.Data;

/**
 * LLM 生成并经校验后的单条用例（即 test_case 表数据的统一形态）
 */
@Data
public class GeneratedCase {

    private String name;

    private String scenarioType;

    private String method;

    private String urlTemplate;

    private String headers;

    private String queryParams;

    private String body;

    private String asserts;

    private String extractVars;
}