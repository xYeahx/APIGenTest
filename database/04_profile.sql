-- =====================================================================
-- APIGenTest 增量脚本：用户个人资料（头像/邮箱/联系方式）
-- 适用 MySQL 8.0+ | 版本 v0.3 | 2026-08-12
-- 全新安装无需执行本脚本（01_schema.sql 已包含这三列）
-- 已执行过的环境重复执行会自动跳过（幂等）
-- =====================================================================
USE apigentest;

DROP PROCEDURE IF EXISTS apigentest_add_profile_col;

DELIMITER $$
CREATE PROCEDURE apigentest_add_profile_col()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'apigentest' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'avatar_url') THEN
    ALTER TABLE `user` ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像URL' AFTER nickname;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'apigentest' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'email') THEN
    ALTER TABLE `user` ADD COLUMN email VARCHAR(100) DEFAULT NULL COMMENT '邮箱' AFTER avatar_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'apigentest' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone') THEN
    ALTER TABLE `user` ADD COLUMN phone VARCHAR(50) DEFAULT NULL COMMENT '联系方式' AFTER email;
  END IF;
END$$
DELIMITER ;

CALL apigentest_add_profile_col();
DROP PROCEDURE apigentest_add_profile_col;