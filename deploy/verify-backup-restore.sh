#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MYSQL_CONTAINER=""
SOURCE_DATABASE=""
TARGET_DATABASE=""
BACKUP_OUTPUT=""
DB_HOST="127.0.0.1"
DB_PORT="3307"
HEALTH_PORT="18081"
BACKEND_JAR="$PROJECT_DIR/backend/target/salary-tracker-backend.jar"
KEEP_DATABASE=false

usage() {
  cat <<'EOF'
用法：
  VERIFY_DB_USER=salary VERIFY_DB_PASSWORD=... bash deploy/verify-backup-restore.sh \
    --container salary-mysql-dev \
    --source-database salary \
    --target-database salary_restore_verify_20260917 \
    --backup-output /tmp/salary-restore-20260917.sql.gz \
    [--db-port 3307] [--health-port 18081] [--backend-jar PATH] [--keep-database]

安全约束：目标库必须显式命名为 salary_restore_verify_*；数据库地址只允许本机。
脚本只读导出源库，目标临时库默认在演练结束后删除。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --container) MYSQL_CONTAINER="${2:-}"; shift 2 ;;
    --source-database) SOURCE_DATABASE="${2:-}"; shift 2 ;;
    --target-database) TARGET_DATABASE="${2:-}"; shift 2 ;;
    --backup-output) BACKUP_OUTPUT="${2:-}"; shift 2 ;;
    --db-host) DB_HOST="${2:-}"; shift 2 ;;
    --db-port) DB_PORT="${2:-}"; shift 2 ;;
    --health-port) HEALTH_PORT="${2:-}"; shift 2 ;;
    --backend-jar) BACKEND_JAR="${2:-}"; shift 2 ;;
    --keep-database) KEEP_DATABASE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$MYSQL_CONTAINER" && -n "$SOURCE_DATABASE" && -n "$TARGET_DATABASE" && -n "$BACKUP_OUTPUT" ]] || {
  echo "必须显式提供 container、source-database、target-database 和 backup-output。" >&2
  exit 2
}
[[ "$MYSQL_CONTAINER" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "容器名格式不安全。" >&2; exit 2; }
[[ "$SOURCE_DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || { echo "源库名格式不安全。" >&2; exit 2; }
[[ "$TARGET_DATABASE" =~ ^salary_restore_verify_[A-Za-z0-9_]+$ ]] || {
  echo "拒绝执行：目标库必须使用 salary_restore_verify_* 临时库名。" >&2
  exit 2
}
[[ "$SOURCE_DATABASE" != "$TARGET_DATABASE" ]] || { echo "源库与目标库不能相同。" >&2; exit 2; }
[[ "$DB_HOST" == "127.0.0.1" || "$DB_HOST" == "localhost" ]] || {
  echo "拒绝执行：恢复演练只允许连接本机数据库地址。" >&2
  exit 2
}
[[ "$DB_PORT" =~ ^[0-9]+$ && "$HEALTH_PORT" =~ ^[0-9]+$ ]] || { echo "端口必须为数字。" >&2; exit 2; }
[[ "$BACKUP_OUTPUT" == *.sql.gz ]] || { echo "backup-output 必须以 .sql.gz 结尾。" >&2; exit 2; }
[[ ! -e "$BACKUP_OUTPUT" ]] || { echo "备份文件已存在，拒绝覆盖：$BACKUP_OUTPUT" >&2; exit 2; }

: "${VERIFY_DB_USER:?必须设置 VERIFY_DB_USER}"
: "${VERIFY_DB_PASSWORD:?必须设置 VERIFY_DB_PASSWORD}"
[[ "$VERIFY_DB_USER" =~ ^[A-Za-z0-9_]+$ ]] || { echo "VERIFY_DB_USER 格式不安全。" >&2; exit 2; }

command -v docker >/dev/null 2>&1 || { echo "缺少 docker。" >&2; exit 1; }
command -v gzip >/dev/null 2>&1 || { echo "缺少 gzip。" >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "缺少 Java 17。" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "缺少 curl。" >&2; exit 1; }
[[ -f "$BACKEND_JAR" ]] || { echo "后端 JAR 不存在，请先运行：cd backend && mvn package" >&2; exit 1; }
docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1 || { echo "MySQL 容器不存在：$MYSQL_CONTAINER" >&2; exit 1; }

mkdir -p "$(dirname "$BACKUP_OUTPUT")"
TMP_DIR="$(mktemp -d)"
BACKEND_PID=""
TARGET_CREATED=false

root_sql() {
  local sql="$1"
  docker exec "$MYSQL_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot -e "$1"' sh "$sql"
}

database_count() {
  local database="$1" table="$2"
  local exists
  exists="$(root_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${database}' AND table_name='${table}'")"
  if [[ "$exists" == "0" ]]; then
    echo "MISSING"
  else
    root_sql "SELECT COUNT(*) FROM \`${database}\`.\`${table}\`"
  fi
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$BACKEND_PID" ]]; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
    wait "$BACKEND_PID" >/dev/null 2>&1 || true
  fi
  if [[ "$TARGET_CREATED" == true && "$KEEP_DATABASE" == false ]]; then
    root_sql "DROP DATABASE IF EXISTS \`${TARGET_DATABASE}\`" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_DIR"
  exit "$status"
}
trap cleanup EXIT INT TERM

echo "[1/6] 只读导出 ${MYSQL_CONTAINER}/${SOURCE_DATABASE}"
docker exec "$MYSQL_CONTAINER" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --single-transaction --quick --routines --triggers --events --no-tablespaces -uroot "$1"' \
  sh "$SOURCE_DATABASE" | gzip -1 > "$BACKUP_OUTPUT"
gzip -t "$BACKUP_OUTPUT"
[[ -s "$BACKUP_OUTPUT" ]] || { echo "备份文件为空。" >&2; exit 1; }

CORE_TABLES=(app_user work_record ledger_book ledger_transaction)
for table in "${CORE_TABLES[@]}"; do
  database_count "$SOURCE_DATABASE" "$table" > "$TMP_DIR/source-$table.count"
done

echo "[2/6] 创建并恢复临时库 ${TARGET_DATABASE}"
root_sql "DROP DATABASE IF EXISTS \`${TARGET_DATABASE}\`; CREATE DATABASE \`${TARGET_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON \`${TARGET_DATABASE}\`.* TO '${VERIFY_DB_USER}'@'%'; FLUSH PRIVILEGES;"
TARGET_CREATED=true
gzip -dc "$BACKUP_OUTPUT" | docker exec -i "$MYSQL_CONTAINER" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' sh "$TARGET_DATABASE"

echo "[3/6] 对账核心数据量"
for table in "${CORE_TABLES[@]}"; do
  source_count="$(<"$TMP_DIR/source-$table.count")"
  target_count="$(database_count "$TARGET_DATABASE" "$table")"
  [[ "$source_count" == "$target_count" ]] || {
    echo "数据对账失败：$table source=$source_count target=$target_count" >&2
    exit 1
  }
  echo "  $table=$target_count"
done

echo "[4/6] 启动临时后端并执行 Flyway migrate/validate"
VERIFY_JWT_SECRET="${VERIFY_JWT_SECRET:-restore-verification-only-secret-32-bytes}"
env \
  PORT="$HEALTH_PORT" \
  DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${TARGET_DATABASE}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  DB_USER="$VERIFY_DB_USER" \
  DB_PASSWORD="$VERIFY_DB_PASSWORD" \
  JWT_SECRET="$VERIFY_JWT_SECRET" \
  COOKIE_SECURE=false \
  java -jar "$BACKEND_JAR" --spring.flyway.validate-on-migrate=true > "$TMP_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "[5/6] 等待健康检查"
healthy=false
for _ in {1..90}; do
  if response="$(curl -fsS "http://127.0.0.1:${HEALTH_PORT}/api/health" 2>/dev/null)" && \
      grep -Eq '"ok"[[:space:]]*:[[:space:]]*true' <<<"$response"; then
    healthy=true
    break
  fi
  if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    echo "临时后端提前退出：" >&2
    tail -80 "$TMP_DIR/backend.log" >&2
    exit 1
  fi
  sleep 1
done
[[ "$healthy" == true ]] || { echo "健康检查超时。" >&2; tail -80 "$TMP_DIR/backend.log" >&2; exit 1; }

echo "[6/6] 校验 Flyway 与迁移后数据"
failed_migrations="$(root_sql "SELECT COUNT(*) FROM \`${TARGET_DATABASE}\`.flyway_schema_history WHERE success=0")"
[[ "$failed_migrations" == "0" ]] || { echo "发现失败的 Flyway migration。" >&2; exit 1; }
current_version="$(root_sql "SELECT version FROM \`${TARGET_DATABASE}\`.flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"
for table in "${CORE_TABLES[@]}"; do
  source_count="$(<"$TMP_DIR/source-$table.count")"
  target_count="$(database_count "$TARGET_DATABASE" "$table")"
  [[ "$source_count" == "$target_count" ]] || {
    echo "迁移后数据对账失败：$table source=$source_count target=$target_count" >&2
    exit 1
  }
done

echo "恢复演练通过：backup=$BACKUP_OUTPUT flyway=$current_version health=http://127.0.0.1:${HEALTH_PORT}/api/health"
if [[ "$KEEP_DATABASE" == true ]]; then
  echo "已按要求保留临时库：$TARGET_DATABASE"
else
  echo "临时库将在退出时删除：$TARGET_DATABASE"
fi
