# 个人工作台项目总览

> 状态日期：2026-09-27
> 当前主线：Phase 3B/3D Web Agent 核心闭环已完成，真实模型已能调用只读工具及获准的 R2/R3 prepare；R4 站内审批已覆盖导入确认、成员/角色、账本删除和永久清除，模型仍不可调用 R4。下一阶段进入只读 MCP、PAT 与 scope，继续按“现有功能 Agent 化 → MCP → 文件/RAG”顺序推进

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

- **Phase 2 任务管理：未启动。** 导航只有禁用占位，没有 task/file/notification 模块或表结构。
- **Phase 3A-D Agent/MCP：Phase 3A/3B Web 核心闭环完成，Phase 3C 待启动。** 会话、队列、SSE、模型连接、Usage 与 Trace 已落地；首页已支持工时记录/设置、七类流水、批量账务、账本基础资料、预算、周期任务、回收站恢复、导出和导入预览的受控卡片。统一 R4 审批中心已覆盖导入确认、成员/角色、账本删除和永久清除，批准后执行且可幂等恢复；MCP Server 尚未实现。
- **Phase 4 文件/RAG：未启动。** 尚无 MinIO/NAS 文件域、Tika、Qdrant 和知识库。
- **Phase 5 跨域洞察：未启动。** 只有 `domain_event` 预留表，无事件发布/消费、`report_fact`、`report_snapshot` 或洞察页面。
- **Phase 6 持续打磨：部分能力提前实现。** 已有响应式布局、主题、共享账本和可重复恢复演练；PWA、全局搜索和完整可观测体系尚未实现。

## 当前验证基线

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
4. 继续 [工作台的 Agent 改造计划](工作台的Agent改造计划.md)：下一增量进入只读 MCP Server、PAT、scope、撤销、审计、限流与 Inspector/Codex/WorkBuddy 连接验证，随后开放 MCP prepare/commit 和 OAuth；真实同步投影回归作为每个写入增量的共同门禁。
5. Phase 2 任务域与现有工时/账本 Agent 可分别推进；任务能力完成后再注册为新的 Domain Tool，不阻塞 Phase 3A-D。

## 文档约定

- `ARCHITECTURE.md`：长期目标、阶段计划、架构门禁和当前状态。
- `工作台的Agent改造计划.md`：Agent、MCP、受控写入、工具覆盖和逐阶段验证计划。
- `Phase 1 —— 账本设计具体展开.md`：账本产品约定、已实现能力和剩余验收项。
- `DEPLOY.md`：本地联调、构建、部署与排障。
- `docs/superpowers/plans/`：阶段性实施计划记录。

根 `.gitignore` 继续忽略未纳入交付的 `docs/` 新文件；本轮 `ARCHITECTURE.md`、`overview.md` 和工作台 Agent 计划已显式纳入版本控制。后续新增文档仍需评估是否加入白名单。
