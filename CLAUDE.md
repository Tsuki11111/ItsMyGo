# 项目约定（一次买够 · ItsMyGo）

## 刚进到这个目录，先做这两件事

1. 读 **[README.md](README.md)** —— 了解项目是什么、技术栈、架构选型、部署方式
2. 读 **[HANDOFF.md](HANDOFF.md)** —— 了解现在做到哪、下一步做什么、哪里有坑

**顺序不要颠倒**：README 给你全局，HANDOFF 给你当前的落点。
读懂之后**按问题选文档**（2026-11-05 文档已归并成四层，边界清晰）：

| 你的问题 | 读哪里 |
|---|---|
| 要做什么、做到什么程度、什么不做、怎么验收 | [docs/requirements.md](docs/requirements.md) |
| 选了什么技术、为什么、整体架构、契约怎么对接、决策记录 D1–D15 | [docs/architecture.md](docs/architecture.md) |
| 每个机制具体怎么实现（认证链路、越权校验、库存、缓存、拆单…） | [docs/implementation.md](docs/implementation.md) |
| 接口清单、请求 / 响应、限流、SSE 事件 | [docs/api.md](docs/api.md) |
| 部署、端口、联合部署、生产 VM | [docs/deployment.md](docs/deployment.md) |

**要动代码之前必须先读 implementation.md**，别重复造已有的轮子。

> 旧的 [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md) 已改为**跳转页**（原需求并入
> requirements、原选型并入 architecture），**不要再往里面补内容**。

---

## 关联仓库：rag_helper（客服智能体）

**本项目的智能客服不在这个仓库里**，在外部目录 **`D:\PycharmProjects\rag_helper`** ——
Python 项目，RAG 客服智能体「买小购」，gRPC 服务端跑在 **8003**。

**涉及客服 / agent / gRPC 的改动，动手前先读那边**（⚠️ 2026-11-05 rag_helper 做过一次
**文档归并**：`evolution-plan.md` 与 `docs/decisions-evolution.md` 已变为跳转页，
原 evolution-plan §5 / §6 现对应下表的新位置；`proto/agent.proto` 字段未变）：

| 读什么 | 为什么 |
|---|---|
| `proto/agent.proto` | **契约字段的唯一权威**（字段源码）；Java 侧由它生成 stub（`java_multiple_files = true`） |
| `docs/architecture.md` **§4** | gRPC 接口 / 事件 / `Trace` / 内部回调 / 动作流程的**说明性概览** |
| `docs/requirements.md` **§4** | 平台对接前提：身份、票据、写操作边界、隔离边界、Java 侧已拍板参数 |
| `HANDOFF.md` | 那边的进度状态与踩坑（与本文分工相同，是状态权威源） |

**两者关系**：浏览器只跟 Java 说话 → Java 经 gRPC 调 agent → 事件流转 SSE 回浏览器。
Java 是**调用方**；agent 只在内网、只允许本单体访问；agent 需要数据时反向回调
Java 的 `/internal/agent/**`（凭证是 Java 每轮签发的短期票据，模型不参与身份）。
**本仓库不能自行更改对接契约** —— 要改先跟用户确认，并同步改那边。

那边的约定同样适用：全程中文回复、Conventional Commits（描述用中文）、
改完同步它的 requirements / architecture / implementation / HANDOFF / README。

---

## 协作偏好

- **全程中文回复**
- **改了代码，主动同步文档，不要等被提醒；改完自动提交并推送**（Conventional Commits，描述中文）。
  完整流程见 [HANDOFF.md](HANDOFF.md) **§4.9「新功能开发流程」** —— 那是唯一权威：
  **先读需求 → 等用户敲定 → 补 requirements → 自评审需求 → 改 architecture → 写代码 →
  改 implementation → 最后 HANDOFF / README → 自动 commit + push**。
  各文档分工：requirements 讲「要什么」、architecture 讲「为什么这么选」、
  implementation 讲「怎么做到的」（**代码的镜像，实现变了必须更新**）、
  HANDOFF 讲「到哪了」（**状态与坑只写这里**）、README 是精简报告（以 HANDOFF 为准）。
  **旧的跳转页（decisions-ecommerce）不要再补内容。**
- **收到用户补充的新约束，立刻写进文档**，不要只留在对话里（本项目的 Docker 命名约定
  就是这么来的）
- **本项目按高并发标准做**（用户 2026-10-15 明确要求）—— 即使是练手项目，
  缓存、并发、一致性相关的选型**不因「反正没流量」而降低要求**。
  **已知但尚未处理的高并发问题清单在 HANDOFF §5 末尾**，动手前先看有没有已解决的
- **提交信息用 Conventional Commits，描述用中文**。`type` 取
  `feat`/`fix`/`docs`/`style`/`refactor`/`perf`/`test`/`build`/`ci`/`chore`/`revert`，
  `scope` 用短词（`order`/`product`/`auth` 等）
- **涉及客服 / agent / gRPC 的改动**，先读上面「关联仓库：rag_helper」一节 ——
  对接契约在那边，本仓库不能自行更改

---

## 硬约束

### 1. 中间件复用，不另起容器

本机 Docker 里的中间件是**多项目共用**的：

| 中间件 | 容器 | 本项目用 |
|---|---|---|
| MySQL 8 | `mysql` | 库 `its_mygo` |
| Redis 7 | `redis` | key 前缀 `its-my-go:` |
| MinIO | `minio` | bucket `its-my-go-product-images`（**别碰 rag_helper 的 `rag-helper-knowledge-files`**） |

`mysql` 容器里还有 `dw` / `meta` / `pharma_db` 三个**别的项目**的库，**不要动**。

**RabbitMQ 是唯一的例外**：它是本项目自有的超时关单中间件，已并入根目录
`docker-compose.yml`，**不属于共用容器**。容器 `its-my-go-rabbitmq`、
命名卷 `its-my-go-rabbitmq-data` 显式以 `its-my-go` 开头；端口 **5672**（AMQP）、
**15672**（管理台）。账号从 `.env` 的 `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` 读取，
不写进仓库。

**开工前先确认这三个容器都在跑**（Docker Desktop 重启后不保证自动拉起）：

```bash
docker ps --filter "name=^(mysql|redis|minio)$" --format "table {{.Names}}\t{{.Status}}"
docker start mysql redis minio     # 缺哪个就拉哪个
```

`redis` 与 `minio` 的 restart policy 是 `no`（**必须手动拉**）；
`mysql` 是 `unless-stopped`（一般能自动起，但被显式 stop 过就不会 —— 它曾停摆过两个月）。

### 2. Docker 资源命名

本项目**若**创建任何 Docker 资源（卷 / 网络 / 容器），名字一律以 `its-my-go` 开头，
**不依赖 Compose 从目录名推导的默认前缀**（那会得到 `itsmygo_`）。

### 3. Redis key 必须带 TTL

共用 Redis 以 `--maxmemory 256mb --maxmemory-policy volatile-lru` 启动。
该策略下只有带 TTL 的 key 会被淘汰 —— 本项目写入**不带 TTL 的 key**，
内存打满时会让写入**直接失败**。

### 4. 凭据不进 Git

**本仓库是公开的。** 数据库口令走环境变量 / `.env`，并确保 `.gitignore` 已排除。

### 5. 技术栈已定，别擅自换

见 [docs/architecture.md](docs/architecture.md) §2 的技术栈与选型理由。选型阶段的风险
（MyBatis-Plus / Flyway / Spring Security 在 Boot 4.1.1 上的适配）**已在 M0–M1 实测通过**，
不要再回头质疑。真正会绊人的是 **Boot 4 的模块化差异**，见 HANDOFF §5.1–§5.4、§5.9。
