# database

APIGenTest 数据库脚本目录。

| 文件 | 说明 |
| --- | --- |
| `01_schema.sql` | 建库 + 14 张表（核心表 + v2 团队协作 + P2 实验 + 审计日志，全新安装执行） |
| `02_init_data.sql` | 初始化数据：默认超级管理员 admin、sys_config 默认配置 |
| `03_member.sql` | 团队协作增量脚本：project_member 表（旧库单独执行） |
| `04_profile.sql` | 个人资料增量脚本：user 表头像/邮箱/联系方式字段（旧库单独执行） |
| `05_p2_stats.sql` | P2 实验埋点增量脚本：test_case 生成参数、failure_analysis 确认字段、generation_record 表（旧库单独执行） |
| `06_legacy_ai_backfill.sql` | 一次性数据回填脚本（仅针对特定旧版 AI 用例，按脚本内说明执行） |
| `07_audit_log.sql` | 管理操作审计日志表增量脚本：audit_log（旧库单独执行，幂等） |

## 使用方式

```bash
# 需先启动 MySQL 服务
mysql -u root -p < 01_schema.sql
mysql -u root -p < 02_init_data.sql
```

默认管理员：`admin / admin123`（登录后请修改）。

## 设计约定

- 库名：`apigentest`，字符集 `utf8mb4`
- 表清单：user、project、project_member、api_info、environment、test_case、execution、execution_detail、failure_analysis、scheduled_task、sys_config、notification、generation_record、audit_log
- 命名：主键 `id`、时间字段 `created_at / updated_at`、唯一键 `uk_*`、普通索引 `idx_*`、外键 `fk_*`
- 可选表（加分功能用，暂不建）：test_suite/test_suite_case、ai_generation_log
- 安全：user.password 为 BCrypt 密文；sys_config 中 llm_api_key 为 AES-GCM 加密存储（enc:v1: 前缀）；audit_log 记录管理端操作留痕
