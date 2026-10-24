# 实现说明 · 一次买够（ItsMyGo）

> **读者**：想知道「这些代码具体是怎么跑的」的人（包括下一个接手项目的会话）
> **性质**：**实现层**文档 —— 一条请求怎么流转、每个机制怎么落地、为什么这么写
>
> 三者分工：**README** 讲「是什么」（技术栈 / 架构 / 部署）、
> **HANDOFF** 讲「到哪了」（进度 / 下一步 / 踩过的坑）、
> **本文**讲「怎么做到的」（实现细节）。
> 需求与选型理由见 [decisions-ecommerce.md](decisions-ecommerce.md)。
>
> ⚠️ **本文是代码的镜像：只要实现变了（新增机制、换了做法、改了关键取舍），就要同步更新本文。**
> 过期的实现文档比没有更糟 —— 它会让人按旧做法去改新代码。

---

## 1. 一条请求的完整旅程

以「顾客带 token 查询商品列表」为例：

```
GET /api/products?keyword=HAK          Authorization: Bearer <JWT>
   │
   ├─ JwtAuthenticationFilter          ① 取 token → 验签 → 解析出 CurrentUser
   │                                      → 构造 Authentication 放进 SecurityContext
   │
   ├─ Spring Security 授权              ② 查 SecurityConfig 里的路径规则
   │                                      （GET /api/products/** 是 permitAll，放行）
   │
   ├─ DispatcherServlet → ProductController
   │                                   ③ 接参数（@RequestParam），调 Service，包 Result
   │
   ├─ ProductServiceImpl               ④ 业务规则：拼查询条件、分页归一化
   │
   ├─ ProductMapper (BaseMapper)       ⑤ MyBatis-Plus 由实体 + Wrapper 生成 SQL
   │
   └─ MySQL                            ⑥ 返回；往回逐层包装成 Result<T> → JSON
```

出错时（业务异常 / 参数校验失败 / 未捕获异常）在 ③④ 之间抛出，
由 `GlobalExceptionHandler` 统一转成 `Result`，**Controller 里不用写 try-catch**。

---

## 2. 横切机制

### 2.1 统一响应体 `Result<T>`

`common/Result.java` —— 所有接口返回同一个形状：

```json
{ "code": 200, "message": "成功", "data": { ... } }
```

四个静态工厂：`ok()` / `ok(data)` / `fail(ResultCode)` / `fail(int, String)`。
`ResultCode` 是枚举，**code 与 HTTP 语义对齐**（200 / 400 / 401 / 403 / 404 / 500），
前端拿到 code 就能判断，不用维护第二套错误码表。

### 2.2 全局异常处理 `GlobalExceptionHandler`

`@RestControllerAdvice`，三类异常三种处理：

| 异常 | 处理 | 为什么 |
|---|---|---|
| `BizException` | 用异常自带的 code/message 返回，**只打 warn 不打堆栈** | 可预期的失败（库存不足、越权），堆栈是噪音 |
| `MethodArgumentNotValidException` | 取**第一条**字段错误当提示 | `@Valid` 校验失败，一次只提示一个更清楚 |
| 其他 `Exception` | 打 error 全堆栈，对外只回「服务器内部错误」 | 不把内部细节泄给调用方 |

`BizException` 带 `code` 字段，所以业务失败也能用非 400 的码（例如越权用 404）。

### 2.3 认证与鉴权全链路

**无状态**：`SessionCreationPolicy.STATELESS`，不存 session，全靠 JWT。

**注册**（`AuthServiceImpl.register`）：
1. 密码 `BCryptPasswordEncoder.encode()` 后落库（**绝不明文**）
2. 插入失败捕获 `DuplicateKeyException` → 说明用户名被占

> 关键点：靠数据库 `uk_username` 唯一索引兜底，**不是「先 select 再 insert」**——
> 后者两个请求同时来会双双通过检查，然后一个插入失败。

**登录**（`login`）：查库 → `passwordEncoder.matches()` → 签发 JWT。
用户不存在与密码错误返回**同一句提示**，不泄露「这个账号是否注册过」。

**JWT 内容**（`JwtService`）：subject = userId，claim = username + role，
HS256 签名，有效期取 `itsmygo.jwt.expire-minutes`。

**每个后续请求**（`JwtAuthenticationFilter`，继承 `OncePerRequestFilter`）：

```
Authorization: Bearer xxx
   → jwtService.parse()           验签 + 验有效期，失败抛 JwtException
   → 成功：new UsernamePasswordAuthenticationToken(
              new CurrentUser(id, username, role),
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + role)))   ← 注意这个前缀
   → 放进 SecurityContextHolder
```

> 两个容易踩的点：
> 1. 解析失败**不在这里抛错**，只 `clearContext()` —— 让请求继续走到 Security 的
>    entry point，由它统一返回 401 JSON（否则会走 Servlet 容器的错误页）
> 2. authority 必须是 `ROLE_` 前缀，因为 `SecurityConfig` 里用的是 `hasRole("ADMIN")`

**路径权限**（`SecurityConfig`）按从宽到严排列，**顺序不能乱**：

```java
.requestMatchers("/api/health", "/api/auth/register", "/api/auth/login").permitAll()
.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

401 与 403 也是统一响应：`authenticationEntryPoint` 返回未登录，
`accessDeniedHandler` 返回权限不足，都手写 JSON（`writeError` 方法）。

**取当前用户**：Controller 参数上写 `@AuthenticationPrincipal CurrentUser user`，
直接用 `user.id()`，**不要**自己去读 `SecurityContextHolder`。

### 2.4 数据库迁移（Flyway）

`src/main/resources/db/migration/V{n}__{name}.sql`，启动时自动执行，已到 **v5**。

**规矩：只增不改。** 已执行过的脚本改内容，Flyway 校验不过会启动失败。
要改表结构就加新脚本（如 V3 给 users 加 role 就是 ALTER）。

Flyway 自己建的 `flyway_schema_history` 表是迁移记录，**不要动**。

---

## 3. 业务模块实现要点

### 3.1 账号（`AuthServiceImpl`）

已在 §2.3 讲完注册/登录。补充：注册出来的用户 role 固定为 `Roles.CUSTOMER`，
**管理员不开放自助注册**（只能直接改数据库），否则任何人都能把自己提权成管理员。

**用户中心的三件事**（`UserServiceImpl` + `UserController`，`/api/user/*`）：

- `GET/PUT /api/user/profile`：昵称与手机号。只更新传进来的字段（`updateById`），
  不要把查出来的整条 User 再写回去 —— 会覆盖并发改过的其它字段。
  头像**不在这个 DTO 里**：它只能来自上传接口，否则用户能提交任意 URL
- `PUT /api/user/password`：先 `passwordEncoder.matches` 校验旧密码，**失败返回 400 而不是 401** ——
  401 会触发前端拦截器自动登出，用户只是输错一次旧密码不该被踢出去。新旧相同也拒绝
- `POST /api/user/avatar`：`StorageService.uploadAvatar`（对象放 MinIO 的 `avatars/`，
  和商品图的 `products/` 分开），按用户限流 10/分钟，单独限 2MB

`LoginResponse` 里也带上了 `avatar`，这样重新登录后头像不会丢。
改密码**不会吊销 token**（JWT 无状态），由前端改完后主动 `logout()`。

### 3.2 收货地址（`AddressServiceImpl`）—— 越权校验的样板

**越权校验只有一出口**（`requireOwned`）：

```java
private Address requireOwned(Long userId, Long id) {
    Address address = addressMapper.selectById(id);
    if (address == null || !userId.equals(address.getUserId())) {
        throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
    }
    return address;
}
```

**为什么是 404 而不是 403**：403 等于告诉调用方「这条数据存在，只是不属于你」——
把别人数据的 id 空间暴露了。404 则一律当「不存在」，什么也不透露。

增删改全部先过这个方法；列表查询则直接在 SQL 里 `eq(userId)`。

**默认地址唯一**：设置某条为默认时，先 `clearDefault(userId)` 把该用户其他地址清零，
两步包在 `@Transactional` 里。

**手机号校验**：按大陆口径 `^1[3-9]\d{9}$`（1 开头、第二位 3-9、共 11 位），
和淘宝/京东一致，**只收手机号、不接座机**。前端 `el-form` 用同一条正则即时提示，
后端 `@Pattern` 作最终防线 —— 前端校验只是体验，真正拦住脏数据的是后端。
个人资料里的手机号是**选填**，空串放行（正则里带 `^$` 分支）。

### 3.3 分类（`CategoryServiceImpl`）—— 树形结构怎么组装

`tree()` **一次查全表，再在内存里递归组树**：

```java
List<Category> all = categoryMapper.selectList(...);
Map<Long, List<Category>> byParent = all.stream()
        .collect(Collectors.groupingBy(Category::getParentId));   // 按父 ID 分组
return buildNodes(TOP_LEVEL, byParent);                            // 从 0 递归下去
```

> **不按层递归查库**（那会有 N+1 次查询）。分类表很小，全查一次内存分组最快。
> 真实电商里分类也就几百条，这个做法一直够用。

**删除前两道校验**：下面还有子分类 → 拒绝；下面还挂着商品 → 拒绝。
另外 `update()` 里挡了「把自己设成自己的父分类」。

**顾客端 vs 管理端**：同一个 `tree(boolean onlyEnabled)`，
顾客端传 `true`（只出 status=1），管理端传 `false`（全出）。

### 3.4 商品（`ProductServiceImpl`）

**分页**用 MyBatis-Plus 的 `Page`，但**必须先在 `MyBatisPlusConfig` 注册
`PaginationInnerInterceptor`**，否则既不拼 LIMIT 也不查总数（total 恒为 0）。

**查询条件**全用 Lambda Wrapper，三个条件的开关靠第一个布尔参数：

```java
.eq(onlyOnSale, Product::getStatus, ON_SALE)          // 顾客端才加 status=1
.eq(categoryId != null, Product::getCategoryId, categoryId)
.and(hasText(keyword), w -> w.like(Product::getName, keyword)
                              .or().like(Product::getSubtitle, keyword))
```

**关键词搜索目前用 LIKE**（`%kw%`）—— 数据量小够用，不需要配 ngram 分词。
上万条以后再换 FULLTEXT 或搜索引擎，代码里已留注释。

**上架/下架的双重效果**：顾客端列表里看不到（`status=1` 过滤），
**详情也返回 404**（`detailForCustomer` 里判断），管理端则全都能看。

**分页参数归一化**：页码最小 1，每页条数夹在 1–100，防止 `size=999999` 打穿数据库。

### 3.5 对象存储（`StorageServiceImpl`）

**复用本机共用的 MinIO 实例**，靠 bucket 隔离（`its-my-go-product-images`）。

上传流程：

```
MultipartFile
  → 扩展名白名单校验（jpg/jpeg/png/gif/webp）——防止传 .sh/.jsp 之类
  → ensureBucket()：懒初始化，bucket 不存在就建 + 配公开只读策略
  → objectName = prefix + UUID + "." + 扩展名    ← UUID 防重名/防路径穿越
  → minioClient.putObject(...)
  → 返回 http://localhost:9000/its-my-go-product-images/{prefix}xxx.png
```

按用途分三个前缀：商品图 `products/`（5MB multipart 上限）、头像 `avatars/`（2MB）、
店铺 Logo `merchants/`（2MB）。店铺 Logo 有两个入口：商家后台走
`POST /api/merchant/files/upload`；**入驻申请人还没有 MERCHANT 角色**，走
`POST /api/merchant-apply/logo`（仅要求登录，按用户限流）。

**桶策略设为公开只读**（`s3:GetObject` 允许 `*`），这样商品图 URL 能直接放进 `<img src>`
被浏览器加载，不用每次生成预签名 URL。

**懒初始化而不是 `@PostConstruct`**：MinIO 没起来时应用照样能启动，
只有真正上传时才报错 —— 否则整个应用会被一个非核心依赖拖死。

**幂等**：`bucketReady` 标志 + 双重检查，避免每次上传都去查一次 bucket 是否存在。

### 3.6 购物车（`CartServiceImpl`）

**加购用一条原子 SQL**，而不是「先查再插/改」：

```sql
INSERT INTO cart_items (user_id, product_id, sku_id, quantity, selected)
VALUES (?, ?, ?, ?, 1)
ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}
```

> 靠 `uk_user_sku (user_id, sku_id)` 唯一索引 + `ON DUPLICATE KEY UPDATE` 一次搞定
> 「不存在就插、已存在就累加」。换成「先 select 判断再 insert/update」，
> 两个请求同时加购同一规格会双双查到「不存在」、双双插入，其中一个必然撞唯一索引报错。
>
> 唯一键从「用户 + 商品」换成「用户 + SKU」之后，**同一件商品的不同规格可以各占一行** ——
> 这正是多规格购物车和单规格时代最大的区别（见 §3.12）。

**视图组装**（`buildView`）：购物车行只存 `product_id`，展示需要商品名/图/价/库存 ——
所以**按 productId 批量查一次商品、再按 skuId 批量查一次 SKU，然后内存拼装**，
不做逐行查库（N+1），也不做 join
（join 了还得处理重复列名）。

**商品被删除时**不隐藏这一行，而是显示「商品已删除」让用户自己删 —— 静默消失比看得见的异常更让人困惑。

**总价只算「已勾选 且 可购买」的项**，且用 `BigDecimal` 累加。

### 3.7 订单与状态机（`OrderServiceImpl` / `OrderStatus`）

**状态机集中在一个枚举里**（`OrderStatus.canTransitionTo`），不允许散落到各个 Service 里 `if`：

```
待支付 ──支付──> 待发货 ──发货──> 待收货 ──确认收货──> 已完成
   │                 │
   └──取消──> 已取消  └──取消──> 已取消        待收货之后不能取消，已完成/已取消是终态
```

**展示文案**：`PAID` 显示「待发货」，`SHIPPED` 显示「待收货」；枚举名、数据库存储值和
接口 `status` 不变，老订单无需迁移。顾客端订单 Tab 按 `PAID` / `SHIPPED` 拆成
「待发货」「待收货」。

所有状态变更都走同一个 `transition(order, target, stampSetter)`：先问状态机准不准，
不准就抛「订单当前是「待支付」，不能变成「待收货」」，然后才改状态和时间戳。
**非法流转在这一处被挡住**，而不是在每个接口里各写一遍校验。

**两种下单来源：购物车结算 / 立即购买**

- `CreateOrderRequest.items` **为空** → 从购物车**勾选项**下单，下单后删掉这些行
- `items` **传了** → 就是「立即购买」，**完全不碰购物车**

两种来源在 Service 里先归一到同一个内部结构，之后走同一条路：

```java
private record OrderLine(Long productId, int quantity) { }

boolean fromCart = request.items() == null || request.items().isEmpty();
List<OrderLine> lines = fromCart ? selectedCartLines(userId) : buyNowLines(request.items());
...
// 只有购物车结算才清空勾选行 —— 「立即购买」从头到尾没碰过购物车
if (fromCart) { cartItemMapper.delete(...); }
```

**最后那个 `if` 就是两种来源唯一的实质差异**，也是「立即购买」的语义所在：
用户没让我加购物车，我就不能动它。库存扣减、快照、令牌那些全都共用。

**订单快照**（`order_items` 的 `product_name` / `product_image` / `spec_text` / `unit_price`）：

```java
item.setProductName(product.getName());     // 下单那一刻的名字
item.setUnitPrice(sku.getPrice());          // 下单那一刻**那一款**的价格
item.setSpecText(sku.getSpecText());        // 「颜色:黑色;版本:静音版」
```

`product_id` / `sku_id` 只是留个追溯线索，**展示订单时绝不能 join 商品表取当前值** ——
否则商品一改价，所有历史订单的金额跟着变，对账就废了。

`spec_text` 同样是快照、而且**必须**是快照：规格值会改名、会被删掉，
历史订单得原样显示当时买的是哪一款。`SkuOrderTest` 里有一条专门盯这个 ——
下单后把「白色」改名叫「珍珠白」，订单里读出来仍是「白色」。

收货信息同理：`orders.receiver / phone / address` 也是下单时的快照，
用户事后改地址或删地址都不影响历史订单。

**订单号**：`yyyyMMddHHmmss` + 6 位随机数，冲突由 `uk_order_no` 唯一索引兜底。

### 3.7.1 状态流转必须是原子的（M4 补）

原先的 `transition` 是**两步**：`selectById` 读出来 → 判断 `canTransitionTo` → `updateById` 写回。
**两步之间有窗口**，并发请求会双双读到同一个旧状态、双双通过检查。

M3 时后果不明显（只是状态被写两遍，结果一样）。M4 加上支付流水后它变成了**真资损**：

| 场景 | 没有原子性会怎样 |
|---|---|
| 并发支付 | 生成**多条支付流水**（被 `payments.uk_order_id` 唯一索引兜住了） |
| **并发取消** | 执行**多次 `restoreStock`** → **库存凭空多出来**（没有任何唯一索引兜得住） |

改成条件更新，把「判断」和「修改」合成一条 SQL（跟库存扣减 §3.8 同一套路）：

```sql
UPDATE orders SET status = #{target} WHERE id = #{id} AND status = #{expected}
```

影响行数为 0 就说明状态已被别人改过，直接拒。

**变异验证**（去掉条件更新后跑测试）证实了这件事：

```
concurrentCancelRestoresStockOnlyOnce  ✗ 期望 1 个成功，实际 5 个全成功 → 库存回滚 5 次
concurrentPayOnlySucceedsOnce          ✓ 仍通过（被唯一索引兜住）
```

**教训**：测并发要挑**没有唯一索引兜底**的场景，否则测试会被兜底机制掩盖成"通过"。

### 3.7.2 支付流水与退款（M4）

**支付**：`pay()` 除了转状态，还往 `payments` 写一条流水（支付方式、流水号、金额、时间）。
`uk_order_id` 是数据库层兜底 —— 一个订单不可能有两条成功支付记录。

**退款**走完整的状态流转，**不是直接改状态**：

```
待发货 / 待收货 / 已完成 ──申请──> 退款中 ──同意──> 已退款（终态）
                                    │
                                    └──驳回──> 回到申请前的状态
```

三个实现要点：

1. **申请时记下 `status_before_refund`** —— 驳回要回到原状态，不记就回不去了
2. **审核期间库存不动** —— 万一被驳回呢。只有**同意**时才 `restoreStock`
3. **同意退款时同步把支付流水置为 `REFUNDED`** —— 那才是退款真正的凭据

**退款记录（V24）**：申请时记录原因（≤200 字，`RefundRequest`；顾客端强制填写，
后端允许为空以兼容智能体代申请与老客户端），同时写入 `refund_reason` 与
`refund_applied_at`；同意 / 驳回时写 `refund_handled_at`。
被驳回后再次申请会**清空上次的处理时间**。`refund_reason` 默认空串，
迁移时把存量 `REFUNDING / REFUNDED` 订单的 `refund_applied_at` 从 `updated_at` 回填，
`refund_handled_at` 只回填已退款订单。
`status_before_refund` 和 `refund_handled_at` 的「清空」都必须用
`LambdaUpdateWrapper.set(..., null)` —— `updateById` 会忽略 null 字段，
看起来执行成功、实际没清掉。

**超时取消**（`cancelExpired`）：批量取消超过 N 分钟未支付的订单并回滚库存。
管理端仍保留手动触发入口；M17 起另外挂了两条自动链路：
**RabbitMQ 延迟消息准时关单 + 数据库定时扫描兜底**，见 §3.26。

⚠️ **时间基准必须用数据库的 `NOW()`**：

```java
.apply("created_at < DATE_SUB(NOW(), INTERVAL {0} MINUTE)", window)
```

**不能拿 JVM 的 `LocalDateTime.now()` 去比** —— 本机 MySQL 容器原本跑在 UTC、应用跑在 UTC+8，
两者差 8 小时，结果是**刚下的订单被当成「已经超时 8 小时」立刻取消**。
（这个 bug 是 `freshOrderIsNotCancelledByExpiryJob` 抓出来的。）

**时区已经统一了**（见 HANDOFF §5.14）：容器用 `SET PERSIST time_zone='+08:00'` 改成了 +08:00。
但**这条规矩仍然成立** —— 拿应用时间去比数据库时间，等于默认两边时区一致，
这个假设迟早会破。让比较的两边都来自同一个时钟源最稳。

**防重复提交（一次性提交令牌）**

用户连点两次「下单」会生成两笔订单、扣两次库存 —— 这是实打实的资损。
前端禁用按钮不够（网络重试、直接调接口都绕得过去），要靠服务端。

```
① 进结算页：GET /api/orders/token  →  Redis 存 its-my-go:order-token:{userId}:{token}，TTL 10 分钟
② 提交订单：POST /api/orders 带上 token
③ 服务端：DEL 这个 key  →  返回 1 = 首次放行；返回 0 = 重复提交，拒
```

**关键在 `DEL` 是原子的**：两个并发请求同时删同一个 key，Redis 单线程处理，
只有一个返回 1 —— 所以**不需要额外加锁**就保证了「只有一个请求能往下走」。
（对比「先 GET 判断存在再 DEL」，那中间有竞态窗口。）

三个细节：

1. **令牌 key 里带 userId** —— 别人的令牌用不了（有独立测试守着）
2. **令牌消费放在业务逻辑之前** —— 重复提交在第一时间被挡，一个字节的业务都不会执行
3. **业务失败要把令牌还回去** —— `consumeToken` 之后套 `try/catch`，
   失败时 `restoreToken`；否则用户遇到「库存不足」改完数量再提交，令牌已经没了。
   注意区分：**「重复提交」不归还，「业务失败」才归还**

令牌必须带 TTL：既满足共用 Redis 的硬约束，也让没人用的令牌自然消失。

### 3.8 库存扣减（`StockServiceImpl` + `ProductSkuMapper.deductStock`）—— 本项目最值钱的一段

**要防的问题**：两个人同时买最后一件，不能都下单成功。

**粒度是 SKU，不是商品** —— 同一件商品的黑色和白色各算各的库存。
商品上的 `stock` 只是各 SKU 的汇总，不参与任何判定（见 §3.12）。

**两层防线**：

**第一层 · Redis 预扣** —— 原子 Lua 脚本（GET + 比较 + DECRBY 三步必须原子，分开写就有窗口）：

```lua
local stock = redis.call('GET', KEYS[1])
if not stock then return -1 end
if tonumber(stock) < tonumber(ARGV[1]) then return -1 end
return redis.call('DECRBY', KEYS[1], ARGV[1])
```

作用是**把绝大多数并发请求挡在数据库之外**。键是 `its-my-go:stock:sku:{skuId}`，
**必须带 TTL**（30 分钟 ± 5 分钟抖动）——共用 Redis 是 volatile-lru，
不带 TTL 的键在内存打满时写失败。

**加载用互斥锁，防缓存击穿**：

```
                    库存键刚过期（TTL 到点）
                             ↓
   请求 1..N 同时发现 isLoaded() = false
                             ↓
        抢 SETNX 加载锁（its-my-go:stock:sku:{id}:loading，TTL 5 秒）
              ↓                          ↓
        抢到的 1 个                   没抢到的 N-1 个
        双重检查 → 查库 → 写回          自旋等待（50ms × 最多 10 轮）
        finally 里放锁                  → 等不到就放弃，按库存不足处理
                             ↓
                  数据库总共只被查了 1 次
```

**为什么必须有这把锁**：`setIfAbsent` 只能保证**写入**唯一，**挡不住重复查询** ——
键一过期，N 个并发会一起 `selectById`，而「不让请求打到数据库」恰恰是这层缓存存在的意义。
这就是缓存击穿。

细节：① 抢锁用 `setIfAbsent(key, val, ttl)` 一条命令，**别拆成 SETNX + EXPIRE**
（两步之间崩掉就是死锁）；② 锁**必须带 TTL**；③ 拿到锁后**必须双重检查**，
因为抢锁期间上一个持锁者可能已经写好了；④ 放锁在 `finally` 里；
⑤ 没抢到的线程**绝不能也去查库**，只能等。

**实测对比**（`StockCacheBreakdownTest`，20 并发 + 故意 200ms 慢查询）：

| | 数据库被查询次数 |
|---|---|
| 有互斥锁 | **1** |
| 去掉互斥锁（临时改代码跑过一次） | **20** ← 这就是击穿 |

键不存在时从数据库加载，用 `setIfAbsent` 避免并发加载互相覆盖。

**第二层 · 数据库条件更新** —— **这才是防超卖的最终真相**：

```sql
UPDATE product_skus
SET stock = stock - #{quantity}, sales = sales + #{quantity}
WHERE id = #{skuId} AND status = 1 AND stock >= #{quantity}
```

`WHERE stock >= ?` 让「判断」和「扣减」在同一条 SQL 里完成，InnoDB 行锁保证原子，
**返回 0 就代表库存不足**。用 `stock = stock - ?` 而不是「先查出来再写回」——
后者并发下会互相覆盖，是经典的丢更新。

**为什么两层都要**：Redis 那层不追求与数据库强一致（键会过期、可能偏乐观/偏保守），
它只负责削峰；**正确性完全由数据库条件更新裁决**。第一层过了第二层没过，照样按库存不足处理。

**Redis 不在事务里，失败要手动回补**：

```java
Map<Long, Integer> reserved = new LinkedHashMap<>();
try {
    for (OrderLine line : lines) {
        ProductSku sku = skus.get(line.skuId());
        if (!stockService.tryReserve(sku.getId(), qty)) throw ...;       // 第一层
        reserved.merge(sku.getId(), qty, Integer::sum);
        if (skuMapper.deductStock(sku.getId(), qty) == 0) throw ...;     // 第二层
        productMapper.applyDeducted(sku.getProductId(), qty);            // 商品汇总，只为显示
    }
    // …建订单、清购物车
} catch (RuntimeException e) {
    reserved.forEach(stockService::release);   // 数据库由 @Transactional 回滚，Redis 得手动补
    throw e;
}
```

**取消订单会回滚库存**（`restoreStock`）：SKU 行 `stock + n`、`sales - n`，Redis 同步 `INCRBY`，
商品汇总用 `applyRestored` 拉回去。回补时只补**已存在**的键 ——
键过期后再 `INCRBY` 会凭空造出一个错误的库存值。

⚠️ 回补用的是订单项里那个 **`skuId`**（`item.getSkuId()`）。传成 `productId` 的话
数字看着也会动、测试也可能"过"，但补的是别人的库存 —— `SkuOrderTest` 里那条
「另一款纹丝不动」的断言就是专门盯这个的。

### 3.9 缓存防护另两项：穿透与雪崩

§3.8 讲的是**击穿**（热 key 过期 → 并发回源）。另外两件容易混为一谈的事：

| | 触发条件 | 本项目的对策 |
|---|---|---|
| **击穿** | 热 key **过期**，并发同时回源 | 加载加互斥锁（§3.8） |
| **穿透** | 查**压根不存在**的数据，缓存永不命中 | 布隆过滤器（本节） |
| **雪崩** | **大量 key 同时过期** | TTL 加随机抖动（本节） |

#### 穿透 → 布隆过滤器（`ProductBloomFilter`）

查一个不存在的商品 ID，库存缓存里当然没有它，于是每次都打到数据库 —— Redis 完全不起作用。
恶意刷接口时这就成了打库的利器。

**用 Redis bitmap 自己实现**（而不是 Guava 的本地布隆过滤器）：本地版在**多实例部署下各存一份**，
商品上下架无法同步，重启还会丢；放 Redis 里天然是分布式共享的。

```
BIT_SIZE = 2^20 位（128 KB）·  HASH_COUNT = 6 个 hash 函数
add          → 6 个 bit 全置 1
mightContain → 6 个 bit 全为 1 才返回 true
```

**特性**：说「不存在」**一定准**，说「存在」**有小概率误判**（假阳性）。
所以它只能用来**挡掉确定不存在的请求**，命中了的仍要正常查库：

```java
if (!bloomFilter.mightContain(id)) {
    throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");   // 一次库都不查
}
Product product = productMapper.selectById(id);                        // 命中的照常查
```

**两个必须做的维护点**：
1. **新商品创建时增量加入**（`ProductServiceImpl.create` 里 `bloomFilter.add`），
   否则刚上架的商品自己的详情页会被判成「不存在」
2. **应用启动时预热**（实现 `ApplicationRunner`）—— 重启后 Redis 里的位图可能已过期，
   过滤器若是空的会**把所有请求都判为不存在**，比没有过滤器还糟

6 个 bit 的读写各用一条 Lua 脚本一次往返完成，不是 6 次网络调用。

#### 雪崩 → TTL 随机抖动

库存键的 TTL 原本固定 30 分钟。若一批商品是同时被加载的，它们的键就会在**同一秒集体过期**，
那一瞬间所有请求一起回源 —— 和击穿的现象一样，只是成因是「同时过期」而非「单个热 key」。

```java
private Duration stockTtl() {
    long jitter = ThreadLocalRandom.current()
            .nextLong(-300, 301);              // ±5 分钟
    return STOCK_TTL_BASE.plusSeconds(jitter);
}
```

**凡是给一批 key 设 TTL，就该带抖动** —— 这条在实现里比记在文档里重要。

### 3.10 Cache-Aside：商品详情与分类树（`RedisCacheHelper`）

前面三节讲的是「缓存怎么不出事」，这节讲**缓存本身怎么用**。

**只有两处该缓存**，其余查询照旧直查数据库：

| 接口 | 缓存？ | 理由 |
|---|---|---|
| `GET /api/products/{id}` | ✅ `its-my-go:product:{id}`，TTL 10 分钟 | 热点、读多写少、所有人看到的一样 |
| `GET /api/categories` | ✅ `its-my-go:category:tree`，TTL 30 分钟 | 极热点、极少变动 |
| `GET /api/products`（列表/搜索） | ❌ | 关键词 × 分类 × 分页的组合太多，key 会爆炸 |
| 购物车 / 订单 | ❌ | 用户私有 + 频繁变动 |
| `GET /api/auth/me` | ❌ | 根本不查库 |

**读**（`getOrLoad`）：命中直接返回；未命中时**用互斥锁保护回源**（复用 §3.8 那套），
再写回缓存。写回时 TTL 带 ±20% 抖动。

**写**（`evict`）—— 这里是**双写一致性**真正登场的地方：

```java
productMapper.updateById(product);                       // ① 先更新数据库
cacheHelper.evict(cacheKey(id));                         // ② 再删缓存
```

**为什么是「删」不是「更新」**：并发更新两个请求时，「写缓存的顺序」可能和
「写库的顺序」相反，更新缓存会留下永久脏数据；删掉则下次读自然加载最新。
**删失败**也不要紧 —— TTL 是最终兜底，最多脏一个 TTL 周期。

**分类树为什么用数组接缓存**：`Class<T>` 传不了泛型（`List<CategoryNode>`），
用 `CategoryNode[].class` 绕开，出来再 `List.of` 包一层。

**管理端那份分类树不缓存** —— 管理员操作本来就少，缓存只会让「改完看不到自己改的」
变成困惑。

### 3.11 接口限流（`common/ratelimit`）

**用法**：在 Controller 方法上标注解，`RateLimitAspect` 拦截。

```java
@RateLimit(scene = "register", limit = 5, windowSeconds = 60, byIp = true)
@RateLimit(scene = "order",    limit = 10, windowSeconds = 60, byUser = true)
```

| 接口 | 粒度 | 配额 | 防什么 |
|---|---|---|---|
| 注册 | IP | 5/分钟 | 批量注册 |
| 登录 | IP | 10/分钟 | **密码暴力破解** |
| 下单 | 用户 | 10/分钟 | 脚本刷单（与提交令牌双重保险） |
| 上传 | 用户 | 20/分钟 | 刷存储 |

**算法：固定窗口计数 + Lua 原子**。INCR、首次设 TTL、判断三步必须在一条脚本里：

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
if count > tonumber(ARGV[2]) then return 0 end
return 1
```

分开写的话，「INCR 之后、EXPIRE 之前」进程挂掉会留下**永不过期的计数键**，
那个场景的配额就永远卡死了。

**窗口边界**会放行到接近 2 倍配额（第 59 秒打满、第 61 秒又能打满）。对「防刷防爆破」
完全可以接受；要精确得用滑动窗口（ZSET 记时间戳），代价是每个请求都要存一个成员。

**Redis 挂了怎么办 → fail-open（放行）**。限流是保护措施，不该因为它自己抖了一下
就把整个站点拒之门外。

**刻意不读 `X-Forwarded-For`**：那个头客户端可以随便伪造，信它等于给攻击者
无限换 IP 绕过限流的机会。本项目单体直连、前面没有可信代理，用 `remoteAddr` 就够。
将来真上了 Nginx，得改成只信任来自已知代理的 XFF。

**总开关** `itsmygo.ratelimit.enabled`（默认 true）—— 生产出事故时可以一键关掉止血。

#### 四个必须知道的坑（当天全踩了一遍）

**① 库存拆成独立接口，详情里的 `stock` 也不受缓存影响**

一开始详情缓存里的 `stock` 会滞后（下单不失效它，否则高频下单会把缓存打穿）。
最终方案是**把库存从「缓存的字段」里拿出去**：

- 新增 `GET /api/products/{id}/stock` → 实时库存
- **数据来源是 §3.8 那个库存计数器**（`its-my-go:stock:sku:{skuId}`），它本来就在被实时维护
  （下单 DECRBY、取消 INCRBY），**读它一次库都不用查**
- 详情接口里也把 `stock` 换成计数器的值 —— 从缓存反序列化出来的对象是新实例，
  改它不会污染缓存

**代价是引入了一个新的同步点**：`ProductServiceImpl.update` 里改完库存必须
`stockService.reload(id)`，否则管理员补了货顾客仍看到旧数字。**这个 bug 是测试抓出来的。**

**② 布隆过滤器只覆盖「走 create 接口」创建的商品**

`ProductServiceImpl.create` 会 `bloomFilter.add`，但**直接写库的商品**（数据迁移、
手工 SQL、导入脚本）不在过滤器里，会被**永久判为「不存在」**。三层补救：

- `ProductBloomFilter.rebuild()`：按主键游标分批全量重建
- `POST /api/admin/products/rebuild-bloom`：手工插库后调一次
- `@Scheduled` 每天凌晨 4:17 自动重建一次兜底

**重建必须「建到临时 key 再 RENAME 切换」**，绝不能「先删再建」——
先删的话重建完成前过滤器是空的，那一小段时间里**所有商品都被判为不存在**，比不重建还糟。

```java
redisTemplate.rename(tempKey, KEY);   // 原子切换
```

⚠️ 多实例部署时 `@Scheduled` 会在每个实例上各跑一遍，届时要么加分布式锁、
要么只在指定实例执行。目前是单体，无所谓。

**③ 缓存不参与数据库事务回滚**

`@Transactional` 的测试里，数据库会回滚但**缓存不会** —— 测试 A 写进缓存的分类树
在回滚后仍然存在，测试 B 就会读到「数据库里根本没有」的数据。
生产环境同理：**回滚了事务，别忘了缓存里可能还留着那份数据。**

**④ 限流计数同样跨用例累积，而且基类没被所有测试继承**

限流计数存在 Redis 里，也是全局状态：所有测试都来自同一个 IP，
注册配额 5 次/分钟，跑几个用例就撞墙了。

排查时绕了很久，最后发现真正的原因是：**`AuthControllerTest` 和 `AddressControllerTest`
是 M1 时期写的，压根没继承 `ApiTestBase`**（基类是 M2 才引入的），
所以基类的清理逻辑对它们从来没生效过。

**解法**：把「清限流计数」放进 `customerToken(text)` / `adminToken(text)`
这些**真正会打认证接口的 helper** 里，而不是只依赖 `@BeforeEach` ——
每次要打接口前都清一次，比依赖生命周期回调的时机可靠得多。

> 试过但**没用**的方案：`@ActiveProfiles` + `application-test.yml`、
> `@TestPropertySource` 标在抽象父类上 —— 后者实际表现是**上下文缓存没按预期区分**，
> 同一个类里既有 `开关=true` 又有 `开关=false`。抽象父类上的测试注解不要指望它可靠。

---

### 3.12 商品规格与 SKU

价格和库存的**真相在 SKU 上**，不在商品上。这是本项目里最「重」的一次结构调整 ——
它牵动了所有按 `productId` 建立的东西（库存计数器、详情缓存、购物车唯一键、订单快照）。

**三张表**：

| 表 | 存什么 |
|---|---|
| `product_specs` | 规格定义，如「颜色」 |
| `product_spec_values` | 规格值，如「黑色」 |
| `product_skus` | 规格组合，**每行有独立的价格、库存、图片** |

**`spec_value_ids` 是这套东西的地基**：升序、逗号分隔的规格值 id（如 `"11,12"`），
落成 `UNIQUE KEY uk_product_specs (product_id, spec_value_ids)`。
为什么不用关联表 —— 关联表表达不了「一个 SKU 的规格值集合唯一」，
而组合的唯一性恰恰是这里最需要守住的东西。空串是合法值：它表示无规格商品的
**默认 SKU**，唯一键顺带保证每个商品只有一条。

⚠️ 写入前必须按**数值**排序再去重 —— `"10" < "2"` 是字符串比较的经典坑，
排错了就匹配不上，表现为「前端选了规格但价格不联动」。

**`products.price` / `stock` / `sales` 退化成汇总值**：价格取在售 SKU 的最低价、
库存与销量求和，另加一列 `has_specs` 供列表页显示「¥19.8 起」。
不删它们是为了**列表页分页零改动** —— 否则每次列表查询都要 join SKU 做聚合，配分页会很重。
汇总只在两处更新：管理端保存 SKU 时全量重算（`recalcFromSkus`）、
下单与取消时增量更新（`applyDeducted` / `applyRestored`，用 `GREATEST` 容忍漂移）。
**汇总不参与任何判定** —— 防超卖永远看 SKU 那行的条件更新。

**保存走整组覆盖**（`PUT /api/admin/products/{id}/skus`）：规格值一改，SKU 组合的集合就变了
（加一组「颜色」直接翻倍），逐个 CRUD 表达不了；商户在后台也是一次填完一整张表再提交。

**最容易出事的地方是「库存要保住」**：商户改个价格、给规格值改个名，
不能把 SKU 删了重建 —— 那样库存和销量全归零。做法是**按规范化后的 `spec_value_ids`
匹配到原来那行**，命中就原地更新，只改价格/图片/文本，**绝不动 stock**。

新建的规格值在提交时还没有 id，而 SKU 又要引用它 —— 约定是**用负数当临时 id**，
服务端插入后建立映射把引用一并改写（见 `SkuSaveRequest` 的类注释）。

**已有 SKU 的库存单独走一个接口**（`PUT /api/admin/products/{productId}/skus/{skuId}/stock`）：
整组保存**故意不碰已有行的库存**，否则商户改个价格就把库存冲掉；补货、盘库必须显式调它。
请求带 `expectedStock`（表单打开时看到的库存），服务端用
`UPDATE ... WHERE stock = ?` 做**条件更新** —— 期间被下单/取消改过就返回
400「库存已变化，请刷新后重试」，而不是硬覆盖把并发扣减冲掉（丢更新）。
成功后同样要刷三处：SKU 行、`recalcFromSkus`、`reload(skuId)`，并失效详情缓存。

**每个商品都必须有 SKU**：管理端 `create` 会自动建一条默认 SKU，
`V9__product_sku.sql` 也给存量商品回填过。少了它，详情页会显示总库存 0、
下单永远报「库存不足」—— 看着像库存 bug，其实是数据没铺开。

**缓存与三处库存口径**：详情缓存里存的是 Product（带 SKU 列表），
读出来时把每个 SKU 的库存换成 Redis 里的实时值。SKU 一旦变动，**三个地方要一起刷**：
SKU 行（写库）、商品汇总（`recalcFromSkus`）、Redis 计数器（`reload` / `drop`）——
漏掉任何一个，顾客看到的都是改动前的数字。

### 3.13 收藏与浏览足迹

两张表都是「用户-商品」关系，靠唯一键做幂等：

| 表 | 唯一键 | 语义 |
|---|---|---|
| `user_favorites` | `(user_id, product_id)` | 收藏；重复收藏用 `INSERT IGNORE` 静默忽略 |
| `user_footprints` | `(user_id, product_id)` | 足迹；重复浏览只把 `viewed_at` 更新到当前 |

**列表都是两条 SQL**：先分页查关系表，再按 productId 批量查商品，内存里拼成视图。
不 join、也不按行 N+1。商品下架后记录保留并显示「已下架」；商品被删则显示「商品已不存在」。

**足迹只保留最近 100 条**：`INSERT ... ON DUPLICATE KEY UPDATE viewed_at = NOW(3)` 之后，
只有 `count > 110` 才触发清理（避免每次浏览都全扫该用户的行）。清理 SQL 必须用
**双层派生表**：

```sql
DELETE FROM user_footprints
WHERE user_id = ? AND id NOT IN (
  SELECT id FROM (
    SELECT id FROM user_footprints WHERE user_id = ?
    ORDER BY viewed_at DESC, id DESC LIMIT 100
  ) keep_rows
)
```

原因：MySQL 8 的 `IN (SELECT ... LIMIT)` 报 1235，同表 DELETE 的直接子查询报 1093。
`viewed_at` 用 `DATETIME(3)`，排序再加 `id DESC` 兜底。

**上报时机**：商品详情页 `fetchProduct` 成功后才 `POST /api/footprints/{id}`，
失败静默；未登录不记录。收藏状态走独立的 `GET /api/favorites/{id}/status`，
**不要塞进商品详情缓存** —— 那是用户私有数据，缓存是整个用户共享的。

越权一律按「不存在」处理：收藏/足迹接口只操作 `@AuthenticationPrincipal` 的 userId。

### 3.14 商品评价

评价挂在**订单项**上而不是「用户+商品」：同一个人买两次可以评两次，且只有已完成订单能评。

| 表 | 关键约束 |
|---|---|
| `reviews` | `UNIQUE KEY uk_order_item (order_item_id)`；`idx_product_status (product_id, status, created_at)` |

**提交校验**（`ReviewServiceImpl.create`）：订单项存在 → 订单属于本人（否则 404）→
订单状态 `COMPLETED` → 该订单项未评价。并发时唯一索引是最终防线，捕获
`DuplicateKeyException` 转 400「该商品已评价」，不能让它冒成 500。

**展示**：商品公开评价只查 `status=1`；`GET /api/products/{id}/reviews/summary`
返回 `AVG` 与 `COUNT`（SQL 聚合，没有评价时 count=0、平均分 0）。
昵称/头像按 userId 批量查用户，避免 N+1；**不返回 userId**。

**管理端只做显示/隐藏**（`PUT /api/admin/reviews/{id}/visible`），不删除评价。
隐藏后商品详情不显示，但「我的评价」里仍能看到并标记「已隐藏」。

**订单页「已评价」标记**：`OrderItem` 有 `@TableField(exist=false) Boolean reviewed`，
`OrderServiceImpl` **只注入 `ReviewMapper`**（不注入 `ReviewService`，避免 Service 循环依赖），
在 `pageForCustomer` 里对已完成订单收集本页全部 itemIds、**一次** `IN` 查询后回填；
`view(order)` 同理。非已完成订单不返回该字段（`@JsonInclude(NON_NULL)`）。

### 3.15 优惠券与订单抵扣

**两张表**：

| 表 | 关键点 |
|---|---|
| `coupons` | 券模板：满减金额、发放总量 / 已领数、有效期、启停 |
| `user_coupons` | 用户券：领取时**快照**名称/门槛/减免/有效期；`uk_user_coupon` 每人每券一张；`uk_used_order` 一张订单最多一张券 |

**领取是幂等的，但计数必须条件更新**：

1. 预检模板存在、启用、未领完（只用于提示）
2. `INSERT user_coupons` 快照条款；`DuplicateKeyException` → 400「该优惠券已领取」
3. `UPDATE coupons SET claimed_count = claimed_count + 1
   WHERE id = ? AND claimed_count < total_count AND 有效期内`
4. 第 3 步 0 行 → 抛 400，事务回滚第 2 步

整个方法 `@Transactional`，中间不能吞异常。`total_count` 必须 `NOT NULL`。

**我的券状态**：DB 存 `UNUSED/USED/EXPIRED`；未使用但已过期的券在
**查询 SQL 里**用 `CASE WHEN valid_to < NOW() THEN 'EXPIRED' ELSE status END`
计算展示状态，筛选也走 SQL。不用 JVM 的 `now()`（见 HANDOFF §5.14 时区教训）。

**下单抵扣**（`OrderServiceImpl.doCreate`）：

1. `CreateOrderRequest.couponId` 是**用户券 id**，不是模板 id
2. 校验券属于本人、`UNUSED`、`threshold_amount <= 商品总额`；别人的券一律 404
3. `discount = min(券减免, 商品总额)`；`payable = 商品总额 - discount`
4. 订单写 `original_amount` / `discount_amount` / `user_coupon_id` / `coupon_name` 快照；
   `total_amount` 继续表示**实付**
5. 订单插入后条件更新券：
   ```sql
   UPDATE user_coupons SET status='USED', used_order_id=?, used_at=NOW()
   WHERE id=? AND user_id=? AND status='UNUSED'
     AND valid_from <= NOW() AND valid_to >= NOW()
   ```
   0 行 → 抛 400，事务回滚订单与库存；Redis 预扣由 `doCreate` 的 catch 回补

**退券**：抽成 `restoreCoupon(order)`，在 `cancel`、`approveRefund`、
**`cancelExpired`（逐单循环里）** 三处调用：

```sql
UPDATE user_coupons
SET status = IF(valid_to >= NOW(), 'UNUSED', 'EXPIRED'),
    used_order_id = NULL, used_at = NULL
WHERE used_order_id = ? AND status = 'USED'
```

未过期恢复可用、已过期置 EXPIRED；`WHERE ... status='USED'` 保证幂等。
方法内部 catch 并打日志，**退券失败不阻断取消/退款** —— 订单状态不能卡住。

**历史订单只读订单快照**：展示优惠用 `orders.coupon_name` / `discount_amount`，
**不要 join `user_coupons`** —— 券退回后会被新订单复用，旧订单指向的那条记录已经变了。

**权限**：`SecurityConfig` 只精确放行 `GET /api/coupons`（领券中心匿名可看）；
不能写 `/api/coupons/**`，否则 `/api/coupons/mine` 会变成匿名可访问。
管理端模板**不提供删除**，停用即可；停用只阻止新领取，已领的券仍按快照可用。

### 3.16 多商户：店铺、归属与按店拆单

**角色与默认商户**：`CUSTOMER` / `MERCHANT` / `ADMIN`。
`merchants` 表的 **id=1「平台自营」** 承接改造前的全部存量数据，`owner_user_id = NULL`，
只能由平台管理员管理，不允许审核、禁用或转让。

**入驻流程**：顾客 `POST /api/merchant-apply` → `PENDING` → 平台 `approve` 变 `ACTIVE`
并写入 `users.role=MERCHANT` / `users.merchant_id`；驳回可改资料重新提交；
平台可禁用/启用。审核用条件更新 `WHERE status='PENDING'`，防并发重复审核。
入驻页的 Logo 用 `POST /api/merchant-apply/logo` 上传（申请人尚无商家角色，
不能复用 `/api/merchant/files/upload`）；店铺资料页则走商家上传接口。
提交成功（含驳回后重提）后前端 `router.replace('/')` 回主页，刷新仍停在主页；
审核进度可再从用户面板的「商家入驻」进入查看。

**授权以数据库为准**：`JwtAuthenticationFilter` 只从 JWT 取 userId，
再用 `AuthIdentityService` 查用户 + 店铺快照（60 秒 Redis 缓存，变更时 evict）。
店铺不是 `ACTIVE` 时有效角色降为 CUSTOMER，**旧 token 立即失去商家权限**。
`merchantId` 永远由服务端身份推导，请求体里的 merchantId 一律不信任。

**商品归属**：`products.merchant_id`。顾客列表/详情只展示 `merchants.status='ACTIVE'`
的商品；详情命中缓存后仍要**在缓存外再查一次店铺状态**，否则禁用店铺后旧缓存还能访问。
商家接口 `/api/merchant/products/**`（含 SKU / 库存 / 上传）逐项校验商品归属，越权 404；
平台 `/api/admin/products` 可全量查看并按店铺筛选。分类仍由平台统一维护。

**发货地址**：V23 给 `merchants` 加 `ship_*` 字段，商家 / 平台自营各自设置；
虚拟物流起点优先取商家坐标，见 §3.22。

**购物车与店铺页**：`cart_items` **不存 merchant_id**，由商品派生，避免归属漂移；
`CartItemView` 带 merchantId/merchantName/merchantActive，前端按店分组。
公开店铺 `GET /api/shops/{id}` 只返回 ACTIVE 店铺；`/shop/:id` 展示店铺商品。

**按店拆单**：`orders.merchant_id` 是订单归属；`order_items` 不冗余 merchant（一单只属于一店）。
`CreateOrderRequest.coupons` 是 `[{merchantId, userCouponId}]`：

1. 后端按 `product.merchant_id` 分组、校验商品/规格/店铺
2. **先预校验所有券再动库存**：每组最多一张、同一张券全局去重（平台券只能给一个子订单）、
   店铺券必须匹配本店、门槛按该组商品总额
3. 按 skuId 排序预扣库存，逐组插订单、条件 markUsed 券、插明细
4. 清一次购物车勾选行，返回 `CheckoutView{orders:[...]}`（一个商家一单）

**事务回补**：整个拆单在一个 `@Transactional` 里。Redis 预扣与提交令牌归还除了业务 catch，
还注册 `TransactionSynchronization.afterCompletion(ROLLED_BACK)` —— 提交阶段失败也能回补；
用 `AtomicBoolean` 防止业务 catch 与事务回调重复回补。每个子订单独立支付、
取消、确认收货、退款；`payments.uk_order_id` 因此不用改。

**商家订单**：`/api/merchant/orders/**` 的列表/详情/发货/退款审核全部按 token merchantId 过滤，
别家订单一律 404。平台 `/api/admin/orders` 保留全平台监管，并支持按商户筛选。
订单响应里的 `merchantName` 是批量查店铺名回填的，列表不会 N+1。

**优惠券归属**：`coupons.merchant_id` 为 NULL 是平台券，非空是店铺券；
`user_coupons` 领取时快照 merchantId。平台券可指定给任意一个子订单，店铺券只能用于本店。
领券中心展示两类券并标注店铺；店铺券只在店铺 ACTIVE 时可领。
券一旦有领取记录，归属/金额/有效期不可再改（模板名称与启停仍可改）。

**评价归属**：`reviews.merchant_id` 下单时从 `orders.merchant_id` 快照；
商家 `/api/merchant/reviews` 只看/只隐藏本店评价，平台可全量并按商户筛选。

**迁移（V16–V18）**：V16 建 `merchants` + 默认商户 + `users.merchant_id`；
V17 给 `products` / `orders` / `reviews` 加 `merchant_id`（存量回填 1）；
V18 给 `coupons` / `user_coupons` 加可空 `merchant_id`（存量券保持平台券）。
V23 给 `merchants` 加发货地址字段（详见 §3.22）。

### 3.17 在线聊天与私信留言（V19）

**会话模型**：一店 × 一客只有一条会话（`conversations.uk_customer_merchant`）。
`last_message_id IS NULL` 的空会话不进列表。消息只追加，`messages.sender_type`
为 `CUSTOMER` / `MERCHANT`；`AGENT` 用于买小购代发的平台客服消息（M8 催发货）。

**权限**：顾客侧任意已登录非 ADMIN（商家账号也可作为顾客联系别家店）；
商家侧 `/api/merchant/chat/**` 必须有 MERCHANT 角色。会话参与者按
`customer_user_id` / `merchant_id` 判定，跨租户一律 404；**不提供商家创建会话的接口**，
从协议层保证商家不能主动私信。管理员不能使用顾客侧聊天接口，但可以通过
`/api/admin/chat/**` 以商家身份处理**平台自营店（merchant_id=1）**的会话。

**平台自营店**：`owner_user_id` 为 NULL，商家侧消息由**全部管理员共同接收**。
`merchantRecipients` 对普通店铺返回店主，对平台自营店返回所有正常状态的管理员；
SSE 推送、已读回执、在线状态（管理员连接即视为平台自营店在线）都走这一规则。
管理员接口只能访问 merchant_id=1 的会话，普通商家会话一律 404。

**未读与已读**：会话表冗余 `customer_unread` / `merchant_unread` 和双方
`last_read_message_id`。发消息时：

1. 插入消息；
2. `last_message_*` 只在**更大消息 id** 时覆盖（防并发旧消息倒灌）；
3. 原子累加对端未读。

已读时先把指针 `GREATEST` 只前进，再 `COUNT` 重算未读 —— **不能直接置 0**，
否则和并发到达的新消息竞争会丢未读。SSE `message` 事件与已读接口返回的
`unread` 都是**该身份的全局未读总数**，前端直接用于顶栏/菜单角标。

**卡片**：发送请求只传 `productId` / `orderId`，服务端查库校验归属后生成 JSON 快照存
`messages.card_data`；商品改价、订单改状态都不影响历史消息。图片只接受本系统 MinIO
`chat/` 前缀 URL，外链直接 400。文本 trim 后非空、≤2000 字。

**SSE**：

- EventSource 不能带 Authorization：`POST /api/chat/stream-ticket` 用 JWT 换
  **60 秒一次性票据**（Redis key `its-my-go:chat:ticket:{uuid}`，带 TTL）；
  `GET /api/chat/stream?ticket=` 用 `getAndDelete` 消费。SecurityConfig 只精确放行
  这一条 GET，其余 `/api/chat/**` 仍需登录。
- `ChatStreamRegistry` 是**单实例内存注册表**：按 userId 存连接，按 merchantId 统计
  在线商家；每条连接独立加锁发送，发送/心跳失败立即摘除。Redis 不缓存消息。
- 事件：`connected` / `message` / `read` / `presence`；`@Scheduled` 每 25 秒发心跳注释。
- 推送注册在 `TransactionSynchronization.afterCommit`，事务回滚不推送；推送失败不回滚消息，
  客户端重连后用 `afterId` 补齐。
- 多实例部署需要 Redis Pub/Sub 或粘性会话，本期单机，明确不做。

**入口行为**：从商品/店铺/订单页发起会话；只有**新建会话**时自动附一张卡片，
重复点「联系卖家」不会重复发。所有入口都要登录，未登录先跳
`/login?redirect=当前页`。平台自营店同样显示入口，由管理员在后台回复；
管理员自己浏览商城时不显示联系卖家按钮。

**迁移（V19）**：建 `conversations` 与 `messages`；会话表带唯一键
`uk_customer_merchant`、双方未读/已读指针；消息表索引 `(conversation_id, id)`。

### 3.18 客服智能体「买小购」接入（M6，V20）

**拓扑**：`浏览器 → Java（/api/agent/**）→ gRPC 8003 → rag_helper Agent 服务`。
浏览器只跟 Java 说话；agent 只在内网、只允许 Java 调用。
契约权威在 `rag_helper/proto/agent.proto`，本仓库的 `src/main/proto/agent.proto`
是原样镜像，**不能单方面修改**。

**Maven/protobuf**：原生 `grpc-java 1.68.2` + `protobuf 3.25.5`，用
`protobuf-maven-plugin` 生成 `agent.v1.*`。**Boot 4 的依赖管理会把部分 grpc 模块
拉到 1.83.1**，与 netty 1.68.2 混用会在运行时抛 `NoSuchMethodError`（表现为
`INTERNAL Panic! This is a bug!`），所以子 POM 里用 `grpc-bom` 显式锁死整套版本。

**传输**：`POST /api/agent/converse` 返回 `SseEmitter`（`text/event-stream`），
前端用 `fetch` + `ReadableStream` 消费。这里不用 EventSource：
每次 Converse 的事件流只属于本次调用，POST 又能直接带 Authorization，
不需要聊天系统那套全局 SSE 连接 + 票据。

**会话归属（双侧安全边界）**：
- **agent 侧**：`chat_message` 每条消息带 `user_id` / `tenant_id`；`chat_session`
  （`session_id` 唯一）记录会话 owner。Converse / Stop / GetHistory 先
  `ensure_session_owner`，owner 冲突返回 PERMISSION_DENIED；ListSessions、GetHistory
  都按 `x-user-id` 过滤；老消息没有 user_id 时，首次认领会补齐。
- **Java 侧**：`agent_sessions(user_id, session_id)`（V20）继续记录归属。
  新会话由 Java 生成 UUID 先落库，再调 Converse；Stop / History / 带 sessionId 的
  Converse 先校验 `session_id + user_id`，越权统一 404。
- `GET /api/agent/sessions` 调 agent 的 ListSessions（已按用户过滤），**再用 Java 归属表
  过滤一次**，双保险；前端据此展示历史会话、支持跨设备续聊。
- 历史里的执行流：`HistoryMessage` 增加 `repeated Trace trace = 5`（proto 加字段，
  向后兼容），agent 从 `chat_message.trace` 读回，Java 映射成 `traces[]`，
  前端折叠成一行、展开看全流程。

**gRPC 客户端**（`AgentGrpcClient`）：
- `NettyChannelBuilder` 明文内网，keepAlive 300s / timeout 20s / withoutCalls(false)
- 每次调用新建 metadata：`authorization: Bearer <服务端密钥>` + `x-user-id`
  （x-user-id 来自 JWT 的 `CurrentUser.id()`，不接受客户端传值）
- Converse deadline 默认 240s；Stop/GetHistory 15s；**不开启自动重试**
- 密钥来自 `.env` 的 `AGENT_GRPC_KEY`，缺失时应用照常启动、调用时返回明确错误

**SSE 事件**：`start / trace / delta / usage / final / error / done`。
`trace` 是统一执行流（`kind/agent/detail`），前端渲染成灰色小字；
`final` 带 `answer/images/webOnly`；`usage` 可能出现在 final 之后，
所以**收到 final 后继续读到 EOF**，最后补 `done{reason}` 再 complete。
每 20 秒发一次 SSE comment 心跳，SseEmitter 发送加锁。

**停止与取消**：`CancellableContext.cancel` 只能断 HTTP/2 流，Python worker
阻塞在队列上不会自动停；客户端断开 / 超时 / 用户点停止时，必须用
`run_id` 调 gRPC `Stop`（best-effort，可能返回 false）。

**线程池**：每条流独占线程（core 8 / max 32 / SynchronousQueue / AbortPolicy），
满了直接返回 429「客服繁忙」；Stop 与心跳放独立小池，避免被长流拖住。

**异步分派**：SseEmitter 完成时会走 ASYNC dispatch，SecurityConfig 必须放行
`DispatcherType.ASYNC/ERROR`，否则原始请求已鉴权、收尾时却被 403
（报错 `response is already committed`）。

**错误映射**：`UNAVAILABLE → AGENT_UNAVAILABLE`、`DEADLINE_EXCEEDED → AGENT_TIMEOUT`、
`UNAUTHENTICATED/PERMISSION_DENIED → AGENT_AUTH_FAILED`、
`RESOURCE_EXHAUSTED → AGENT_BUSY`、其它 → `AGENT_ERROR`。
Python 的 ErrorEvent 只给用户通用文案，原始 message 记日志。

**迁移（V20）**：建 `agent_sessions`；唯一键 `session_id`，索引 `user_id`。

### 3.19 订单查询工具回调（M7，2026-10-21）

**拓扑补了一条边**：`agent（rag_helper）→ Java /internal/agent/**`。
方向与 M6 相反，但仍然是内网、仍然不让浏览器参与：

```
浏览器 → Java（/api/agent/**）→ gRPC 8003 → agent
                                  ↑
          Java /internal/agent/orders/query ← agent 的 order-service 工具
```

**为什么是 agent 回调 Java，而不是把订单预注入请求**：下单草稿、退款申请这类
后续能力需要同步校验库存 / 地址 / 券；预注入只能给快照，给不了实时校验。
选型对比见 HANDOFF §6 的 2026-10-21 记录。

**票据（每次 Converse 一张）**：
- `AgentToolTicketService.issue(userId, sessionId)` 写 Redis
  `its-my-go:agent:tool-ticket:{uuid}`，TTL 10 分钟（硬约束：共用 Redis 的 key 必须带 TTL）
- `AgentGrpcClient` 把它放进 gRPC metadata `x-agent-tool-ticket`；**proto 不变**
- Python 的 gRPC 层读出后经 `run_main_agent(tool_ticket=...)` 放进 ContextVar；
  order-service 工具回调 Java 时带回 `X-Agent-Ticket`
- Java 只认票据里的 userId —— **模型不参与身份**，prompt injection 也传不了别人的 id

**接口**：`POST /internal/agent/orders/query`，`SecurityConfig` 放行
`/internal/agent/**`，鉴权完全由票据承担。请求体：
`{orderNo?, status?, limit?}`；`limit` 上限 10，默认 5。
返回 `Result<List<AgentOrderView>>`，**刻意不含收货人 / 电话 / 地址**，
只给订单号、状态、店铺、金额、物流单号、时间和商品行。

**归属**：`OrderServiceImpl.findForAgent` 按 `orderNo + userId` 或
`userId + status` 查；别人的订单返回空列表，不泄露存在性。

**Python 工具**：`query_orders(order_no="", status="", limit=5)` 用 httpx 调 Java；
没有票据时直接返回「当前会话没有订单查询权限」，不发请求；HTTP 失败 / 401 / 业务错误
都降级成可读提示，**不抛异常**（工具抛错会打断整轮对话）。

**验证**：Java 新增 `AgentToolControllerTest` 4 条 + `AgentControllerTest`
票据下发 1 条，全量 194 条全绿；rag_helper 回归 51/51（新增「订单查询：票据与格式化」）；
浏览器实测「我的订单现在都到哪一步了」→ 执行流出现 delegate order-service →
tool query_orders → final 输出 5 笔真实订单。

### 3.20 智能下单与退款申请的动作层（M9/M10，V21）

**为什么不能直接执行**：下单、退款是有真实后果的写操作。agent 只负责
「准备草稿」，用户必须在聊天卡片里确认，Java 才按服务端快照执行。
这与 evolution-plan §1 的三条红线一致：LLM 不直接写业务库、风控不用 LLM、
高危动作人工确认 + 审计。

**表 `agent_actions`（V21）**：`action_no`（对外 UUID）、`user_id`、`session_id`、
`target_id`（退款存订单 id）、`type`、`status`、`payload_json`（执行快照）、
`summary_json`（卡片展示）、`result_json`、`error_message`、`expires_at`、
`confirmed_at`、`executed_at`。状态机：`PENDING_CONFIRM → EXECUTING →
SUCCEEDED / FAILED`，另有 `CANCELLED` / `EXPIRED`。

**准备（agent 调内部接口）**：
- `POST /internal/agent/actions/order`：校验地址归属、商品上架、SKU 归属、库存；
  金额由 Java 计算（**智能下单暂不支持优惠券**）；签发一次性提交令牌并存入
  Redis `its-my-go:agent:action-token:{actionNo}`（TTL = 卡片有效期）；落
  PENDING_CONFIRM 快照。
- `POST /internal/agent/actions/refund`：按订单号 + userId 查订单，校验
  `OrderStatus.refundable()`；同一订单已有未过期草稿时直接返回那一张（幂等）；
  `target_id` 存订单 id。

**确认（浏览器调）**：
- `POST /api/agent/actions/{actionNo}/confirm`，只收 actionNo，**不收业务参数**。
- `startExecution` 用条件更新把 `PENDING_CONFIRM → EXECUTING`，双击只有一个成功。
- 下单：从 Redis 取走提交令牌，重建 `CreateOrderRequest`，调
  `OrderService.create`（库存 / 状态机 / 拆单全部复用现有逻辑）。
- 退款：解析快照里的 orderId，调 `OrderService.applyRefund`。
- 成功写 `result_json`；业务失败写 `FAILED + error_message`；过期直接 `EXPIRED`；
  卡在 EXECUTING 超过 5 分钟视为执行中断，标记 FAILED 让用户重来。
- `cancel` 只能取消 PENDING_CONFIRM；`GET /api/agent/actions?sessionId=` 返回
  该会话的动作，前端刷新后靠它恢复卡片。

**前端**：`AgentActionCard.vue` 渲染订单 / 退款两类卡片；`AgentChatPanel.vue`
在 `done` 和加载历史时拉取动作列表，确认 / 取消后就地更新；`visibilitychange`
时也会重新拉一次。卡片由 Java 数据驱动，不依赖模型在回复里写什么。

前端只展示**还需要用户关注**的卡片：待确认 / 执行中 / 失败；已取消、已过期、
已完成默认隐藏。唯一例外是下单成功但订单仍 `PENDING_PAYMENT` —— 保留卡片给
「去支付」入口，支付完成后再消失；退款成功则直接隐藏。

**卡片状态是活的**：`result_json` 只存执行时的快照；`GET /api/agent/actions`
在返回前按 `orders` 当前状态覆盖 `result.orders[].status / statusLabel`
（退款动作覆盖 `result.status`）。前端按状态改标题、只在 `PENDING_PAYMENT`
时显示「去支付」。用户支付 / 商家发货 / 退款后回到聊天，卡片不会停在旧状态。

**安全性质**：
- userId：agent 侧来自票据，浏览器侧来自 JWT；确认接口没有 userId 参数
- 参数：全部来自服务端快照，客户端只传 actionNo
- 数据：订单查询不含 PII；地址只返回当前用户自己的
- 频控：confirm / cancel 10 次/分钟/用户；总开关 `itsmygo.agent.actions.enabled`
- 审计：每次状态流转都落在 `agent_actions`

**测试**：`AgentActionControllerTest` 6 条（下单一次、越权、他人地址、过期、
退款、无票据），全量 200 条；rag_helper 回归 52/52；浏览器实测三张卡片渲染、
确认下单后生成新订单并出现「去支付」。

### 3.21 AI 导购、商品搜索与商品卡（M11–M13，2026-10-21）

**拓扑**：主 agent 新增 `product-guide` 子 agent（rag_helper），与
`product-kb`（产品手册）、`order-service`（订单 / 下单 / 退款）分工：

- 想买 / 推荐 / 预算 / 用途 → `product-guide`
- 怎么用 / 参数 → `product-kb`
- 订单 / 退款 / 已选定商品下单 → `order-service`

> 注册名是 `order-service`（原 `order-query`）；rag_helper 的 Python 包为
> `order_service_agent`，工具文件为 `order_service.py`，改名不影响对接协议。

**商品目录接口**（内部，`X-Agent-Ticket`）：

- `POST /internal/agent/catalog/products`：
  `keyword / categoryId / priceMin / priceMax / sort / limit`；
  `sort = recommend`（有货优先，销量 / 评分降序）/ `sales` / `price_asc` /
  `price_desc` / `newest`
- `GET /internal/agent/catalog/all?limit=100`：语义索引全量（在售 + ACTIVE 店铺）
- `GET /internal/agent/catalog/categories`：分类树

`AgentProductView` 带 `price / totalStock / sales / rating / reviewCount /
merchantName / categoryName / skus`；评价用 `ReviewMapper.statsByProductIds`
批量回填，避免 N+1。商品量小，推荐排序在内存里做；**不做 text2sql** ——
模型只选筛选参数，不写 SQL。

**语义检索（M13）**：rag_helper 的 `semantic_index` 拉取全量商品，用既有
DashScope embedding 生成向量，内存余弦排序；缓存 TTL 300 秒。关键词结果少于 2 条
时自动语义回退；embedding 失败只走关键词，不阻断对话。量大了再迁移 Milvus /
搜索引擎。

**商品卡（M12）**：`FinalEvent.products` / `HistoryMessage.products`（proto 加字段，
向后兼容）；前端 `AgentProductCard.vue` 渲染图片 / 价格 / 库存 / 销量 / 店铺，
「查看详情」跳 `/product/{id}`，「让买小购下单」把商品名与规格带回对话。
主 agent 收尾时按最终回复里的 `/product/{id}` 或精确商品名**裁商品卡**，
避免把中间搜索的无关候选全挂上去；历史同样持久化。

**测试**：Java 202 条（新增 `AgentCatalogToolTest` 2 条）；rag_helper 回归 **56/56**
（新增导购工具、语义索引、商品卡往返、商品卡对账）；浏览器实测语义问题
「能做名片烫金的设备，预算 3000」→ 推荐 HAK 180，商品卡与历史各 1 张。

### 3.22 虚拟物流与发货地址（M7.5/M7.6，V22/V23）

**目标**：发货后生成一张虚拟运单，物流状态按时间推进；**预计送达时间 = 最终送达时间**，
到点状态必为 `DELIVERED`，`delivered_at` 写 `eta`。快递公司 / 快递员 / 运单号都是虚拟数据。

**表 `shipments`（V22）**：`order_id` 唯一、`tracking_no`、`carrier`、`courier_name`、
`courier_phone`、`origin_name/lng/lat`、`status`、`shipped_at`、`eta`、`delivered_at`。

- 发货（`OrderServiceImpl.doShip`）后 `ShipmentService.createForOrder` 生成运单；
- 快递公司 / 快递员从固定池按 orderId 取；
- 发货仓优先用商家的发货坐标（V23）：有坐标时 `origin_name = <店铺名>发货仓`，
  没有坐标时回退固定转运中心池；
- `eta = shippedAt + itsmygo.logistics.delivery-minutes`（默认 120 分钟；
  本地演示可设 `LOGISTICS_DELIVERY_MINUTES=2` 看完整轨迹）；
- 老订单（V22 之前已发货）在首次查询物流时按 `shipped_at` 懒生成运单。

**状态推进**（读取时计算，无需定时任务）：

- 已揽收 → 运输中（25% 时长）→ 派送中（80% 时长）→ 已送达（100%）；
- 阶段用**秒**计算，避免 2 分钟演示被按分钟取整；
- `LogisticsView.progress` 0–100，给前端地图插值快递员位置；
- 退款通过后 `cancelForOrder` 把未送达的运单置为 `CANCELLED`。

**商家 / 平台发货地址（M7.6，V23）**

- `merchants` 增加 `ship_province / ship_city / ship_district / ship_address /
  ship_lng / ship_lat`；
- 商家在「商家后台 → 店铺资料 → 发货地址」设置自己的地址；平台在
  「管理后台 → 商户管理 → 发货地址」代设，**平台自营（id=1）也走同一入口**；
- 经纬度必须成对；只填地址文字也能保存，但发货仍回退虚拟转运中心；
- 公开店铺接口 `GET /api/shops/{id}` 复用 `MerchantView`，返回前把 `ship_*`
  脱敏为空 / `null`，避免把商家仓库位置公开；商家自己与平台后台可见真实值；
- `createForOrder` 读订单所属商户：有坐标 → `origin_name = <店铺名>发货仓`，
  `origin_lng/lat` 用商家坐标；无坐标 → 原固定池（杭州 / 上海 / 深圳，按 orderId 取）；
- 已有运单不回填历史起点 —— 地址只影响**之后**新生成的运单。

**接口**：`GET /api/orders/{id}/logistics`（登录 + 订单归属；未发货返回 `data:null`）；
`PUT /api/merchant/ship-address`（商家自己）；`PUT /api/admin/merchants/{id}/ship-address`
（平台代设，含平台自营）。
`LogisticsView` 含运单 / 快递员 / 状态 / eta / 进度 / 发货仓坐标 / 收货地址 / 四段 timeline。
收货地址只给订单详情页画地图，**不进入 agent 上下文**。

**agent**：`AgentOrderView` 增加 `logistics` 摘要（status / statusLabel / eta / carrier /
courierName / progress），Python order-service 工具在订单文本里输出
「物流：运输中，预计 … 送达（买够速运 / 王师傅）」。

**前端**：订单详情页新增「物流状态」区块：

- `LogisticsPanel.vue`：状态、ETA、快递员、运单号、四段时间线；
- `LogisticsMap.vue`：腾讯地图 JS API GL（`TMap.Map` / `MultiPolyline` / `MultiMarker`）
  画发货仓→收货地址的平滑曲线，按 `progress` 插值快递员位置；
  浏览器端用 `TMap.service.Geocoder` 解析收货地址，失败时按城市中心兜底；
- `utils/tencentMap.ts`：物流地图与发货地址弹窗共用的加载器 / 地理编码；
  **加载脚本必须带 `libraries=service`**，否则 `TMap.service` 不存在，
  Geocoder 会报「不可用」；
- `ShipAddressDialog.vue`：省市区级联 + 详细地址 + 「解析坐标」；
  商家资料页与管理端商户列表共用，平台自营行同样有入口；
  修改地址后未重新解析时，保存会自动清空旧坐标，避免物流起点仍指向旧地址；
- 腾讯 Key 放仓库根目录 `.env` 的 `VITE_TMAP_KEY`；Vite 通过 `envDir` 读取根目录，
  只暴露 `VITE_` 前缀；未配置时显示配置提示，时间线仍可用；
- 发货后每 30 秒刷新一次，送达 / 取消后停止轮询。

**验证**：Java 207 条全绿（新增 `OrderLogisticsControllerTest` 2 条：
平台自营地址成为物流起点、商家自设 / 清空地址与顾客越权）；
浏览器实测管理员平台自营与商家资料页两个入口：保存、回显、未定位提示、
已定位坐标展示；未改地址直接保存时坐标保留。
验证当天腾讯 Key 的 Geocoder 日报额度用尽（status 121），
因此坐标解析走的是失败提示 + 虚拟仓兜底；「有坐标」的起点分支由后端测试覆盖。

**边界**：这是**确定性模拟**，不接承运商 API；真实项目要接快递100 / 快递鸟回调，
并用承运商轨迹覆盖模拟状态。

### 3.23 催发货动作（M8，2026-10-22）

**目标**：用户在聊天里说「帮我催一下发货」，order-service 先校验订单处于待发货
（`PAID`，已支付未发货），生成 `URGE_SHIPMENT` 待确认草稿；用户确认后 Java 触发
agent 执行图，**由 agent 调 Java 发送工具**，Java 校验并落库为 AGENT 消息；
agent 再回复用户「已完成 + 代商家致歉」。

- `AgentActionType.URGE_SHIPMENT`；`prepareUrgeShipment` 只落 `PENDING_CONFIRM`，
  业务参数（订单 id）来自服务端快照。
- 仅 `PAID`（待发货）且 `shipped_at IS NULL` 可催；`PENDING_PAYMENT` / `SHIPPED`（待收货）/
  `COMPLETED` / `REFUNDING` / `REFUNDED` / `CANCELLED` 一律拒绝并给可读原因。
- 幂等与防打扰：同一订单已有未过期草稿时复用；成功发送后 10 分钟内再次催同一单返回 400；
  双击确认由 `startExecution` CAS 保证只进入一次执行态。
- **确认只 CAS 到 EXECUTING，不直接发消息**：`confirmUrgeShipment` 签发短期 tool ticket，
  通过 gRPC metadata `x-agent-internal-event: urge-shipment-confirmed` 触发 agent；
  最多等 20 秒，再返回动作最终状态。
- **agent 执行图** `shipment_graph.py`（LangGraph，`nodes/` + `state.py` + `tools/`）：
  1. `send_merchant_message` 调 `POST /internal/agent/actions/urge-shipment/send`；
  2. Java 用 ticket 解析 userId/session，校验动作归属、`EXECUTING` 状态、订单仍是待发货；
  3. `ChatService.sendAgentText` 写 `AGENT / TEXT` 消息（商家未读 +1、顾客不加未读）并推 SSE；
  4. Redis `its-my-go:agent:urge-send:{actionNo}`（30 秒 TTL）防并发，
     `finishExecution` CAS 保证只成功一次；
  5. 图再 `compose_reply → save_reply`，把完成 / 失败 + 代商家致歉写为 assistant 历史。
- 失败路径：发送工具返回错误时动作 FAILED，图仍写一条失败 + 致歉回复；
  前端对 `SUCCEEDED` / `FAILED` 都刷新历史，失败卡片保留。
- 无数据库迁移：`agent_actions.type` 是 VARCHAR，新增枚举值即可。
- 前端 `AgentActionCard.vue` 增加第三种卡片：标题 / 图标 / 「确认催发货」按钮；
  成功后卡片默认隐藏（与退款一致），失败卡片保留。
- rag_helper：`order-service` 的 `prepare_urge_shipment` 工具；提示词要求先
  `query_orders` 确认状态，草稿不等于已发送。确认后的发送由 agent 图执行，不由主模型自由发挥。

**验证**：Java 210 条全绿（`AgentUrgeShipmentControllerTest` 3 条：agent 回调发送工具、
冷却 / 幂等、状态与越权）；rag_helper `_check_urge_shipment_graph` PASS；
前端 `npm run build` 通过。浏览器实测订单 770：确认后 agent 收到内部事件并调用发送工具，
商家会话消息 `sender_type=AGENT`，agent 聊天随即出现「催发货已完成 + 代商家致歉」，
动作 `SUCCEEDED`，卡片按预期隐藏。

### 3.24 管理后台数据概览（M15，2026-10-22）

**目标**：管理后台默认落地页，把「今天该处理什么」集中展示，不再一进来就是分类树。

- `GET /api/admin/dashboard`，只读聚合，不参与业务状态流转。
- **今日成交额**：`paid_at` 在当天、状态为 `PAID / SHIPPED / COMPLETED / REFUNDING`
  的订单金额之和；已退款 / 已取消不计。今日订单按 `created_at`，今日支付按 `paid_at`。
- **待办**：待发货（`PAID`）、待退款（`REFUNDING`）、待审核商户（`PENDING`）、
  低库存 SKU（在售商品 + 在售规格，`stock <= 10`）。
- **近 7 天成交**：按 `DATE(paid_at)` 聚合金额与订单数，Java 侧补齐没有成交的日期。
- 待办列表各取最新 5 条；订单列表复用 `OrderService.pageForAdmin`，店铺名不 N+1。
- 聚合 SQL 在 `AdminDashboardMapper`；无数据库迁移。
- 前端 `/admin/dashboard` 是后台默认页，菜单第一项「数据概览」：
  指标卡、7 天 SVG 折线图（悬停显示日期 / 成交额 / 订单数）、
  待发货/待退款/待审核商户/低库存四块待办；
  点击卡片跳订单 / 商户 / 商品管理。

**验证**：`AdminDashboardControllerTest` 1 条（指标、7 天趋势、待发货/待退款、低库存）；
Java 全量 211 条；前端 `npm run build` 通过；浏览器实测 `/admin` 默认进入看板。

### 3.25 订单筛选、CSV 导出与退款记录（M16，2026-10-22）

- `GET /api/admin/orders` 增加 `keyword` 与 `dateFrom` / `dateTo`：
  - `keyword` 同时匹配**订单号 / 收货人 / 电话 / 运单号**（`LIKE %keyword%`）；
  - 日期按**下单时间**过滤，`dateFrom` / `dateTo` 是 `LocalDate`，SQL 用
    `created_at >= dateFrom 00:00` 与 `created_at < dateTo + 1 天`，含首尾两天；
  - 与 `status` / `merchantId` 可任意组合，查询与导出共用同一套条件。
- `GET /api/admin/orders/export`：同步写出 CSV，响应 `text/csv;charset=UTF-8`，
  第一行状态用中文标签。三个必踩的点：
  1. **写 UTF-8 BOM**（`writer.write(0xFEFF)`），否则 Excel 打开中文乱码；
  2. 字段按 RFC 4180 转义（含逗号 / 引号 / 换行时整体加引号，内部引号翻倍）；
  3. 最多 5000 行（`listForAdminExport` 里 `LIMIT 5000`），避免一次导出拖垮内存。
- 前端 `http.ts` 增加 `download()`：`responseType: 'blob'`，用 blob URL 创建临时 `<a>`
  触发下载（文件名由调用方给，导出固定 `orders.csv`）；`Orders.vue` 增加关键词、
  下单日期区间、查询 / 重置 / 导出 CSV。

**验证**：`OrderControllerTest` 30 条（含管理端筛选 + CSV 导出）；Java 全量 212 条全绿；
接口实测 `/api/admin/orders?keyword=...&dateFrom=...&dateTo=...` 与
`/api/admin/orders/export?...`（返回带 BOM 的 CSV）；浏览器实测筛选、导出与
退款订单详情抽屉。

### 3.26 RabbitMQ 延迟消息与超时关单（M17，2026-10-23）

**为什么用 RabbitMQ**：项目是单体，只需要一个轻量的延迟关单能力；RabbitMQ
一个容器即可（带管理台），Spring AMQP 也是 Boot 官方 starter，不需要额外的
客户端兼容层。延迟用 **TTL + 死信队列**，不依赖 delayed-message-exchange 插件。

```
下单事务提交后
  └─ OrderTimeoutPublisher (afterCommit)
       └─ RabbitTemplate ──> its-my-go-order-timeout-exchange
                                │ RK: order-timeout-delay
                                ▼
                      延迟队列（x-message-ttl = minutes*60000）
                                │ 到期后 DLX / DL RK
                                ▼
                          工作队列
                                │
                         @RabbitListener
                                ▼
                   OrderService.cancelIfExpired(orderId, minutes)
                   ├─ id + PENDING_PAYMENT + created_at < NOW()-N
                   ├─ CAS 转 CANCELLED
                   └─ restoreStock / restoreCoupon

兜底：OrderTimeoutScanJob（默认每 5 分钟）
  └─ Redis 锁 → cancelExpired(minutes)
```

要点：

- **拓扑**：DirectExchange `its-my-go-order-timeout-exchange`；延迟队列
  `its-my-go-order-timeout-delay-queue`（RK `order-timeout-delay`）；工作队列
  `its-my-go-order-timeout-work-queue`（RK `order-timeout-work`）。延迟队列参数
  `x-message-ttl`、`x-dead-letter-exchange`、`x-dead-letter-routing-key`。
- **延迟一致性**：队列级 TTL 对队列里所有消息一致，先进先过期；本项目所有订单
  共用同一个超时分钟数，因此不会出现逐消息 TTL 的队头阻塞。
- **发送时机**：`doCreate` 收集本次创建的订单，调用 `OrderTimeoutPublisher.publishAfterCommit`；
  有事务同步时挂 `afterCommit`，没有则立即发送。`RabbitMqOrderTimeoutSender`
  发到延迟队列，`messageId=orderNo`；发送异常只 warn，下单不受影响。
- **消费幂等**：`cancelIfExpired` 先用数据库 `NOW()` 查“仍未支付且已超时”，
  再走现有 `transition` CAS；已支付 / 已取消 / 未到时间 / 重复消息都正常 ack。
  查询用 `created_at <= NOW() - N 分钟` —— MQ 的 TTL 到期时刻可能正好等于阈值，
  用 `<` 会因几毫秒误差漏关。数据库等临时异常抛
  `AmqpRejectAndDontRequeueException`，不无限重投，由扫描兜底。
- **兜底扫描**：`@Scheduled(fixedDelay)` + Redis `SETNX` 锁
  `its-my-go:order-timeout:scan-lock`（TTL 240s，Lua 比对 token 释放）；
  多实例只跑一个，Redis 故障时跳过本轮。测试 profile 关闭扫描。
- **开关**：`ORDER_TIMEOUT_MQ_ENABLED`、`ORDER_TIMEOUT_SCAN_ENABLED`；
  MQ 关闭时 `@ConditionalOnProperty` 不装配拓扑 / 监听器，应用照常启动。
- **队列参数变更**：修改 `ORDER_TIMEOUT_MINUTES` 后，已存在的延迟队列
  `x-message-ttl` 不会自动更新，声明会报 `PRECONDITION_FAILED`；需要先删除
  延迟队列再重启（命令见 `start.txt`）。
- **无数据库迁移**；取消动作完全复用现有库存 / 券回滚。

**验证**：新增 RabbitMQ sender 2 条、consumer 4 条、publisher 3 条、scan 3 条，
加上 `cancelIfExpired` 幂等 1 条；真实环境下单后 60 秒订单 `CANCELLED`，
日志出现 `RabbitMQ 定时关单成功`；关掉扫描、仅靠 MQ 也能关单；
管理端手动 `cancel-expired` 仍能清理存量。

### 3.27 催退款动作（M8 二期，2026-10-23）

**范围**：只对 `REFUNDING`（退款中）订单，用户让买小购提醒商家尽快处理退款。
交互与催发货一致：agent 只 prepare 草稿，用户确认后 Java 触发 agent 执行图，
agent 调 Java 发送工具，Java 重新校验后以 `senderType=AGENT` 落站内消息。

```
用户「催退款，订单 X」
  → prepare_urge_refund（PENDING_CONFIRM，summary 含退款原因/申请时间）
  → 用户确认
  → Java gRPC 内部事件 urge-refund-confirmed
  → agent refund_graph: send_refund_message → compose_reply → save_reply
  → POST /internal/agent/actions/urge-refund/send
  → Java 校验订单仍 REFUNDING → ChatService.sendAgentText → 动作 SUCCEEDED
```

**Java**：

- `AgentActionType.URGE_REFUND`；新增 `AgentUrgeRefundRequest/ExecuteRequest`。
- `AgentActionServiceImpl` 把催发货 / 催退款差异抽成私有 `UrgeKind`：
  动作类型、内部事件、Redis 锁前缀、中文名；`confirmUrgeAction` /
  `executeUrgeAction` / `sendUrgeMessage` 共用，原 `executeUrgeShipment` 公开签名不变。
- `prepareUrgeRefund`：仅 `REFUNDING`；已 `REFUNDED` 提示「商家已处理退款，无需再催」；
  未过期草稿复用；成功后 10 分钟冷却；summary 带 `refundReason/refundAppliedAt`。
- `executeUrgeRefund`：类型 / session / EXECUTING 校验，Redis
  `its-my-go:agent:urge-refund-send:{actionNo}` 锁，发送成功后 `finishExecution` CAS。
- `sendUrgeMessage` 退款分支重新查订单：`REFUNDED` 或非 `REFUNDING` 都拒绝，
  不写商家消息；消息内容含订单号、退款原因、申请时间。
- `AgentToolController`：`/internal/agent/actions/urge-refund`（prepare）与
  `/urge-refund/send`（agent 回调），均要求 `X-Agent-Ticket`。
- 列表刷新：`collectOrderIds` / `enrichResult` 把 `URGE_REFUND` 与催发货同等对待，
  商家处理完成后退款卡片能显示当前状态。

**agent（rag_helper）**：

- `order_actions.prepare_urge_refund` 调 Java；返回「草稿尚未发送」口径。
- `refund_graph.py`：`node_send_refund_message` → `node_compose_refund_reply` →
  复用 `node_save_reply`；成功「催退款已完成 + 代商家致歉」，失败「没有成功 + 致歉」。
- `main_agent._run_internal_event` 改为事件映射，支持
  `urge-shipment-confirmed` / `urge-refund-confirmed`。
- order-service 工具增至 6 个；提示词明确「仅退款中可催退款，退款中不要催发货」。

**前端**：`AgentActionType` 增加 `URGE_REFUND`；新增 summary/result 类型；
`AgentActionCard.vue` 显示退款原因 / 申请时间 / 商品件数，按钮「确认催退款」；
`AgentChatPanel.vue` 对两种催办的成功 / 失败都刷新历史。

**验证**：`AgentUrgeRefundControllerTest` 3 条（成功、非法状态/越权、确认前已退款）；
原 `AgentUrgeShipmentControllerTest` 3 条保持全绿；rag_helper
`_check_urge_refund_graph` 与 `_check_order_actions_tools` 通过；前端构建通过。

### 3.28 日志与 traceId（2026-10-24）

**目标**：请求日志可关联、生产日志可采集，同时避免把 PII 写进日志。

- **门面 / 实现**：SLF4J + Logback；`logback-spring.xml` 控制台模式带
  `traceId / userId`，`prod` profile 额外输出 JSON 文件
  `logs/its-my-go.log`（logstash encoder，按天 + 100MB 滚动，保留 14 天）。
- **traceId**：`common/logging/RequestIdFilter`（`@Order(LOWEST_PRECEDENCE)`，
  在安全过滤器链之后）读取 `X-Trace-Id`（校验字符集），没有则生成 UUID；
  写入 MDC 和响应头，每个请求结束打一条
  `HTTP METHOD path -> status (ms)` 访问日志。登录用户从 `SecurityContext`
  取 `CurrentUser.id` 写 `userId`。
- **跨异步链路**：
  - RabbitMQ 发送端把当前 MDC `traceId` 放进消息头；
  - 消费端从消息头恢复 MDC，没有则生成 `mq-*`，处理完清理；
  - 定时扫描生成 `scan-*` traceId，便于区分定时任务日志。
- **生产级别**：`application-prod.yml` 把 root / 业务包 / MyBatis mapper 都设为
  `INFO`，避免 SQL 参数（手机号、地址）进入日志；启动用
  `SPRING_PROFILES_ACTIVE=prod`。
- **边界**：Java 单体日志已统一；agent（rag_helper）仍使用它自己的 Python 日志，
  Java 只通过 gRPC 调用，不保证跨进程 trace 串联；管理端审计日志仍未建表，
  不应拿运行日志代替审计。

**验证**：prod 启动后 `/api/health` 响应带 `X-Trace-Id`，JSON 日志包含
`traceId` 和 `application` 字段；登录后访问 `/api/addresses` 的日志带
`userId=3362`；prod 日志中没有 MyBatis SQL 明细；开发 profile 控制台日志带
`[trace=...] [user=...]`。

### 3.29 用户管理与操作审计（M18，2026-10-24）

**用户管理**：
- `GET /api/admin/users` 支持 keyword（用户名 / 昵称 / 手机号）、role、status、
  page/size；查询显式排除 `password`，映射为 `AdminUserView`。
- `POST /api/admin/users/{id}/disable|enable`：不能禁用自己；禁用活跃 ADMIN 时
  `UserMapper.selectActiveAdminIdsForUpdate()` 用 `FOR UPDATE` 锁活跃管理员，
  数量 ≤ 1 拒绝；已禁用/已启用幂等。
- 更新后立即 `AuthIdentityService.evict(id)`，并在事务 afterCommit 再 evict 一次；
  旧 JWT 下一次请求因 `load()` 返回 null 得 401。

**审计表（V25）**：`admin_audit_logs` 只追加，字段
`actor_user_id/actor_username/action/target_type/target_id/summary/result/
error_message/ip/user_agent/trace_id/detail/created_at`；`users` 增加
`idx_user_role_status`。`GET /api/admin/audit-logs` 只读分页筛选。

**审计切面**：
- `@AdminAudit(action, label, targetType)` 标在 Controller 写方法上；
- `AdminAuditAspect` 切所有 `Admin*Controller`，GET/HEAD/OPTIONS/TRACE 放行；
  有注解用注解，无注解写方法用 `HTTP方法 + URI` 兜底；
- 成功且 `Result.code == 200` → SUCCESS；业务 code 非 200 或抛异常 → FAILED，
  异常仍原样抛出，由 GlobalExceptionHandler 处理；
- 记录 CurrentUser、IP、UA、MDC `traceId`、URI；detail 不含请求体；
- 审计写入异常只打运行日志，不影响业务；`@Valid` 失败、401/403 不进入审计。

**验证**：`AdminUserControllerTest` 4 条（列表筛选、禁用后旧 token 401、自禁用、
最后管理员保护）；`AdminAuditControllerTest` 2 条（成功/失败写审计、GET 不审计）；
浏览器实测用户页与审计页，审计记录带操作人、目标、结果、IP 与 traceId。

---

## 4. 前端（`frontend/`）

### 4.1 工程与启动

```
frontend/
├── vite.config.ts          # envDir 指向仓库根目录（前后端共用 .env，只暴露 VITE_ 前缀）；/api 代理到 8080（`API_TARGET` 可覆盖）；端口读 `PORT` 环境变量（预览工具靠它挑空闲端口）
├── src/
│   ├── api/
│   │   ├── http.ts         # axios 封装 + 拦截器 + **登录凭据的存取**（token 的唯一归属地）
│   │   ├── types.ts        # 与后端 DTO 一一对应的类型（手写）
│   │   ├── product.ts      # 商品 / 分类 / 实时库存
│   │   ├── cart.ts         # 购物车
│   │   ├── auth.ts         # 登录 / 注册
│   │   ├── address.ts      # 收货地址
│   │   ├── order.ts        # 下单 / 支付 / 我的订单
│   │   ├── user.ts         # 个人资料 / 改密码 / 头像
│   │   ├── favorite.ts     # 收藏
│   │   ├── footprint.ts    # 浏览足迹
│   │   ├── review.ts       # 评价（提交 / 我的 / 商品公开评价）
│   │   ├── coupon.ts       # 领券中心 / 我的券 / 可用券
│   │   ├── shop.ts         # 公开店铺信息
│   │   ├── merchant.ts     # 商家后台：资料 / 商品 / 订单 / 店铺券 / 评价
│   │   ├── chat.ts         # 聊天：会话 / 消息 / SSE 票据 / 图片上传
│   │   ├── agent.ts        # 买小购：POST SSE 流 / 停止 / 历史
│   │   └── admin.ts        # 管理端：分类 / 商户 / 商品 / SKU / 上传 / 订单 / 券 / 评价
│   ├── stores/user.ts      # 登录态（localStorage 的响应式包装）
│   ├── stores/chat.ts      # 未读角标 + EventSource 生命周期（登录连接、登出断开）
│   ├── utils/orderStatus.ts # 订单状态中文名与标签色（管理端列表只有原始 status）
│   ├── components/
│   │   ├── ProductImage.vue      # 商品图 + 无图占位（列表 / 详情 / 订单共用）
│   │   ├── ImageUpload.vue       # 图片上传（admin / merchant / 入驻申请三种接口）
│   │   ├── AddressFormDialog.vue # 收货地址表单弹窗（结算页与用户中心共用）
│   │   └── chat/                 # 会话列表 / 聊天窗口 / 消息气泡 / 输入框
│   ├── styles/theme.css    # 色板 + Element Plus 主题覆盖
│   ├── router/index.ts
│   ├── views/
│   │   ├── ProductList.vue     # 首页：品类栏 + 轮播 + 品牌专区 + 商品网格（滚动加载）
│   │   ├── ProductDetail.vue   # 商品详情（含规格选择栏）
│   │   ├── Login.vue           # 登录 / 注册（同一个页签切换）
│   │   ├── Cart.vue            # 购物车：按店分组、勾选 / 改数量 / 移除 / 去结算
│   │   ├── Checkout.vue        # 确认订单：按店分组、每店选券、两种商品来源
│   │   ├── CheckoutResult.vue  # 下单结果：多个子订单分别支付
│   │   ├── OrderDetail.vue     # 订单详情 + 支付 + 评价
│   │   ├── Shop.vue            # 店铺主页（店铺信息 + 商品）
│   │   ├── CouponCenter.vue    # 领券中心（匿名可浏览）
│   │   ├── user/               # 用户中心（左侧菜单 + 子路由）
│   │   │   ├── UserCenter.vue  # 外壳：资料卡 + 菜单 + <router-view>
│   │   │   ├── Orders.vue      # 我的订单（按状态 Tab + 评价入口）
│   │   │   ├── Messages.vue    # 我的消息（顾客 ↔ 商家会话）
│   │   │   ├── Favorites.vue   # 我的收藏
│   │   │   ├── Footprints.vue  # 浏览足迹
│   │   │   ├── Coupons.vue     # 我的优惠券
│   │   │   ├── Reviews.vue     # 我的评价
│   │   │   ├── Addresses.vue   # 收货地址管理
│   │   │   ├── Profile.vue     # 个人资料（昵称 / 手机 / 头像）
│   │   │   ├── Password.vue    # 修改密码
│   │   │   └── components/ReviewDialog.vue # 发表评价弹窗
│   │   ├── admin/              # 平台管理端（独立外壳，不共用顾客顶栏）
│   │       ├── AdminLayout.vue # 侧边菜单 + 页头 + <router-view>
│   │       ├── Merchants.vue   # 商户审核 / 禁用 / 启用
│   │       ├── Categories.vue  # 分类树 CRUD
│   │       ├── Products.vue    # 商品列表 / 店铺筛选 / 上下架 / 传图
│   │       ├── Orders.vue      # 全平台订单 / 店铺筛选 / 发货 / 退款审核
│   │       ├── Coupons.vue     # 平台券 + 店铺券模板
│   │       ├── Reviews.vue     # 全平台评价显示 / 隐藏
│   │       └── components/     # 商品表单、SKU 编辑器、库存调整、订单抽屉
│   │   └── merchant/           # 商家后台（独立外壳）
│   │       ├── MerchantLayout.vue # 商家侧边菜单 + 页头
│   │       ├── Apply.vue       # 入驻申请 / 审核状态 / 驳回后重提
│   │       ├── Messages.vue    # 本店会话 / 回复 / 未读
│   │       ├── Products.vue    # 本店商品 / SKU / 库存 / 上下架
│   │       ├── Orders.vue      # 本店订单 / 发货 / 退款审核
│   │       ├── Coupons.vue     # 本店店铺券
│   │       ├── Reviews.vue     # 本店评价显示 / 隐藏
│   │       └── Profile.vue     # 店铺资料
│   ├── App.vue             # 顶栏 + 内容区（管理端路由下隐藏顾客顶栏）
│   └── main.ts
└── .gitignore
```

启动（后端要同时跑着）：`npm run dev` → http://localhost:5173

### 4.2 配色：浅粉做面 + 深粉做点

定稿方案见 [color-preview.html](color-preview.html)（三个方案的 A/B/C 对比）。

```
--c-pink:       #e8638f   点：价格、主按钮、选中态
--c-pink-soft:  #ffe4ef   浅：标签底、选中背景
--c-pink-tint:  #fff5f9   更浅：区块背景、商品图占位
--c-text:       #2d2d2d   近黑 —— 粉色页面很"轻"，得靠它压住
```

**规矩：粉色只出现在「价格 / 主按钮 / 选中态」三个位置**，其余一律中性色
（卡片边框、分类导航、销量文字全是灰）。粉色一多就腻，一腻就廉价。

另外两个让它不显廉价的技术点：

- **圆角要大**（卡片 16px、按钮 24px）—— 小圆角 + 糖果粉 = 廉价，大圆角就是另一个气质
- **阴影带粉色不灰**：`rgba(232,99,143,.14)`。灰阴影配粉背景会显脏

价格用 **`ui-monospace` 等宽字体** —— 一列 `¥259.00` / `¥2680.00` 的小数点会垂直对齐，
成本几乎为零，质感差别很大。

### 4.3 与后端对接的四个约定

**① 响应拦截器按 body 的 `code` 判断，不能只看 HTTP 状态码**

后端的 HTTP 状态码**不总是语义化的**：401/403 是真状态码（Spring Security 的 entry point
手动 `setStatus` 的），而 400/404/429 只体现在 body 的 code 里、HTTP 仍是 200。
`http.ts` 里因此统一剥掉外层 `Result` 包装，业务失败走 `result.code !== 200` 分支。

**② 库存走独立接口**

`GET /api/products/{id}/stock`，不要用商品详情里的 `stock` 字段
（详情虽然也会替换成实时值，但独立接口语义更清楚，且不用为一个数字拉整个详情）。

**③ 下单要先领提交令牌**

进结算页 → `GET /api/orders/token` → 提交时带上。不传会被拒（见 §3.7）。

**④ 401 会**自动**清登录态**

`http.ts` 收到 401（token 过期 / 无效）时清掉 localStorage 里的凭据，
并广播 `its-my-go:unauthorized`；`stores/user.ts` 监听后同步内存状态。
**不这么做的话，用户会卡在「顶栏显示已登录、但每个操作都报错」的状态里**。

### 4.4 无图占位

商品 `mainImage` 为空时，用**按 id 取的粉色渐变块 + 商品名首字**占位，
而不是灰底"暂无图片"。这样既不会让卡片塌掉，也能看出"图占大头"的版式效果。

实现在 `components/ProductImage.vue`，列表页和详情页共用；
**首字字号由外层容器给**（卡片 40px、详情页 96px），组件自己不写死。

### 4.5 登录态存在哪

- 凭据放 localStorage 两个 key：`its-my-go:token`（JWT）、`its-my-go:user`（身份信息 JSON）
- **`api/http.ts` 是这两个 key 的唯一归属地** ——
  `getToken` / `setToken` / `getStoredUser` / `setStoredUser` / `clearAuth` 都在那里，
  别在组件里直接 `localStorage.getItem`
- `stores/user.ts` 只是它的**响应式包装**，页面里统一用
  `userStore.isLoggedIn` / `displayName` / `isAdmin`，判断登录**不要**去读 token 有没有值
- 刷新页面直接从 localStorage 恢复，不必再打一次 `/auth/me`
- 需要登录的操作（加购…）在未登录时提示一句，并带 `redirect` 跳登录页，
  **登录完回到原处**（`/login?redirect=/product/3`）

### 4.6 「立即购买」这一串怎么走的

```
详情页「立即购买」
   → /checkout?productId=X&quantity=N    结算页：选地址 + 商品清单 + 提交
   → /order/:id                          订单详情：能看到状态，待支付时直接付款
```

- **商品通过 URL 参数传，不塞 Pinia** —— 刷新页面不丢，链接也能直接发给别人
- 结算页**进页面就领提交令牌**，提交时复用同一个。放在提交时才领的话，
  连点两次会各领一个新令牌，防重复提交就形同虚设了
- 结算页提交时带上 `items`，后端据此走「立即购买」分支，**购物车毫发无损**
  （见 §3.7）。用户如果是加购后再结算，`items` 留空即可，前端不用另做一套

---

### 4.7 规格选择栏

详情页把 `product.specs` 渲染成按钮组，选中的组合**按规范化后的 `specValueIds` 匹配**到
对应的 SKU —— 规则必须和后端生成这个键时一致（都是按数值升序拼），
差一点就匹配不上，表现为「选了规格但价格不动」。匹配上就把价格、库存、图片换成那一款的。

- **无规格商品只有一条默认 SKU**（`specValueIds` 是空串），组件直接用它 ——
  这种商品的交互和没有这套东西时一模一样
- 规格没选全时，价格显示商品最低价、库存显示汇总，主按钮文案变成「请选择规格」
- 列表页对多规格商品的按钮是「**选规格**」（点进详情挑）—— 列表接口不带 SKU，
  否则分页查询要 join SKU 变重；单规格商品则直接加购（加购前拉一次详情拿默认 SKU）

### 4.8 购物车与两种结算来源

**购物车页**（`views/Cart.vue`）：勾选 / 改数量 / 移除 / 去结算。

- 后端每个写操作（改数量、改勾选、删除）都返回**整份 `CartView`**，所以前端直接换掉本地状态，
  不用再查一遍
- 失效行（下架 / 库存不够 / 规格已删）**照常显示但选不了**，并在行里写明原因 ——
  静默隐藏比看得见的异常更让人困惑
- 金额只算「勾选 且 可买」的行，和返参里的 `selectedTotal` 保持一致
- 改数量走的还是同一套后端校验（≤ 库存、≤ 999），前端 `el-input-number` 的 `max` 只是提前拦一下

**结算页的两种来源**（`Checkout.vue`）用 URL 区分，不需要额外状态：

| 入口 | URL | 提交时传什么 |
|---|---|---|
| 立即购买 | `/checkout?productId=X&skuId=Y&quantity=N` | `items: [{productId, skuId, quantity}]` |
| 购物车结算 | `/checkout` | **不传 `items`** —— 后端按购物车勾选行下单并清空它们 |

购物车结算时，页面上的清单是 `GET /api/cart` 里**勾选且可买**的那几行，
只用于展示；真正决定买什么的是提交那一刻购物车里的勾选状态。

### 4.9 首页的两个细节

**品牌专区**（首屏右侧 240px 竖栏）：品牌数据**暂时硬编码**在 `ProductList.vue` 里，
点进去就是 `/?keyword=品牌名`，复用列表页的关键词搜索。要让商户自己维护品牌，
得建 `brands` 表 + 商品关联 + 管理端接口，那时把数组换成接口调用即可。

**商品列表是滚动加载**，没有分页器：滚到底部自动拉下一页并追加。
用 `IntersectionObserver` 盯一个页面底部的哨兵元素（`rootMargin: 200px` 提前触发），
而不是 Element Plus 的 `v-infinite-scroll` —— 后者要求滚动发生在绑定元素内部，
而我们是整页滚动。「还有没有下一页」用**已拿到数量 vs 总数**判断，不靠页码算。

> 筛选条件（分类、关键词）**只存在 URL 的 query 里**，组件不另存一份 ——
> 刷新能保持、链接能分享，也避免两处状态打架。

### 4.10 用户中心

`/user` 是嵌套路由，外壳 `UserCenter.vue` 负责左侧资料卡 + 菜单 + `<router-view>`：

| 子页 | 要点 |
|---|---|
| `orders` | 七个 Tab 本质是**同一个订单列表按状态筛**；待发货 / 待收货分别对应 `PAID` / `SHIPPED`；Tab 存在 `?tab=` 里（刷新保持）；已完成订单的明细行有「评价 / 已评价」 |
| 订单详情 | 按状态显示操作：待支付「立即支付 / 取消订单」、待发货「取消订单 / 申请退款」、待收货「确认收货 / 申请退款」、已完成「申请退款」；**申请退款必须填写原因**（textarea，最多 200 字），提交后页面多出「退款信息」块（原因 / 申请时间 / 处理时间）；取消/退款会回滚库存并退回未过期券 |
| `favorites` | 收藏卡片列表、取消收藏、分页；下架商品标「已下架」且不可点进详情 |
| `footprints` | 按浏览时间倒序、分页、清空全部；同一商品只有一条 |
| `coupons` | 未使用 / 已使用 / 已过期三个 Tab；「去领券中心」入口 |
| `reviews` | 我的评价列表，显示商品、星级、内容与「展示中 / 已隐藏」 |
| `addresses` | 列表 + 新增 / 编辑 / 删除 / 设默认；表单来自公共组件 `AddressFormDialog` |
| `profile` | 昵称、手机号、头像上传 |
| `password` | 旧 / 新 / 确认，成功后 `logout()` + 跳登录 |

- **路由守卫**：`meta.requiresAuth` + `beforeEach` 直接读 `getToken()`，不依赖 Pinia 安装时机；
  未登录跳 `/login?redirect=...`，登录后自动回来
- 订单 Tab 的状态映射：待付款 `PENDING_PAYMENT`；待发货 `PAID`；待收货 `SHIPPED`；
  买过 `PAID,SHIPPED,COMPLETED,REFUNDING,REFUNDED`；退款售后 `REFUNDING,REFUNDED`；
  **待评价 `COMPLETED`**（评价系统还没有，页面上用 `el-alert` 明说）
- **地址弹窗抽成了公共组件**（`components/AddressFormDialog.vue`），结算页和地址管理页共用 ——
  各写一份校验规则必然漂移
- **省 / 市 / 区是级联的可筛选下拉**（`el-select filterable`）：选中省后市的选项跟着换，选中市后再换区县；
  可以直接输入过滤（如输「广东」只剩「广东省」）。数据在 `src/data/china-area.json`
  （来源 `modood/Administrative-divisions-of-China`，31 个省级，已压成 `{省:{市:[区县]}}`，约 47KB）。
  编辑老数据时若城市/区县不在数据集里（比如直辖市的「北京市」在数据里是「市辖区」），
  会把原值保留成一个选项，不会被悄悄清空
- **头像上传**用 `el-upload` 的 `:http-request` 自定义上传：默认的 `action` 会绕过 axios、
  带不上 JWT；`api/http.ts` 的 `postForm` **不手写 Content-Type**（否则丢 boundary）
- 资料改动后调 `stores/user.ts` 的 `patchProfile` 同步内存与 localStorage，顶栏昵称立刻更新
- **改密码失败时留在页面上**（后端返回 400），成功才登出 —— 输错旧密码不该被踢下线

**顶栏用户区**（`App.vue`）：登录后是「圆形头像 + 昵称」，鼠标移上去**向下滑出一个抽屉面板**
（我的订单 / 我的收藏 / 我的优惠券 / 收货地址 / 个人资料 / 修改密码 / 退出登录），移开即收起。
**领券中心是公共入口，不放用户面板**：它放在顶栏主导航（「领券中心 / 购物车」），匿名可见。
头像来自 `userStore.user.avatar`（登录响应里就带），空值回落到首字渐变占位。
面板用 `opacity + translateY + visibility` 过渡，收起时 `pointer-events: none`；
**面板顶部留了 8px 内边距**，让鼠标从头像滑到面板时不会跨出元素、把面板误收掉。
窄屏（≤720px）把昵称收掉，只留头像。

### 4.11 管理端（`views/admin/`）

管理端是**同一套前端工程里的独立外壳**：`/admin` 嵌套路由 + `AdminLayout.vue`
（左侧菜单 + 页头 + `<router-view>`）。管理端不共用顾客顶栏 —— `App.vue` 按路由判断，
`/admin` 下隐藏顶栏、内容区也不套 `.page` 的最大宽度。

**访问控制**：

- 父路由标 `meta.requiresAdmin`；守卫里先看未登录（跳 `/login?redirect=`），
  再看 `getStoredUser()?.role !== 'ADMIN'`（提示无权限并回首页）
- 守卫继续**直接读 localStorage**，不用 Pinia（可能早于应用挂载触发）
- 管理员只能直接改库提权：`UPDATE users SET role='ADMIN' WHERE username='testuser';`
  **提权后必须重新登录** —— JWT 里的 role 是登录时签进去的
- 顾客用户抽屉里仅在 `userStore.isAdmin` 时显示「管理后台」入口

**三个页面**（直接套 Element Plus 默认样式，不折腾配色）：

| 页面 | 要点 |
|---|---|
| `Categories.vue` | `el-table` 树形（`row-key="id"` + `tree-props`）；父分类下拉用 `el-tree-select`，**必须剔除自己及全部后代**（后端只拦了「自己设自己」）；启停用 `@change` 调更新接口，成功后重拉树 |
| `Products.vue` | 分类 + 关键词筛选、分页；**不做硬删除**，只提供上下架（避免购物车/订单/统计的孤儿数据）；商品表单里的主图用 `ImageUpload` 上传 |
| `Orders.vue` | 店铺 / 状态 / **关键词（订单号 / 收货人 / 电话 / 运单号）** / **下单日期区间**筛选；状态单选（**管理端接口只支持单个枚举，不支持逗号**）、分页；**导出 CSV**（与筛选同参）；详情用 `OrderDetailDrawer`；发货填单号、退款同意/驳回、取消超时未支付订单 |
| `Coupons.vue` | 券模板列表、新增/编辑、启停；**不提供删除**；编辑时总量不能小于已领数；可建平台券或指定店铺券 |
| `Reviews.vue` | 评价列表、按展示状态/店铺筛选、显示/隐藏开关；**不删除评价** |

**多商户后**新增 `Merchants.vue`（入驻审核 / 禁用 / 启用）；商品、订单、券、评价页都增加
「店铺」列与筛选。商家后台复用同一批组件（`ProductFormDialog` / `SkuEditorDialog` /
`OrderDetailDrawer` 等）—— 通过 `merchant` prop 把 API 从 `/api/admin/**` 切到
`/api/merchant/**`，不复制组件。

**商品表单的多规格约束**：`hasSpecs === 1` 时**禁用价格/库存输入框**并提示去 SKU 编辑器。
原因在 §3.12 与 `ProductServiceImpl.update`：多规格商品调 `syncDefaultSku` 是 no-op，
商品级价格/库存改了不生效。提交时仍带当前汇总值（`ProductRequest` 必填），不能传 0。

**SKU 编辑器**（`components/SkuEditorDialog.vue`）是管理端最复杂的一块：

- 本地状态：`specs: { id, name, values: { id, value }[] }[]`，
  新建规格 `id = null`、新建值用**负数临时 id**（全局递减、删除后不复用）
- 回显时把后端 `ProductSku.specValueIds`（升序逗号串）按 `valueId → specIndex`
  **重排成规格顺序**，否则表格列会错位；匹配已有 SKU 时再按数值升序拼 key
- 组合矩阵 = 各规格值的**笛卡尔积**；增删规格/值后重建矩阵，
  按 key 复用旧行（保留用户已填的价格/图片），新组合默认用商品当前价、库存 0
- 已有 SKU 的库存**只读**，旁边「调整」按钮走 `StockAdjustDialog` → CAS 接口；
  新 SKU 的库存直接在表格里填
- 保存前校验：≤3 规格、每规格 ≤20 值、≤100 SKU、名称/值非空且不重名；
  若原 SKU 的 key 不在当前组合里，`ElMessageBox.confirm` 明确提示「会删 N 个 SKU，
  库存和销量丢失（历史订单快照不受影响）」
- 无规格商品：`specs = []`，矩阵只有一条「默认规格」

**订单列表没有 `statusLabel`**：管理端接口返回原始 `Order`，所以状态中文映射放在
`utils/orderStatus.ts`（顾客端用的是后端给的 `statusLabel`，不需要它）。
详情抽屉打开时才单独请求 `GET /api/admin/orders/{id}` 拿明细；
抽屉里的「退款信息」块与顾客订单详情一致，只在有退款原因 / 申请时间 / 处理时间时显示。

### 4.12 消息中心（`components/chat/`）

顾客与商家共用同一套组件，`ChatCenter.vue` 用 `mode="CUSTOMER" | "MERCHANT"`
切换 API 与展示对象：

- 顾客看店铺名 / Logo，显示商家在线状态与「进店逛逛」；
- 商家看顾客昵称 / 头像，不显示顾客在线状态；
- 窄屏（≤820px）单栏：会话列表 ↔ 聊天窗口切换，`ChatWindow` 的返回按钮回列表。

**状态与实时**：`stores/chat.ts` 负责登录后取票据建 EventSource、断线指数退避重连、
维护未读角标、分发事件。页面通过 `onEvent` 订阅新消息 / 已读 / 在线状态；
消息按 id 去重，当前会话收到对端消息立即调用已读接口。
`App.vue` watch 登录态调用 `connect()` / `disconnect()`；管理员也连接，
用于接收平台自营店消息。

**已读回执**：自己的气泡下方显示「已读 / 未读」，由 `peerLastReadMessageId` 与 SSE
`read` 事件驱动。

**入口**：

- 商品详情店铺卡片「联系卖家」（带 productId，新会话自动发商品卡）
- 店铺主页「联系商家」
- 订单详情状态区「联系卖家」（带 orderId，自动发订单卡；管理员浏览时不显示）
- 顶栏「消息」+ 未读角标、用户抽屉「我的消息」
- 商家后台菜单「消息」+ 未读角标
- 管理后台菜单「平台消息」（`/admin/messages`，同一套 `ChatCenter`，`admin` 模式走
  `/api/admin/chat/**`），只显示平台自营店会话；管理员登录后也会建立 SSE 连接，
  因此平台自营店对顾客显示在线

未登录点击入口跳 `/login?redirect=当前页`；管理员浏览商城时不显示联系卖家入口。

**顶栏角标**：

- 消息：`stores/chat.ts` 的 `unreadTotal`，由 SSE 实时更新；
- 领券中心 / 购物车：`stores/badge.ts`。领券中心统计 `!claimed` 的券数
  （匿名时即全部可领），购物车统计所有行 `quantity` 之和；
- 轮询 30 秒（后台标签页跳过）+ 加购、购物车增删改、领券后立即同步；
  三项都用 `el-badge`，值为 0 时 `hidden`，不显示红点。

**买小购客服**（M6）：顾客、商家、管理后台三个消息中心的会话列表顶部固定一张
「买小购」卡片，所有登录用户都能看到；点击进入
`/user/messages/agent`、`/merchant/messages/agent` 或 `/admin/messages/agent`。

- `api/agent.ts` 用 `fetch` POST `/api/agent/converse`，按 SSE 帧解析
  `start/trace/delta/usage/final/error/done`；不走 axios（15s 超时）也不走 EventSource。
- `AgentChatPanel.vue` 是真实对话：sessionId 按用户存 localStorage，打开时先拉
  `/api/agent/sessions`（跨设备历史会话），优先恢复最后使用的会话，否则取最近一条；
  发送后展示执行流、整段答案与图片、usage；支持「历史」切换、「停止」「新会话」。
- **执行流默认折叠成一行**：进行中显示转圈图标 + 最新一步；结束后显示
  「执行过程 · N 步」；点击展开全部步骤（最新一步高亮）。展开区最高 200px、
  内部滚动，避免长流程把整段对话撑出聊天框；摘要行超长省略、正文自动换行。
  外层 `ChatCenter` 的网格用 `grid-template-columns: 300px minmax(0, 1fr)`，
  面板 / 消息区 / 执行流链路都带 `min-width: 0` —— 否则 `1fr` 轨道会按执行流的
  最小内容宽度撑宽，长串详情直接把整个面板顶出窗口（2026-10-21 修）。
  历史消息同样折叠，展开后恢复该轮完整执行流（`HistoryMessage.trace` 透传）。
- **召回图片**：`grid auto-fit minmax(240px, 1fr)` 两列自适应、间距 12px；
  图片 280px 高、`fit=contain` 不裁切，左上角显示「图 N」标号，图注在图下。
  聊天区高度提升到 `min(860px, 100vh - 130px)`，给图片留足空间。
- 当前流未结束时禁用发送；中文输入法用 `isComposing` 防误发送。
- 客服服务不可用时显示 SSE `error` 事件里的通用文案，不自动重试。

---

## 5. 测试怎么写的

`ApiTestBase` 是基类，提供两样东西：

- **`customerToken(username)`** —— 走真实注册接口造顾客并拿 token
- **`adminToken(username)`** —— 注册接口只出顾客，所以**直接插库造管理员**再登录拿 token

所有测试都是 `@SpringBootTest + @AutoConfigureMockMvc + @Transactional`：
**整个测试方法跑在一个事务里，结束后自动回滚**，所以测试数据不会污染数据库。
（例外：MinIO 上传不在事务里，会留下文件——本地开发可接受。）

**两个并发测试（`StockConcurrencyTest` / `PaymentConcurrencyTest`）刻意不加 `@Transactional`** ——
并发要的是真实提交，整个测试包在一个事务里的话其他线程根本看不到数据，也就测不出竞态。
代价是数据不会自动回滚，所以它们自己在 `@AfterEach` 里清理。

⚠️ **清理必须按「本次测试造的那些行」删，不能图省事传 null**：

```java
// ❌ 看着像「无条件」，其实就是没有 where —— DELETE FROM orders，整张表没了
orderMapper.delete(null);

// ✅ 只删本次测试造的买家的订单
orderMapper.delete(Wrappers.<Order>lambdaQuery().in(Order::getId, orderIds));
```

MyBatis-Plus 的 `delete(null)` 生成的是 `DELETE FROM orders`。
**本地开发库和测试库是同一个**，所以一跑测试，开发时下的订单、加过的购物车、
存过的收货地址会被一起抹掉 —— 而且很难往「是测试干的」上想。
（这个问题 2026-10-16 发现并修掉，见 HANDOFF §5.19。）

**测试跑在独立的库和 Redis database 上**（`its_mygo_test` / db 1），跟开发环境彻底分开 ——
为什么非分不可、怎么配的，见 HANDOFF §5.20。

覆盖重点（当前 **189** 条；CI 上跳过 3 条依赖 MinIO 的，跑 186）：

| 测试类 | 覆盖 |
|---|---|
| `AuthControllerTest` | 注册→登录→访问受保护接口；无 token / 坏 token 401；重复用户名；密码错误；参数校验 |
| `AddressControllerTest` | 本人 CRUD；**越权返回 404**；默认地址唯一；**独立的设默认接口（含幂等与越权）**；**删除默认后不递补**；未登录 401 |
| **`UserProfileControllerTest`** | 未登录 401；资料回显（昵称回退用户名）；改昵称手机；空昵称 400；**旧密码错 400 而不是 401**；新旧相同被拒；改密后旧密码登录失败、新密码成功；**头像上传（StorageService 用 @MockitoBean 替身，不依赖 MinIO）** |
| `CategoryControllerTest` | 树形结构；停用分类对顾客隐藏；非管理员 403；有子分类/有商品时禁删 |
| `ProductControllerTest` | 上架可见/下架隐藏；关键词搜名称与副标题；分类筛选；分页；非管理员 403 |
| `CartControllerTest` | 重复加购累加数量；总价只算已勾选且可购买；超库存被拒；**越权 404**；需登录 |
| `OrderControllerTest` | 下单扣库存并清空购物车；**订单快照**（改价后历史订单不变）；完整生命周期；非法流转被拒；取消回滚库存；越权 404；顾客不能发货；**库存实时性**；**防重复提交**；**支付流水**；**退款申请/同意/驳回**；**超时取消**；**立即购买不动购物车**、超库存被拒、中途失败要回补预扣的库存 |
| **`StockConcurrencyTest`** | **10 个买家并发抢 5 件**：恰好 5 成功 5 拒，库存归零、销量 5，卖的+剩的 == 初始库存 |
| **`StockCacheBreakdownTest`** | **缓存击穿**：清空库存键后 20 并发，断言数据库**只被查 1 次**（去掉互斥锁会变成 20） |
| **`PaymentConcurrencyTest`** | **并发支付**只成功一次（守唯一索引）；**并发取消只回滚一次库存**（守状态机原子性，没有索引兜底的那个） |
| **`CacheProtectionTest`** | **穿透**：查不存在的 ID 时数据库零查询；存在的不被误挡。**雪崩**：连续刷新 20 次，TTL 不是固定值。**重建**：手工插库的商品重建后能被过滤器认到 |
| **`CacheAsideTest`** | **第二次读走缓存**（三次读只查一次库）；**改完立刻读到新值**（缓存被删而非等 TTL）；分类变更后树缓存失效 |
| **`RateLimitTest`** | 注册按 IP 打到第 6 次被拒；**场景之间互不影响**（注册打满不影响登录）；下单按用户限流 |
| **`ProductSkuControllerTest`** | 新建商品自带默认 SKU；负数临时 id 的规格值能落库并被 SKU 引用；**改价格再保存时库存不动**；汇总字段（最低价 / 总库存 / has_specs）跟着重算；重复组合与漏选规格被拒；非管理员 403；**库存调整同时刷新 SKU 行 / 商品汇总 / Redis**；**`expectedStock` 过期（CAS 失效）被拒且库存不变**；跨商品的 SKU 返回 404；负库存 400 |
| **`FavoriteControllerTest`** | 收藏/取消幂等；列表带当前商品信息；状态接口；别人的收藏不可见；商品不存在 404；未登录 401 |
| **`FootprintControllerTest`** | 同一商品只留一行；按浏览时间倒序；**超过 110 条只保留最新 100 条**；清空；商品不存在 404；未登录 401 |
| **`ReviewControllerTest`** | 仅已完成订单项可评；同一订单项不可重复评；评分越界 400；商品公开评价只展示未隐藏；汇总平均分/数量；管理端隐藏后商品详情消失、我的评价标记隐藏；顾客不能隐藏；别人的订单项 404 |
| **`CouponControllerTest`** | 领取后领券中心 claimed/剩余数正确；重复领取 400 而非 500；最后一张领完拒绝；过期券不可领且不展示；可用券按金额过滤；**EXPIRED 由 SQL 按数据库时间计算**；管理端模板增改与停用；总量不能小于已领；非管理员 403 |
| **`OrderCouponTest`** | 用券后实付 = 原价 - 减免；门槛不足拒绝；已用券不能再用于另一单；别人的券 404；取消订单退券；退款同意退券；**退回过期券置 EXPIRED** |
| **`MerchantApplyControllerTest`** | 申请 / 重复申请 / 驳回后重提 / 平台通过后 role 与 merchant_id 生效；禁用后旧 token 立即 403、店铺页 404；默认商户不能审核/禁用；店铺名唯一 |
| **`MerchantProductTest`** | 商家只能看/改本店商品与 SKU；跨店操作 404；店铺禁用后顾客列表与详情不可见；平台可按商户筛选；平台改商品不改变归属 |
| **`OrderSplitTest`** | 跨店结算生成 N 个子订单且金额正确；商家只能看/操作本店订单；平台券重复选择被拒；店铺券不能跨店；某店库存失败时 Redis 预扣回补 |
| **`MerchantReviewTest`** | 商家只看本店评价、只能隐藏本店评价；平台可按商户筛选 |
| **`ChatControllerTest`** | 会话找或建幂等、商品卡片只发一次；自己的店被拒；发送 / 未读 / 已读 / 已读回执；文本、长度、外链图片、未知类型校验；商品/订单卡片归属与快照冻结；跨用户/跨商家 404；停用店铺禁止发送但保留历史；`beforeId` 翻页与 `afterId` 补齐；SSE 票据过期/一次性/管理员可连接；**平台自营店由管理员接收、回复并标记已读，管理员不能访问普通商家会话** |
| **`AgentControllerTest`** | Converse 返回 start/trace/final/done SSE 并落 `agent_sessions`；A/B 跨用户会话在 converse/stop/history 全部 404；会话列表按 Java 归属表二次过滤；Stop 透传 runId；未配置密钥返回明确错误；空消息/空 sessionId 400 |
| **`AgentEventMapperTest`** | start/trace/delta/final（含图片）/usage 映射；Python ErrorEvent 映射为通用文案；done/error 辅助方法；空 Event 返回 null |
| **`SkuCartTest`** | 同商品两个规格各占一行、重复加购只累加；小计按**那一款**的价（不是商品最低价）；跨商品的 skuId 被当「不存在」挡住；SKU 被删后那行标成不可购买；停售款加不进去 |
| **`SkuOrderTest`** | 订单项带 `specText` 快照且单价取那一款；**规格改名后历史订单不变**；**取消只回补买过的那一款**、另一款纹丝不动；立即购买只扣选中的 SKU；买停售款被拒 |
| **`SkuStockConcurrencyTest`** | 10 人抢 5 件的单一 SKU（恰好 5 成功、Redis 与库都对得上）；**同商品两款互不串扰**（黑 3 件售罄不影响白 10 件） |
| `StorageServiceTest` | 上传成功 + **URL 可匿名读取**；拒绝非图片；顾客不能上传 |
| `SharedRedisTest` | 共用 Redis 连通；key 前缀；**TTL 存在**（见 HANDOFF §5.5） |
| `MyBatisPlusCompatibilityTest` | 一次完整 CRUD，验证 MP 在 Boot 4.1.1 上可用 |

---

## 6. 几条贯穿全局的约定

| 约定 | 原因 |
|---|---|
| **金额一律 `BigDecimal` + `DECIMAL` 列** | double 有精度误差，钱不能用 |
| **Controller 只做参数接收与包装，业务判断在 Service** | 便于测试与复用 |
| **越权一律 404** | 不泄露数据存在性 |
| **事务边界在 Service**（`@Transactional`） | 一次业务操作要么全成要么全败 |
| **Service 是接口 + `impl` 实现类** | 国内主流分层，Controller 注入接口 |
| **写 Redis 的 key 必须带 TTL** | 共用实例是 volatile-lru 策略，见 HANDOFF §5.5 |
| **外部资源的命名以 `its-my-go` 开头** | 本机多项目共用，见 HANDOFF §4.4 |

---

## 7. 还没实现的部分

**多商户改造已完成**（2026-10-19）：商家入驻/审核、店铺主页、商品归属、
商家商品/订单/店铺券/评价后台、跨店购物车按店拆单分别支付、平台监管，
见 §3.16。明确**不做**：佣金、分账、商家结算/提现、商家子账号、关注店铺。

平台管理端已完成：**商户审核、分类管理、商品管理（含传图 / 上下架）、规格与 SKU 编辑器、
订单管理（发货 / 退款审核 / 取消超时订单）、平台券 + 店铺券、评价管理**，见 §4.11。
顾客端已完成商品列表、商品详情（含规格选择栏 / 收藏 / 评价展示 / 店铺卡片）、登录注册、
购物车（按店分组）、结算（按店选券、拆单）、多订单结果页、订单详情、用户中心
（订单 / 收藏 / 足迹 / 优惠券 / 评价 / 地址 / 资料 / 改密）。

⚠️ **顾客端订单操作入口已补齐**：订单详情页按状态显示
「取消订单 / 确认收货 / 申请退款 / 立即支付」，与后端
`OrderStatus.cancellable()` / `refundable()` 的口径一致：
待支付和待发货可取消，待收货可确认收货，待发货/待收货/已完成可申请退款。
取消与退款会回滚库存，用券时未过期券自动退回。

⚠️ 管理端**不做商品的硬删除**（只提供上下架）：商品删掉会留下购物车、订单快照、
销量统计的孤儿数据，真实电商也通常用下架代替。后端 `DELETE /api/admin/products/{id}`
仍然存在，但前端不调用它。

**前端还没有自动化测试，也没进 CI** —— 目前靠浏览器手工走一遍
（本轮已实测：收藏/取消、足迹记录与清空、领券中心领取、结算选券抵扣、
下单后券变已使用、取消后退券、完成订单评价、商品详情评价展示、管理端隐藏评价）。

M6 / M7 是客服链路：gRPC 接入（`rag_helper` 那边已就绪）、查订单环回。

详见 [decisions-ecommerce.md](decisions-ecommerce.md) §4 的分阶段路线。
