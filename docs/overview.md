# 任务概述：整体架构设计与长期发展规划

## 完成内容

基于对现有 salary-sync 代码库（Vue 3 + Spring Boot 3.2 + MySQL，单用户快照式加班时薪系统）的评估，输出了完整的架构设计方案 `ARCHITECTURE.md`，覆盖用户要求的全部六项内容：

1. **整体架构分层设计**：四层（接入层 / 模块化单体应用层 / 领域事件层 / 基础设施层），前端 pnpm monorepo 分层，并明确 Tailwind CSS + shadcn-vue 的源码组件层。
2. **模块划分与依赖关系**：核心域 worktime / ledger / task + 支撑域 identity / audit / file / ai / schedule / notification / insight，依赖规则由 Spring Modulith + ArchUnit 强制校验。
3. **技术选型建议**：延续 Java 17 + Spring Boot + MyBatis-Plus、Vue 3 + Vite + Pinia，新增 Spring Modulith、Flyway、Spring Security + JWT、Redis、ShedLock、EasyExcel、Tika、LangChain4j、Qdrant、MinIO（NAS S3 网关）、Tailwind CSS 4、shadcn-vue、Lucide、FullCalendar、Dexie/vite-plugin-pwa。
4. **数据模型与存储方案**：约 30 张核心表设计，全局审计字段 + user_id + revision；账本 append-only；附件 blob 落 NAS、DB 只存 storage_uri；离线同步采用 oplog 双向增量模型。
5. **RAG/Agent 接入方式**：附件 → MinIO(NAS) → 签名 URL 动态拉取 → Tika 解析 → 分块 → embedding → Qdrant；Agent 工具化调用各域公共 API，权限同源，agent_trace 可观测。
6. **跨模块数据联动流程**：领域事件 → report_fact 日事实表 → 定时聚合 → report_snapshot 日/周/月/年报 → 报表中心 / AI 总结 / 日报推送。

## 关键决策

- **模块化单体而非微服务**：匹配个人/小团队规模，进程内事件总线完成联动，保留未来拆分能力。
- **Phase 0 必须先重构地基**：现有 `PUT /api/data` 全量覆盖写入和无用户体系是最大架构债务，必须先资源化 API + 引入用户/RBAC/审计。
- **时薪计算口径迁到服务端**：保证三模块报表数字一致（Single Source of Truth）。
- **离线同步作为独立前端资产**（sync-engine 包）在 Phase 1 落地并先行验证于账本域。
- **一套业务代码、两类响应式布局**：Tailwind CSS + shadcn-vue 作为目标 UI 基座；桌面端使用侧边栏/多列，移动端使用底部导航/单列和 Sheet/Drawer，不维护三套端专属业务页面。
- **Element Plus 仅作为迁移期依赖**：新页面不得继续引用，按应用壳、设置、打卡、记录、统计顺序逐页替换，最终移除。

## 交付物

- `ARCHITECTURE.md`：完整架构设计文档（含分阶段规划 Phase 0–5、非功能需求、风险对策、现有代码迁移对照表）
- `overview.md`：方案评审结论、前端 UI 选型决策、Phase 0 首个迭代交付物
- 会话内三张架构图：整体分层架构 / 模块依赖关系 / 跨模块数据联动流程

## 后续建议

- 下一步从 Phase 0（地基重构）启动：Flyway + 用户体系 + API 资源化 + 多模块拆分
- 同步启动 Tailwind/shadcn-vue 设计基座和响应式应用壳，但先不改业务 API；按页面逐步迁移，保持每次提交可回滚

## 评审结论与必要补充

本次阅读将 `ARCHITECTURE.md` 和本文件视为“方案资料”，不是直接约束实现的指令；当前用户请求是对方案做评审、细化和补充。对照仓库现状后，方案主线成立，但如果直接按原顺序编码，存在以下高风险点：

1. **迁移缺少可逆路径**：现有 `schema.sql` 启动初始化、`/api/data` 全量覆盖和 `localStorage` 快照不能直接切换到新模型，必须先备份、回填、对账、灰度，再下线旧接口。
2. **事件与 Agent 的阶段依赖不一致**：Phase 3 的 `insight_report` 依赖 Phase 4 的报表读模型，已调整为先落事件基础设施；跨域 Agent 工具在报表快照稳定后再启用。
3. **离线同步契约不够具体**：补充了 `op_id` 幂等、设备/游标、墓碑保留、409 冲突、游标过期全量重同步和事件去重要求。
4. **历史计算可能漂移**：工时记录需要保存计算版本、时区、工资快照和计算时间，避免设置变更后历史时薪被重算成另一种口径。
5. **安全与运维门槛不足**：补充了 HttpOnly 刷新令牌、CSRF/限流、附件校验、AI 脱敏与工具二次确认、RPO/RTO、备份恢复和测试门禁。
6. **目标 UI 与当前实现不一致**：当前仓库仍使用 Element Plus，已将目标明确为 Tailwind CSS 4 + shadcn-vue，并定义迁移期兼容规则、组件分层、响应式断点和多设备验收标准。

以上内容已写入 `ARCHITECTURE.md` 第 12 节“实施补充：任务拆解、迁移与验收”，包括：

- Phase 0 的 0A～0G 任务包、依赖、产出和退出条件；
- `/api/v1` 最小 API 契约（分页、幂等、乐观锁、同步接口和错误体）；
- 数据表、事件信封、消费位点、转账配对和客户端 ID 的补充约束；
- 迁移灰度、观察窗口、回滚与旧表保留的 runbook；
- 后端、前端、E2E、契约测试及备份/事件重放演练门槛；
- 安全、隐私、RAG/Agent 上线条件和编码前必须确认的产品决策。
- Tailwind/shadcn-vue 组件分层、桌面/平板/手机布局策略、Element Plus 退出计划和视觉/无障碍验收门槛。

## 建议执行顺序

1. 先确认第 12.8 节的账号、时区、货币、兼容期、LLM 和保留策略。
2. 按 0A 基线冻结建立备份、计算黄金样例和 CI，不先改业务行为。
3. 完成 0B～0D 后再开放任何新业务域；先让工时新旧接口可对账、可回滚。
4. 完成 0E～0G 并通过质量门槛，再进入账本和离线同步；RAG/Agent 不提前耦合跨域报表。
5. UI 迁移始终与业务迁移解耦：先建立 tokens 和基础组件，再迁移页面；任何未迁移页面允许暂时使用 Element Plus，但新功能一律使用 `packages/ui`。

## 首个迭代的可交付物

- `docs/adr/0001-phase0-decisions.md`：记录第 12.8 节已确认决策；
- `frontend/apps/web/components.json`、Tailwind CSS 入口、语义化 tokens 和 `packages/ui` 基础组件；
- Flyway V1～V3 迁移脚本和旧数据回填/对账脚本；
- `/api/v1/auth`、`/api/v1/worktime` OpenAPI 草案及生成的前端客户端；
- 工时计算黄金样例、权限隔离、并发版本和幂等集成测试；
- 一份可在临时库复现的备份恢复与回滚演练记录，以及 320/375/768/1024/1440px 视口的视觉回归基线。

## 前端 UI 执行原则

- **基础层**：Tailwind CSS 4 负责布局和主题 token；shadcn-vue 组件以源码形式纳入仓库，项目拥有组件实现和视觉变更权。
- **组合层**：在 `packages/ui` 中沉淀 `ResponsiveTable`、`MobileActionSheet`、`ResponsiveForm`、`AdaptiveCalendar` 等领域无关或跨域组合组件；页面只编排组合组件，不直接堆叠第三方 DOM。
- **响应式层**：`<768px` 为手机、`768–1023px` 为平板/小屏、`≥1024px` 为电脑；使用 CSS 媒体查询/容器查询，不用 User-Agent 分支。
- **交互层**：手机端优先底部导航、单列卡片和全屏编辑；电脑端保留侧边栏、多列卡片、表格和批量操作；弹窗/抽屉/确认/空状态/加载态统一使用 shadcn-vue 组合。
- **迁移层**：Element Plus 只允许出现在尚未迁移的旧页面；新组件、新路由和新业务禁止引入 Element Plus，迁移完成后从依赖和构建产物中删除。
