-- =====================================================================
-- APIGenTest 初始化数据脚本
-- 依赖：先执行 01_schema.sql
-- 版本 v0.1 | 2026-08-07
-- =====================================================================

USE apigentest;

-- 初始管理员账号：admin / admin123（BCrypt 加密存储，登录后请尽快修改密码）
INSERT INTO `user` (username, password, nickname, role, status)
VALUES ('admin', '$2b$10$.rsrYY9RQRB2ZJQibbF6.OmVdBsNIV4Nxeod2uvW6obE/DhGAc6q6', '管理员', 2, 1);

-- 系统配置默认值（llm_api_key 留空，由管理员在后台配置；llm_model 可改为实际使用的模型）
INSERT INTO sys_config (config_key, config_value, is_secret) VALUES
('llm_api_key',    '',                                       1),
('llm_model',      'qwen-plus',                              0),
('llm_base_url',   'https://dashscope.aliyuncs.com/compatible-mode/v1', 0),
('default_timeout', '10000',                                 0),
('default_retry',  '2',                                      0);