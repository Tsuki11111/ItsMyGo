# HANDOFF · 一次买够（ItsMyGo）

> **读者**：下一个接手本项目的 Claude 会话
> **性质**：本项目**一切细节的权威源** —— 做到哪、下一步做什么、踩过哪些坑。
> [README.md](README.md) 是它的**精简报告**（内容以本文为准，两者允许重复）；
> 需求分析与选型理由见 [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md)
>
> **改动本项目时：先改本文，再同步 README。**

---

## 0. 先读这些（按顺序）

1. **[README.md](README.md)** —— 项目是什么、技术栈、架构、部署方式。**先读它**。
2. **本文件** —— 现在做到哪、下一步做什么、哪里有坑。
3. **[docs/decisions-ecommerce.md](docs/decisions-ecommerce.md)** —— 需求分析、选型理由、
   分阶段路线的**唯一权威**。只要涉及「接下来做什么」，先读它。
4. **涉及客服 / agent / gRPC 对接时**，还必须读外部仓库
   `D:\PycharmProjects\rag_helper`：
   - `evolution-plan.md` **§5（gRPC 接口）、§6（平台对接前提）** —— 对接契约的唯一权威
   - `proto/agent.proto` —— Java 侧生成 stub 的凭据

---

## 1. 当前状态一句话

**尚未开始编码。** 需求分析、技术选型、接口契约均已定稿，本地中间件已就绪，
停在 **M0（骨架）起点**。

已完成的准备工作（2026-10-12）：

- Maven 环境修复（见 §3.1、§3.2），Spring Boot 4.1.1 骨架能在命令行跑通 `clean test`
- GitHub 公开仓库已建：https://github.com/Tsuki11111/ItsMyGo
- MySQL 容器已启动，`its_mygo` 库已建、账号已授权
- 三份文档（README / HANDOFF / CLAUDE）与决策记录落盘

---

## 2. 下一步做什么：M0 · 骨架

参考工期 2–3 天。执行顺序建议如下，**第 3 步是整个方案唯一的未验证项**：

1. **包结构与基础配置** —— `config` / `controller` / `service` / `mapper` / `entity` / `dto`
   （单模块，不拆多模块）
2. **统一响应体与全局异常处理** —— `Result<T>` + `@RestControllerAdvice`
3. **⚠️ 实测 MyBatis-Plus 在 Spring Boot 4.1.1 上的兼容性** ——
   **这是整个选型里唯一没验证的点**。建个最小示例（一张表、一个 Mapper、一次 CRUD），
   跑通即定；**不通过则整体切 Spring Data JPA，其余选型不受影响**。
   同时验证 `mybatis-plus-spring-boot3-starter` 之类 starter 的实际可用版本
4. **Flyway 接上** —— `db/migration/V1__init.sql`，先把用户表建出来
5. **连接共用中间件** —— MySQL `localhost:3306/its_mygo`、Redis `localhost:6379`，
   凭据走 `.env`（**确认 `.gitignore` 已排除**）
6. **验收**：应用启动能连上库与 Redis，Flyway 迁移成功，最小 CRUD 接口通

---

## 3. 环境与约定（已就绪，勿重复劳动）

### 3.1 JDK 与 Maven

- **`JAVA_HOME` 已从 JDK 8 改为 `C:\Users\28470\.jdks\jbr-17.0.14`**（用户级变量）。
  改之前 `mvnw` 跑在 Java 1.8 上，而 Spring Boot 4.1.1 需要 17+
- 环境变量对**已打开的终端和 IDEA 进程不生效**，需要重启才读到新值
- 本机 JDK 清单：`~/.jdks/jbr-17.0.14`（17）、`~/.jdks/ms-11.0.27`（11）、
  `D:\Java`（23）、`C:\Program Files\Java\jdk1.8.0_201`（8）

### 3.2 Maven 镜像

- `C:\Users\28470\.m2\settings.xml` 已配**阿里云镜像**
  （`https://maven.aliyun.com/repository/public`，`mirrorOf=central`）
- 起因：直连 `repo.maven.apache.org` 下载被截断，报
  `java.io.EOFException: Unexpected end of file from server`，
  本地仓库留下一堆 `.lastUpdated` 失败标记
- 若再遇到下载类错误，先查镜像配置；必要时用 `-U` 强制重试

### 3.3 共用中间件（**本项目不另起容器**）

本机 Docker 里跑着**多个项目共用**的中间件，ItsMyGo 一律复用：

| 中间件 | 容器 | 连接 | 本项目隔离方式 |
|---|---|---|---|
| MySQL | `mysql`（mysql:8.0，**需手动 start**） | `localhost:3306` | 独立 database `its_mygo` |
| Redis | `redis`（redis:7-alpine，运行中） | `localhost:6379` | key 前缀 `its-mygo:` |

- `mysql` 容器里还有 `dw` / `meta` / `pharma_db` 三个**别的项目**的库，**不要动**
- `mysql` 与 `rag_helper` 那批容器一样**不设 restart policy**，
  Docker Desktop 重启后要手动 `docker start mysql`

### 3.4 命名约定（硬约束）

本项目**若**创建任何 Docker 资源（卷 / 网络 / 容器），名字一律以 `its-my-go` 开头，
**不依赖 Compose 从目录名推导的默认前缀**（那会得到 `itsmygo_`）。

来源：`rag_helper/docker/redis-compose.yml` 的注释 —— 本机同时存在
`docker_mysql_data` / `docker_qdrant_data` / `docker_es_data` 等看不出归属的旧卷，
是已经踩过的坑。

### 3.5 凭据

数据库口令走环境变量 / `.env`，**绝不提交进 Git** ——
ItsMyGo 的 GitHub 仓库是**公开**的。

### 3.6 提交信息

Conventional Commits，**描述用中文**（与 `rag_helper` 保持一致）：

```
<type>(<scope>): <subject>
```

`type` 取 `feat` / `fix` / `docs` / `style` / `refactor` / `perf` / `test` /
`build` / `ci` / `chore` / `revert`；`scope` 用短词（如 `order` / `product` / `auth`）。

---

## 4. 已知风险与坑

### 4.1 ⚠️ Spring Boot 4.1.1 生态兼容性（**最重要的未验证项**）

Boot 4.1.1 发布不久，第三方生态适配可能滞后：

| 组件 | 风险 | 应对 |
|---|---|---|
| MyBatis-Plus | 对 Boot 4 适配可能滞后 | M0 第 3 步实测；**不通过则切 Spring Data JPA** |
| `grpc-spring-boot-starter` | 大概率未适配 | **已决定不用**，直接上原生 `grpc-java` + `protobuf-maven-plugin` |

### 4.2 共用 Redis 的内存策略约束

`redis` 容器以 `--maxmemory 256mb --maxmemory-policy volatile-lru` 启动。
该策略下**只有带 TTL 的 key 会被淘汰** —— 本项目若写入**不带 TTL 的 key**，
内存打满时写入会**直接报错失败**，而不是被淘汰。

**因此：本项目所有 Redis key 必须带 TTL。** 库存预扣、缓存、令牌黑名单都要设过期时间。

### 4.3 库存扣减（M3 的关键点）

并发下不能超卖。方案已定：**Redis 预扣 + 数据库乐观锁**
（`UPDATE ... WHERE stock >= n` 或版本号）。
**不要**写成「先 select 查库存、再 update 扣减」——那是面试必被问穿的两步竞态。

### 4.4 订单快照

`order_item` 必须冗余存下单时的**商品名、单价、图片**。
商品后续改价，历史订单不能跟着变。

---

## 5. 历史记录

### 2026-10-12 · 立项与准备（本文件建立）

- Maven 下载被截断（`Unexpected end of file from server`）→ 配阿里云镜像解决（§3.2）
- `JAVA_HOME` 指向 JDK 8 → 改到 `jbr-17.0.14`（§3.1）
- GitHub 公开仓库建好：https://github.com/Tsuki11111/ItsMyGo
  （账号下已有一个同名空私有仓库，由用户在 IDEA 里创建后改为公开）
- 需求分析 + 技术选型定稿 → `docs/decisions-ecommerce.md`
- 启动共用 `mysql` 容器，建 `its_mygo` 库并授权 `tsuk1` 账号
- 确认复用共用中间件的方案与命名约定（§3.3、§3.4）
