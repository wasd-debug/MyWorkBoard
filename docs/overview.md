# 个人工作台项目总览

> 状态日期：2026-09-22
> 当前主线：Phase 3B 增量实施中，真实 DeepSeek 已能通过最小编排循环调用七个只读 Domain Tool；仍按“现有功能 Agent 化 → MCP → 文件/RAG”顺序推进

## 项目定位

本项目从“加班时长与真实时薪计算”工具演进为个人效率工作台，长期由五类能力组成：

1. **工时**：打卡、工资、加班、真实时薪与节假日。
2. **账本**：账本、账户、流水、分类、预算、成员与权限、报表、导入导出、周期流水和 AI 记账。
3. **任务**：清单、任务、日历、提醒、番茄钟、习惯和倒数日。
4. **AI 与知识库**：附件、NAS、RAG、Agent 工具调用。
5. **洞察**：工时、账本和任务的跨域日报、周报、月报及年报。

目标架构保持为 Java 17 + Spring Boot 模块化单体、MySQL/Flyway、Vue 3/Vite/Pinia、Tailwind CSS + 源码组件、IndexedDB/oplog local-first。完整目标与阶段门禁见 [ARCHITECTURE.md](ARCHITECTURE.md)，Agent、MCP、受控确认和逐阶段验证见 [工作台的 Agent 改造计划](工作台的Agent改造计划.md)。

## 当前实现

### Phase 0：工程与自动化发布门禁已完成

已实现：

- Flyway V1-V3 接管基础表结构与旧数据回填，`schema.sql` 不再参与启动初始化。
- Spring Security + JWT access token + HttpOnly refresh cookie；注册、登录、刷新、退出和修改密码。
- `app_user`、角色/权限、审计日志、统一异常响应和 trace id。
- `/api/v1/worktime` 设置与记录 CRUD；旧 snapshot、`/api/data` 和非 v1 业务入口已删除。
- Maven 父工程及 `platform`/`identity`/`worktime`/`ledger`/`ai`/`app` 物理模块，以及 ArchUnit 依赖和源码归属测试。
- Tailwind CSS、语义 token、响应式应用壳、基础 UI 组件和按用户隔离的本地缓存。

本轮新增收口：

- 工时 store 与页面已切换 settings/records CRUD；服务端持久化计算结果，前端计算只用于未保存预览和页面聚合。
- 工时服务按法定节假日、调休补班和自然周末统一判定日期类型：工作日加班为净工时减标准工时，休息日加班为扣除午休与自定义休息后的全部净工时。Flyway V14 将历史记录统一回算为 `phase0-v2-day-type`，避免旧算法和重新保存后的新算法混用。
- Testcontainers 覆盖 MySQL 资源契约、工资口径、权限、同步与 Flyway 空库/旧 fixture 重放。
- Element Plus 与图标包已经移除，统一使用源码 UI、轻量消息服务和 Lucide。
- 恢复脚本和 Playwright 工时资源/账本断网场景已建立。
- 业务 Controller/Service 已改为 record/enum DTO，统一使用 `ApiResponse<T>` 与 RFC 7807 `ApiProblem`。
- 运行时 OpenAPI 生成 `typescript-axios` 客户端，业务源码只通过集中 transport/facade 调用，生成幂等与严格类型检查已纳入门禁。
- Dialog/Sheet/Drawer/Tabs/Switch 等交互基于 Reka UI，统一 focus-visible、触控热区、主题对比度、图表文本替代和响应式布局。

自动化环境中的 OpenAPI、视觉、无障碍、WebKit、恢复演练和源码 Docker 构建门禁均已通过；仅真机验收必须在 Android Chrome 与 iOS Safari 实际设备上另行留档。

### Phase 1：local-first 主链与自动化发布验收已完成

已实现：

- 多账本、账户、两级分类、商家、项目、成员、角色与细粒度账本权限。
- 流水分页/筛选/排序、转账与债权债务类型、append-only 版本历史、软删、回收站和审计日志。
- 月度总预算与分类预算、账户余额刷新和月末物化。
- 账本首页、流水、管理、报表、定时任务页面及桌面/移动响应式布局。
- 随手记多 Sheet Excel/CSV 分阶段导入、重复流水识别和 Excel 导出。
- IndexedDB + oplog 同步引擎、按用户和账本隔离、分页拉取、冲突/拒绝队列及游标重置。
- 周期流水任务、ShedLock 调度、固定日期及间隔规则。
- OpenAI 兼容 LLM 网关、自然语言记账预览/确认、图片识别和月度 AI 分析。
- 共享账本切换、跨账号缓存隔离和失权回退。

本轮新增收口：

- 流水与五类离线资源写入统一走 command facade + sync-engine；在线命令也由 store 统一拒绝离线伪成功。
- 浏览器 E2E 覆盖断网新增/修改/删除、刷新恢复、联网重放、冲突/拒绝处理和用户/账本隔离。
- MySQL 集成测试覆盖六类离线资源 push/pull、op 幂等、revision 冲突、校验拒绝与权限隔离。
- 真实随手记工作簿用例已执行，覆盖金额汇总和重复导入判定。
- Playwright 已覆盖 WebKit、320/375/768/1024/1440px、深浅与四套主题、键盘和 axe serious/critical 零违规。

不属于本轮已实现范围：

- MoneyWiz 模板没有明确实现证据；支付宝、微信和银行卡账单自动拉取仍为 `STATEMENT_IMPORT` 占位。
- Android Chrome 与 iOS Safari 仍需各执行一次真机记录。
- 部分页面和服务文件体积较大，仍需后续按职责拆分。

### Phase 2-6

- **Phase 2 任务管理：未启动。** 导航只有禁用占位，没有 task/file/notification 模块或表结构。
- **Phase 3A-D Agent/MCP：Phase 3A/3B 部分实现。** 已新增 AI 物理模块、七个内部 R1 查询工具、action JDBC 持久化、首个工时 prepare/commit、V13 会话消息持久化和最小受控 REST 入口；首页真实 DeepSeek 已能选择当前用户可见的只读工具，并支持同一会话连续追问。SSE、turn 恢复、受控确认 UI 和 MCP Server 尚未完成。
- **Phase 4 文件/RAG：未启动。** 尚无 MinIO/NAS 文件域、Tika、Qdrant 和知识库。
- **Phase 5 跨域洞察：未启动。** 只有 `domain_event` 预留表，无事件发布/消费、`report_fact`、`report_snapshot` 或洞察页面。
- **Phase 6 持续打磨：部分能力提前实现。** 已有响应式布局、主题、共享账本和可重复恢复演练；PWA、全局搜索和完整可观测体系尚未实现。

## 当前验证基线

2026-09-22 本轮 Agent 后端验证与既有基线：

- `cd backend && mvn -pl modules/ai -am test`：目标 Reactor 共执行 75 项，74 项通过、1 项账本 Excel fixture 跳过；platform 1/1、AI 27/27 通过，覆盖模型工具协议、只读工具隔离、参数失败回传和调用上限。
- `cd frontend && npm run build`：成功；聚焦 Agent Playwright（desktop Chromium）2/2 通过，覆盖新会话首条响应立即可见、Markdown、假打字机和滚动跟随控制。
- 本地真实 DeepSeek 已完成账本列表、近 30 天工时和写入边界手动验证；只读查询返回当前用户数据，写入请求没有产生记录。
- `AgentConversationIntegrationTest` 使用真实 MySQL 执行 V1-V13 并验证消息顺序与用户隔离；OpenAPI 生成检查和 TypeScript 严格编译通过。

- `cd backend && mvn test`：共发现 70 个用例，63 个通过、7 个跳过、0 个失败；AI 11/11、架构边界 6/6 通过。跳过项为 1 个真实 Excel fixture 用例和 6 个当前 Docker 未运行的 Testcontainers 用例。

以下完整前端、E2E 和恢复演练来自 2026-09-18 基线；本次仅重跑了前端构建和聚焦 Agent E2E：

- `cd frontend && npm run api:check && npm test`：客户端重新生成无差异、TypeScript 严格编译通过、44 个 Node/契约测试全部通过。
- `cd frontend && npm run build`：生产构建成功。
- `SALARY_E2E_SOURCE_BUILD=1 npm run test:e2e`：32 passed、4 个非快照项目按设计 skipped；覆盖 Chromium、WebKit、320/375/768/1024/1440px 和旧路径 404。
- `deploy/verify-backup-restore.sh`：旧 schema 快照演练通过；已有核心表数量一致，缺失的 `ledger_book` 由 Flyway v11 创建并回填 3 条，临时库自动删除。
- `git diff --check`：通过。

自动化测试覆盖契约、账本规则、同步引擎、用户缓存隔离、JWT、视觉和无障碍；真机与持续生产演练仍按 `ARCHITECTURE.md` 第 12.6 节单独留档。

## 下一步顺序

1. 完成 Android Chrome 与 iOS Safari 真机验收记录。
2. 确认 MoneyWiz 与外部账单源范围。
3. 将恢复脚本纳入季度生产运维并持续留存发布/回滚记录。
4. 继续 [工作台的 Agent 改造计划](工作台的Agent改造计划.md) Phase 3B：在现有文本上下文之上建设会话列表/消息查询、turn 状态和 SSE，再接入受控表单。
5. Phase 2 任务域与现有工时/账本 Agent 可分别推进；任务能力完成后再注册为新的 Domain Tool，不阻塞 Phase 3A-D。

## 文档约定

- `ARCHITECTURE.md`：长期目标、阶段计划、架构门禁和当前状态。
- `工作台的Agent改造计划.md`：Agent、MCP、受控写入、工具覆盖和逐阶段验证计划。
- `Phase 1 —— 账本设计具体展开.md`：账本产品约定、已实现能力和剩余验收项。
- `DEPLOY.md`：本地联调、构建、部署与排障。
- `docs/superpowers/plans/`：阶段性实施计划记录。

根 `.gitignore` 继续忽略未纳入交付的 `docs/` 新文件；本轮 `ARCHITECTURE.md`、`overview.md` 和工作台 Agent 计划已显式纳入版本控制。后续新增文档仍需评估是否加入白名单。
