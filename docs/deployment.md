# 部署说明 · ItsMyGo

> README 只留三行命令；端口、环境变量、手工 Compose 等细节放这里。

## 一键启动

前置：Docker Desktop 已启动。

```bash
./deploy.sh
```

Windows 用 `./deploy.ps1`；也可以统一用 npm（需要 Node ≥18，脚本会自动按平台转发到
`deploy.ps1` / `deploy.sh`，逻辑仍只有一份）：

```bash
npm run deploy
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

**npm 入口**（`package.json` 在仓库根，`scripts/npm-deploy.mjs` 只做跨平台转发，
真正的逻辑仍在 `deploy.sh` / `deploy.ps1`，不重复实现）：

| npm 命令 | 等价 |
|---|---|
| `npm run deploy` | `./deploy.sh`（Windows 下走 `deploy.ps1`） |
| `npm run deploy -- down` / `npm run deploy:down` | `./deploy.sh down` |
| `npm run deploy -- logs` / `npm run deploy:logs` | `./deploy.sh logs` |
| `npm run deploy -- status` / `npm run deploy:status` | `./deploy.sh status` |
| `npm run deploy -- seed` / `npm run deploy:seed` | `./deploy.sh seed` |
| `npm run deploy -- reset` / `npm run deploy:reset` | `./deploy.sh reset` |
| `npm run deploy -- up --no-seed` | `./deploy.sh up --no-seed` |

依赖只有 Node 本身，没有 npm 依赖（`npm install` 都不需要）；退出码与底层脚本一致，
`status` / `logs` 在 Docker 没启动时会明确报错（bash 与 PowerShell 行为已对齐）。

> 根目录 npm 还提供了开发/构建/测试转发：`npm run dev`、`dev:api`、`build`（=前端+后端 jar）、
> `type-check`、`test`（=`./mvnw test`）。它们不涉及 Docker，详见 README「本地开发」。

## 端口

| 服务 | 默认端口 | `.env` 变量 |
|---|---|---|
| 前端 | 8088 | `FRONTEND_PORT` |
| 后端 | 8080 | `BACKEND_PORT` |
| MySQL | 3306 | `MYSQL_PORT` |
| Redis | 6379 | `REDIS_PORT` |
| MinIO API / 控制台 | 9000 / 9001 | `MINIO_PORT` / `MINIO_CONSOLE_PORT` |
| RabbitMQ / 管理台 | 5672 / 15672 | `RABBITMQ_PORT` / `RABBITMQ_CONSOLE_PORT` |

改端口只改 `.env`。MinIO 端口变化不影响图片地址：浏览器访问的是前端同源路径
`/its-my-go-product-images/...`，由 Nginx 反代到 `minio:9000`。

## 常用配置

- `MINIO_PUBLIC_ENDPOINT`：生成图片 URL 用的前缀，**默认 `/`**（同源相对路径）。
  容器内后端始终用 `http://minio:9000` 连接 MinIO，两者不能混。
  保持 `/` 即可；配成绝对地址会把主机名固化进数据库，换 IP 后历史图片全部失效
  （2026-11-05 的故障即此原因）。
- `VITE_TMAP_KEY`：订单地图的腾讯位置服务 Key。它是**构建期变量**，改了要重新
  `./deploy.sh`（会重建前端镜像）。
- `AGENT_GRPC_KEY` / `AGENT_IMPORT_KEY` / `AGENT_EVAL_KEY`：联合部署时由
  `rag_helper/deploy.sh joint` 自动写入，不需要手填；后两者不填时默认复用
  `AGENT_GRPC_KEY`。
- 管理员密码在 `.env` 的 `BOOTSTRAP_ADMIN_PASSWORD`；只在库中没有管理员时使用。
- `AFTER_SALE_*`：售后超时策略 —— `AFTER_SALE_REVIEW_HOURS`（商家审核时限，默认 48）、
  `AFTER_SALE_RETURN_CONFIRM_DAYS`（退货确认期，默认 7 天）、`AFTER_SALE_SCAN_INTERVAL_MS`
  （扫描间隔，默认 5 分钟）；`AFTER_SALE_SCAN_ENABLED=false` 可停掉自动处理。
  这是**扫描式**的，不需要 RabbitMQ。

## 从旧版本升级（图片绝对地址 → 相对路径）

旧版 `.env` 里可能写着 `MINIO_PUBLIC_ENDPOINT=http://<宿主机IP>:9000`，数据库里也存了
绝对 URL。升级到同源方案：

1. `.env` 改为 `MINIO_PUBLIC_ENDPOINT=/`，重建前端镜像并重启前后端；
2. 执行一次数据迁移（按需先在旧 host 列表里加上自己的地址）：

```bash
docker exec -i its-my-go-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" its_mygo' \
  < sql/migrate-relative-image-urls.sql
```

3. 清掉商品详情缓存，让接口立即返回新 URL（否则要等 TTL）：

```bash
docker exec its-my-go-redis sh -c 'redis-cli --scan --pattern "its-my-go:product:*" | xargs -r redis-cli del'
```

## 联合部署客服智能体

两个仓库放成同级（`ItsMyGo/`、`rag_helper/`），在 `rag_helper` 里执行：

```bash
./deploy.sh joint
```

它会自动生成共享密钥、写入两边的 `.env`、先启动 ItsMyGo，再启动 agent。
Java 通过 `rag-agent:8003` / `rag-import:8001` / `rag-chat:8002` 访问 agent，
agent 通过 `its-my-go-backend:8080` 回调 Java。停止两套：`./deploy.sh joint-down`。

## 生产（演示）部署：内网 VM

> ⚠️ 本项目**没有真实的线上生产**（定位是练手 / 简历项目）。文档里说「生产」，
> 指的是一台内网 VM 上的演示环境。这一节只记录**已核实的事实**和**已知缺口**，
> 动手前先看完，别按本地开发的习惯想当然。

**机器与网络**（2026-11-05 变更）：

| 项 | 值 |
|---|---|
| 访问入口 | `http://10.184.171.106:8088`（前端 Nginx） |
| VM IP | 静态 `10.184.171.106/24` |
| 网关 / DNS | 网关 `10.184.171.119`；DNS 同网关 + `223.5.5.5` |
| 网络由谁配 | 原为 DHCP（IP 曾从 `10.76.196.106` 漂到现址），已改静态，**并禁用了 cloud-init 网络配置** |

⚠️ **不要再把 VM 改回 DHCP，也不要恢复 cloud-init 的网络配置** —— 2026-11-05 图片全部失效
的事故就是 IP 漂移引起的（数据库里固化了绝对 URL）。同源化改造之后这个坑才拔掉。

**部署形态**：

- 全栈靠 `docker compose`；容器 / 卷一律 `its-my-go-*`，网络 `its-my-go-net`
  （与 rag_helper 联合部署共用）。
- VM **拉不到 Docker Hub**，用的是**仓库外的离线部署包**：镜像在本地打成
  `its-my-go-*:local`，由 VM 上的 `docker-compose.offline.yml` 覆盖固定 tag。
  这个覆盖文件**不在仓库里**，所以「换机器 / 重新发版」不能指望
  `docker compose up --build` 能跑通，得重新打离线包。
- 图片必须**同源**：`.env` 保持 `MINIO_PUBLIC_ENDPOINT=/`（默认即 `/`），数据库只存
  `/its-my-go-product-images/...` 相对路径，浏览器请求由前端 Nginx 反代到 `minio:9000`；
  客服召回图走 `/rag-images/` → `host.docker.internal:9002`。
  **一旦配成绝对地址，主机名就会固化进库，换 IP 后历史图片全部失效。**
- 联合部署的 agent 地址用 `RAG_*` 前缀变量（`RAG_AGENT_GRPC_TARGET=rag-agent:8003`、
  `RAG_AGENT_IMPORT_URL=http://rag-import:8001`、`RAG_AGENT_EVAL_URL=http://rag-chat:8002`），
  本地调试用的 `AGENT_GRPC_TARGET=127.0.0.1:8003` 不会进容器。
- 数据库 schema 由 Flyway 在启动时迁到最新（当前 v31）；数据在命名卷
  `its-my-go-mysql-data` 里，**`./deploy.sh reset` 会删卷**，在 VM 上别随手跑。
- **发版流程**（VM 拉不到 Hub，镜像必须在开发机打好再传）：
  1. 开发机：`docker compose build backend frontend`（或本地离线打包 jar 后构建运行时镜像），
     产物 tag 固定为 `its-my-go-backend:local` / `its-my-go-frontend:local`；
  2. `docker save -o zz-itsmygo.tar its-my-go-backend:local its-my-go-frontend:local`；
  3. `scp` 覆盖 VM 的 `~/itsmygo-offline/images/zz-itsmygo.tar`，同时同步
     `docker-compose.yml`（离线覆盖文件不动）；
  4. VM：`./stop.sh` → `docker rmi` 旧 `its-my-go-*` → `docker load -i images/zz-itsmygo.tar`
     → 在 `ItsMyGo/` 里 `docker compose -f docker-compose.yml -f docker-compose.offline.yml up -d --no-build --pull never`；
  5. 验收：`/api/health`、`flyway_schema_history` 最新版本、容器 healthy。
  - ⚠️ **不要直接跑 `install.sh` 做发版**：它会无条件恢复 `data/product-images.sql`，
    那脚本写的是 `http://__VM_IP__:9000/...` 绝对地址，会把同源化改回去
    （2026-11-05 已把该脚本的 sed 改成去掉主机名、只留相对路径；以后更新离线包别忘了带上）。
- **prod profile**：`docker-compose.yml` 的 backend 已透传 `SPRING_PROFILES_ACTIVE`
  （默认空 = 开发行为），VM 的 `ItsMyGo/.env` 里设 `SPRING_PROFILES_ACTIVE=prod`。

**现状与缺口**（都是「知道但未做」，不是遗漏）：

| 项 | 现状 |
|---|---|
| HTTPS / 域名 | ❌ 没有，纯 HTTP + IP 访问 |
| prod profile | ✅ 2026-11-05 起 `docker-compose.yml` 透传 `SPRING_PROFILES_ACTIVE`，VM 的 `ItsMyGo/.env` 已设 `prod`（日志级别按 `application-prod.yml`，关 SQL 明细） |
| 备份 / 恢复 | ❌ 仓库内没有任何备份配置或脚本 |
| 监控告警 | ❌ 没有；只有 `/api/health` 可探活 |
| 单实例假设 | ⚠️ SSE 在线状态是进程内注册表、`@Scheduled` 的布隆过滤器预热与订单扫描都按单实例写；多实例部署需要另外改造 |
| 凭据 | ⚠️ VM 的 SSH 账号 / 密钥**不在仓库**（凭据不进 Git），接手时向用户要；MinIO 是 rag_helper 共用的实例、默认凭据且 9000/9001 绑 `0.0.0.0`，**不要暴露到公网** |
| 可达性 | ✅ 2026-11-05 晚已核实：从开发机 `ssh itsmygo-vm` 正常，`10.184.171.106` 的 8088 / 8080 / 8002 均 200（此前探测超时是 VM 当时没开机） |
| 离线包数据脚本 | ⚠️ `data/product-images.sql` 写的是绝对地址，`install.sh` 会无条件执行；已在 VM 侧改成相对路径，见上面「发版流程」 |

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
