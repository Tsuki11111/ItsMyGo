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

**M5（前端）进行中（2026-10-16）。** 已跑通：**商品列表、商品详情、登录注册、
结算下单（含「立即购买」）、订单详情与支付**；后端 M0–M4 完整，
**自动化测试 76 条**。

前端技术栈：Vite 8 + Vue 3.5 + TypeScript 5.9 + Element Plus 2.14 + Pinia + Vue Router。
配色定稿为「浅粉做面 + 深粉做点」，见 [docs/color-preview.html](docs/color-preview.html)。

**做之前先看 [docs/implementation.md](docs/implementation.md) §4** —— 前端的工程结构、
配色规矩、登录态怎么存、下单链路怎么串、和后端对接的四个约定都在那。
前端**还没有自动化测试**，也没进 CI。

下一步：**购物车页 → 我的订单 → 管理端**。

---

## 2. 下一步做什么：M5 前端的剩余页面

商品列表、商品详情、登录注册、结算下单、订单详情都已完成。按这个顺序往下做：

1. **购物车页** — 改数量、勾选、删除、算总价。顶栏那个「购物车」链接目前还是断的；
   做完它顺便把结算页的**购物车来源**接上（后端和 `Checkout.vue` 的结构都已就绪）
2. **我的订单** — 列表已支持带明细（`GET /api/orders`，`previewLimit` 控制每个订单给几条）。
   订单详情页 `OrderDetail.vue` 已经能用，列表项点进去即可
3. **管理端** — 分类/商品 CRUD、传图、发货、退款审核、超时取消

⚠️ **四个对接约定**（细节见 implementation §4.3）：
响应拦截器按 **body 的 `code`** 判断而不是 HTTP 状态码；库存走独立接口；
下单必须先领提交令牌；**401 会自动清登录态**。

⚠️ **登录态统一走 `stores/user.ts`**（细节见 implementation §4.5）——
判断登录用 `userStore.isLoggedIn`，别在组件里直接读 localStorage。

⚠️ 管理端**直接套 Element Plus 默认样式，别折腾**——后台没人看颜值，时间投在顾客端。

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

- 开发库 `its_mygo`、测试库 `its_mygo_test`（都在共用的 `mysql` 容器里，为什么要分开见 §5.20）
- 迁移脚本在 `src/main/resources/db/migration/`，**当前 schema 版本 v8**
- 已建表：`users`（V1，V3 加 `role`）、`addresses`（V2）、`categories`（V4）、
  `products`（V5）、`cart_items`（V6）、`orders` / `order_items`（V7，V8 加 `status_before_refund`）、
  `payments`（V8）
  （另有 Flyway 自建的 `flyway_schema_history`，是迁移记录表，**不要动**）
- **迁移脚本只增不改**：已执行过的脚本再改内容会让 Flyway 校验失败
- **容器时区已设为 +08:00**（见 §5.14），与开发机一致

---

### 3.4 前端结构

```
frontend/
├── vite.config.ts          # /api 代理到 8080；端口读 `PORT` 环境变量
├── src/
│   ├── api/http.ts         # axios 封装：拦截器按 body 的 code 判断 + 登录凭据存取
│   ├── api/types.ts        # 与后端 DTO 对应的类型（手写）
│   ├── api/{product,cart,auth,address,order}.ts
│   ├── stores/user.ts      # 登录态
│   ├── components/ProductImage.vue   # 商品图 + 无图占位
│   ├── styles/theme.css    # 色板 + Element Plus 主题覆盖（必须在 element-plus 之后引入）
│   ├── router/ · views/ · App.vue · main.ts
```

**启动**（后端要同时跑着）：

```bash
npm run dev          # → http://localhost:5173
```

**演示账号**：`testuser` / `123456`
**种子数据**：[sql/seed-data.sql](sql/seed-data.sql)（**10 个分类、23 个商品**，
型号取自 rag_helper 的产品手册语料）。导入方式：

```bash
docker exec -i mysql sh -c 'mysql --default-character-set=utf8mb4 -utsuk1 -p"$MYSQL_PASSWORD" its_mygo' < sql/seed-data.sql
```

> ⚠️ **导完种子数据要重启后端** —— 布隆过滤器只在启动时预热，
> 应用运行期间手工插的商品不在过滤器里，点详情会被判"不存在"（见 §5.12 的说明）。

**页面结构**（`views/ProductList.vue`）：首屏是「左品类栏 + 右轮播」两栏
（`grid` 190px + 1fr，高 428px），下面才是商品网格。

- 品类栏 11 项（全部 + 10 个分类），图标按**分类名映射**（`ICONS` 表，匹配不上用 `Box` 兜底）。
  那个 428px 是量出来的：内容 `scrollHeight` 375 + 头部，少了就出滚动条
- 轮播用 `el-carousel`，**它的 `height` 参数只认具体像素**，传 `100%` 会塌成空白 ——
  得用 `:deep(.el-carousel__container) { height: 100% }` 撑满
- 窄屏（≤820px）时品类栏收成横向滚动条

**商品详情页**（`views/ProductDetail.vue`）：左图右信息两栏（`grid` 420px + 1fr），
下面接「商品详情」「同类商品」两个区块。

- 库存走 `GET /api/products/{id}/stock`，**和详情分开处理**：详情成功但库存拿不到时，
  页面照常显示，只是数字暂时是旧的
- 同类商品：后端只有分页接口，所以取同分类 8 条后**在内存里**排掉自己、留前 4 条

**登录页**（`views/Login.vue`）：登录 / 注册同页签切换，共用一套校验规则
（约束照抄后端 `RegisterRequest`）。登录成功按 `?redirect=` 回跳，
从「加购」跳过来的能直接回到那件商品。页脚写着演示账号，**真上线要拿掉**。

**结算页**（`views/Checkout.vue`）：选收货地址（页内就能新增）+ 商品清单 + 提交订单。
商品通过 **URL 参数**传（`?productId=X&quantity=N`），刷新页面不丢。
**提交令牌在进页面时就领好**，提交时复用同一个 —— 提交时才领的话连点两次会各领一个，
防重复提交就形同虚设。

**订单详情页**（`views/OrderDetail.vue`）：下单后的落点，待支付时能直接付款。

---

## 4. 环境与约定（已就绪，勿重复劳动）

### 4.0 ⚡ 动手前先确认这些在不在跑

上一次会话结束时**后台跑着这些进程**，但它们随会话结束就可能没了 ——
**先确认再动手，别直接重复启动，会撞端口**：

| 服务 | 端口 | 起来的方式 | 怎么确认 |
|---|---|---|---|
| 后端 | 8080 | `./mvnw spring-boot:run` | `curl http://localhost:8080/api/health` |
| 前端 | 5173 | `cd frontend && npm run dev` | 浏览器开 http://localhost:5173 |
| MySQL / Redis / MinIO | — | `docker start mysql redis minio` | `docker ps` |

**端口被占但进程是僵尸时**（我踩过两次：停后台任务没杀干净 Java 进程）：

```bash
netstat -ano | grep ":8080" | grep LISTENING     # 查 PID
taskkill //PID <pid> //F                          # 杀
```

**启动后端时不要加 `| tail`** —— tail 要等进程结束才输出，会把日志全憋住，
出问题的时候什么都看不到。让日志直接落到后台任务文件里。

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
| MySQL 8.0（`mysql`） | `localhost:3306` | 开发库 `its_mygo`；**测试库 `its_mygo_test`** |
| Redis 7（`redis`） | `localhost:6379` | 开发 db 0、**测试 db 1**；key 前缀一律 `its-my-go:` |
| MinIO（`minio`） | `localhost:9000`（Console `:9001`） | 独立 bucket `its-my-go-product-images` |

**测试库**（为什么这么分见 §5.20）：测试跑在自己的库和 Redis db 上，与开发数据互不影响。
**库要事先建好** —— Flyway 只建表不建库；建库命令写在
`src/test/resources/application-test.yml` 顶部的注释里，直接复制执行即可。

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

### 5.16 ★ TypeScript 7 刚发布，前端工具链还没跟上（2026-10-16 踩到）

`npm install -D typescript` 默认装的是 **7.0.2**（Go 重写的那版），
而 `vue-tsc` 还不支持它，跑类型检查直接抛：

```
Error [ERR_PACKAGE_PATH_NOT_EXPORTED]: Package subpath './lib/tsc' is not defined
  by "exports" in .../typescript/package.json
```

**降到 TypeScript 5.x** 解决：`npm install -D typescript@5`。

这和 §5.1–§5.4、§5.9 是同一类问题：**依赖的"最新版"不一定是"能用的版本"**。
装之前先去 npm 看一眼生态跟没跟上。

### 5.17 在 Windows Git Bash 里给数据库/接口传中文会乱码（2026-10-16 踩到）

两个现场：

| 做法 | 结果 |
|---|---|
| `docker exec mysql -e "INSERT ... '中文'"` | 库里存成 `å¥”å›¾`（latin1 解释 UTF-8） |
| `curl -d '{"nickname":"测试用户"}'` | 后端抛 `Invalid UTF-8 start byte 0xb2` |

**都不是应用的问题**——是 Git Bash 把中文按 GBK 发出去了。
`mysql` 客户端还会另报一个 `character_set_client = latin1`（表本身是 utf8mb4，没毛病）。

**绕法**：
- SQL：写成 UTF-8 文件，`docker exec -i mysql mysql --default-character-set=utf8mb4 ... < file.sql`
- HTTP：测试时用 ASCII；要发中文就用文件 `curl -d @body.json`

**排查提示**：遇到这类问题先确认「是不是我这条命令的编码」，别一上来就怀疑应用。
看 `SHOW VARIABLES LIKE 'character_set_client'` 和响应里的错误字节，能很快定位。

### 5.18 `git push` 报错但实际成功（本机网络特征，遇见别慌）
本项目已**三次**出现「push 返回错误，但远端其实已经更新成功」：

| 时间 | 报错 | 实际结果 |
|---|---|---|
| 2026-10-12 首次推送 | `remote: Your repository is disabled`（403） | 提交已上传，远端 ref 已建 |
| 2026-10-12 M0 推送 | `cannot lock ref 'refs/heads/main': is at <新值> but expected <旧值>` | 已在远端，本地与远端一致 |
| 2026-10-16 测试库那次 | 同上，`cannot lock ref ... is at <新值> but expected <旧值>` | 已在远端，本地与远端一致 |

**第三种报错尤其有迷惑性**：它说「远端现在停在 `<新值>`，我却以为它在 `<旧值>`」——
`<新值>` 正是**你这次要推的提交**，等于直接告诉你「远端已经是新的了」。

疑似与**本机到 GitHub 的网络链路**有关（响应被截断或延迟）。

**排查规矩**：push 报错后**先 `git fetch origin`、再看 `git status -sb`** 确认真实状态。
**绝不因为报错就 `--force` 强推** —— 那才是真会丢东西的操作。

### 5.19 ★ 测试把整张订单表清空了（2026-10-16 踩到并修复）

`StockConcurrencyTest` / `PaymentConcurrencyTest` 的 `@AfterEach` 里原本写着：

```java
orderMapper.delete(null);
cartItemMapper.delete(null);
addressMapper.delete(null);
```

**MyBatis-Plus 的 Wrapper 传 `null` 不是「无条件匹配」，而是「没有 where 条件」** ——
生成的 SQL 就是 `DELETE FROM orders`，整张表清空。

这两个测试刻意不加 `@Transactional`（并发要的是真实提交），所以清理是**真删**；
而**当时开发库和测试库还是同一个库**（当天晚些就分开了，见 §5.20）—— 跑一次测试，
开发时下的订单、加过的购物车、
存过的收货地址全都没了。

**发现过程**：改完「立即购买」跑了一次全量测试，之后发现页面上的购物车空了、
`orders` 表只剩刚下的那一单，而自增 id 已经到 721（说明历史上确实有过很多订单）。

**修法**：按「本次测试造的那些行」删 —— 测试本来就维护了 `createdUserIds`，
顺着它删订单、订单项、支付流水、购物车行、地址即可。
`StockConcurrencyTest` 里那句 `orderMapper.selectCount(null)` 同理，
数全表的话库里只要有别的订单就恒真，等于没测，改成只数本次测试的买家。

**修完验证过**：跑全量测试前后对比开发数据，一条没少。

**教训**：`delete(null)` 长得像「删全部」，所以不容易被当成 bug ——
问题不在于它删了什么，而在于它删了**不属于这次测试**的东西。
写测试清理时都问一句：**这条 SQL 会碰到别人造的数据吗**。

### 5.20 ★ 测试和开发共用一个库，互相踩（2026-10-16 记录并解决）

**症状**：开发库里导入种子数据（10 个分类、23 个商品）之后，
`CategoryControllerTest` / `ProductControllerTest` 有 5 条会失败 ——
它们断言的是**全表条数**，以为库里只有自己造的那一条：

```java
.andExpect(jsonPath("$.data.total").value(1));
```

报 `expected:<1> but was:<24>`。反过来也一样：测试里那些**不加 `@Transactional`**
的并发用例会往库里真写数据，污染开发环境。

**解法：测试跑在自己的库和 Redis database 上。**

| | 开发 | 测试 |
|---|---|---|
| MySQL | `its_mygo` | `its_mygo_test` |
| Redis | db 0 | **db 1** |

配置都在 `src/test/resources/` 下，两个文件：

- **`application.properties`** —— 只写一句 `spring.profiles.active=test`。
  **刻意不写成测试类上的 `@ActiveProfiles`**：两个并发测试类不继承 `ApiTestBase`
  （它们要的是另一套注解组合），用注解一定会漏掉谁。放这里对 classpath 上所有
  `@SpringBootTest` 一视同仁，也不依赖注解在父类上的继承行为
- **`application-test.yml`** —— 覆盖数据源

**Redis 也要分 db 的原因**：库存计数器、商品缓存这些 key 都按**商品 id** 命名。
两个库各自维护自己的自增序列，迟早会撞到同一个 id —— 共用 db 的话，
测试会读到开发环境的库存，反之亦然。

**效果**：本地 76 条全绿（之前 5 红），**连跑两次稳定**，跑完开发库数据一条没少。

**测试库脏了怎么办**：正常不会 —— `@Transactional` 的用例自动回滚，
并发用例自己在 `@AfterEach` 里清理（见 §5.19），实测跑完 `products` / `orders` 都是 0。
真跑脏了就 `DROP DATABASE its_mygo_test` 再重建，Flyway 下次启动会重新建表。

---

## 6. 历史记录

### 2026-10-16 · 测试改用独立的库和 Redis db

**起因**：用户要求「搞个测试库」，解决的正是上一条里记的 §5.20（测试和开发共用一个库，
开发库导了种子数据后 5 条测试失败；反方向则是并发测试往开发库里写数据）。

**做法**：

- 建库 `its_mygo_test`（utf8mb4 / `utf8mb4_0900_ai_ci`，与 `its_mygo` 一致），授权给 `tsuk1`
- `src/test/resources/application.properties` 里写一句 `spring.profiles.active=test`
  —— **刻意不用测试类上的 `@ActiveProfiles`**：两个并发测试类不继承 `ApiTestBase`，
  用注解一定会漏掉谁
- `src/test/resources/application-test.yml` 覆盖数据源，并把 Redis 指到 **db 1**
- CI 的 `MYSQL_DATABASE` 跟着改成 `its_mygo_test`（CI 只跑测试，建这一个库就够）

**为什么 Redis 也要分 db**：库存计数器、商品缓存都按**商品 id** 命名，
两个库的自增序列各走各的，早晚撞到同一个 id。

**效果**：本地 76 条全绿（之前 5 红），**连跑两次稳定**，跑完开发库数据一条没少。
顺带改正了 §4.3 中间件表格里两处 `its-mygo` 的笔误（正确写法是 `its-my-go`）。

### 2026-10-16 · 立即购买 + 结算页 + 订单详情

**起因**：用户要在商品详情页「加入购物车」旁边加一个「立即购买」。

**先确认了语义**：主流电商（淘宝 / 京东）的「立即购买」是**跳过购物车直达确认订单**。
所以后端下单接口得支持两种商品来源，而不是「前端先加购再结算」——
后者会让购物车里出现用户没主动加过的记录。

**后端**：`CreateOrderRequest` 加可选 `items`。为空 → 从购物车勾选项下单并清空这些行；
传了 → 「立即购买」，**完全不碰购物车**。两种来源在 Service 里归一到同一个 `OrderLine`，
之后共用库存扣减、快照、令牌那一整套，唯一的分叉就是最后那句「要不要清购物车」。
**新增测试 3 条**：不动购物车、超库存被拒、中途失败要回补 Redis 预扣。

**前端**：详情页加「立即购买」按钮（实心 / 描边拉开层级）；新增 `Checkout.vue`
（选地址 + 内联新增地址 + 商品清单 + 提交）、`OrderDetail.vue`（订单信息 + 支付）、
`api/address.ts`、`api/order.ts`。链路是
详情页 → `/checkout?productId=X&quantity=N` → `/order/:id`。

**顺带修了一个真 bug**（详见 §5.19）：两个并发测试的 `@AfterEach` 用
`xxxMapper.delete(null)` 清数据，在 MyBatis-Plus 里那等于 `DELETE FROM 整张表` ——
跑一次测试就把开发库的订单、购物车、地址全抹掉。改成按本次测试的 `createdUserIds` 删。

**验证**：浏览器里真走了「未登录点立即购买 → 跳登录 → 回跳 → 结算 → 新增地址 → 下单 → 支付」
全流程；另外用接口确认**立即购买前后，购物车里那条商品和它的库存纹丝不动**。

**当时的遗留**：§5.20 —— 测试和开发共用一个库。**当天晚些就解决了**，见下一条。

### 2026-10-16 · M5 前端续：商品详情页 + 登录注册

**新增页面**：`ProductDetail.vue`（左图右信息 + 实时库存 + 加购）、
`Login.vue`（登录 / 注册同页签切换）。

**新增前端基建**：

- `stores/user.ts` —— 登录态的 Pinia store。凭据存 localStorage
  （`its-my-go:token` / `its-my-go:user`），**`api/http.ts` 是这两个 key 的唯一归属地**
- `api/cart.ts`、`api/auth.ts`
- `components/ProductImage.vue` —— 无图占位从列表页抽出来，列表 / 详情两处共用
- **401 自动登出**：`http.ts` 收到 401 就清凭据并广播事件，store 监听后同步内存状态。
  没有这一步，用户会卡在「顶栏显示已登录、但每个操作都报错」的状态里

**顺带接上的**：列表页卡片的「加入购物车」按钮（上一轮留下的空壳，点了没反应）
与卡片点击进详情。

**改了一处工具链**：`vite.config.ts` 的端口改成读 `PORT` 环境变量（没设时仍是 5173），
`.claude/launch.json` 开 `autoPort` —— 为了在浏览器里验证时不和**另一个会话已经占着 5173**
的 dev server 撞车。

**验证**（浏览器里真跑过一遍）：登录 → 带 `redirect` 回跳 → 加购 → 查购物车接口确认数据落库
→ 退出登录；另外看了商品不存在时的空状态。**前端仍无自动化测试。**

### 2026-10-16 · M5 前端起步：工程骨架 + 商品列表页

**先讨论了设计再动手**（用户明确要求"探讨一下前端设计"）：

- **一个工程还是两个** → 选一个（顾客端 `/` + 管理端 `/admin` 路由区分），
  两端共用 TypeScript 类型和 axios 封装
- **TypeScript 类型怎么对齐后端** → 手写（十几个 DTO，量不大）
- **订单列表缺明细** → 顺着这个问题聊了真实电商的做法：**1 条查订单 + 1 条 IN 查明细 +
  内存分组**，而不是 `JOIN ... LIMIT`（那会分页错乱，因为 LIMIT 限制的是 JOIN 后的行数）。
  已按这个改掉 `pageForCustomer`，加了 `previewLimit` 参数和 `itemCount` 字段，
  并补了测试验证「3 个订单只发 1 条明细 SQL」
- **配色** → 做了 [docs/color-preview.html](docs/color-preview.html) 三方案对比，
  用户选了「浅粉做面 + 深粉做点」

**产出**：Vite 8 + Vue 3.5 + TS 5.9 + Element Plus 2.14 + Pinia + Vue Router 的骨架；
`theme.css` 色板（含 Element 主题覆盖）；axios 封装；商品列表页；
种子数据（8 个商品，型号取自 rag_helper 手册语料）。
**目前还没有前端自动化测试。**

**踩了两个坑**（记进 §5.16 / §5.17）：TypeScript 7 生态没跟上、Git Bash 传中文乱码。

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
