# 真实时薪 · 加班追踪 — 部署说明

应用已部署至 **http://212.64.29.21/**（腾讯云 Ubuntu 24.04 CVM，前端 80 端口）。

## 架构

- **后端** Spring Boot 3 + Java 17 + MyBatis-Plus，容器 `salary-backend`
- **前端** Vue 3 + Element Plus，nginx 容器 `salary-frontend`，宿主机 **80 端口**
- **数据库** MySQL 8，容器 `salary-mysql`（首次启动自动建表 + 导入 SQLite 迁移数据）

## 服务器位置

```
~/salary-tracker/
├── docker-compose.prod.yml   # 生产 compose（使用预构建镜像）
├── mysql-init/                # MySQL 启动初始化（建表 + 数据导入）
├── salary-config.tar.gz      # 上传的配置包
└── salary-images-amd64.tar.gz # amd64 镜像包
```

## 常用运维命令

```bash
# 查看容器状态
sudo docker ps -a

# 查看日志
sudo docker logs -f salary-backend
sudo docker logs -f salary-frontend

# 重启服务
cd ~/salary-tracker && sudo docker compose -f docker-compose.prod.yml restart

# 停止/启动整个栈
cd ~/salary-tracker && sudo docker compose -f docker-compose.prod.yml [down|up -d]

# 进入 MySQL 客户端
sudo docker exec -it salary-mysql mysql -usalary -pSalaryApp@2026 salary
```

## 重要文件

| 文件 | 用途 |
|---|---|
| `deploy/docker-compose.prod.yml` | 生产环境 compose |
| `deploy/mysql-init/01-schema.sql` | MySQL 表结构 |
| `deploy/mysql-init/02-data.sql` | 从旧 SQLite 迁移的数据 |
| `deploy/migrate/sqlite_to_mysql.py` | 旧 SQLite → MySQL 转换工具 |
| `deploy/build/Dockerfile.backend` | 后端镜像构建文件（jre + jar） |
| `deploy/build/Dockerfile.frontend` | 前端镜像构建文件（nginx + dist） |
| `deploy/nginx.conf` | 前端 nginx 配置（80 端口 + /api 反代） |

## 更新与回滚

### 推送新代码

```bash
# 本地（Mac M 系列必须用 --platform linux/amd64）
cd "/Users/jianshengnan/Documents/Projects/WorkBuddy/个人工作台/salary-sync"
mvn -DskipTests package
cd frontend && npm run build && cd ..
docker build --platform linux/amd64 -f deploy/build/Dockerfile.backend -t salary-backend:latest .
docker build --platform linux/amd64 -f deploy/build/Dockerfile.frontend -t salary-frontend:latest .
docker save salary-backend:latest salary-frontend:latest | gzip > /tmp/salary-images-amd64.tar.gz
scp /tmp/salary-images-amd64.tar.gz ubuntu@212.64.29.21:~/salary-tracker/

# 服务器
cd ~/salary-tracker
sudo docker load -i salary-images-amd64.tar.gz
sudo docker compose -f docker-compose.prod.yml up -d
```

### 回滚到旧版 Python

```bash
sudo docker stop salary-frontend salary-backend salary-mysql
sudo docker rm salary-frontend salary-backend salary-mysql
# 旧版启动（保留镜像 salary-tracker-frontend / salary-tracker-backend）
cd /path/to/old/salary-tracker && sudo docker compose up -d
# 旧版监听 8787 端口
```

## 安全建议（公网部署）

- 已开启腾讯云安全组 80 端口
- 数据库密码为默认值 `SalaryRoot@2026` / `SalaryApp@2026`，**生产环境务必修改** `~/salary-tracker/.env`
- 可选：设置 `ACCESS_CODE` 启用 API 访问口令（前端会弹出输入框）
- 旧版 8787 端口建议在腾讯云安全组关闭
