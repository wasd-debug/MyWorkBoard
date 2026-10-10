# 个人工作台项目总览

> 状态日期：2026-09-30
> 当前主线：Phase 3B/3D Web Agent 核心闭环已完成；Phase 3C-5 已交付 MCP OAuth、协议错误分类、连接诊断、客户端级撤销、协议事件、DCR 限流和失效凭据清理，以及此前的 R2 单次提交和账本同步 oplog。R3/R4 仍禁止 MCP commit；下一步是用户手工完成 MCP Inspector、Codex 和 WorkBuddy 真实客户端兼容验收。公共/模块设置拆分、用户资料、Redis/RabbitMQ、站内信与 NAS 音乐已形成独立 Future F1-F5 路线图，不与当前 Agent/MCP 收口混为一次交付

## 项目定位

本项目从“加班时长与真实时薪计算”工具演进为个人效率工作台，长期由七类能力组成：

1. **工时**：打卡、工资、加班、真实时薪与节假日。
2. **账本**：账本、账户、流水、分类、预算、成员与权限、报表、导入导出、周期流水和 AI 记账。
3. **任务**：清单、任务、日历、提醒、番茄钟、习惯和倒数日。
4. **AI 与知识库**：附件、NAS、RAG、Agent 工具调用。
5. **洞察**：工时、账本和任务的跨域日报、周报、月报及年报。
6. **身份、设置与通知**：个人资料、社交绑定、公共/模块设置、站内信、邀请、审批与任务结果。
7. **音乐**：个人 NAS 曲库扫描、在线 Range 播放、播放列表、收藏和播放历史。

目标架构保持为 Java 17 + Spring Boot 模块化单体、MySQL/Flyway、Vue 3/Vite/Pinia、Tailwind CSS + 源码组件、IndexedDB/oplog local-first。完整目标与阶段门禁见 [ARCHITECTURE.md](ARCHITECTURE.md)，Agent、MCP、受控确认和逐阶段验证见 [工作台的 Agent 改造计划](工作台的Agent改造计划.md)。

2026-09-30 暗色对比度修正：主题层新增与强调色配套的前景色契约，主按钮及其悬停态、Agent 操作卡片、审批按钮、报表日期确认和随身 AI 操作统一使用成对的背景/前景色；夜间主题不再在浅色强调背景上保留白字。首页 Agent 模块卡片与账本摘要的柔和色块改用独立文字色，避免继承暗色页面的浅色文字。Node 51/51、类型检查、OpenAPI 一致性和生产构建通过，提交 `12299cf` 已推送并部署，V28/V29 迁移成功；浏览器视觉结果按前端手工检查清单验收。

## 当前实现

### Phase 0：工程与自动化发布门禁已完成

已实现：

- Flyway V1-V3 接管基础表结构与旧数据回填，`schema.sql` 不再参与启动初始化。
- Spring Security + JWT access token + HttpOnly refresh cookie；注册、登录、刷新、退出和修改密码。
- `app_user`、角色/权限、审计日志、统一异常响应和 trace id。
- `/api/v1/worktime` 设置与记录 CRUD；旧 snapshot、`/api/data` 和非 v1 业务入口已删除。
- Maven 父工程及 `platform`/`identity`/`worktime`/`ledger`/`task`/`ai`/`app` 物理模块，以及 ArchUnit 依赖和源码归属测试。
- Tailwind CSS、语义 token、响应式应用壳、基础 UI 组件和按用户隔离的本地缓存。

本轮新增收口：

- 工时 store 与页面已切换 settings/records CRUD；服务端持久化计算结果，前端计算只用于未保存预览和页面聚合。
- 工时服务按法定节假日、调休补班和自然周末统一判定日期类型：工作日加班为净工时减标准工时，休息日加班为扣除午休与自定义休息后的全部净工时。Flyway V14 将历史记录统一回算为 `phase0-v2-day-type`，避免旧算法和重新保存后的新算法混用。
- 2026-09-24 午休重算增量：每条工时记录保存午休分钟快照；前端通过 `PUT /api/v1/worktime/settings/lunch` 修改全局午休，并可选择仅影响新记录、重算全部历史，或从指定日期开始重算。重算在同一事务内刷新午休快照、加班分钟、实际时薪、工资快照和计算版本，未选中的历史记录继续使用原午休口径。记录日历与打卡详情展示“结束时间 - 开始时间 - 午休 - 自定义休息 = 净工时”以及税前/税后月薪折算时薪过程。
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

- **Phase 2 任务管理：P2-I4 已完成。** V32/V33 增加组织与删除来源，V34 增加重复实例、提醒投递、投递去重和任务站内信；任务工作区提供重复规则、相对/绝对站内提醒，顶栏未读角标与提醒中心支持 SSE，失败时降级轮询。完成/删除会取消未投递提醒，完成重复任务保留历史并生成下一实例。下一步为 P2-I5 日历与跨域只读叠加；页面行为待用户按手工清单验收。
- **Phase 3A-D Agent/MCP：Phase 3A/3B Web 核心闭环完成，Phase 3C-5 已完成服务端收口。** 会话、队列、SSE、模型连接、Usage 与 Trace 已落地；首页支持 DeepSeek 深度思考开关及默认收起的思考块。MCP 通过 `/mcp` 暴露按 scope 裁剪的 read/prepare/action 工具，并在独立 commit scope 下允许 R2 新增工时和新增流水；PAT 与 OAuth 2.1 + PKCE 均可认证，设置页具备诊断、客户端断开和协议事件，R3/R4 commit 继续关闭。
- **Phase 4 文件/RAG：未启动。** 尚无 MinIO/NAS 文件域、Tika、Qdrant 和知识库。
- **Phase 5 跨域洞察：未启动。** 只有 `domain_event` 预留表，无事件发布/消费、`report_fact`、`report_snapshot` 或洞察页面。
- **Phase 6 持续打磨：部分能力提前实现。** 已有响应式布局、主题、共享账本和可重复恢复演练；PWA、全局搜索和完整可观测体系尚未实现。
- **Future F1-F5：已完成规划，未启动编码。** 依次拆分公共/模块设置和用户资料，建设 Redis/RabbitMQ/outbox/任务运行平台，上线站内信与账本邀请，再基于 NAS/file 能力建设独立音乐模块。详见 [后续特性路线图](后续特性路线图.md)。

## 当前验证基线

2026-09-30 MCP 已撤销凭据清理：PAT 管理增加已撤销凭据的永久删除入口和 `DELETE /api/v1/mcp/tokens/{id}/purge`。服务端仅允许当前用户删除自己的已撤销 PAT，不接受有效 PAT、其他用户凭据或 OAuth token；V29 通过可空外键与 `ON DELETE SET NULL` 保留工具调用和协议事件审计。真实 MySQL 定向集成测试覆盖权限边界、状态限制和审计保留；页面确认、分页回退和响应式布局按手工清单待验收。

2026-09-30 随身 AI 与输入框增长：聊天首页和跨页面 AI 面板的文本输入框按内容自动增长，达到各自上限后在文本区内部滚动；已登录在线用户在除聊天首页外的常规页面可打开悬浮 AI 面板，复用服务端 Agent 会话与 SSE，工具调用状态可见，需要补充或确认的操作可跳转完整对话继续。会话标识按用户隔离保存在浏览器本地。新增自动增长边界单测；桌面/移动与主题交互按手工清单待验收。

2026-09-30 MCP 工具覆盖修正：MCP `tools/list` 不再维护账本与工时工具名称白名单，而是按 `ledger.*`、`worktime.*` 命名空间和 Token read/prepare scope 自动推导可见性。已注册的账户、分类、商家、项目、预算、周期任务、回收站、导入导出和工时管理工具因此可随 Domain Tool Registry 自动接入；全部领域 `*.commit` 仍禁止直接暴露，外部写入继续统一走网站审批后的 `agent.action.commit`。定向单测覆盖只读、prepare、管理工具和 commit 隔离。

2026-09-30 前端工作台体验增量：账本流水收紧分类、商家、账户、项目和金额默认列宽，分类以“一级在上、二级在下”展示；账本报表的收入分类与支出分类专页补齐一级/二级聚合切换；首页时间摘要改为内容高度，自定义首页改用拖拽排序。顶部移除帮助中心和配色入口，只保留当前主题色下的亮暗切换，配色与退出登录集中到全局设置；标准工时、午休重算、排班和工时数据管理迁入工时模块自己的“设置”入口。打卡改为明确保存/删除，周与双周记录切换自动回到本周，Agent 输入框采用稳定的上下两行布局并压缩移动端工具栏。生产构建和非浏览器测试作为自动门禁，视觉、响应式与拖拽体验按前端手工检查清单验收。

2026-09-28 设置页布局修正：对用户反馈的 Agent 运行质量与外部 Agent/MCP 区块重新梳理信息密度、表单分组、状态颜色和长文本换行；预算与 Token 的业务接口不变。前端 Node 49/49、TypeScript、OpenAPI 一致性及生产构建通过，本地 Vite/Nginx 已提供新版本；桌面/移动、亮色/暗色交互继续由用户按前端手工检查清单验收。

2026-09-28 Agent 运行质量增量：设置页新增运行质量面板，支持今日、本周、本月、近 7 天、近 30 天与自定义范围，以及自动、小时、天、周粒度；后端按当前用户汇总 turn 成功/失败、首字时延、P95、Token、模型费用、工具调用、MCP 调用和最近失败。V27 增加用户预算与告警结构，费用严格按预算币种汇总，不同币种不直接相加；真实 MySQL 定向集成测试覆盖时间预设、自动粒度、用户隔离、多币种、预算进度和 revision 冲突。

2026-09-28 成本告警与调用追踪增量：复用 V27 表，新增日/月 50%、80%、100% 站内告警及开关、已读状态；相同用户/周期/币种/阈值只生成一次。设置页增加调用分页、失败/取消筛选、每轮 Token/费用与工具耗时、原会话入口；接口不返回模型正文、工具参数/结果和原始异常。告警失败与正常用量持久化隔离，不做预算硬限制、自动重试、熔断或 RAG。前端视觉与交互仍由用户按手工检查清单验收。

2026-09-28 MCP OAuth 增量：V25 新增 OAuth 客户端、用户授权和一次性授权码表，并为 MCP token 增加 PAT/OAuth 类型、grant 绑定和 refresh hash。服务端实现 Protected Resource Metadata、Authorization Server Metadata、DCR、Authorization Code + PKCE S256、RFC 8707 resource 精确绑定、1 小时 access token、30 天 refresh token 轮换和 grant 撤销；设置页增加已授权应用列表，新增独立授权页面。纯 HTTP 联调通过 DCR、authorize 302、站内授权、token 交换、MCP initialize/tools/list、scope 裁剪、旧 access/refresh 失效和撤销即时失效。后端全量 203 项中 201 项通过、2 项按既有规则跳过；前端 Node 49/49、sync-engine 8/8、TypeScript、OpenAPI 一致性和生产构建通过。真实 MCP Inspector、Codex、WorkBuddy 与页面视觉操作按项目规则留给用户手工验收。

2026-09-28 MCP 运维收口增量：V26 新增客户端诊断字段和 `mcp_protocol_event`；服务端增加可信 redirect 标准错误回调、DCR 限流、token/revoke 禁止缓存、协议版本 400/认证 401/限流 429 分类、客户端级断开和每日数据清理。设置页新增状态摘要、公开地址告警、已连接客户端、最近事件和脱敏诊断复制。自动化覆盖用户隔离、级联撤销、过期清理和协议错误；真实客户端版本与 UI 连接结果继续由用户填写前端手工检查清单。

2026-09-28 MCP 低风险 commit 增量：V24 为外部 action 增加 commit 状态、结果和时间；PAT 新增账本/工时 commit scope，且必须与同领域 prepare scope 同时授予。`agent.action.commit` 只接受原 PAT 创建、已在网站批准且风险为 R2 的新增工时或新增流水 action；提交前重新校验用户、PAT、scope、账本范围、权限和 action 状态，重复调用返回首次结果。后端 `mvn test` 共 199 项，197 项通过、2 项按既有规则跳过；真实 MySQL 定向测试 11/11 通过，覆盖账本/工时实际写入、未批准、缺 scope、R3 拒绝、单次幂等和 Flyway V24，账本写入同时验证 `ledger_sync_oplog`。纯 HTTP/MCP 联调确认工时 read/prepare/commit PAT 可见 9 个工具、没有领域 commit，批准后只写入一条记录，重复提交返回 `replayed=true`，R3 获批后仍拒绝提交。前端 Node 49/49、sync-engine 8/8、TypeScript、OpenAPI 一致性和生产构建通过；浏览器操作与视觉检查转入 [`前端手工检查清单.md`](前端手工检查清单.md)，等待用户手工验收。

2026-09-28 MCP prepare 与站内确认增量：V23 新增外部 action 绑定，PAT 支持账本/工时 prepare scope；MCP 首批开放工时记录和账本流水的 create/update/delete prepare，以及 action get/list/cancel。后端 `mvn test` 共 196 项，194 项通过、2 项按既有规则跳过，Flyway V1-V23、真实 MySQL、混合 collation 回归和架构边界通过；前端 Node 49/49、sync-engine 8/8、TypeScript、OpenAPI 一致性和生产构建通过。真实协议联调确认工时 read+prepare PAT 可见 8 个对应工具、commit 为 0；prepare、网站批准、MCP 状态回查和取消均成功，批准前后工时业务表保持零新增。确认页通过桌面与 375px 移动端亮暗主题验收，无横向溢出，确认操作栏不遮挡底部导航。

2026-09-27 R4 审批与导入确认增量：后端 184 项测试中 182 项通过、2 项按既有规则跳过，Flyway V21、真实 MySQL、迁移回放和架构边界通过；前端 49 项 Node 测试、8 项 sync-engine、TypeScript、OpenAPI 一致性和生产构建通过。导入审批主流程在桌面 Chromium、桌面 WebKit和 375px 移动 Chromium 共 3/3 通过。本地 `salary-backend`、`salary-frontend` 已刷新并健康。

2026-09-27 成员与角色 R4 审批增量：成员/角色查询作为 R1 工具开放，创建、修改、删除统一改走 R4 prepare/commit 和站内审批；OWNER、系统角色、角色引用和权限值均由领域层保护，重复批准不会重复写入。后端 187 项测试中 185 项通过、2 项按既有规则跳过，Flyway V21、真实 MySQL、迁移回放和架构边界通过；前端 49 项 Node 测试、8 项 sync-engine、TypeScript、OpenAPI 一致性和生产构建通过。成员及角色审批主流程在桌面 Chromium、桌面 WebKit和 375px 移动 Chromium 共 3/3 通过，移动端长表单现可在对话框内滚动到提交操作。

2026-09-27 账本删除与永久清除 R4 审批增量：删除账本仅允许 OWNER，prepare 展示完整影响并固化 revision，批准时强制保留至少一个可用账本；永久清除支持单项和当前回收站快照，冻结每项 type/id/revision 后全量校验并原子执行。管理页和审批中心已完成三端流程，批准后自动刷新账本选择或回收站投影。后端 191 项测试中 189 项通过、2 项按既有规则跳过；前端 49 项 Node 测试、8 项 sync-engine、TypeScript、OpenAPI、生产构建和三端 E2E 3/3 通过。

2026-09-23 服务端会话恢复与基础 SSE 增量：

- AI/身份相关 Reactor 测试通过；真实 MySQL `AgentConversationIntegrationTest` 3/3 通过并执行 V1-V15，覆盖会话恢复、用户隔离、turn 幂等和终态保护。
- OpenAPI 重新生成、客户端一致性检查、TypeScript 严格编译和前端生产构建通过。
- Agent 专项 E2E 在桌面 Chromium、桌面 WebKit 与 375px 移动 Chromium共 9/9 通过；WebKit 验证复制状态，Chromium 额外验证剪贴板内容。
- 会话交互增量完成：桌面右键与全端“更多”菜单提供改名、归档、删除；回复生成期间后续消息进入可管理队列；自动跟随只在严格底部状态启用。前端构建通过，Chromium 新增聚焦 E2E 2/2 通过。
- 本地 Compose 后端 schema 已到 v15；真实 DeepSeek SSE、Token/TTFT/工具耗时展示及异步安全分派日志修正完成。
- 2026-09-23 会话组织增量：V16 增加会话分组和置顶；分组 CRUD、会话移动、置顶、用户隔离由后端持久化，前端支持空分组投放、拖拽进出分组及队列拖拽重排。真实 MySQL 集成测试 3/3 通过；Agent 专项在桌面 Chromium、桌面 WebKit 与 375px 移动 Chromium 共 23 项通过、1 项移动端原生拖拽按设计跳过，生产构建和 OpenAPI 客户端检查通过。
- 分组布局与本地环境已修正：窄侧栏标题保持单行，侧栏和分组折叠具备连续动画；本地后端已重建并迁移至 V16，`/api/v1/agent/session-groups` 与会话移动接口可用。
- 2026-09-23 服务端队列恢复增量：V17 增加持久队列 revision/position/retry 关系，同一会话串行执行并在应用重启后重新领取遗留 turn；支持刷新恢复、排序冲突回读、等待项删除、执行中取消和失败/取消重试。AI 31/31、真实 MySQL 集成 4/4、前端生产构建通过；Agent 专项跨桌面 Chromium、桌面 WebKit 与 375px 移动 Chromium 共 29 项通过、1 项移动端会话原生拖拽按设计跳过。
- 2026-09-23 移动/平板模块导航修正：1024px（含）以下底栏跟随当前模块显示二级菜单，不再混入其他模块切换。工时提供打卡/记录/统计；账本提供九个现有二级入口。底栏不重复显示模块名，每项以图标为主要识别、文字为辅助；可见数量根据实际宽度计算，溢出项进入“更多”，用户可调整顺序并按模块持久化；当前入口始终保持可达和高亮。
- 2026-09-23 模型配置与 Trace 增量：V18 从空库和旧 fixture 迁移通过；AI 35/35、真实 MySQL 6/6、前端 46/46、构建及 OpenAPI 检查通过。API Key 由独立凭据密钥 AES-GCM 加密，模型端点默认拒绝非 HTTPS 和私网地址；环境变量模型保留为只读兜底。
- 2026-09-23 受控写入与成本体验增量：工时新增和单笔收入/支出采用动态补参、预览确认、幂等 commit；action 替换同步持久化到消息元数据。V19 在真实 MySQL 从空库迁移通过，支持环境模型价格配置、DeepSeek 峰谷档位与逐轮费用展示；模型选择移入输入框，滚动到底部按钮避开输入区，用户消息不再显示导航按钮。
- 2026-09-24 记录修改增量：新增流水历史、收入/支出修改和工时修改工具；prepare 返回完整编辑字段及前后差异，工时由服务端重算加班与实际时薪，commit 使用原 revision 并阻止并发覆盖。AI 50/50、前端 Node 47/47、构建、类型/OpenAPI 一致性检查通过；新增 E2E 在 Chromium、WebKit 和 375px 移动端共 6/6 通过。
- 2026-09-24 流式滚动修正：SSE 完成态、Trace 回填和服务端历史对账不再重建当前回复 DOM；自动跟随时持续保持底部锚点，用户主动上滚时继续保持原阅读位置，消除回复结束瞬间闪到上方的问题。
- 2026-09-27 复杂流水与批量账务增量：单笔工具扩展到普通收支、转账、借入、借出、收债和还款；新增父级批量创建/删除 action、最多 50 笔、逐项完整编辑、汇总预览和原子 commit。后端 153 项中 151 项通过、2 项既有跳过；真实 MySQL 4/4、前端 Node 49/49、sync-engine 8/8、桌面 Chromium Agent 22/22、新批量卡片跨 WebKit/375px 移动端 4/4 通过，类型、OpenAPI 和生产构建均通过。
- 2026-09-27 首批管理工具增量：工时设置支持薪资、标准时间、午休与历史范围重算；账本支持创建/修改，账户和两级分类支持查询及受控 CRUD。所有写入固化 revision 并继续使用 prepare/commit；账本删除仅生成 R4 影响预览。前端增加管理卡片、差异、影响和高风险禁用状态，提交后刷新工时或账本投影。
  验证结果：后端共 157 项，155 项通过、2 项既有规则跳过，真实 MySQL、Flyway V1-V20、架构边界和本地 Spring 容器启动均通过；前端 Node 49/49、sync-engine 8/8、类型检查、OpenAPI 一致性和生产构建通过；Agent 桌面 Chromium 24/24，新管理卡片在桌面 WebKit与 375px 移动 Chromium 4/4 通过。
- 2026-09-27 第二批管理工具增量：新增商家/项目查询与受控 CRUD，删除预览统计有效流水引用并继续采用软删除；预算支持月度总预算和支出分类预算的设置/删除，预览显示已使用金额与调整后使用率，更新和删除固化 revision。前端补齐月份输入、分类搜索、差异、影响和危险态确认；模型继续只可调用 list/prepare。
  验证结果：后端共 165 项，163 项通过、2 项既有规则跳过；真实 MySQL 新增 2/2 通过并覆盖关联流水、软删除、预算支出和 revision 冲突。前端 Node 49/49、sync-engine 8/8、类型检查、OpenAPI 一致性和生产构建通过；Agent 桌面 Chromium 26/26，新卡片跨桌面 WebKit与 375px 移动 Chromium 4/4 通过。
- 2026-09-27 周期任务 Agent 增量：新增周期任务查询、创建、修改、删除和手动执行受控工具；复用现有固定日期、间隔规则、任务权限、真实流水生成和到期日唯一键，修改、删除及执行均固化 revision。前端增加完整周期规则、流水预览、差异、危险删除和立即执行强确认卡片。
  验证结果：后端 `mvn test` 共 171 项，169 项通过、2 项按既有规则跳过，新增真实 MySQL 集成测试 3/3 通过并覆盖手动执行、到期日去重、revision 冲突、历史流水保留和跨用户隔离；前端 Node 49/49、sync-engine 8/8、类型检查、OpenAPI 一致性和生产构建通过；周期任务卡片在桌面 Chromium、桌面 WebKit 与 375px 移动 Chromium 共 6/6 通过。
- 2026-09-27 回收站与导入导出基础增量：新增回收站查询与恢复 prepare/commit、R4 永久清除影响预览、流水导出 prepare/commit 和 CSV/XLS/XLSX 导入预览 prepare。恢复固化 revision；导出确认后通过既有受认证接口下载且不持久化临时文件；导入文件不进入模型上下文，只返回有效、重复、错误行结构化摘要，本轮不开放导入确认。后端限制 10 MB、10,000 行并为 CSV 导出增加公式注入防护。
  验证结果：后端 `mvn clean test` 共 179 项，177 项通过、2 项按既有规则跳过；新增真实 MySQL 3/3 通过，覆盖恢复 revision 冲突、恢复结果、跨用户回收站隔离、导入预览零写入/用户隔离和 CSV 公式注入防护。前端 Node 49/49、sync-engine 8/8、类型检查、OpenAPI 一致性和生产构建通过；回收站恢复、受认证导出和只读导入预览在桌面 Chromium、桌面 WebKit与 375px 移动 Chromium 共 9/9 通过。

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
4. 继续 [工作台的 Agent 改造计划](工作台的Agent改造计划.md)：按 [前端手工检查清单](前端手工检查清单.md) 使用 MCP Inspector、Codex 和 WorkBuddy 完成真实 OAuth 连接与兼容记录；R3/R4 commit 继续关闭。连接方法见 [MCP 连接指南](MCP连接指南.md)。
5. 按 [Phase 2 增量执行计划](superpowers/plans/2026-10-09-phase2-increment-plan.md) 启动 P2-I4 重复、提醒与任务站内信；任务域与现有工时/账本 Agent 可分别推进，完整任务能力完成后再注册为新的 Domain Tool。
6. 按 [后续特性路线图](后续特性路线图.md) 的 F0 先冻结设置归属、事件信封、邀请/任务状态机与 NAS 接入决策，再分别启动 F1 设置/资料和 F2 异步基础设施；站内信与音乐不得绕过这些前置门禁。

## 文档约定

- `ARCHITECTURE.md`：长期目标、阶段计划、架构门禁和当前状态。
- `工作台的Agent改造计划.md`：Agent、MCP、受控写入、工具覆盖和逐阶段验证计划。
- `MCP连接指南.md`：PAT 创建、Streamable HTTP 配置、scope、联调与故障排查。
- `Phase 1 —— 账本设计具体展开.md`：账本产品约定、已实现能力和剩余验收项。
- `DEPLOY.md`：本地联调、构建、部署与排障。
- `后续特性路线图.md`：公共/模块设置、用户资料、Redis/RabbitMQ、站内信/邀请和 NAS 音乐的详细拆解、依赖与验收门禁。
- `docs/superpowers/plans/`：阶段性实施计划记录。

根 `.gitignore` 继续忽略未纳入交付的 `docs/` 新文件；本轮 `ARCHITECTURE.md`、`overview.md` 和工作台 Agent 计划已显式纳入版本控制。后续新增文档仍需评估是否加入白名单。
