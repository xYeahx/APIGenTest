package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.LlmCallException;
import com.apigentest.common.UserContext;
import com.apigentest.entity.SysConfig;
import com.apigentest.mapper.SysConfigMapper;
import com.apigentest.service.AdminConfigService;
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
            "llm_api_key", "llm_model", "llm_base_url", "default_timeout", "default_retry");

    private static final String TEST_SYSTEM_PROMPT = "你是一个连接测试助手。";
    private static final String TEST_USER_CONTENT = "请只回复四个字：连接成功。";

    private final SysConfigMapper sysConfigMapper;
    private final LlmConfigService llmConfigService;
    private final LlmClient llmClient;

    public AdminConfigServiceImpl(SysConfigMapper sysConfigMapper,
                                  LlmConfigService llmConfigService, LlmClient llmClient) {
        this.sysConfigMapper = sysConfigMapper;
        this.llmConfigService = llmConfigService;
        this.llmClient = llmClient;
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
            throw new BusinessException(400, "不允许的配置项：" + key);
        }
        if ("default_retry".equals(key) || "default_timeout".equals(key)) {
            try {
                int v = Integer.parseInt(value);
                if (v < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(400, key + " 必须是正整数");
            }
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setIsSecret("llm_api_key".equals(key) ? 1 : 0);
        }
        config.setConfigValue(value);
        if (config.getId() == null) {
            sysConfigMapper.insert(config);
        } else {
            sysConfigMapper.updateById(config);
        }
    }

    @Override
    public Map<String, String> testLlm() {
        checkAdmin();
        String apiKey = llmConfigService.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(400, "LLM API Key 未配置，请先在系统设置中保存");
        }
        String baseUrl = llmConfigService.getBaseUrl();
        String model = llmConfigService.getModel();
        try {
            String reply = llmClient.chat(TEST_SYSTEM_PROMPT, TEST_USER_CONTENT, apiKey, baseUrl, model);
            return Map.of(
                    "model", model,
                    "baseUrl", baseUrl,
                    "reply", reply == null ? "" : reply.trim());
        } catch (LlmCallException e) {
            throw new BusinessException(400, "LLM 连接失败：" + e.getMessage());
        }
    }

    private void checkAdmin() {
        Integer role = UserContext.getRole();
        if (role == null || role != 2) {
            throw new BusinessException(403, "仅管理员可操作系统配置");
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