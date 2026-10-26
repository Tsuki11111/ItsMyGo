# 部署说明 · ItsMyGo

> README 只留三行命令；端口、环境变量、手工 Compose 等细节放这里。

## 一键启动

前置：Docker Desktop 已启动。

```bash
./deploy.sh
```

打开 `http://localhost:8088`。

首次启动会自动做四件事：

1. 从 `.env.example` 生成 `.env`；
2. 生成 JWT / 数据库 / RabbitMQ / MinIO 的随机密码；
3. 生成管理员 `admin` 和随机初始密码（打印一次，并写入 `.env`）；
4. 后端健康后，如果商品表为空，导入 44 个演示商品。

不想导入演示数据：

```bash
./deploy.sh up --no-seed
```

## 命令

| 命令 | 作用 |
|---|---|
| `./deploy.sh` | 启动全栈 |
| `./deploy.sh down` | 停止（保留数据卷） |
| `./deploy.sh logs` | 跟踪后端日志 |
| `./deploy.sh status` | 查看容器状态 |
| `./deploy.sh seed` | 重新导入演示商品 |
| `./deploy.sh reset` | 停止并删除所有数据卷（需输入 `yes`） |

PowerShell 用 `./deploy.ps1`，参数相同；跳过演示数据用 `./deploy.ps1 up -NoSeed`。

## 端口

| 服务 | 默认端口 | `.env` 变量 |
|---|---|---|
| 前端 | 8088 | `FRONTEND_PORT` |
| 后端 | 8080 | `BACKEND_PORT` |
| MySQL | 3306 | `MYSQL_PORT` |
| Redis | 6379 | `REDIS_PORT` |
| MinIO API / 控制台 | 9000 / 9001 | `MINIO_PORT` / `MINIO_CONSOLE_PORT` |
| RabbitMQ / 管理台 | 5672 / 15672 | `RABBITMQ_PORT` / `RABBITMQ_CONSOLE_PORT` |

改端口只改 `.env`。改了 MinIO 端口时，同步改 `MINIO_PUBLIC_ENDPOINT`
（浏览器实际访问的地址，例如 `http://localhost:19000` 或内网 IP）。

## 常用配置

- `MINIO_PUBLIC_ENDPOINT`：浏览器加载商品图用的地址。容器内后端始终用 `http://minio:9000`，
  这两个地址不能混。
- `VITE_TMAP_KEY`：订单地图的腾讯位置服务 Key。它是**构建期变量**，改了要重新
  `./deploy.sh`（会重建前端镜像）。
- `AGENT_GRPC_KEY` / `AGENT_IMPORT_KEY` / `AGENT_EVAL_KEY`：联合部署时由
  `rag_helper/deploy.sh joint` 自动写入，不需要手填；后两者不填时默认复用
  `AGENT_GRPC_KEY`。
- 管理员密码在 `.env` 的 `BOOTSTRAP_ADMIN_PASSWORD`；只在库中没有管理员时使用。

## 联合部署客服智能体

两个仓库放成同级（`ItsMyGo/`、`rag_helper/`），在 `rag_helper` 里执行：

```bash
./deploy.sh joint
```

它会自动生成共享密钥、写入两边的 `.env`、先启动 ItsMyGo，再启动 agent。
Java 通过 `rag-agent:8003` / `rag-import:8001` / `rag-chat:8002` 访问 agent，
agent 通过 `its-my-go-backend:8080` 回调 Java。停止两套：`./deploy.sh joint-down`。

## 手工 Compose

不使用脚本时：

```bash
cp .env.example .env
# 手工路径需要自己填 DB / MinIO 凭据和 BOOTSTRAP_ADMIN_*
docker compose up -d --build
docker compose --profile seed run --rm seed
```

## Docker Hub 拉不动

本机访问 Docker Hub 可能超时。先用国内镜像站拉基础镜像，再打成标准名：

```bash
docker pull docker.m.daocloud.io/library/mysql:8.0
docker tag docker.m.daocloud.io/library/mysql:8.0 mysql:8.0
```

需要：`maven:3.9.9-eclipse-temurin-17`、`eclipse-temurin:17-jre`、`node:22-alpine`、
`nginx:1.27-alpine`、`mysql:8.0`、`redis:7-alpine`、`minio/minio:latest`、
`rabbitmq:4-management`。

## 本地开发（不用 Docker 跑应用）

复用本机已有的 `mysql` / `redis` / `minio`，后端 `./mvnw spring-boot:run`，
前端 `cd frontend && npm run dev`。完整清单见 [start.txt](../start.txt)。
