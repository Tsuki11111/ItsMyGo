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
  → objectName = "products/" + UUID + "." + 扩展名    ← UUID 防重名/防路径穿越
  → minioClient.putObject(...)
  → 返回 http://localhost:9000/its-my-go-product-images/products/xxx.png
```

**桶策略设为公开只读**（`s3:GetObject` 允许 `*`），这样商品图 URL 能直接放进 `<img src>`
被浏览器加载，不用每次生成预签名 URL。

**懒初始化而不是 `@PostConstruct`**：MinIO 没起来时应用照样能启动，
只有真正上传时才报错 —— 否则整个应用会被一个非核心依赖拖死。

**幂等**：`bucketReady` 标志 + 双重检查，避免每次上传都去查一次 bucket 是否存在。

### 3.6 购物车（`CartServiceImpl`）

**加购用一条原子 SQL**，而不是「先查再插/改」：

```sql
INSERT INTO cart_items (user_id, product_id, quantity, selected)
VALUES (?, ?, ?, 1)
ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}
```

> 靠 `uk_user_product` 唯一索引 + `ON DUPLICATE KEY UPDATE` 一次搞定「不存在就插、已存在就累加」。
> 换成「先 select 判断再 insert/update」，两个请求同时加购同一商品会双双查到「不存在」、
> 双双插入，其中一个必然撞唯一索引报错。

**视图组装**（`buildView`）：购物车行只存 `product_id`，展示需要商品名/图/价/库存 ——
所以**按 productId 批量查一次商品再内存拼装**，不做逐行查库（N+1），也不做 join
（join 了还得处理重复列名）。

**商品被删除时**不隐藏这一行，而是显示「商品已删除」让用户自己删 —— 静默消失比看得见的异常更让人困惑。

**总价只算「已勾选 且 可购买」的项**，且用 `BigDecimal` 累加。

### 3.7 订单与状态机（`OrderServiceImpl` / `OrderStatus`）

**状态机集中在一个枚举里**（`OrderStatus.canTransitionTo`），不允许散落到各个 Service 里 `if`：

```
待支付 ──支付──> 已支付 ──发货──> 已发货 ──确认收货──> 已完成
   │                 │
   └──取消──> 已取消  └──取消──> 已取消        已发货之后不能取消，已完成/已取消是终态
```

所有状态变更都走同一个 `transition(order, target, stampSetter)`：先问状态机准不准，
不准就抛「订单当前是「待支付」，不能变成「已发货」」，然后才改状态和时间戳。
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

**订单快照**（`order_items` 的 `product_name` / `product_image` / `unit_price`）：

```java
item.setProductName(product.getName());     // 下单那一刻的名字
item.setUnitPrice(product.getPrice());      // 下单那一刻的价格
```

`product_id` 只是留个追溯线索，**展示订单时绝不能 join 商品表取当前值** ——
否则商品一改价，所有历史订单的金额跟着变，对账就废了。

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
已支付 / 已发货 / 已完成 ──申请──> 退款中 ──同意──> 已退款（终态）
                                    │
                                    └──驳回──> 回到申请前的状态
```

三个实现要点：

1. **申请时记下 `status_before_refund`** —— 驳回要回到原状态，不记就回不去了
2. **审核期间库存不动** —— 万一被驳回呢。只有**同意**时才 `restoreStock`
3. **同意退款时同步把支付流水置为 `REFUNDED`** —— 那才是退款真正的凭据

**超时取消**（`cancelExpired`）：批量取消超过 N 分钟未支付的订单并回滚库存。
先做成管理端手动触发，将来挂 `@Scheduled` 即可（多实例下要加分布式锁）。

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

### 3.8 库存扣减（`StockServiceImpl` + `ProductMapper.deductStock`）—— 本项目最值钱的一段

**要防的问题**：两个人同时买最后一件，不能都下单成功。

**两层防线**：

**第一层 · Redis 预扣** —— 原子 Lua 脚本（GET + 比较 + DECRBY 三步必须原子，分开写就有窗口）：

```lua
local stock = redis.call('GET', KEYS[1])
if not stock then return -1 end
if tonumber(stock) < tonumber(ARGV[1]) then return -1 end
return redis.call('DECRBY', KEYS[1], ARGV[1])
```

作用是**把绝大多数并发请求挡在数据库之外**。键是 `its-my-go:stock:{productId}`，
**必须带 TTL**（30 分钟）——共用 Redis 是 volatile-lru，不带 TTL 的键在内存打满时写失败。

**加载用互斥锁，防缓存击穿**：

```
                    库存键刚过期（TTL 到点）
                             ↓
   请求 1..N 同时发现 isLoaded() = false
                             ↓
        抢 SETNX 加载锁（its-my-go:stock:{id}:loading，TTL 5 秒）
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
UPDATE products
SET stock = stock - #{quantity}, sales = sales + #{quantity}
WHERE id = #{productId} AND status = 1 AND stock >= #{quantity}
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
    for (CartItem cartItem : cartItems) {
        if (!stockService.tryReserve(productId, qty)) throw ...;   // 第一层
        reserved.merge(productId, qty, Integer::sum);
        if (productMapper.deductStock(productId, qty) == 0) throw ...;  // 第二层
    }
    // …建订单、清购物车
} catch (RuntimeException e) {
    reserved.forEach(stockService::release);   // 数据库由 @Transactional 回滚，Redis 得手动补
    throw e;
}
```

**取消订单会回滚库存**（`restoreStock`）：数据库 `stock + n`、`sales - n`，Redis 同步 `INCRBY`。
回补时只补**已存在**的键 —— 键过期后再 `INCRBY` 会凭空造出一个错误的库存值。

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
- **数据来源是 §3.8 那个库存计数器**（`its-my-go:stock:{id}`），它本来就在被实时维护
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

## 4. 前端（`frontend/`）

### 4.1 工程与启动

```
frontend/
├── vite.config.ts          # /api 代理到 8080；端口读 `PORT` 环境变量（预览工具靠它挑空闲端口）
├── src/
│   ├── api/
│   │   ├── http.ts         # axios 封装 + 拦截器 + **登录凭据的存取**（token 的唯一归属地）
│   │   ├── types.ts        # 与后端 DTO 一一对应的类型（手写）
│   │   ├── product.ts      # 商品 / 分类 / 实时库存
│   │   ├── cart.ts         # 购物车
│   │   ├── auth.ts         # 登录 / 注册
│   │   ├── address.ts      # 收货地址
│   │   └── order.ts        # 下单 / 支付 / 提交令牌
│   ├── stores/user.ts      # 登录态（localStorage 的响应式包装）
│   ├── components/
│   │   └── ProductImage.vue  # 商品图 + 无图占位（列表页 / 详情页 / 订单共用）
│   ├── styles/theme.css    # 色板 + Element Plus 主题覆盖
│   ├── router/index.ts
│   ├── views/
│   │   ├── ProductList.vue     # 首页：品类栏 + 轮播 + 商品网格
│   │   ├── ProductDetail.vue   # 商品详情
│   │   ├── Login.vue           # 登录 / 注册（同一个页签切换）
│   │   ├── Checkout.vue        # 确认订单：选地址 + 商品清单 + 提交
│   │   └── OrderDetail.vue     # 订单详情 + 支付
│   ├── App.vue             # 顶栏 + 内容区
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

覆盖重点（当前 **76** 条；CI 上跳过 3 条依赖 MinIO 的，跑 73）：

| 测试类 | 覆盖 |
|---|---|
| `AuthControllerTest` | 注册→登录→访问受保护接口；无 token / 坏 token 401；重复用户名；密码错误；参数校验 |
| `AddressControllerTest` | 本人 CRUD；**越权返回 404**；默认地址唯一；未登录 401 |
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

M5 前端已完成**商品列表、商品详情、登录注册、结算下单、订单详情**，
其余页面待做：购物车、我的订单、以及整个管理端。

⚠️ 结算页现在**只支持「立即购买」那一套**（商品通过 URL 参数传）。
购物车结算的入口等购物车页做好再接 —— 后端已经支持了（`items` 留空即可），
前端也只要把商品清单的数据源换掉，`Checkout.vue` 的结构不用动。

**前端还没有自动化测试，也没进 CI** —— 目前靠浏览器手工走一遍。

M6 / M7 是客服链路：gRPC 接入（`rag_helper` 那边已就绪）、查订单环回。

详见 [decisions-ecommerce.md](decisions-ecommerce.md) §4 的分阶段路线。
