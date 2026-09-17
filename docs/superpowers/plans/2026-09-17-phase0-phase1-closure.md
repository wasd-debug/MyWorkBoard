# Phase 0/1 收口实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 逐项实施；生产代码遵循 RED -> GREEN -> REFACTOR。
> **分支：** `feat/phase0-phase1-closure`
> **规格：** `docs/superpowers/specs/2026-09-17-phase0-phase1-closure-design.md`

## 全局约束

- 保留 `deploy/docker-compose.yml` 的现有用户改动，不触碰根目录 Excel。
- snapshot 后端端点本轮保留兼容，前端停止使用。
- 数据流、Maven 拆分、UI 依赖退出分开实施和验证。
- 每项行为变更先新增失败测试并记录预期失败原因。
- 每个任务完成后同步更新本文件 checkbox；最终同步 README 和全部相关 docs。

## Task 1：建立前端 store 与后端集成测试基线

**文件**

- Modify: `frontend/package.json`
- Create: `frontend/src/stores/worktime.test.js`
- Modify: `backend/pom.xml`
- Create: `backend/app/src/test/java/com/salarytracker/integration/MySqlIntegrationTestSupport.java`
- Create: `backend/modules/worktime/src/test/java/com/salarytracker/worktime/WorktimeResourceContractTest.java`

**步骤**

- [x] 为 Node 测试增加统一脚本，并让现有测试仍可运行。
- [x] RED：测试 worktime store 初始化只调用 settings/records 资源 API，当前实现应因 snapshot 调用而失败。
- [x] RED：契约测试固定 settings/records 的字段、If-Match、Idempotency-Key 和权限要求。
- [x] 增加 Testcontainers MySQL 基类；Docker 不可用时由 Testcontainers 显式跳过。
- [x] GREEN：只补足测试夹具与可注入边界，不改变页面行为。

**验证**

```bash
cd frontend && npm test
cd backend && mvn -pl app -am test
```

## Task 2：冻结工时计算黄金样例

**文件**

- Create: `backend/app/src/test/java/com/salarytracker/worktime/WorktimeCalculationIntegrationTest.java`
- Modify: `backend/modules/worktime/src/main/java/com/salarytracker/worktime/WorktimeService.java`（仅测试暴露缺陷时）

**步骤**

- [x] RED：普通工作日、跨午夜、无结束时间、休息时长。
- [x] RED：月度工资覆盖、税前/税后口径。
- [x] RED：资源创建/更新返回 `overtimeMin`、`realHourlyWage`、`calcVersion`、`timezone`、`revision`。
- [x] GREEN：修正计算或序列化的最小实现。

**验证**

```bash
cd backend && mvn -pl app -am -Dtest=WorktimeCalculationIntegrationTest test
```

## Task 3：将 worktime store 切换到资源 API

**文件**

- Modify: `frontend/src/stores/worktime.js`
- Modify: `frontend/src/api/worktime.js`
- Modify: `frontend/src/stores/worktime.test.js`
- Reuse: `frontend/src/utils/clientSession.js`

**接口**

- `fetchSettings()` / `fetchRecords(range)`
- `saveSettings(patch)`
- `saveRecord(date, input)`：按是否已有 `id` 选择 POST/PATCH
- `deleteRecord(date)`
- `importResources(snapshot)` / `clearResources()`

**步骤**

- [x] RED：读取合并为日期索引，保留服务端计算字段，并完整分页读取历史记录。
- [x] RED：新建使用 idempotency key；更新/删除使用 revision。
- [x] RED：响应覆盖乐观输入，冲突不破坏当前投影。
- [x] GREEN：实现资源 action，移除 store 对 snapshot API 的 import。
- [x] REFACTOR：统一规范化与 loading 状态。

**验证**

```bash
cd frontend && node --test src/stores/worktime.test.js
```

## Task 4：迁移工时页面并收缩 app store

**文件**

- Modify: `frontend/src/stores/app.js`
- Modify: `frontend/src/views/PunchView.vue`
- Modify: `frontend/src/views/RecordsView.vue`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/StatsView.vue`
- Modify: `frontend/src/App.vue`

**步骤**

- [x] RED：store 与浏览器测试证明读取、保存、更新、删除不调用 snapshot。
- [x] 页面读取 `useWorktimeStore`；auth/theme/holiday 继续由 app store 负责。
- [x] Punch 保存和 Records 删除改调资源 action。
- [x] Settings 设置、导入和清空改调 store command。
- [x] 已保存日指标优先使用服务端字段，`CALC` 仅保留表单预览。
- [x] 删除 app store 的 snapshot 同步队列与工时本地缓存写路径。

**验证**

```bash
cd frontend && npm test
cd frontend && npm run build
rg -n "api(Get|Put)WorktimeSnapshot|saveAll\(" frontend/src
```

## Task 5：定义账本 command facade 与命令分类

**文件**

- Modify: `frontend/src/stores/ledger.js`
- Create: `frontend/src/stores/ledgerCommands.test.js`
- Modify: `frontend/packages/sync-engine/src/index.js`
- Modify: `frontend/packages/sync-engine/src/index.test.js`

**步骤**

- [x] RED：transaction/account/category/merchant/project/budget 的 put/remove 先落本地与 oplog。
- [x] RED：断网失败保留 pending；rejection/conflict 可查询。
- [x] RED：在线命令断网时返回 `ONLINE_REQUIRED`，不产生伪 oplog。
- [x] GREEN：提供具名 command facade，封装 client id、revision、同步触发与错误分类。

**验证**

```bash
cd frontend && node --test packages/sync-engine/src/index.test.js src/stores/ledgerCommands.test.js
```

## Task 6：迁移账本流水写入口

**文件**

- Modify: `frontend/src/views/LedgerView.vue`
- Modify: `frontend/src/views/LedgerTransactionsView.vue`
- Modify: `frontend/src/stores/ledger.js`
- Modify: `frontend/src/stores/ledgerCommands.test.js`

**步骤**

- [x] RED：新增、编辑、删除流水在 API 失败/离线时仍出现在本地投影并标记 pending。
- [x] 迁移所有流水写操作到 store command。
- [x] 服务端确认后刷新余额/投影；冲突和拒绝显示可操作状态。
- [x] 行为测试通过后删除 legacy/`*Fixed` 死路径。

## Task 7：迁移账本资源与在线命令写入口

**文件**

- Modify: `frontend/src/views/LedgerManagementView.vue`
- Modify: `frontend/src/views/LedgerScheduledTasksView.vue`
- Modify: 其他包含账本写 API 的 `frontend/src/views/*.vue`
- Modify: `frontend/src/stores/ledger.js`

**步骤**

- [x] RED：账户、分类、商家、项目、预算离线 CRUD。
- [x] RED：成员/角色/权限、账本删除、导入、AI 确认、任务执行、恢复必须经过在线 store command。
- [x] GREEN：逐入口迁移；页面只保留读 API 或 store 调用。
- [x] 静态扫描确认 view 中没有账本写 API import/call。

## Task 8：补账本同步契约与 MySQL 集成测试

**文件**

- Create: `backend/app/src/test/java/com/salarytracker/ledger/LedgerSyncIntegrationTest.java`
- Create: `backend/app/src/test/java/com/salarytracker/ledger/LedgerPermissionIntegrationTest.java`
- Modify: `backend/modules/ledger/src/main/java/com/salarytracker/ledger/LedgerSyncService.java`（仅测试暴露缺陷时）

**场景**

- [x] 六类离线资源 push/pull。
- [x] op id 幂等重放。
- [x] revision 冲突进入冲突响应。
- [x] 校验/权限拒绝可定位到具体 op。
- [x] 用户、账本、失权隔离。

## Task 9：增加 Playwright 断网 E2E

**文件**

- Modify: `frontend/package.json`
- Create: `frontend/playwright.config.js`
- Create: `frontend/e2e/offline-ledger.spec.js`
- Create: `frontend/e2e/worktime-resource.spec.js`
- Create: `deploy/docker-compose.e2e.yml`

**场景**

- [x] 在线工时资源读写且无 snapshot 请求。
- [x] 账本断网新增/修改/删除，刷新后 pending 仍在。
- [x] 恢复网络后自动同步。
- [x] 冲突、拒绝和切换用户/账本隔离。
- [x] 桌面与移动视口关键流程。

## Task 10：Flyway replay 与恢复演练

**文件**

- Create: `backend/app/src/test/java/com/salarytracker/migration/FlywayMigrationIntegrationTest.java`
- Create: `deploy/verify-backup-restore.sh`
- Modify: `docs/DEPLOY.md`

**步骤**

- [x] 空 MySQL 执行 V1-V11+，校验关键表、索引和约束。
- [x] 从受支持旧版本 fixture 升级并校验用户/工时/账本数量。
- [x] 恢复脚本只针对显式临时数据库，拒绝生产地址与默认库名。
- [x] 恢复后执行 Flyway validate、数据对账和健康检查。

## Task 11：物理拆分 Maven 模块

**文件**

- Create: `backend/modules/ledger/pom.xml`
- Move: `backend/src/main/java/com/salarytracker/{platform,identity,worktime,ledger}` 到对应 module
- Move: 对应单元测试到 module
- Modify: `backend/pom.xml`
- Modify: `backend/app/pom.xml`
- Modify: `backend/app/src/test/java/com/salarytracker/ArchitectureBoundaryTest.java`

**步骤**

- [x] RED：架构测试要求业务类实际位于所属 module source root。
- [x] 先 platform，再 identity/worktime，再 ledger，最后 app 装配。
- [x] 删除 app 的中央 `sourceDirectory`/`testSourceDirectory` 配置。
- [x] 每移动一个模块运行该模块及 app 回归。

## Task 12：退出 Element Plus

**文件**

- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/stores/app.js`
- Modify: `frontend/src/views/*.vue`
- Modify: `frontend/src/components/**/*.vue`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`

**步骤**

- [x] 建立 message/icon 替代组件与服务的交互测试。
- [x] 分组件替换并保持 ARIA、主题和窄屏行为。
- [x] 删除 `element-plus` 与 `@element-plus/icons-vue`。
- [x] 全仓扫描 import 为零，构建和 E2E 通过。

## Task 13：全量回归与文档收口

**文件**

- Modify: `README.md`
- Modify: `docs/overview.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/Phase 1 —— 账本设计具体展开.md`
- Modify: `docs/DEPLOY.md`
- Modify: 本计划

**验证**

```bash
cd backend && mvn test
cd backend && mvn package
cd frontend && npm test
cd frontend && npm run build
cd frontend && npm run test:e2e
git diff --check
```

- [x] 记录测试数、skip 原因、E2E 浏览器/视口、迁移版本和恢复演练结果。
- [x] 文档只声明有验证证据的完成状态。
- [x] 完成本分支提交、推送与服务器部署。
