# HANDOFF · 一次买够（ItsMyGo）

> **读者**：下一个接手本项目的 Claude 会话
> **性质**：本项目**状态层的权威源** —— 做到哪、下一步做什么、踩过哪些坑。
> [README.md](README.md) 是它的**精简报告**（内容以本文为准，两者允许重复）；
> [docs/implementation.md](docs/implementation.md) 讲**代码具体怎么实现的**；
> 需求分析与选型理由见 [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md)
>
> **改动本项目时**：先改 implementation.md（实现变了就必须改），再改本文，最后同步 README。

---

## 0. 先读这些（按顺序）

1. **[README.md](README.md)** —— 项目是什么、技术栈、架构、部署方式。**先读它**。
2. **本文件** —— 现在做到哪、下一步做什么、哪里有坑。
3. **[docs/decisions-ecommerce.md](docs/decisions-ecommerce.md)** —— 需求分析、选型理由、
   分阶段路线的**唯一权威**。只要涉及「接下来做什么」，先读它。
4. **[docs/implementation.md](docs/implementation.md)** —— **代码具体怎么实现的**：
   一条请求怎么流转、认证全链路、各业务模块的实现要点与关键取舍、测试怎么写的。
   **动代码之前先读它**，别重复造已有的机制。
5. **涉及客服 / agent / gRPC 对接时**，还必须读外部仓库
   `D:\PycharmProjects\rag_helper`：
   - `evolution-plan.md` **§5（gRPC 接口）、§6（平台对接前提）** —— 对接契约的唯一权威
   - `proto/agent.proto` —— Java 侧生成 stub 的凭据

---

## 1. 当前状态一句话

**M4（支付与售后）已完成（2026-10-16）。**

M0 → M4 五段全部落地，**自动化测试 70 个全绿**。
支付流水、退款（申请/同意/驳回）、超时未支付自动取消都已跑通；
**订单状态流转改成原子条件更新**（并发取消不再回滚多次库存）。
数据库 schema 到 **v8**。

下一步：**M5（前端）**。

---

## 2. 下一步做什么：M5 · 前端

参考工期 1–2 周。后端已经完整（M0–M4），终于轮到界面了。

1. **顾客端**（Vue 3 + TypeScript + Element Plus + Pinia）
   - 注册登录、商品列表/搜索/详情、购物车、结算下单、我的订单
   - **下单流程要带提交令牌**：进结算页先 `GET /api/orders/token`，提交时带上（见 implementation §3.7）
   - **库存要调独立接口** `GET /api/products/{id}/stock`，别用详情里的 stock 字段（它虽然会被替换成实时值，但独立接口语义更清楚）
2. **管理端**：分类/商品 CRUD、图片上传、订单列表与发货、**退款审核**
3. 跨域：本地开发时 Vite 需要 proxy 到 8080，或者后端配 CORS
4. 验收：浏览器里能走完「浏览 → 加购 → 下单 → 支付」，管理端能发货与审批退款

⚠️ 后端接口清单见 [docs/implementation.md](docs/implementation.md) 与各 Controller；
**统一响应格式是 `{code, message, data}`，HTTP 状态码不总是语义化的**
（401/403 是真状态码，400/404/429 在 body 的 code 里）—— 前端拦截器按 body code 判断更稳。

---

## 3. 项目结构

> **本文只记结构，不解释实现** —— 每个机制具体怎么落地的见
> [docs/implementation.md](docs/implementation.md)。

### 3.1 包结构（单模块 Maven）

```
src/main/java/com/tsuk1/itsmygo/
├── ItsMyGoApplication.java      # @SpringBootApplication + @MapperScan("com.tsuk1.itsmygo.mapper")
├── common/                      # Result / ResultCode / Roles + exception/（BizException / GlobalExceptionHandler）
├── config/
│   ├── SecurityConfig.java      # 过滤器链 + BCrypt + 401/403 统一响应 + 路径权限
│   ├── MyBatisPlusConfig.java   # ★ 分页拦截器（不注册分页就不生效，见 §5.3）
│   └── MinioConfig.java         # MinioClient bean
├── security/                    # CurrentUser（id+username+role）/ JwtService / JwtAuthenticationFilter
├── controller/
│   ├── HealthController / AuthController / AddressController
│   ├── CategoryController / ProductController / CartController / OrderController        # 顾客端
│   └── AdminCategoryController / AdminProductController / AdminFileController /
│       AdminOrderController                                                             # 管理端，需 ADMIN
├── service/                     # 接口：Auth / Address / Category / Product / Storage / Cart / Order / Stock
│   └── impl/                    # 实现：…ServiceImpl（@Service 标在这层，Controller 注入接口）
├── mapper/                      # MyBatis-Plus BaseMapper（User / Address / Category / Product / CartItem / Order / OrderItem）
├── entity/                      # User / Address / Category / Product / CartItem / Order / OrderItem / OrderStatus
└── dto/                         # 请求响应体
```

**接口分层**：

| 路径前缀 | 权限 |
|---|---|
| `/api/auth/register`、`/api/auth/login`、`/api/health` | 匿名 |
| `/api/products/**`、`/api/categories/**` 的 **GET** | 匿名（顾客浏览不用登录） |
| `/api/admin/**` | `hasRole("ADMIN")` |
| 其余 | 登录即可 |

**代码约定**：

- Controller 只做参数接收与响应包装，业务判断一律在 Service
- 取当前用户统一用 `@AuthenticationPrincipal CurrentUser user`，
  **不要**自己去读 `SecurityContextHolder`
- **越权校验只在 Service 里做**（例：`AddressService.requireOwned`）；
  别人的数据一律当「不存在」返回 **404，不用 403** —— 403 等于告诉调用方
  「这条数据存在，只是不属于你」

### 3.2 配置与凭据

- 配置文件是 `src/main/resources/application.yml` —— **原来的 `application.properties` 已删除**
- 凭据放项目根目录的 **`.env`**，由 `application.yml` 的
  `spring.config.import: optional:file:.env[.properties]` 引入
- `.env` 已被 `.gitignore` 排除；仓库里只留 `.env.example`
- 变量名：`DB_USERNAME`、`DB_PASSWORD`

### 3.3 数据库

- 库名 `its_mygo`（在共用的 `mysql` 容器里）
- 迁移脚本在 `src/main/resources/db/migration/`，**当前 schema 版本 v8**
- 已建表：`users`（V1，V3 加 `role`）、`addresses`（V2）、`categories`（V4）、
  `products`（V5）、`cart_items`（V6）、`orders` / `order_items`（V7，V8 加 `status_before_refund`）、
  `payments`（V8）
  （另有 Flyway 自建的 `flyway_schema_history`，是迁移记录表，**不要动**）
- **迁移脚本只增不改**：已执行过的脚本再改内容会让 Flyway 校验失败
- **容器时区已设为 +08:00**（见 §5.14），与开发机一致

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

**开工前先确认这三个容器都在跑** —— 它们不在本项目任何 Compose 里，
Docker Desktop 重启后不保证自动拉起：

```bash
docker ps --filter "name=^(mysql|redis|minio)$" --format "table {{.Names}}\t{{.Status}}"
```

缺哪个就拉哪个（已在运行的会提示 "already running"，无害）：

```bash
docker start mysql redis minio
```

| 容器 | restart policy | 实际行为 |
|---|---|---|
| `mysql` | `unless-stopped` | 一般能自动起；**但被显式 stop 过就不会** —— 它曾停摆两个月（2026-10-12 才拉起） |
| `redis` | `no` | **不会**自动起，必须手动 |
| `minio` | `no` | **不会**自动起，必须手动 |

| 中间件 | 连接 | 本项目隔离方式 |
|---|---|---|
| MySQL 8.0（`mysql`） | `localhost:3306` | 独立 database `its_mygo` |
| Redis 7（`redis`） | `localhost:6379` | key 前缀 `its-mygo:` |
| MinIO（`minio`） | `localhost:9000`（Console `:9001`） | 独立 bucket `its-mygo-product-images` |

- `mysql` 容器里还有 `dw` / `meta` / `pharma_db` 三个**别的项目**的库，**不要动**
- **MinIO 是 rag_helper 在用的实例**：它的 bucket 是 `rag-helper-knowledge-files`
  （已配公网只读策略），本项目**不许读写它**；本项目一律用 `its-mygo-product-images`
- MinIO 凭据与 rag_helper 共用（在容器环境变量里），M2 落地对象存储时写进 `.env`
- ⚠️ 该实例用的是 MinIO **默认凭据**，且端口绑定在 `0.0.0.0`。
  本机开发可接受，但**别把 9000 / 9001 暴露到公网**

### 4.4 命名约定（硬约束）

本项目**若**创建任何 Docker 资源（卷 / 网络 / 容器）或其他外部资源（MinIO bucket、
数据库 schema），名字一律以 **`its-my-go`** 开头。

⚠️ **前缀是 `its-my-go`（三个词、两个连字符）** —— 不是 `itsmygo`，也不是 `its-mygo`。
用户最初口述时写的是 `its-mygo`，2026-10-13 已确认统一用 `its-my-go`。

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

### 5.2 ★ Spring Boot 4 默认用 Jackson 3，包名变了（2026-10-12 踩到）

**`ObjectMapper` 的包名从 `com.fasterxml.jackson.databind` 变成了 `tools.jackson.databind`。**

Boot 4.1.1 的 JSON 实现是 **Jackson 3**（依赖树里是 `tools.jackson.core:jackson-databind:3.1.5`）。
照抄 Boot 3 的代码写 `import com.fasterxml.jackson.databind.ObjectMapper`，
**编译直接失败**：`程序包 com.fasterxml.jackson.databind 不存在`。

**最容易误判的地方**：依赖树里**同时存在** `com.fasterxml.jackson.core:jackson-databind:2.21.5` ——
那是 `jjwt-jackson` 带进来的 **Jackson 2**，scope 是 `runtime`，**不是** Boot 自己用的那个。
别被它误导以为包名没变。

**规矩**：在 Boot 4 项目里写 Jackson 相关代码，一律用 **`tools.jackson.*`**。

### 5.3 ★ MyBatis-Plus 3.5.9+ 把分页插件拆出去了（2026-10-13 踩到）

**只加 `mybatis-plus-spring-boot4-starter` 是不够的。** 从 3.5.9 起，分页等功能连同
JSqlParser 一起被拆成独立模块，得显式补：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

**症状**：编译报 `找不到符号: 类 PaginationInnerInterceptor` ——
`mybatis-plus-extension` 的 `plugins/inner` 包下只剩 `OptimisticLockerInnerInterceptor` 等。

**还有一件必须做的事**：分页拦截器**不会自动注册**。不写下面这个 bean，
`selectPage` 既不拼 `LIMIT` 也不查总数，**`total` 恒为 0**：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

### 5.4 ★ MinIO SDK 8.6.0 带的 okhttp 5 拉不到实际 class（2026-10-13 踩到）

`io.minio:minio:8.6.0` 依赖 `okhttp:5.1.0`，但那个 jar **只有 1528 字节、3 个文件**
（`kotlin-project-structure-metadata.json` 之类）—— 它是 **Kotlin Multiplatform 的元数据空壳**，
真正的 class 在 `okhttp-jvm` 变体里。**Maven 不读 Gradle Module Metadata**，
所以只拿到壳，编译时报 `无法访问 okhttp3.HttpUrl`。

（`okio` 没这问题，因为它的 pom 显式声明了 `okio-jvm`；okhttp 没有。）

**本项目的解法：用 `io.minio:minio:8.5.17`** —— 依赖纯 Java 的 okhttp 4.12，
依赖链干净，也不会把 Kotlin 运行时拖进 classpath。

将来若要升到 8.6+，得显式补 `com.squareup.okhttp3:okhttp-jvm`。

### 5.5 共用 Redis 的内存策略约束

`redis` 容器以 `--maxmemory 256mb --maxmemory-policy volatile-lru` 启动。
该策略下**只有带 TTL 的 key 会被淘汰** —— 本项目若写入**不带 TTL 的 key**，
内存打满时写入会**直接报错失败**，而不是被淘汰。

**因此：本项目所有 Redis key 必须带 TTL，且一律以 `its-mygo:` 开头。**
（已有回归用例 `SharedRedisTest` 守着这两条。）

### 5.6 库存扣减（M3 已实现，细节见 implementation §3.8）

并发下不能超卖。已落地**两层防线**：Redis Lua 原子预扣（挡流量）+
数据库 `UPDATE ... WHERE stock >= ?`（**最终真相**）。

**不要**写成「先 select 查库存、再 update 扣减」—— 那是面试必被问穿的两步竞态，
并发下会互相覆盖（丢更新）。

回归保障：`StockConcurrencyTest` —— 10 个买家并发抢 5 件，断言恰好 5 成功。

### 5.7 ★ Redis 和数据库不在同一个事务里（2026-10-15 立规矩）

**`@Transactional` 只回滚数据库，不会回滚 Redis。** 任何「先动 Redis 再动数据库」
的写入逻辑，都必须在 catch 里手动补 Redis。

本项目下单流程的做法：用一个 `Map<Long, Integer> reserved` 记下已扣过的商品，
异常时 `reserved.forEach(stockService::release)`；数据库那边交给 `@Transactional`。

**回补时只补已存在的键** —— 键过期后再 `INCRBY` 会凭空造出一个错误的库存值。

### 5.8 订单快照

`order_items` 必须冗余存下单时的**商品名、单价、图片**；`orders` 还要冗余存
**收货人/电话/地址**。商品改价、用户改地址，历史订单都不能跟着变。

### 5.9 Boot 4 把 AOP 的 starter 改名了（2026-10-15 踩到）

**`spring-boot-starter-aop` 在 `4.0.0-M2` 之后就不发布了**，现在叫
**`spring-boot-starter-aspectj`**。按老名字加依赖会解析不到。

这是本项目踩到的**第四个** Boot 4 模块化差异，前三个见 §5.1（自动配置拆模块）、
§5.2（Jackson 3 包名）、§5.3（MyBatis-Plus 分页拆包）。

**规矩不变**：加任何新集成前，先去 Maven 仓库确认 Boot 4 里它叫什么、还在不在。

### 5.10 测试类继承基类要一致（2026-10-15 踩到）

`ApiTestBase` 是 M2 才引入的，而 `AuthControllerTest` / `AddressControllerTest`
是 M1 写的 —— 它们**没继承基类**，导致基类里的清理逻辑对它们从来没生效过。

加了限流之后这个问题才暴露：这两个类的用例累积注册计数，跑几个就撞 429。
排查绕了很久，因为「单独跑通过、全量跑失败」很容易误判成顺序问题或残留数据。

**教训**：
1. 引入基类后要**回头把老测试类也接上**，别只给新测试用
2. 共享状态（Redis 计数器、缓存）的清理放在**实际要打接口的 helper 里**，
   比依赖 `@BeforeEach` 可靠 —— 后者在抽象父类上的行为不如预期
3. **抽象父类上的 `@TestPropertySource` / `@ActiveProfiles` 不要指望它可靠**：
   实测出现过同一个类里既有 `开关=true` 又有 `开关=false` 的情况

### 5.11 Windows 开发 + Linux CI：`mvnw` 缺可执行位（2026-10-15 踩到）

CI 跑 `./mvnw` 报 **`Permission denied`（exit 126）**。

**Windows 上的 git 不记录 Unix 可执行位**，checkout 出来是 `100644`，
到了 Linux 就不能直接执行。

修法是改 **git 索引里的模式**，而不是在 CI 里 `chmod +x` 绕过去：

```bash
git update-index --chmod=+x mvnw
git ls-files -s mvnw        # 应该显示 100755
```

这样任何人 clone 下来都是可执行的。同理适用于任何需要在 Linux 上执行的脚本。

### 5.12 MinIO 在 CI 里起不来（2026-10-15 踩到，尚未解决）

**MinIO 2025 年停止了官方镜像与二进制的公开分发**：

| 源 | 结果 |
|---|---|
| `minio/minio`（Docker Hub） | `denied: requested access to the resource is denied` |
| `bitnami/minio:latest` | `manifest unknown`（tag 已下架） |
| `dl.min.io/.../minio`（二进制） | **`410 Gone`** |

你本地能跑是因为镜像早就拉下来了，CI 上从零开始就拉不到。

**当前的应对**：`StorageServiceTest` 用 `@DisabledIfEnvironmentVariable(SKIP_MINIO_TESTS)`
跳过，CI 里设 `SKIP_MINIO_TESTS=true` —— **CI 跑 57 条，本地跑全部 60 条**。
这是**已知并记录的缺口，不是遗漏**。

**将来要补的话**，可选方向：找 MinIO 的历史镜像 tag、换成别的 S3 兼容实现
（如 LocalStack / SeaweedFS）、或者把上传逻辑抽象成接口后在测试里用假实现。

### 5.13 缓存击穿：用互斥锁护住缓存加载（2026-10-15 补）

库存键过期的那一瞬间，N 个并发会同时发现键不存在、**一起打向数据库** ——
而「不让请求打到数据库」恰恰是这层缓存存在的意义。
`setIfAbsent` 只能保证写入唯一，**挡不住重复查询**。

解法：**SETNX 抢加载锁 + 双重检查 + 落锁的线程自旋等待**（细节见 implementation §3.8）。

**实测**：`StockCacheBreakdownTest` 用 20 并发 + 故意 200ms 慢查询，
有锁时数据库只被查 **1** 次，临时去掉锁会变成 **20** 次。

**写任何「缓存未命中就回源」的逻辑都要过一遍这个检查** —— 不只是库存，
商品详情、分类树将来加缓存时同样适用。

## 本项目已知但**尚未处理**的高并发问题

按优先级记在这里，动手前先看有没有已解决的：

| 接口限流 | ✅ 已解决 | 注解式（AOP + Redis Lua 固定窗口），见 implementation §3.11 |
| ~~重复提交订单~~ | ✅ 已解决 | 一次性提交令牌（`GET /api/orders/token` + `DEL` 原子消费），见 implementation §3.7 |
| ~~缓存穿透~~ | ✅ 已解决 | 布隆过滤器（`ProductBloomFilter`），见 implementation §3.9 |
| ~~缓存雪崩~~ | ✅ 已解决 | 缓存 TTL 统一带 ±抖动，见 implementation §3.9 |
| ~~查询无缓存~~ | ✅ 已解决 | 商品详情 + 分类树上 Cache-Aside，见 implementation §3.10 |
| ~~库存显示滞后~~ | ✅ 已解决 | 独立库存接口，直读 Redis 计数器，见 implementation §3.10 |
| 订单号冲突 | ⚠️ 靠唯一索引兜底 | `yyyyMMddHHmmss` + 6 位随机，高并发下会撞，目前是直接报错而非重试 |
| 连接池 | ⚠️ 用默认 | HikariCP 默认 10 连接，压测前要调 |
| 限流多实例 | ⚠️ 单体无碍 | 当前限流按 IP/用户计数，多实例下共享 Redis 所以正确；但 `@Scheduled` 建布隆过滤器会各跑一遍 |

**高并发清单到此清空**（剩下的两项都是「知道但暂时不必做」）。

⚠️ **前端接下单时必须先领令牌**：`POST /api/orders` 现在要求 `token` 字段，
不传会被拒。流程是「进结算页 → `GET /api/orders/token` → 提交时带上」。

**三个缓存相关的坑**（当天全踩了，细节见 implementation §3.10 末尾）：

1. **库存显示**已彻底与缓存解耦：新增 `GET /api/products/{id}/stock` 走 Redis 计数器
   （不查库），详情接口里的 `stock` 也换成实时值。
   **代价是新增一个同步点** —— `ProductServiceImpl.update` 改完库存必须
   `stockService.reload(id)`，否则管理员补货后顾客仍看到旧数字（这个 bug 是测试抓出来的）
2. **布隆过滤器漏手工插库的商品**：`POST /api/admin/products/rebuild-bloom` 手动重建 +
   `@Scheduled` 每天兜底。**重建必须「临时 key + RENAME」原子切换**，
   不能「先删再建」（那样中间所有商品都会被判为不存在）
3. **缓存不参与事务回滚** —— 测试里 `@Transactional` 回滚了数据库，缓存里那份还在；
   生产环境回滚事务后同样要记得缓存可能已脏

⚠️ **布隆过滤器有两个维护点**，改商品逻辑时别漏（细节见 implementation §3.9）：
新商品创建要增量 `add`；应用启动要 `ApplicationRunner` 预热 ——
预热失败会让过滤器把**所有**请求判为「不存在」，比没有过滤器更糟。

### 5.14 ★ 数据库和应用差 8 小时：MySQL 容器跑在 UTC（2026-10-16 踩到）

**症状**：刚下的订单被超时任务当成「已经放了 8 小时」**立刻取消**。

**根因**：`mysql` 容器时区是 `SYSTEM`（容器内 = UTC），开发机是 UTC+8。
`orders.created_at` 由数据库默认值写入（UTC），而 `paid_at` 等由应用写（UTC+8）——
**同一行订单里的时间戳互相差 8 小时**。

**已修**（不用重建容器，那会丢配置，而且里面还有别的项目的库）：

```bash
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "SET PERSIST time_zone = \"+08:00\";"'
```

`SET PERSIST` 会写进 `mysqld-auto.cnf`，**容器重启后仍然生效** ✓
（`SET GLOBAL` 只改内存，重启就没了。）

⚠️ 两个注意点：
1. **只对新连接生效**。已建立的连接（比如正在跑的应用）还是旧时区，改完要重启应用
2. 用 `+08:00` 而不是 `Asia/Shanghai` —— 后者需要容器里导入时区表，默认没有。
   中国没有夏令时，写死偏移量没副作用

**即使时区统一了，规矩仍然成立**：比较时间时**让两边来自同一个时钟源**。
超时判断已经改成用数据库的 `NOW()`（`created_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)`），
而不是拿 JVM 的 `LocalDateTime.now()` 去比 —— 后者默认两边时区一致，这个假设迟早会破。

⚠️ **历史数据**：改时区前写入的 `created_at` 是按 UTC 存的，现在按 +08:00 读会显示早 8 小时。
本地都是测试数据，无所谓；真项目里这种改动要连带做数据迁移。

### 5.15 测试并发要挑「没有唯一索引兜底」的场景（2026-10-16 踩到）

修状态流转的原子性时写了个并发支付测试，跑绿了。**但变异验证（把条件更新去掉）发现它照样通过** ——
因为 `payments.uk_order_id` 唯一索引把并发挡住了，测试**根本没测到状态机**。

换成**并发取消**才抓出问题：没有唯一索引兜底，五个并发请求会执行五次 `restoreStock`，
**库存凭空多出 4 件**。去掉条件更新后这条测试立刻失败（期望 1 个成功，实际 5 个）。

**教训**：写并发测试先问一句「这里有没有别的机制会兜住？」——
被兜底掩盖的"通过"比失败更危险，它会让人以为防线已经生效了。

### 5.16 `git push` 报错但实际成功（本机网络特征，遇见别慌）
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

### 2026-10-16 · M4 支付与售后完成

**数据库**：`V8__payment.sql` —— 新建 `payments`（含 `uk_order_id` 唯一索引）、
`orders` 加 `status_before_refund` → schema 到 **v8**

**新增代码**：`entity/Payment`、`mapper/PaymentMapper`；
`OrderStatus` 加 `REFUNDING` / `REFUNDED` 两个状态；
`OrderMapper.compareAndSetStatus`（条件更新）；
`OrderServiceImpl` 的 `pay` 落流水、`applyRefund` / `approveRefund` / `rejectRefund`、`cancelExpired`；
Controller 加 4 个接口。

**新增测试 10 条**（共 70 个全绿）：`OrderControllerTest` 加 8 条（支付流水、退款全流程、
驳回回退、未支付不能退款、不能重复退款、顾客不能审批、超时取消、新单不被误伤），
新增 `PaymentConcurrencyTest` 2 条（**并发支付**、**并发取消**）。

**修了两个真 bug**（都记进了 §5）：

1. **状态流转不原子** —— 原先「读出来判断 → 写回去」两步之间有窗口。
   M3 时后果不明显，M4 加了支付流水后变成真资损：**并发取消会执行多次 `restoreStock`，
   库存凭空多出来**。改成 `WHERE status = 期望值` 条件更新。
   **变异验证证明了这条防线必要**：去掉条件更新后并发取消测试立刻失败（1 个成功变 5 个）
2. **数据库和应用差 8 小时** —— MySQL 容器跑 UTC、应用跑 UTC+8，
   刚下的订单被超时任务当成「已经 8 小时」立刻取消。
   用 `SET PERSIST time_zone='+08:00'` 统一了时区；超时判断也改成用数据库的 `NOW()` 做基准

**还学到一个测试教训**（§5.15）：并发测试要挑**没有唯一索引兜底**的场景 ——
我最初的并发支付测试被 `uk_order_id` 掩盖成"通过"，根本没测到状态机。

### 2026-10-15 · 配上 GitHub Actions CI

`.github/workflows/test.yml`：每次 push / PR 在全新 Ubuntu 上跑 `./mvnw test`，
用 `services` 起 MySQL 与 Redis（端口映射到 localhost，正好与应用配置一致，
**应用配置一行都没改**）。公开仓库，Actions 免费。

**跑通之前踩了三个坑**（都记进了 §5）：

1. `bitnami/minio:latest` 已从 Docker Hub 下架（manifest unknown）
2. 换官方 `minio/minio` 也不行 —— **MinIO 2025 年停止了官方镜像与二进制的公开分发**
   （Docker Hub 返回 denied、`dl.min.io` 返回 410 Gone）。
   最终让 `StorageServiceTest` 在 CI 跳过（57/60），本地仍全跑
3. **`./mvnw` 报 Permission denied** —— Windows 的 git 不记录可执行位，
   用 `git update-index --chmod=+x mvnw` 修索引里的模式（而不是在 CI 里 chmod 绕过）

README 顶部加了徽章：CI 状态（**真实**，来自 GitHub 自家域名）+ Stars + Last commit
+ 技术栈。**CI 徽章的意义在于它是真的** —— 点进去能看到每次跑了什么、耗时多久、日志长什么样。

### 2026-10-15 · 接口限流

**注解式**（`@RateLimit` + AOP + Redis Lua 固定窗口）：注册按 IP 5/分（防批量注册）、
登录按 IP 10/分（**防密码爆破**）、下单按用户 10/分、上传按用户 20/分。
带总开关 `itsmygo.ratelimit.enabled`（出事可一键止血）。

**踩了两个坑**（都记进了 §5）：

1. **Boot 4 把 `spring-boot-starter-aop` 改名成了 `spring-boot-starter-aspectj`**
   —— 这是本项目遇到的第四个 Boot 4 模块化差异
2. **`AuthControllerTest` / `AddressControllerTest` 没继承 `ApiTestBase`**（M1 写的，
   基类 M2 才有），基类的清理对它们从来没生效过。加了限流才暴露出来：
   这两个类的用例累积注册计数，跑几个就撞 429。
   排查绕了很久 —— 「单独跑通过、全量跑失败」很容易误判成顺序或残留问题

**解法**：把清限流计数放进 `customerToken` / `adminToken` 这些实际打认证接口的 helper 里，
比依赖 `@BeforeEach` 可靠。试过但没用的：`@ActiveProfiles` + `application-test.yml`、
`@TestPropertySource` 标抽象父类（上下文缓存的表现不如预期）。

**新增 `RateLimitTest` 3 条**，共 **60 个测试全绿**（连续跑两次验证稳定）。
**高并发清单到此清空。**

### 2026-10-15 · 防重复提交（下单幂等）

清单里最要紧的一项 —— 连点两次会生成两笔订单、扣两次库存，是真实资损。

**方案**：一次性提交令牌。`GET /api/orders/token` 领，`POST /api/orders` 带上，
服务端 **`DEL` 原子消费**（返回 1 放行、返回 0 拒绝）。
选它的理由：`DEL` 本身原子，**不需要额外加锁**；
而「请求内容 hash」会误杀用户真想买两次的合法场景。

三个细节：令牌 key 带 userId（别人的用不了）、消费放在业务逻辑之前、
**业务失败要归还令牌**（否则用户遇到「库存不足」改完数量就没法重试了）。

**新增测试 4 条**（共 57 个全绿）：同令牌重放被拒且只扣一次库存、令牌绑用户、
失败后令牌仍可用、无令牌被拒。

⚠️ **前端接下单时必须先领令牌** —— `POST /api/orders` 现在强制要求 `token` 字段。

### 2026-10-15 · 库存拆独立接口 + 布隆过滤器可重建

用户提出两个问题，本轮解决：

**① 库存显示滞后** → 新增 `GET /api/products/{id}/stock`，**数据源是 §3.8 那个库存计数器**
（下单 DECRBY、取消 INCRBY，本来就在实时维护），读它一次库都不用查。
详情接口里的 `stock` 也换成这个实时值。

⚠️ **引入了一个新同步点**：管理员改库存（`ProductServiceImpl.update`）必须
`stockService.reload(id)`，否则计数器还是旧值。**这个 bug 是测试抓出来的** ——
`updateInvalidatesCacheImmediately` 报 `expected: 5 but was: 10`。

**② 布隆过滤器漏手工插库的商品** → `ProductBloomFilter.rebuild()` 全量重建
（按主键游标分批 + 临时 key + `RENAME` 原子切换）、管理端手动接口、`@Scheduled` 每天兜底。

**新增测试 3 条**（共 53 个全绿）：库存实时性（详情与独立接口都要实时）、
管理员改库存立刻可见、重建后能认到手工插库的商品。

### 2026-10-15 · 加 Cache-Aside 缓存（商品详情 + 分类树）

**先讨论了方案**：用户问「能不能用 Redisson 读写锁保证双写一致性」，
结论是**不该用** —— 每次读要付 3 次 Redis 往返（把缓存的收益吃掉了）、
写锁会阻塞所有读、而且**锁只缩小窗口不根除不一致**（删缓存失败照样脏）。
最终选了业界主流的 Cache-Aside。

**新增**：`common/RedisCacheHelper`（读：互斥锁保护回源；写：`evict` 删缓存；TTL 带抖动）；
商品详情缓存（10 分钟）+ 分类树缓存（30 分钟，管理端那份不缓存）。

**新增测试 `CacheAsideTest`（3 条）**：第二次读走缓存（三次读只查一次库）、
**改完立刻读到新值**（验证缓存被删而不是等 TTL）、分类变更后树缓存失效。

**当天踩了三个坑**（都记进了 implementation §3.10）：
缓存不参与事务回滚（测试互相污染）、布隆过滤器不覆盖手工插库的商品、
以及「库存变动不失效详情缓存」这个**刻意保留**的取舍。

顺带清掉了数据库里两条测试残留的分类数据。

### 2026-10-15 · 补缓存穿透与雪崩防护

用户点名要的另两项（击穿已在同日早先补完）：

- **穿透** → `common/ProductBloomFilter`：Redis bitmap 自实现布隆过滤器
  （不用 Guava 本地版 —— 多实例各存一份且重启即丢）。
  6 个 hash 函数走一条 Lua 脚本，`BIT_SIZE = 2^20`。
  集成点：`ProductServiceImpl.create` 增量加入 + `ApplicationRunner` 启动预热。
- **雪崩** → 库存 TTL 从固定 30 分钟改为 **±5 分钟随机抖动**。

新增 `CacheProtectionTest`（3 条）：不存在的 ID 数据库零查询、存在的不被误挡、TTL 确实有抖动。
**47 个测试全绿**。

**踩坑**：Lua 里写 `ARGV[#ARGV + 1]` 取最后一个参数是**越界**（会得到 nil），
正确的是 `ARGV[#ARGV]`；后来改成把 TTL 放 `ARGV[1]`，语义更清楚。

### 2026-10-15 · 补缓存击穿防护（M3 收尾）

用户提出：「虽然是练手项目但也要考虑高并发」。据此给库存缓存的加载加了互斥锁。

**改动**：`StockServiceImpl.loadIfAbsent` 从「查一下没就写」改成
**SETNX 抢锁 + 双重检查 + 未抢到者自旋等待**；
新增 `StockCacheBreakdownTest`（20 并发断言数据库只被查 1 次）。

**做法**：临时去掉互斥锁跑了一次测试，确认会失败（`expected: 1 but was: 20`），
再恢复 —— 证明这个测试真的能测出击穿，不是白跑的。

**同时立下的规矩**：本项目**即使是练手项目也按高并发标准做**，
缓存/并发相关选型不因「反正没流量」而降低要求。

**顺带纠正**：此前文档里写的「数据库乐观锁」措辞不准确 ——
实际用的是**条件更新**（`WHERE stock >= ?`），与乐观锁的区别见 implementation §3.8。

### 2026-10-15 · M3 购物车与下单完成

**数据库**：`V6__cart.sql`（cart_items，含 `uk_user_product` 唯一索引）、
`V7__order.sql`（orders + order_items）→ schema 到 **v7**

**新增代码**：`entity/CartItem`、`entity/Order`、`entity/OrderItem`、
`entity/OrderStatus`（**状态机集中在这一个枚举里**）；
`service/StockService`（Redis Lua 原子预扣）；
`service/CartService`、`service/OrderService` 及其 impl；
`ProductMapper` 加 `deductStock` / `restoreStock` 两条条件更新 SQL；
`CartItemMapper` 加 `addQuantity`（`ON DUPLICATE KEY UPDATE`）；
`controller/CartController`、`OrderController`、`AdminOrderController`

**新增测试（+15，共 43 个全绿）**：`CartControllerTest`（6 条）、
`OrderControllerTest`（8 条，含订单快照与非法流转）、
**`StockConcurrencyTest`（1 条，10 买家并发抢 5 件，恰好 5 成功）**

**踩坑**：本次没踩硬坑（一次跑通）。但有两条**规矩**要记住：
Redis 不在事务里（§5.7）、订单快照不能 join 商品表（§5.8）。

**当天开工前**：`mysql` 容器又处于 stopped 状态，按 §4.3 自检拉起（这是第二次了）。

### 2026-10-13 · Service 层改为接口 + 实现类

按国内主流分层重构：**接口留在 `service` 包，实现移入 `service.impl` 并标 `@Service`**，
Controller 注入接口类型（Spring 自动匹配实现）。5 个 Service 全部改造，测试 28 个仍全绿。

顺带确认资源命名前缀统一为 **`its-my-go`** —— 文档、配置、MinIO 里的 bucket 本来就都是
这个写法，只有一处测试常量误写成 `itsmygo`（已修正）。

另外修掉 pom 里重复声明的 `spring-boot-starter-web`（**Maven 对重复依赖是直接报错
`must be unique`，不是警告**）。

### 2026-10-13 · M2 商品与分类完成

**新增依赖**：`mybatis-plus-jsqlparser`、`io.minio:minio:8.5.17`

**数据库**：`V3__user_role.sql`（users 加 role）、`V4__category.sql`、`V5__product.sql`
→ schema 到 **v5**

**新增代码**：`entity/Category`、`entity/Product`、对应 Mapper；
`service/CategoryService`（内存组树，避免按层查库）、`service/ProductService`、
`service/StorageService`（MinIO 上传 + 懒初始化 bucket + 公开读策略）；
`controller/` 六个（顾客端 CategoryController / ProductController，
管理端 AdminCategoryController / AdminProductController / AdminFileController）；
`config/MyBatisPlusConfig`、`config/MinioConfig`；
`security/` 全面支持 role（CurrentUser 加字段、JWT 带 role、过滤器转 `ROLE_xxx` authority）

**新增测试（+14，共 28 个全绿）**：`ApiTestBase`（基类，提供顾客/管理员 token）、
`CategoryControllerTest`（5 条）、`ProductControllerTest`（6 条）、
`StorageServiceTest`（3 条，含**上传后 URL 可匿名读取**的验证）

**踩坑**：MyBatis-Plus 分页拆包（§5.3）、MinIO 8.6.0 的 KMP 空壳问题（§5.4）；
另外自己犯过一次 YAML 重复顶级键的低级错误（写 `application.yml` 时又开了一个 `spring:`）

**当天开工前**：`mysql` 容器处于 `Exited (137)`（停摆 21 小时），按 §4.3 自检拉起

### 2026-10-12 · M1 账号体系完成

**新增依赖**：`spring-boot-starter-security`、`spring-boot-starter-validation`、
`jjwt-api` / `jjwt-impl` / `jjwt-jackson`（0.13.0）

**新增代码**：`config/SecurityConfig`、`security/`（CurrentUser / JwtService /
JwtAuthenticationFilter）、`service/AuthService`、`service/AddressService`、
`controller/AuthController`、`controller/AddressController`、`entity/Address`、
`mapper/AddressMapper`、`dto/`（4 个请求响应体）；`db/migration/V2__address.sql`

**新增测试（+10，共 14 个全绿）**：
`AuthControllerTest`（6 条：注册登录链路、无 token 401、坏 token 401、
重复用户名、密码错误、参数校验）、
`AddressControllerTest`（4 条：本人 CRUD、**越权访问返回 404**、默认地址唯一、未登录 401）

**配置**：`.env` 增加 `JWT_SECRET`（HS256，随机生成）；`application.yml` 增加 `itsmygo.jwt.*`

**踩坑**：Jackson 3 包名变更（§5.2）；`GlobalExceptionHandler` 需要
`Result.fail(ResultCode, String)` 重载

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
