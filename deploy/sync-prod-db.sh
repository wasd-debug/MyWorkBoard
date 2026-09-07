#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.dev-db.yml"
PROD_SSH_HOST="${PROD_SSH_HOST:-212.64.29.21}"
PROD_SSH_USER="${PROD_SSH_USER:-ubuntu}"
SNAPSHOT_DIR="$PROJECT_DIR/.local/db"
SNAPSHOT_FILE="$SNAPSHOT_DIR/production-latest.sql.gz"
TEMP_FILE="$SNAPSHOT_FILE.part"

command -v docker >/dev/null 2>&1 || { echo "缺少 Docker，请先启动 Docker Desktop。"; exit 1; }
command -v ssh >/dev/null 2>&1 || { echo "缺少 ssh 命令。"; exit 1; }
command -v gzip >/dev/null 2>&1 || { echo "缺少 gzip 命令。"; exit 1; }

mkdir -p "$SNAPSHOT_DIR"
trap 'rm -f "$TEMP_FILE"' EXIT

echo "从 ${PROD_SSH_USER}@${PROD_SSH_HOST} 只读导出生产数据库，SSH 密码将交互输入。"
ssh -o StrictHostKeyChecking=accept-new "${PROD_SSH_USER}@${PROD_SSH_HOST}" \
  "sudo docker exec salary-mysql sh -c 'MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" exec mysqldump --single-transaction --quick --routines --triggers --events --no-tablespaces -uroot salary' | gzip -1" \
  > "$TEMP_FILE"

test -s "$TEMP_FILE" || { echo "生产数据库导出为空，已停止。"; exit 1; }
mv "$TEMP_FILE" "$SNAPSHOT_FILE"

docker compose -p salary-workspace-dev -f "$COMPOSE_FILE" up -d mysql
echo "等待本地 MySQL 就绪..."
for _ in {1..60}; do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' salary-mysql-dev 2>/dev/null || true)" = "healthy" ]; then
    break
  fi
  sleep 2
done
test "$(docker inspect -f '{{.State.Health.Status}}' salary-mysql-dev 2>/dev/null || true)" = "healthy" || { echo "本地 MySQL 启动超时。"; exit 1; }

echo "用生产快照覆盖本地 salary 开发库..."
docker exec salary-mysql-dev sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS salary; CREATE DATABASE salary CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON salary.* TO salary@\"%\"; FLUSH PRIVILEGES;"'
gzip -dc "$SNAPSHOT_FILE" | docker exec -i salary-mysql-dev sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot salary'

echo "同步完成：本地 MySQL 127.0.0.1:${DEV_MYSQL_PORT:-3307}，快照保存在 .local/db（已被 Git 忽略）。"

