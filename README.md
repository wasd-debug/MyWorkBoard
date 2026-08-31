# 真实时薪 · 加班追踪

记录每天真实上下班时间，自动计算加班时长与实际时薪（周报 / 月报）。

## 技术栈（v2 重构版）

| 层 | 技术 | 说明 |
|---|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router | 打卡 / 记录 / 统计 / 设置 四页面 |
| 后端 | Java 17 + Spring Boot 3 + MyBatis-Plus | REST API，与旧版 Flask 接口完全兼容 |
| 数据库 | MySQL 8 | 数据卷持久化；旧版 SQLite 数据自动迁移 |
| 部署 | Docker Compose | mysql + backend + frontend 三容器，前端暴露 80 端口 |

```
浏览器 → [nginx 容器 :80]（Vue 静态页面 + /api 反向代理）
             │
             └──→ [backend 容器 :8080]（Spring Boot）
                          │
                          └──→ [mysql 容器 :3306]（数据卷 mysql-data）
```

## 目录结构

```
salary-sync/
├── backend/            # Spring Boot 后端（Java 17 + Maven）
├── frontend/           # Vue 3 前端
├── deploy/             # Docker 部署包
│   ├── docker-compose.yml
│   ├── Dockerfile.backend / Dockerfile.frontend
│   ├── nginx.conf      # 前端容器配置（listen 80 + /api 反代）
│   ├── mysql-init/     # MySQL 首次启动自动建表 + 迁移数据
│   ├── migrate/        # SQLite → MySQL 迁移工具
│   └── deploy.sh       # 服务器端一键部署脚本
├── data/               # 旧版 SQLite 数据快照（salary-latest.db）
└── legacy/             # 旧版 Python/单文件前端（存档）
```

## 本地开发

```bash
# 后端（需本地 MySQL，或直接 docker compose 起 mysql）
cd backend && mvn spring-boot:run        # http://localhost:8080

# 前端（Vite 代理 /api → 8080）
cd frontend && npm install && npm run dev  # http://localhost:5173
```

## Docker 一键部署（腾讯云）

在服务器上执行（项目目录为 /opt/salary-tracker）：

```bash
sudo bash deploy/deploy.sh
```

脚本自动：检查/安装 Docker → 开机自启 → 构建镜像 → 启动三容器 → 健康检查。
部署完成后访问 `http://<服务器公网IP>`（80 端口）。

**别忘了在腾讯云控制台 → 安全组，放行 TCP 80 端口。**

可选配置（`deploy/.env`）：

- `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` — 数据库密码（生产环境务必修改）
- `ACCESS_CODE=口令` — 开启访问保护：所有设备打开应用需先输口令

常用运维命令：

```bash
sudo docker compose -f deploy/docker-compose.yml ps                 # 容器状态
sudo docker compose -f deploy/docker-compose.yml logs -f backend   # 后端日志
sudo docker compose -f deploy/docker-compose.yml restart           # 重启全部
sudo docker compose -f deploy/docker-compose.yml down              # 停止（数据保留在数据卷）
```

## 数据迁移（SQLite → MySQL）

`deploy/migrate/sqlite_to_mysql.py` 将旧版 `data/salary-latest.db` 转为
`deploy/mysql-init/02-data.sql`；MySQL 容器**首次创建**时通过
`docker-entrypoint-initdb.d` 自动建表并导入全部历史数据（设置 + 打卡记录）。

> 注意：`mysql-init/02-data.sql` 只在数据卷首次创建时执行。若数据卷已存在，
> 需手动执行 `02-data.sql` 或删除旧数据卷后重新部署。

## API 说明

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/health` | GET | 健康检查 |
| `/api/data` | GET | 读整体数据快照 `{settings, records}` |
| `/api/data` | PUT | 写整体数据快照（整体替换） |

可选：设置 `ACCESS_CODE` 后，除健康检查外所有接口需带 `X-Access-Code` 请求头。

## 页面功能

| 页面 | 功能 |
|------|------|
| 打卡 | 填实际上下班时间 + 当日自定义休息 → 当日实际时薪、加班时长、日薪 |
| 记录 | 按月查看/编辑/删除每日记录（含休息时长），月度汇总 |
| 统计 | 本周/本月：总工时、加班、本来时薪 vs 实际时薪、柱状图、加班预测 |
| 设置 | 标准上下班时间、午休扣除、每月排班天数、税前/税后月薪、数据导出导入 |

## 计算口径

```
基准时薪 = 月薪 ÷ 每月排班天数 ÷ 每日标准工时
当日实际时薪 = 日薪 ÷ 当日实际工时        # 加班越多，时薪越低
当日实际工时 = 下班 - 上班 - 午休 - 自定义休息
周期实际时薪 = (打卡天数 × 日薪) ÷ 周期总工时
```

- 顶栏一键切换 **税前 / 税后** 口径
- 支持跨零点下班（夜班/加班到次日凌晨）；早退记为负加班
- **自定义休息**：打卡页可填当日除午休外的休息分钟数，不区分类型，直接从实际工时扣除
