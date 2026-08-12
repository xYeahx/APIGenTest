-- =====================================================================
-- APIGenTest 数据库建表脚本
-- 对应《需求与设计文档》第二部分 2.1 核心表（10 张）
-- 适用 MySQL 8.0+，字符集 utf8mb4
-- 版本 v0.2 | 2026-08-11（新增 project_member 项目成员表，团队协作）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS apigentest
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE apigentest;

-- ---------------------------------------------------------------------
-- 表1 user 用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username   VARCHAR(50)  NOT NULL COMMENT '登录名',
  password   VARCHAR(100) NOT NULL COMMENT '加密存储',
  nickname   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  email      VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  phone      VARCHAR(50)  DEFAULT NULL COMMENT '联系方式',
  role       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 普通用户 / 2 管理员 / 3 超级管理员',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------------------------------------------------------------------
-- 表2 project 项目表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS project (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  name        VARCHAR(100) NOT NULL COMMENT '项目名',
  description VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
  owner_id    BIGINT       NOT NULL COMMENT '所属人',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_owner_id (owner_id),
  CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- ---------------------------------------------------------------------
-- 表12 project_member 项目成员表（团队协作：成员 / 只读成员）
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 表3 api_info 接口表（OpenAPI 解析结果）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_info (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '接口ID',
  project_id  BIGINT       NOT NULL COMMENT '所属项目',
  method      VARCHAR(10)  NOT NULL COMMENT 'GET / POST / ...',
  path        VARCHAR(500) NOT NULL COMMENT '接口路径',
  summary     VARCHAR(200) DEFAULT NULL COMMENT '接口名称',
  description TEXT         COMMENT '接口描述',
  tags        VARCHAR(200) DEFAULT NULL COMMENT '分组标签',
  spec        TEXT         COMMENT '原始 OpenAPI 定义（JSON，供 LLM 生成使用）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_project_id (project_id),
  KEY idx_method_path (method, path),
  CONSTRAINT fk_api_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口表（OpenAPI 解析结果）';

-- ---------------------------------------------------------------------
-- 表4 environment 环境表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS environment (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '环境ID',
  project_id  BIGINT       NOT NULL COMMENT '所属项目',
  name        VARCHAR(50)  NOT NULL COMMENT '环境名（测试 / 预发）',
  base_url    VARCHAR(255) DEFAULT NULL COMMENT 'Base URL',
  variables   TEXT         COMMENT '环境变量（JSON）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_project_id (project_id),
  CONSTRAINT fk_env_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='环境表';

-- ---------------------------------------------------------------------
-- 表5 test_case 用例表（核心表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS test_case (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用例ID',
  project_id    BIGINT       NOT NULL COMMENT '所属项目',
  api_id        BIGINT       DEFAULT NULL COMMENT '关联接口（手动用例可空）',
  name          VARCHAR(200) NOT NULL COMMENT '用例名',
  scenario_type VARCHAR(20)  NOT NULL DEFAULT 'manual' COMMENT 'normal / boundary / exception / manual',
  method        VARCHAR(10)  NOT NULL COMMENT '请求方法',
  url_template  VARCHAR(500) NOT NULL COMMENT '含 {{变量}} 占位符',
  headers       TEXT         COMMENT '请求头（JSON）',
  query_params  TEXT         COMMENT '查询参数（JSON）',
  body          TEXT         COMMENT '请求体（JSON）',
  asserts       TEXT         COMMENT '断言数组（JSON）',
  pre_case_id   BIGINT       DEFAULT NULL COMMENT '前置依赖用例',
  extract_vars  TEXT         COMMENT '响应提取变量（JSON）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
  creator_id    BIGINT       DEFAULT NULL COMMENT '创建人',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_project_status (project_id, status),
  KEY idx_api_id (api_id),
  KEY idx_pre_case_id (pre_case_id),
  CONSTRAINT fk_case_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_case_api     FOREIGN KEY (api_id) REFERENCES api_info (id),
  CONSTRAINT fk_case_creator FOREIGN KEY (creator_id) REFERENCES `user` (id),
  CONSTRAINT fk_case_pre     FOREIGN KEY (pre_case_id) REFERENCES test_case (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用例表（核心表）';

-- ---------------------------------------------------------------------
-- 表6 execution 执行记录表（一次执行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS execution (
  id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '执行ID',
  project_id   BIGINT   NOT NULL COMMENT '所属项目',
  trigger_type TINYINT  NOT NULL DEFAULT 1 COMMENT '1 手动 / 2 定时 / 3 CI',
  status       TINYINT  NOT NULL DEFAULT 0 COMMENT '0 执行中 / 1 完成',
  total_cases  INT      NOT NULL DEFAULT 0 COMMENT '总数',
  passed       INT      NOT NULL DEFAULT 0 COMMENT '通过数',
  failed       INT      NOT NULL DEFAULT 0 COMMENT '失败数',
  duration_ms  BIGINT   NOT NULL DEFAULT 0 COMMENT '总耗时（毫秒）',
  started_at   DATETIME DEFAULT NULL COMMENT '开始时间',
  finished_at  DATETIME DEFAULT NULL COMMENT '结束时间',
  operator_id  BIGINT   DEFAULT NULL COMMENT '触发人（定时可空）',
  PRIMARY KEY (id),
  KEY idx_project_id (project_id),
  KEY idx_operator_id (operator_id),
  CONSTRAINT fk_exec_project  FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_exec_operator FOREIGN KEY (operator_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行记录表（一次执行）';

-- ---------------------------------------------------------------------
-- 表7 execution_detail 用例执行明细表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS execution_detail (
  id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  execution_id   BIGINT   NOT NULL COMMENT '所属执行',
  case_id        BIGINT   DEFAULT NULL COMMENT '执行的用例（用例删除后置空）',
  status         TINYINT  NOT NULL DEFAULT 1 COMMENT '1 通过 / 2 失败 / 3 异常',
  request_text   MEDIUMTEXT COMMENT '实际发送的完整请求',
  response_text  MEDIUMTEXT COMMENT '完整响应',
  error_message  MEDIUMTEXT COMMENT '失败信息',
  duration_ms    BIGINT   DEFAULT NULL COMMENT '单条耗时（毫秒）',
  retry_count    INT      NOT NULL DEFAULT 0 COMMENT '重试次数',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_execution_id (execution_id),
  KEY idx_case_id (case_id),
  KEY idx_exec_status (execution_id, status),
  CONSTRAINT fk_detail_exec FOREIGN KEY (execution_id) REFERENCES execution (id),
  CONSTRAINT fk_detail_case FOREIGN KEY (case_id) REFERENCES test_case (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用例执行明细表';

-- ---------------------------------------------------------------------
-- 表8 failure_analysis 失败归因表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS failure_analysis (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '归因ID',
  execution_detail_id BIGINT       NOT NULL COMMENT '对应失败明细',
  category            VARCHAR(20)  DEFAULT NULL COMMENT 'assert_error / data_error / env_error / real_defect',
  reason              TEXT         COMMENT 'LLM 分析原因',
  suggestion          TEXT         COMMENT '定位建议',
  confirmed           TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待确认 / 1 已确认',
  llm_model           VARCHAR(50)  DEFAULT NULL COMMENT '使用的模型',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_execution_detail_id (execution_detail_id),
  CONSTRAINT fk_failure_detail FOREIGN KEY (execution_detail_id) REFERENCES execution_detail (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失败归因表';

-- ---------------------------------------------------------------------
-- 表9 scheduled_task 定时任务表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduled_task (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  project_id     BIGINT       NOT NULL COMMENT '所属项目',
  name           VARCHAR(100) NOT NULL COMMENT '任务名',
  cron           VARCHAR(50)  NOT NULL COMMENT 'cron 表达式',
  environment_id BIGINT       DEFAULT NULL COMMENT '执行环境',
  case_filter    VARCHAR(200) DEFAULT NULL COMMENT '执行范围（全部 / 指定用例集）',
  enabled        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
  creator_id     BIGINT       DEFAULT NULL COMMENT '创建人',
  last_run_at    DATETIME     DEFAULT NULL COMMENT '上次执行时间',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_project_id (project_id),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_task_env     FOREIGN KEY (environment_id) REFERENCES environment (id),
  CONSTRAINT fk_task_creator FOREIGN KEY (creator_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';

-- ---------------------------------------------------------------------
-- 表10 sys_config 系统配置表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  config_key   VARCHAR(50) NOT NULL COMMENT 'llm_api_key / llm_model / default_timeout / default_retry 等',
  config_value TEXT        COMMENT '配置值',
  is_secret    TINYINT     NOT NULL DEFAULT 0 COMMENT '1 加密存储（API Key）',
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ---------------------------------------------------------------------
-- 表11 notification 站内信通知表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  user_id      BIGINT       NOT NULL COMMENT '接收人',
  type         VARCHAR(20)  NOT NULL DEFAULT 'execution' COMMENT '通知类型：execution 执行完成等',
  title        VARCHAR(200) NOT NULL COMMENT '标题',
  content      VARCHAR(1000) DEFAULT NULL COMMENT '内容',
  execution_id BIGINT       DEFAULT NULL COMMENT '关联执行记录（无外键，便于清理）',
  is_read      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信通知表';