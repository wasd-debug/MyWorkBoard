#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.dev-db.yml"
SYNC_PRODUCTION=false
SKIP_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --sync-production) SYNC_PRODUCTION=true ;;
    --skip-build) SKIP_BUILD=true ;;
    -h|--help)
      echo "用法: bash deploy/dev-backend.sh [--sync-production] [--skip-build]"
      echo "  --sync-production  从生产服务器拉取快照并覆盖本地开发库"
      echo "  --skip-build       跳过 Maven 打包，直接运行现有 JAR"
      exit 0
      ;;
    *) echo "未知参数: $arg"; exit 2 ;;
  esac
done

command -v docker >/dev/null 2>&1 || { echo "缺少 Docker，请先启动 Docker Desktop。"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "缺少 Java 17。"; exit 1; }

docker compose -p salary-workspace-dev -f "$COMPOSE_FILE" up -d mysql
echo "等待本地 MySQL 就绪..."
for _ in {1..60}; do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' salary-mysql-dev 2>/dev/null || true)" = "healthy" ]; then
    break
  fi
  sleep 2
done
test "$(docker inspect -f '{{.State.Health.Status}}' salary-mysql-dev 2>/dev/null || true)" = "healthy" || { echo "本地 MySQL 启动超时。"; exit 1; }

if [ "$SYNC_PRODUCTION" = true ]; then
  "$SCRIPT_DIR/sync-prod-db.sh"
fi

if [ "$SKIP_BUILD" = false ]; then
  command -v mvn >/dev/null 2>&1 || { echo "缺少 Maven。"; exit 1; }
  mvn -f "$PROJECT_DIR/backend/pom.xml" -DskipTests package
fi

echo "启动后端：http://127.0.0.1:8080（Ctrl+C 仅停止后端，本地 MySQL 继续运行）"
exec env \
  PORT=8080 \
  DB_URL="jdbc:mysql://127.0.0.1:${DEV_MYSQL_PORT:-3307}/salary?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  DB_USER=salary \
  DB_PASSWORD=SalaryDevApp@2026 \
  JWT_SECRET=local-development-secret-change-me-32-bytes \
  COOKIE_SECURE=false \
  java -jar "$PROJECT_DIR/backend/target/salary-tracker-backend.jar"

