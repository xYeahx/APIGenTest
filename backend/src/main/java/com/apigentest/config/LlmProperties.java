package com.apigentest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 默认配置（application.yml 中 llm.*），可被 sys_config 中管理员配置覆盖
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String baseUrl;

    private String model;

    private int timeoutMs = 60000;

    private int maxRetry = 2;
}