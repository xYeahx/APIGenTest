-- =====================================================================
-- APIGenTest 增量脚本：团队协作 - 项目成员表（在已初始化库上执行）
-- 适用 MySQL 8.0+ | 版本 v0.2 | 2026-08-11
-- 全新安装无需执行本脚本（01_schema.sql 已包含该表）
-- =====================================================================
USE apigentest;

CREATE TABLE IF NOT EXISTS project_member (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '成员ID',
  project_id BIGINT   NOT NULL COMMENT '所属项目',
  user_id    BIGINT   NOT NULL COMMENT '成员用户',
  role       TINYINT  NOT NULL DEFAULT 1 COMMENT '1 成员 / 2 只读成员',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_user (project_id, user_id),
  KEY idx_user_id (user_id),
  CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';