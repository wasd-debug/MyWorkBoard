# 部署说明

本项目通过 Docker Compose 运行三个服务：MySQL、Spring Boot 后端和 Nginx 前端。前端对外提供 80 端口，并将 `/api` 请求转发到后端。

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
   - `ACCESS_CODE`：可选的 API 访问口令；留空表示不启用。

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
- `ACCESS_CODE` 启用后，前端和其他客户端调用受保护 API 时要发送 `X-Access-Code`。

生产环境请使用高强度密码和访问口令，不要把 `deploy/.env` 提交到版本库。
