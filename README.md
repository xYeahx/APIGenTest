# APIGenTest

基于大模型的接口自动化测试平台（毕业设计项目）。

平台以「导入接口文档 → AI 生成测试用例 → 执行引擎回归 → 失败归因 → 质量统计」为主线，提供用例管理、执行引擎、Mock 服务、测试报告、定时回归、CI 集成、Webhook 告警、团队协作、审计日志等能力，并配套 PowerShell 回归脚本对平台自身做端到端验证。

## 功能特性

- **AI 用例生成**：导入 OpenAPI / Postman 集合后，调用大模型（OpenAI 兼容模式）自动生成测试用例，输出校验、失败重试、人工确认入库
- **用例管理**：手动/生成用例，支持正常流程、边界值、异常参数等场景类型；断言（状态码、JSONPath 字段）、变量提取、前置依赖用例
- **执行引擎**：范围解析 → 依赖排序 → 变量替换 → HTTP 执行 → 断言 → 提取变量 → 失败重试；异步线程池执行、逐条落库、级联清理
- **环境管理**：多环境 baseUrl 与变量配置，测试/生产环境数据隔离
- **Mock 服务**：基于接口 schema 自动 Mock，支持 `mock_error` / `mock_data` 注入故障场景
- **测试报告**：单次执行报告（汇总、失败 TOP、错误聚合）、项目执行趋势（通过率 / 耗时）、Dashboard 总览
- **失败归因**：LLM 自动分析失败用例（分类、根因、修复建议）+ 人工确认，并统计归因准确率
- **定时回归**：Cron 定时任务自动执行全量/指定用例，失败回调通知
- **CI 集成**：`X-CI-Token` 免登录触发执行（triggerType=3），可被 Jenkins / GitLab CI 流水线调用
- **通知与 Webhook**：站内通知 + Webhook 事件推送（执行完成、定时任务失败、AI 生成完成）
- **团队协作**：项目成员三级角色（owner / member / readonly），细粒度权限控制
- **质量统计**：AI 生成质量、归因准确率、执行趋势等指标统计
- **审计日志**：管理端操作留痕
- **安全**：JWT 鉴权、BCrypt 密码、敏感配置（LLM Key 等）AES-GCM 加密存储、OpenAPI 导入 SSRF 防护

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue3 + Vite + Element Plus + ECharts + Pinia + Vue Router |
| 后端 | Spring Boot 3.3（JDK 17）+ MyBatis-Plus + MySQL 8 + Redis（预留） |
| AI | 大模型 OpenAI 兼容模式（默认 DashScope 通义千问 `qwen-plus`，可在系统设置修改） |
| 测试 | PowerShell 回归脚本 + Node.js Mock 服务（mock-target / mock-llm / webhook-listener） |

## 目录结构

| 目录 | 说明 |
| --- | --- |
| `backend` | Spring Boot 后端（端口 8081） |
| `frontend` | Vue3 前端（端口 5173，开发代理 /api → 8081） |
| `database` | 建表脚本与初始化数据（01~07） |
| `scripts` | 开发辅助与自动化回归脚本 |
| `docs` | 项目文档与示例文件（OpenAPI / Postman 样例） |

## 快速开始

前置环境：JDK 17、Maven、Node.js 18+、MySQL 8（Redis 当前未在核心链路使用，未启动不影响）。

1. 初始化数据库（需 MySQL 已启动，默认 root/root，可用脚本参数调整）：

   ```powershell
   .\scripts\init-db.ps1
   ```

2. 启动后端（端口 8081）：

   ```powershell
   .\scripts\dev-backend.ps1
   ```

3. 启动前端（另开一个终端，端口 5173）：

   ```powershell
   .\scripts\dev-frontend.ps1
   ```

4. 浏览器访问 http://localhost:5173

默认管理员：`admin / admin123`（登录后请修改）。

## 配置说明

`backend/src/main/resources/application.yml` 关键配置：

| 配置 | 说明 |
| --- | --- |
| `jwt.secret` / `jwt.expire-hours` | JWT 密钥与有效期（生产环境务必替换） |
| `app.crypto-key` | 敏感配置 AES 加密密钥（生产环境务必替换为随机长字符串） |
| `app.import.block-private` | OpenAPI 导入 SSRF 防护：禁止导入内网/保留地址文档（生产保持 true） |
| `llm.base-url` / `llm.model` / `llm.temperature` | 大模型地址、模型、采样温度 |
| `llm.timeout-ms` / `llm.max-retry` | LLM 调用超时与重试次数 |

系统设置（管理员端）可配置项：`llm_api_key`（AES 加密存储）、`llm_model`、`llm_base_url`、`llm_temperature`、`default_timeout`、`default_retry`、`webhook_url`、`webhook_enabled`。

## 自动化回归脚本

脚本以平台自身 API 为被测对象，覆盖登录、项目管理、OpenAPI/Postman 导入、用例 CRUD、执行引擎、Mock、报告、失败归因、定时任务、CI 触发、通知、Webhook、成员权限、级联清理等场景。

| 脚本 | 说明 | 依赖 |
| --- | --- | --- |
| `smoke-exec.ps1` | 执行引擎端到端冒烟：自动拉起 mock-target + 后端（可选前端），跑 6 条用例并校验变量注入/重试/异常，最后级联删除并清理数据库 | 自动启动，无需预置 |
| `regression-p0.ps1` | P0 核心功能冒烟（导入、Mock、Webhook、覆盖率等） | 后端 8081 运行中 |
| `regression-member.ps1` | 团队协作/权限回归（owner/member/readonly/越权/通知） | 后端 8081 运行中 |
| `regression-p2.ps1` | P2 功能回归（生成质量/归因统计等增量功能） | 后端 8081 运行中 |
| `regression-full.ps1` | 全量功能回归（26 组场景），结果写入仓库外 `files/测试/full-regression-result.json` | 后端 8081、mock-target 9090、MySQL 运行中 |

```powershell
# 执行引擎冒烟（自动拉起依赖）
powershell -File .\scripts\smoke-exec.ps1

# 全量回归（需先启动后端与 mock-target）
powershell -File .\scripts\regression-full.ps1
```

辅助工具：

| 脚本 | 说明 |
| --- | --- |
| `scripts/mock-target.js` | 本地 Mock 被测系统（默认 9090：login/users/orders/boom/unauth） |
| `scripts/mock-llm.js` | 本地 Mock LLM 服务（默认 9999：valid/invalid/retry 三种模式） |
| `scripts/fixtures/webhook-listener.js` | 本地 Webhook 接收器，用于验证 Webhook 推送 |

## CI 集成

管理员在「系统设置」中生成 CI Token 后，流水线可免登录触发执行：

```bash
curl -X POST http://127.0.0.1:8081/api/ci/run \
  -H "X-CI-Token: YOUR_CI_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"projectId":1,"environmentId":1,"scope":{"type":"all"}}'
```

- `POST /api/ci/run`：触发执行，返回 `executionId`，triggerType=3
- `GET /api/admin/ci/token`：查看 Token 状态（脱敏）
- `POST /api/admin/ci/token/regenerate`：重新生成 Token

## Webhook 事件

| 事件 | 触发时机 | 主要字段 |
| --- | --- | --- |
| `execution_finished` | 一次执行完成 | executionId、projectId、triggerType、totalCases、passed、failed、passRate、durationMs |
| `scheduled_task_failed` | 定时任务执行失败 | taskId、taskName、projectId、error |
| `generation_finished` | AI 用例生成完成 | projectId、status、success、failed、total |
| `test` | 配置测试消息 | message |

## 定时任务

平台内置 Cron 定时回归（支持 5/6 段 Quartz Cron 表达式），可配置执行环境与用例范围（全部/指定用例），支持预览下次执行时间、启停、删除；任务失败自动触发站内通知与 Webhook 回调。

## 数据库脚本

| 文件 | 说明 |
| --- | --- |
| `01_schema.sql` | 建库 + 全部表（全新安装执行） |
| `02_init_data.sql` | 默认超级管理员与 sys_config 初始配置 |
| `03_member.sql` / `04_profile.sql` | 团队协作、个人资料增量脚本（旧库单独执行） |
| `05_p2_stats.sql` | P2 统计埋点增量脚本 |
| `06_legacy_ai_backfill.sql` | 旧版 AI 用例一次性回填 |
| `07_audit_log.sql` | 审计日志表增量脚本（幂等） |

## 设计基线

需求与设计文档见仓库外 `files/需求与设计/需求与设计文档.docx`，数据库与接口定义以该文档为准，开发调整需同步回写。