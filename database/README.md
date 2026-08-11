# database

APIGenTest 数据库脚本目录。

| 文件 | 说明 |
| --- | --- |
| `01_schema.sql` | 建库 + 10 张核心表（对应《需求与设计文档》2.1 节） |
| `02_init_data.sql` | 初始化数据：默认管理员 admin、sys_config 默认配置 |

## 使用方式

```bash
# 需先启动 MySQL 服务
mysql -u root -p < 01_schema.sql
mysql -u root -p < 02_init_data.sql
```

默认管理员：`admin / admin123`（登录后请修改）。

## 设计约定

- 库名：`apigentest`，字符集 `utf8mb4`
- 核心表：user、project、api_info、environment、test_case、execution、execution_detail、failure_analysis、scheduled_task、sys_config
- 可选表（加分功能用，暂不建）：project_member、test_suite/test_suite_case、notification、ai_generation_log