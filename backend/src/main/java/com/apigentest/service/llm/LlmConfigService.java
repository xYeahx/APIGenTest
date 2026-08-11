package com.apigentest.service.llm;

import com.apigentest.config.LlmProperties;
import com.apigentest.entity.SysConfig;
import com.apigentest.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

/**
 * LLM 配置读取：优先 sys_config（管理员后台配置），缺失时回退 application.yml 默认值
 */
@Service
public class LlmConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final LlmProperties llmProperties;

    public LlmConfigService(SysConfigMapper sysConfigMapper, LlmProperties llmProperties) {
        this.sysConfigMapper = sysConfigMapper;
        this.llmProperties = llmProperties;
    }

    /** 返回 API Key；未配置时返回 null（调用方给出友好提示） */
    public String getApiKey() {
        return getValue("llm_api_key");
    }

    public String getModel() {
        String v = getValue("llm_model");
        return v == null ? llmProperties.getModel() : v;
    }

    public String getBaseUrl() {
        String v = getValue("llm_base_url");
        return v == null ? llmProperties.getBaseUrl() : v;
    }

    public int getMaxRetry() {
        String v = getValue("default_retry");
        if (v == null) {
            return llmProperties.getMaxRetry();
        }
        try {
            return Math.max(0, Integer.parseInt(v));
        } catch (NumberFormatException e) {
            return llmProperties.getMaxRetry();
        }
    }

    /** 返回执行请求超时（毫秒），读 sys_config default_timeout，缺省回退 llm.timeout-ms */
    public int getDefaultTimeoutMs() {
        String v = getValue("default_timeout");
        if (v == null) {
            return llmProperties.getTimeoutMs();
        }
        try {
            return Math.max(1000, Integer.parseInt(v));
        } catch (NumberFormatException e) {
            return llmProperties.getTimeoutMs();
        }
    }

    private String getValue(String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return null;
        }
        return config.getConfigValue();
    }
}