# 个人效率中枢 · 整体架构设计与长期发展规划

> 版本：v1.2（2026-09-04）
> 范围：基于现有 salary-sync（加班时长与时薪计算）系统，规划"工时 + 账本 + 任务 + AI"一体化个人效率平台的整体架构与演进路线。

---

## 目录

1. [现状评估与架构债务](#1-现状评估与架构债务)
2. [架构目标与设计原则](#2-架构目标与设计原则)
3. [整体架构分层设计](#3-整体架构分层设计)
4. [模块划分与依赖关系](#4-模块划分与依赖关系)
5. [技术选型建议](#5-技术选型建议)
6. [数据模型与存储方案](#6-数据模型与存储方案)
7. [离线同步方案](#7-离线同步方案)
8. [RAG + Agent 接入方式](#8-rag--agent-接入方式)
9. [跨模块数据联动流程设计](#9-跨模块数据联动流程设计)
10. [分阶段落地规划](#10-分阶段落地规划)
11. [非功能需求与风险](#11-非功能需求与风险)
12. [实施补充：任务拆解、迁移与验收](#12-实施补充任务拆解迁移与验收)

---

## 1. 现状评估与架构债务

### 1.1 现有资产盘点

| 层 | 现状 | 评价 |
|---|---|---|
| 前端 | 当前为 Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts，4 个视图（打卡/记录/统计/设置） | 业务资产可延续；UI 目标迁移到 Tailwind CSS + shadcn-vue，localStorage 优先 + 后端快照同步 |
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus，仅 2 个 Controller（data/holiday） | 骨架干净，但 API 为"整体快照"模式 |
| 数据库 | MySQL 8，3 张表（settings 单行 JSON / records 按日主键 / kv），无用户维度 | 需要重构为多用户、资源化模型 |
| 鉴权 | 静态 `ACCESS_CODE` 请求头拦截器（单口令） | 必须替换为完整用户体系 |
| 部署 | Docker Compose（Nginx + Spring Boot + MySQL），deploy.sh 一键部署 | 部署形态健康，可平滑扩展 |

### 1.2 关键架构债务（新架构必须解决）

1. **无用户体系**：所有表没有 `user_id`，鉴权是全局口令，无法支撑多账号与权限控制。
2. **快照式写入**：`PUT /api/data` 全量覆盖（DELETE 全表 + 逐条 INSERT），写入放大、无法并发、丢失中间历史——这是账本（强一致性要求）的致命伤。
3. **无变更历史**：没有操作日志、没有数据版本，无法做审计、离线合并与回滚。
4. **计算口径耦合在前端**：时薪计算逻辑在 `utils/calc.js`，后端仅存原始数据。报表联动要求计算口径在服务端有唯一权威实现（Single Source of Truth）。
5. **无模块边界**：单包 `com.salarytracker` 平铺结构，业务扩张后会迅速腐化。

---

## 2. 架构目标与设计原则

### 2.1 目标

- 以现有加班追踪为核心，演进出 **工时（Worktime）/ 账本（Ledger）/ 任务（Task）三大领域**，辅以 **洞察报表（Insight）** 与 **AI 能力（RAG + Agent）**，共同构成"个人效率中枢"。
- 支撑离线优先（local-first）的多端使用体验。
- 架构可在 1～N 个开发者规模下持续演进 3～5 年，不因早期偷懒而被迫推倒重来。

### 2.2 设计原则

| 原则 | 落地方式 |
|---|---|
| **模块化单体优先，不上微服务** | 单个 Spring Boot 应用内按领域划分 Maven Module（Spring Modulith 强制校验边界），进程内事件总线解耦；个人/小团队系统，微服务只会带来运维负担 |
| **资源化 API，拒绝快照** | 所有写操作为细粒度 CRUD（RESTful + 幂等键），服务端是唯一权威 |
| **事件驱动联动** | 跨模块数据联动一律通过领域事件（持久化事件表），模块间不直接调用彼此的 Service/DB |
| **Local-first** | 客户端 IndexedDB 为主存储，操作日志（oplog）双向增量同步，断网可用 |
| **多用户就绪** | 从第一天起所有业务表带 `user_id`、审计字段与行版本，即使当前只有一个人用 |
| **附件只存链接，blob 落 NAS** | 数据库只存 `storage_uri`，原始文件存 NAS（经 MinIO S3 网关暴露），RAG 管道按链接动态拉取 |
| **演进式引入** | 每个新技术组件（Redis/Qdrant/MinIO）只在对应阶段引入，避免一次搭完空转 |

---

## 3. 整体架构分层设计

```text
┌─────────────────────────────────────────────────────────────────┐
│  接入层   Nginx（静态资源 + /api 反代 + HTTPS + 限流）              │
│           PWA（Service Worker 离线缓存 + Web Push）               │
├─────────────────────────────────────────────────────────────────┤
│  应用层   模块化单体 Spring Boot（Spring Modulith）                │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐      │
│  │ identity │ worktime │  ledger  │   task   │ insight  │      │
│  │ 用户/权限 │ 工时/时薪 │ 账本/报表 │ 清单/日历 │ 报表聚合  │      │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘      │
│  ┌──────────┬──────────┬──────────┐                            │
│  │ ai(agent)│ file     │ schedule │   ← 支撑域                  │
│  │ RAG+Agent│ NAS/附件  │ 定时任务  │                            │
│  └──────────┴──────────┴──────────┘                            │
│  公共横切：JWT 鉴权 · RBAC · 操作日志(AOP) · 统一异常/响应体        │
├─────────────────────────────────────────────────────────────────┤
│  领域事件层  Spring Modulith 事件表（进程内发布 + 持久化 + 可重放）  │
├─────────────────────────────────────────────────────────────────┤
│  基础设施层 MySQL 8(主数据) · Redis(缓存/令牌/限流) ·               │
│             MinIO→NAS(附件blob) · Qdrant(向量) · LLM API 网关      │
└─────────────────────────────────────────────────────────────────┘
```

### 分层职责

| 层         | 职责                      | 关键约束                         |
| --------- | ----------------------- | ---------------------------- |
| 接入层       | TLS 终止、静态资源、API 反代、基础限流 | 不含业务逻辑                       |
| 应用层（领域模块） | 各领域业务逻辑、REST API、事务边界   | 模块间只允许通过公共 API（`api` 包）和事件交互 |
| 事件层       | 领域事件持久化、订阅、重放           | insight 只依赖事件与只读视图，绝不直连他人表   |
| 基础设施层     | 存储、缓存、对象存储、向量库、LLM      | 通过适配器接口隔离，可替换                |

### 前端分层（pnpm monorepo）

```text
frontend/
├── apps/
│   └── web/                  # 主应用（Vue 3 + Vite + PWA）
├── packages/
│   ├── ui/                   # 源码归属的 shadcn-vue 基础组件 + 领域组合组件
│   ├── api-client/           # 按模块生成的 API 客户端（OpenAPI 生成）
│   ├── sync-engine/          # 离线同步引擎（IndexedDB + oplog，核心资产）
│   └── shared/               # 计算口径、常量、工具函数
```

`packages/ui` 不再作为 Element Plus 的二次包装层，而是维护可审查、可定制的 shadcn-vue 源码组件；复杂交互使用其无障碍 primitive，页面通过组合组件复用能力。Element Plus 只在迁移期间保留，禁止被新页面和新业务模块继续引用。

> 计算口径迁移说明：现有 `utils/calc.js` 的时薪公式复制到后端 `worktime` 模块作为权威实现，前端仅保留展示层计算，两者通过 OpenAPI 中的常量定义保持一致，报表联动一律采用服务端计算结果。

---

## 4. 模块划分与依赖关系

### 4.1 模块清单

**核心域（业务价值）**

| 模块 | 职责 | 对应需求 |
|---|---|---|
| `worktime` | 打卡记录、工时计算、加班统计、时薪计算（权威口径）、节假日 | 已有功能迁入 |
| `ledger` | 账户、交易流水、分类、预算、账单周期、账本报表 | 账本（随手记形态） |
| `task` | 清单、任务、标签、筛选器、附件、提醒、日历视图、番茄钟、习惯打卡、倒数日、搜索 | 任务管理（滴答清单形态） |
| `insight` | 跨域数据聚合、日/周/月/年报生成与快照 | 数据联动 |

**支撑域（通用能力）**

| 模块 | 职责 | 对应需求 |
|---|---|---|
| `identity` | 注册登录、JWT 会话、RBAC 权限、用户资料 | 登录注册、权限控制 |
| `audit` | 操作日志（AOP 切面自动采集 + 异步落库 + 查询 API） | 操作日志 |
| `file` | 附件上传、NAS 存储链接管理、签名 URL、生命周期 | 附件 NAS 备份 |
| `ai` | LLM 网关、RAG 管道、Agent 编排与工具注册 | AI 助手、RAG+Agent |
| `schedule` | 定时任务注册中心（业务定时逻辑仍归各领域模块） | 定时任务 |
| `notification` | 提醒投递（站内/Web Push/邮件，策略路由） | 提醒 |

### 4.2 依赖规则（强制）

```text
                 ┌────────────┐
                 │  identity  │ ← 被所有模块依赖（仅限 user_id 解析接口）
                 └─────┬──────┘
                       │
 ┌───────────┐   ┌───────────┐   ┌───────────┐
 │ worktime  │   │  ledger   │   │   task    │    核心域互不依赖
 └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
       │  事件    │  事件    │  事件
       ▼         ▼         ▼
 ┌─────────────────────────────┐        ┌──────────┐
 │           insight           │ ◀────── │ ai(agent)│
 └─────────────────────────────┘  只读   └──────────┘
（订阅事件 + 读各域只读视图，绝不直写他域表）   （作为工具调用各域公共 API）
```

规则（由 Spring Modulith 的 `ApplicationModule` + ArchUnit 测试强制约束）：

1. **核心域之间零依赖**：worktime / ledger / task 互相不 import 对方代码、不读对方表。联动一律走事件或公共查询 API。
2. **insight 只读**：只订阅事件 + 读取各域暴露的只读投影视图（`*_view`），不产生对核心域的写操作。
3. **ai 通过工具调用各域公共 API**：Agent 的每个能力注册为 Tool（function calling），内部调用各模块 `api` 包，与前端同级待遇，天然继承权限。
4. **identity 是唯一的公共依赖**：且仅暴露 `CurrentUser` 上下文与用户查询接口。
5. **依赖方向单向**：核心域依赖 identity；insight、notification、ai 通过事件和各域 `api` 包消费能力；核心域之间禁止反向依赖。支撑域不得直接读写核心域表。

### 4.3 模块内部分层（每个模块统一）

```text
com.hub.<module>/
├── api/        # 对外 REST Controller + DTO + 公共接口（唯一对外出口）
├── app/        # 应用服务（用例编排、事务边界）
├── domain/     # 实体、领域服务、领域事件（纯业务，无框架依赖）
├── infra/      # Mapper、外部适配器（实现 domain 定义的端口）
└── event/      # 领域事件定义与监听器
```

---

## 5. 技术选型建议

### 5.1 后端

| 关注点       | 选型                                                                       | 理由与替代方案                                                                            |
| --------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| 框架        | **Spring Boot 3.2+ / Java 17**（延续现有）                                     | 已有资产直接延续；升级到 3.3+ 获得更好虚拟线程支持                                                       |
| 模块化       | **Spring Modulith**                                                      | 官方模块化单体方案：边界校验 + 持久化领域事件（`@ApplicationModuleListener`），零额外中间件即可做事件溯源式联动            |
| ORM       | **MyBatis-Plus**（延续）                                                     | 团队已熟悉；复杂查询仍写 XML                                                                   |
| 迁移        | **Flyway**                                                               | 替代现有 `schema.sql` 启动建表，任何表结构变更必须走版本化脚本                                             |
| 安全        | **Spring Security + JWT 双令牌**（access 30min / refresh 7d，Redis 管理刷新令牌与吊销） | 替换 AccessCodeInterceptor；密码 bcrypt；登录失败限流                                          |
| 权限        | **RBAC 三表模型 + `@PreAuthorize`**                                          | user / role / permission + 关联表；够用且可演进到数据级权限                                        |
| 缓存/限流     | **Redis 7**（Compose 新增）                                                  | 刷新令牌、验证码、接口限流、报表缓存；单机初期可延后到 Phase 2                                                |
| 定时任务      | **Spring Scheduling + ShedLock**（起步）→ **XXL-Job**（任务量 >20 或需可视化时）        | 周期账单、报表聚合、节假日抓取、提醒投递；ShedLock 保证多实例不重复执行                                           |
| Excel/CSV | **EasyExcel**                                                            | 账本导入导出（随手记/MoneyWiz 模板映射）                                                          |
| 文档解析      | **Apache Tika**                                                          | RAG 附件文本抽取（PDF/Office/文本）                                                          |
| AI 框架     | **LangChain4j**（首选）或 Spring AI                                           | Java 原生、Tool/Function Calling 成熟、与 Spring Boot 集成好；LLM 网关抽象支持 DeepSeek/OpenAI 兼容端点 |
| 向量库       | **Qdrant**（Docker 单容器）                                                   | 轻量、过滤检索（按 user_id/模块过滤）强；替代：pgvector（若引入 PG）；不建议 Milvus（过重）                        |
| 对象存储      | **MinIO（S3 网关模式挂载 NAS 目录）**                                              | NAS 上的文件获得 S3 API：签名 URL、分片上传、生命周期；数据库只存 `storage_uri`                             |
| API 文档    | **springdoc-openapi**                                                    | 前端 api-client 自动生成的基础                                                              |

### 5.2 前端

| 关注点 | 选型 | 说明 |
|---|---|---|
| 框架 | **Vue 3 + Vite + Pinia**（延续） | monorepo 化（pnpm workspace） |
| 样式系统 | **Tailwind CSS 4** | utility-first 响应式布局；通过 CSS 变量定义语义色、间距、圆角、阴影和明暗主题 |
| UI 基础组件 | **shadcn-vue**（源码归属）+ 无障碍 primitive | 按需加入 `packages/ui`，组件代码归项目所有；使用 `Button`、`Card`、`Dialog`、`Sheet`、`Drawer`、`Table`、`Tabs`、`Command`、`Badge`、`Alert`、`Skeleton` 等组合页面 |
| 图标 | **Lucide Vue** | 统一线性图标风格；图标作为组件传入，不在页面散落 SVG |
| 旧 UI 兼容 | **Element Plus**（仅迁移期） | 仅服务未迁移页面；禁止新功能依赖，逐页替换后移除依赖 |
| 日历视图 | **FullCalendar 6** | 日/周/月视图开箱即用；多日/多周切换用其 date navigation API；年视图自研（12 宫格热力图） |
| 图表 | **ECharts**（延续） | 账本报表 + 工时趋势 + 年度报告 |
| 本地存储 | **Dexie (IndexedDB)** | 替换 localStorage 作为主存储；localStorage 仅存会话与偏好 |
| 离线 | **vite-plugin-pwa** | Service Worker 静态资源缓存 + 后台同步 + Web Push |
| 提醒 | Web Push（VAPID）+ 站内通知中心 | 桌面端兜底浏览器 Notification |
| 搜索 | 起步 MySQL 全文索引 → 量大后 **Meilisearch** | 任务/账本/附件名统一搜索 |
| 番茄钟 | 纯前端实现（Web Timer + 本地统计上报） | 不依赖服务端计时 |

#### 5.2.1 UI 设计与响应式约束

目标是“一套业务代码、两类布局”：桌面和移动端共享路由、状态、API、校验和领域组件，但允许导航、编辑方式和信息密度随屏幕变化。禁止为 Android、iOS、桌面维护三套业务页面。

| 视口 | 布局策略 | 交互策略 |
|---|---|---|
| `<768px` 手机 | 单列内容；底部导航；表单使用全屏页或 `Drawer` | 触控优先；显式操作按钮；不依赖 hover；输入控件避免被软键盘遮挡 |
| `768–1023px` 平板/小屏 | 可折叠侧栏；双列卡片；日历优先日/周视图 | 兼容触控和鼠标；减少并排表格列 |
| `≥1024px` 电脑 | 侧边栏；多列卡片；表格和右侧编辑面板 | 支持键盘快捷键、批量操作和密集信息展示 |

统一约束：断点使用 CSS 媒体查询或容器查询，不通过 User-Agent 判断；可点击区域不小于 `44px`；适配 `safe-area-inset-*`；颜色使用语义 token（如 `bg-background`、`text-muted-foreground`），不在页面散落硬编码颜色；弹窗、抽屉、表单和空状态优先复用 shadcn-vue 组件；所有交互同时满足键盘可用、焦点可见和屏幕阅读器标签要求。

建议的组件分层如下：

```text
packages/ui/
├── components/ui/             # shadcn-vue 基础组件，源码归项目维护
├── components/domain/         # Worktime、Ledger、Task、Insight 组合组件
├── styles/tokens.css          # CSS variables、主题和设计 token
└── lib/utils.ts               # cn()、格式化和无障碍辅助函数

apps/web/src/
├── layouts/                   # DesktopShell、MobileShell、响应式导航
├── views/                     # 路由页面，只负责编排领域组件
└── styles/tailwind.css        # Tailwind 入口和全局基础样式
```

shadcn-vue 组件采用“组合而非大而全”的方式：例如设置页由 `Tabs + Card + Field` 组成，编辑操作使用 `Sheet/Drawer`，破坏性操作使用 `AlertDialog`，加载和空状态分别使用 `Skeleton` 与 `Empty`。组件 API、主题 token 和视觉基线必须在 `packages/ui` 中集中维护。

### 5.3 部署形态演进

```text
Phase 0-2（现有形态增强）          Phase 3+（AI 阶段）
┌─────────────────────┐          ┌─────────────────────────────┐
│ nginx               │          │ nginx                        │
│ ├─ web 静态资源      │          │ ├─ web（PWA）                │
│ └─ /api → backend   │          │ └─ /api → backend            │
├─────────────────────┤          ├─────────────────────────────┤
│ backend (boot)      │          │ backend (boot + LangChain4j) │
├─────────────────────┤          ├──────┬──────┬──────┬─────────┤
│ mysql   [redis]     │          │mysql │redis │qdrant│minio    │
└─────────────────────┘          │      │      │      └→ NAS卷  │
                                 └──────┴──────┴──────┴─────────┘
```

所有新组件均以 Docker Compose service 加入，`deploy.sh` 演进为带健康检查的服务编排；NAS 以 host volume 挂给 MinIO。

---

## 6. 数据模型与存储方案

### 6.1 全局约定

所有业务表统一携带以下字段（由公共 BaseDO + MyBatis-Plus 自动填充）：

```sql
id          BIGINT      主键（雪花 ID，为离线同步预留客户端可生成空间）
user_id     BIGINT      归属用户（所有查询强制注入，MyBatis-Plus 租户插件）
created_at  DATETIME
updated_at  DATETIME
deleted     TINYINT     软删除标记
revision    BIGINT      行版本（乐观锁 + 同步合并依据）
```

### 6.2 公共基础（identity + audit）

```sql
user            (id, username, password_hash, nickname, email, phone, status, ...)
role            (id, code, name)                      -- ADMIN / USER
permission      (id, code, module, action)            -- 如 ledger:transaction:write
user_role       (user_id, role_id)
role_permission (role_id, permission_id)
refresh_token   (id, user_id, token_hash, device, expires_at, revoked)
audit_log       (id, user_id, module, action, target_type, target_id,
                 detail_json, ip, user_agent, cost_ms, created_at)
```

> 操作日志由 AOP 切面（`@Audit(module, action)` 注解）在写接口上自动采集，`@Async` 异步批量落库，不侵入业务代码；日志表按月分区，超期归档到 NAS。

### 6.3 工时域（worktime，迁移现有 records）

```sql
work_record     (id, user_id, date, start_time, end_time, rest_min,
                 overtime_min, real_hourly_wage,        -- 服务端按权威口径计算落库
                 note, revision)
work_setting    (id, user_id, salary_pre, salary_post, basis,
                 work_start, work_end, lunch_min, days_per_month, auto_days)
salary_monthly  (id, user_id, month, salary_pre, salary_post)
holiday         (id, year, date, name, is_off)          -- 从 resources JSON 迁入库表
```

迁移要点：现有 `records` 表数据一次性脚本迁入 `work_record`（补 user_id=管理员）；`GET/PUT /api/data` 保留 6 个月兼容期后下线。

### 6.4 账本域（ledger）

```sql
account         (id, user_id, name, type, balance_init, currency, sort, archived)
                -- type: cash|bank|credit|virtual（负债账户）
category        (id, user_id, parent_id, name, kind, icon, sort)
                -- kind: expense|income|transfer；两级分类
transaction     (id, user_id, account_id, category_id, amount DECIMAL(12,2),
                 type, date, payee, note, tags_json,
                 cleared, recurring_id, revision)
                -- append-only：修改=新版本行，删除=软删；amount 恒为正，方向由 type 决定
budget          (id, user_id, category_id, period, amount, start_date, end_date)
recurring       (id, user_id, name, rule_json, next_run_at, amount, category_id,
                 account_id, enabled)                    -- 周期账单（房租/订阅）
```

设计要点：

- **交易流水 append-only + 软删**：账本数据的生命线是可追溯，任何修改留痕（为对账与 AI 分析保留历史）。
- 余额不做实时 SUM：`account_balance` 由每日定时任务物化（`account_daily_balance` 快照表），避免长事务大聚合。
- **金额用 DECIMAL，不用 FLOAT**。

### 6.5 任务域（task）

```sql
task_list       (id, user_id, name, color, sort, kind, archived)
                -- kind: list|folder（支持清单分组）
task            (id, user_id, list_id, parent_id, title, content, priority,
                 status, due_at, start_at, completed_at,
                 progress, recurrence_rule, sort, pinned, revision)
tag             (id, user_id, name, color)
task_tag_rel    (task_id, tag_id)
filter          (id, user_id, name, condition_json, sort)   -- 用户自定义筛选器
task_attachment (id, user_id, task_id, file_id, name)       -- 关联附件（见 file 域）
task_activity   (id, user_id, task_id, type, from_json, to_json, created_at)
                -- 任务日志：创建/完成/改期/加标签…（领域事件快照，供时间线与AI）
pomodoro        (id, user_id, task_id, started_at, minutes, completed, tag)
habit           (id, user_id, name, icon, frequency_json, goal, archived)
habit_checkin   (id, user_id, habit_id, date, value)
countdown       (id, user_id, name, target_date, repeat_yearly, icon)
reminder        (id, user_id, target_type, target_id, remind_at, channel, status)
```

日历视图不建表：日/周/月/年由 `task(due_at/start_at)` + `work_record(date)` + `transaction(date)` 三源聚合渲染；日程（有起止时间的任务）与打卡、记账在同一日历叠加展示。

### 6.6 附件与文件域（file —— NAS 方案核心）

```sql
file_object     (id, user_id, original_name, ext, size, mime,
                 sha256,          -- 内容寻址 + 秒传/去重
                 storage_uri,     -- s3://attachments/2026/09/xx.ext（NAS 上真实位置）
                 status,          -- uploaded|parsing|parsed|failed
                 biz_type, biz_id -- 归属：task_attachment / ledger_bill / rag_doc
                 )
rag_document    (id, user_id, file_id, title, chunk_count, parse_status, ...)
rag_chunk       (id, user_id, doc_id, chunk_index, content,
                 vector_id        -- Qdrant 中的 point id
                 )                -- MySQL 存元数据与原文，向量在 Qdrant
```

**NAS 备份与动态拉取**：

- 附件上传：后端直传 MinIO（S3 分片上传）→ MinIO 后端目录即 NAS 卷 → 定期 `rclone`/`restic` 对 NAS 做异地备份。
- 数据库只存 `storage_uri`，任何消费方（前端预览、RAG 管道、Agent 工具）都通过**签名 URL（15min 有效）动态拉取**，不复制 blob，不落本地磁盘。
- RAG 管道解析失败的文档保留 `storage_uri`，可随时重新入队。

### 6.7 洞察域（insight —— 联动报表）

```sql
domain_event    (id, event_type, aggregate_type, aggregate_id, user_id,
                 payload_json, published_at)          -- Spring Modulith 事件表
report_fact     (id, user_id, date,                   -- 日粒度事实表
                 work_minutes, overtime_minutes, real_wage,
                 expense_total, income_total,
                 task_created, task_completed)
report_snapshot (id, user_id, period,     -- daily|weekly|monthly|yearly
                 period_key,              -- 2026-09-01 / 2026-W36 / 2026-09 / 2026
                 content_json, ai_summary, generated_at)
```

---

## 7. 离线同步方案

### 7.1 总体模型：oplog 双向增量同步

```text
客户端（IndexedDB 为主存储）
   │  写操作 → 本地落库成功即返回（UI 零延迟）
   │        → 同时追加本地 oplog {op_id(uuid), entity, action, data, base_rev}
   │
   ├─ push: 增量提交 oplog ──→ 服务端按序合并（乐观锁/版本冲突返回 409 + 服务端版本）
   └─ pull: cursor 增量拉取 ──→ 服务端返回 {changed: [...], deleted: [...], cursor}
```

### 7.2 关键设计

| 问题 | 方案 |
|---|---|
| 主键 | 雪花 ID 由**客户端生成**，离线创建不冲突 |
| 冲突 | 同一行并发修改：`revision` 乐观锁；冲突默认 **LWW（后写胜）**，但对 `transaction.amount` 等敏感字段返回冲突让用户手工合并 |
| 删除 | 软删除墓碑（tombstone），30 天后物理清除 |
| 顺序 | 每用户一个单调 `sync_cursor`；服务端按 op 到达顺序定序 |
| 范围 | 仅同步"我拥有的数据"：全部个人业务表；字典数据（分类/节假日）只拉不改 |
| 降级 | 同步引擎对上层（Pinia store）透明；断网时 UI 全功能可用 |

> 账本的 append-only 特性使它天然适合 oplog 同步；任务/工时为行级 LWW。`packages/sync-engine` 是前端最重要的长期资产，独立包维护 + 完整单测。

---

## 8. RAG + Agent 接入方式

### 8.1 附件 → RAG 管道（异步、可重试）

```text
上传附件(task/ledger)
  → file 模块存 MinIO(NAS)，写 file_object(status=uploaded)
  → 发布 AttachmentStored 事件
  → ai 模块 RAG 管道消费：
      ① 按 storage_uri 签名 URL 动态拉取原文件（不落本地盘，流式）
      ② Tika 抽取文本 → 清洗 → 按语义分块（512~1024 token，标题感知）
      ③ Embedding（API 或本地 bge-m3，经 LLM 网关统一路由）
      ④ 写 Qdrant（payload: user_id/doc_id/chunk_index，强过滤 user_id）
      ⑤ 回写 rag_document/rag_chunk 元数据，status=parsed
  失败 → status=failed，可从 storage_uri 随时重新入队
```

### 8.2 Agent 架构（工具型 Assistant）

```text
用户消息（对话/报表生成/自然语言记账）
  → ai 模块：Agent 编排（LangChain4j）
      ├─ 意图路由：闲聊 | 工具调用 | RAG 检索
      ├─ RAG：Query → Qdrant 检索（过滤 user_id + 业务域）→ 上下文注入
      └─ Tools（function calling，权限随当前用户）：
           · worktime_query(range)      查工时/加班/时薪
           · ledger_query(range, dims)  查收支/分类/预算执行
           · task_query(filter)         查任务/清单/习惯
           · insight_report(period)    生成日/周/月/年报
           · attachment_fetch(file_id)  按 storage_uri 动态拉取附件内容
           · ledger_book(entry)         自然语言记账（"午饭 32 元"→结构化交易）
           · task_create(...)           自然语言建任务
  → LLM 网关（DeepSeek / OpenAI 兼容端点可配置，密钥服务端持有）
  → 流式响应（SSE）回前端对话组件
```

### 8.3 关键约束

1. **权限同源**：Agent 工具内部调用各域 `api` 包，与 REST 同一套 `@PreAuthorize`，杜绝 AI 越权读写。
2. **成本控制**：会话上下文按条数+token 双截断；RAG 检索 top-k 默认 5；LLM 调用计入 `ai_usage` 表便于监控。
3. **可观测**：每次 Agent 执行落 `agent_trace`（工具调用链、token 消耗、耗时），排查"AI 答错了"必备。
4. **LLM 供应商可替换**：网关层统一 `ChatModel` 抽象，DeepSeek/通义/OpenAI 用配置切换，不绑死单一厂商。

---

## 9. 跨模块数据联动流程设计

### 9.1 联动机制：领域事件 → 日事实表 → 周期快照

```text
[worktime] 打卡保存 ──┐
[ledger] 交易写入 ────┼──→ 领域事件(Modulith持久化事件表)
[task] 任务完成 ──────┘         │
                               ▼ (异步监听，失败自动重试)
                    insight: report_fact 当日行 upsert
                               │
                               ▼ (定时任务: 每日 02:00 / 每周一 06:00 / 每月1日 / 每年)
                    insight: report_snapshot 生成
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
              报表中心页面渲染        ai 模块：AI 总结（年报含趋势洞察）
              (ECharts + 导出)       (调用 insight_report 工具 + RAG 上下文)
```

### 9.2 事件目录（首批）

| 事件 | 发布方 | 订阅方 | 用途 |
|---|---|---|---|
| `WorkRecordSaved` | worktime | insight | 工时/加班入事实表 |
| `TransactionRecorded` | ledger | insight, ai | 支出入事实表；AI 记账回执 |
| `TaskCompleted` | task | insight | 任务完成数入事实表 |
| `AttachmentStored` | file | ai | 触发 RAG 解析管道 |
| `BudgetExceeded` | ledger | notification | 预算超支提醒 |
| `ReminderDue` | task | notification | 提醒投递 |
| `ReportGenerated` | insight | notification | 日报推送（早 8 点站内/Push） |

### 9.3 报表内容矩阵

| 周期 | 加班/工时 | 账本 | 任务 | AI 增强 |
|---|---|---|---|---|
| 日报 | 当日工时、加班、实际时薪 | 当日收支 | 完成/新建/逾期任务数 | 一段话总结 + 明日建议 |
| 周报 | 本周加班趋势、周薪折算 | 分类支出环比 | 完成率、番茄钟专注时长 | 趋势归因（结合 RAG 笔记） |
| 月报 | 月加班、月薪/时薪曲线 | 收支平衡、预算执行、Top 分类 | 任务吞吐、习惯达成率 | 月度洞察长文（可导出 PDF） |
| 年报 | 年加班总时长、时薪变化 | 年度收支总账、储蓄率 | 年度任务完成率、专注统计 | 年度回顾报告（AI 撰写） |

> 时薪口径以服务端 `work_record.real_hourly_wage` 落库值为准，报表不再前端计算——保证三个模块数字永远一致。

> **阶段依赖修正**：Phase 0/1 先交付事件信封、幂等和消费位点等基础设施，但不提前交付完整 insight。Phase 1 的 AI 仅包含账本自然语言记账；`insight_report` 工具和跨域报表必须等 Phase 4 的事实表与快照稳定后启用。这样可以避免 Agent 依赖尚未存在的读模型。

---

## 10. 分阶段落地规划

> 节奏设计原则：每阶段 2～6 周可交付、可上线、可回滚；先地基后上层；AI 相关放后（依赖数据积累）。

所有改动发生后，进行打包IP地址：212.64.29.21 用户名：ubuntu 密码：wasd123.
依旧使用docker打包后发送到主机，停止原来服务后使用新的镜像进行启动部署

### Phase 0 —— 地基重构（一切的前置）

**目标**：把单用户快照系统改造成多用户资源化系统，旧功能行为不变。

- [ ] 后端改造为多 Maven Module（platform/identity/worktime），引入 Spring Modulith + ArchUnit 边界测试
- [ ] Flyway 接管表结构；`work_record` 迁移（加 user_id/revision），旧数据脚本迁入
- [ ] identity：注册/登录/JWT 双令牌/Spring Security；下线 AccessCode
- [ ] RBAC 三表 + `@PreAuthorize`；audit：AOP 操作日志 + `audit_log` 表 + 查询 API
- [ ] API 资源化：`/api/worktime/records` CRUD 替代 `/api/data` 整体快照（旧接口保留兼容期）
- [ ] 时薪计算口径迁到后端（`worktime` 领域服务），前端 calc.js 降级为展示用途
- [ ] 统一响应体/异常处理/springdoc-openapi；前端 api-client 按模块重写
- [ ] 前端建立 Tailwind CSS + shadcn-vue 设计基座：`components.json`、语义化 CSS tokens、`packages/ui` 基础组件、桌面/移动响应式应用壳
- [ ] 按“应用壳 → 设置 → 打卡 → 记录 → 统计”顺序迁移现有页面；迁移期间 Element Plus 仅保留在未迁移页面
- **验收**：老数据无损迁移；登录后所有旧功能可用；ArchUnit 边界测试进 CI；操作日志可查；核心页面在手机、平板、电脑视口无横向溢出且交互可用。

### Phase 1 —— 账本（随手记形态）

- [ ] 账户/分类/交易 CRUD（append-only + 软删）；月末余额物化任务
- [ ] 账本响应式页面（shadcn-vue `Card`/`Table`/`Sheet`/`Drawer`）：桌面多列、移动单列录入和列表
- [ ] 账本报表（ECharts：收支趋势、分类占比、预算执行），图表容器适配窄屏和深色主题
- [ ] 导入导出：CSV/Excel（EasyExcel）+ 随手记/MoneyWiz 模板映射
- [ ] **离线同步引擎 v1**（sync-engine 包）：IndexedDB 主存储 + oplog 双向同步（先覆盖 ledger 域）
- [ ] 周期账单 recurring + 到期生成交易（ShedLock 定时任务）
- [ ] AI 助手雏形：LLM 网关 + 自然语言记账工具（无 RAG）
- **验收**：断网记账→联网自动合并无丢失；周期账单自动生成；导入随手记 CSV 成功。

### Phase 2 —— 任务管理（滴答清单形态）

- [ ] 清单（含分组）/任务/子任务/标签/优先级/自定义筛选器；使用 shadcn-vue `Command`、`Tabs`、`DropdownMenu` 组合筛选交互
- [ ] 任务日志 task_activity（时间线）；附件关联（file 域先行落地：MinIO→NAS + 签名 URL）
- [ ] 日历视图：FullCalendar 日/周/月 + 自研年视图；多日/多周切换；移动端默认日/周视图，桌面端支持月视图；与打卡、记账叠加显示
- [ ] 提醒：reminder 表 + notification 模块（站内 + Web Push）
- [ ] 番茄钟（前端计时 + 统计上报）；习惯打卡；倒数日/纪念日
- [ ] 全局搜索 v1（MySQL 全文索引，任务+账本+附件名）；离线同步覆盖 task 域；桌面快捷键与移动端显式入口一致
- **验收**：任务全流程离线可用；提醒准点送达（±1min）；日历四视图切换流畅；手机端无 hover-only 操作。

### Phase 3 —— RAG + Agent + NAS 深化

- [ ] RAG 管道：Tika 解析 → 分块 → embedding → Qdrant（Qdrant 容器上线）
- [ ] 附件知识库页面：文档列表/解析状态/重新解析/删除（向量联动清除）
- [ ] Agent 编排（LangChain4j）：意图路由 + 工具注册（worktime/ledger/task/insight 查询类工具）
- [ ] attachment_fetch 动态拉取工具（签名 URL 流式读取）
- [ ] agent_trace 可观测 + ai_usage 计量；对话历史落库
- [ ] NAS 备份自动化（restic 定时快照 + 异地同步）
- **验收**：上传 PDF 后可就其内容问答（答案含引用定位）；Agent 能正确回答"这个月加了多少班、花了多少钱、完成了几个任务"。

### Phase 4 —— 数据联动与全景报表

- [ ] domain_event 事件表 + insight 监听器上线，report_fact 日聚合
- [ ] 日报/周报/月报/年报快照生成（定时任务）+ 报表中心页面
- [ ] AI 报告：日报推送（早 8 点）、月报/年报 AI 总结与导出（PDF/Markdown）
- [ ] 旧 `/api/data` 快照接口下线；前端 localStorage 旧数据迁移完成
- **验收**：三域数据在任意报表中口径一致；断开任一模块，其余模块不受影响（联动仅靠事件，天然解耦）。

### Phase 5 —— 打磨与规模化（持续）

- 全局搜索升级 Meilisearch；多端适配（PWA 安装体验、平板布局）
- 视觉系统收敛：移除所有 Element Plus 依赖；统一 shadcn-vue 组件版本、设计 token、响应式回归和无障碍基线
- 数据级权限（家庭/团队共享账本与清单：share 表 + 邀请机制）
- 可观测体系：Actuator + Prometheus + Grafana；慢查询治理
- 备份演练自动化（季度性恢复演练）；XXL-Job（如任务规模需要）

### 里程碑总览

```text
Phase 0 地基 ──→ Phase 1 账本 ──→ Phase 2 任务 ──→ Phase 3 RAG/Agent ──→ Phase 4 联动报表 ──→ Phase 5 打磨
  多用户/RBAC      离线同步v1        附件/NAS/日历      向量库/Agent        事件联动/年报       规模化
  审计/资源化API   周期账单/AI记账    提醒/番茄钟/习惯    知识库问答          AI日报月报          共享/可观测
```

---

## 11. 非功能需求与风险

### 11.1 非功能需求

| 维度 | 要求 |
|---|---|
| 安全 | 全站 HTTPS；bcrypt(cost≥10)；JWT 短时效 + 刷新吊销；接口限流（Redis 令牌桶）；上传文件类型白名单 + 病毒扫描位预留 |
| 备份 | MySQL 每日 mysqldump → NAS；NAS restic 快照 → 异地；恢复演练每季度 |
| 性能 | 核心页面接口 P95 < 300ms；报表读快照（report_snapshot）不实时聚合；列表分页 + 游标 |
| 可用性 | Compose healthcheck 全覆盖；deploy.sh 滚动更新；单机即可支撑百级用户 |
| 数据一致性 | 金额 DECIMAL；跨域只经事件；关键写路径幂等键（客户端 op_id 去重） |

### 11.2 主要风险与对策

| 风险 | 对策 |
|---|---|
| 离线同步是最复杂资产，后期返工代价极高 | Phase 1 即落地 sync-engine 独立包 + 冲突单测矩阵；先覆盖数据结构最简单的账本域验证模型，再复制到任务/工时域 |
| AI 幻觉导致错误记账/错误报告 | 写类工具（记账/建任务）执行前展示结构化预览让用户确认；查询类工具结果附数据来源（日期范围+行数） |
| NAS 单点 | restic 异地备份 + 完整性校验；MinIO 存储元数据（storage_uri）支持挂载点迁移 |
| LLM 供应商锁定/涨价 | 网关层抽象 + 多端点配置热切换；embedding 模型版本记录在 rag_document（换模型需重索引） |
| 模块边界腐化 | ArchUnit + Modulith 边界测试进 CI，违规即构建失败 |
| 单人开发节奏失控 | 严格按 Phase 交付，每阶段先出可运行骨架再补细节；拒绝跨阶段提前引用未落地组件 |

## 12. 实施补充：任务拆解、迁移与验收

本节将架构方案转换为可以排期、评审和回滚的执行计划。文档中的技术选型和阶段目标是设计建议，不是绕过用户确认即可执行的外部操作指令；真正的实施以本节的门禁和待决策项为准。

### 12.1 现状基线与优先级

已对照当前仓库核验：后端仍是单 Maven 模块、`/api/data` 全量覆盖写入、`schema.sql` 启动初始化三张表；前端仍以 `localStorage` 和单 Pinia store 保存快照；鉴权仍是 `X-Access-Code`。因此先处理数据安全和兼容性，再扩展业务功能。

| 优先级 | 必须解决的问题 | 未解决的后果 | 截止门禁 |
|---|---|---|---|
| P0 | 备份/恢复、Flyway、`user_id`、资源化写入、服务端计算 | 数据丢失、跨用户越权、快照并发覆盖 | Phase 0 退出前全部通过 |
| P0 | 认证会话、租户隔离、审计和敏感数据脱敏 | 无法安全上线多用户和 AI | 首个新 API 上线前 |
| P1 | 事件信封、幂等键、同步契约、冲突处理 | 跨域联动和离线能力不可验证 | Phase 1 账本上线前 |
| P1 | 领域模块边界、契约测试、迁移演练 | 后续扩展持续积累架构债务 | 合并到主分支前 |
| P2 | Qdrant、MinIO、Meilisearch、推送和可观测平台 | 影响体验，不阻塞核心账本/工时 | 按对应 Phase 引入 |

### 12.2 Phase 0 详细任务包

以单人开发估算为 6～8 周，以小团队可并行压缩；每个任务包完成后都要有可运行版本和回滚点。

| 任务包 | 主要工作 | 依赖 | 产出与完成定义 |
|---|---|---|---|
| 0A 基线冻结（2～3 天） | 生产数据导出、记录/设置数量和校验和；建立 API 快照和计算口径黄金样例；加 CI 基线 | 无 | 可重复导出；现有 `mvn test`、前端构建和部署脚本结果留档 |
| 0B 数据地基（1～1.5 周） | 引入 Flyway；创建 `app_user`、权限、审计和 `work_*` 新表；补唯一键、索引、时区字段；保留旧表 | 0A | V1～V3 迁移可重复执行；空库和带旧数据的库均能启动 |
| 0C 身份与安全（1 周） | Spring Security；注册/登录/刷新/退出；短时 access token；刷新令牌吊销；租户上下文；统一 401/403 | 0B | 未认证不能访问业务 API；用户 A 无法读取用户 B；安全测试通过 |
| 0D 工时资源化（1～1.5 周） | records/settings 迁移到 worktime；记录/设置 CRUD、分页和 `If-Match`；服务端权威计算；节假日版本化 | 0B、0C | 新旧接口结果对账；并发更新返回 409；计算黄金样例 100% 一致 |
| 0E 兼容适配（3～5 天） | 旧 `/api/data` 变为兼容适配器；增加灰度开关、请求指标和弃用响应头；禁止新代码调用旧接口 | 0D | 旧前端仍可用；新前端可按开关切换；可一键退回旧读路径 |
| 0F 前端迁移（1.5～2 周） | `api-client` 按 OpenAPI 生成；Pinia 按域拆分；初始化 Tailwind CSS + shadcn-vue；建立 `components.json`、CSS tokens、`packages/ui` 和响应式应用壳；按页面顺序迁移；localStorage 一次性迁移；错误/登录过期状态统一处理 | 0D、0E | 新旧页面关键流程回归通过；目标页面不再引用 Element Plus；320/375/768/1024/1440px 视口无横向溢出；刷新/登出不会泄露凭据 |
| 0G 地基验收（2～3 天） | Testcontainers 集成测试、契约测试、迁移重放、备份恢复演练、安全检查、视觉回归和键盘/触控可用性检查 | 0A～0F | 满足 12.5 的退出清单；形成发布说明和回滚步骤；核心页面通过桌面、Android Chrome、iOS Safari/PWA 验收 |

### 12.3 API v1 最小契约

所有新接口统一挂在 `/api/v1`，旧接口只作为兼容层。响应使用 RFC 9457 Problem Details 风格的错误体，并携带 `trace_id`。

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/worktime/settings
PUT    /api/v1/worktime/settings              If-Match: <revision>
GET    /api/v1/worktime/records?from=&to=&cursor=&limit=
POST   /api/v1/worktime/records               Idempotency-Key: <op_id>
PATCH  /api/v1/worktime/records/{id}           If-Match: <revision>
DELETE /api/v1/worktime/records/{id}           If-Match: <revision>
POST   /api/v1/sync/push
GET    /api/v1/sync/pull?cursor=&limit=
```

契约约束：

1. 所有列表都必须分页，默认 `limit=50`、最大 `200`；时间范围和时区由用户配置解释。
2. 写请求必须支持幂等键；同一用户同一 `op_id` 重放返回原结果，不重复产生流水、事件或审计记录。
3. 更新必须带 `If-Match`/`revision`；版本不一致返回 `409`，响应同时给出服务端版本和可合并字段。
4. 删除返回墓碑版本，不立即物理删除；游标过期或墓碑已清理时返回 `410 SYNC_RESET_REQUIRED`，客户端执行全量重同步。
5. 金额、时间和枚举禁止使用隐式字符串转换；金额使用 `DECIMAL(12,2)`，时间使用 ISO-8601，并明确用户时区。

### 12.4 数据与事件模型补充

1. MySQL 中不要使用 `user` 作为表名，统一使用 `app_user`；`username`、邮箱、设备标识、业务自然键建立带作用域的唯一索引。
2. `work_record` 保存 `calc_version`、`timezone`、包含税前/税后口径和排班天数的 `salary_snapshot`、`calculated_at`，防止未来修改设置后历史时薪漂移；`date`、`start_time`、`end_time` 使用明确的 `DATE`/`TIME` 类型。
3. 账本转账必须有 `transfer_group_id`，一笔转账对应借贷两条不可分割的流水；金额禁止用浮点；余额快照必须记录生成游标和重算时间。
4. 事件采用统一信封：`event_id`、`event_type`、`schema_version`、`aggregate_id`、`user_id`、`occurred_at`、`payload`、`trace_id`。发布和业务写入同一事务，监听器按 `event_id` 去重。
5. 增加 `event_consumer_checkpoint` 和失败重试/死信状态；事件消费失败不回滚原业务写入，可从指定游标重放。`report_fact` upsert 必须幂等。
6. 同步元数据单独建表（设备、`op_id`、实体版本、墓碑和游标），不要把客户端 oplog 直接混入业务表；客户端生成 ID 优先采用 UUIDv7/ULID，若坚持雪花 ID，必须固定 worker 分配和时钟回拨策略。

### 12.5 迁移、灰度与回滚 runbook

```text
备份并校验 → 建新表/迁移脚本 → 回填旧数据 → 新旧读对账
   → 灰度新 API（只读） → 灰度写入（兼容双写） → 观察窗口
   → 切换默认读写 → 旧 PUT 只读/下线 → 保留旧表至兼容期结束
```

- **备份**：切换前执行 MySQL 全量备份和 NAS 文件清单校验；记录备份 ID、校验和、恢复耗时。
- **回填**：旧 `settings` 映射为每用户 `work_setting`，旧 `records` 映射为 `work_record`；异常行进入隔离表，不得静默丢弃。
- **对账**：按用户和日期比较记录数、设置字段、工时、加班和时薪；允许的舍入误差必须写入规则。
- **灰度**：先 shadow read，新旧结果不一致只告警；写入阶段以新表为主，兼容层按 `migration_version` 控制，避免无限期双写。
- **回滚**：应用故障通过 feature flag 回到旧读路径；数据故障从最近备份恢复到临时库并重放迁移，不在生产直接执行破坏性回滚。
- **退出**：连续 7 天无 P0/P1 对账差异、旧接口调用量为 0 后，才移除旧 PUT；旧表至少保留 6 个月并完成一次恢复演练。

### 12.6 测试与质量门槛

| 层级 | 必测内容 | 门槛 |
|---|---|---|
| 领域单测 | 跨午夜、早退、节假日调休、月薪封顶、舍入和时区边界 | 计算核心分支覆盖率 ≥95%，黄金样例全通过 |
| 后端集成 | Flyway、权限隔离、并发版本、幂等、事件重试 | Testcontainers MySQL 全绿 |
| API 契约 | OpenAPI 生成客户端与服务端字段/错误码一致 | 契约差异阻断合并 |
| 前端单测 | store、迁移脚本、同步合并和离线重放 | 关键状态转换全覆盖 |
| E2E | 登录、打卡、编辑、导入导出、断网记账、刷新恢复；桌面/移动导航和 Sheet/Drawer 操作 | Chromium、WebKit 主流程通过；Android Chrome 与 iOS Safari/PWA 至少各有一次真机验收 |
| 视觉与无障碍 | 320/375/768/1024/1440px 视口、深色/浅色主题、键盘焦点、触控热区、安全区和软键盘遮挡 | 无横向溢出；触控目标 ≥44px；关键页面满足 WCAG 2.2 AA 基线；视觉差异需人工确认 |
| 运维演练 | 备份恢复、迁移重放、事件重放、旧接口回滚 | RPO ≤24h、RTO ≤2h；步骤可由他人复现 |

### 12.7 安全、隐私与 AI 上线门槛

- access token 只放内存；refresh token 使用 `HttpOnly + Secure + SameSite` Cookie（或明确的移动端替代方案），避免将长期凭据放入 `localStorage`。
- 登录、刷新、导入、上传和 Agent 工具调用分别限流；日志禁止记录密码、令牌、完整财务明细和附件原文。
- 附件按扩展名、MIME、大小和内容探测双重校验；预留病毒扫描和压缩炸弹防护；签名 URL 默认 15 分钟且按用户授权生成。
- AI 默认脱敏并明确“不用于供应商训练”的端点策略；RAG 过滤必须同时包含 `user_id` 和业务域。文档内容视为不可信输入，禁止其改变系统提示或工具权限。
- `ledger_book`、`task_create` 等写工具必须先返回结构化预览并二次确认；工具失败、超时、重复提交都要可追踪且不产生半笔交易。
- 提供用户数据导出、删除和撤回 AI 处理授权的入口；明确审计日志、向量和附件的保留期限。

### 12.8 需要在编码前确认的产品决策

| 决策项 | 推荐默认值 | 影响范围 |
|---|---|---|
| 首个版本的账号策略 | 邀请制单用户，保留公开注册开关 | identity、部署和支持成本 |
| 用户时区 | `Asia/Shanghai`，每用户可配置 | 日期边界、报表、提醒和同步 |
| 默认货币 | CNY，账本预留多币种字段但 Phase 1 不做汇率 | 金额、导入和报表 |
| 旧接口兼容期 | 6 个月，期间只读监控 | 前端迁移和回滚 |
| LLM 供应商 | OpenAI 兼容网关，默认关闭外发敏感字段 | 成本、隐私和 Agent 能力 |
| 数据保留 | 审计 12 个月、墓碑 90 天、附件按用户策略 | 存储成本和离线重同步 |
| 发布方式 | 单机 Compose + 手动审批，稳定后再做自动滚动 | 运维复杂度和可用性 |

### 12.9 全阶段门禁与并行策略

| 阶段 | 进入条件 | 本阶段必须交付 | 退出门禁 |
|---|---|---|---|
| Phase 0 地基 | 决策项确认；完成 0A 备份 | identity、worktime v1、迁移/兼容层、CI、恢复演练、Tailwind/shadcn-vue 设计基座和响应式应用壳 | 新旧数据对账无 P0/P1 差异；可回滚；核心页面通过三类视口和基础无障碍检查 |
| Phase 1 账本 | Phase 0 通过；事件信封和同步契约冻结 | 账户/交易/预算、导入导出、sync-engine v1、周期账单、自然语言记账预览、账本桌面/移动布局 | 断网记账无重复/丢失；金额对账通过；写工具必须确认；手机端录入无需缩放 |
| Phase 2 任务 | sync-engine ledger 稳定；file 接口冻结 | 任务/日历/提醒/附件关联/番茄钟/习惯、移动端底部导航和日历布局 | 离线任务重放通过；提醒误差 ≤1 分钟；附件权限隔离；无 hover-only 核心操作 |
| Phase 3 RAG/Agent | file/NAS 和隐私策略通过；供应商预算确定 | 解析、向量索引、引用问答、查询工具、AI 可观测 | 每个答案可定位来源；越权检索为 0；失败可重试 |
| Phase 4 联动 | 事件重试和事实表稳定；口径冻结 | 日/周/月/年报、AI 总结和推送 | 事件重放后报表可重建；三域数字对账一致 |
| Phase 5 持续 | 监控、备份和告警已启用 | 搜索、多端、共享、规模化运维 | 按 SLO 和季度恢复演练持续评估 |

执行上允许并行的只有不改变契约的工作：前端 UI 草图、OpenAPI 文档、领域单测和部署脚本可并行；数据库迁移、认证切换、同步协议和 Agent 写工具必须按门禁串行推进。

### 12.10 仓库演进骨架

不要在一次提交中同时改包名、构建系统、数据库和业务行为。推荐先保留 `com.salarytracker`，以边界测试稳定为前提，再逐模块迁移到目标命名空间。

```text
backend/
├── pom.xml                         # parent，packaging=pom
├── app/                            # 启动类、配置、组装和 REST 入口
├── modules/
│   ├── platform/                   # 基础设施、异常、事件、审计公共适配器
│   ├── identity/                   # 用户、会话、RBAC
│   ├── worktime/                   # 工时、薪资和节假日
│   ├── ledger/                     # Phase 1 加入
│   └── task/                       # Phase 2 加入
└── integration-tests/              # Testcontainers 和契约测试

frontend/
├── pnpm-workspace.yaml
├── apps/web/                       # 现有 Vue 应用渐进迁入
│   ├── components.json             # shadcn-vue 组件路径与风格配置
│   └── src/styles/tailwind.css     # Tailwind 入口、tokens 和全局基础样式
└── packages/
    ├── api-client
    ├── shared
    ├── sync-engine
    └── ui/                         # shadcn-vue 源码组件和领域组合组件
```

迁移顺序固定为：先在现有目录加入测试和 Flyway → 抽出 `platform` → 抽出 `identity`/`worktime` → 生成 `api-client` → 将现有页面切换到新 API → 最后再切换到 pnpm workspace。每一步都保留可运行的 Compose 配置，避免构建系统迁移和数据迁移互相阻塞。

---

## 附录 A：现有代码迁移对照表

| 现有资产 | 去向 |
|---|---|
| `frontend/src/utils/calc.js` | 口径迁至后端 `worktime` 领域服务；前端保留展示计算 |
| `frontend/src/stores/app.js`（localStorage 快照） | 拆分为 sync-engine + 各域 Pinia store |
| `backend/.../DataController`（GET/PUT /api/data） | Phase 0 下线，替代为 `/api/worktime/**` |
| `schema.sql` 三表 | settings→work_setting；records→work_record；kv→废弃（配置入 work_setting） |
| `AccessCodeInterceptor` | Spring Security + JWT 取代 |
| `holidays/*.json` | 迁入 `holiday` 表（支持在线更新） |
| `deploy/`（Compose/deploy.sh） | 演进为多服务编排 + 健康检查 + NAS 卷 |
