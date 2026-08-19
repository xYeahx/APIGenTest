-- =====================================================================
-- APIGenTest 增量脚本：管理操作审计日志表（audit_log）
-- 适用 MySQL 8.0+ | 版本 v0.5 | 2026-08-20
-- 全新安装无需执行本脚本（01_schema.sql 已包含该表）
-- 已执行过的环境重复执行会自动跳过（幂等）
-- =====================================================================
USE apigentest;

CREATE TABLE IF NOT EXISTS audit_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  user_id    BIGINT       DEFAULT NULL COMMENT '操作人ID（CI/系统触发可为空）',
  username   VARCHAR(50)  DEFAULT NULL COMMENT '操作人登录名',
  action     VARCHAR(50)  NOT NULL COMMENT '操作类型：UPDATE_CONFIG / UPDATE_USER_STATUS / UPDATE_USER_ROLE / RESET_PASSWORD / DELETE_USER / REGENERATE_CI_TOKEN 等',
  target     VARCHAR(255) DEFAULT NULL COMMENT '操作对象描述（如 config:llm_api_key / user:5）',
  detail     VARCHAR(500) DEFAULT NULL COMMENT '补充说明（敏感值不记录原文）',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_action (action),
  KEY idx_user_id (user_id),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理操作审计日志表';
