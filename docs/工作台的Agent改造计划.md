# 工作台 Agent 与 MCP 改造计划

> 版本：v1.6（2026-09-22）
> 状态：实施中（阶段 2 已完成真实 DeepSeek、R0/R1 Domain Tool、基础会话上下文和回复可观测元数据；SSE、turn 状态、持久化 trace 和受控写入仍按后续增量建设）

> 运维修正：迁移 `V8.1` 是在 `V8` 已发布后补充的索引迁移，已有数据库升级时需开启 `FLYWAY_OUT_OF_ORDER=true`；不得删除或改写 `flyway_schema_history`。

> 本地数据库修复记录（2026-09-22）：旧库中的 `V9` checksum 与当前源码不一致，已确认源码未被改写后执行 Flyway repair；`V8.1` 的 MySQL DDL 已实际完成但留下 `success=0`，核对索引存在后仅修复历史状态。未删除业务表或数据，后端已成功迁移至 V13。
> 适用范围：现有工时、账本和 AI 工作台；任务、文件、向量库与 RAG 按后续阶段接入
> 总原则：先把现有业务能力收敛为可验证的领域工具，再接入 Web Agent 和 MCP；每一阶段独立交付、独立验证、可通过功能开关回滚，未通过退出门禁不得进入下一阶段。

## 0. 实施进度快照（2026-09-22）

当前增量交付 Phase 3B 的只读 Agent 回复可观测体验，不开放写工具或 MCP 入口。

已完成：

- 创建 `backend/modules/ai` Maven 模块，并由 `app` 装配；原有 `AiController` 迁入 AI 模块，对外行为不变。
- 建立 `DomainTool`、`DomainToolRegistry`、`ToolDefinition`、`ToolResult`、`ToolStatus`、`ToolRisk` 以及 JSON Schema/输入处理基础。
- 注册首批 R1 只读工具：`worktime.settings.get`、`worktime.records.search`、`ledger.books.list`、`ledger.overview`。
- 增加 `ledger.transactions.search`、`ledger.reports.summary`、`ledger.budgets.list`，仍通过领域服务执行账本成员权限与查询规则。
- 增加 [Agent 功能覆盖与中文评测集](Agent功能覆盖与中文评测集.md)，固定首批工具覆盖状态、中文表达和安全断言。
- 建立 `ActionStatus`、`PendingAction`、`InteractionPolicy` 和 JDBC `PendingActionRepository`，通过 `agent_pending_action` 持久化 action，覆盖过期、用户隔离、强制确认和数据库条件更新的单次 commit 入口。
- 增加 `worktime.record.create.prepare/commit`：prepare 只生成缺参提示或服务端工时预览，commit 仅接受已批准且未过期的 action，并复用现有 `WorktimeService.createRecord` 的幂等、冲突和计算规则。
- 增加最小 Agent REST 入口：`GET /api/v1/agent/tools`、`POST /api/v1/agent/tools/{toolName}/invoke`、action approve/reject/commit；工具目录按当前用户 authority 过滤，所有调用仍经过 Domain Tool Registry。
- 工具调用由服务端当前用户上下文注入身份，调用前检查 authority，并继续复用现有工时/账本服务的用户与账本成员权限。
- 工具注册表拒绝重名工具和未知输入字段；工时查询加入 ISO 日期、日期范围、最大分页数量和 offset 上限校验。
- 增加 AI 模块单测和架构边界测试：核心领域不得依赖 AI，AI 不得依赖领域 Controller 或 Mapper，AI Java 源码必须归属物理模块。
- 新增 `AgentOrchestrator`，由首页既有 `/api/v1/ai/chat` 入口驱动 DeepSeek function calling；只向模型暴露当前用户可见的 R0/R1 工具，点分工具名转换为供应商兼容名称，执行时仍由 `DomainToolRegistry` 完成 Schema、authority 和领域权限校验。
- 工具结果作为不可信数据回传模型，单轮最多执行 4 次工具；达到上限后关闭工具选择并要求模型总结，写工具 R2-R4 不会进入模型上下文。
- LLM 网关已覆盖 OpenAI-compatible `tools`、`tool_choice`、assistant `tool_calls` 和 tool `tool_call_id` 请求格式，并解析 DeepSeek 工具调用响应。
- 修复新会话首条回答继续修改非响应式原始对象的问题；“正在思考…”和后续假打字机内容无需刷新即可显示，并加入独立浏览器回归。
- 增加 V13 `agent_session`、`agent_message` 表和用户隔离的 JDBC 会话服务；首页现将同一前端会话的 `sessionId` 发送到后端，模型每轮加载最近 20 条 user/assistant 消息，并在最终回答后持久化本轮问答。
- 上下文有长度上限和消息长度校验；实时账本/工时数据仍要求重新调用只读工具，历史回答不能替代事实查询。不存在的或其他用户的会话 ID 不会被接受。
- DeepSeek OpenAI-compatible 响应中的输入、输出、总 Token 以及缓存命中/未命中用量现由网关解析；`AgentOrchestrator` 汇总一次用户 turn 中的多轮模型用量、总耗时以及每次工具的名称、状态、摘要和耗时，并返回前端。
- 首页助手消息现显示消息时间、工具调用明细、Token 用量和总耗时；用户与助手消息均可复制。元数据会随本地会话一同保存，刷新后仍可查看。

本次明确未实施：

- SSE、turn 状态、持久化 Agent trace、动态表单和完整服务端会话 UI；当前可展示本次响应的工具/Token/耗时，但这些 trace 元数据尚未写入服务端表，也仍不能恢复进行中的 turn。
- 账本真实写工具、工时修改/删除、revision 冲突展示和相关领域审计扩展。
- MCP Server、PAT/OAuth、scope、站内审批与外部客户端兼容验证。
- 文件、向量库和 RAG。

验证记录：

- `cd backend && mvn -pl modules/ai -am test` 成功；目标 Reactor 共执行 75 项，74 项通过、1 项账本 Excel fixture 跳过；其中 platform 1/1、AI 模块 27/27 通过。新增测试覆盖只读工具暴露、R2 隔离、参数校验、4 次调用上限及 DeepSeek 工具协议序列化/解析。
- `cd frontend && npm run build` 成功；`SALARY_E2E_SOURCE_BUILD=1 PLAYWRIGHT_BROWSERS_PATH=0 npx playwright test e2e/agent-chat.spec.js --project=desktop-chromium` 2/2 通过，覆盖新会话首条响应、Markdown、假打字机和用户控制的底部跟随。
- 本地真实 DeepSeek 手动验证通过：“我有哪些账本？”返回当前用户默认账本；“查询我最近的工时记录”调用查询工具并返回近 30 天 0 条；“帮我记今天工时”明确拒绝写入。发送后思考占位与回复均无需刷新可见。
- Testcontainers `AgentConversationIntegrationTest` 成功，真实 MySQL 从空库执行 V1-V13 并验证会话消息顺序和用户隔离；本地 Compose 后端已迁移至 V13 并启动成功。
- `cd frontend && npm run build && npm run api:check` 成功；新增浏览器回归覆盖同一 sessionId 的连续追问。
- `cd backend && mvn -pl app -am -Dtest=ArchitectureBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test` 成功；架构边界测试 7/7 通过。
- `cd backend && mvn test` 已运行至 app 的 Testcontainers 阶段；Docker 客户端连接成功，但 Ryuk 容器持续停在启动状态且未出现在 `docker ps`。使用 `TESTCONTAINERS_RYUK_DISABLED=true` 复测后，目标 `mysql:8.0.36` 容器也停在相同状态，两次测试进程均已人工终止。真实 MySQL 门禁仍标记为未完成，不能用本次结果宣称通过。

当前判定：本增量仍未满足阶段 2 的完整退出门禁。首页真实模型已能自主选择只读工具、保留最近文本上下文并显示工具/Token/耗时，但尚无 SSE、turn 状态、断流恢复和自动中文评测；下一增量优先建设服务端会话列表/消息查询与 turn 状态，再接入 SSE，不启动 MCP。

### 本地手动验证

1. 登录后在首页询问“我有哪些账本？”，确认回答来自当前用户可见账本。
2. 询问“查询我最近的工时记录”，确认模型调用工时查询工具并说明日期范围。
3. 询问“查看这个月的账本概览”；若未指定账本，模型应先查询账本列表，再查询概览或要求选择。
4. 询问“帮我记今天工时”，确认当前只读 Agent 不会声称已经写入，也不会调用 R2 工具。
5. 观察发送后立即出现“正在思考…”；Markdown、假打字机、手动上滚停止吸附和恢复到底部按钮仍应正常。

## 1. 背景与目标

当前系统已经具备以下基础：

- Java 17、Spring Boot、Spring Modulith 模块化单体和 MySQL/Flyway。
- 工时设置与记录资源 API，服务端持久化加班时长和真实时薪。
- 工时页面切换税前/税后口径时，单日和记录列表时薪按当前口径与当月工资实时重算；记录中的 `realHourlyWage` 仍作为保存时的服务端计算快照，不作为跨口径展示值。
- 账本、账户、分类、商家、项目、成员、角色、预算、流水、周期任务、导入导出、回收站和审计能力。
- 账本 IndexedDB + oplog local-first 同步链路。
- OpenAI 兼容 LLM 网关，以及自然语言记账“解析草稿 → 审核 → 确认”能力。
- AI 工作台页面骨架；首页已从本地关键词回复切换到服务端 DeepSeek 聊天，并具备带服务端文本上下文的只读工具调用循环。发送后立即创建“正在思考…”助手气泡，模型返回后在同一气泡内进行假打字机输出。助手内容支持经过标签白名单清理的 Markdown；只有用户处于底部附近时才自动跟随，向上滚动后停止吸附并提供恢复到底部的按钮。当前尚未形成可恢复的服务端 Agent turn。

本次改造的目标不是用对话页面替换现有 Web 页面，而是让同一套业务能力同时服务于三类入口：

```text
传统 Web 页面
Web AI 工作台
WorkBuddy / Codex / 其他 MCP Host
        │
        ▼
统一 Domain Tool 层
        │
        ▼
ledger / worktime 应用服务
        │
        ▼
权限、事务、校验、审计、幂等、同步
```

改造完成后，用户可以：

- 在工作台中用自然语言查询账本、报表和工时。
- 通过自然语言新增、修改或删除工时和账本数据。
- 在信息不足时通过受控弹窗补齐字段，在写入前查看结构化预览并确认。
- 从支持 MCP 的外部 Agent 查询或处理网站中的数据。
- 保留原有页面作为批量管理、复杂筛选、离线操作和故障降级入口。

## 2. 范围与硬约束

### 2.1 首轮范围

首轮只覆盖已经存在且具备服务端业务规则的能力：

- 工时设置、工时记录及服务端计算结果。
- 账本、账户、分类、商家、项目、预算、流水、周期任务、报表、导入导出、回收站和审计。
- 现有账本自然语言记账和图片识别能力的统一收口。
- Web 工作台会话、流式响应、补充输入、确认、执行和结果展示。
- MCP 工具发现、调用、认证、授权、审计和分阶段写入。

本计划的阶段 0-6 可以在 Phase 1 稳定后启动，不等待尚未实现的 Phase 2 任务域；任务模块完成后再按同一工具契约增加 `task.*`，不得反向改写已发布的工时和账本工具语义。

以下内容不作为首轮 Agent/MCP 的前置条件：

- 任务、提醒、日历、番茄钟和习惯模块。
- 文件域、MinIO/NAS、Tika、embedding、Qdrant 和知识库。
- RAG 检索和基于附件内容的问答。
- 跨域日报、周报、月报和年报事实表。

### 2.2 不可突破的边界

- 不删除现有页面，不改变传统页面的业务含义。
- 模型不得直接访问数据库、Mapper、Shell、内部网络或任意外部 URL。
- Agent、MCP 和 REST Controller 不得各自复制业务规则。
- 当前用户、账本权限和授权范围只能由服务端认证上下文注入，不能由模型传入。
- 所有业务写入必须复用领域服务的权限、事务、校验、审计、revision 和幂等规则。
- Agent 在线写入成功后，通过现有同步游标刷新账本本地投影；不得绕过 sync-engine 形成第二套前端状态。
- 断网时继续提供传统 local-first 页面；首轮不承诺离线大模型对话。
- 模型输出只能生成文本和受控结构化数据，不能生成并执行任意 HTML、JavaScript 或 SQL。

## 3. 目标架构

### 3.1 模块结构

新增 `backend/modules/ai` Maven 模块。`app` 只负责装配和运行配置，AI 业务代码不继续堆放在 `backend/app`。

```text
com.salarytracker.ai/
├── api/                 # Web Agent REST/SSE API、DTO
├── agent/               # 会话、turn、编排和模型循环
├── tool/                # 工具契约、注册表、策略和执行器
│   ├── ledger/
│   └── worktime/
├── action/              # 待补充/待确认 action 生命周期
├── mcp/                 # MCP Server、工具适配和认证上下文
├── model/               # AgentModel 和供应商适配器
├── trace/               # trace、usage、脱敏和指标
└── infra/               # JDBC repository、时钟、序列化等适配器
```

主要职责：

| 组件 | 职责 |
|---|---|
| `AgentOrchestrator` | 维护模型与工具调用循环，控制最大轮次、超时、取消和结束条件 |
| `AgentSessionService` | 保存会话、消息、turn 和恢复点；保证刷新或断流后可以查询最终状态 |
| `DomainToolRegistry` | 注册工具元数据、输入 Schema、风险级别、scope 和执行器 |
| `InteractionPolicy` | 决定直接执行、补充输入、普通确认或站内审批 |
| `PendingActionService` | 管理 action 创建、回答、批准、拒绝、过期和单次提交 |
| `AgentTraceService` | 记录模型请求摘要、工具调用、耗时、token 和失败原因 |
| `McpToolAdapter` | 将 Domain Tool 映射为 MCP Tool，不复制工具实现 |
| `AgentModel` | 屏蔽 DeepSeek/OpenAI 等供应商差异，支持结构化输出和工具调用 |

### 3.2 依赖规则

- `ai` 可以依赖 `identity`、`worktime` 和 `ledger` 暴露的公共应用接口。
- `worktime` 和 `ledger` 不依赖 `ai` 或 MCP。
- 内部 Agent 通过进程内接口调用 Domain Tool，不反向 HTTP 调用本站 `/mcp`。
- MCP Server 和 Web Agent 共享 `DomainToolRegistry`、`InteractionPolicy` 和 `PendingActionService`。
- 工具适配器只能编排公开应用接口，不得直接查询或更新其他模块的表。
- 使用 Spring Modulith 和 ArchUnit 固化上述依赖方向。

### 3.3 供应商与框架边界

- 保留现有 OpenAI 兼容网关配置和服务端密钥管理。
- 在其上增加 `AgentModel` 抽象，首个实现支持工具调用、结构化 JSON 和流式文本。
- Domain Tool、action 状态机和确认策略不得依赖特定模型 SDK。
- MCP 采用官方 Java MCP SDK 的 Spring WebMVC/Streamable HTTP 传输；具体版本在开始 Phase 3C 时锁定并记录兼容矩阵。
- 更换模型或 MCP SDK 不能改变领域工具的名称、Schema、风险级别和业务结果。

## 4. 工具契约与执行模型

### 4.1 工具命名和版本

工具使用稳定的点分名称：

```text
<domain>.<resource>.<action>[.<stage>]
```

例如：

```text
worktime.records.search
worktime.record.create.prepare
worktime.record.create.commit
ledger.transaction.update.prepare
ledger.transaction.update.commit
```

每个工具登记以下元数据：

- `name`：稳定名称，发布后不原地改变语义。
- `version`：整数版本，输入或结果不兼容时递增。
- `description`：面向模型的简明用途和禁止事项。
- `inputSchema`：严格 JSON Schema，默认拒绝未知字段。
- `outputSchema`：统一 Tool Result 加领域 `structuredContent`。
- `riskLevel`：R0-R4。
- `requiredScopes`：Web 权限和 MCP scope 映射。
- `timeout`、`maxResultItems` 和 `rateLimitClass`。

### 4.2 统一状态机

```text
RECEIVED
  → PLANNING
  → WAITING_INPUT | WAITING_CONFIRMATION
  → APPROVED
  → EXECUTING
  → COMPLETED | CONFLICT | DENIED | FAILED | CANCELLED | EXPIRED
```

状态转换规则：

- `WAITING_INPUT` 只能通过回答 action 返回 `PLANNING` 或进入 `WAITING_CONFIRMATION`。
- `WAITING_CONFIRMATION` 只能批准、拒绝或过期，不能直接执行。
- `APPROVED` 的 action 只允许一次 `commit` 抢占执行权。
- `EXECUTING` 成功后不可退回等待状态；失败重试必须使用同一个幂等键。
- `CONFLICT` 必须重新读取服务端事实并创建新 action，不能复用旧 revision 强行提交。
- 已拒绝、取消、完成或过期的 action 不能恢复。

### 4.3 风险分级

| 等级 | 代表操作 | Web Agent | MCP |
|---|---|---|---|
| R0 | 页面导航、打开编辑器 | 直接执行 | 默认不暴露 UI 工具 |
| R1 | 查询工时、流水、报表 | 直接执行，限制范围和数量 | 获得 read scope 后直接执行 |
| R2 | 新增工时、记账、修改个人设置 | 结构化预览并确认 | prepare 后确认，获得 commit scope 才可提交 |
| R3 | 修改、删除、恢复、执行周期任务 | 展示前后差异并强制确认 | prepare/commit，必要时转站内审批 |
| R4 | 删除账本、成员和角色权限、批量导入、永久清除 | 必须站内审批 | 不直接执行，只返回站内审批链接 |

### 4.4 两阶段写入

所有 R2-R4 写工具拆成 `prepare` 和 `commit`：

1. `prepare` 只解析、查询和生成预览，不产生业务写入。
2. 缺少字段时返回 `needs_input` 和白名单表单 Schema。
3. 存在多个候选时返回候选 ID、显示名称和必要摘要，不由模型擅自选择。
4. 参数完整后返回 `needs_confirmation`、变更摘要、风险提示和过期时间。
5. 用户批准后，服务端把 action 转为 `APPROVED`。
6. `commit` 使用 action 中保存的规范化参数，不接受模型重新提交一份不同业务参数。
7. 执行前重新校验认证、scope、账本访问权、资源状态、revision 和幂等键。
8. 成功后记录结果资源 ID、业务审计和工具 trace；同一 action 再次提交返回原结果。

禁止把“`confirmed=true`”之类的模型参数当作用户确认依据。

### 4.5 统一工具结果

```json
{
  "status": "completed|needs_input|needs_confirmation|conflict|denied|failed",
  "summary": "人类可读说明",
  "structuredContent": {},
  "actionId": null,
  "confirmationUrl": null,
  "expiresAt": null,
  "traceId": "trace_xxx"
}
```

- `summary` 不包含密码、令牌、完整隐私数据或模型内部推理。
- `structuredContent` 必须符合工具输出 Schema。
- 错误使用稳定 code，模型友好文本和日志详细错误分离。
- 查询结果必须分页；模型上下文只接收当前任务所需字段。

## 5. Web 工作台 Agent

### 5.1 API

新增：

```text
POST   /api/v1/agent/sessions
GET    /api/v1/agent/sessions
GET    /api/v1/agent/sessions/{id}/messages
POST   /api/v1/agent/sessions/{id}/turns
GET    /api/v1/agent/turns/{id}
POST   /api/v1/agent/actions/{id}/answer
POST   /api/v1/agent/actions/{id}/approve
POST   /api/v1/agent/actions/{id}/reject
POST   /api/v1/agent/turns/{id}/cancel
```

所有接口要求登录，会话和 action 按不可变用户 ID 隔离。`GET /turns/{id}` 用于 SSE 断开后的状态对账。

### 5.2 SSE 事件

`POST /sessions/{id}/turns` 创建 turn 并返回 SSE：

```text
turn.started
assistant.delta
tool.started
tool.completed
input.required
confirmation.required
navigation.requested
turn.completed
turn.failed
```

要求：

- 每个事件携带 `sessionId`、`turnId`、单调 `sequence` 和时间戳。
- 文本 delta 可以丢失后重建；action 和最终结果必须持久化。
- 浏览器断开不取消服务端执行，用户可显式调用 cancel。
- 服务端完成事件必须包含最终状态，前端不能只依赖连接关闭判断成功。
- 同一 turn 只允许一个活动执行器，重试创建请求必须幂等。

### 5.3 受控 UI Schema

模型不得生成任意 HTML。后端只允许：

```text
text
textarea
number
money
date
time
select
entity-picker
toggle
file
confirmation-summary
diff
```

字段至少包含 `name`、`label`、`control`、`required`、`value`、`options`、`validation` 和 `helpText`。服务端负责 Schema 校验；前端校验只用于即时反馈。

前端组件：

- `AgentConversationList`：服务端会话列表和新建/删除入口。
- `AgentMessageThread`：消息、工具状态和结果展示。
- `AgentComposer`：文本、图片和以后文件/音频输入。
- `AgentActionCard`：显示等待输入、等待确认、执行中和失败状态。
- `AgentFormDialog`：桌面动态表单。
- `AgentFormDrawer`：移动端动态表单。
- `AgentConfirmDialog`：最终业务摘要和风险提示。
- `AgentResultCard`：成功结果、资源链接和继续操作。

前端按服务端事件渲染，不根据自然语言自行猜测业务状态。

## 6. 工时工具覆盖

### 6.1 工具清单

```text
worktime.settings.get
worktime.settings.update.prepare
worktime.settings.update.commit
worktime.records.search
worktime.record.create.prepare
worktime.record.create.commit
worktime.record.update.prepare
worktime.record.update.commit
worktime.record.delete.prepare
worktime.record.delete.commit
```

### 6.2 行为约定

- “今天”“昨天”“上周”等相对日期按用户时区解析，默认 `Asia/Shanghai`。
- “帮我记今天工时”缺少开始和结束时间时返回输入表单。
- 完整输入直接生成预览，但仍需用户确认写入。
- 修改指令先查询候选；无法唯一定位时要求用户选择。
- 修改预览展示日期、开始、结束、休息时间和备注的前后差异。
- 删除必须展示目标记录和服务端计算结果，不支持按模糊条件批量删除。
- commit 后以服务端返回的 `overtimeMin`、`realHourlyWage`、`calcVersion` 和 `timezone` 为准。
- revision 冲突返回 `conflict`，附带最新服务端摘要并要求重新 prepare。

### 6.3 验收语句

- “帮我记今天工时。”
- “今天九点上班，晚上八点半下班，休息一小时。”
- “昨天加班到九点。”
- “把 9 月 18 日的休息时间改成 45 分钟。”
- “删除昨天的工时记录。”
- “这个月加了多少班，真实时薪是多少？”

每个语句至少验证：完整输入、缺参、歧义、拒绝确认、重复提交、无权限和 revision 冲突。

## 7. 账本工具覆盖

### 7.1 第一批核心工具

```text
ledger.books.list
ledger.overview
ledger.transactions.search
ledger.transaction.create.prepare/commit
ledger.transaction.update.prepare/commit
ledger.transaction.delete.prepare/commit
ledger.transaction.history
ledger.reports.summary
ledger.budgets.list
```

自然语言记账复用现有账户、两级分类、商家、成员和项目匹配规则。收入和支出缺少有效二级分类时不能 commit；转账必须有转出和转入账户；未指定成员时使用当前登录用户在当前账本中的成员记录。

### 7.2 第二批管理工具

```text
ledger.book.create/update/delete
ledger.account.list/create/update/delete
ledger.category.list/create/update/delete
ledger.merchant.list/create/update/delete
ledger.project.list/create/update/delete
ledger.budget.upsert/delete
ledger.schedule.list/create/update/delete/run
ledger.recycle.list/restore/purge
ledger.audit.list/clear
ledger.import.preview/confirm
ledger.export
```

约束：

- 所有新增、修改和删除均按风险级别转换为 prepare/commit，清单中的简写不代表可以直接写入。
- 成员、角色和权限操作属于 R4，首轮只允许 Web 站内审批，不向 MCP 暴露 commit。
- 账本删除、永久清除、批量导入确认和批量清理审计属于 R4。
- `sync/push`、`sync/pull`、余额物化和维护任务是基础设施接口，不注册为用户 Agent Tool。
- 导出返回受认证的短期下载结果，不把完整文件内容塞进模型上下文。
- 查询工具限制账本、日期范围、分页大小和排序字段。

### 7.3 验收语句

- “午饭花了 32 元。”
- “昨天在盒马买菜 128.6，用微信支付，记到餐饮/买菜。”
- “把昨天 32 元午饭改成 35 元。”
- “删除昨天那笔 200 元支出。”
- “这个月餐饮花了多少，比上个月多多少？”
- “列出本月超过 500 元的支出。”
- “把每月 5 号的房租周期任务暂停。”
- “导出这个账本今年的流水。”

多笔匹配、同名分类、隐藏资源、共享账本权限、转账和债权债务类型必须分别建立测试样例。

## 8. MCP Server

### 8.1 协议与部署

MCP Server 与现有 Spring Boot 应用同进程部署，由 Nginx 暴露：

```text
POST https://<domain>/mcp
```

首选 Streamable HTTP。只有目标 WorkBuddy/Codex 版本确实不兼容时才增加旧 SSE 传输，兼容端点不得形成第二套工具实现。

首轮提供：

- `initialize`：协议和能力协商。
- `tools/list`：按认证用户和 scope 返回可见工具。
- `tools/call`：调用统一 Domain Tool。
- `resources/list`：后续用于帮助文档和只读报表，不暴露数据库实体全集。
- `prompts/list`：后续可提供“月度复盘”“工时补录”等模板，不作为上线前置条件。

MCP 错误应同时满足协议错误结构和应用稳定错误 code；内部堆栈不得返回给客户端。

### 8.2 MCP 与 Web 的能力差异

| 能力 | Web Agent | MCP |
|---|---|---|
| 查询 | 登录权限允许即可 | 还需 read scope |
| 补充字段 | Dialog/Drawer | MCP elicitation；不支持时由 Agent 追问后重新调用 |
| 普通确认 | Web 确认组件 | prepare/commit；依赖 commit scope |
| 高风险确认 | 站内审批 | 返回 `confirmationUrl`，批准后才可 commit |
| 页面导航 | 支持 | 不暴露 |
| 文件下载 | 浏览器下载 | 返回受控链接或资源引用，不返回服务器路径 |

### 8.3 认证演进

第一步：Personal Access Token（PAT）。

- 用于本地 Codex、开发联调和不支持 OAuth 的客户端。
- 创建时只显示一次明文，数据库只保存带独立 salt 的 hash。
- 记录名称、客户端说明、scope、可访问账本、创建时间、过期时间、最后使用时间和撤销时间。
- 默认只读、默认短有效期；用户可以随时撤销。
- PAT 不能替代网站登录 token，也不能作为 refresh token 使用。

第二步：OAuth 2.1 Authorization Code + PKCE。

- 提供授权服务器元数据、受保护资源元数据、授权端点和 token 端点。
- 正式远程客户端通过浏览器完成登录、scope 展示和授权。
- refresh token 轮换并支持撤销；客户端和 redirect URI 必须登记。
- OAuth 上线后 PAT 仍仅作为受限兼容方式保留。

Scopes：

```text
mcp:ledger:read
mcp:ledger:prepare
mcp:ledger:commit
mcp:worktime:read
mcp:worktime:prepare
mcp:worktime:commit
```

`tools/list` 不返回超出 scope 的工具；`tools/call` 仍必须再次校验，不能只依赖列表隐藏。

### 8.4 MCP 写入确认

- R2/R3 的 prepare 返回 `actionId`，MCP elicitation 仅用于收集字段或向用户展示摘要。
- MCP Host 声称“用户已确认”不能直接把 action 改为 `APPROVED`。
- 低风险 MCP commit 必须拥有 commit scope，并使用服务端生成的单次 action。
- R4 返回站内 `confirmationUrl`；用户登录本站审批后，action 才进入 `APPROVED`。
- confirmation URL 使用短期随机 token，但审批仍绑定登录用户、MCP client、action 和业务摘要。
- 审批后参数被冻结；任何参数变化都必须创建新 action 并重新审批。

### 8.5 真实客户端验证

每个受支持客户端记录：

- 客户端名称和版本。
- 使用的传输和认证方式。
- 支持的工具、elicitation 和资源能力。
- 已通过的只读、prepare、审批和 commit 场景。
- 已知限制和复测日期。

至少使用 MCP Inspector、Codex 和 WorkBuddy 各完成一次真实连接。不能仅以单元测试宣称兼容。

## 9. 数据模型与迁移

新增 Flyway 迁移，不修改已经应用的旧迁移：

| 表 | 主要用途 |
|---|---|
| `agent_session` | 用户会话、标题、状态、最近活动和上下文摘要 |
| `agent_message` | 用户/助手/工具消息和内容类型 |
| `agent_turn` | 单次输入执行状态、模型、开始/结束时间和错误码 |
| `agent_pending_action` | 工具版本、规范化参数、预览、revision、风险、审批和过期状态 |
| `agent_tool_call` | 工具调用链、脱敏输入摘要、结果状态、耗时和幂等键 |
| `ai_usage` | 模型、输入/输出 token、费用估算、来源和日期 |
| `agent_prompt_version` | 系统提示版本、启用状态和校验 hash |
| `mcp_personal_token` | PAT hash、scope、账本限制、过期和撤销信息 |
| `mcp_oauth_client` | OAuth 客户端和 redirect URI 元数据 |
| `mcp_grant` | 用户对客户端的 scope 授权和撤销状态 |

数据要求：

- 所有用户数据表带 `user_id`、创建时间和必要索引。
- action 保存工具名称与版本、参数快照、业务预览、目标 revision、来源渠道和过期时间。
- confirmation token、PAT 和 refresh token 只保存 hash。
- 工具输入/输出默认脱敏；密码、JWT、API key、完整附件和无关财务明细不得进入 trace。
- 业务变更继续进入现有审计日志，Agent trace 只说明“如何发起和执行”，不代替业务审计。
- 会话删除、审计保留和 AI usage 保留期必须可配置，并提供清理任务。

## 10. 安全、隐私与可靠性

### 10.1 安全控制

- 工具白名单：模型只能看到当前用户、渠道和 scope 允许的工具。
- 参数校验：所有模型参数经过 JSON Schema 和领域校验，拒绝未知字段。
- 身份注入：user ID、角色、账本访问权和 MCP client ID 由认证上下文提供。
- 查询限额：限制日期范围、页大小、总返回量和可选排序，防止全库泄露。
- 工具限流：按用户、客户端、工具风险和 IP 分层限流。
- Prompt Injection：用户文本、账本备注和未来文档内容均视为不可信数据，不得改变系统指令和工具权限。
- SSRF 防护：首轮 Agent 无任意 URL 获取工具；未来附件工具只接收内部 file ID。
- 重放防护：action 单次提交、短期过期、幂等键和 token hash 校验。
- 高风险审批：R4 永远需要站内登录审批，MCP 默认不提供直接 commit。

### 10.2 故障语义

- 模型超时：turn 标记失败，不创建业务写入；已创建 action 保持可解释状态。
- SSE 断开：服务端继续执行，客户端通过 turn 查询恢复。
- 工具成功但响应丢失：同一 action 和幂等键重试返回原结果。
- 数据冲突：不自动覆盖，返回最新服务端摘要并要求重新 prepare。
- 模型供应商不可用：只读/写 Agent 暂停，传统页面继续工作；现有确定性本地记账解析可作为明确标识的受限降级。
- MCP 客户端异常循环：触发限流、熔断和审计告警，不影响 Web 页面。

## 11. 分阶段实施和验证门禁

每个阶段必须提交：实现、自动化测试、文档更新、测试命令和结果、部署说明、功能开关及回滚说明。

### 阶段 0：基线与契约

**当前状态：部分完成。** 后端 Maven 与架构测试基线已重验；功能覆盖矩阵、中文评测集和本轮前端/Playwright 回归尚未完成。

实施：

- 固化现有工时、账本、权限、同步和 AI 草稿契约。
- 建立“现有用户功能 → Domain Tool → Web Agent → MCP”覆盖矩阵。
- 建立中文指令评测集，包含完整、缺参、歧义、错误和越权表达。
- 固化 OpenAPI 生成、Maven 模块和前端测试基线。

验证：

- `cd backend && mvn test`
- `cd frontend && npm run api:check && npm test`
- `cd frontend && npm run build`
- 现有 Playwright 工时和账本主流程。
- `git diff --check`

退出门禁：现有测试全部通过，覆盖矩阵和评测集可审查，未改变生产行为。

回滚：仅测试和文档变更，无运行时功能需要回滚。

### 阶段 1：统一 Domain Tool

**当前状态：部分完成。** AI 物理模块、工具契约/注册表/风险/结果基础、七个 R1 查询工具、action JDBC 持久化以及首个工时新增 prepare/commit 已完成；其余写工具、revision 差异预览和隔离 MySQL 集成门禁尚未完成。

实施：

- 创建 `ai` 模块、工具契约、注册表、风险策略和 action 状态机。
- 先包装只读工具，再实现 prepare/commit。
- 暂不连接模型和 MCP，以集成测试直接调用工具。
- 加入迁移、幂等、revision、权限和审计。

验证：

- 每个工具覆盖成功、缺参、无权限、无资源、冲突、过期和重复提交。
- Testcontainers 验证真实 MySQL 事务与 Flyway。
- ArchUnit 验证 ai 不直接访问领域内部实现或表。
- 确认 prepare 不产生业务数据变更。

退出门禁：核心工时和账本工具具备稳定 Schema；重复 commit 不重复写入；越权调用为零。

上线与回滚：`agent.enabled=false`，仅部署内部代码和表；回滚应用版本时保留新增表。

### 阶段 2：只读 Web Agent

**当前状态：部分完成。** 首页已接入真实 DeepSeek，最小 `AgentOrchestrator` 能选择和串联当前用户可用的 R0/R1 Domain Tool；会话、turn、SSE、断流恢复、trace 和自动评测尚未完成。

实施：

- 接入会话、消息、turn、SSE 和 AgentModel。
- 开放工时、流水、账本总览、预算和报表查询。
- 接入导航卡片，但不开放写工具。
- [x] 将首页本地关键词回复替换为真实服务端 DeepSeek 聊天；保留假打字机，未配置密钥或上游失败时展示明确状态。
- [x] 建立最多 4 次调用的只读模型工具循环；R2-R4 不向模型暴露，工具执行继续复用注册表的 Schema、权限和领域校验。
- [x] 持久化前端会话的最近 20 条文本消息，连续追问复用同一 `sessionId`；会话按认证用户隔离，历史实时数据仍强制重新查询。

验证：

- 模型只能选择允许的只读工具。
- 会话按用户隔离，刷新后历史可恢复。
- SSE 断开后可通过 turn API 对账。
- 大范围查询自动收窄或要求补充条件。
- 模型错误或超时不影响传统页面。

退出门禁：评测集只读工具选择准确率达到约定阈值；无跨用户/跨账本泄露；主要浏览器流式交互稳定。

上线与回滚：只对内部测试用户打开 `agent.enabled`；关闭开关恢复传统首页入口。

### 阶段 3：Web Agent 写入

实施：

- 接入动态表单、确认弹窗、diff、审批状态和结果卡片。
- 先开放记账和记工时，再开放修改、删除及管理工具。
- 写入完成后触发账本增量同步和 Pinia 投影刷新。
- 把现有账本 AI 草稿接入统一 action 协议。

验证：

- 详细输入直接预览，缺参弹窗，歧义展示候选。
- 取消和拒绝不产生写入。
- 重复确认、刷新重试和断线重试只写一次。
- revision 冲突不覆盖最新数据。
- 桌面 Dialog、移动 Drawer、键盘和屏幕阅读器可用。
- Agent 写入后传统账本页面和 IndexedDB 数据一致。

退出门禁：核心写入 E2E 全部通过；错误写入为零；审计能关联 action、tool call 和业务资源。

上线与回滚：独立使用 `agent.write-enabled` 灰度；关闭后保留只读 Agent。

### 阶段 4：只读 MCP

实施：

- 接入官方 MCP Java SDK 和 Streamable HTTP。
- 实现 PAT 管理页面和 token scope。
- 只暴露工时、账本 R1 工具。
- 增加客户端、工具和用户维度审计与限流。

验证：

- MCP Inspector 完成 initialize、tools/list、tools/call 和错误场景。
- Codex 和 WorkBuddy 分别完成真实连接。
- 验证跨用户隔离、scope 隐藏与调用时二次校验。
- 验证 PAT 过期、撤销、账本限制、分页和限流。

退出门禁：真实客户端只读用例通过；撤销即时生效；不存在越权工具或数据泄露。

上线与回滚：使用 `mcp.enabled`；关闭 Nginx 路由和应用开关即可停用，不影响 Web Agent。

### 阶段 5：MCP 写入

实施：

- 开放 prepare 工具和 MCP elicitation 兼容流程。
- 建设网站内待审批中心和短期 confirmation URL。
- 先开放 R2 commit，再按验证结果开放部分 R3。
- 增加 OAuth 2.1 + PKCE，PAT 保持受限兼容。

验证：

- 无审批、伪造确认、审批过期、参数变化、重复提交均不能产生额外写入。
- 缺少 prepare/commit scope 时分别拒绝。
- 伪造 user ID、book ID 和 action ID 均被认证上下文拦截。
- OAuth redirect URI、PKCE、refresh rotation 和撤销通过安全测试。
- MCP commit 后 Web 页面和本地投影正确刷新。

退出门禁：低风险写入在 Codex、WorkBuddy 中通过；高风险操作只能完成站内审批；错误写入为零。

上线与回滚：使用 `mcp.write-enabled` 和 `mcp.oauth-enabled` 分开灰度；关闭写开关后保留只读 MCP。

### 阶段 6：现有功能全量覆盖与稳定化

实施：

- 完成所有用户级现有功能的 Agent 覆盖矩阵。
- 补齐管理工具、导入导出、回收站和周期任务。
- 建立提示词/模型/工具版本回归评测和失败回放。
- 加入成本告警、客户端熔断、数据保留清理和运营仪表盘。

验证：

- 覆盖矩阵逐项签字，无无主工具和无测试工具。
- 批量、长会话、并发、超时和供应商故障压测。
- 生产影子流量或内部灰度观察成功率、澄清率、取消率和人工纠正率。
- 季度安全复测和备份恢复演练。

退出门禁：关键链路有指标、审计、告警和运行手册；连续灰度周期无 P0/P1 数据事故。

### 阶段 7：文件、向量库与 RAG

只有阶段 0-6 全部通过后才开始：

- file 模块和 MinIO/NAS。
- Tika 文本抽取、分块和重试管道。
- embedding 和 Qdrant，按 `user_id + 业务域` 强过滤。
- 带引用定位的知识问答。
- MCP `knowledge.search`、`knowledge.document.list` 和受控重新索引工具。

RAG 文档内容始终是不可信输入，不允许修改系统提示、授权范围或工具参数。

## 12. 测试矩阵

| 层级 | 必测内容 | 退出标准 |
|---|---|---|
| 工具单测 | Schema、未知字段、缺参、默认值、风险和 scope | 每个工具所有状态分支通过 |
| 领域集成 | MySQL、事务、权限、幂等、revision、审计 | Testcontainers 全绿，无重复写入 |
| Agent 评测 | 同义词、相对日期、多笔输入、歧义、拒绝和越权 | 固定数据集可重复，版本间退化可见 |
| 前端组件 | SSE reducer、表单 Schema、action 状态和错误恢复 | 状态转换全覆盖 |
| Web E2E | 输入、确认、取消、刷新、断线、移动端 Drawer | Chromium/WebKit 主流程通过 |
| MCP 合约 | initialize、list、call、协议错误和分页 | MCP Inspector 全部通过 |
| MCP 安全 | scope、撤销、伪造、重放、限流、审批 | 未授权写入和数据泄露为零 |
| 同步回归 | Agent 写入、增量 pull、冲突和用户隔离 | IndexedDB 与服务端最终一致 |
| 故障恢复 | 模型超时、SSE 断开、响应丢失、commit 重试 | 无半笔数据，可查询最终状态 |
| 真实兼容 | Codex、WorkBuddy 连接和写入审批 | 保存版本、证据和已知限制 |

## 13. 发布、指标和回滚

功能开关：

```text
agent.enabled
agent.write-enabled
mcp.enabled
mcp.write-enabled
mcp.oauth-enabled
```

核心指标：

- Agent turn 成功率、P50/P95 耗时和取消率。
- 工具选择准确率、参数校验失败率和平均工具调用次数。
- `needs_input`、`needs_confirmation`、批准和拒绝比例。
- commit 幂等命中、revision 冲突和业务失败数量。
- MCP 按客户端、用户和工具统计的调用量、失败率和限流次数。
- 模型输入/输出 token、费用和供应商错误率。
- 每个 prompt、模型和工具版本的评测结果。

告警：

- 未授权调用或跨账本访问尝试激增。
- 同一 action 多次 commit 或幂等异常。
- MCP 客户端短时间高频写入。
- 工具错误率、模型超时率或成本突然上升。
- Agent 写入后同步游标长时间不推进。

回滚原则：

- 先关闭最小粒度功能开关，再回滚应用版本。
- 数据库迁移只向前兼容，回滚应用时保留新增表，不执行破坏性 down migration。
- 传统 Web 页面和原有 API 始终作为可用回退路径。
- 写入事故时立即关闭 Agent/MCP 写开关，保留查询和审计能力用于排查。

## 14. 完成定义

Agent 与 MCP 改造只有在以下条件全部满足时才算完成：

- 工时和账本全部用户级功能已进入覆盖矩阵，并明确支持渠道和风险级别。
- Web Agent 能完成查询、缺参补充、歧义选择、确认、执行、结果和失败恢复。
- MCP 可以被 MCP Inspector、Codex 和 WorkBuddy 真实连接。
- 默认只读、scope、PAT/OAuth、站内审批和撤销机制可用。
- 所有写操作复用领域规则，具备幂等、revision、权限和业务审计。
- Agent/MCP 写入不会破坏现有 local-first 同步和用户/账本隔离。
- 关键行为有自动化测试、指标、告警、运行手册和可操作的功能开关。
- 文件、向量库与 RAG 在 Agent/MCP 核心链路稳定后独立建设，不反向阻塞现有能力交付。

## 15. 已确定的默认决策

- 保持 Java 模块化单体，不新增 Python 服务。
- 新增独立 `ai` Maven 模块。
- 内部 Agent 与 MCP 共用 Domain Tool，但不通过 HTTP 相互调用。
- MCP 首选 Streamable HTTP。
- PAT 用于首轮兼容验证，OAuth 2.1 + PKCE 用于正式远程接入。
- MCP 默认只读；prepare 和 commit 使用独立 scope。
- 所有业务写入采用 prepare/commit。
- R4 操作必须在网站内审批，外部 Agent 不能自行声明用户已经确认。
- RAG 不进入首轮 Agent 和 MCP 交付范围。
- 各阶段必须串行通过退出门禁，允许并行的仅限不改变契约的 UI、测试和文档工作。
