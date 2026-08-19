-- =====================================================================
-- APIGenTest 增量脚本：P2 实验数据埋点（生成参数 + 归因准确率字段）
-- 适用 MySQL 8.0+ | 版本 v0.4 | 2026-08-16
-- 全新安装无需执行本脚本（01_schema.sql / 02_init_data.sql 已包含）
-- 已执行过的环境重复执行会自动跳过（幂等）
-- =====================================================================
USE apigentest;

DROP PROCEDURE IF EXISTS apigentest_add_p2_cols;

DELIMITER $$
CREATE PROCEDURE apigentest_add_p2_cols()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'apigentest' AND TABLE_NAME = 'test_case' AND COLUMN_NAME = 'source') THEN
    ALTER TABLE test_case
      ADD COLUMN source             TINYINT      NOT NULL DEFAULT 1 COMMENT '1 手动 / 2 AI 生成（P2 埋点）' AFTER status,
      ADD COLUMN gen_task_id        VARCHAR(36)  DEFAULT NULL COMMENT 'AI 生成任务ID' AFTER source,
      ADD COLUMN gen_model          VARCHAR(50)  DEFAULT NULL COMMENT '生成模型' AFTER gen_task_id,
      ADD COLUMN gen_temperature    DECIMAL(4,2) DEFAULT NULL COMMENT '生成温度' AFTER gen_model,
      ADD COLUMN gen_prompt_version VARCHAR(20)  DEFAULT NULL COMMENT '生成 Prompt 版本' AFTER gen_temperature,
      ADD COLUMN gen_retry_count    INT          DEFAULT 0 COMMENT '生成实际重试次数' AFTER gen_prompt_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'apigentest' AND TABLE_NAME = 'failure_analysis' AND COLUMN_NAME = 'confirmed_category') THEN
    ALTER TABLE failure_analysis
      ADD COLUMN confirmed_category VARCHAR(20) DEFAULT NULL COMMENT '人工确认/修正后的分类（NULL=未确认；与 category 相同=确认正确，不同=人工修正）' AFTER confirmed,
      ADD COLUMN confirmed_at       DATETIME    DEFAULT NULL COMMENT '确认时间' AFTER confirmed_category;
  END IF;
END$$
DELIMITER ;

CALL apigentest_add_p2_cols();
DROP PROCEDURE apigentest_add_p2_cols;

-- 生成记录表（任务 x 接口粒度）
CREATE TABLE IF NOT EXISTS generation_record (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  task_id             VARCHAR(36)  NOT NULL COMMENT '生成任务ID',
  project_id          BIGINT       NOT NULL COMMENT '所属项目',
  api_id              BIGINT       NOT NULL COMMENT '生成接口',
  model               VARCHAR(50)  DEFAULT NULL COMMENT '生成模型',
  temperature         DECIMAL(4,2) DEFAULT NULL COMMENT '生成温度',
  prompt_version      VARCHAR(20)  DEFAULT NULL COMMENT '生成 Prompt 版本',
  max_retry           INT          NOT NULL DEFAULT 0 COMMENT '配置最大重试次数',
  retry_used          INT          NOT NULL DEFAULT 0 COMMENT '本次实际重试次数',
  business_desc       VARCHAR(500) DEFAULT NULL COMMENT '补充业务描述',
  generated_count     INT          NOT NULL DEFAULT 0 COMMENT '校验通过可入库用例数',
  confirmed_count     INT          NOT NULL DEFAULT 0 COMMENT '确认入库用例数',
  scenario_generated  VARCHAR(500) DEFAULT NULL COMMENT '按场景类型生成数（JSON）',
  scenario_confirmed  VARCHAR(500) DEFAULT NULL COMMENT '按场景类型确认数（JSON）',
  status              VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / PARTIAL_FAILED / FAILED',
  error               VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  created_by          BIGINT       DEFAULT NULL COMMENT '操作人',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成完成时间',
  confirmed_at        DATETIME     DEFAULT NULL COMMENT '确认入库时间',
  PRIMARY KEY (id),
  KEY idx_gen_task (task_id),
  KEY idx_gen_project (project_id),
  CONSTRAINT fk_gen_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_gen_api     FOREIGN KEY (api_id) REFERENCES api_info (id),
  CONSTRAINT fk_gen_user    FOREIGN KEY (created_by) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 生成记录表（P2 实验数据埋点）';

-- 生成温度配置项（幂等）
INSERT INTO sys_config (config_key, config_value, is_secret) VALUES ('llm_temperature', '0.3', 0)
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), is_secret = VALUES(is_secret);
