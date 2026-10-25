#!/usr/bin/env bash
# 停止 ItsMyGo 全栈（保留数据卷）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose down
echo "已停止。数据卷保留；要连数据一起清掉：docker compose down -v"
