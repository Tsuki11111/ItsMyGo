#!/usr/bin/env bash
# ItsMyGo 一键部署（Docker Compose）
#
#   ./deploy.sh              启动全栈（首次自动生成 .env / 管理员 / 演示数据）
#   ./deploy.sh up --no-seed 启动但不导入演示数据
#   ./deploy.sh down         停止（保留数据）
#   ./deploy.sh logs         跟踪后端日志
#   ./deploy.sh status       查看容器状态
#   ./deploy.sh seed         重新导入 44 个演示商品
#   ./deploy.sh reset        停止并删除所有数据卷（需输入 yes）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

usage() {
  sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

need_docker() {
  command -v docker >/dev/null 2>&1 || { echo "未安装 Docker，请先安装 Docker Desktop。"; exit 1; }
  docker info >/dev/null 2>&1 || { echo "Docker Desktop 未启动，请先启动它。"; exit 1; }
}

rand_hex() {
  local bytes="${1:-24}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$bytes"
  else
    od -An -tx1 -N "$bytes" /dev/urandom | tr -d ' \n'
  fi
}

env_get() {
  [ -f .env ] || return 0
  grep -E "^[[:space:]]*$1=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '\r'
}

env_set() {
  local key="$1" value="$2"
  if grep -qE "^[[:space:]]*${key}=" .env 2>/dev/null; then
    sed -i "s|^[[:space:]]*${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

ADMIN_PASSWORD_NEW=""

ensure_env() {
  if [ ! -f .env ]; then
    cp .env.example .env
    echo "已生成 .env（密码和密钥会自动生成，不需要手填）。"
  fi

  [ -n "$(env_get JWT_SECRET)" ] || env_set JWT_SECRET "$(rand_hex 48)"
  [ -n "$(env_get DB_USERNAME)" ] || env_set DB_USERNAME itsmygo
  [ -n "$(env_get DB_PASSWORD)" ] || env_set DB_PASSWORD "$(rand_hex 24)"
  [ -n "$(env_get DB_NAME)" ] || env_set DB_NAME its_mygo
  [ -n "$(env_get MYSQL_ROOT_PASSWORD)" ] || env_set MYSQL_ROOT_PASSWORD "$(rand_hex 24)"
  [ -n "$(env_get RABBITMQ_USERNAME)" ] || env_set RABBITMQ_USERNAME itsmygo
  [ -n "$(env_get RABBITMQ_PASSWORD)" ] || env_set RABBITMQ_PASSWORD "$(rand_hex 24)"
  [ -n "$(env_get MINIO_ACCESS_KEY)" ] || env_set MINIO_ACCESS_KEY "itsmygo$(rand_hex 4)"
  [ -n "$(env_get MINIO_SECRET_KEY)" ] || env_set MINIO_SECRET_KEY "$(rand_hex 24)"

  if [ -z "$(env_get MINIO_PUBLIC_ENDPOINT)" ]; then
    local minio_port
    minio_port="$(env_get MINIO_PORT)"
    env_set MINIO_PUBLIC_ENDPOINT "http://localhost:${minio_port:-9000}"
  fi

  [ -n "$(env_get BOOTSTRAP_ADMIN_USERNAME)" ] || env_set BOOTSTRAP_ADMIN_USERNAME admin
  if [ -z "$(env_get BOOTSTRAP_ADMIN_PASSWORD)" ]; then
    ADMIN_PASSWORD_NEW="Admin$(rand_hex 8)"
    env_set BOOTSTRAP_ADMIN_PASSWORD "$ADMIN_PASSWORD_NEW"
  fi
}

port_of() {
  local value
  value="$(env_get "$1")"
  printf '%s' "${value:-$2}"
}

wait_backend() {
  local port
  port="$(port_of BACKEND_PORT 8080)"
  echo "等待后端就绪..."
  for _ in $(seq 1 90); do
    if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "后端启动超时。查看日志：./deploy.sh logs"
  exit 1
}

import_seed_if_empty() {
  [ "$NO_SEED" = "1" ] && return 0
  local db_name count
  db_name="$(env_get DB_NAME)"
  db_name="${db_name:-its_mygo}"
  count="$(docker compose exec -T mysql sh -c \
    "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --default-character-set=utf8mb4 -N -e 'select count(*) from ${db_name}.products'" \
    2>/dev/null | tr -d '\r\n' | tail -c 20 || true)"
  if [ "${count:-0}" = "0" ]; then
    echo "首次启动：导入 44 个演示商品..."
    docker compose --profile seed run --rm seed || echo "演示数据导入失败（不影响启动）。"
  fi
}

print_summary() {
  local frontend_port
  frontend_port="$(port_of FRONTEND_PORT 8088)"
  echo
  echo "ItsMyGo 已启动：http://localhost:${frontend_port}"
  echo "管理员：$(env_get BOOTSTRAP_ADMIN_USERNAME)"
  if [ -n "$ADMIN_PASSWORD_NEW" ]; then
    echo "初始密码：$ADMIN_PASSWORD_NEW（已写入 .env，请自行保存）"
  else
    echo "密码见 .env 的 BOOTSTRAP_ADMIN_PASSWORD"
  fi
  echo "停止：./deploy.sh down    日志：./deploy.sh logs"
}

cmd="${1:-up}"
shift || true

NO_SEED=0
for arg in "$@"; do
  [ "$arg" = "--no-seed" ] && NO_SEED=1
done

case "$cmd" in
  up)
    need_docker
    ensure_env
    docker compose up -d --build
    wait_backend
    import_seed_if_empty
    print_summary
    ;;
  down)
    need_docker
    docker compose down
    echo "已停止（数据保留）。"
    ;;
  logs)
    docker compose logs -f backend
    ;;
  status)
    docker compose ps
    ;;
  seed)
    need_docker
    ensure_env
    docker compose --profile seed run --rm seed
    ;;
  reset)
    need_docker
    read -r -p "会删除所有数据卷，输入 yes 继续：" answer
    [ "$answer" = "yes" ] || { echo "已取消。"; exit 1; }
    docker compose down -v
    echo "已删除容器与数据卷。"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "未知命令：$cmd"
    usage
    exit 1
    ;;
esac
