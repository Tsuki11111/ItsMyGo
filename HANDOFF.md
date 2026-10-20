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

**多商户改造已完成（2026-10-19）。** 商家可自助申请入驻、平台审核；商品归属店铺；
顾客跨店购物车结算时**按店拆单、分别支付**；商家后台管理本店商品 / 订单 / 店铺券 / 评价；
平台后台监管全平台。顾客端包含商品列表、详情（收藏 / 评价 / 店铺卡片 / 联系卖家）、
购物车（按店分组）、结算（按店选券）、多订单结果页、订单详情与售后、
用户中心（收藏 / 足迹 / 券 / 评价 / 消息等）；平台端包含商户审核、分类、商品、订单、
平台券 + 店铺券、评价管理。**在线聊天与私信留言已完成（2026-10-19）**：
顾客 ↔ 商家按店会话、SSE 实时推送、未读角标、已读回执、在线状态、文本 / 图片 /
商品卡片 / 订单卡片，支持离线留言；**平台自营店的会话由平台管理员在
`/admin/messages` 统一接收和回复**。后端 **自动化测试 180 条**。

**交接前先看 `git status`** —— 聊天系统与之前的 Logo 上传、领券中心入口调整
可能还有未提交改动（见 §6 最新一条），提交后再开始新一轮工作。

前端技术栈：Vite 8 + Vue 3.5 + TypeScript 5.9 + Element Plus 2.14 + Pinia + Vue Router。
配色定稿为「浅粉做面 + 深粉做点」，见 [docs/color-preview.html](docs/color-preview.html)；
**管理端 / 商家端直接套 Element Plus 默认样式**。

**做之前先看 [docs/implementation.md](docs/implementation.md) §4** —— 前端工程结构、
配色、登录态、下单链路、四个对接约定、管理端与商家端页面要点都在那。
前端**还没有自动化测试**，也没进 CI。

下一步：**M6 客服接入 → M7 查订单环回**。

---

## 2. 下一步做什么：客服接入

用户中心后续、顾客端订单操作、多商户改造都已完成，本轮不再拆新页面
（佣金 / 分账 / 商家结算、商家子账号、关注店铺明确不做）。

1. **M6 客服接入 → M7 查订单环回** — 先读外部仓库 `D:\PycharmProjects\rag_helper`
   的 `evolution-plan.md` §5/§6 与 `proto/agent.proto`，对接契约以那边为准

⚠️ **四个对接约定**（细节见 implementation §4.3）：
响应拦截器按 **body 的 `code`** 判断而不是 HTTP 状态码；库存走独立接口；
下单必须先领提交令牌；**401 会自动清登录态**。

⚠️ **登录态统一走 `stores/user.ts`**（细节见 implementation §4.5）——
判断登录用 `userStore.isLoggedIn`，别在组件里直接读 localStorage。

⚠️ **管理端不做商品的硬删除**，只提供上下架 —— 删商品会留下购物车/订单/统计的孤儿数据。
后端 `DELETE /api/admin/products/{id}` 还在，但前端不调用。

⚠️ **管理员账号靠 SQL 提权**：`UPDATE users SET role='ADMIN' WHERE username='testuser';`
**提权后必须重新登录** —— JWT 里的 role 是登录时签进去的，改库不会刷新已签发的 token。

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
- 迁移脚本在 `src/main/resources/db/migration/`，**当前 schema 版本 v19**
- 已建表：`merchants`（V16，默认商户 id=1 平台自营）、`users`（V1，V3 加 `role`，
  V12 加 `avatar`，**V16 加 `merchant_id`**）、`addresses`（V2）、`categories`（V4）、
  `products`（V5，V9 加 `has_specs`，**V17 加 `merchant_id`**）、
  `cart_items`（V6，V10 改成按 SKU 唯一）、`orders` / `order_items`（V7，V8 加
  `status_before_refund`，V11 加 `sku_id` / `spec_text`，V15 加优惠快照字段，
  **V17 给 orders 加 `merchant_id`**）、`payments`（V8）、
  `product_specs` / `product_spec_values` / `product_skus`（V9）、
  `user_favorites` / `user_footprints`（V13）、`reviews`（V14，**V17 加 `merchant_id`**）、
  `coupons` / `user_coupons`（V15，**V18 加可空 `merchant_id`**）、
  `conversations` / `messages`（**V19，在线聊天**）
  （另有 Flyway 自建的 `flyway_schema_history`，是迁移记录表，**不要动**）
- **V9 给存量商品回填了默认 SKU**（无规格的那条），老数据不用手工处理；
  但**新直插 `products` 的地方（手工 SQL、导入脚本）必须自己补 SKU** —— 见 §5.21
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
│   ├── api/{product,cart,auth,address,order,user}.ts
│   ├── api/admin.ts        # 平台管理端：分类 / 商户 / 商品 / SKU / 上传 / 订单 / 券 / 评价
│   ├── api/merchant.ts     # 商家后台：资料 / 商品 / 订单 / 店铺券 / 评价
│   ├── api/shop.ts         # 公开店铺信息
│   ├── api/{favorite,footprint,review,coupon}.ts
│   ├── stores/user.ts      # 登录态
│   ├── utils/orderStatus.ts # 订单状态中文名与标签色（管理端列表只有原始 status）
│   ├── components/ProductImage.vue   # 商品图 + 无图占位
│   ├── styles/theme.css    # 色板 + Element Plus 主题覆盖（必须在 element-plus 之后引入）
│   ├── router/ · App.vue · main.ts
│   ├── views/              # 顾客端页面 + user/ 用户中心 + Shop.vue / CheckoutResult.vue
│   ├── views/admin/        # 平台管理端：Merchants + 分类 / 商品 / 订单 / 券 / 评价 + components/
│   └── views/merchant/     # 商家后台：Apply / Products / Orders / Coupons / Reviews / Profile
```

**启动**（后端要同时跑着）：

```bash
npm run dev          # → http://localhost:5173
```

**演示账号**：`testuser` / `123456`（顾客）
**管理员**：先注册，再提权成 ADMIN（提权后要重新登录）：
```bash
docker exec -i mysql sh -c 'mysql --default-character-set=utf8mb4 -utsuk1 -p"$MYSQL_PASSWORD" its_mygo' -e "UPDATE users SET role='ADMIN' WHERE username='testuser';"
```
管理端入口：登录后顶栏用户面板里的「管理后台」，或直接访问 `/admin`。
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

- **规格选择栏**：把 `product.specs` 渲染成按钮组，选中的组合按**规范化后的 `specValueIds`**
  匹配到对应 SKU（规则必须和后端生成这个键时一致，都是按数值升序拼）——
  匹配上就把价格、库存、图片换成那一款的。无规格商品只有一条默认 SKU，直接用它
- 库存已经在详情的 `skus[]` 里（后端读时换成了实时值），不用另外拉；
  `GET /api/products/{id}/stock` 留给「加购失败后刷新」用，它同时返回每个 SKU 的余量
- 同类商品：后端只有分页接口，所以取同分类 8 条后**在内存里**排掉自己、留前 4 条

**登录页**（`views/Login.vue`）：登录 / 注册同页签切换，共用一套校验规则
（约束照抄后端 `RegisterRequest`）。登录成功按 `?redirect=` 回跳，
从「加购」跳过来的能直接回到那件商品。页脚写着演示账号，**真上线要拿掉**。

**购物车页**（`views/Cart.vue`）：勾选 / 改数量 / 移除 / 去结算。
失效行（下架、库存不够、规格被删）照常显示但选不了，并写明原因。
后端每个写操作都返回整份购物车，前端直接换状态。

**结算页**（`views/Checkout.vue`）：选收货地址（页内就能新增）+ 商品清单 + 提交订单。
**两种来源共用这一个页面**：带 `?productId=X&skuId=Y&quantity=N` 就是「立即购买」，
不带就是从购物车结算（提交时不传 `items`，后端按勾选行下单）。
**提交令牌在进页面时就领好**，提交时复用同一个 —— 提交时才领的话连点两次会各领一个，
防重复提交就形同虚设。

**订单详情页**（`views/OrderDetail.vue`）：下单后的落点，待支付时能直接付款。

**用户中心**（`views/user/`）：`/user` 是嵌套路由，外壳 `UserCenter.vue` 是「左侧资料卡 + 菜单 +
`<router-view>`」，子页有 订单 / 地址 / 资料 / 改密。路由守卫在 `router/index.ts` 里按
`meta.requiresAuth` 拦，未登录跳登录并带 `redirect`。
订单页六个 Tab 其实是一个列表按状态筛（状态映射见 implementation §4.10）；
地址表单已抽成 `components/AddressFormDialog.vue`，结算页共用同一份。

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

### 5.21 ★ SKU 化时最容易漏的两处（2026-10-16 踩到）

把价格和库存下沉到 SKU 这一轮，实际绊到的就是这两处 —— 和动手前的预判完全一致。

**① 直插库的商品没有 SKU**

「每个商品都必须有 SKU」这条规矩，走管理端 `create` 接口会自动建，
但**测试里直插 `products`、以及 `seed-data.sql` 这类直接 SQL** 都会绕过它。
症状是详情页总库存显示 0、下单永远报「库存不足」—— 看着像库存 bug，其实是数据没铺开。

这一轮里 `StockConcurrencyTest` / `PaymentConcurrencyTest` / `StockCacheBreakdownTest` /
`CacheProtectionTest` / `CacheAsideTest` **五个测试类全栽在这上面**，各补了一条默认 SKU。
`ApiTestBase.defaultSkuId(productId)` 就是为这个加的。

**② `reload` 传了商品 id**

`ProductServiceImpl.update` 里原本是 `stockService.reload(id)` —— 那时计数器按商品建，
没问题。改成按 SKU 之后，`id` 是**商品 id 却被当成 SKU id 去查**，刷新的键根本不是对的那个。

**是测试抓出来的**：`OrderControllerTest.adminStockChangeIsVisibleImmediately`
期望改完库存后详情显示 50，实际还是 5。

**教训**：这类「同一个参数名换了含义」的改造，**编译器帮不上忙**（`Long` 还是 `Long`），
只能靠「改完之后读回来对不对」的断言兜住。所以改粒度时，
优先补端到端读回来的用例，而不只是改签名。

### 5.22 ★ 管理端接 SKU / 订单时最容易漏的三点（2026-10-18 踩到 / 补齐）

**① 多规格商品的已有 SKU 库存根本没有接口能改。** 整组保存（`PUT .../skus`）
**故意不碰已有行的 stock**（否则商户改个价格就把库存冲掉），而商品更新里的
`syncDefaultSku` 对多规格是 no-op。结果是管理端能改价、能加规格，**唯独不能补货**。
本轮补了 `PUT /api/admin/products/{productId}/skus/{skuId}/stock`：
请求带 `expectedStock`，服务端用 `UPDATE ... WHERE stock = ?` 做条件更新，
被并发改过就拒绝（「库存已变化，请刷新后重试」），成功后刷 SKU 行 / 汇总 / Redis 三处。

**② 回显的 `specValueIds` 是升序串，不是规格顺序。**
后端存的是数值升序的 `"11,12"`，而展示要按「颜色、版本」的规格顺序排。
不回排就会列错位（颜色列显示出版本的值）。做法：先建 `valueId → specIndex` 映射重排，
匹配已有 SKU 时再按数值升序拼 key —— 两处都不能用字符串 `sort()`。

**③ 管理端订单列表和顾客端不是同一套返回。**
`/api/admin/orders` 返回原始 `Order`，**没有 `statusLabel`**（前端要自己映射），
而且 `status` 只支持**单个枚举精确匹配，不支持逗号分隔**（顾客端支持）。
详情要单独请求 `/api/admin/orders/{id}` 才有明细。

### 5.23 ★ 用户中心后续功能的五个坑（2026-10-18 踩到 / 补齐）

**① 重复领券默认会变成 500。** `user_coupons` 的唯一键会抛 `DuplicateKeyException`，
而 `GlobalExceptionHandler` 只认 `BizException`。必须在 `CouponServiceImpl.claim` 里
catch 并转 400「该优惠券已领取」；否则用户看到的是服务器错误。

**② 退券必须覆盖 `cancelExpired`。** 超时取消不走 `cancel()`，而是 `cancelExpired` 自己的循环。
退券逻辑抽成 `restoreCoupon(order)`，在 `cancel`、`approveRefund`、`cancelExpired`
**三处**都调；`WHERE used_order_id=? AND status='USED'` 保证幂等。
方法内部 catch + 日志，退券失败不阻断订单状态回滚。

**③ 退券后「已过期」筛选会漏。** `restoreByOrderId` 把过期券置成 `status='EXPIRED'`，
而最初「已过期」查询只认 `status='UNUSED' AND valid_to < NOW()`，两者对不上，
退回过期券在「已过期」Tab 里查不到。改成
`status='EXPIRED' OR (status='UNUSED' AND valid_to < NOW())`。
展示状态统一在 SQL 里按数据库 `NOW()` 算，不用 JVM 时间（§5.14）。

**④ 足迹清理 SQL 有两个 MySQL 8 坑。** `IN (SELECT ... LIMIT)` 报 1235，
同表 DELETE 的直接子查询报 1093 —— 必须用**双层派生表**
（见 `UserFootprintMapper.trimToLatest`）。另外只在 `count > 110` 时才清理，
否则每次浏览都要扫一遍该用户的所有足迹。

**⑤ 订单列表回填「已评价」不能 N+1，也不能循环依赖。**
`OrderServiceImpl` 只注入 `ReviewMapper`（不要再注入 `ReviewService`，会形成 Service 环）；
`pageForCustomer` 先把本页所有已完成订单的 itemIds 收集起来、一次 `IN` 查询，
再回填 `reviewed`。`previewLimit` 截断必须在回填之后。

### 5.24 ★ 多商户改造的五个坑（2026-10-19 踩到 / 补齐）

**① JWT 里的 role 不能当权限。** 商家审核通过/禁用后，旧 token 里的 claim 不会变。
`JwtAuthenticationFilter` 只取 userId，角色与店铺状态交给 `AuthIdentityService`
按数据库查（60 秒 Redis 缓存 + 变更时 evict）；店铺非 ACTIVE 时有效角色降为 CUSTOMER。
`merchantId` 也永远从身份推导，不能信任请求体。

**② 拆单必须一个事务 + 事务回调回补 Redis。** 一次结算生成 N 个订单；业务 catch
只能覆盖方法内异常，**提交阶段失败不会进 catch**。Redis 预扣和提交令牌归还都要挂
`TransactionSynchronization.afterCompletion(ROLLED_BACK)`，并用 `AtomicBoolean`
防止业务 catch 与事务回调重复回补（`release` 是 INCRBY，重复会多补）。

**③ 平台券重复选择要在扣库存前拦住。** 同一张平台券被分配给多个子订单时，
靠第二次 `markUsed` 返回 0 虽然能回滚，但报错含混、依赖执行顺序。正确做法是
**先校验所有券再预扣库存**：userCouponId 全局去重、每组最多一张、店铺券匹配本店、
门槛按该组商品总额算。

**④ 商品详情缓存不含店铺状态。** 店铺被禁用后，`its-my-go:product:{id}` 可能还在；
`detailForCustomer` / `stockForCustomer` 命中缓存后必须**再查一次店铺 ACTIVE**，
否则禁用店铺的商品还能继续卖。

**⑤ 下单响应改了结构但测试大量依赖旧路径。** `CheckoutView` 顶层保留
`order/statusLabel/items/itemCount` 镜像第一个子订单，同时新增 `orders` 数组 ——
旧测试的 `$.data.order` / `$.data.items` 不用改，新前端用 `$.data.orders`。
另外测试方法本身在 `@Transactional` 里时，服务层扣减会加入测试事务，
**看不到生产环境的整单回滚**；这类断言只能测 Redis 回补。

### 5.25 ★ 在线聊天的五个坑（2026-10-19 踩到 / 补齐）

**① EventSource 带不了 Authorization。** 不能把 JWT 塞进 URL（会进访问日志）。
做法：JWT 换 **60 秒一次性票据**（Redis，带 TTL），`GET /api/chat/stream?ticket=`
用 `getAndDelete` 消费；SecurityConfig 只精确放行这一条 GET。票据被消费后
EventSource 自动重连会 401，前端必须 `close()` 后重新取票（指数退避）。

**② SseEmitter 不是线程安全的，且断线不会立刻被发现。** 每条连接单独加锁发送；
发送失败即摘除。客户端 kill 后服务端往往要等下一次心跳（25 秒）才能发现，
所以**在线状态最多滞后 25 秒**，验证时别在 kill 后立刻断言离线。

**③ 未读不能用「直接置 0」。** 已读和并发新消息竞争时会丢未读。正确顺序：
指针 `GREATEST` 只前进 → 用 `COUNT(messages.id > 指针 AND sender_type <> 我)` 重算。
发消息时未读用原子 `+1`；`last_message_*` 只允许更大消息 id 覆盖。
SSE 事件与已读接口返回的是**全局未读总数**，不是单会话未读数 —— 否则顶栏角标会算错。

**④ 推送只能挂在事务提交后。** 用 `TransactionSynchronization.afterCommit`，
回滚不推送；推送失败只记日志、不回滚消息。客户端重连后用 `afterId` 补齐，
不能假设 SSE 一定送达。

**⑤ 注册表是单实例内存态。** 在线状态和连接表都在本进程；多实例部署需要
Redis Pub/Sub 或粘性会话。本期明确单机，不要顺手加 MQ。
另外 Windows Git Bash 下 curl 直接传中文会变 GBK（§5.17），
接口联调优先用 ASCII 或由前端发送。

---

## 6. 历史记录

### 2026-10-20 · 客服智能体更名为「买小购」

**范围**：客服智能体的对外名称由「掌柜智库」改为「买小购」，仓库内所有文案、
占位卡片、文档与 CLAUDE.md 已同步。外部 `rag_helper` 仓库尚未改动（需要时再同步）。

### 2026-10-20 · 后端 API 接口文档

**范围**：新增 `docs/api.md`，覆盖 `controller/` 下全部接口（119 个），
包含统一响应、认证与角色、分页、枚举、公共 DTO、各域接口清单、聊天 SSE 事件、
限流汇总与上传约束。

**维护约定**：新增/修改接口时同步更新 `docs/api.md`；README 的文档表已加入该文件。

### 2026-10-20 · 客服智能体前端占位卡片

**范围**：仅前端。顾客、商家、管理后台三个消息中心的会话列表顶部固定「买小购」卡片，
所有登录用户可见；点击进入 `.../messages/agent` 占位面板。

**实现**：`ConversationList` 增加置顶 agent 卡片；`AgentChatPanel.vue` 提供按角色的
欢迎语、建议问题和禁用输入框；`ChatCenter` 识别 `agent` 路由参数并切换面板。
**不调用后端、不改数据库**，M6 接入时替换为真实会话与 SSE。

**验证**：三个角色分别确认卡片存在、可选中、路由为对应 `.../agent`，输入框禁用。

### 2026-10-20 · 顶栏动态角标

**范围**：消息、领券中心、购物车都显示数量角标，数量为 0 时隐藏。

**实现**：消息继续用 SSE 的 `chatStore.unreadTotal`；新增 `stores/badge.ts`
维护「可领券数（`!claimed`） + 购物车数量之和」，30 秒轮询（后台标签页跳过），
并在加购、购物车增删改、领券后立即同步。匿名用户也能看到可领券数量。

**验证**：浏览器实测领券后角标 2→1、加购后购物车角标出现、移除后隐藏、
管理员发消息后消息角标实时 +1。

### 2026-10-20 · 平台管理员接管平台自营店消息

**范围**：平台自营商品恢复「联系卖家」入口；管理员在管理后台
`/admin/messages` 以商家身份处理 merchant_id=1 的会话。

**后端**：`startConversation` 允许平台自营店（owner 为 NULL）；新增
`/api/admin/chat/**`（列表/详情/消息/发送/已读/未读）只暴露 merchant_id=1 的会话；
平台自营店的消息推送给全部正常状态管理员，顾客已读回执也发给管理员；
管理员 SSE 连接计入平台自营店在线状态；管理员仍不能使用顾客侧聊天接口。

**前端**：`ChatCenter` 增加 `admin` 模式；`/admin/messages` 页面与菜单未读角标；
管理员顶栏「消息」指向 `/admin/messages`；商品/店铺/订单页对平台自营店恢复联系入口
（管理员自己浏览时不显示）。

**测试 178 → 180**：新增平台自营店管理员收/发/已读、管理员不能访问普通商家会话。
全量 180 条全绿。

**浏览器实测**：平台自营商品「联系卖家」→ 会话自动带商品卡 → 管理员后台看到未读并回复 →
顾客端显示回复；管理员 SSE 连接后顾客端显示「在线」。

### 2026-10-19 · 在线聊天与私信留言

**范围**：顾客 ↔ 商家按店会话；商家只能回复、不能主动私信；管理员不参与。
文本 + 图片 + 商品卡 + 订单卡；SSE 实时推送；未读角标、已读回执、在线状态、离线留言。

**数据库（V19）**：`conversations`（唯一键 customer+merchant、双方未读/已读指针、
最后消息预览）+ `messages`（sender_type 预留 AGENT、content_type、卡片 JSON 快照）。

**后端**：`ChatService` / `ChatServiceImpl`、`ChatController`（`/api/chat`）+
`MerchantChatController`（`/api/merchant/chat`，无创建会话接口）；
`ChatTicketService`（Redis 票据）、`ChatStreamRegistry`（SSE 连接 + 在线状态）、
`ChatHeartbeatJob`（25 秒心跳）；发消息/已读原子 SQL，推送挂 afterCommit；
卡片由服务端按 ID 生成快照，图片只收 `chat/` 前缀。

**前端**：`stores/chat.ts`（EventSource 生命周期 + 角标 + 事件订阅）、
`components/chat/*`（ChatCenter / 会话列表 / 聊天窗口 / 气泡 / 输入框）、
`/user/messages` 与 `/merchant/messages`；商品详情 / 店铺页 / 订单详情加「联系卖家」，
顶栏与商家菜单加未读角标，移动端单栏切换。

**测试 167 → 178**：新增 `ChatControllerTest` 11 条（会话幂等、权限、消息校验、
卡片归属与快照、未读/已读、分页补齐、票据一次性）。全量 178 条全绿。

**浏览器实测**：商品页「联系卖家」自动发商品卡 → 顾客发文本 → 商家 curl 回复
在顾客页通过 SSE 实时出现 → 商家未读角标与已读回执 → 订单卡片点击跳订单详情 →
聊天图片上传与渲染 → 商家 SSE 在线/离线状态 → 移动端单栏切换。

### 2026-10-19 · 领券中心移到顶栏主导航

**范围**：领券中心是公共功能，不属于用户个人范畴。入口从登录后的用户抽屉面板
移到顾客顶栏主导航（「领券中心 / 购物车」），匿名用户也能直接进入；用户面板只保留
「我的优惠券」。

### 2026-10-19 · 商家 Logo 改为上传

**范围**：入驻申请页与商家店铺资料页的 Logo 从手填 URL 改为图片上传。

**后端**：`StorageService.uploadMerchantLogo` 存 `merchants/` 前缀、限 2MB；
新增 `POST /api/merchant-apply/logo`（任意登录用户可用，按用户限流 20/分钟），
解决申请人尚无 `MERCHANT` 角色、不能走 `/api/merchant/files/upload` 的问题。

**前端**：`ImageUpload` 从 `views/admin/components` 移到共享 `components/`，
新增 `apply` 模式；`Apply.vue` 用 `apply`、`Profile.vue` 用 `merchant`。
提交入驻申请成功后 `router.replace('/')` 回主页，刷新不会停在申请页。

**验证**：后端 167 条测试全绿，前端 `vue-tsc` 通过。

### 2026-10-19 · 多商户平台改造

**范围**：单商户自营 → 多商户平台。商家自助申请 + 平台审核；商品归属店铺；
跨店购物车按店拆单、分别支付；商家后台（商品 / 订单 / 店铺券 / 评价 / 资料）；
平台监管（商户审核、分类、全平台商品 / 订单 / 券 / 评价）。
**不做**佣金、分账、商家结算/提现、商家子账号、关注店铺。

**数据库（V16–V18）**：`merchants` + 默认商户 id=1 + `users.merchant_id`；
`products` / `orders` / `reviews` 加 `merchant_id`（存量回填 1）；
`coupons` / `user_coupons` 加可空 `merchant_id`（NULL=平台券）。

**后端**：角色加 `MERCHANT`；`AuthIdentityService` 按数据库实时算权限（60 秒缓存，
审核/禁用时 evict），旧 token 不再保留越权；商家商品 / 订单 / 券 / 评价接口全部按
token merchantId 过滤；购物车视图带店铺信息；`OrderServiceImpl` 按店分组拆单，
券在扣库存前全局预校验，Redis 预扣与令牌归还挂事务回滚回调；
`CheckoutView` 保留旧字段兼容测试；公开店铺、平台商户审核、平台/店铺券、商家评价接口。

**前端**：`/shop/:id` 店铺主页与商品详情店铺卡片；购物车 / 结算按店分组、每店选券；
`CheckoutResult` 多订单分别支付；订单与券展示店铺；`/merchant` 商家后台
（商品 / 订单 / 店铺券 / 评价 / 资料）复用 admin 组件（`merchant` prop 切 API）；
`/admin/merchants` 商户审核 + 各页店铺列/筛选。

**测试 148 → 167**：新增 `MerchantApplyControllerTest` 10、`MerchantProductTest` 4、
`OrderSplitTest` 4、`MerchantReviewTest` 1。全量 167 条全绿。

**浏览器实测**：顾客申请入驻 → 平台通过 → 商家后台建商品/SKU → 店铺页与详情店铺卡片；
跨店购物车两组 → 每店选券 → 提交生成 2 个子订单 → 分别支付 →
商家只看/只发本店订单，平台看全量；商家禁用后旧 token 被拦、店铺页 404。

**决策变更**：`decisions-ecommerce.md` §1.1/§1.2/§1.7 已同步为多商户定位，
并记录「佣金/分账/结算仍不做」。

### 2026-10-19 · 顾客端订单操作入口：取消 / 确认收货 / 申请退款

**范围**：订单详情页按状态显示操作按钮，复用后端已有接口，前端只加 API 封装与交互：

- 待支付：立即支付 / 取消订单
- 已支付：取消订单 / 申请退款
- 已发货：确认收货 / 申请退款
- 已完成：申请退款
- 退款中 / 已退款 / 已取消：无操作

按钮显隐与后端 `OrderStatus.cancellable()` / `refundable()` 口径一致；
取消、退款、超时取消都会回滚库存并退回未过期优惠券。所有操作带二次确认，
成功后直接用返回的 `OrderView` 更新页面，失败则重新拉取真实状态。

**验证**：浏览器完整走了两条链路 ——
① 新建待支付订单 → 取消 → 状态「已取消」；
② 新建订单 → 支付（已支付，出现取消/退款）→ 管理端发货（已发货，出现确认收货/退款）
→ 确认收货（已完成，出现退款与评价入口）→ 申请退款（退款中，无操作）
→ 管理端同意退款（已退款）。前端 `vue-tsc` 与 `vite build` 通过。

**顺带**：新增项目根目录 `start.txt`，把中间件/后端/前端的启动命令与常见问题整理成清单。

### 2026-10-18 · 用户中心后续：收藏 / 足迹 / 评价 / 优惠券

**范围**：收藏、浏览足迹、商品评价（含管理端隐藏）、满减优惠券
（领券中心 / 下单抵扣 / 取消退款退券），以及用户中心四个新子页。

**数据库（V13–V15）**：

- V13 `user_favorites`、`user_footprints`（按商品去重，只保留最近 100 条，`viewed_at` 用 `DATETIME(3)`）
- V14 `reviews`（`uk_order_item_id`，一个订单项只能评一次）
- V15 `coupons` / `user_coupons`（领取时快照条款；`uk_user_coupon` 每人一张；
  `uk_used_order` 一单一张），`orders` 加 `original_amount` / `discount_amount` /
  `user_coupon_id` / `coupon_name`，存量订单回填 `original_amount = total_amount`

**后端新增**：`FavoriteService` / `FootprintService` / `ReviewService` / `CouponService`
及对应 Controller；商品公开评价 `GET /api/products/{id}/reviews` + `/summary`；
管理端 `AdminCouponController` / `AdminReviewController`。
`OrderServiceImpl` 接入优惠券：`CreateOrderRequest.couponId`、下单后条件更新券为 USED、
取消/退款/超时取消统一 `restoreCoupon`；`OrderItem` 加 `reviewed` 并由 `ReviewMapper`
批量回填。`SecurityConfig` 只精确放行 `GET /api/coupons`。

**前端新增**：用户中心 `Favorites` / `Footprints` / `Coupons` / `Reviews`、
`CouponCenter`（`/coupons`）、`ReviewDialog`；商品详情加收藏按钮与评价区块、
加载成功上报足迹；订单页与订单详情加「评价」入口、显示优惠金额；
结算页加优惠券选择与抵扣；管理端加「优惠券管理」「评价管理」两页。
顶栏用户面板加收藏 / 我的优惠券 / 领券中心入口。

**决策变更**：`decisions-ecommerce.md` §1.7 原把「优惠券」列为明确不做，
本轮按用户要求解禁，已同步修改并注明变更记录；秒杀/拼团仍不做。

**测试 114 → 148**：新增 `FavoriteControllerTest` 4、`FootprintControllerTest` 6、
`ReviewControllerTest` 8、`CouponControllerTest` 9、`OrderCouponTest` 7。全量 148 条全绿。

**浏览器实测**：详情页收藏 → 用户中心收藏列表出现；浏览商品 → 足迹页按时间倒序且去重；
管理端建「满100减20」→ 领券中心领取 → 结算页选中后应付 329→309 →
下单后券变已使用 → 管理端取消超时订单 → 券恢复未使用；
完成订单后提交 5 星评价 → 商品详情显示 5.0 分/1 条 → 管理端隐藏后商品详情消失、
我的评价显示「已隐藏」。

**已知缺口**：顾客端仍没有取消订单 / 确认收货 / 申请退款的按钮（后端接口都有），
本轮验证评价链路时是用管理端 + SQL 把测试订单推到 `COMPLETED` 的；下一轮补。

**验证留下的演示数据**：开发库里有一条启用中的「满100减20」券（已领 1/5）、
一笔已完成订单（760）及一条可见评价；不需要可在管理端停用券/隐藏评价。

### 2026-10-18 · 管理端前端完成（分类 / 商品 / SKU / 订单）

**范围**：`/admin` 独立外壳 + 分类树 CRUD、商品列表/新增/编辑/传图/上下架、
规格与 SKU 编辑器、订单列表/详情/发货/退款审核/取消超时订单。
管理端**直接套 Element Plus 默认样式**；**不做商品硬删除**，只做上下架。

**访问控制**：`meta.requiresAdmin` + 守卫读 localStorage 的 role；
未登录跳登录并回跳，非管理员提示后回首页。顾客用户面板里仅在 ADMIN 时显示「管理后台」。
管理员靠 SQL 提权，**提权后必须重新登录**（JWT 里的 role 不会变）。

**后端新增**：`PUT /api/admin/products/{productId}/skus/{skuId}/stock`
（`AdjustSkuStockRequest` + `ProductSkuMapper.compareAndSetStock` + `ProductSkuService.adjustStock`）。
原因见 §5.22：多规格商品的已有 SKU 库存原本没有任何接口能改。
条件更新防丢更新，成功后刷 SKU 行 / 商品汇总 / Redis 三处。

**前端新增**：`api/admin.ts`、`utils/orderStatus.ts`、`views/admin/`（3 个页面 +
商品表单、SKU 编辑器、库存调整、订单抽屉、图片上传 5 个组件）；`App.vue` 在
`/admin` 下隐藏顾客顶栏。`vite.config.ts` 的代理目标支持 `API_TARGET` 环境变量
（验证时 8080 被别的会话占着，用 8081 跑了一遍）。

**测试 109 → 114**：`ProductSkuControllerTest` 新增 5 条（库存三处一致、CAS 过期被拒、
跨商品 SKU 404、负库存 400、顾客 403）。全量 114 条全绿。

**浏览器实测**：未登录 `/admin` 跳登录并回跳；分类新增/子分类/删除约束/父分类排除自身后代；
商品新增（自动默认 SKU）、编辑、多规格时价格库存禁用、上下架后顾客端不可见；
SKU 编辑器加规格生成组合、改价/改值后库存保住（25/30）、库存调整 CAS、删 SKU 二次确认；
订单筛选、详情抽屉、发货填单号、退款驳回/同意、取消超时未支付订单。

**清理**：验证用的测试商品已从库和 Redis 键里删掉；测试订单走完了
支付 → 发货 → 退款/取消的完整链路（属测试数据）。

### 2026-10-18 · 手机号校验按主流口径补齐

收货地址和用户资料里的手机号改成按大陆口径校验：`^1[3-9]\d{9}$`（1 开头、第二位 3-9、共 11 位），
和淘宝/京东一致，**只收手机号、不接座机**（地址表单里的「手机号或座机」提示一并改掉）。

前端 `el-form` 同一条正则即时提示，后端 `@Pattern` 兜底；个人资料的手机号是选填，空串放行。
地址与资料各补了一条测试（非法号码 400、空号码允许），共 **109 条**。

### 2026-10-18 · 地址表单改成省市区级联 + 可输入筛选

省 / 市 / 区从自由输入改成级联的 `el-select filterable`：选省后市跟着换、选市后区县跟着换，
也可以直接输入过滤（输「广东」只剩「广东省」）。

**数据**：没引 npm 包，从 `modood/Administrative-divisions-of-China` 取了民政部行政区划
（31 个省级），压成 `{省:{市:[区县]}}` 放在 `frontend/src/data/china-area.json`（约 47KB，去掉了编码）。
编辑老地址时，城市/区县若不在数据集里（直辖市的「北京市」在数据里叫「市辖区」），
会保留原值成一个选项，不会被清空 —— 这条有浏览器实测。

### 2026-10-18 · 用户中心：订单 / 地址 / 资料 / 改密 / 头像

**范围**：用户要「完整的用户中心」，经两轮确认，本轮先做骨架 + 订单 + 地址 + 资料（含头像），
收藏 / 足迹 / 优惠券 / 评价体系留到后续（其中「待评价」在没有评价表之前只是已完成订单的入口）。

**数据库**：`V12__user_avatar.sql` 给 `users` 加 `avatar`，schema 到 **v12**。

**后端新增**（`UserController` `/api/user/*`）：

- `GET/PUT /profile` —— 昵称、手机号。只更新传进来的字段，不把整条 User 写回去
- `PUT /password` —— 校验旧密码，**失败返回 400 而不是 401**（401 会让前端自动登出）
- `POST /avatar` —— `StorageService.uploadAvatar`，对象进 MinIO 的 `avatars/`，
  按用户限流 10/分钟、单独限 2MB；`LoginResponse` 顺带带上 avatar
- `GET /api/orders` 加 **status 参数**（逗号分隔），一个接口撑起订单页的六个 Tab
- `PUT /api/addresses/{id}/default` —— 独立的设默认接口，幂等

**前端**：`/user` 嵌套路由 + 守卫；四个子页；地址表单抽成公共组件
（结算页和地址管理页共用）；头像用 `el-upload` 的 `:http-request` 自定义上传
（默认 action 绕过 axios 会丢 JWT）。

**测试 95 → 107 条**：新增 `UserProfileControllerTest`（8 条，头像用 `@MockitoBean` 替掉
StorageService，不依赖 MinIO）、`AddressControllerTest` +3、`OrderControllerTest` +1。

**浏览器验证**：未登录访问 `/user/orders` → 跳登录并回跳；订单 Tab 筛选（待付款 5 单、
待评价带提示）；地址新增/编辑/设默认/删除；改昵称后顶栏与 localStorage 同步；
头像上传后 MinIO、库、页面三处一致；改密后强制跳登录，旧密码登录失败、新密码成功。

**顶栏用户区**（同日后补）：登录后是「圆形头像 + 昵称」，**鼠标移上去向下滑出抽屉面板**
（订单 / 地址 / 资料 / 改密 / 退出），移开收起；窄屏收起昵称。

### 2026-10-18 · 购物车页，结算接上购物车来源

**购物车页**（`views/Cart.vue` + `/cart` 路由）：勾选 / 改数量 / 移除 / 去结算。
失效行（下架、库存不够、规格被删）照常显示但选不了、并写明原因；
金额只算「勾选且可买」的行。后端每个写操作都返回整份 `CartView`，前端直接换状态。

**结算页支持两种来源**：带 `?productId=...` 是立即购买，不带就是从购物车结算 ——
后者提交时**不传 `items`**，后端按购物车勾选行下单并清空它们（这套后端本来就有，
这次只是把前端入口补上）。

**验证**（浏览器真走了一遍）：未登录进购物车 → 跳登录并带 `redirect=/cart`；
勾选联动金额（478 → 149）；改数量（1 → 2，小计和合计同步变）；
移除有二次确认、删完显示空状态；结算页只带勾选行（×2、298）；
提交后订单页显示规格快照，购物车只剩**没勾选**的那件。

### 2026-10-16 · 首页几处打磨（品牌专区 / 搜索 / 滚动加载）

- **品牌专区**：首屏改三栏（190px 品类栏 | 轮播 | 240px 品牌区），
  7 个品牌是**硬编码**的、只挑了商品名里真实出现过的词，点进去走关键词搜索
- **顶栏搜索框接上了**（之前一直是摆设），和 URL 的 `keyword` 双向同步；
  筛选条件（分类 + 关键词）统一下沉到 URL
- **商品列表改滚动加载**：去掉分页器，`IntersectionObserver` + 底部哨兵，
  `rootMargin: 200px` 提前触发；用「已拿到数 vs 总数」判断还有没有下一页
- **内容区 1200 → 1440**：1680 宽的屏上两侧空白从各 240px 降到 120px，商品网格 5 列变 6 列
- **卡片**：缩略图改 `aspect-ratio: 1/1`（原来写死 160px，卡片宽度是弹性的，所以不是正方形）；
  去掉卡片上的加购按钮，点进详情再买

### 2026-10-16 · 商品 SKU：多规格 + 每规格独立价格库存

**起因**：用户要给商品加「可供商户编辑的商品属性选择栏」（详情页那种
「款式：云宝 / 珍奇」）。确认按**完整 SKU** 做 —— 每个规格组合独立价格、独立库存；
管理端前端不做，顾客端详情页要做。

**动手前先探了三条链路**（库存与缓存、购物车与订单、商品 CRUD 与测试），
摸清「按 productId 建立的四处」：Redis 库存计数器、详情缓存、布隆过滤器、条件更新 SQL。
方案经 Plan agent 设计与复核后才开工。

**数据库（V9 / V10 / V11）**：

- V9 建 `product_specs` / `product_spec_values` / `product_skus`，并**给存量商品回填默认 SKU**
- V10 `cart_items` 加 `sku_id`，唯一键 `uk_user_product` → `uk_user_sku`
- V11 `order_items` 加 `sku_id` + **`spec_text` 快照**

**关键设计**：`spec_value_ids`（升序逗号分隔）作规格组合的规范键、落成唯一索引 ——
关联表表达不了「值集合唯一」。`products` 的 price/stock/sales 退化成汇总值，
列表页分页因此**零改动**。

**后端**：`ProductSkuService`（整组覆盖保存，**按组合键匹配以保住库存**）、
`ProductSkuMapper`（SKU 级条件更新）、`StockService` 改成 SKU 粒度并新增批量
`currentSkuStocks`（一次 multiGet）、详情与库存接口带上 SKU 列表。

**前端**：详情页规格选择栏（选中联动价格/库存/图片）、加购与立即购买带 `skuId`、
列表页多规格显示「¥19.8 起」且按钮变「选规格」、订单页显示规格快照。

**新增测试 19 条**（共 95）：`ProductSkuControllerTest` 7、`SkuCartTest` 5、
`SkuOrderTest` 5、`SkuStockConcurrencyTest` 2 —— 最后那条专门盯
「同商品两款互不串扰」，这是 SKU 化新引入的风险。

**踩的坑见 §5.21**（直插库的商品没 SKU、`reload` 传错 id）。
**种子数据也更新了**：HDMI 线配了长度三选一、键鼠套装配了颜色 × 版本四组合 ——
前端才有东西可看。

**验证**：浏览器里真走了一遍 —— 选规格后价格 129→149、库存 88→4 件跟着变，
加购、立即购买、下单、订单里显示「颜色:白色;版本:静音版」；
回查数据库确认**只扣了那一款**（4→3），另外三款纹丝不动。

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
