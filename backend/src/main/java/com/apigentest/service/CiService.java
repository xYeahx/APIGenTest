package com.apigentest.service;

import com.apigentest.dto.RunRequestDTO;

import java.util.Map;

/**
 * CI 集成：CI Token 管理 + 免登录触发执行（triggerType=3）
 */
public interface CiService {

    /** 重新生成 CI Token（管理员），返回完整 Token（仅本次可见） */
    String regenerateToken();

    /** 查看 CI Token 状态（管理员，脱敏展示） */
    Map<String, Object> tokenInfo();

    /** 凭 CI Token 触发执行（无需登录态） */
    Long runByToken(String token, RunRequestDTO dto);
}