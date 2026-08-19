package com.apigentest.service.llm;

import com.apigentest.common.AesUtil;
import com.apigentest.config.LlmProperties;
import com.apigentest.entity.SysConfig;
import com.apigentest.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LLM 配置读取：优先 sys_config（管理员后台配置），缺失时回退 application.yml 默认值。
 * llm_api_key 为加密存储（AES-GCM），读取时自动解密；兼容历史明文。
 */
@Service
public class LlmConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);
    private static final String API_KEY_KEY = "llm_api_key";

    private final SysConfigMapper sysConfigMapper;
    private final LlmProperties llmProperties;
    private final AesUtil aesUtil;

    public LlmConfigService(SysConfigMapper sysConfigMapper, LlmProperties llmProperties, AesUtil aesUtil) {
        this.sysConfigMapper = sysConfigMapper;
        this.llmProperties = llmProperties;
        this.aesUtil = aesUtil;
    }

    /** 返回 API Key；未配置时返回 null（调用方给出友好提示） */
    public String getApiKey() {
        return getValue(API_KEY_KEY);
    }

    public String getModel() {
        String v = getValue("llm_model");
        return v == null ? llmProperties.getModel() : v;
    }

    public String getBaseUrl() {
        String v = getValue("llm_base_url");
        return v == null ? llmProperties.getBaseUrl() : v;
    }

    /** 返回生成温度，读 sys_config llm_temperature，缺省回退 llm.temperature */
    public double getTemperature() {
        String v = getValue("llm_temperature");
        if (v == null) {
            return llmProperties.getTemperature();
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return llmProperties.getTemperature();
        }
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
        String value = config.getConfigValue();
        if (API_KEY_KEY.equals(key)) {
            value = aesUtil.decrypt(value);
            if (value == null) {
                log.warn("llm_api_key 解密失败，按未配置处理（请重新保存 API Key）");
                return null;
            }
        }
        return value;
    }
}
