# 部署说明

> 状态日期：2026-09-28
> SSH 规则：所有连接必须使用仓库根目录的 `workboard.pem`，禁止密码认证。

本项目通过 Docker Compose 运行三个服务：MySQL、Spring Boot 后端和 Nginx 前端。前端对外提供 80 端口，并将 `/api`、MCP Streamable HTTP `/mcp`、`/.well-known/*` 和 OAuth 协议端点转发到后端。

## 已配置服务器

- 主机：`212.64.29.21`
- SSH 用户：`ubuntu`
- SSH 私钥：仓库根目录 `./workboard.pem`（仅本机存在，已由根 `.gitignore` 排除）

首次使用前确认权限为 `600`：

```bash
chmod 600 ./workboard.pem
ssh -i ./workboard.pem ubuntu@212.64.29.21
```

不得显示、复制、上传或提交私钥内容，不得回退到密码认证。所有 `ssh`、`scp` 和依赖 SSH 的同步流程都必须显式使用该密钥。

## 部署前准备

1. 准备一台安装 Docker Engine 与 Docker Compose 插件的 Linux 主机。
2. 将项目复制到主机并进入项目根目录。
3. 创建部署配置并修改数据库密码：

   ```bash
   cp deploy/.env.example deploy/.env
   ${EDITOR:-vi} deploy/.env
   ```

   可配置项：

   - `MYSQL_ROOT_PASSWORD`：MySQL root 密码。
   - `MYSQL_PASSWORD`：应用连接 MySQL 使用的密码。
   - `JWT_SECRET`：至少 32 字节的 access token 签名密钥。
   - `JWT_ACCESS_TTL`：access token 有效期，单位秒，默认 `7200`（2 小时）。
   - `JWT_REFRESH_TTL`：refresh token 有效期，单位秒，默认 `2592000`（30 天）。
   - `LEGACY_ADMIN_PASSWORD`：旧数据回填的 `admin` 账户初始密码。
   - `COOKIE_SECURE`：仅 HTTPS 生产环境设为 `true`；通过服务器 IP + HTTP 访问时必须为 `false`，否则浏览器不会发送 refresh cookie，刷新页面会反复回到登录页。
   - `APP_MCP_ENABLED`（映射到 `app.mcp.enabled` 时使用）：控制只读 MCP 入口；关闭后 `/mcp` 返回 404，不影响传统页面和 Web Agent。
   - `APP_MCP_WRITE_ENABLED`（映射到 `app.mcp.write-enabled`）：控制 MCP prepare、action 查询/取消和 R2 `agent.action.commit`，默认应为 `false`；关闭后只保留 read scope 工具。R3/R4 commit 不受此开关放宽，始终拒绝。
   - `APP_MCP_OAUTH_ENABLED`（映射到 `app.mcp.oauth-enabled`）：控制 OAuth discovery、DCR、authorize/token/revoke、站内授权管理和 OAuth access token 认证；默认 `false`，关闭后既有 OAuth token 也不能访问 `/mcp`，PAT 不受影响。
   - `APP_PUBLIC_BASE_URL`（映射到 `app.public-base-url`）：网站对外公开根地址，例如 `https://work.example.com`，不得带结尾 `/`。生产启用 OAuth 时必须配置，用于 issuer、resource metadata、授权地址和 MCP `WWW-Authenticate`；禁止配置成容器内地址或 HTTP 生产地址。

## 从源码构建并启动

```bash
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

也可以使用仓库内的一键脚本：

```bash
sudo bash deploy/deploy.sh
```

脚本会检查 Docker、启动 Docker 服务、检查 80 端口、构建镜像、启动容器，并验证 `http://127.0.0.1/api/health`。

部署完成后，通过服务器的 80 端口访问应用。若服务器有防火墙或云安全组，请放行 TCP 80。

MCP 使用与网站相同的公开地址，例如 `https://work.example.com/mcp`。Nginx 必须保持 `/mcp` 的 `proxy_buffering off`、HTTP/1.1 和长读取超时，并转发 `/.well-known/*`、`/oauth/authorize`、`/oauth/token`、`/oauth/register`、`/oauth/revoke`。PAT 在网站设置页创建，完整值只显示一次；OAuth 正式接入必须同时设置 `APP_MCP_OAUTH_ENABLED=true` 和正确的 HTTPS `APP_PUBLIC_BASE_URL`。生产环境建议先保持 `APP_MCP_WRITE_ENABLED=false`，先验证只读 OAuth，再灰度开放 prepare/R2 commit。详细连接与验证见 [MCP 连接指南](MCP连接指南.md)。

### MCP 上线检查与数据保留

1. 发布包含 `V26__mcp_client_diagnostics.sql` 的版本后，确认 Flyway schema 为 v26，`mcp_protocol_event` 表及 OAuth 客户端/授权最近使用字段存在；不得修改或忽略已经应用的 V25/V26。
2. 使用站内登录态访问 `GET /api/v1/mcp/diagnostics`，确认 `configuredBaseUrl` 与 `observedBaseUrl` 一致、远程 OAuth 使用 HTTPS 且 `warnings` 为空。
3. initialize 首次协商可不带版本头；显式 `MCP-Protocol-Version` 当前只接受 `2025-06-18`。发布验收要区分 400 协议错误、401 凭据错误和 429 限流，不能把三者统一改写成登录失败。
4. 动态注册按来源 IP 限制为每小时 20 次和最多 100 个未撤销客户端；MCP 调用继续按 Token 每分钟 120 次限流。反向代理必须传递可信的客户端地址头，并限制只有受信代理能覆盖这些头。
5. 设置页断开 OAuth 客户端只撤销当前用户 grant、关联 Token 和未使用授权码，不应删除其他用户授权。诊断、客户端和协议事件 API 均需要网站 JWT，禁止由 Nginx 配置成匿名路径。
6. 每天北京时间 03:35 自动清理超过 1 天的过期/已消费授权码、超过 90 天的协议事件，并清除已撤销或过期超过 30 天的 OAuth 凭据 hash；该任务不删除业务审计与 `mcp_tool_call`。

上线后先查看设置页诊断摘要和最近协议事件，再按 [前端手工检查清单](前端手工检查清单.md) 分别记录 MCP Inspector、Codex 与 WorkBuddy 的真实客户端版本和结果。当前自动化验证不替代真实客户端及浏览器验收。

### Agent 运行质量面板（V27）

包含 `V27__agent_operations_budget.sql` 的版本启动时会新增用户预算及告警表。升级前备份数据库，升级后确认 Flyway 到 v27；不要改写已应用迁移。设置页的运行质量统计按北京时间提供今日、本周、本月、近 7 天、近 30 天和最多 366 天的自定义范围，支持小时/天/周粒度。总费用和趋势费用仅汇总预算币种，模型费用按原币种分别显示，不做隐式汇率换算。日/月预算支持 50%、80%、100% 站内告警与阈值开关；预算不阻断请求，不提供邮件/推送通知。

成本告警与调用追踪增量不新增迁移：直接复用 V27。Trace/预算事务提交后以独立事务生成告警，失败仅记录异常类型，不打印参数或密钥；每 5 分钟对当月有用量且启用预算的用户补算当前日/月。历史告警保留，只读或已读不触发删除；修改预算不重新发送同周期同币种同阈值告警，切换币种独立计数。未定价用量不参与费用告警。新接口为 `/api/v1/agent/operations/alerts`、`/alerts/{id}/read`、`/calls`、`/calls/{turnId}`，均需登录并按当前用户隔离。调用分页每页展示 20 条，API 额外返回第 21 条用于判断下一页；明细只显示统计和固定失败摘要，不自动重试。

### MCP Token 策略（V28）

V28 为 `mcp_personal_token` 增加权限模板、高风险策略和每分钟限流字段。升级后确认 Flyway 到 v28；旧 Token 默认按只读/已有 scopes 推断模板，OAuth Token 使用数据库默认策略。完整工作台模板只扩大 MCP 工具发现范围，不绕过领域权限、账本范围、幂等、revision 或站内 R3/R4 审批。生产建议从只读或可准备开始，确认审计和限流后再为独立客户端创建完整模板 Token。

2026-09-28 本地刷新记录：Docker Hub 获取 Node 构建镜像时返回 EOF，未完成镜像重建；改用本机 Maven/Vite 构建产物复制至现有应用容器并重启，MySQL 容器及数据卷未变更。此方式仅用于本地手工联调，应用容器重建会丢失容器层中的产物，后续正式发布仍须构建新镜像。本次没有远程推送或云部署。

2026-09-28 成本告警增量本地刷新：延续本机打包后复制至 `salary-backend:/app/app.jar` 和 `salary-frontend:/usr/share/nginx/html/` 并重启应用容器的方式，MySQL/卷未改动；Vite 5173 保持运行并读取最新源码。后端健康、Nginx 首页及 Vite 首页 HTTP 200；未登录访问告警、调用列表和单次明细均为 401。OpenAPI 快照与生成客户端已同步，全量 Maven、真实 MySQL 定向 11/11、Node 49/49、类型检查、客户端一致性和生产构建通过；前端交互验收留给用户。本轮没有重建正式镜像、推送或云部署。

## 使用已构建镜像

`deploy/docker-compose.prod.yml` 用于运行本地已有的 `salary-backend:latest` 和 `salary-frontend:latest` 镜像：

Nginx API 代理对账本导入使用 15 分钟连接/发送/读取超时，前端导入预览请求也使用 15 分钟超时，避免大账本批量写入被网关提前断开。

```bash
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d
```

镜像可以通过项目中的 Dockerfile 构建：

```bash
sudo docker build -f deploy/Dockerfile.backend -t salary-backend:latest .
sudo docker build -f deploy/Dockerfile.frontend -t salary-frontend:latest .
```

如果 Docker 构建后端时在 Maven `dependency:go-offline` 阶段因网络或镜像源长时间卡住，可先在宿主机完成一次 Maven 构建，再使用运行时 Dockerfile 打包已经验证过的 JAR：

```bash
(cd backend && mvn -DskipTests package)
sudo docker build --platform linux/amd64 -f deploy/Dockerfile.backend.runtime -t salary-backend:latest .
```

### 跨架构服务器部署

如果在 Apple Silicon（`arm64`）Mac 上构建并上传到常见的 `x86_64` 云服务器，必须显式指定目标平台；否则镜像会因架构不匹配无法启动：

```bash
sudo docker build --platform linux/amd64 -f deploy/Dockerfile.frontend -t salary-frontend:latest .
sudo docker image inspect salary-frontend:latest --format 'architecture={{.Architecture}} os={{.Os}}'
sudo docker save salary-frontend:latest | gzip > salary-frontend-amd64.tar.gz
```

确认输出为 `architecture=amd64 os=linux` 后，再上传并加载镜像：

```bash
scp -i ./workboard.pem salary-frontend-amd64.tar.gz ubuntu@212.64.29.21:/tmp/
ssh -i ./workboard.pem ubuntu@212.64.29.21 'gunzip -c /tmp/salary-frontend-amd64.tar.gz | sudo docker load'
```

生产环境只替换前端时，不要执行会影响 MySQL 数据卷或后端的整套 `down`；在生产 Compose 文件所在目录执行：

```bash
sudo docker compose -f docker-compose.prod.yml stop frontend
sudo docker compose -f docker-compose.prod.yml rm -f frontend
sudo docker compose -f docker-compose.prod.yml up -d --no-deps frontend
sudo docker compose -f docker-compose.prod.yml ps frontend
```

确认新容器正常运行后，可删除服务器 `/tmp` 中的镜像压缩包。若需要回滚，应在加载新镜像前先为当前 `salary-frontend:latest` 创建备份标签。

## 本地联调

### 设置页模块化与 MCP 分页（2026-09-30）

设置页已拆为四个模块标签：基础设置、模型与成本、运行质量、MCP / 外部 Agent。MCP Token 接口 `GET /api/v1/mcp/tokens?page=0&pageSize=20` 与协议事件接口 `GET /api/v1/mcp/events?page=0&pageSize=20` 返回 `items/page/pageSize/total/totalPages`，旧的服务内部列表调用保持兼容。部署后需确认数据库迁移无需新增版本，并按 [`前端手工检查清单`](前端手工检查清单.md) 手工检查页码、筛选和移动端显示。

### 一键启动后端与开发数据库

首次需要把服务器数据同步到本地时执行：

```bash
bash deploy/dev-backend.sh --sync-production
```

脚本会启动独立的 `salary-mysql-dev`（MySQL 8，宿主机端口 `3307`），通过 SSH 只读导出生产数据库并覆盖本地开发库，然后打包并启动后端。数据库快照保存在 `.local/db/`，该目录已被 Git 忽略。

`deploy/sync-prod-db.sh` 强制使用仓库根目录的 `workboard.pem`，以 batch 模式连接并明确禁用密码认证；密钥缺失或认证失败时立即停止，不会降级到密码登录。

后续不需要重新同步生产数据时：

```bash
bash deploy/dev-backend.sh
```

已有最新 JAR 时可以附加 `--skip-build`。

只刷新本地数据库时可直接运行 `bash deploy/sync-prod-db.sh`；该操作会覆盖本地 `salary` 开发库，执行前应确认本地数据不需要保留。

生产快照包含真实用户数据，只能留在受控的本地开发机；不要复制到仓库、聊天记录或共享目录。同步脚本只读取生产库，但会删除并重建本地 `salary` 数据库。

先构建后端和前端产物：

```bash
(cd backend && mvn -DskipTests package)
(cd frontend && npm install && npm run build)
```

再启动联调 Compose：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
```

该模式把 `backend/target` 和 `frontend/dist` 挂载到容器中，适合验证构建产物与容器网络。

本地联调 Compose 对 Flyway 使用宽松校验：关闭历史 checksum 校验，并忽略快照中已落地但尚未登记的 pending migration。这样可以直接使用旧的本地数据库快照启动后端；生产 Compose 仍保持严格校验。若需要验证迁移完整性，请使用上面的 `verify-backup-restore.sh` 恢复演练，而不要把本地宽松配置带到生产环境。

## 运维命令

以下命令均在项目根目录执行：

```bash
# 状态
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps

# 查看日志
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f backend
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f frontend
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f mysql

# 重启或停止
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml restart
sudo docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

`down` 不会删除 `mysql-data` 数据卷。只有在明确需要重新初始化数据库时，才删除该卷；删除后数据库中的数据将无法从卷中恢复。

## 备份恢复演练

`deploy/verify-backup-restore.sh` 用于在本机 MySQL 容器内完成一次可重复的恢复验收。脚本只读导出源库，把备份恢复到显式命名的临时库，随后完成核心表数量对账、Flyway migrate/validate、临时后端启动和 `/api/health` 检查。它不会连接远程数据库，也不会接受 `salary`、`salary_e2e` 等普通库名作为恢复目标。

前置条件：

- MySQL 容器已经运行，并将端口映射到本机；本地开发栈默认是 `salary-mysql-dev:3307`。
- 已运行 `cd backend && mvn package`，生成 `backend/target/salary-tracker-backend.jar`。
- `VERIFY_DB_USER` / `VERIFY_DB_PASSWORD` 对应容器内已有应用用户；凭据只通过环境变量传入，不写入命令历史或仓库。
- 备份输出路径必须是一个尚不存在的 `.sql.gz` 文件。

本地开发库演练示例：

```bash
export VERIFY_DB_USER=salary
export VERIFY_DB_PASSWORD='本地应用数据库密码'
bash deploy/verify-backup-restore.sh \
  --container salary-mysql-dev \
  --source-database salary \
  --target-database salary_restore_verify_20260917_01 \
  --backup-output /tmp/salary-restore-20260917-01.sql.gz \
  --db-port 3307 \
  --health-port 18081
unset VERIFY_DB_PASSWORD
```

通过标准：

- gzip 完整性检查通过，备份文件非空。
- 源库已存在的 `app_user`、`work_record`、`ledger_book`、`ledger_transaction` 在恢复前后和应用迁移后数量一致；旧 schema 中缺失的核心表必须由 Flyway 创建成功。
- `flyway_schema_history` 没有失败记录，并输出当前 migration 版本。
- 临时后端的 `GET /api/health` 返回 `ok: true`。

脚本默认在退出时停止临时后端并删除 `salary_restore_verify_*` 临时库，备份文件保留供人工校验。需要检查恢复库时可加 `--keep-database`；检查结束后只删除命令输出中确认过的临时库，禁止对源库执行清理。演练失败时先保留终端输出和备份文件，修复原因后换一个新的临时库名重跑；生产系统与源库不需要回滚，因为整个流程对源库只有一致性只读导出。

2026-09-17 本地验收记录：在独立 tmpfs MySQL 8 容器中完成一次演练，`app_user`、`work_record`、`ledger_book`、`ledger_transaction` 恢复前后数量一致，Flyway 当前版本为 v11，临时后端健康检查通过，临时恢复库和容器均已清理。备份文件仅保留在 `/private/tmp`，不进入仓库。

2026-09-18 本地验收记录：对实际旧 schema 开发快照执行只读导出与隔离恢复；`app_user=3`、`work_record=32`、`ledger_transaction=89` 在迁移前后保持一致，原快照缺失的 `ledger_book` 由 Flyway v11 创建并回填 3 条，临时后端健康检查通过，`salary_restore_verify_*` 临时库已自动删除。脚本据此区分“已有表数量漂移”和“旧 schema 缺表待迁移”两类结果。

## 2026-09-17 生产发布记录

- 应用提交：`0fcd6d1`，发布目录：`/home/ubuntu/salary-tracker/releases/0fcd6d1`。
- 发布前备份：`backups/salary-before-6184e20-20260917-1830.sql.gz`，已通过 gzip 完整性检查；发布前镜像保留为 `salary-backend:pre-6184e20` 和 `salary-frontend:pre-6184e20`。
- 使用 Compose 项目名 `salary-tracker` 原位更新；MySQL 容器重建后继续挂载 `salary-tracker_mysql-data`，未删除或新建数据卷。
- Flyway v11 校验成功，13 个迁移全部有效且无需执行新迁移；四张核心表发布后记录数与发布前备份源一致。
- 内网与公网 `GET /api/health` 均返回 `{"ok":true}`，公网首页返回 HTTP 200，新后端与前端容器运行正常。

## 2026-09-18 生产发布记录

- 应用提交：`642d191`，以 `git archive` 创建独立发布目录：`/home/ubuntu/salary-tracker/releases/642d191`；后端和前端均在服务器上从该源码目录构建为 `amd64` 镜像。
- 发布前先将当前运行镜像保留为 `salary-backend:pre-642d191` 和 `salary-frontend:pre-642d191`，并完成可校验备份 `backups/salary-before-642d191-20260918-171520.sql.gz`（`gzip -t` 和 SQL 结尾标记均通过）。生产数据库账户没有 `PROCESS` 权限，因此备份明确使用 `mysqldump --no-tablespaces`；不读取 tablespace 元数据不会影响应用表的逻辑恢复。
- 仅通过 `docker compose ... up -d --no-deps --force-recreate backend frontend` 更新应用容器；MySQL 容器和数据卷未重建。切换后健康探测在第 6 次成功，启动早期的短暂 502 属于后端尚未监听时的预期窗口；若 60 次探测均失败，发布脚本会把上述 `pre-642d191` 标签重新标记为 `latest` 并重建应用容器。
- 发布后核心数据为 `app_user=3`、`work_record=38`、`ledger_book=5`、`ledger_transaction=16435`；Flyway 已验证 13 个迁移且当前 schema 为 v11，无失败迁移。
- 内网和公网 `GET /api/health`、首页与 `/v3/api-docs` 均返回 200；旧路径 `/api/data`、`/api/auth/login`、`/api/worktime/settings`、`/api/ledger/books` 均返回 404，未认证的 `/api/v1/worktime/settings` 和 `/api/v1/ledger/books` 均返回 401。线上 OpenAPI 统计为 49 个路径、70 个 operation、120 个 schema、0 个重复 operationId 与 0 个自由 object schema。

## 2026-09-20 生产发布记录

- 前端应用提交：`6d15aca`，发布目录：`/home/ubuntu/salary-tracker/releases/6d15aca`；提交归档在服务器上构建为 `amd64` 镜像 `salary-frontend:6d15aca`。
- 本次仅更新前端容器，后端、MySQL 容器及数据卷均未重建；当前前端镜像已保留为 `salary-frontend:pre-6d15aca`，用于快速回滚。
- 使用 `docker compose ... up -d --no-deps --force-recreate frontend` 完成切换；内网 `GET /api/health` 返回 `{"ok":true}`，公网 `/ledger` 与首页均返回 HTTP 200，MySQL 状态为 `running/healthy`。
- 发布内容包括 GPT 式 AI 工作台、会话侧栏和多模态输入界面、账本卡片布局、移动端底栏、五套主题以及暗夜导航选中态。知识库和任务仍保持规划状态，没有新增虚构后端接口。

## 2026-09-22 生产发布记录

- 应用提交：`1d2c465`，发布目录：`/home/ubuntu/salary-tracker/releases/1d2c465`；后端和前端均在服务器上从该源码目录构建为 `amd64` 镜像 `salary-backend:1d2c465` 与 `salary-frontend:1d2c465`。
- 发布前完成逻辑备份：`backups/salary-before-1d2c465-20260922-182728.sql.gz`，使用 `mysqldump --no-tablespaces --single-transaction` 并通过 `gzip -t` 校验；旧应用镜像保留为 `salary-backend:pre-1d2c465` 和 `salary-frontend:pre-1d2c465`。
- 仅执行 `up -d --no-deps --force-recreate backend frontend`，MySQL 容器与数据卷未重建。后端启动约 12 秒期间 Nginx 出现短暂 502，随后 `/api/health` 恢复为 `{"ok":true}`，首页返回 HTTP 200。
- Flyway 从 v11 成功应用两条迁移至 v13；`agent_session` 和 `agent_message` 表已在生产库创建。后端日志确认 `Started SalaryTrackerApplication`，未发现应用启动失败。
- 本次上线包含 Agent 服务端文本会话上下文；SSE、可恢复 turn、写入 Agent、MCP、文件与 RAG 仍未开放。

## 健康检查与配置

### 2026-10-01 云端 MCP OAuth 与写入开关

- 云服务器 `/home/ubuntu/salary-tracker/.env` 已开启 `APP_MCP_ENABLED=true`、`APP_MCP_WRITE_ENABLED=true`、`APP_MCP_OAUTH_ENABLED=true`，并设置 `APP_PUBLIC_BASE_URL=http://jsn1024.cn`。
- 后端和前端按 `--no-deps --force-recreate` 重启，MySQL 容器和数据卷未重建；健康检查第 7 次通过，后端正常启动。
- `/.well-known/oauth-protected-resource/mcp`、`/.well-known/oauth-authorization-server` 和 `/.well-known/openid-configuration` 均返回 HTTP 200；未认证 `POST /mcp` 返回带 `resource_metadata` 的 HTTP 401。
- 当前域名 443 端口尚未提供 HTTPS。PAT 可立即用于 WorkBuddy；OAuth discovery 已开启，但正式远程 OAuth 仍应先配置 TLS，然后将 `APP_PUBLIC_BASE_URL` 切换为 `https://jsn1024.cn`。

### 2026-09-30 暗色模式对比度修正发布记录

- 应用提交：`12299cf`，发布目录：`/home/ubuntu/salary-tracker/releases/12299cf`；后端和前端在服务器构建为 `amd64` 镜像 `salary-backend:12299cf` 与 `salary-frontend:12299cf`。
- 发布前完成 `backups/salary-before-12299cf-20260930-221811.sql.gz` 逻辑备份并通过 `gzip -t`；保留旧镜像 `salary-backend:pre-12299cf`、`salary-frontend:pre-12299cf`，MySQL 容器和数据卷未重建。
- Flyway 从 v27 成功应用 V28、V29；发布前后核心数量保持 `app_user=3`、`work_record=46`、`ledger_book=5`、`ledger_transaction=16510`。
- 切换期间出现短暂 502，健康探测第 8 次恢复；后端日志确认 `Started SalaryTrackerApplication`，未发现 ERROR 或迁移失败。服务器 IP 与 `jsn1024.cn` 的首页和 `/api/health` 均返回 HTTP 200。
- 本次发布修正暗色主题按钮默认/hover 对比度、Agent/MCP/审批/随身 AI 操作按钮前景色，以及首页 Agent 模块卡片和账本摘要卡文字对比度。浏览器视觉交互仍按前端手工检查清单由用户验收。

### 2026-10-01 WorkBuddy MCP 2025-11-25 协议兼容热修复

- 应用提交：`1a827a8`，发布目录：`/home/ubuntu/salary-tracker/releases/1a827a8`；仅重建并切换 `salary-backend:1a827a8`，前端继续运行 `salary-frontend:12299cf`，MySQL 容器和数据卷未重建。
- 发布前完成逻辑备份 `backups/salary-before-1a827a8-20261001-233212.sql.gz` 并通过 `gzip -t`；旧后端镜像保留为 `salary-backend:pre-1a827a8`，Compose 文件保留为 `docker-compose.prod.yml.bak.1a827a8`。
- 根因是应用路由层只接受 `2025-06-18`，而 WorkBuddy 5.6.2 发送 `MCP-Protocol-Version: 2025-11-25`；现已与 MCP SDK 2.0.1 对齐接受 `2024-11-05`、`2025-03-26`、`2025-06-18`、`2025-11-25`。
- 切换后 `/api/health` 返回 200；云端内网和 `http://jsn1024.cn/mcp` 在带 `2025-11-25` 请求头但无凭据时均返回预期 401，而不是 400。PAT 需重新创建，OAuth 远程正式接入仍需 HTTPS。

### 2026-09-23 Agent 受控写入与峰谷计价生产发布记录

- 应用提交：`fd08695`，发布目录：`/home/ubuntu/salary-tracker/releases/fd08695`；后端和前端均在服务器上从提交归档构建为 `amd64` 镜像 `salary-backend:fd08695` 与 `salary-frontend:fd08695`。
- 发布前完成逻辑备份：`backups/salary-before-fd08695-20260923-175716.sql.gz`，使用 `mysqldump --no-tablespaces --single-transaction` 并通过 `gzip -t` 校验；旧镜像保留为 `salary-backend:pre-fd08695` 和 `salary-frontend:pre-fd08695`。
- 生产环境原有 `DEEPSEEK_API_KEY` 保持不变；为 V18 用户模型凭据加密功能在服务器本地生成并写入了 `AI_CREDENTIAL_KEY`，密钥值未输出、未上传且未进入 Git。
- 仅执行 `up -d --no-deps --force-recreate backend frontend`，MySQL 容器和数据卷未重建。Flyway 从 v14 连续成功应用 v15、v16、v17、v18、v19，当前 schema 为 v19。
- 首次切换后的健康检查脚本因字符串匹配条件过严，在接口已经持续返回 HTTP 200 时产生误判并触发旧镜像回滚；确认 Flyway 已到 v19 后立即重新切换 `fd08695`，改用 HTTP 状态码和 JSON 解析验收，第 6 次探测恢复健康。最终运行容器的镜像 ID 与 `fd08695` 标签完全一致。
- 发布前后核心数据数量一致：`app_user=3`、`work_record=42`、`ledger_book=5`、`ledger_transaction=16460`；发布后已有 `agent_session=5`，新建 `agent_turn` 初始为空。
- 内网和公网首页、`GET /api/health`、`/v3/api-docs` 均返回 HTTP 200；未认证访问 Agent 会话、模型配置、工具列表、账本和工时设置接口均返回 HTTP 401。线上 OpenAPI 已包含 action 查询、补参、批准、拒绝和提交接口。
- 后端精确错误日志扫描未发现 `ERROR`、启动失败或 Flyway 失败；公网桌面浏览器烟测正常，登录页成功加载本次前端资源，未捕获 4xx/5xx 请求。

### 2026-09-27 Agent 账本写入增强生产发布记录

- 应用提交：`d6ef378`，发布目录：`/home/ubuntu/salary-tracker/releases/d6ef378`；后端和前端均在服务器从提交归档构建为 `amd64` 镜像 `salary-backend:d6ef378` 与 `salary-frontend:d6ef378`。
- 发布前完成逻辑备份 `backups/salary-before-d6ef378-20260927-153651.sql.gz`，使用 `mysqldump --no-tablespaces --single-transaction` 并通过 `gzip -t` 校验；旧应用镜像保留为 `salary-backend:pre-d6ef378` 和 `salary-frontend:pre-d6ef378`。
- 发布前已通过后端 Maven 全量测试、真实 MySQL/Testcontainers 集成测试、前端生产构建和同步引擎 8/8 测试。生产仅执行 `up -d --no-deps --force-recreate backend frontend`，MySQL 容器和数据卷未重建。
- Flyway 从 v19 成功应用 `V20__worktime_lunch_snapshot.sql` 至 v20；发布前后核心数据数量一致：`app_user=3`、`work_record=43`、`ledger_book=5`、`ledger_transaction=16469`。
- 健康检查在第 7 次探测成功，后端日志确认 `Started SalaryTrackerApplication`；启动窗口内出现约 10 秒短暂 502，恢复后未再发现 Nginx 5xx、后端 `ERROR`、Flyway 失败或应用启动失败。
- 内网、服务器 IP 和 `jsn1024.cn` 的首页与 `/api/health` 均返回 HTTP 200，公网 `/v3/api-docs` 返回 200，未认证访问 `/api/v1/agent/sessions` 返回预期 HTTP 401；线上 OpenAPI 包含 77 个路径和 167 个 schema。
- 本次发布包含 Agent 账本完整操作卡片、默认值修正、流水与工时修改/删除受控链路、流式滚动稳定性、两小时 access token、午休变更后的历史工时重算、中文评测安全基线和记账实体消歧。回滚应用时恢复 `docker-compose.prod.yml.bak.d6ef378` 并重建 backend/frontend；数据库备份保留用于迁移异常恢复核验。

### 2026-09-27 Agent 工具协议兼容热修复生产发布记录

- 后端提交：`aad8833`，发布目录：`/home/ubuntu/salary-tracker/releases/aad8833`；仅构建并切换 `amd64` 镜像 `salary-backend:aad8833`，前端继续运行 `salary-frontend:d6ef378`，MySQL 容器和数据卷未重建。
- 发布前完成逻辑备份 `backups/salary-before-aad8833-20260927-160231.sql.gz`，使用 `mysqldump --no-tablespaces --single-transaction` 并通过 `gzip -t` 校验；旧后端镜像保留为 `salary-backend:pre-aad8833`，Compose 配置备份为 `docker-compose.prod.yml.bak.aad8833`。
- Flyway 成功校验 22 条迁移，schema 保持 v20 且无需执行新迁移；后端在第 6 次探测恢复健康，启动日志确认 `Started SalaryTrackerApplication`，近 10 分钟未发现 `ERROR`、应用启动失败或 Flyway 失败。
- 服务器内网、服务器 IP 和 `jsn1024.cn` 的 HTTP `/api/health` 均返回 200，公网 `/v3/api-docs` 返回 200，未认证访问 `/api/v1/agent/sessions` 返回预期 401。当前 Compose 只对外暴露 80 端口，未配置 443 HTTPS 入口。
- 本次热修复兼容 DeepSeek 偶发返回的全角双竖线、标签名前空格和关闭标签反斜杠 DSML 变体；普通响应会恢复结构化工具调用，SSE 会过滤协议片段，避免原始工具调用标记显示在聊天消息中。

### 2026-09-27 Agent 工具调用上限调整生产发布记录

- 后端提交：`06acaad`，发布目录：`/home/ubuntu/salary-tracker/releases/06acaad`；仅构建并切换 `amd64` 镜像 `salary-backend:06acaad`，前端继续运行 `salary-frontend:d6ef378`，MySQL 容器和数据卷未重建。
- 发布前完成逻辑备份 `backups/salary-before-06acaad-20260927-161625.sql.gz`，使用 `mysqldump --no-tablespaces --single-transaction` 并通过 `gzip -t` 校验；旧后端镜像保留为 `salary-backend:pre-06acaad`，Compose 配置备份为 `docker-compose.prod.yml.bak.06acaad`。
- Flyway 成功校验 22 条迁移，schema 保持 v20 且无需执行新迁移；后端在第 6 次探测恢复健康，启动日志确认 `Started SalaryTrackerApplication`，近 10 分钟未发现 `ERROR`、应用启动失败或 Flyway 失败。
- 服务器 IP 和 `jsn1024.cn` 的 HTTP `/api/health` 均返回 200，公网 `/v3/api-docs` 返回 200，未认证访问 `/api/v1/agent/sessions` 返回预期 401。
- 本次发布将单轮 Agent 工具调用上限从 4 次提升到 50 次，普通与 SSE 编排使用同一限制；第 51 次调用不会进入领域工具，随后关闭模型工具目录并进入总结轮，既有权限、Schema 和受控写入边界保持不变。

### 2026-09-27 Thinking Mode 多轮工具调用修复生产发布记录

- 后端提交：`fdc3d4d`，发布目录：`/home/ubuntu/salary-tracker/releases/fdc3d4d`；仅构建并切换 `amd64` 镜像 `salary-backend:fdc3d4d`，前端继续运行 `salary-frontend:d6ef378`，MySQL 容器和数据卷未重建。
- 发布前完成逻辑备份 `backups/salary-before-fdc3d4d-20260927-162254.sql.gz`，使用 `mysqldump --no-tablespaces --single-transaction` 并通过 `gzip -t` 校验；旧后端镜像保留为 `salary-backend:pre-fdc3d4d`，Compose 配置备份为 `docker-compose.prod.yml.bak.fdc3d4d`。
- Flyway 成功校验 22 条迁移，schema 保持 v20 且无需执行新迁移；后端在第 6 次探测恢复健康，启动日志确认 `Started SalaryTrackerApplication`，近 10 分钟未发现 `ERROR`、应用启动失败或 Flyway 失败。
- 服务器 IP 和 `jsn1024.cn` 的 HTTP `/api/health` 均返回 200，公网 `/v3/api-docs` 返回 200，未认证访问 `/api/v1/agent/sessions` 返回预期 401。
- 本次修复解析普通与 SSE 响应中的 `reasoning_content`，并在同一 Agent turn 的后续 assistant 消息中原样回传给思考模型，解决 DeepSeek thinking mode 在第二轮工具调用时报错的问题；思考内容不进入页面正文或长期会话历史。

### 2026-09-23 本地 Agent SSE 增量说明

- 本地后端启动会由 Flyway 从 v14 升级至 v15，新增 `agent_turn`、会话归档字段和消息元数据；不得改写或忽略已应用迁移。
- 本轮只刷新本地开发服务，不执行生产部署。升级后应确认日志显示 schema 为 v15，并检查 `/api/v1/agent/sessions` 与流式 turn 接口可用。
- SSE 客户端断开不会终止后台只读 turn；客户端通过 `GET /api/v1/agent/turns/{turnId}` 查询最终状态。若 SSE 在收到首个事件前不可用，首页才降级到普通 `/api/v1/ai/chat`。
- 回滚应用代码前必须确认旧版本是否能容忍 v15 新增表和列；迁移本身只增加结构，不删除现有会话、工时或账本数据。

- 健康检查：`GET /api/health`，成功响应包含 `{"ok": true}`。
- 后端端口由 `PORT` 控制，默认 `8080`；Compose 内部由 Nginx 代理，无需直接暴露。
- 数据库连接由 `DB_URL`、`DB_USER`、`DB_PASSWORD` 控制。
- 除认证、健康检查和节假日接口外，API 使用 JWT Bearer access token；refresh token 由 HttpOnly Cookie 管理。
- 生产环境必须设置高强度 `JWT_SECRET`，HTTPS 下启用 `COOKIE_SECURE=true`。

生产环境请使用高强度数据库密码和管理员初始密码，不要把 `deploy/.env` 提交到版本库。

## HTTP/IP 部署排障记录

本次移动端“服务器拒绝服务”实际不是 80 端口不可达，而是旧生产 Compose 将 `COOKIE_SECURE` 固定为 `true`。站点通过 `http://服务器IP` 访问时，移动端会忽略带 `Secure` 属性的 refresh cookie，日志表现为 `/api/v1/auth/refresh` 连续返回 `401`，刷新后看起来像服务不可用。

排查顺序：

```bash
sudo docker compose -f docker-compose.prod.yml ps
curl -i http://127.0.0.1/api/health
sudo docker logs --tail 100 salary-frontend
sudo docker logs --tail 100 salary-backend
```

HTTP/IP 部署时确认 `deploy/.env` 没有覆盖为 `COOKIE_SECURE=true`，并重新创建后端容器使环境变量生效：

```bash
sudo docker compose --env-file .env -f docker-compose.prod.yml up -d --no-deps backend frontend
```

若切换到 HTTPS，再设置 `COOKIE_SECURE=true` 并确保反代传递 HTTPS；不要在明文 HTTP 站点启用该选项。
