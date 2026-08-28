package com.apigentest.service.impl;

import com.apigentest.common.AesUtil;
import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.LlmCallException;
import com.apigentest.common.UserContext;
import com.apigentest.entity.SysConfig;
import com.apigentest.mapper.SysConfigMapper;
import com.apigentest.service.AdminConfigService;
import com.apigentest.service.AuditService;
import com.apigentest.service.WebhookService;
import com.apigentest.service.llm.LlmClient;
import com.apigentest.service.llm.LlmConfigService;
import com.apigentest.vo.SysConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminConfigServiceImpl implements AdminConfigService {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "llm_api_key", "llm_model", "llm_base_url", "default_timeout", "default_retry",
            "webhook_url", "webhook_enabled", "super_admin_invite_code", "llm_temperature");
    private static final Set<String> SECRET_KEYS = Set.of("llm_api_key", "super_admin_invite_code");

    private static final String TEST_SYSTEM_PROMPT = "你是一个连接测试助手。";
    private static final String TEST_USER_CONTENT = "请只回复四个字：连接成功。";

    private final SysConfigMapper sysConfigMapper;
    private final LlmConfigService llmConfigService;
    private final LlmClient llmClient;
    private final WebhookService webhookService;
    private final AesUtil aesUtil;
    private final AuditService auditService;

    public AdminConfigServiceImpl(SysConfigMapper sysConfigMapper,
                                  LlmConfigService llmConfigService, LlmClient llmClient,
                                  WebhookService webhookService, AesUtil aesUtil, AuditService auditService) {
        this.sysConfigMapper = sysConfigMapper;
        this.llmConfigService = llmConfigService;
        this.llmClient = llmClient;
        this.webhookService = webhookService;
        this.aesUtil = aesUtil;
        this.auditService = auditService;
    }

    private static final Set<String> HIDDEN_KEYS = Set.of("ci_token", "ci_user_id");

    @Override
    public List<SysConfigVO> list() {
        checkAdmin();
        return sysConfigMapper.selectList(
                        new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getConfigKey))
                .stream()
                .filter(c -> !HIDDEN_KEYS.contains(c.getConfigKey()))
                .map(this::toVO).toList();
    }

    @Override
    public void update(String key, String value) {
        checkAdmin();
        if (!ALLOWED_KEYS.contains(key)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不允许的配置项：" + key);
        }
        if ("webhook_enabled".equals(key) && !"0".equals(value) && !"1".equals(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "webhook_enabled 必须是 0 或 1");
        }
        if ("webhook_url".equals(key) && value != null && !value.isBlank()
                && !value.startsWith("http://") && !value.startsWith("https://")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "webhook_url 必须是 http/https 地址");
        }
        if ("default_retry".equals(key) || "default_timeout".equals(key)) {
            try {
                int v = Integer.parseInt(value);
                if (v < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, key + " 必须是正整数");
            }
        }
        // LLM API Key 加密后落库（AES-GCM），避免明文存储
        String stored = value;
        if ("llm_api_key".equals(key) && value != null && !value.isBlank()) {
            stored = aesUtil.encrypt(value);
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setIsSecret(SECRET_KEYS.contains(key) ? 1 : 0);
        }
        config.setConfigValue(stored);
        if (config.getId() == null) {
            sysConfigMapper.insert(config);
        } else {
            sysConfigMapper.updateById(config);
        }
        // 审计留痕：敏感配置不记录原文
        String detail = SECRET_KEYS.contains(key) ? "已更新（敏感值不记录原文）" : "value=" + stored;
        auditService.log("UPDATE_CONFIG", "config:" + key, detail);
    }

    @Override
    public Map<String, String> testLlm() {
        checkAdmin();
        String apiKey = llmConfigService.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.LLM_NOT_CONFIGURED, "LLM API Key 未配置，请先在系统设置中保存");
        }
        String baseUrl = llmConfigService.getBaseUrl();
        String model = llmConfigService.getModel();
        try {
            String reply = llmClient.chat(TEST_SYSTEM_PROMPT, TEST_USER_CONTENT, apiKey, baseUrl, model, llmConfigService.getTemperature());
            return Map.of(
                    "model", model,
                    "baseUrl", baseUrl,
                    "reply", reply == null ? "" : reply.trim());
        } catch (LlmCallException e) {
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "LLM 连接失败：" + e.getMessage());
        }
    }

    @Override
    public String testWebhook() {
        checkAdmin();
        return webhookService.sendTest();
    }

    private void checkAdmin() {
        Integer role = UserContext.getRole();
        if (role == null || role < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员或超级管理员可操作系统配置");
        }
    }

    private SysConfigVO toVO(SysConfig config) {
        SysConfigVO vo = new SysConfigVO();
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(maskIfSecret(config));
        vo.setIsSecret(config.getIsSecret());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    private String maskIfSecret(SysConfig config) {
        String value = config.getConfigValue();
        if (config.getIsSecret() != null && config.getIsSecret() == 1
                && value != null && !value.isBlank()) {
            if (value.length() <= 8) {
                return "******";
            }
            return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        }
        return value;
    }
}
