#!/usr/bin/env bash
# 真实时薪 · Java + Vue + MySQL Docker 一键部署脚本（服务器端执行）
# 用法：root 或 sudo 执行  bash deploy/deploy.sh
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$APP_DIR"

echo "==> [1/4] 检查 Docker"
if ! command -v docker >/dev/null 2>&1; then
  echo "未安装 Docker，正在安装（腾讯云镜像源）..."
  if command -v apt-get >/dev/null 2>&1; then
    curl -fsSL https://mirrors.cloud.tencent.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.cloud.tencent.com/docker-ce/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
    sudo apt-get update -qq
    sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  else
    echo "✗ 暂不支持自动安装，请手动安装 Docker 后重试"; exit 1
  fi
fi
command -v docker compose >/dev/null 2>&1 || { echo "✗ 缺少 docker compose 插件"; exit 1; }

echo "==> [2/4] 开启 Docker 开机自启"
sudo systemctl enable docker >/dev/null 2>&1 || true
sudo systemctl start docker

echo "==> [3/4] 检查 80 端口占用"
if sudo lsof -i :80 -sTCP:LISTEN -t >/dev/null 2>&1; then
  echo "⚠️ 端口 80 已被占用，请先释放（如 systemctl stop nginx）后再部署"
  sudo lsof -i :80 -sTCP:LISTEN || true
  exit 1
fi

echo "==> [4/4] 构建并启动容器"
if [ ! -f deploy/.env ]; then
  cp deploy/.env.example deploy/.env
  echo "  已生成 deploy/.env（默认密码，生产环境请修改）"
fi
sudo docker compose -f deploy/docker-compose.yml up -d --build

echo "==> 验证"
sleep 8
curl -sf "http://127.0.0.1:80/api/health" >/dev/null \
  && echo "✓ 后端健康检查通过" \
  || { echo "✗ 健康检查失败，日志："; sudo docker compose -f deploy/docker-compose.yml logs --tail 40; exit 1; }

IP=$(curl -sf --max-time 3 https://ifconfig.me 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || echo "<服务器IP>")
echo
echo "════════════════════════════════════════════════"
echo "  部署完成！"
echo "  访问地址   http://${IP}  (前端 80 端口)"
echo "  数据       MySQL 容器 salary-mysql（数据卷 mysql-data 持久化）"
echo "  常用命令   sudo docker compose -f deploy/docker-compose.yml ps|logs -f backend|restart"
echo "  开机自启   docker（systemd）+ 容器（restart: always）均已开启"
echo "  别忘了在腾讯云控制台安全组放行 TCP 80 端口"
echo "════════════════════════════════════════════════"
