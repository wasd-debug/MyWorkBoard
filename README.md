# 真实时薪 · 加班追踪

记录每天的实际上下班时间，计算工作时长、加班时长、工资和实际时薪，并按周、月或自定义时间段查看统计结果。

## 功能

- **打卡**：记录上班、下班和额外休息时间，实时查看当天工时、加班和实际时薪。
- **记录**：按月浏览、编辑、删除打卡记录，并按月设置税前、税后工资。
- **统计**：查看本周、本月、本年、上周、上月、去年或自定义时间段的工时、加班、工资和时薪趋势。
- **设置**：配置标准工作时间、午休扣除、排班天数和统计口径。
- **节假日**：读取法定节假日与调休信息，区分工作日和休息日加班。
- **数据管理**：支持 JSON 导出、导入和清空；应用优先使用浏览器本地缓存，连接后端后会同步到 MySQL。
- **主题与访问保护**：支持明暗主题；部署时可通过访问口令保护 API。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts |
| 后端 | Java 17、Spring Boot 3.2、MyBatis-Plus |
| 数据库 | MySQL 8 |
| 部署 | Docker Compose、Nginx |

运行时请求关系：

```text
浏览器 → Nginx:80（Vue 静态页面和 /api 反向代理）
             └→ Spring Boot:8080 → MySQL:3306
```

## 项目结构

```text
salary-sync/
├── backend/                 # Spring Boot 后端和数据库初始化脚本
├── frontend/                # Vue 前端
└── deploy/                  # Docker、Compose、Nginx 和部署脚本
    ├── docker-compose.yml   # 从源码构建并启动完整服务
    ├── docker-compose.local.yml  # 使用本地构建产物联调
    ├── docker-compose.prod.yml   # 使用已构建镜像运行
    ├── Dockerfile.backend
    ├── Dockerfile.frontend
    ├── nginx.conf
    ├── mysql-init/           # MySQL 初始化 SQL
    └── deploy.sh             # Linux 服务器一键部署
```

## 本地开发

### 方式一：分别启动前后端

准备 Java 17、Maven、Node.js 20+ 和 MySQL 8，并创建名为 `salary` 的数据库及可访问账号。后端会在启动时执行 `backend/src/main/resources/schema.sql`。

启动后端：

```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。如需使用其他数据库，可通过环境变量覆盖连接信息：

```bash
export DB_URL='jdbc:mysql://localhost:3306/salary?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export DB_USER='salary'
export DB_PASSWORD='你的数据库密码'
mvn spring-boot:run
```

启动前端（Vite 已配置 `/api` 代理到 `localhost:8080`）：

```bash
cd frontend
npm install
npm run dev
```

开发页面默认地址为 `http://localhost:5173`。只启动前端也可以使用本地模式，数据保存在当前浏览器中。

### 方式二：Docker Compose

在项目根目录执行：

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env，至少修改数据库密码
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

服务启动后访问 `http://localhost`。常用命令：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f backend
docker compose --env-file deploy/.env -f deploy/docker-compose.yml restart
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

MySQL 数据保存在 Compose 卷 `mysql-data` 中。数据库初始化脚本只会在卷首次创建时执行。

## 服务器部署

Linux 服务器安装 Docker Engine 和 Compose 插件后，在项目目录执行：

```bash
sudo bash deploy/deploy.sh
```

脚本会检查 Docker、构建前后端镜像、启动 MySQL/后端/前端容器，并请求 `/api/health` 进行健康检查。部署前请编辑 `deploy/.env` 设置数据库密码；如需访问保护，设置 `ACCESS_CODE`。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 返回服务健康状态 |
| GET | `/api/data` | 获取 `{settings, records}` 数据快照 |
| PUT | `/api/data` | 覆盖保存数据快照 |
| GET | `/api/holidays?year=2026` | 获取指定年份的节假日与调休数据 |

设置 `ACCESS_CODE` 后，除 `/api/health` 外的请求都必须携带 `X-Access-Code` 请求头。前端会在首次收到 401 响应时提示输入口令，并将其保存在当前浏览器中。

## 计算口径

```text
标准工时 = 标准下班时间 - 标准上班时间 - 午休扣除
实际工时 = 下班时间 - 上班时间 - 午休扣除 - 自定义休息
基准时薪 = 月薪 ÷ 当月排班天数 ÷ 标准工时
日薪 = 月薪 ÷ 当月排班天数
实际时薪 = 日薪 ÷ 实际工时
```

- 支持跨午夜下班；早退会产生负加班时长。
- 自动排班模式按节假日和调休计算当月工作日，并计入休息日打卡。
- 顶栏可以切换税前或税后口径；月薪可按月份单独调整。

## 构建检查

```bash
cd backend && mvn test
cd ../frontend && npm run build
```
