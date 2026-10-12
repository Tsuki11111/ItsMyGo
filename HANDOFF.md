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

**M0（骨架）已完成（2026-10-12）。**

骨架搭好、中间件接通、**自动化测试 4 个全绿**。**选型阶段最大的风险已经排除**：
MyBatis-Plus 3.5.17 与 Flyway 12.4.0 在 Spring Boot 4.1.1 上均验证可用，
**不需要退回 Spring Data JPA**。

下一步：**M1（账号体系）**。

---

## 2. 下一步做什么：M1 · 账号体系

参考工期 3–5 天。

1. 注册 / 登录接口，密码用 BCrypt 加密
2. JWT 签发与校验（Spring Security 配置 + 过滤器）
3. 收货地址 CRUD —— 新增 `addresses` 表走 `V2__address.sql`
4. **越权校验**：A 不能读写 B 的数据（面试必问，别漏）
5. 验收：注册登录拿 token，带 token 能访问受保护接口，不带被拒

⚠️ **动手前先看 §5.1** —— 加 Spring Security 时同样要确认 Boot 4 的自动配置在哪个模块。

---

## 3. 项目结构

### 3.1 包结构（单模块 Maven）

```
src/main/java/com/tsuk1/itsmygo/
├── ItsMyGoApplication.java      # @SpringBootApplication + @MapperScan("com.tsuk1.itsmygo.mapper")
├── common/                      # 跨层共用
│   ├── Result.java              # 统一响应体 Result<T>（code / message / data）
│   ├── ResultCode.java          # 响应码枚举，与 HTTP 语义对齐（200 / 400 / 401 / 403 / 404 / 500）
│   └── exception/
│       ├── BizException.java           # 可预期失败（参数错、库存不足…），只记 warn 不打堆栈
│       └── GlobalExceptionHandler.java # @RestControllerAdvice，兜底转统一响应
├── controller/                  # HTTP 入口
├── service/                     # 业务逻辑
├── mapper/                      # MyBatis-Plus BaseMapper
└── entity/                      # 数据库实体
```

DTO 层随 M1 需要再加。

### 3.2 配置与凭据

- 配置文件是 `src/main/resources/application.yml` —— **原来的 `application.properties` 已删除**
- 凭据放项目根目录的 **`.env`**，由 `application.yml` 的
  `spring.config.import: optional:file:.env[.properties]` 引入
- `.env` 已被 `.gitignore` 排除；仓库里只留 `.env.example`
- 变量名：`DB_USERNAME`、`DB_PASSWORD`

### 3.3 数据库

- 库名 `its_mygo`（在共用的 `mysql` 容器里）
- 迁移脚本在 `src/main/resources/db/migration/`，**当前 schema 版本 v1**
- 已建表：`users`
  （另有 Flyway 自建的 `flyway_schema_history`，是迁移记录表，**不要动**）

---

## 4. 环境与约定（已就绪，勿重复劳动）

### 4.1 JDK 与 Maven

- **`JAVA_HOME` 已从 JDK 8 改为 `C:\Users\28470\.jdks\jbr-17.0.14`**（用户级变量）。
  改之前 `mvnw` 跑在 Java 1.8 上，而 Spring Boot 4.1.1 需要 17+
- 环境变量对**已打开的终端和 IDEA 进程不生效**，需重启才读到新值
- 本机 JDK 清单：`~/.jdks/jbr-17.0.14`（17）、`~/.jdks/ms-11.0.27`（11）、
  `D:\Java`（23）、`C:\Program Files\Java\jdk1.8.0_201`（8）
- 命令行跑 Maven 时若 `java -version` 显示 1.8，说明继承了旧环境，
  可显式指定 `JAVA_HOME="C:/Users/28470/.jdks/jbr-17.0.14"`

### 4.2 Maven 镜像

- `C:\Users\28470\.m2\settings.xml` 已配**阿里云镜像**
  （`https://maven.aliyun.com/repository/public`，`mirrorOf=central`）
- 起因：直连 `repo.maven.apache.org` 下载被截断，报
  `java.io.EOFException: Unexpected end of file from server`
- 若再遇下载类错误，先查镜像配置；必要时用 `-U` 强制重试

### 4.3 共用中间件（**本项目不另起容器**）

| 中间件 | 容器 | 连接 | 本项目隔离方式 |
|---|---|---|---|
| MySQL | `mysql`（mysql:8.0，**需手动 start**） | `localhost:3306` | 独立 database `its_mygo` |
| Redis | `redis`（redis:7-alpine，运行中） | `localhost:6379` | key 前缀 `its-mygo:` |

- `mysql` 容器里还有 `dw` / `meta` / `pharma_db` 三个**别的项目**的库，**不要动**
- `mysql` 与 `rag_helper` 那批容器一样**不设 restart policy**，
  Docker Desktop 重启后要手动 `docker start mysql`

### 4.4 命名约定（硬约束）

本项目**若**创建任何 Docker 资源（卷 / 网络 / 容器），名字一律以 `its-my-go` 开头，
**不依赖 Compose 从目录名推导的默认前缀**（那会得到 `itsmygo_`）。

来源：`rag_helper/docker/redis-compose.yml` 的注释 —— 本机同时存在
`docker_mysql_data` / `docker_qdrant_data` / `docker_es_data` 等看不出归属的旧卷。

### 4.5 凭据

数据库口令走 `.env`，**绝不提交进 Git** —— ItsMyGo 的 GitHub 仓库是**公开**的。

### 4.6 提交信息

Conventional Commits，**描述用中文**（与 `rag_helper` 保持一致）：

```
<type>(<scope>): <subject>
```

`type` 取 `feat` / `fix` / `docs` / `style` / `refactor` / `perf` / `test` /
`build` / `ci` / `chore` / `revert`；`scope` 用短词（如 `order` / `product` / `auth`）。

---

## 5. 踩过的坑

### 5.1 ★ Spring Boot 4 把自动配置拆成了独立模块（2026-10-12 踩到）

**这是 Boot 4 与 Boot 2/3 最大的实操差异，不知道就会卡住。**

第三方库只加「库本身」的依赖**不会激活它的自动配置**，必须同时引入 Spring Boot 官方的
对应模块 / starter：

| 想做的事 | Boot 2/3 的写法 | **Boot 4 必须这样** |
|---|---|---|
| 引入 Flyway | `org.flywaydb:flyway-core` | **`org.springframework.boot:spring-boot-starter-flyway`** |
| MockMvc 测试 | `spring-boot-starter-test` 自带 | 另加 **`spring-boot-starter-webmvc-test`** |

**症状**：加了 `flyway-core` 后应用照常启动、测试照常跑，但 **Flyway 一条迁移都不执行**，
日志里连 Flyway 字样都没有 —— 因为 `FlywayAutoConfiguration` 根本不在 classpath 上。

**排查陷阱**：Spring Boot 启动时如果**没有任何组件用到 DataSource**，
HikariPool 会推迟到首次使用时才初始化。日志里 `Started ItsMyGoApplicationTests`
出现在 `HikariPool - Starting...` **之前**，就是「数据源没人用」的信号 ——
所以**「应用启动成功」不代表数据源和迁移就绪**。

**包路径也变了**（Boot 4 模块化后）：

| 类 | Boot 3 及以前 | Boot 4 |
|---|---|---|
| `AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | **`org.springframework.boot.webmvc.test.autoconfigure`** |

**规矩**：以后加任何新集成（Spring Security、springdoc 等），**先确认 Boot 4 里它的自动配置
在哪个模块**，别直接照抄 Boot 3 的教程。

### 5.2 共用 Redis 的内存策略约束

`redis` 容器以 `--maxmemory 256mb --maxmemory-policy volatile-lru` 启动。
该策略下**只有带 TTL 的 key 会被淘汰** —— 本项目若写入**不带 TTL 的 key**，
内存打满时写入会**直接报错失败**，而不是被淘汰。

**因此：本项目所有 Redis key 必须带 TTL，且一律以 `its-mygo:` 开头。**
（已有回归用例 `SharedRedisTest` 守着这两条。）

### 5.3 库存扣减（M3 的关键点）

并发下不能超卖。方案已定：**Redis 预扣 + 数据库乐观锁**
（`UPDATE ... WHERE stock >= n` 或版本号）。
**不要**写成「先 select 查库存、再 update 扣减」—— 那是面试必被问穿的两步竞态。

### 5.4 订单快照

`order_item` 必须冗余存下单时的**商品名、单价、图片**。
商品后续改价，历史订单不能跟着变。

### 5.5 `git push` 报错但实际成功（本机网络特征，遇见别慌）

本项目已**两次**出现「push 返回错误，但远端其实已经更新成功」：

| 时间 | 报错 | 实际结果 |
|---|---|---|
| 2026-10-12 首次推送 | `remote: Your repository is disabled`（403） | 提交已上传，远端 ref 已建 |
| 2026-10-12 M0 推送 | `cannot lock ref 'refs/heads/main': is at <新值> but expected <旧值>` | 已在远端，本地与远端一致 |

疑似与**本机到 GitHub 的网络链路**有关（响应被截断或延迟）。

**排查规矩**：push 报错后**先 `git fetch origin`、再看 `git status -sb`** 确认真实状态。
**绝不因为报错就 `--force` 强推** —— 那才是真会丢东西的操作。

---

## 6. 历史记录

### 2026-10-12 · M0 骨架完成

**新增依赖**：`spring-boot-starter-web`、`spring-boot-starter-data-redis`、
`mybatis-plus-spring-boot4-starter:3.5.17`、`mysql-connector-j`、
`spring-boot-starter-flyway`、`flyway-mysql:12.4.0`、`lombok`、
`spring-boot-starter-webmvc-test`（测试用）

**新增代码**：`common/`（Result / ResultCode / BizException / GlobalExceptionHandler）、
`entity/User`、`mapper/UserMapper`、`controller/HealthController`；
`db/migration/V1__init.sql`（建 `users` 表）

**新增测试（4 个，全绿）**：
`MyBatisPlusCompatibilityTest`（CRUD）、`SharedRedisTest`（连通性 + 前缀 + TTL）、
`HealthControllerTest`（Web 层 + 统一响应）、`ItsMyGoApplicationTests`（上下文）

**配置调整**：`application.properties` → `application.yml`；凭据移入 `.env`（已 gitignore）；
`.gitignore` 增加 `.env` 排除规则

**踩坑**：Spring Boot 4 模块化（见 §5.1）—— 两条：Flyway 要换官方 starter、
`AutoConfigureMockMvc` 换了包路径

### 2026-10-12 · 立项与准备

- Maven 下载被截断（`Unexpected end of file from server`）→ 配阿里云镜像解决（§4.2）
- `JAVA_HOME` 指向 JDK 8 → 改到 `jbr-17.0.14`（§4.1）
- GitHub 公开仓库建好：https://github.com/Tsuki11111/ItsMyGo
  （账号下已有一个同名空私有仓库，由用户在 IDEA 里创建后改为公开）
- 需求分析 + 技术选型定稿 → `docs/decisions-ecommerce.md`
- 启动共用 `mysql` 容器，建 `its_mygo` 库并授权 `tsuk1` 账号
- 确认复用共用中间件的方案与命名约定（§4.3、§4.4）
