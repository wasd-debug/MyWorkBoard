#!/usr/bin/env bash
# 本地打包部署包（排除依赖与构建产物，体积小、可直接 scp 到服务器）
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="salary-tracker-deploy-$(date +%Y%m%d-%H%M).tar.gz"

paths=(backend frontend deploy README.md ARCHITECTURE.md DEPLOY.md overview.md)
existing=()
for path in "${paths[@]}"; do
    [ -e "$path" ] && existing+=("$path")
done

tar --exclude='./frontend/node_modules' \
    --exclude='./frontend/dist' \
    --exclude='./frontend/node_modules/.npm-cache' \
    --exclude='./backend/target' \
    --exclude='./.git' \
    --exclude='.DS_Store' \
    -czf "$PKG" "${existing[@]}"

echo "打包完成: $(pwd)/$PKG"
ls -lh "$PKG" | awk '{print $5, $9}'
