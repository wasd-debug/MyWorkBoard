# 个人工作台

一个正在从“真实时薪与加班追踪”演进为“工时 + 账本 + 任务 + AI + 洞察”的个人效率平台。

当前可用主线是多用户工时与个人账本。Phase 0/1 本轮已完成资源化、local-first、物理模块拆分和自动化验收收口；视觉/真机、真实导入金额对账与持续运维演练仍属于发布门禁，任务、RAG 知识库和跨域洞察属于后续阶段。

## 当前能力

### 工时

- 记录上班、下班和额外休息时间。
- 按月浏览、编辑和删除记录，配置税前/税后工资与排班设置。
- 查看周、月、年和自定义时间范围的工时、加班、工资及时薪趋势。
- 读取法定节假日与调休信息，支持跨午夜和休息日打卡。
- 按登录用户隔离本地缓存和服务端数据。

工时前端已经切换到 `/api/v1/worktime/settings` 与 `/records` 资源 API，新建使用幂等键，更新和删除使用 revision。已保存记录展示服务端返回的加班分钟、实际时薪、计算版本和时区；`frontend/src/utils/calc.js` 只承担未保存表单预览与页面聚合。snapshot 后端端点仅保留旧客户端兼容，前端不再导出或调用。

### 账本

- 多账本、账户、两级收支分类、商家、项目、成员和角色权限。
- 支出、收入、转账、借入、借出、收债和还债流水。
- 服务端分页、排序与组合筛选；流水版本历史、软删、回收站和审计日志。
- 月度总预算、一级/二级支出分类预算、账户余额刷新和月末物化。
- 响应式首页、流水、管理、报表和定时任务页面。
- 随手记多 Sheet Excel/CSV 预览、确认导入、重复流水识别和 Excel 导出。
- IndexedDB + oplog 同步引擎，支持用户/账本隔离、分页拉取、冲突与拒绝队列。
- 周期流水、固定日期/间隔规则、ShedLock 到期执行和手动执行。
- 自然语言记账、图片识别、结构化草稿确认和月度 AI 分析。
- 共享账本、成员角色权限、跨账号缓存隔离和失权回退。

流水、账户、分类、商家、项目和预算写入统一经过 ledger command facade，先更新 IndexedDB 投影和 oplog，再由 sync-engine 推送；成员/角色、账本删除、导入、AI 确认、定时任务和恢复等跨资源命令明确要求联网。Playwright 已覆盖断网新增/编辑/删除、刷新保留、重连同步、冲突/拒绝处理及用户/账本隔离。支付宝、微信、银行卡账单自动拉取目前仍只是待接入类型。

### 后续阶段

- **Phase 2 任务管理：未启动。** 计划包含清单、任务、日历、提醒、番茄钟、习惯和倒数日。
- **Phase 3 RAG/Agent：主体未启动。** 当前只有 OpenAI 兼容 LLM 网关和账本 AI 能力。
- **Phase 4 跨域洞察：未启动。** 计划通过领域事件生成日/周/月/年报。
- **Phase 5 持续打磨：部分能力提前实现。** 响应式布局、主题和共享账本已存在；PWA、搜索和可观测体系尚未实现。

详细现状见 [项目总览](docs/overview.md)，长期设计和阶段门禁见 [架构文档](docs/ARCHITECTURE.md)，账本细节见 [Phase 1 设计](<docs/Phase 1 —— 账本设计具体展开.md>)。

## 技术栈

| 层 | 当前实现 | 后续目标 |
|---|---|---|
| 前端 | Vue 3、Vite、Pinia、Vue Router、ECharts、Tailwind CSS 4、源码 UI 组件、Lucide | PWA，并把 local-first 模式扩展到后续领域 |
| 后端 | Java 17、Spring Boot 3.2、Spring Security、Spring Modulith、JDBC/MyBatis-Plus、Flyway、EasyExcel、ShedLock；platform/identity/worktime/ledger/app 物理模块 | 按阶段引入文件、事件、RAG 和洞察能力 |
| 数据库 | MySQL 8，Flyway V1-V11 | 后续按需增加 Redis、MinIO/NAS、Qdrant 和搜索服务 |
| 部署 | Docker Compose、Nginx、Spring Boot、MySQL | 健康检查、备份恢复和可观测体系持续完善 |

当前请求关系：

```text
浏览器 → Nginx:80（Vue 静态页面和 /api 反向代理）
             └→ Spring Boot:8080 → MySQL:3306
```

## 项目结构

```text
salary-sync/
├── backend/
│   ├── app/                         # 应用组装与 Spring Boot 打包
│   │   └── src/                     # 装配、迁移、跨模块集成与架构测试
│   └── modules/                     # platform/identity/worktime/ledger 真实源码模块
├── frontend/
│   ├── src/views/                   # 工时和账本页面
│   ├── src/components/              # 通用与账本组件
│   ├── src/stores/                  # Pinia 状态
│   ├── src/api/                     # API 封装
│   └── packages/
│       ├── sync-engine/             # IndexedDB + oplog 同步引擎
│       ├── api-client/              # API 客户端入口
│       └── ui/                      # 共享 UI 基础组件
├── deploy/                          # Docker、Compose、Nginx、E2E 与恢复演练脚本
└── docs/                            # 架构、账本设计、部署说明和实施计划
```

Maven 子模块已经完成物理拆分。`app` 只负责应用装配、Flyway 资源和跨模块测试，不再通过自定义 source directory 编译中央源码；ArchUnit 同时校验依赖方向和源码目录归属。

## 本地开发

### 后端与开发数据库

推荐使用本地 MySQL 开发脚本：

```bash
bash deploy/dev-backend.sh
```

它会启动 `salary-mysql-dev`（默认端口 `3307`）、构建后端并监听 `http://127.0.0.1:8080`。

也可以自行准备 MySQL 8 数据库后启动：

```bash
cd backend
export DB_URL='jdbc:mysql://localhost:3306/salary?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export DB_USER='salary'
export DB_PASSWORD='你的数据库密码'
export JWT_SECRET='至少32字节的本地开发密钥'
mvn spring-boot:run
```

后端由 Flyway 执行版本化迁移；`schema.sql` 不参与启动初始化。

生产数据库同步脚本目前尚未显式适配仓库要求的 `workboard.pem`，在修复前不要执行 `bash deploy/dev-backend.sh --sync-production` 或 `bash deploy/sync-prod-db.sh`，也不要通过密码认证绕过。

### 前端

```bash
cd frontend
npm install
npm run dev
```

Vite 默认监听 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。应用需要后端认证；本地缓存用于工时缓存和账本投影，不代表只启动前端即可使用全部功能。

### 完整 Compose

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env，至少设置数据库密码、JWT_SECRET 和管理员初始密码
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

服务启动后访问 `http://localhost`。常用命令：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f backend
docker compose --env-file deploy/.env -f deploy/docker-compose.yml restart
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

不要使用 `down -v`，除非明确要删除 MySQL 数据卷。

## 认证与 API

- Spring Security 使用 JWT access token；默认有效期 15 分钟，仅保存在前端内存。
- Refresh token 默认有效期 30 天，通过 HttpOnly Cookie 轮换并持久化在 MySQL。
- 除健康检查、认证和节假日接口外，API 都需要 `Authorization: Bearer <access_token>`。
- OpenAPI/Swagger 开发入口为 `/swagger-ui.html` 和 `/v3/api-docs`。
- 旧 `/api/data` 和工时 snapshot 接口仍处于兼容期，不应被新功能继续依赖。

主要 API 分组：

| 路径 | 能力 |
|---|---|
| `/api/health` | 健康检查 |
| `/api/v1/auth/**` | 注册、登录、刷新、退出、修改密码 |
| `/api/v1/worktime/**` | 工时设置、记录 CRUD 和兼容 snapshot |
| `/api/v1/ledger/books/**` | 多账本、资源、预算、流水、报表数据、导入导出、同步、定时任务和 AI |
| `/api/v1/audit/logs` | 当前用户审计日志 |
| `/api/v1/ai/**` | 通用 LLM 网关入口 |
| `/api/holidays?year=2026` | 节假日与调休 |

从旧表回填的 `admin` 账户默认锁定。迁移部署时通过 `LEGACY_ADMIN_PASSWORD` 设置初始密码，登录后应立即修改或停用该账户。

## 构建与测试

```bash
cd backend
mvn test
mvn package

cd ../frontend
npm install
npm test
npm run build
npm run test:e2e
```

2026-09-17 的收口验证结果：Maven 58 个测试中 57 通过、1 个依赖本地真实 Excel 的可选用例跳过；前端 Node 41/41；Vite 生产构建成功；Playwright 6/6，覆盖 desktop Chromium 与 Pixel 7。后端 Testcontainers 实际覆盖 MySQL 权限、六类账本同步、Flyway 空库/旧 fixture 重放；恢复演练完成只读备份、临时库恢复、核心数据对账、Flyway v11 validate 和健康检查。完整阶段退出仍需视觉/无障碍、真机和真实导入金额对账记录。

## 服务器部署

所有 SSH 连接必须使用仓库根目录的 `workboard.pem`，禁止密码认证：

```bash
chmod 600 ./workboard.pem
ssh -i ./workboard.pem ubuntu@212.64.29.21
```

私钥已由根 `.gitignore` 排除。不得显示、复制、上传或提交私钥内容。

服务器安装 Docker Engine 和 Compose 插件后，在项目目录执行：

```bash
sudo bash deploy/deploy.sh
```

部署前需创建 `deploy/.env`，设置数据库密码、`JWT_SECRET`、`LEGACY_ADMIN_PASSWORD` 和与访问协议匹配的 `COOKIE_SECURE`。完整构建、跨架构镜像、局部更新和 HTTP/IP Cookie 排障见 [部署文档](docs/DEPLOY.md)。

## 当前优先级

1. 完成随手记真实工作簿重复导入与金额对账，并确定 MoneyWiz 支持范围。
2. 完成 WebKit、320/375/768/1024/1440px、深浅主题、键盘、无障碍和真机验收。
3. 将恢复脚本纳入季度演练，补发布回滚记录与生产监控。
4. Phase 0/1 通过全部退出门禁后，再进入任务管理模块。
