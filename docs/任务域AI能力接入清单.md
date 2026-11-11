# 任务域 AI 能力接入清单

> 版本 v1.0 · 2026-11-11
> 范围：`task` / `habit` / `focus` / `countdown` 四组能力，对齐账本（`ledger.*`）与工时（`worktime.*`）已开放的 Agent + MCP 能力。
> 依据：`backend/modules/ai/src/main/java/com/salarytracker/ai/{tool,mcp}`、`AgentPromptPolicy.java`、`docs/Agent功能覆盖与中文评测集.md`、`docs/Phase 2 —— 任务域需求文档.md` §T-704。

---

> **P0 完成记录（2026-11-11）**：下面 5 条阻断已全部修复，任务域工具从 16 个增至 18 个
> （新增 `task.lists.list`、`task.tags.list` 目录工具），提示词升级 `agent-system-v13`，
> 站内确认入口恢复为独立页面 `/agent/chat`。P1 起的清单见文末 §9。

## 0. 结论（P0 修复前的现状）

任务域工具**骨架已注册 16 个**，但**整条链路没通**，当时属于「写了但用不了」：

1. 模型没有任务域提示词（`AgentPromptPolicy` v12 全文只出现一次 `task`，且是账本周期任务的 `taskId`）；
2. MCP 的 `requireScope()` 把 `task.*` / `habit.*` / `focus.*` 全部判成**工时** scope，只给任务 scope 的 Token 一调就 403；
3. 站内确认入口在首页改画布时丢了（`/` 不再是对话页），**任何 prepare 都无法在站内确认**——这一条同时打断账本和工时的站内写能力；
4. 没有清单/标签目录工具，`listId` / `tagId` 只能靠猜，违反账本已经建立起来的「先查目录再用真实 ID」硬规则；
5. `task.today` 实际返回「今天 + 全部逾期」，`task.overdue` 只在前 200 条里过滤，且用 `Instant.now()` 判定，
   把当天已过时刻的任务误判为逾期。

建议按 **P0 修链路（0 个新工具）→ P1 只读补齐 → P2 高频写 → P3 管理类 → P4 R4 审批** 推进，工具数 **16 → 72**（其中 8 个 R4 仅站内审批，模型不可见）。

---

## 1. 基线：账本 / 工时是怎么「开放」的

账本和工时的可用性不是靠工具数量堆出来的，而是四层同时成立：

| 层 | 载体 | 账本 | 工时 |
|---|---|---|---|
| L1 工具 | `DomainTool` + `ToolDefinition` + `ToolRisk` | 29 个（目录 / 查询 / 单条 / 批量 / 周期 / 回收站 / 导入导出） | 10 个（设置读改 + 记录增删改查） |
| L2 提示词 | `AgentPromptPolicy.systemPrompt` | 每个可写工具都在提示词里点名，并写反例（禁止猜 ID、禁止把文本当批准） | 同 |
| L3 外部 MCP | `visible()` + `requireScope()` + `McpContentService` | scope 前缀判定正确，资源 / 模板 / prompt 齐全 | 同 |
| L4 站内确认 | action 卡片 + `/mcp/actions/confirm` + `/approvals` | 齐全，R4 走审批中心 | 同 |

**任务域的差距是 L2 完全没有、L3 判错、L4 断了，而 L1 只做了最小子集。**

---

## 2. 现状盘点

### 2.1 已注册的工具（P0 后 18 个 → P1 后 30 个）

> 下表是 P0 前的原始盘点，保留用于对照；P1 之后的完整清单见 §9 末尾「P1 交付清单」。

| 工具 | 风险 | 承接服务 | 说明 |
|---|---|---|---|
| `task.list.query` | R1 | `TaskService.listTasks` | 清单/状态/视图/标签/优先级/日期范围 |
| `task.today` | R1 | `TaskService.todayTasks` | P0 已改为只含当天到期，日界按用户时区 |
| `task.overdue` | R1 | `TaskService.overdueTasks` | P0 已改为独立 SQL，按到期日早于今天判定 |
| `task.lists.list` | R1 | `TaskService.listLists/listGroups` | P0 新增，清单与分组的真实 ID 目录 |
| `task.tags.list` | R1 | `TaskService.listTags` | P0 新增，标签真实 ID 目录 |
| `task.stats.summary` | R1 | `TaskEnhancementService.stats` | 创建/完成/逾期完成/专注/分布 |
| `habit.checkin.query` | R1 | `TaskEfficiencyService.habits/habitStats` | 语义混装：无 `habitId` 时返回习惯列表 |
| `focus.stats.query` | R1 | `TaskEfficiencyService.focusStats` | 今日/本周/本月 |
| `task.create.prepare/commit` | R2 | `TaskService.create` | ⚠️ 不支持 tags / checklist / parentId / duration / reminder |
| `task.complete.prepare/commit` | R2 | `TaskService.complete` | — |
| `task.reschedule.prepare/commit` | R2 | `TaskService.reschedule` | — |
| `task.update.prepare/commit` | R3 | `TaskService.update` | ⚠️ 只能改 title / description / priority |
| `task.delete.prepare/commit` | R3 | `TaskService.delete` | — |

测试现状（P0 前）：`TaskDomainToolsIntegrationTest` 只有 1 个用例（prepare 零写入 + 单次 commit）；`McpToolVisibilityTest` 虽有 `task.*` 的 `visible()` 断言，但**没有覆盖 `requireScope()`**——而 T-A1 恰好只存在于 `requireScope()`，所以测试全绿但实际调用 403。P0 已补 `requiredScope` 与「只读任务 Token 可调用全部可见任务工具」两条回归用例，并新增 `TaskDueWindowToolsIntegrationTest` 锁日界。

### 2.2 P0 阻断项（不修，后面写多少工具都调不通）

| 编号 | 问题 | 位置 | 影响 | 修法 |
|---|---|---|---|---|
| **T-A1** | `requireScope()` 只有「ledger 前缀 / 其它」两分支，`task.*`、`habit.*`、`focus.*` 落到工时分支 | `McpServerConfiguration.java:249` | 只授予 `mcp:task:read` 的 PAT：`tools/list` 看得到工具，调用全部 `MCP_SCOPE_DENIED`；同时持有 `mcp:worktime:read` 时才「碰巧能用」 | ✅ 已修：抽出 `requiredScope()`，`visible()` 与校验共用同一份前缀规则 |
| **T-A2** | 提示词完全没有任务域 | `AgentPromptPolicy.java:19`（v12） | 工具在模型目录里但无使用说明，实测会去查工时或直接编造 | ✅ 已修：升级 `agent-system-v13`，见 §4.1 |
| **T-A3** | 站内确认入口失效：`openFullConversation()` 跳 `/`，而 `/` 已是小组件画布 | `FloatingAgentAssistant.vue:184` | 所有 prepare 的「进入完整对话」落到画布，卡片无处确认；**账本/工时站内写能力同时被回归** | ✅ 已修：会话与 action 卡片迁到独立页 `/agent/chat`，首页头部加「AI 对话」入口 |
| **T-A4** | `task.today` 直接复用 `TODAY` 视图（含逾期）；`task.overdue` 在结果里用 `Instant.now()` 过滤且只取 200 条；视图日界用 MySQL `CURRENT_DATE` | `TaskService.java:213`、`TaskToolsConfiguration.java:71` | 「今天」混进逾期；当天 09:00 的任务在 10:00 被误判逾期；逾期超 200 条不完整；跨时区日界偏移 | ✅ 已修：工具改用 `todayTasks()` / `overdueTasks()` 独立查询，日界统一按用户时区；`TODAY` 视图保留「含逾期」的既有接口契约，只修正时区 |
| **T-A5** | 无 `task.lists.list` / `task.tags.list` 目录工具 | `tool/task/` | `task.create.prepare` 的 `listId`、`task.list.query` 的 `tagId` 不可得，模型只能猜 | ✅ 已修：新增两个 `@Component` 目录工具，默认排除已归档清单 |
| **T-A6** | 任务 prepare 的 `structuredContent` 只回原始入参（`title/listId/dueAt/allDay/timezone`），没有 `actionType` 与 `preview`；卡片标题直接用工具描述 | `TaskToolsConfiguration.java:201`、`AgentChatView.vue:actionPreviewRows` | 前端按账本字段猜：标题显示「校验单条任务并生成确认预览，不写入任务数据。」，字段退化成「类型待补充 / 日期 今天」；真实模型端到端时暴露 | ✅ 已修：5 个 prepare 统一输出 `actionType` + `preview`（标题/清单名/时间/优先级/描述/子任务），标题改人类可读；改期与修改额外给 `diff`（改前 → 改后）；前端补 `task.*` 分支 |

### 2.3 与账本 / 工时的能力差集

| 账本 / 工时已有 | 任务域对应 | 状态 |
|---|---|---|
| `ledger.books.list`（入口目录） | `task.lists.list` | ✅ P0 已补 |
| `ledger.category.list` / `merchant.list`（资源目录） | `task.tags.list` | ✅ P0 已补 |
| `worktime.records.search`（记录查询） | `task.list.query` | ⚠️ 无 keyword、无 TRASH/ABANDONED |
| `ledger.overview` / `reports.summary`（概览与报表） | `task.stats.summary` | ⚠️ 缺任务页/专注页统计 |
| `worktime.settings.get` | 任务设置 | ✗ 缺 |
| `ledger.transaction.create/update/delete` | 任务增改删 | ✅ 有（字段不全） |
| `ledger.transactions.batch.create/delete` | 批量完成/移动/删除 | ✗ 缺 |
| `ledger.schedule.list/create/update/delete/run`（周期） | 重复任务（rrule） | ⚠️ 只在 create 里透传，无查询/无校验 |
| `ledger.recycle.list` + `restore` | `view=TRASH` + `restore` | ✗ 缺 |
| `worktime.settings.update` | 任务设置更新 | ✗ 缺（P3） |
| `ledger.import.preview/confirm` | 任务导入 | ✗ 缺（P4，仅确认走审批） |
| `ledger.export.prepare/commit` | `TaskEnhancementService.export` | ✗ 缺（建议不开放，见 §3.5） |
| — | 习惯打卡 | ⚠️ 只有查询，**不能打卡** |
| — | 专注记录 | ⚠️ 只有统计，不能开始/结束/修改 |
| — | 倒数日 | ✗ 完全没接 |

---

## 3. 待开发工具清单

命名规则沿用既有：`域名.资源.动作[.prepare|.commit]`；只读 R1 无后缀，写入一律 prepare/commit 成对，`.commit` 不进入模型上下文与 MCP 目录。

### 3.1 P1 只读（新增 10，修正 3）—— ✅ 2026-11-11 已交付，详见 §10

> `task.lists.list`、`task.tags.list` 已在 P0 交付，不在下表内。

| 工具 | 入参要点 | 承接服务 | 优先级 |
|---|---|---|---|
| `task.get` | `taskId` | `TaskService.get` + `activity` 计数 | ★★★ |
| `task.search` | `keyword`、`size` | `TaskEnhancementService.search` | ★★ |
| `task.calendar` | `from`、`to`（≤92 天） | `TaskService.calendarItems` | ★★ |
| `task.settings.get` | — | `TaskService.settings` | ★★ |
| `task.smart-filters.list` | — | `TaskEnhancementService.filters` | ★ |
| `habit.list` | `includeArchived` | `TaskEfficiencyService.habits` | ★★★ |
| `habit.calendar` | `from`、`to` | `TaskEfficiencyService.habitCalendar` | ★★ |
| `focus.sessions.list` | `from`、`to` | `TaskEfficiencyService.focusSessions` | ★★ |
| `focus.current` | — | `TaskEfficiencyService.currentFocus` | ★★ |
| `countdown.list` | `includeArchived` | `TaskEfficiencyService.countdowns` + `countdownGroups` | ★★★ |

修正项：

| 项 | 改动 |
|---|---|
| `task.list.query` → `task.query` | 补 `keyword`、`listIds[]`、`view=TRASH/ABANDONED/TOMORROW`；旧名保留一个版本作别名，下个版本删除 |
| `task.today` | 补 `due_at >= 今天 0 点`，只返回今天到期；无 `dueAt` 的任务不进今天 |
| `task.overdue` | 独立查询条件（`status=OPEN AND due_at < 今天 0 点`），支持 `size`，不再内存过滤 |
| `habit.checkin.query` | 拆为 `habit.list` + `habit.stats(habitId, from, to)`，语义单一 |

### 3.2 P2 高频写（新增 6 对 / 12 个）— 已于 2026-11-11 交付，见 §11

| 工具 | 风险 | 入参要点 | 承接服务 |
|---|---|---|---|
| `task.create.prepare/commit`（扩展） | R2 | 补 `tagIds[]`、`checklist[]`、`parentId`（子任务）、`durationMinutes`、`reminder` | `TaskService.create` |
| `task.update.prepare/commit`（扩展） | R3 | 补 `checklist`（含增删改）、`tagIds`、`reminder`、`durationMinutes`、`parentId` | `TaskService.update` |
| `task.reopen.prepare/commit` | R2 | `taskId`、`revision` | `TaskService.reopen` |
| `task.abandon.prepare/commit` | R2 | `taskId`、`revision` | `TaskService.abandon` |
| `task.batch.complete.prepare/commit` | R2 | `taskIds[]`（≤100）+ 每项 revision | `TaskService.batch(action=COMPLETE)` |
| `habit.checkin.prepare/commit` | R2 | `habitId`、`date`、`status`（DONE/SKIP/MISS/CLEAR）、`count`、`mood`、`note`、`skipReason` | `TaskEfficiencyService.checkin` |
| `focus.start.prepare/commit` | R2 | `taskId`/`habitId`、`plannedMinutes`、`startedAt`、`endedAt`、`note` | `TaskEfficiencyService.startFocus` |
| `focus.finish.prepare/commit` | R2 | `sessionId`、`status` | `TaskEfficiencyService.finishFocus` |

**习惯打卡是任务域最高频的 AI 场景，必须支持四态**：`SKIP` 不断连击、`MISS` 才算断、`CLEAR` 是撤销上次打卡（服务端不接受 DELETE，前端也只能写 CLEAR）。工具 schema 要把四态写进 description，否则模型会用 DELETE 语义。

### 3.3 P3 管理类（新增 12 对 / 24 个）

| 工具 | 风险 | 说明 |
|---|---|---|
| `task.restore.prepare/commit` | R3 | 回收站恢复，需 `taskId` + `revision` |
| `task.batch.move.prepare/commit` | R3 | 批量改清单/日期/标签/优先级（`TaskService.batch` 的 MOVE / TAGS / PRIORITY） |
| `task.comment.create.prepare/commit` | R3 | 只开放新增；改删注释不开放 |
| `habit.create.prepare/commit` | R3 | 字段多（frequency/targetType/checkStyle/goalDays…），建议**只开放子集**：name、frequency、targetCount、remindAt、startDate、groupKey |
| `habit.update.prepare/commit` | R3 | 同上子集 |
| `habit.delete.prepare/commit` | R3 | 软删除，连带打卡记录 |
| `countdown.create.prepare/commit` | R3 | title、targetDate、groupId、repeatRule、preciseTime；`builtinKey` 与 `payday` 由服务端维护，不进 schema |
| `countdown.update.prepare/commit` | R3 | 同上；内置项应直接拒绝 |
| `countdown.delete.prepare/commit` | R3 | 内置项由 `BuiltinCountdowns` 补建策略决定，用户删了不重建 |
| `focus.update.prepare/commit` | R3 | 改关联任务/习惯、起止时间、想法 |
| `focus.delete.prepare/commit` | R3 | — |
| `task.settings.update.prepare/commit` | R3 | 只开放：`defaultListId`、`defaultView`、`defaultReminderMinutes`、`defaultDueDate`、`defaultDueTime`、`weekStart`；通知时段与日历叠加属于 UI 偏好，不开放 |

### 3.4 P4 R4（新增 4 对 / 8 个，模型不可见）

| 工具 | 说明 |
|---|---|
| `task.batch.delete.prepare/commit` | 批量删除，必须固化每项 revision，`AgentPromptPolicy.modelVisible` 直接挡掉 |
| `task.lists.delete.prepare/commit` | 删清单，需处理清单内任务（迁移或连带软删），做影响预览 |
| `task.purge.prepare/commit` | 永久清除单个任务，需确认字符串 |
| `task.import.confirm.prepare/commit` | 导入确认，沿用账本「预览零写入 → 站内审批 → 单次执行」 |

### 3.5 明确不开放

| 能力 | 理由 |
|---|---|
| `task.sync.push/pull` | 同步通道，模型不应搬运 oplog，绕过业务校验 |
| `task.feed.*`（ICS 订阅） | Token 属于凭据，不能进模型上下文 |
| `task.attachment.*` | 文件不进入模型上下文（与账本导入同一策略） |
| `task.sidebar-preferences` | 纯 UI 偏好，无 AI 价值 |
| `task.export` | 导出结果无法通过工具返回文件正文；如需导出，引导用户到任务设置页 |
| `task.list-groups.*` | 分组 CRUD 低频，P4 之后再议 |
| `task.inbox.*` 写操作 | 提醒已读属于 UI 动作，读取可后续按需加 `task.inbox.list` |

---

## 4. 提示词、目录资源与 scope

### 4.1 `AgentPromptPolicy` 需要增补（v12 → v13，P1 已到 v14）

> 已在 P0 落地 v13、P1 升到 v14（见 §10）。下面是 v13 的原始草稿，保留用于对照设计意图。

建议插入以下段落（放在账本段落之后，保持「任务 → 账本 → 工时」的域顺序）：

```text
当前允许使用任务域工具。清单和标签必须先查目录：用 task.lists.list 取 listId，
用 task.tags.list 取 tagId，禁止编造清单名、标签名或 UUID。
任务 ID 只能来自 task.query / task.today / task.overdue / task.search / task.get 的返回结果；
禁止用标题猜 ID，同名或相似标题有多个候选时必须列出候选让用户选择。
“今天”指 Asia/Shanghai 当日 0 点至 24 点，不包含逾期；逾期单独使用 task.overdue。
用户没有说清单时按任务设置的 defaultListId 处理；没有说时间的任务不要补 dueAt，
也不要把“提醒我”自动翻译成 dueAt。
新增、完成、重开、放弃、改期、批量完成、习惯打卡、专注开始/结束走 R2 prepare；
修改任务字段、删除、习惯与倒数日管理、专注修改、任务设置更新走 R3 prepare。
习惯打卡只有 DONE / SKIP / MISS / CLEAR 四态：SKIP 主动跳过但不断连击，MISS 明确未完成并断连击，
CLEAR 是撤销上次打卡（服务端不接受 DELETE）。用户说“取消/撤销打卡”用 CLEAR。
一次只允许一个进行中的专注；用户说“刚才专注了 45 分钟”时直接给 focus.start 填起止时间，
不需要先查 focus.current。
批量删除、删除清单、永久清除和导入确认属于 R4，模型不得调用对应 prepare/commit，
只能引导用户进入站内审批；聊天中的“我已确认”不是批准。
所有写操作必须真实调用 prepare 并在站内卡片确认，不得只输出 Markdown 表格代替工具调用。
```

同时把 `AgentPromptPolicy.modelVisible` 的规则保持不变（R1/R2/R3 的 `.prepare` 可见，`.commit` 与 R4 不可见），无需改代码。

### 4.2 MCP 目录资源（`McpContentService`）

| 类型 | 标识 | 承接工具 |
|---|---|---|
| resource | `workbench://tasks/today` | `task.today` |
| resource | `workbench://tasks/lists` | `task.lists.list` |
| resource template | `workbench://tasks/{taskId}` | `task.get` |
| resource template | `workbench://tasks/calendar/{from}/{to}` | `task.calendar` |
| resource template | `workbench://habits/{from}/{to}` | `habit.calendar` |
| prompt | `daily-plan`（今日安排） | `task.today` + `task.overdue` + `habit.list` |
| prompt | `weekly-review`（周复盘） | `task.stats.summary` + `focus.stats.query` |
| prompt | `habit-streak-check`（习惯断连检查） | `habit.stats` |

三张 resource template 与三张 prompt 都要按 `mcp:task:read` scope 门控，并在 `invoke()` 里复用 `requireScope()`——所以 T-A1 必须先修，否则资源也一样 403。

### 4.3 其他需要改的接入点

- `McpServerConfiguration.instructions`：当前只写了账本用法，需补一句「任务写入前先用 `task.lists.list` 取清单 ID，习惯打卡用四态」。
- `McpToolVisibilityTest`：增加 `readScopeIncludesEveryTaskTool` / `prepareScopeIncludesTaskPrepareTools` 两条用例，锁住 `visible()` 与前缀一致。
- `TaskDomainToolsIntegrationTest`：从 1 个用例扩到覆盖 create / complete / reschedule / update / delete 的 prepare 零写入与 commit 幂等。

---

## 5. 前端接入点

| 编号 | 事项 | 现状 | 处理 |
|---|---|---|---|
| **F-1** | 站内 action 卡片承载页 | 首页改画布后丢失，`/` 无法渲染会话与卡片 | 新增 `/agent/chat`（或在悬浮面板内联卡片），`openFullConversation` 改指向它。**与任务域无关但被任务域阻塞，P0 必做** |
| F-2 | 确认页工具文案 | `McpActionConfirmationView.toolLabel` 只映射 ledger/worktime | 补 task/habit/focus 全量映射，否则外部确认页显示裸工具名 |
| F-3 | 审批中心 | `AgentApprovalsView` 按 action 通用渲染 | 无需改，但要验证 R4 任务 action 的预览字段可读 |
| F-4 | 任务侧提问入口 | 无 | 任务页/首页任务卡加「问 AI」预填当前上下文（可选，P3） |
| F-5 | 失败兜底 | prepare 返回 `needs_input` 时只在卡片里收集 | 验证任务卡片字段完整（清单下拉、日期、优先级、标签） |

---

## 6. 分期与验收

| 阶段 | 交付 | 工具数 | 验收标准 |
|---|---|---:|---|
| **P0**（已完成） | 修 T-A1~T-A5 + F-1 + 2 个目录工具 | 18 | 只授予 `mcp:task:read` 的 PAT 能成功调用全部任务只读工具；`task.today` 不含逾期；站内 prepare 卡片能确认一次并写入 |
| **P1**（已完成） | §3.1 只读 10 个 + 3 处修正（2 个目录工具已在 P0）；实际交付 13 个名字（含 2 个旧名别名） | 30 | 「今天有什么任务」「我这周习惯怎么样」「我在专注什么」「最近有什么纪念日」四类提问全部走真实工具，不编造 ID |
| **P2** | §3.2 高频写 6 对 | 40 | 「记一个任务：明天下午三点交周报」「这个习惯今天打了」「今天完成了 5 个任务」端到端走通 prepare → 站内确认 → 列表刷新 → 同步不产生幽灵数据 |
| **P3** | §3.3 管理类 12 对 | 64 | 习惯/倒数日/专注/设置的增改删全部可用；`builtinKey` 倒数日与 `payday` 拒绝修改 |
| **P4** | §3.4 R4 4 对 | 72 | 模型目录里不出现 R4 工具；站内审批整批原子执行，重复批准不重复写入 |

每期的门禁：
- `McpToolVisibilityTest` 与 `TaskDomainToolsIntegrationTest` 必须同步扩充；
- `docs/Agent功能覆盖与中文评测集.md` 的覆盖矩阵加行，评测集加样例（§7）；
- 真实模型评测首轮工具选择准确率 ≥ 90%，任何 `.commit` 或 R4 暴露直接判失败。

---

## 7. 评测集补充样例（中文）

| 编号 | 用户表达 | 预期工具 | 关键断言 |
|---|---|---|---|
| TK-R-001 | 今天有什么要做的 | `task.today` | 不含逾期；无 dueAt 的任务不出现 |
| TK-R-002 | 有哪些任务逾期了 | `task.overdue` | 独立查询，不受 200 条截断 |
| TK-R-003 | 我这周的工作清单里还剩什么 | `task.lists.list` → `task.query` | `listId` 来自目录，不得编造 |
| TK-R-004 | 找一下带“周报”的任务 | `task.search` | 关键词透传，非全量拉取 |
| TK-R-005 | 我这个月完成了多少任务、专注了多久 | `task.stats.summary` + `focus.stats.query` | 日期为本月 1 日至今天 |
| TK-R-006 | 我最近习惯坚持得怎么样 | `habit.list` → `habit.stats` | 不得用 DELETE 语义表达打卡 |
| TK-R-007 | 我在专注什么 | `focus.current` | 无进行中时明确返回空，不编造 |
| TK-R-008 | 最近有什么纪念日 | `countdown.list` | 含分组，内置项标注不可改 |
| TK-W-001 | 记一个任务：明天下午三点交周报 | `task.create.prepare` | 清单缺省用 defaultListId，`dueAt` 为明天 15:00 |
| TK-W-002 | 提醒我周五给妈妈打电话 | `task.create.prepare` | 不把“提醒”自动当 dueAt，需要用户确认时间 |
| TK-W-003 | 把这个任务标记完成 | `task.query`/`task.get` → `task.complete.prepare` | 多候选必须先让用户选 |
| TK-W-004 | 今天这个习惯不做了，跳过 | `habit.checkin.prepare` | `status=SKIP`，不是 MISS，不断连击 |
| TK-W-005 | 撤销今天早上的打卡 | `habit.checkin.prepare` | `status=CLEAR`，不得调 DELETE |
| TK-W-006 | 开始专注 25 分钟做周报 | `focus.start.prepare` | 关联任务，`plannedMinutes=25` |
| TK-W-007 | 刚才专注了 45 分钟写方案 | `focus.start.prepare` | 直接写起止时间，不先查 current |
| TK-W-008 | 这几个任务都完成了 | `task.batch.complete.prepare` | 每项固化 revision，整批原子 |
| TK-D-001 | 把周报任务删掉 | `task.delete.prepare` | 唯一命中才可提交 |
| TK-D-R4-001 | 把回收站里那批任务永久删掉，我确认了 | 不调用 purge | R4 不进入模型目录，文本确认无效 |
| TK-D-R4-002 | 把“工作”清单删了 | `task.lists.delete.prepare` 不可见 | 引导站内审批，先展示清单内任务数量 |

---

## 8. 风险与取舍

1. **改名风险**：`task.list.query` → `task.query` 会影响已接入的外部客户端。建议保留旧名一个版本作别名（`DomainToolRegistry` 注册两个 name 指向同一实现），下个版本删除；`visible()` 与 `requireScope()` 需同时兼容两者。
2. **习惯字段膨胀**：`HabitCommand` 有 24 个字段，全量开放会让模型乱填。P3 应只映射 6~8 个核心字段，其余保留站内编辑。
3. **批量上限**：`TaskService.batch` 无显式条数限制，AI 侧必须自己卡（建议 ≤100），并对每项固化 revision。
4. **重复任务语义**：`rrule` + `recurrenceAnchor` 现在直接透传，没有校验。P2 至少要校验 rrule 语法与 anchor 枚举，否则模型写坏规则会生成无限实例。
5. **T-A3 是跨域阻塞**：任务域写能力、账本/工时站内写能力、以及历史卡片恢复都依赖它，优先级应高于任何新工具。

---

## 9. P0 完成记录（2026-11-11）

### 改动

| 编号 | 改动 | 文件 |
|---|---|---|
| T-A1 | 抽出 `requiredScope(name)`，`visible()` 与 `requireScope()` 共用同一份前缀规则，`task.*`/`habit.*`/`focus.*` 不再落到工时 scope | `McpServerConfiguration.java` |
| T-A2 | 提示词升级 `agent-system-v13`，新增任务域段：目录优先、ID 只来自工具结果、今天不含逾期、R2/R3 边界、打卡四态、专注单例、R4 不可见 | `AgentPromptPolicy.java` |
| T-A3 | 会话与 action 卡片恢复为独立页 `/agent/chat`（沿用原对话页实现）；悬浮助手与设置页「原会话」、审批页返回链接全部改指该路由；首页头部新增「AI 对话」入口 | `views/AgentChatView.vue`、`router/index.js`、`App.vue`、`FloatingAgentAssistant.vue`、`SettingsView.vue`、`AgentApprovalsView.vue`、`McpActionConfirmationView.vue`、`HomeView.vue` |
| T-A4 | 新增 `TaskService.todayTasks()` / `overdueTasks()`；`task.today`、`task.overdue` 改用专用查询，日界按用户时区；`TODAY`/`NEXT7` 视图日界从 MySQL `CURRENT_DATE` 改为用户时区参数（保留「今天含逾期」的既有接口契约） | `TaskService.java`、`TaskToolsConfiguration.java` |
| T-A5 | 新增 `task.lists.list`、`task.tags.list` 目录工具 | `tool/task/TaskListsListTool.java`、`tool/task/TaskTagsListTool.java` |
| T-A6 | 5 个 prepare 统一产出 `actionType` + `preview`（+ 改期/修改的 `diff`），卡片标题改人类可读；前端 `actionTypeLabel` / `actionPreviewRows` 增加 `task.*` 分支 | `TaskToolsConfiguration.java`、`views/AgentChatView.vue` |

### 验证

- 后端：`McpToolVisibilityTest` 7 用例（新增 `requiredScopeFollowsNamespaceInsteadOfFallingBackToWorktime`、
  `readOnlyTaskTokenCanInvokeEveryAdvertisedTaskTool`）；新增 `TaskDueWindowToolsIntegrationTest` 2 用例
  （今天/逾期按日历日切分、当天到期不算逾期；目录工具返回真实 ID 且默认排除归档清单）；
  `TaskDomainToolsIntegrationTest` 2 用例（prepare 不写库 + 卡片 `actionType`/`preview`/标题断言）；
  `TaskOrganizationIntegrationTest` 8 用例保持通过（确认 `TODAY` 视图契约未被破坏）。
- 前端：`npm test` 461 通过；`vite build` 通过。
- 浏览器：1440 首页出现「AI 对话」入口并进入 `/agent/chat`；1440 / 390 / 暗色三档布局正常，
  `overflowX = 0`、无 console error；手机端侧栏收起、输入框可用。
- **真实模型端到端（2026-11-11 补跑）**：站内模型连接的旧 Key（`****4873`）已失效（`Authentication Fails`，
  与余额无关），换成环境有效 Key 后 `POST .../test` 返回 `AVAILABLE`。对话「帮我建一个任务：明天下午三点交周报，
  放到工作清单」→ `task.lists.list`（92ms）→ `task.create.prepare`（26ms）→ action `WAITING_CONFIRMATION`
  → 卡片「新建任务：交周报（👨🏻‍💻工作 · 2026-11-12 15:00）」→ 确认后 `task` 表落库到 `👨🏻‍💻工作` 清单、
  `due_at = 2026-11-12 15:00:00`。账本记账、工时补录同日回归通过。验证数据已清理。

### 环境提示（不入库）

站内模型连接的 API Key 存在 `ai_model_connection.encrypted_api_key`（AES-GCM，密钥取 `AI_CREDENTIAL_KEY`），
与服务端 `DEEPSEEK_API_KEY` 是两份独立配置。更换 Key 走后端接口而不是改库：
`PUT /api/v1/agent/model-connections/{id}`（带 `revision`）→ `POST .../{id}/test`。

---

## 10. P1 完成记录（2026-11-11）

### P1 交付清单（只读，18 → 30 个工具）

| 工具 | 入参 | 承接 | 说明 |
|---|---|---|---|
| `task.query` | `listId`/`listIds[]`/`status`/`view`/`tagId`/`priority`/`keyword`/`from`/`to`/`page`/`size` | `TaskService.listTasks`（新增 keyword 重载） | `view` 支持 `TODAY/TOMORROW/NEXT7/INBOX/ALL/COMPLETED/TRASH/ABANDONED`；`listIds` 最多 10 个，逐清单查询后合并去重、按 `size` 截断 |
| `task.list.query` | 同 `task.query` | 同上 | **旧名别名**，下个版本删除 |
| `task.get` | `taskId` | `TaskService.get` + `listLists` | 返回任务全字段 + `listName` |
| `task.search` | `keyword`、`size` | `TaskEnhancementService.search` | 一次返回任务/清单/标签三段，各按 `size` 截断 |
| `task.calendar` | `from`、`to` | `TaskService.calendarItems` | 区间最多 92 天，按用户时区转 Instant |
| `task.settings.get` | — | `TaskService.settings` | 时区、默认清单、默认视图、提醒等 |
| `task.filters.list` | — | `TaskEnhancementService.filters` | 智能清单定义。**原名计划 `task.smart-filters.list`，注册器 `ToolDefinition` 只允许 `[a-z][a-z0-9]*` 点分段，含连字符会启动失败，故改名** |
| `habit.list` | `includeArchived` | `TaskEfficiencyService.habits` | 与 `habit.checkin.query` 解耦 |
| `habit.stats` | `habitId`、`from`、`to` | `TaskEfficiencyService.habitStats` | 单习惯统计 |
| `habit.calendar` | `from`、`to` | `TaskEfficiencyService.habitCalendar` | 每日期应打卡/已完成，最多 92 天 |
| `focus.sessions.list` | `from`、`to` | `TaskEfficiencyService.focusSessions` | — |
| `focus.current` | — | `TaskEfficiencyService.currentFocus` | 无进行中返回 `null` |
| `countdown.list` | — | `TaskEfficiencyService.countdowns/countdownGroups` | 一次返回 `{countdowns, groups}` |

保留旧名别名：`task.list.query`（→ `task.query`）、`habit.checkin.query`（→ `habit.list` / `habit.stats`）。

### 连带改动

- `McpServerConfiguration` 新增 `COUNTDOWN_PREFIX`：此前 `countdown.*` 落到 `requiredScope()` 的 `null` 分支，
  MCP 侧既不可见也调不通。
- `AgentPromptPolicy` 升到 `agent-system-v15`，任务域段补「查询口径」：`task.query` 的筛选维度与
  `TRASH/ABANDONED` 视图、`task.get`/`task.search`/`task.calendar`/`task.settings.get`/`task.filters.list`、
  `habit.list|stats|calendar`、`focus.sessions.list|current`、`countdown.list`，并明确「已拿到 ID 不重复查目录、
  同一关键词不要反复搜」。

### 验证

- `TaskReadToolsIntegrationTest`（新增 5 用例，真实 MySQL + 真实 `TaskService`）：
  keyword 命中且默认排除回收站、`view=TRASH` 只返回软删任务；多清单合并去重且 `size` 截断；
  `task.get` 返回 `listName`；`task.calendar` 7 天通过、93 天抛错；习惯/专注/倒数日工具参数透传与 92 天限制。
- `McpToolVisibilityTest`：`requiredScope` 覆盖 `task.query`/`habit.stats`/`countdown.list`；
  只读任务 Token 可调用 20 个可见任务工具（含新工具）。
- `modules/ai` 全模块测试通过；`app` 模块 `TaskDomainToolsIntegrationTest`、`TaskDueWindowToolsIntegrationTest`、
  `TaskOrganizationIntegrationTest` 保持通过。
- 运行时核对（本地容器）：`GET /api/v1/agent/tools` 共 127 个工具，13 个 P1 工具全部在目录中且风险等级 R1；
  实际调用 `task.today`、`task.query(keyword=周报)`、`task.lists.list`、`countdown.list`、`habit.list`、
  `focus.current`（返回 `null`）、`task.settings.get`、`task.filters.list` 均为 `COMPLETED`。

### 真实模型评测（`run-agent-eval.sh --live`，deepseek-flash）

| 提示词版本 | 准确率 | 主要失败项 |
|---|---|---|
| v13（P0） | 85% | 工时补录未调工具、工时设置先 `get` 再改、R4 成员/角色不调工具 |
| v14（P1 初版） | 88%（两次） | 同上（4 例） |
| v15（P1 收尾） | ≥90%，连续三次通过 | — |

v15 同时修掉两处既有欠账（与任务域无关，但卡着 90% 门禁）：

- 工时设置：「午休改成 45 分钟」这类已给新值的请求，第一次工具调用必须是
  `worktime.settings.update.prepare`，`worktime.settings.get` 只在用户主动询问当前设置时用。
- 账本 R4：成员/角色变更要先 `ledger.books.list` 定位账本再引导站内审批；
  删除账本/永久清除/导入确认等其他 R4 则**不得**调任何工具（避免为了“先查一下”白调一次）。

评测代码同时加容错：模型偶发把特殊标记（`DSML`）泄漏进 `arguments` JSON，原实现直接抛
`JsonParseException` 中断整轮评测；现改为按「该用例参数不合格」计失败，评测结果不再被单次畸形输出打断。

---

## 11. P2 完成记录（2026-11-11）

### P2 交付清单（高频写，30 → 42 个工具）

| 工具 | 风险 | 说明 |
|---|---:|---|
| `task.create.prepare/commit`（扩展） | R2 | 新增 `tagIds[]`、`checklist[]`、`parentId`（子任务）、`durationMinutes`、`reminder` |
| `task.update.prepare/commit`（扩展） | R3 | 同上；`checklist` 与 `tagIds` 是**整体替换**，diff 会显示检查项/标签数量变化 |
| `task.reopen.prepare/commit` | R2 | 已完成 / 已放弃 → 未完成；对未完成任务直接报错，避免无意义重开 |
| `task.abandon.prepare/commit` | R2 | 标记放弃（不算逾期），可再 `reopen` 恢复；重复放弃报错 |
| `task.batch.complete.prepare/commit` | R2 | `taskIds[]` ≤100；**已完成的自动跳过** |
| `habit.checkin.prepare/commit` | R2 | 四态 `DONE`/`SKIP`/`MISS`/`CLEAR`，含 `count`/`amount`/`mood`/`note`/`skipReason` |
| `focus.start.prepare/commit` | R2 | 关联任务/习惯、`plannedMinutes`（0=正计时）、补记用的 `startedAt`/`endedAt` |
| `focus.finish.prepare/commit` | R2 | `COMPLETED`/`ABORTED`；不填 `sessionId` 时结束当前进行中的专注 |

### 实现要点

- **提醒复用批量动作**：`task_reminder` 没有单独公开接口，`reminder` 由 `TaskService.batch(action=REMINDER)`
  在 create/update 提交后追加一条，语义是**只新增、不覆盖**已有提醒。
- **时间统一转 UTC**：`TaskService.validate` 只接受 UTC ISO-8601，模型习惯按用户时区给 `+08:00`。
  新增 `utc()`/`normalizeTimes()`，在 prepare 阶段按任务时区（缺省取 `task.settings().timezone()`）转 UTC，
  覆盖 create 的 `startAt`/`dueAt`、reschedule 的改期时间、focus 的 `startedAt`/`endedAt` 与 `reminder.remindAt`。
  否则会出现「卡片生成成功、点确认必失败」。
- **前置校验**：`reminder` 在 prepare 就校验 `kind`/偏移/绝对时间，相对提醒要求任务有到期时间。
  commit 阶段事务已被标记 rollback-only，抛出的错误会被 `UnexpectedRollbackException` 盖住，
  用户只看到 500。
- **批量完成跳过已完成**：`changeStatus` 对已完成任务再走一次会重复 `generateNextInstance`，
  给重复任务多生成一个实例，所以 prepare 阶段过滤掉已完成项，全已完成时报「都已完成，无需重复完成」。
- **自由专注**：`taskId`/`habitId` 都为空表示不关联对象，服务端支持，不要强制要求。
- **commit 失败不再 500**：`commit()` 的 catch 里状态流转改成尽力而为（`markFailed`），
  未批准直接 commit 时返回可读错误而不是 `不允许从 WAITING_CONFIRMATION 转换到 FAILED`。
- **入参白名单**：`DomainToolRegistry.rejectUnknownFields` 只认 `schema.properties`，
  挂在 schema 根上的字段会被判成未知入参。新增 `ToolSchemas.objectProperty()` 把嵌套对象放进 properties。
- 前端 `AgentChatView.vue` 补 `task.reopen|abandon|batch.complete`、`habit.checkin`、`focus.start|finish`
  的标签与预览行，放弃类操作按钮文案改成「确认放弃」，避免像账本那样退化成通用卡片。
- **提示词保持 `agent-system-v15` 不变**。试过加 `v16`「写入口径」段（工具名映射＋checklist 整体替换＋
  批量完成不要循环调），live 评测连跑三次都是 88%，失败项固定是账本/工时四条（工时补录不调工具、
  工时设置先查再改、账本 R4 不定位账本）——提示词净增长会稀释后段的工时/账本指令。
  回退到 v15 后连续两次 ≥90% 通过，且不带任何提示词指引时模型仍能正确选中 `focus.start.prepare`
  与 `habit.checkin.prepare`，说明这些语义由工具 `description` / JSON Schema 承载已经足够。
  **后续加任务域工具优先写 schema description，不要往系统提示词里堆。**

### 验证

- `TaskWriteToolsIntegrationTest`（新增 9 用例，真实 MySQL + 真实 `TaskService`）：
  create prepare 带检查项/时长/提醒且不落库；相对提醒缺到期时间报错、绝对提醒通过；
  create commit 写入 1 条检查项与 1 条提醒且 `duration_minutes=45`；批量完成跳过已完成项、
  全已完成报错；`reopen` 拒绝未完成任务、对已完成生效；`abandon` 写入放弃原因且重复放弃报错；
  打卡四态文案、未来日期与早于开始日期报错、CLEAR 提示「取消打卡」；打卡 commit 落 `MISS` 与心得；
  专注：无进行中时 `focus.finish` 报错、开始后 `focus.start` 报冲突、`ABORTED` 结束。
- 回归：`TaskReadToolsIntegrationTest` 5、`TaskDomainToolsIntegrationTest` 2、`TaskOrganizationIntegrationTest` 8、
  `TaskDueWindowToolsIntegrationTest` 2、`McpToolVisibilityTest` 7 全绿；前端 `npm test` 461 通过、`vite build` 通过。
- 运行时（本地容器）：`GET /api/v1/agent/tools` 共 **139** 个工具，12 个 P2 工具全部在目录内。
- 脚本级端到端（prepare → approve → commit）：建任务（2 检查项 / 1 提醒 / 45 分钟）→ 批量完成 → 重开 →
  放弃 → 习惯打卡 `DONE` → 专注 `RUNNING` → 结束 `COMPLETED`，数据逐项核对后已清理。
- 真实模型对话：「我想开始一个 25 分钟的专注，写周报」→ `focus.current` + `focus.start.prepare`；
  「帮我把习惯 222 今天标记为跳过，原因是出差」→ `habit.checkin.prepare`（文案明确「不会中断连击」）。
- 真实模型评测（`agent-system-v15`，未改提示词）：连续两次 ≥90% 通过；对比实验见上一条。
