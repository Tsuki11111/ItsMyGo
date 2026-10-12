# 项目约定（一次买够 · ItsMyGo）

## 刚进到这个目录，先做这两件事

1. 读 **[README.md](README.md)** —— 了解项目是什么、技术栈、架构选型、部署方式
2. 读 **[HANDOFF.md](HANDOFF.md)** —— 了解现在做到哪、下一步做什么、哪里有坑

**顺序不要颠倒**：README 给你全局，HANDOFF 给你当前的落点。
需要需求细节或选型理由时，再读 [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md)。

---

## 协作偏好

- **全程中文回复**
- **每完成一个目标，主动同步更新 HANDOFF.md 与 README.md，不要等被提醒**。
  **更新顺序固定，不许颠倒**：
  1. **先改 [HANDOFF.md](HANDOFF.md)** —— 它是**本项目一切细节的权威源**
  2. **再改 [README.md](README.md)** —— 它是精简报告，**内容一律以 HANDOFF 为准**
  两者**允许内容重复**，只是详略不同：HANDOFF 详细、README 简洁。
  **README 与 HANDOFF 若有出入，以 HANDOFF 为准**
- **收到用户补充的新约束，立刻写进文档**，不要只留在对话里（本项目的 Docker 命名约定
  就是这么来的）
- **提交信息用 Conventional Commits，描述用中文**。`type` 取
  `feat`/`fix`/`docs`/`style`/`refactor`/`perf`/`test`/`build`/`ci`/`chore`/`revert`，
  `scope` 用短词（`order`/`product`/`auth` 等）
- **涉及客服 / agent / gRPC 的改动**，先读外部仓库 `D:\PycharmProjects\rag_helper` 的
  `evolution-plan.md` §5/§6 与 `proto/agent.proto` —— 那边是对接契约的**唯一权威**，
  本仓库不能自行更改

---

## 硬约束

### 1. 中间件复用，不另起容器

本机 Docker 里的中间件是**多项目共用**的：

| 中间件 | 容器 | 本项目用 |
|---|---|---|
| MySQL 8 | `mysql` | 库 `its_mygo` |
| Redis 7 | `redis` | key 前缀 `its-mygo:` |
| MinIO | `minio` | bucket `its-mygo-product-images`（**别碰 rag_helper 的 `rag-helper-knowledge-files`**） |

`mysql` 容器里还有 `dw` / `meta` / `pharma_db` 三个**别的项目**的库，**不要动**。
`mysql` 与 `rag_helper` 那批容器一样**不设 restart policy**，
Docker Desktop 重启后需手动 `docker start mysql`。

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

见 README 的技术栈表。**唯一的未验证项**是 MyBatis-Plus 对 Spring Boot 4.1.1 的适配 ——
M0 阶段实测，不通过才整体切 Spring Data JPA，其余选型不受影响。
