# 个人效率中枢 · 整体架构设计与长期发展规划

> 版本：v1.7（2026-09-22）
> 范围：基于现有 salary-sync（加班时长与时薪计算）系统，规划"工时 + 账本 + 任务 + AI"一体化个人效率平台的整体架构与演进路线。

> 实施状态：Phase 0/Phase 1 自动化收口已完成，真机验收和周期生产运维按发布记录持续执行。本文同时包含目标架构与实施计划；除明确标注“当前实现”的内容外，其余技术组件和阶段能力均为目标状态，不代表已经上线。

## 0. 当前实施快照（2026-09-22）

| 阶段 | 状态 | 结论 |
|---|---|---|
| Phase 0 地基 | 工程与自动化发布门禁完成 | Flyway、JWT、唯一 v1 API、record/enum DTO、OpenAPI 生成客户端、工时资源前端、物理模块、视觉/无障碍和恢复自动化已落地；真机结果单独留档 |
| Phase 1 账本 | local-first 主链与自动化发布门禁完成 | 六类离线资源统一走 sync-engine，断网/重连/冲突/拒绝、真实工作簿、WebKit、多视口和 axe E2E 已通过 |
| Phase 2 任务 | 未启动 | 只有禁用导航占位，无领域模块、数据表和页面 |
| Phase 3A-D Agent/MCP | Phase 3A/3B 部分实现 | AI 物理模块、7 个 R1 查询工具、action JDBC 持久化、首个工时 prepare/commit、V15 会话/消息/turn、基础 SSE 与断流查询已落地；首页可恢复历史并展示真实增量、TTFT、模型/工具耗时和 Token 细分，支持会话右键管理、页面内消息队列和严格滚动跟随，完整 trace、队列持久化、受控写入和 MCP 尚未实现 |
| Phase 4 文件/RAG | 未启动 | 无文件域、MinIO/NAS、Tika、Qdrant 和知识库 |
| Phase 5 洞察 | 未启动 | 只有 `domain_event` 预留表，无事件链路和报表快照 |
| Phase 6 打磨 | 部分提前实现 | 已有响应式布局、主题、共享账本、自动视觉/无障碍和恢复演练；PWA、搜索及完整可观测体系未实现 |

2026-09-22 执行 `mvn -pl modules/ai -am test`，目标 Reactor 共 75 项，74 项通过、1 项账本 Excel fixture 跳过；platform 1/1、AI 27/27 通过，新增 DeepSeek 工具协议和编排上限测试。前端生产构建成功，聚焦 Agent Playwright 2/2 通过，并完成真实 DeepSeek 账本/工时查询与只读写入边界验证。完整 `mvn test` 最近基线仍为 70 项中 63 项通过、7 项跳过、0 项失败；完整前端 Node/契约 44/44、OpenAPI 生成、TypeScript 严格编译、Playwright 32 passed/4 skipped 和恢复演练结果继续作为既有基线。第 12.6 节中的 Android Chrome 与 iOS Safari 真机验收仍需在实际设备上留档。

---

## 目录

1. [现状评估与架构债务](#1-现状评估与架构债务)
2. [架构目标与设计原则](#2-架构目标与设计原则)
3. [整体架构分层设计](#3-整体架构分层设计)
4. [模块划分与依赖关系](#4-模块划分与依赖关系)
5. [技术选型建议](#5-技术选型建议)
6. [数据模型与存储方案](#6-数据模型与存储方案)
7. [离线同步方案](#7-离线同步方案)
8. [Agent、MCP 与 RAG 接入方式](#8-agentmcp-与-rag-接入方式)
9. [跨模块数据联动流程设计](#9-跨模块数据联动流程设计)
10. [分阶段落地规划](#10-分阶段落地规划)
11. [非功能需求与风险](#11-非功能需求与风险)
12. [实施补充：任务拆解、迁移与验收](#12-实施补充任务拆解迁移与验收)

---

## 1. 现状评估与架构债务

### 1.1 现有资产盘点（2026-09-18）

| 层 | 现状 | 评价 |
|---|---|---|
| 前端 | Vue 3 + Vite + Pinia + Vue Router + ECharts + Tailwind/token + Reka UI + Lucide；Element Plus 已移除 | 工时使用生成客户端访问资源 API；账本六类资源使用 IndexedDB/oplog local-first，在线命令由 store facade 统一管理 |
| 后端 | Java 17 + Spring Boot 3.2；platform/identity/worktime/ledger/ai/app 六个 Maven 模块 | 业务源码和单测已物理归属对应模块，app 仅装配应用、迁移资源和跨模块测试；AI 当前只含原有网关和领域工具基础 |
| 数据库 | MySQL 8 + Flyway V1-V15；用户、工时、账本、同步、定时任务、审计、AI 会话和 turn 表 | 多用户和账本数据模型已落地；事件、任务、文件、RAG 和洞察读模型仍未落地 |
| 鉴权 | Spring Security + JWT access token + HttpOnly refresh cookie；用户、角色和权限表 | 已替换静态 AccessCode；仍需限流、安全集成测试和更完整的会话运维能力 |
| 部署 | Docker Compose（Nginx + Spring Boot + MySQL），源码/预构建镜像/本地产物三种模式 | 当前仍是三服务单机部署；Redis、MinIO、Qdrant、监控等按后续阶段引入 |

### 1.2 当前剩余架构债务

1. **真机发布记录仍需人工执行**：自动化已覆盖 WebKit 与移动视口，但 Android Chrome 和 iOS Safari 仍需在实际设备上各留一次结果。
2. **前端聚合口径仍需继续收敛**：已保存工时使用服务端字段，`utils/calc.js` 保留未保存预览和页面周期聚合；后续跨域报表必须只使用服务端事实。
3. **local-first 尚未扩展到后续领域**：账本已验证该模型，任务等未来领域仍需复用并重新验证同步契约。
4. **生产运维需要持续证据**：恢复脚本已通过本地旧 schema 演练，仍需按季度在生产备份副本上执行并保存记录。
5. **大型文件积累维护成本**：账本报表页面和账本服务已明显超出单文件易维护规模，需要在不改变业务契约的前提下按职责拆分。

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
| **Local-first** | 客户端 IndexedDB 为主存储，操作日志（oplog）双向增量同步，断网可用；IndexedDB、当前账本选择和业务本地缓存必须以不可变用户 ID 分区，登出仅清理内存会话而不读取或上传其他用户缓存 |
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

`packages/ui` 和 `src/components/ui` 维护可审查、可定制的源码组件，页面通过组合组件复用能力；图标统一由 Lucide 提供，Element Plus 及其图标依赖已经移除。

> 计算口径迁移说明：现有 `utils/calc.js` 的时薪公式复制到后端 `worktime` 模块作为权威实现，前端仅保留展示层计算，两者通过 OpenAPI 中的常量定义保持一致，报表联动一律采用服务端计算结果。服务端保存时必须按法定日历区分工作日与休息日：工作日加班为净工时减标准工时，休息日加班为扣除午休和自定义休息后的全部净工时；每条记录通过 `calc_version` 标识所用口径。

---

## 4. 模块划分与依赖关系

### 4.1 模块清单

**核心域（业务价值）**

| 模块 | 职责 | 对应需求 |
|---|---|---|
| `worktime` | 打卡记录、工时计算、加班统计、时薪计算（权威口径）、节假日 | 已有功能迁入 |
| `ledger` | 账户、交易流水、分类、预算、账单周期、账本报表 | 账本（随手记形态）；预算支持月度总预算及一级/二级支出分类 |
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
| 安全        | **Spring Security + JWT 双令牌** | 当前 access token 默认 15 分钟、refresh token 默认 30 天并持久化到 MySQL；密码 bcrypt。Redis 吊销/限流仍是后续目标 |
| 权限        | **RBAC 三表模型 + `@PreAuthorize`**                                          | user / role / permission + 关联表；够用且可演进到数据级权限                                        |
| 缓存/限流     | **Redis 7**（Compose 新增）                                                  | 刷新令牌、验证码、接口限流、报表缓存；单机初期可延后到 Phase 2                                                |
| 定时任务      | **Spring Scheduling + ShedLock**（起步）→ **XXL-Job**（任务量 >20 或需可视化时）        | 周期账单、报表聚合、节假日抓取、提醒投递；ShedLock 保证多实例不重复执行                                           |
| Excel/CSV | **EasyExcel**                                                            | 当前已实现随手记/通用 CSV 与 Excel 导入导出；MoneyWiz 专用映射仍是待确认目标                              |
| 文档解析      | **Apache Tika**                                                          | RAG 附件文本抽取（PDF/Office/文本）                                                          |
| AI 框架     | **LangChain4j**（首选）或 Spring AI                                           | Java 原生、Tool/Function Calling 成熟、与 Spring Boot 集成好；LLM 网关抽象支持 DeepSeek/OpenAI 兼容端点 |
| 向量库       | **Qdrant**（Docker 单容器）                                                   | 轻量、过滤检索（按 user_id/模块过滤）强；替代：pgvector（若引入 PG）；不建议 Milvus（过重）                        |
| 对象存储      | **MinIO（S3 网关模式挂载 NAS 目录）**                                              | NAS 上的文件获得 S3 API：签名 URL、分片上传、生命周期；数据库只存 `storage_uri`                             |
| API 文档    | **springdoc-openapi**                                                    | 前端 api-client 自动生成的基础                                                              |

账本导入按上传解析、确认写入、刷新本地账本三个阶段向前端反馈状态，文件上传阶段展示真实字节进度，服务端解析和批量写入阶段展示处理中状态。随手记多 Sheet 导入按 Sheet 名识别流水类型；只有收入、支出校验二级分类，转账及债权债务类流水允许分类为空。旧版默认账本 Excel 兼容接口也应成对写入转账，不得跳过转账 Sheet。导入忽略源文件成员字段，不创建成员，统一绑定当前登录用户在当前账本中的成员身份。
导入查重按完整流水指纹的出现次数处理，不能用单一 `Set` 折叠源文件中合法的同日同金额重复流水；缺失一级分类的收入/支出在指纹和落库时都规范为“其他”。资源存在性在预览开始时批量载入，禁止逐行查询账户、分类、商家和项目。确认写入后先刷新账户派生余额和账本有效流水数量，再在后台增量同步本地流水投影；同步引擎按服务端分页批量写入 IndexedDB，避免大批量导入后逐条开启事务阻塞首页。

账本列表接口同时返回有效流水数量，统计时排除软删流水和转账的内部转入镜像。账本管理页可直接按账本下载全量 Excel，前端使用认证请求获取 Blob，不通过无认证的普通下载链接。
账本成员仅能通过全站唯一用户名关联已注册且状态为 `ACTIVE` 的 `app_user`；邮箱同样有唯一索引，当前不提供手机号身份字段。新增成员必须在线提交，服务端拒绝未注册、停用和已经加入当前账本的用户，并将原因明确返回给表单；离线不能乐观显示添加成功。已软删的成员记录可在重新添加时恢复。
流水主列表采用服务端分页、排序和组合筛选，首屏只获取当前页；账户筛选同时覆盖交易主账户与转账对方账户。首页、报表和管理页的账户、一级/二级分类、商家及项目入口统一跳转到带筛选参数的流水页。

### 5.2 前端

| 关注点 | 选型 | 说明 |
|---|---|---|
| 框架 | **Vue 3 + Vite + Pinia**（延续） | monorepo 化（pnpm workspace） |
| 样式系统 | **Tailwind CSS 4** | utility-first 响应式布局；通过 CSS 变量定义语义色、间距、圆角、阴影和明暗主题 |
| UI 基础组件 | **shadcn-vue**（源码归属）+ 无障碍 primitive | 按需加入 `packages/ui`，组件代码归项目所有；使用 `Button`、`Card`、`Dialog`、`Sheet`、`Drawer`、`Table`、`Tabs`、`Command`、`Badge`、`Alert`、`Skeleton` 等组合页面 |
| 图标 | **Lucide Vue** | 统一线性图标风格；图标作为组件传入，不在页面散落 SVG |
| UI 与图标 | **源码 UI 组件 + Lucide** | 组件可审查、可定制；Element Plus 已退出，新增页面不得重新引入 |
| 日历视图 | **FullCalendar 6** | 日/周/月视图开箱即用；多日/多周切换用其 date navigation API；年视图自研（12 宫格热力图） |
| 图表 | **ECharts**（延续） | 账本报表 + 工时趋势 + 年度报告 |

账本可视化在前端共享稳定色板：分类颜色由后端持久化，一级分类按账本内顺序使用黄金角步进生成不重复色相，二级分类继承父级色相并变化明度/饱和度；其他实体按公共 ID 稳定映射颜色。分布类排行的名次与图标分隔，类别名称和右侧占比、金额位于细条形进度图的上方；排行子组件样式应实际覆盖其渲染节点，不依赖父页面 scoped CSS 标记。首页和账本报表统一支持自由日期范围，图表、排行和流水筛选复用相同起止日期口径，长于 93 天的趋势自动按月聚合。应用骨架提供路由过渡与全局异步 loading 遮罩，账本弹层和可展开排行使用统一动效并兼容减少动态效果的系统设置。
账本首页首屏与报表一致优先读取 Pinia/IndexedDB 投影，账户、分类、全部流水和年度统计不再重复通过多组接口阻塞加载；月份与自定义日期切换直接重算本地投影，仅预算按月份轻量刷新。后台同步完成后通过 store 响应式更新页面。首页 ECharts 按需注册并复用实例，数据或主题变化时只更新 option。
账本主人通过已注册用户名添加成员后，成员使用自己的账号登录即可在“管理 → 账本管理”看到共享账本及自己的角色，并从该列表切换；切换后读取该账本的独立本地投影，联网恢复与页面重新可见时刷新可访问账本列表。`default` 兼容接口只指向当前用户自己拥有的账本，不因先加入他人账本而重定向到共享账本；所有账本读写仍由服务端校验成员关系和角色权限。
预算写入统一使用账本作用域接口 `/api/v1/ledger/books/{bookId}/budgets`：省略 `categoryId` 表示月度总预算，填写当前账本可见的一级或二级支出分类公共 ID 表示分类预算。前后端均校验月份格式、正数金额和分类归属；收入分类、隐藏分类、已删除分类及跨账本分类不得用于预算，旧非账本作用域预算接口仅作兼容，不再由前端调用。
总资产展示口径为资产账户余额加未收回借出本金，借入现金计入总资产，对应负债只在净资产中扣减；账面账户余额不因展示口径回写。首页、管理和报表复用同一计算，历史报表按对应日期的账户余额和截至当日流水计算。转账双边落库后还须刷新前端账户余额投影，确保转出扣减、转入增加。流水录入按最近 200 笔的使用频次及最近日期排列账户、两级分类，成员默认本账本中的当前用户，提交时将选择的商家、成员、项目名称映射到当前账本公共 ID；仅收入和支出要求二级分类。首页日历跳转当天流水筛选。
| 本地存储 | **Dexie (IndexedDB)** | 替换 localStorage 作为主存储；localStorage 仅存会话与偏好 |
| 离线 | **vite-plugin-pwa** | Service Worker 静态资源缓存 + 后台同步 + Web Push |
| 提醒 | Web Push（VAPID）+ 站内通知中心 | 桌面端兜底浏览器 Notification |
| 搜索 | 起步 MySQL 全文索引 → 量大后 **Meilisearch** | 任务/账本/附件名统一搜索 |
| 番茄钟 | 纯前端实现（Web Timer + 本地统计上报） | 不依赖服务端计时 |

账本报表使用 `/ledger/reports` 独立路由，位于账本导航“账户”之后。报表 main 顶部以可自定义横向 Tab 切换基础统计、分类、账户、商家、月报、支出/收入分类、账户详情、成员、项目分类和项目，右侧报表库负责增删 Tab；Tab 支持拖动排序，配置和顺序保存在本机，当前报表和按年/按月日期范围保存在 URL。基础统计采用双列卡片布局，并将收支、结余和记账里程碑合并展示。分类报表由收入、支出两个卡片组成，各自管理分类层级；图表或排行点击通过 URL 将日期范围和分类条件传递给流水明细页。账户报表按所选日期用期初余额和流水回算资产、负债与净资产，当前范围直接复用账户余额投影，历史范围按截止日回算；转账每一组只对转出和转入各计一次，年度趋势按月末余额生成。资产明细与账户页使用同一账户余额口径，未收回借出本金单独计入总资产口径。商家报表分别聚合商家收入和支出，以环形图和排行展示，未关联商家的流水归入“无商家”。图表读取当前账本的 Pinia/IndexedDB 投影，在断网状态下保持可用，ECharts 容器必须监听尺寸与主题变化；当前报表支持 PNG 图片导出和浏览器打印保存 PDF。
报表首次进入优先读取 IndexedDB 投影并立即结束首屏遮罩，服务端账本与预算刷新在后台完成；账本切换只复用 store 的响应式结果，不再通过页面事件重复读取。路由遮罩在导航完成、导航异常和安全超时三条路径统一清理。ECharts 按实际使用的折线、柱状和饼图模块注册，避免加载完整图表包。
成员、项目分类和项目报表继续沿用双列卡片布局：成员页提供记账笔数、收支对比、支出和收入统计；项目分类页提供结余、收支对比、收入和支出统计；项目页提供总毛利、项目收入、项目毛利和项目支出统计。收入管理和成本管理分别提供分类分布、商家明细、单量及应收/应付款卡片。成员、项目、商家及分类的图表和排行点击后复用流水明细筛选；项目毛利为负时排行显示负号，图表以绝对值避免饼图负值。

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

迁移结果：现有 `records` 表数据已由版本化迁移写入 `work_record`（补 user_id=管理员）；`GET/PUT /api/data` 的过渡期已结束，控制器和运行时路径均已删除。

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
budget          (id, user_id, category_id NULL, period, amount, start_date, end_date)
                -- category_id=NULL 为月度总预算；填写一级或二级支出分类即为分类预算
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

## 8. Agent、MCP 与 RAG 接入方式

详细实现、接口、工具覆盖矩阵、风险分级和逐阶段验证门禁见 [`工作台的Agent改造计划.md`](工作台的Agent改造计划.md)。长期路线调整为先交付 Agent 驱动现有功能和 MCP，再建设文件与 RAG，避免尚未存在的文件域和向量库阻塞账本、工时能力接入。

### 8.1 Agent 与 MCP 共享领域工具

```text
传统 Web 页面     Web AI 工作台     WorkBuddy / Codex / 其他 MCP Host
       │                 │                         │
       └─────────────────┼─────────────────────────┘
                         ▼
               统一 Domain Tool 层
                         ▼
             ledger / worktime 应用服务
                         ▼
          权限、事务、校验、审计、幂等、同步
```

- 新增独立 `ai` Maven 模块，承载会话、Agent 编排、Domain Tool、待确认 action、MCP 适配和 trace。
- Web Agent 与 MCP 共用工具注册表和执行策略，但分别使用进程内和 MCP 协议适配器；内部 Agent 不反向 HTTP 调用本站 MCP。
- Agent 工具调用各域公开应用接口，不直接读写领域表；用户、账本和 scope 由服务端认证上下文注入。
- 所有 R2-R4 写操作使用 `prepare → 补充/确认 → commit`，不能依赖模型自行声明“用户已确认”。
- 工作台通过 SSE 返回文本、工具状态、受控表单和确认事件；模型不得生成可执行 HTML。

### 8.2 MCP 对外接入

- 同一 Spring Boot 应用提供 Streamable HTTP MCP Server，PAT 用于首轮兼容验证，OAuth 2.1 + PKCE 用于正式远程接入。
- MCP 默认只读，账本与工时分别设置 read、prepare 和 commit scope。
- 高风险写操作返回站内审批链接，用户登录网站确认后才允许 commit。
- 至少使用 MCP Inspector、Codex 和 WorkBuddy 完成真实连接验证，保存客户端版本、传输方式、认证方式和已知限制。
- MCP 调用按用户、客户端、工具和账本审计并限流，不能暴露同步、余额物化等基础设施接口。

### 8.3 文件 → RAG 管道（Agent/MCP 稳定后）

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

### 8.4 关键约束

1. **权限同源**：Agent 和 MCP 工具复用领域权限、事务和审计，不复制业务规则。
2. **写入可控**：所有写工具有风险级别、稳定 Schema、幂等键和过期 action；R4 必须站内审批。
3. **成本控制**：会话上下文按条数和 token 双截断；RAG 检索 top-k 默认 5；调用计入 `ai_usage`。
4. **可观测**：每次执行记录工具链、模型、token、耗时和脱敏结果，业务写入仍进入领域审计。
5. **供应商可替换**：`AgentModel` 隔离 DeepSeek/OpenAI 等供应商；工具契约不依赖模型 SDK。
6. **RAG 输入不可信**：文档内容不能改变系统提示、工具权限、用户身份或审批策略。

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

> **阶段依赖修正**：Phase 0/1 先交付事件信封、幂等和消费位点等基础设施，但不提前交付完整 insight。Phase 1 的 AI 仅包含账本自然语言记账；`insight_report` 工具和跨域报表必须等 Phase 5 的事实表与快照稳定后启用。这样可以避免 Agent 依赖尚未存在的读模型。

---

## 10. 分阶段落地规划

> 节奏设计原则：每阶段 2～6 周可交付、可上线、可回滚；先地基后上层；AI 相关放后（依赖数据积累）。

所有改动发生后，使用 Docker 打包并发送到 `212.64.29.21`，SSH 用户为 `ubuntu`；SSH 只能使用仓库根目录的 `workboard.pem`：`ssh -i ./workboard.pem ubuntu@212.64.29.21`。禁止密码认证，不得输出、复制或提交私钥内容。
依旧使用 Docker 打包后发送到主机，停止原来服务后使用新的镜像进行启动部署。

### Phase 0 —— 地基重构（一切的前置）

**目标**：把单用户快照系统改造成多用户资源化系统，旧功能行为不变。

状态符号：`[x]` 已实现，`[~]` 部分实现，`[ ]` 未实现或未完成验收。

- [x] 后端 platform/identity/worktime/ledger/ai/app Maven Module 已物理拆分，Spring Modulith 与 ArchUnit 校验依赖和源码归属
- [x] Flyway 接管表结构；`work_record` 迁移（含 user_id/revision），旧数据通过 V2 回填
- [x] identity：注册/登录/JWT 双令牌/Spring Security；静态 AccessCode 已下线
- [~] RBAC 表、审计 AOP/表/查询 API 已实现；全局 `@PreAuthorize` 策略与安全集成测试仍需补齐
- [x] `/api/v1/worktime` 资源 CRUD 已实现并由工时前端使用；snapshot 与旧 `/api/data` 已删除
- [x] 已保存记录的加班/时薪由后端持久化并有黄金样例，前端 `calc.js` 只用于表单预览和页面聚合
- [x] Flyway V14 已将旧算法记录全量回算为 `phase0-v2-day-type`；后端保存与前端预览统一使用法定节假日、调休补班和自然周末判定
- [x] 统一 `ApiResponse<T>`、RFC 7807 `ApiProblem`、springdoc 与固定 operationId 已实现；`api-client` 由 OpenAPI Generator 生成
- [x] Tailwind、语义 token、Reka UI 基础组件、响应式应用壳、focus-visible 和主题对比度门禁已建立
- [x] 页面具备响应式布局，Element Plus 与图标包已清零，消息和图标使用本地服务/组件及 Lucide
- **验收**：老数据无损迁移；登录后所有旧功能可用；ArchUnit 边界测试进 CI；操作日志可查；核心页面在手机、平板、电脑视口无横向溢出且交互可用。

### Phase 1 —— 账本（随手记形态）

- [x] 账户/分类/交易 CRUD（append-only + 软删）、版本历史、回收站及月末余额物化任务
- [x] 账本首页、流水、报表、定时任务和管理响应式页面；桌面/移动布局已实现
- [x] ECharts 账本报表、时间范围、报表库、图片导出和浏览器打印 PDF
- [~] CSV/Excel 和随手记多 Sheet 导入导出已实现；MoneyWiz 映射无明确实现证据
- [x] **离线同步引擎 v1** 覆盖 IndexedDB、oplog、游标、冲突/拒绝和用户/账本隔离；六类离线资源页面写路径已统一接入并通过 Chromium/WebKit E2E
- [x] 周期流水 + ShedLock 到期任务，支持固定日期和间隔规则
- [x] OpenAI 兼容 LLM 网关、自然语言记账预览/确认、图片识别和月度分析（无 RAG）
- **当前验收状态**：断网写入、刷新恢复、联网重放、冲突/拒绝、隔离、真实导入金额、Chromium/WebKit、多视口、视觉、键盘和无障碍自动化均已通过；真机结果单独留档。

首页：可（增删）组件
- 本月收支、结余数据总览（支出 收入 结余）（主组件）
- 本月每日支出折线图
- 本天周年收支结余统计（简单图表+文字描述（今天 本周 本年 小字标注日期范围）数字）
- 本月（本年 可切换）支出按一级（二级 可切换）分类排序：横向柱状图
- 本年每月收支结余：横向柱状图
- 本年每月收支结余：折线图
- 本月总支出及执行情况：环形图
- 月度记账日历：每日显示收入和支出

### Phase 2 —— 任务管理（滴答清单形态）

- [ ] 清单（含分组）/任务/子任务/标签/优先级/自定义筛选器；使用 shadcn-vue `Command`、`Tabs`、`DropdownMenu` 组合筛选交互
- [ ] 任务日志 task_activity（时间线）；附件关联延后到 Phase 4 file 模块落地后接入，不阻塞任务核心流程
- [ ] 日历视图：FullCalendar 日/周/月 + 自研年视图；多日/多周切换；移动端默认日/周视图，桌面端支持月视图；与打卡、记账叠加显示
- [ ] 提醒：reminder 表 + notification 模块（站内 + Web Push）
- [ ] 番茄钟（前端计时 + 统计上报）；习惯打卡；倒数日/纪念日
- [ ] 全局搜索 v1（MySQL 全文索引，任务+账本；附件名在 Phase 4 接入）；离线同步覆盖 task 域；桌面快捷键与移动端显式入口一致
- **验收**：任务全流程离线可用；提醒准点送达（±1min）；日历四视图切换流畅；手机端无 hover-only 操作。

### Phase 3A —— 统一领域工具层

Phase 3A-D 只依赖已完成的工时和账本能力，可在 Phase 1 稳定后启动，不等待 Phase 2 任务域；任务模块完成后再注册 `task.*` 工具。

- [~] 已新增 `ai` Maven 模块、Domain Tool 注册表、风险分级、统一结果、action JDBC repository 和 V12 Flyway 表
- [~] 已实现 2 个工时与 5 个账本 R1 查询工具，以及 `worktime.record.create.prepare/commit` 内部工具；工时修改/删除和账本写工具待实现
- [~] 已覆盖当前用户、authority、工具目录过滤、未知字段、重名注册、过期、用户隔离、重复 commit、服务端预览和模块边界；Testcontainers 因 Docker Desktop 启动阻塞待重跑，模型 Agent 与 MCP 未对外启用
- **验收**：每个工具具备成功、缺参、无权限、冲突和重复提交测试；prepare 不产生业务写入。

### Phase 3B —— Web 工作台 Agent

- [~] 首页已接入真实 DeepSeek 和最小只读 Agent 编排；模型只看到当前用户可用的 R0/R1 工具，单轮最多执行 4 次，R2-R4 不暴露
- [~] 服务端会话 CRUD、消息元数据、同一 sessionId 连续追问、用户隔离、turn 状态/幂等、基础 SSE 和断流恢复已实现；前端支持右键/更多菜单、改名/归档/删除确认、页面内 FIFO 消息队列和严格底部跟随；服务端队列恢复、结构化表单、确认弹窗和写入结果链路待完成
- [ ] 先开放只读查询，再开放记账/记工时，最后接入修改、删除和管理工具
- [ ] Agent 在线写入后触发账本增量同步，不改变传统页面 local-first 主链
- [ ] agent trace、ai usage、模型/提示词/工具版本评测和功能开关
- **验收**：完整输入、缺参、歧义、拒绝、重复确认和 revision 冲突 E2E 全部通过；错误写入为零。

### Phase 3C —— MCP 对外接入

- [ ] Streamable HTTP MCP Server，Domain Tool 到 MCP Tool 的单一适配层
- [ ] PAT、read/prepare/commit scope、撤销、账本限制、审计和限流
- [ ] OAuth 2.1 + PKCE、站内审批中心和高风险 confirmation URL
- [ ] MCP Inspector、Codex 和 WorkBuddy 真实兼容验证
- **验收**：默认只读；未审批、过期、重放、伪造用户和越权账本均不能写入；真实客户端完成查询和低风险写入。

### Phase 3D —— 现有功能全量覆盖与稳定化

- [ ] 覆盖所有用户级工时和账本功能，排除同步、物化等内部维护接口
- [ ] 模型回归评测、失败回放、成本告警、客户端熔断和数据清理任务
- [ ] 灰度开放 MCP 写入并固化运行手册、指标、告警和回滚步骤
- **验收**：功能覆盖矩阵无缺项，连续灰度周期无 P0/P1 数据事故，关键链路均可审计和关闭。

### Phase 4 —— 文件、向量库与 RAG

- [ ] file 模块、MinIO/NAS、签名 URL 和备份自动化
- [ ] Tika 解析、分块、embedding 和 Qdrant，按用户与业务域强隔离
- [ ] 附件知识库页面、解析重试、引用定位和向量联动清除
- [ ] `knowledge.*` MCP 工具和 Agent RAG 检索，文档内容按不可信输入处理
- **验收**：上传 PDF 后可就其内容问答并定位引用；越权检索为零；失败可重试。

### Phase 5 —— 数据联动与全景报表

- [ ] domain_event 事件表 + insight 监听器上线，report_fact 日聚合
- [ ] 日报/周报/月报/年报快照生成（定时任务）+ 报表中心页面
- [ ] AI 报告：日报推送（早 8 点）、月报/年报 AI 总结与导出（PDF/Markdown）
- [ ] 旧 `/api/data` 快照接口下线；前端 localStorage 旧数据迁移完成
- **验收**：三域数据在任意报表中口径一致；断开任一模块，其余模块不受影响（联动仅靠事件，天然解耦）。

### Phase 6 —— 打磨与规模化（持续）

- 全局搜索升级 Meilisearch；多端适配（PWA 安装体验、平板布局）
- 视觉系统收敛：移除所有 Element Plus 依赖；统一 shadcn-vue 组件版本、设计 token、响应式回归和无障碍基线
- 数据级权限（家庭/团队共享账本与清单：share 表 + 邀请机制）
- 可观测体系：Actuator + Prometheus + Grafana；慢查询治理
- 备份演练自动化（季度性恢复演练）；XXL-Job（如任务规模需要）

### 里程碑总览

```text
Phase 0 地基 → Phase 1 账本 ─┬→ Phase 2 任务核心 ───────────────┐
  多用户/RBAC    离线同步v1  │    清单/日历/提醒/习惯           │
  审计/API       周期/AI记账 │                                  ▼
                             └→ Phase 3A-D Agent/MCP → Phase 4 文件/RAG → Phase 5 联动报表 → Phase 6 打磨
                                工具/工作台/外部接入      向量库/知识库      事件/周期报告       规模化
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

已对照当前仓库核验：Phase 0 的数据地基、身份体系、工时资源 API 和基础前端设计系统已经部分或全部实现，Phase 1 账本主体也已进入可用状态。当前阻塞阶段退出的重点已经从“是否有功能”转为“迁移是否完成、离线写链是否闭环、模块边界和质量门禁是否真实成立”。

当前实现事实：

- 后端为 Maven 父工程 + `platform`/`identity`/`worktime`/`ledger`/`app`，业务源码已物理迁入所属模块，app 只做组装。
- Flyway V1-V15 管理用户、工时、账本、同步、定时任务、审计、Agent 会话和 turn 结构，并通过 V14 统一回算历史工时口径、V15 增加恢复与幂等数据结构；`spring.sql.init.mode=never`。
- Spring Security + JWT 已替换 AccessCode；账本共享权限由服务端逐请求校验。
- 工时前端使用 settings/records 资源 API；账本六类离线资源写入使用 IndexedDB/oplog，跨资源命令由在线 store facade 管理。
- 任务、文件/RAG 和洞察模块尚未进入实现阶段。

| 优先级 | 必须解决的问题 | 未解决的后果 | 截止门禁 |
|---|---|---|---|
| P0 | 备份/恢复、Flyway、`user_id`、资源化写入、服务端计算 | 数据丢失、跨用户越权、快照并发覆盖 | Phase 0 退出前全部通过 |
| P0 | 认证会话、租户隔离、审计和敏感数据脱敏 | 无法安全上线多用户和 AI | 首个新 API 上线前 |
| P1 | 事件信封、幂等键、同步契约、冲突处理 | 跨域联动和离线能力不可验证 | Phase 1 账本上线前 |
| P1 | 领域模块边界、契约测试、迁移演练 | 后续扩展持续积累架构债务 | 合并到主分支前 |
| P2 | Qdrant、MinIO、Meilisearch、推送和可观测平台 | 影响体验，不阻塞核心账本/工时 | 按对应 Phase 引入 |

### 12.1.1 当前验证记录

2026-09-18 收口结果：Maven 默认套件 57 项通过、1 项真实 Excel fixture 按设计跳过，指定真实工作簿后 ledger 37/37；前端 Node/契约 44/44；OpenAPI 49 paths、70 operations、120 schemas，生成幂等和 TypeScript 严格编译通过；Vite 构建通过；Docker 源码构建后的 Playwright 32 passed、4 项按项目设计 skipped。Testcontainers 实际执行 MySQL 权限/同步、Flyway 空库与旧 fixture 重放、工资口径；旧 schema 恢复演练完成核心数据对账、Flyway v11 migrate/validate 和健康检查。Phase 0/1 自动化退出门禁已通过，真机和季度生产恢复演练按发布流程持续记录。

### 12.2 Phase 0 原始任务包与验收参考

以下是 Phase 0 启动时的原始拆分，当前不再表示“尚未开始”。实际完成度以第 0 节、第 10 节状态标记和 12.1 节当前基线为准；尚未满足的完成定义继续作为收口验收参考。

| 任务包 | 主要工作 | 依赖 | 产出与完成定义 |
|---|---|---|---|
| 0A 基线冻结（2～3 天） | 生产数据导出、记录/设置数量和校验和；建立 API 快照和计算口径黄金样例；加 CI 基线 | 无 | 可重复导出；现有 `mvn test`、前端构建和部署脚本结果留档 |
| 0B 数据地基（1～1.5 周） | 引入 Flyway；创建 `app_user`、权限、审计和 `work_*` 新表；补唯一键、索引、时区字段；保留旧表 | 0A | V1～V3 迁移可重复执行；空库和带旧数据的库均能启动 |
| 0C 身份与安全（1 周） | Spring Security；注册/登录/刷新/退出；短时 access token；刷新令牌吊销；租户上下文；统一 401/403 | 0B | 未认证不能访问业务 API；用户 A 无法读取用户 B；安全测试通过 |
| 0D 工时资源化（1～1.5 周） | records/settings 迁移到 worktime；记录/设置 CRUD、分页和 `If-Match`；服务端权威计算；节假日版本化 | 0B、0C | 新旧接口结果对账；并发更新返回 409；计算黄金样例 100% 一致 |
| 0E 兼容适配（已退出） | 迁移期间曾以旧 `/api/data` 兼容适配器完成行为对账；当前已删除适配器、灰度分支和旧前端调用 | 0D | 对账完成后只保留唯一 v1 资源路径 |
| 0F 前端迁移（1.5～2 周） | `api-client` 按 OpenAPI 生成；Pinia 按域拆分；初始化 Tailwind CSS + shadcn-vue；建立 `components.json`、CSS tokens、`packages/ui` 和响应式应用壳；按页面顺序迁移；localStorage 一次性迁移；错误/登录过期状态统一处理 | 0D、0E | 新旧页面关键流程回归通过；目标页面不再引用 Element Plus；320/375/768/1024/1440px 视口无横向溢出；刷新/登出不会泄露凭据 |
| 0G 地基验收（2～3 天） | Testcontainers 集成测试、契约测试、迁移重放、备份恢复演练、安全检查、视觉回归和键盘/触控可用性检查 | 0A～0F | 满足 12.5 的退出清单；形成发布说明和回滚步骤；核心页面通过桌面、Android Chrome、iOS Safari/PWA 验收 |

### 12.3 API v1 最小契约

所有业务接口统一挂在 `/api/v1`，不保留旧接口兼容层。成功响应使用 `ApiResponse<T>`，错误使用 RFC 7807 Problem Details，并携带 `code`、`traceId`、`path` 和 `timestamp`。

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
| Phase 2 任务 | sync-engine ledger 稳定 | 任务/日历/提醒/番茄钟/习惯、移动端底部导航和日历布局；附件关联延后至 Phase 4 | 离线任务重放通过；提醒误差 ≤1 分钟；无 hover-only 核心操作 |
| Phase 3A 工具层 | Phase 1 稳定；领域公开接口和权限契约冻结 | Domain Tool、风险分级、prepare/commit、action 状态机 | prepare 无业务写入；幂等、冲突和越权测试全绿 |
| Phase 3B Web Agent | 3A 通过；模型预算与隐私策略确定 | 会话、SSE、受控表单、确认、工时/账本 Agent | 断流可恢复；错误写入为 0；同步投影一致 |
| Phase 3C MCP | 3B 写入链稳定；外部授权策略冻结 | Streamable HTTP、PAT/OAuth、scope、站内审批、真实客户端验证 | Inspector、Codex、WorkBuddy 通过；撤销即时生效；越权为 0 |
| Phase 3D 稳定化 | 核心工具和客户端兼容通过 | 用户级现有功能全覆盖、评测、告警、熔断和运行手册 | 覆盖矩阵无缺项；连续灰度无 P0/P1 数据事故 |
| Phase 4 文件/RAG | Agent/MCP 稳定；file/NAS 和隐私策略通过 | 解析、向量索引、引用问答、知识库工具 | 每个答案可定位来源；越权检索为 0；失败可重试 |
| Phase 5 联动 | 事件重试和事实表稳定；口径冻结 | 日/周/月/年报、AI 总结和推送 | 事件重放后报表可重建；三域数字对账一致 |
| Phase 6 持续 | 监控、备份和告警已启用 | 搜索、多端、共享、规模化运维 | 按 SLO 和季度恢复演练持续评估 |

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

## 附录 A：迁移对照与当前状态

| 现有资产 | 去向 |
|---|---|
| `frontend/src/utils/calc.js` | **主链已完成**：已保存记录使用后端计算字段；该文件仅服务未保存预览和页面聚合 |
| `frontend/src/stores/app.js`（旧 localStorage 快照） | **已完成迁移**：工时拆到资源 store，账本拆到独立 store/sync-engine；app 只负责会话、主题和节假日 |
| `backend/.../DataController`（GET/PUT /api/data） | **已删除**：前端只调用 `/api/v1/worktime/settings|records`，不再保留 `/api/data` 或 snapshot |
| `schema.sql` 三表 | **已完成启动迁移**：Flyway V1-V3 建表/回填，`spring.sql.init.mode=never`；旧表仍按兼容策略保留 |
| 静态 AccessCode 鉴权 | **已完成**：由 Spring Security + JWT 双令牌取代 |
| `holidays/*.json` | **部分完成**：holiday 表已存在并优先查询，资源 JSON 仍作为回退/导入来源 |
| `deploy/`（Compose/deploy.sh） | **当前为三服务 Compose**：健康检查和本地/生产构建流程已存在；NAS、向量库和监控服务待后续阶段 |
## 定时任务与周期流水（Phase 1 扩展）

账本下的“定时任务”统一承载周期流水和后续账单拉取任务。周期流水写入 `ledger_scheduled_task`，执行明细写入 `ledger_scheduled_task_run`，以 `(task_id, due_on)` 唯一约束保证手动执行、定时执行和重复请求不会重复入账。

周期流水的 `payload_json` 在列表读取后会物化为 Map；手动“立即执行”和 ShedLock 到期执行必须复用该 Map，不能再次按 JSON 字符串解析而丢失 `kind`、账户和金额。自然语言记账草稿覆盖完整流水字段，并在未指定成员时自动填入当前账本中的登录用户成员；商家、成员、项目等模型识别值若未匹配，保留原始名称供用户审核，不静默删除。所有流水录入入口与筛选弹框复用两级分类匹配器，支持按一级名、二级名或“一级 / 二级”路径动态过滤；选中二级后回填一级，收入/支出仍只提交有效二级分类 ID。

调度规则分为互斥的两种模式：固定日期模式支持每周指定星期、每月指定日期、每月第 N 周星期几、每年指定月日；间隔模式支持仅一次，或从生效日期开始每 N 天、周、月、年执行。固定日期规则存入 `calendar_rule_json`，原有任务通过 `schedule_mode=INTERVAL` 保持兼容。每月指定 29—31 日遇到短月按月末执行；每年第 2 月 29 日遇到非闰年按 2 月末执行；“第 5 周星期几”在当月不存在时跳过该月。

任务 payload 复用流水写入契约，仍经过账户、二级分类、权限及金额校验，并沿用 append-only 版本、审计日志和同步 oplog。创建或修改调度规则时根据生效日期物化 `next_run_on`，任务完成后按同一规则推进，达到截止日期或执行次数后自动暂停。

`STATEMENT_IMPORT` 为支付宝、微信支付和银行卡账单拉取预留类型，当前明确标记为“待接入”，不会伪造成功或自动生成流水。后台每日 00:05（Asia/Shanghai）由 ShedLock 扫描到期任务；用户也可在页面手动立即执行。
