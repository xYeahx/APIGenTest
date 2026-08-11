# APIGenTest

基于大模型的接口自动化测试平台（毕业设计项目）。

## 技术栈
- 前端：Vue3 + Vite + Element Plus + ECharts + Pinia + Vue Router
- 后端：Spring Boot 3.3（JDK 17）+ MyBatis-Plus + MySQL 8 + Redis
- AI：接入大模型 API（默认通义千问兼容模式，可在系统配置中修改）

## 目录结构
| 目录 | 说明 |
| --- | --- |
| `backend` | Spring Boot 后端（端口 8081） |
| `frontend` | Vue3 前端（端口 5173，开发代理 /api → 8081） |
| `database` | 建表脚本与初始化数据 |
| `scripts` | 开发辅助脚本 |
| `docs` | 项目内部文档（接口约定、部署说明等） |

## 快速开始
1. 初始化数据库（需 MySQL 已启动）：
   ```powershell
   .\scripts\init-db.ps1
   ```
2. 启动后端：
   ```powershell
   .\scripts\dev-backend.ps1
   ```
3. 启动前端（另开一个终端）：
   ```powershell
   .\scripts\dev-frontend.ps1
   ```
4. 浏览器访问 http://localhost:5173

默认管理员：`admin / admin123`（登录后请修改）。

## 端口说明
- 后端 8081、前端 5173（本机 8080 被其他程序占用，故后端避开使用 8081）。

## 设计基线
需求与设计文档见 `files/需求与设计/需求与设计文档.docx`，数据库与接口定义以该文档为准，开发调整需同步回写。