# 部署说明

本项目通过 Docker Compose 运行三个服务：MySQL、Spring Boot 后端和 Nginx 前端。前端对外提供 80 端口，并将 `/api` 请求转发到后端。

## 已配置服务器

- 主机：`212.64.29.21`
- SSH 用户：`ubuntu`
- SSH 密码：仅保存在用户的密码管理器或会话密钥中，不写入仓库、提交记录或部署包。

部署时通过交互式 SSH 输入密码，或使用本机 SSH 私钥；不要把密码写入命令行、脚本、日志或此文档。

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
scp salary-frontend-amd64.tar.gz <user>@<server>:/tmp/
ssh <user>@<server> 'gunzip -c /tmp/salary-frontend-amd64.tar.gz | sudo docker load'
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
