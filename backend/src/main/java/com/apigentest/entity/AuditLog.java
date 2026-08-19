package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理操作审计日志表
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID（CI/系统触发可为空） */
    private Long userId;

    /** 操作人登录名（系统触发时为 system） */
    private String username;

    /** 操作类型：UPDATE_CONFIG / UPDATE_USER_STATUS / UPDATE_USER_ROLE / RESET_PASSWORD / DELETE_USER / REGENERATE_CI_TOKEN 等 */
    private String action;

    /** 操作对象描述（如 config:llm_api_key / user:5） */
    private String target;

    /** 补充说明（敏感值不记录原文） */
    private String detail;

    private LocalDateTime createdAt;
}
