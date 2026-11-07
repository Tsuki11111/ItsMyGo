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
#
#   —— 联合 rag_helper（演示环境）——
#   ./deploy.sh joint        启动 ItsMyGo + rag_helper 联合栈（8088）
#   ./deploy.sh joint-down   停止两套（保留数据）
#   ./deploy.sh joint-logs   跟踪 rag_helper agent 日志
#   ./deploy.sh joint-status 查看两套容器状态
#
# rag_helper 目录：RAG_HELPER_DIR 环境变量 / 仓库根 .rag-helper-dir 文件 / 默认 ../rag_helper。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

usage() {
  sed -n '2,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
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

  # 图片与前端同源反代，URL 不带主机名；旧部署若残留绝对地址，
  # 升级时手工改成 "/" 并迁移数据库中的历史 URL（见 docs/deployment.md）
  if [ -z "$(env_get MINIO_PUBLIC_ENDPOINT)" ]; then
    env_set MINIO_PUBLIC_ENDPOINT "/"
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

# ── 联合 rag_helper（演示环境）────────────────────────────────────
# 联合入口已收归本仓库：rag_helper 仓的 deploy.sh 只做转发。

find_rag_helper() {
  local dir=""
  if [ -n "${RAG_HELPER_DIR:-}" ]; then
    dir="$RAG_HELPER_DIR"
  elif [ -f .rag-helper-dir ]; then
    dir="$(tr -d '\r\n' < .rag-helper-dir)"
  elif [ -d ../rag_helper ]; then
    dir="$(cd ../rag_helper && pwd)"
  fi

  if [ -z "$dir" ] || [ ! -f "$dir/docker-compose.yml" ]; then
    echo "找不到 rag_helper（需要含 docker-compose.yml 的目录）。" >&2
    echo "请设置 RAG_HELPER_DIR，或在仓库根创建 .rag-helper-dir 文件写入路径。" >&2
    exit 1
  fi
  (cd "$dir" && pwd)
}

rag_env_get() {
  local dir="$1" key="$2"
  [ -f "$dir/.env" ] || return 0
  grep -E "^[[:space:]]*${key}=" "$dir/.env" 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '\r'
}

rag_env_set() {
  local dir="$1" key="$2" value="$3"
  if grep -qE "^[[:space:]]*${key}=" "$dir/.env" 2>/dev/null; then
    sed -i "s|^[[:space:]]*${key}=.*|${key}=${value}|" "$dir/.env"
  else
    printf '%s=%s\n' "$key" "$value" >> "$dir/.env"
  fi
}

ensure_rag_env() {
  local dir="$1" missing=() key value
  [ -f "$dir/.env" ] || { cp "$dir/.env.example" "$dir/.env"; echo "已生成 rag_helper/.env。"; }

  for key in OPENAI_API_KEY DEEPSEEK_API_KEY EMBEDDING_API_KEY; do
    value="$(rag_env_get "$dir" "$key")"
    if [ -z "$value" ] || [ "$value" = "sk-xxx" ]; then
      missing+=("$key")
    fi
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    echo "rag_helper 还差模型密钥：${missing[*]}" >&2
    echo "请编辑 $dir/.env 填好后再执行 joint。" >&2
    exit 1
  fi

  if [ -z "$(rag_env_get "$dir" AGENT_GRPC_KEY)" ]; then
    rag_env_set "$dir" AGENT_GRPC_KEY "$(rand_hex 32)"
    echo "已为 rag_helper 生成 AGENT_GRPC_KEY。"
  fi
}

sync_agent_keys() {
  local dir="$1" key
  key="$(rag_env_get "$dir" AGENT_GRPC_KEY)"
  env_set AGENT_GRPC_KEY "$key"
  env_set AGENT_IMPORT_KEY "$key"
}

wait_agent() {
  local dir="$1" port
  port="$(rag_env_get "$dir" RAG_HTTP_PORT)"
  port="${port:-8002}"
  echo "等待 agent 就绪..."
  for _ in $(seq 1 90); do
    if curl -fsS "http://127.0.0.1:${port}/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "agent 启动超时。查看日志：./deploy.sh joint-logs"
  exit 1
}

print_joint_summary() {
  local frontend_port
  frontend_port="$(port_of FRONTEND_PORT 8088)"
  echo
  echo "联合部署已启动（演示环境）："
  echo "  ItsMyGo   http://localhost:${frontend_port}"
  echo "  客服能力  （知识库导入 / 聊天 / 评测都在平台页面内，Java 代理调用 rag_helper）"
  echo "  管理员：$(env_get BOOTSTRAP_ADMIN_USERNAME)"
  if [ -n "$ADMIN_PASSWORD_NEW" ]; then
    echo "  初始密码：$ADMIN_PASSWORD_NEW（已写入 .env，请自行保存）"
  else
    echo "  密码见 .env 的 BOOTSTRAP_ADMIN_PASSWORD"
  fi
  echo "停止两套：./deploy.sh joint-down    日志：./deploy.sh joint-logs"
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
  joint|up:joint)
    need_docker
    ensure_env
    RAG_HELPER="$(find_rag_helper)"
    ensure_rag_env "$RAG_HELPER"
    sync_agent_keys "$RAG_HELPER"
    export RAG_AGENT_GRPC_TARGET=rag-agent:8003
    export RAG_AGENT_IMPORT_URL=http://rag-import:8001

    echo "==> 启动 ItsMyGo（8088）"
    docker compose up -d --build
    wait_backend
    import_seed_if_empty

    echo "==> 启动 rag_helper（接入 its-my-go-net）"
    ( cd "$RAG_HELPER" && docker compose -f docker-compose.yml -f docker-compose.itsmygo.yml up -d --build )
    wait_agent "$RAG_HELPER"
    ( cd "$RAG_HELPER" && docker compose up -d --force-recreate agent-key-init && docker compose wait agent-key-init )
    print_joint_summary
    ;;
  joint-down|down:joint)
    need_docker
    RAG_HELPER="$(find_rag_helper)"
    echo "==> 停止 rag_helper"
    ( cd "$RAG_HELPER" && docker compose -f docker-compose.yml -f docker-compose.itsmygo.yml down )
    echo "==> 停止 ItsMyGo"
    docker compose down
    echo "已停止两套（数据保留）。"
    ;;
  joint-logs)
    need_docker
    RAG_HELPER="$(find_rag_helper)"
    ( cd "$RAG_HELPER" && docker compose logs -f agent-grpc )
    ;;
  joint-status)
    need_docker
    RAG_HELPER="$(find_rag_helper)"
    echo "==> ItsMyGo"
    docker compose ps
    echo
    echo "==> rag_helper"
    ( cd "$RAG_HELPER" && docker compose ps )
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
