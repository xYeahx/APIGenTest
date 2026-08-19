package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.UserContext;
import com.apigentest.dto.RunRequestDTO;
import com.apigentest.entity.Project;
import com.apigentest.entity.SysConfig;
import com.apigentest.entity.User;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.SysConfigMapper;
import com.apigentest.mapper.UserMapper;
import com.apigentest.service.AuditService;
import com.apigentest.service.CiService;
import com.apigentest.service.ExecutionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * CI 集成实现：
 * - ci_token 存于 sys_config（is_secret=1），仅管理员可重新生成
 * - ci_user_id 记录生成者，作为 CI 执行的操作人
 * - runByToken 通过常量时间比较校验 Token，调用系统级执行（triggerType=3）
 */
@Service
public class CiServiceImpl implements CiService {

    private static final Logger log = LoggerFactory.getLogger(CiServiceImpl.class);
    private static final String CI_TOKEN_KEY = "ci_token";
    private static final String CI_USER_KEY = "ci_user_id";

    private final SysConfigMapper sysConfigMapper;
    private final ExecutionService executionService;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CiServiceImpl(SysConfigMapper sysConfigMapper, ExecutionService executionService,
                         ProjectMapper projectMapper, UserMapper userMapper, AuditService auditService) {
        this.sysConfigMapper = sysConfigMapper;
        this.executionService = executionService;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Override
    public String regenerateToken() {
        checkAdmin();
        String token = randomToken();
        upsertConfig(CI_TOKEN_KEY, token, 1);
        upsertConfig(CI_USER_KEY, String.valueOf(UserContext.getUserId()), 0);
        log.info("CI Token 已重新生成，操作人={}", UserContext.getUsername());
        auditService.log("REGENERATE_CI_TOKEN", "ci_token", "CI Token 已重新生成，旧 Token 立即失效");
        return token;
    }

    @Override
    public Map<String, Object> tokenInfo() {
        checkAdmin();
        Map<String, Object> info = new HashMap<>();
        String token = getConfigValue(CI_TOKEN_KEY);
        boolean configured = token != null && !token.isBlank();
        info.put("configured", configured);
        info.put("tokenMasked", configured ? mask(token) : null);
        info.put("updatedAt", getConfigUpdatedAt(CI_TOKEN_KEY));
        String userIdStr = getConfigValue(CI_USER_KEY);
        Long operatorId = null;
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                operatorId = Long.valueOf(userIdStr);
            } catch (NumberFormatException ignored) {
                // 配置异常时按未配置处理
            }
        }
        String operatorName = null;
        if (operatorId != null) {
            User u = userMapper.selectById(operatorId);
            operatorName = u == null ? null : u.getUsername();
        }
        info.put("operatorName", operatorName);
        return info;
    }

    @Override
    public Long runByToken(String token, RunRequestDTO dto) {
        String stored = getConfigValue(CI_TOKEN_KEY);
        if (stored == null || stored.isBlank() || token == null || !constantTimeEquals(stored, token)) {
            throw new BusinessException(401, "CI Token 无效或未配置，请到系统设置中重新生成");
        }
        Long operatorId = resolveOperator(dto.getProjectId());
        return executionService.runBySystem(dto, 3, operatorId, null);
    }

    private Long resolveOperator(Long projectId) {
        String userIdStr = getConfigValue(CI_USER_KEY);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                return Long.valueOf(userIdStr);
            } catch (NumberFormatException ignored) {
                // fallback
            }
        }
        Project p = projectMapper.selectById(projectId);
        return p == null ? null : p.getOwnerId();
    }

    private void checkAdmin() {
        Integer role = UserContext.getRole();
        if (role == null || role < 2) {
            throw new BusinessException(403, "仅管理员或超级管理员可操作系统配置");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void upsertConfig(String key, String value, int isSecret) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setIsSecret(isSecret);
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(value);
            config.setIsSecret(isSecret);
            sysConfigMapper.updateById(config);
        }
    }

    private String getConfigValue(String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config == null ? null : config.getConfigValue();
    }

    private String getConfigUpdatedAt(String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config == null || config.getUpdatedAt() == null ? null
                : config.getUpdatedAt().toString().replace('T', ' ').substring(0, 19);
    }

    private String mask(String token) {
        if (token.length() <= 8) {
            return "******";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
