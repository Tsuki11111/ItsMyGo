#!/usr/bin/env bash
# ItsMyGo 全栈一键启动（Docker Compose）
# 用法：./scripts/docker-up.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "已生成 .env（compose 有安全默认值，可直接启动）。"
fi

docker compose up -d --build

echo
echo "ItsMyGo 已启动："
echo "  前端    http://localhost:${FRONTEND_PORT:-8088}"
echo "  后端    http://localhost:${BACKEND_PORT:-8080}/api/health"
echo "  MinIO   http://localhost:${MINIO_CONSOLE_PORT:-9001}"
echo "  RabbitMQ 管理台 http://localhost:${RABBITMQ_CONSOLE_PORT:-15672}"
echo
echo "首次使用还没有管理员："
echo "  在 .env 配 BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD 后执行"
echo "  docker compose up -d backend"
echo
echo "可选导入演示数据（44 个商品，等 backend 建完表后执行）："
echo "  docker compose --profile seed run --rm seed"
