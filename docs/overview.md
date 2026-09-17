# 个人工作台项目总览

> 状态日期：2026-09-17
> 当前主线：Phase 0 地基收口 + Phase 1 账本验收收口

## 项目定位

本项目从“加班时长与真实时薪计算”工具演进为个人效率工作台，长期由五类能力组成：

1. **工时**：打卡、工资、加班、真实时薪与节假日。
2. **账本**：账本、账户、流水、分类、预算、成员与权限、报表、导入导出、周期流水和 AI 记账。
3. **任务**：清单、任务、日历、提醒、番茄钟、习惯和倒数日。
4. **AI 与知识库**：附件、NAS、RAG、Agent 工具调用。
5. **洞察**：工时、账本和任务的跨域日报、周报、月报及年报。

目标架构保持为 Java 17 + Spring Boot 模块化单体、MySQL/Flyway、Vue 3/Vite/Pinia、Tailwind CSS + 源码组件、IndexedDB/oplog local-first。完整目标与阶段门禁见 `ARCHITECTURE.md`。

## 当前实现

### Phase 0：工程收口已完成，发布门禁仍有缺口

已实现：

- Flyway V1-V3 接管基础表结构与旧数据回填，`schema.sql` 不再参与启动初始化。
- Spring Security + JWT access token + HttpOnly refresh cookie；注册、登录、刷新、退出和修改密码。
- `app_user`、角色/权限、审计日志、统一异常响应和 trace id。
- `/api/v1/worktime` 设置、记录 CRUD 和兼容 snapshot 接口。
- Maven 父工程及 `platform`/`identity`/`worktime`/`ledger`/`app` 物理模块，以及 ArchUnit 依赖和源码归属测试。
- Tailwind CSS、语义 token、响应式应用壳、基础 UI 组件和按用户隔离的本地缓存。

本轮新增收口：

- 工时 store 与页面已切换 settings/records CRUD；服务端持久化计算结果，前端计算只用于未保存预览和页面聚合。
- Testcontainers 覆盖 MySQL 资源契约、工资口径、权限、同步与 Flyway 空库/旧 fixture 重放。
- Element Plus 与图标包已经移除，统一使用源码 UI、轻量消息服务和 Lucide。
- 恢复脚本和 Playwright 工时资源/账本断网场景已建立。

仍未达到完整 Phase 0 发布门禁的是 OpenAPI 生成客户端、视觉/无障碍与 WebKit/真机记录，以及生产环境周期恢复演练。

### Phase 1：local-first 主链已闭环，完整发布验收未完成

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

尚未达到完整 Phase 1 发布验收：

- MoneyWiz 模板没有明确实现证据；支付宝、微信和银行卡账单自动拉取仍为 `STATEMENT_IMPORT` 占位。
- 真实工作簿重复导入金额对账、WebKit/真机、视觉与无障碍验收仍待完成。
- 部分页面和服务文件体积较大，仍需后续按职责拆分。

### Phase 2-5

- **Phase 2 任务管理：未启动。** 导航只有禁用占位，没有 task/file/notification 模块或表结构。
- **Phase 3 RAG/Agent：仅有 LLM 网关前置能力。** 尚无 MinIO/NAS 文件域、Tika、Qdrant、LangChain4j、知识库和 Agent 工具编排。
- **Phase 4 跨域洞察：未启动。** 只有 `domain_event` 预留表，无事件发布/消费、`report_fact`、`report_snapshot` 或洞察页面。
- **Phase 5 持续打磨：少量能力提前实现。** 已有响应式布局、深色主题和共享账本；PWA、全局搜索、可观测体系与自动恢复演练尚未实现。

## 当前验证基线

2026-09-17 本地验证结果：

- `cd backend && mvn test`：58 个测试中 57 通过，1 个依赖本地真实 Excel 的可选用例跳过；Testcontainers MySQL、迁移和跨模块架构测试均实际执行。
- `cd frontend && npm test`：41 个 Node 测试全部通过。
- `cd frontend && npm run build`：生产构建成功。
- `cd frontend && npm run test:e2e`：6/6，通过 desktop Chromium 与 Pixel 7 的工时资源和账本断网主链。
- `deploy/verify-backup-restore.sh`：tmpfs MySQL 演练通过，四张核心表恢复前后数量一致，Flyway v11 与健康检查通过，临时恢复库自动删除。
- `git diff --check`：通过。

现有自动化测试主要覆盖账本规则、同步引擎、用户缓存隔离和 JWT；不等同于 `ARCHITECTURE.md` 第 12.6 节定义的完整阶段验收。

## 下一步顺序

1. 完成随手记/MoneyWiz 导入范围确认与真实文件金额对账。
2. 完成 WebKit、视觉、键盘、无障碍和真机验收。
3. 将恢复脚本纳入周期运维并留存生产发布/回滚记录。
4. Phase 0/1 通过完整退出门禁后，再进入 Phase 2 任务域。

## 文档约定

- `ARCHITECTURE.md`：长期目标、阶段计划、架构门禁和当前状态。
- `Phase 1 —— 账本设计具体展开.md`：账本产品约定、已实现能力和剩余验收项。
- `DEPLOY.md`：本地联调、构建、部署与排障。
- `docs/superpowers/plans/`：阶段性实施计划记录。

`docs/` 当前仍被根 `.gitignore` 默认忽略；本轮相关文档会随代码显式纳入交付，后续新增文档仍需评估是否调整忽略策略。
