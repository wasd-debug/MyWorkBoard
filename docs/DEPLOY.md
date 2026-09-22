# 部署说明

> 状态日期：2026-09-18
> SSH 规则：所有连接必须使用仓库根目录的 `workboard.pem`，禁止密码认证。

本项目通过 Docker Compose 运行三个服务：MySQL、Spring Boot 后端和 Nginx 前端。前端对外提供 80 端口，并将 `/api` 请求转发到后端。

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
   - `LEGACY_ADMIN_PASSWORD`：旧数据回填的 `admin` 账户初始密码。
   - `COOKIE_SECURE`：仅 HTTPS 生产环境设为 `true`；通过服务器 IP + HTTP 访问时必须为 `false`，否则浏览器不会发送 refresh cookie，刷新页面会反复回到登录页。

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
