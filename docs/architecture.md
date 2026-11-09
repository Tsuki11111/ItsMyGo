# 架构选型 · 一次买够（ItsMyGo）

> **本文定位**：回答「**选了什么、为什么选它、整体长什么样、契约怎么对接**」。
> 「要做什么、做到什么程度」在 [docs/requirements.md](requirements.md)；
> 「具体怎么实现、代价是什么」在 [docs/implementation.md](implementation.md)（代码的镜像）；
> 「当前状态 / 坑 / 操作」以 [HANDOFF.md](../HANDOFF.md) 为准；
> 接口清单与字段见 [docs/api.md](api.md)；部署见 [docs/deployment.md](deployment.md)。
>
> 跨仓库：客服智能体的契约源码是 `rag_helper/proto/agent.proto`，
> 契约说明见 `rag_helper/docs/architecture.md` §4，对接前提见
> `rag_helper/docs/requirements.md` §4。
>
> 归并说明：原 `docs/decisions-ecommerce.md`（已于 2026-11-05 删除，原文可用 `git log` 查）
> 的技术选型、中间件约定与对接契约并入本文；
> 需求、验收、范围外、里程碑并入 [docs/requirements.md](requirements.md)。

---

## 0. 文档定位与权威关系

| 问题 | 看哪里 |
|---|---|
| 要做什么 / 验收标准 / 范围外 | [docs/requirements.md](requirements.md) |
| 选了什么技术、为什么、整体架构、契约对接 | **本文** |
| 每个机制具体怎么实现 | [docs/implementation.md](implementation.md) |
| 现在做到哪、有哪些坑、怎么操作 | [HANDOFF.md](../HANDOFF.md) |
| 接口清单、请求 / 响应、限流 | [docs/api.md](api.md) |
| 部署、端口、联合启动、生产 VM | [docs/deployment.md](deployment.md) |
| gRPC 契约字段 | `rag_helper/proto/agent.proto`（字段源码即权威） |

**冲突时的优先级**：状态与坑以 HANDOFF 为准；实现细节以 implementation 为准（本文与之冲突时
以实现为准并回头修本文）；契约以 rag_helper 的 proto 为准；需求与范围以 requirements 为准。

---

## 1. 系统总览

### 1.1 形态：一个 Java 单体 + 一个外部 agent

```
浏览器 ──HTTPS──> Java 单体（登录 / 权限 / 业务 / 页面 / SSE 转发）──gRPC──> 买小购 agent（rag_helper·8003）
                                                  ▲                              │
                                                  └──── 内部 HTTP + X-Agent-Ticket ┘
                                                        （agent 反向回调 Java 查数据 / 发消息）
```

- **浏览器只跟 Java 说话**：页面、接口、SSE 全部由 Java 提供；浏览器不直连 agent。
- **Java 是 gRPC 调用方**：`Converse` 的 server streaming 事件原样转成 SSE。
- **agent 只在内网**：只允许本单体访问；反向调用走 `/internal/agent/**`，凭证是 Java 签发的
  短期票据（模型不参与身份）。
- **单实例**：Java 与 agent 都是单实例；多实例的已知债见 requirements §6。

### 1.2 三条边界（来自 rag_helper，本仓库不能自行更改）

1. 浏览器只跟 Java 说话；
2. agent 服务只在内网、只允许本单体访问；
3. 知识库导入与智能体评测是 agent 的管理端行为，由 Java 管理端代理，浏览器不直连 agent。

### 1.3 部署形态

| 形态 | 用途 | 入口 |
|---|---|---|
| 本机开发 | 改代码，复用本机共用 MySQL / Redis / MinIO | 后端 8080 + 前端 5173 |
| Compose 全栈 | 新克隆 / 演示 / 内网部署 | `./deploy.sh` → 8088 |
| 联合部署 | 与 agent 一起跑 | `rag_helper/deploy.sh joint` |
| 生产（演示） | 内网 VM（静态 IP、离线镜像包、图片同源） | `http://10.184.171.106:8088` |

端口 / 环境变量 / 离线部署细节见 [docs/deployment.md](deployment.md)。

---

## 2. 技术栈与选型理由

| 层 | 选型 | 为什么选它 | 被否 / 代价 |
|---|---|---|---|
| 后端形态 | **Spring Boot 4.1.1 单体**（单模块 Maven） | 练手项目要的是业务建模与 gRPC 接入，不是分布式治理；单模块构建快、心智负担低 | 不拆微服务；多实例问题留到需要时再说 |
| Web 栈 | **Spring MVC**（不用 WebFlux） | gRPC 走阻塞 stub，事件流用 `SseEmitter`；同步链路调试简单 | 高并发 I/O 不如 WebFlux，但本项目不是 I/O 瓶颈 |
| ORM | **MyBatis-Plus 3.5.17** | SQL 可控（库存条件更新、复杂聚合），分页 / CRUD 少写样板 | 已实测 Boot 4.1.1 兼容；分页拦截器需**手动注册**，见 HANDOFF §5.3 |
| 数据库 | **MySQL 8**（复用共用容器） | 本机已有且多项目共用；事务与条件更新是库存真相 | 库 `its_mygo` / 测试库 `its_mygo_test` 分离 |
| 缓存 | **Redis 7**（复用共用容器） | 库存预扣、缓存三防、限流、令牌、票据都靠它 | 共用实例是 `volatile-lru`：**所有 key 必须带 TTL**，前缀 `its-my-go:` |
| 表结构迁移 | **Flyway 12.4.0** | 迁移可追溯、测试库自动建表；不用 `ddl-auto` | 必须用 `spring-boot-starter-flyway`（Boot 4 模块化，见 HANDOFF §5.1）；脚本只增不改 |
| 安全 | **Spring Security 7.1 + JWT（jjwt 0.13.0）** | 无状态、前后端分离友好 | **JWT 里的 role 不能当权限**：只放 userId，角色按库实时推导（见 D10） |
| 对象存储 | **MinIO 8.5.17**（复用共用容器） | S3 兼容，商品图 / 头像 / 聊天图统一 | 8.6+ 的 okhttp 5 元数据空壳问题，固定 8.5.17（HANDOFF §5.4） |
| 消息队列 | **RabbitMQ 4**（本项目自有容器） | 超时关单用 TTL + 死信延迟队列，避免逐消息 TTL 的队头阻塞 | 从 RocketMQ 迁移而来（Boot 4 生态 + 运维更轻）；数据库扫描做最终兜底 |
| gRPC 客户端 | **原生 `grpc-java` + `protobuf-maven-plugin`** | Boot 4 没有官方 starter；原生可控 | 版本必须用 `grpc-bom` 锁死，否则运行时 `NoSuchMethodError`（HANDOFF §5.26） |
| 前端 | **Vue 3 + Vite + TypeScript + Element Plus + Pinia** | 前后端分离、组件生态成熟 | 无前端自动化测试，手工走查 + build |
| 流式 | **SSE（`SseEmitter`）** | 浏览器侧只需单向推送；gRPC 事件流 → SSE | SSE 带不了 Authorization：聊天用一次性票据换 SSE（见 D12） |
| 部署 | **Docker Compose + 离线镜像包** | 一键起全栈；内网 VM 不能拉 Docker Hub | 生产是演示环境，见 deployment.md 的缺口表 |
| 日志 | **SLF4J + Logback**（Boot 默认；prod 用 logstash JSON encoder，dev 控制台 UTF-8） | 单模块不引入额外日志框架；JSON 便于将来接采集；traceId 由 MDC 统一注入 | 不引入 ELK / Loki / Prometheus（演示环境无集中平台）；详见 §3.8 与 implementation §3.28 |

**Boot 4 的四个结构化差异**（动手前必读）：自动配置拆成独立模块、Jackson 3 包名 `tools.jackson`、
MyBatis-Plus 分页插件拆包、`spring-boot-starter-aspectj` 改名。详见 HANDOFF §5.1–§5.4、§5.9。

---

## 3. 总体架构

### 3.1 组件与端口

| 组件 | 宿主机端口 | 说明 |
|---|---|---|
| 前端（Vite / Nginx） | 5173 / 8088 | 开发用 Vite，部署用 Nginx（同源反代 `/api`、商品图、`/rag-images`） |
| 后端（Java） | 8080 | REST + SSE；`/internal/agent/**` 只在内网 |
| MySQL / Redis / MinIO | 3306 / 6379 / 9000·9001 | 本机共用容器（本项目不另起） |
| RabbitMQ | 5672 / 15672 | 本项目自有（`its-my-go-rabbitmq`） |
| agent（rag_helper） | 8001 / 8002 / 8003 | 导入 / HTTP·评测 / gRPC；联合部署时走 `rag-*` 别名 |

### 3.2 一次下单的数据流（M31 起目标形态：D1 半同步占位 + 异步补账）

> M31 之前是单事务同步链路（Redis 预扣 + DB 扣库存 + 订单/明细/券落库一起提交）。
> 两段式已于 **2026-11-06 落地**（实现细节见 implementation §3.36、验收见 HANDOFF §6）；
> `itsmygo.order-create.async=false` 时退回同步补账，行为与旧链路一致。

```
浏览器 → POST /api/orders（带提交令牌）
  ── 同步段（一个事务）──
  → Security：JWT → AuthIdentityService 推导 role/merchantId
  → OrderService：校验令牌 → 校验商品/SKU/店铺/券 → 按 store 分组拆单
      → StockService：Redis Lua 预扣（挡流量）
      → 写 orders（status=CREATING，金额/收货快照）+ order_items（快照）
  → 事务提交后发 MQ「订单已创建」事件（事件含订单 id、下单行快照、购物车行 id）
  → 返回 CheckoutView（订单状态「创建中」）

  ── 异步段（MQ 消费，一个事务）──
  → DB：UPDATE product_skus SET stock = stock - ? WHERE stock >= ?（最终真相）+ 商品汇总
  → DB：优惠券条件占用（markUsed）
  → DB：按快照 id 清空本次下单的购物车行
  → DB：orders CAS CREATING → PENDING_PAYMENT
  → 提交后：为每张订单发 RabbitMQ 延迟消息（30 分钟超时关单）
  → 任一步失败：回滚 + Redis 预扣回补 + 券回滚 + 订单置 CREATE_FAILED（写原因）
```

超时未支付：延迟队列到期 → `cancelIfExpired`（状态 + `NOW()` 幂等）→ 回滚库存与券；
MQ 不可用时 `OrderTimeoutScanJob` 兜底。`CREATING` 超时由新的扫描任务兜底（重投事件，
仍失败则失败补偿）。

### 3.3 一次客服问答的数据流

```
浏览器 → POST /api/agent/converse（SSE）
  → Java：校验登录 → 签发工具票据 → gRPC Converse(authorization, x-user-id, x-agent-tool-ticket)
  → agent：输入护栏 → 主 agent 编排 subagent → 必要时回调 /internal/agent/**
  → Java：事件流（start/trace/delta/usage/final/error）逐条转 SSE
  → 前端：渲染执行流 / 正文 / 商品卡；动作卡按 sessionId 从 Java 拉取
```

动作类：agent 只 `prepare` → Java 落 `agent_actions` → 用户点确认 → Java 用快照执行 →
触发内部事件（`order-confirmed` / `urge-*-confirmed`）→ agent 写一条 assistant 消息。

### 3.4 模块划分

包结构、每层的类清单与职责见 [HANDOFF.md](../HANDOFF.md) §3.1；
每个机制的具体实现见 [docs/implementation.md](implementation.md)。
本文只保留「分几层、各层边界」，不重复类清单。

### 3.5 售后领域模型与状态机（M30）

**模型**（V31 迁移，详见 requirements §2.5）：

| 表 | 关键字段 | 说明 |
|---|---|---|
| `after_sales` | `after_sale_no`(唯一)、`order_id`、`merchant_id`、`user_id`、`type`、`status`、`reason_code`、`reason_text`、`images`(JSON)、`apply_amount`、`refund_amount`、`reject_reason`、`return_tracking_no/carrier`、`review_deadline`、各时间戳 | 一次售后诉求的完整生命周期 |
| `after_sale_items` | `after_sale_id`、`order_item_id`、`quantity`、`refund_amount` | 支持按项、按数量部分退款 |
| `after_sale_reasons` | `code`(唯一)、`label`、`sort`、`status` | 平台维护的原因字典 |
| `orders` | 新增 `refunded_amount` | 已退金额累计；等于 `total_amount` 即整单退完 |

**状态机**（服务层唯一入口 + CAS 条件更新，禁止散落判断）：

```
PENDING_REVIEW ─同意(REFUND_ONLY)──────────> REFUNDED
      │   └──同意(RETURN_REFUND)──> WAITING_RETURN ──买家填单号──> RETURN_SHIPPED
      │                                                              │
      │                                                     商家确认收货 → REFUNDED
      ├──驳回(必填理由)──> REJECTED（可再次申请）
      └──买家撤销──> CANCELLED
```

**金额口径**：项金额 = 订单项单价 × 退款数量；优惠按订单**等比分摊**
（`项小计 × totalAmount / originalAmount`），尾差归最后一次退款；
**券只在订单全部退完时退回**，部分退款不退券；无运费字段。

**超时处理**：`AfterSaleScanJob`（`@Scheduled` + Redis 锁，复用订单超时扫描的模式）——
商家 48h 未审核自动同意；退货寄出 7 天未确认自动确认收货并退款。两个值都可配
（`itsmygo.after-sale.*`），不引入 RabbitMQ（售后量小、规则可能频繁调，扫描更灵活）。

**与旧整单退款的关系**：旧接口/旧状态（`REFUNDING`/`REFUNDED`）保留不动，新售后单独立推进；
两者都调用同一套补偿（库存/券/支付流水），补偿逻辑抽成按订单项的方法，避免两套实现。

### 3.6 下单异步补账的模型、状态机与 MQ 拓扑（M31 / D1）

**状态机**（`orders.status` 增加两个值，其余状态机不动）：

```
CREATING ──异步补账成功──> PENDING_PAYMENT ──支付──> PAID → SHIPPED → COMPLETED
    │                                              └─取消/超时→ CANCELLED（回滚库存与券）
    ├──补账失败 / CREATING 超时──> CREATE_FAILED（终态；买家常“重新下单”）
    └──CREATING 期间：不能支付、不能取消、不能退款
```

`OrderStatus` 需要同步新增 `CREATING` / `CREATE_FAILED`，并明确：
`payable()` 只含 `PENDING_PAYMENT`；`cancellable()` 不含 `CREATING`；
超时关单（`cancelIfExpired`）只处理 `PENDING_PAYMENT`。

**数据模型（V32 迁移）**：
- `orders.create_error`（varchar 200）：补账失败原因，仅 `CREATE_FAILED` 时有值；
- 不新增占位明细表：同步段已写 `orders` + `order_items` 快照，异步段只做“可重试的补账动作”；
- **不新增 Outbox 表**（见 D22）：用 `CREATING` 超时扫描重投兜底，保持表结构简单。

**MQ 拓扑**（新增，与 D11 超时关单并列）：

| 元素 | 名称 | 说明 |
|---|---|---|
| Exchange | `its-my-go-order-created-exchange`（direct, durable） | 订单创建事件 |
| Queue | `its-my-go-order-created-queue`（durable） | 补账消费队列 |
| Routing key | `order-created` | — |
| 事件体 | `{ requestId, orderIds:[...], cartItemIds:[...], lines:[{skuId, productId, quantity}], coupon: {orderId,userCouponId} }` | 消费端**不信任事件数据**，以 DB 中 `CREATING` 订单/明细为准做幂等 |
| 失败处理 | 消费异常：业务失败走失败补偿并 ack；不可恢复异常 reject 不重投（进日志/人工） | 与现有超时消费者风格一致，避免无限重投 |

**消费语义**：至少一次投递 + 按状态 CAS 幂等（非 `CREATING` 的订单直接跳过）；
补账事务提交后才发超时关单消息，保证“关单消息不会早于订单可用”。

**降级模式**：`itsmygo.order-create.async=false` 时，同步段提交后直接在当前线程执行同一套
补账逻辑（等价旧链路行为），供测试与无 MQ 环境使用；`async=true` 才真正发 MQ。

---

### 3.7 权限与授权模型（目标态，2026-11-08 设计）

> 现状：3 个硬编码用户类型 + URL 前缀 `hasRole` + 服务层归属校验；无权限码/菜单/按钮/数据范围。
> 本节是 v1 目标设计，落地分阶段（见下方）：**P1 后端 + P2 基础前端已于 2026-11-08 落地，
> 开关保持默认关闭；P2.1 邀请管理员选角色已确认不做，因此不启用开关**。

**授权链路（目标态）**：

```
JWT(userId) → AuthIdentityService（按库实时推导，60s 缓存）
  → 用户类型 ROLE_CUSTOMER / ROLE_MERCHANT / ROLE_ADMIN_<SUBROLE>
  → ADMIN 额外加载权限码集合（DB: role_permissions）
  → SecurityContext.authorities = ROLE_* + 权限码
  → ① SecurityConfig URL 前缀粗筛（保留）
  → ② @EnableMethodSecurity + @PreAuthorize("hasAuthority('admin:order:ship')") 细粒度
  → 服务层数据范围：ADMIN=ALL；MERCHANT=本店；CUSTOMER=本人（沿用现有归属校验）
```

**为什么不是“纯前端隐藏”**：前端只负责体验，后端方法级校验是唯一可信边界；
前端权限码仅用于菜单/按钮显隐。

**表结构（V36 草案）**：

```sql
CREATE TABLE roles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,   -- SUPER_ADMIN / OPERATOR / CUSTOMER_SERVICE / FINANCE
  name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  builtin TINYINT NOT NULL DEFAULT 1, -- v1 固定角色，禁改
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(96) NOT NULL UNIQUE,   -- admin:order:ship
  name VARCHAR(64) NOT NULL,
  module VARCHAR(32) NOT NULL,        -- order / aftersale / merchant / product ...
  type VARCHAR(16) NOT NULL,          -- API / MENU / BUTTON
  description VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE admin_user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
```

**初始化数据**：4 个内置角色 + requirements §2.9 的权限码 + 角色映射；
现有 `users.role='ADMIN'` 用户写入 `admin_user_roles(SUPER_ADMIN)`；
商家/顾客**不迁移**（继续用 `users.role` 作为用户类型）。

**分阶段落地**：

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0（当前） | requirements §2.9 + 本节 + D26 评审，不改代码 | 用户确认 |
| P1 后端 | ✅ 2026-11-08 已落地：V36 建表/seed；AuthIdentityService + JwtAuthenticationFilter 加载 ADMIN 权限码并缓存；`@EnableMethodSecurity`；17 个管理端 controller 加 `@PreAuthorize`；`itsmygo.rbac.enabled` 默认 **false**（关闭=现状 ADMIN 全通）；单测覆盖 403/200/子角色边界 | 开关关闭时全量测试不变；打开后按权限码生效 |
| P2 前端 | ✅ 基础版 2026-11-08：`/api/auth/permissions` + 权限 store + `v-permission` 指令 + 管理端菜单过滤 + 订单操作按钮显隐；**P2.1 邀请选角色已确认不做** | 无权限菜单/按钮不渲染；直接调接口仍 403 |
| P3 灰度 | ⏸️ 暂不执行：P2.1 不做，`RBAC_ENABLED` 保持 false；清理 `hasRole` 硬编码和灰度验收一并搁置 | 不启用开关 |

**回退**：`itsmygo.rbac.enabled=false` 立即回到现状（ADMIN 全通、前端不按权限码过滤）；
权限缓存 60s + 角色变更主动 evict，旧 token 不受影响（角色仍按库推导）。

**备选方案对比**：

| 方案 | 结论 |
|---|---|
| 自研 DB RBAC + Spring Method Security（选中） | 复用现有 JWT/AuthIdentityService，成本低、可审计、与现有归属校验衔接自然 |
| Keycloak/OIDC | 多应用统一认证才划算；本项目单体 + 自建登录，引入成本高 |
| Casbin/OPA 策略引擎 | 策略热更新、规则复杂时合适；当前权限规则少且稳定，运维/学习成本不划算 |
| 前端隐藏权限 | 只能做体验，不能作为授权（直接调接口可绕过） |

**风险与对策**：

1. **接口漏配权限码**：P1 启动时输出未标注的管理端接口清单；未标注接口默认拒绝并记审计；
2. **缓存不一致**：权限变更 evict `its-my-go:auth:perm:{userId}`，TTL 60s 兜底；
3. **既有测试**：`rbac.enabled=false` 时行为不变；打开后种子管理员为 SUPER_ADMIN，测试仍通过；
4. **商家/顾客不受影响**：v1 不迁移，避免面过大。

---

### 3.8 日志与可观测性

> 实现细节见 implementation §3.28；这里只讲**选型与边界**。

**选型**：SLF4J + Logback（Spring Boot 默认，不换 log4j2），不引入 ELK / Loki / Prometheus / OTel。
理由：单体 + 演示环境，没有集中日志平台与运维团队；Logback 足够覆盖“排障 + 审计”两个诉求，
JSON encoder（`net.logstash.logback`）让以后接采集系统不用改业务代码。

**两种输出**：

| 环境 | 输出 | 说明 |
|---|---|---|
| dev / test | 控制台（UTF-8，`logback-spring.xml` CONSOLE） | 格式 `时间 level [thread] [traceId] [userId] logger - msg`；Windows 终端代码页问题见 HANDOFF §5.40 |
| prod | 控制台 + **JSON 文件** `logs/its-my-go.log` | 按天 + 100MB 滚动，保留 14 天 / 总上限 2GB；stdout 留给容器采集 |

**traceId 贯穿**：`common/logging/RequestIdFilter` 从 `X-Request-Id` 或生成 ID 写入 MDC，
响应头回传；RabbitMQ 消息头携带 traceId；扫描任务用 `scan-*` 前缀；日志 pattern 统一带
`traceId` 和 `userId`（未登录为空）。

**应用日志 ≠ 审计**：操作审计走 `@AdminAudit` → `admin_audit_logs` 表（可查询、可追责），
应用日志只用于排障；两者字段与保留策略不同，不能互相替代。

**敏感信息边界**：密码 / token / 表单值不落明文日志；异常栈里可能出现 SQL 参数，
因此 **prod 关闭 SQL 明细**（`application-prod.yml`）；日志只记 id / 字段名。

**当前缺口（明确不做/以后补）**：无集中日志、无 metrics、无链路追踪；dev 看 `.dev-logs/` 或终端，
prod 只有 VM 上的 JSON 文件（未接采集）。要做时方向：Loki + Promtail / ELK 采 JSON 文件，
Micrometer + Prometheus 暴露指标，OTel 接 trace。

---

### 3.9 商品生命周期与归档

`上架(1) ⇄ 下架(0) → [下架 ≥180 天] 归档(archived_at) → [归档 ≥30 天且无引用] 物理清理`

- 归档 = 自动隐藏 + 清购物车/缓存，**可恢复**；
- 物理清理只针对“无订单/售后/评价/问答/收藏/足迹”的商品，保证交易与用户数据不断链；
- 图片对象暂不删除（订单快照可能引用），后续做对象存储清理任务；
- 定时任务默认关闭 + dry-run，先出报告再执行（见 D27 / implementation §3.46）。

## 4. 接口与契约概览

### 4.1 对外 REST

全部接口、请求 / 响应、权限、限流与 SSE 事件见 [docs/api.md](api.md)。
路径分层（以 `SecurityConfig` 为准）：

| 前缀 | 权限 |
|---|---|
| `/api/health`、`/api/auth/register|login`、GET `/api/products/**` `/api/categories/**` `/api/shops/**` `/api/coupons` | 匿名 |
| GET `/api/chat/stream` | 匿名，凭 60 秒一次性票据 |
| `/internal/agent/**` | `permitAll`，凭 `X-Agent-Ticket`（**只在内网**） |
| `/api/merchant/**` | `hasRole("MERCHANT")` |
| `/api/admin/**` | `hasRole("ADMIN")` |
| 其余 | 登录 |

### 4.2 gRPC 契约（Java 是调用方）

> 字段源码：`rag_helper/proto/agent.proto`（**唯一权威**，`option java_multiple_files = true`）；
> 说明性概览：`rag_helper/docs/architecture.md` §4；对接前提：`rag_helper/docs/requirements.md` §4。

- **4 个 RPC**：`Converse`（server streaming）/ `Stop` / `ListSessions` / `GetHistory`。
- **`Event` 是 oneof，6 类**：`start`（session_id + run_id）、`trace`（统一执行流）、
  `delta`（流式增量）、`usage`（累计用量）、`final`（回复 + 图片 + 商品卡）、`error`。
- **`Trace.kind`**：`delegate` / `tool` / `result` / `reply` / `node`，前端统一渲染成执行流。
- **metadata**：`authorization: Bearer <服务端密钥>`、`x-user-id: <顾客ID 字符串>`、
  `x-agent-tool-ticket: <每轮票据>`；Java 注入前**先剥离客户端同名 metadata**。
- **票据**：Java 每轮签发（Redis 10 分钟，必须 ≥ `AGENT_GRPC_DEADLINE_SECONDS=240`）；
  回调时经 `X-Agent-Ticket` 带回，Java 只认票据里的 userId。
- **run_id**：由 agent 生成；Java 存映射，**不透传当 thread_id**。
- **会话内表单（2026-11-07，方案 A）**：`FinalEvent.forms` 是流式下发的**可交互**表单，
  `HistoryMessage.forms` 是**只读**回放；用户提交走下一轮 `ConverseRequest.form_result`
  （Java 只透传、不落库），服务端按 `form_id` 校验一次性 / 过期后直接调工具。
  首批 `form_type=refund_reason`；退款原因随草稿落 `agent_actions`，确认时传入 `RefundRequest`。
- **契约铁律**：**只加字段不删改**（老消息返回空列表）；改 proto 必须双端同步；
  Java 侧由 proto 生成 stub。

### 4.3 agent 反向回调 Java 的内部接口

| 方向 | 接口 | 用途 |
|---|---|---|
| agent → Java | `POST /internal/agent/orders/query` | 查当前用户订单（含物流摘要，**无 PII**） |
| agent → Java | `POST /internal/agent/catalog/products`、`GET /internal/agent/catalog/all\|categories\|addresses` | 商品目录 / 分类 / 收货地址 |
| agent → Java | `POST /internal/agent/actions/order|refund|urge-shipment|urge-refund`（及 `/send`） | 准备草稿 / 执行发送 |

- 全部 `permitAll`、只在内网、**票据是唯一用户身份**；不要把 `/internal/agent/**` 暴露公网。
- 工具回调失败必须返回可读字符串 / 明确错误，让 agent 能转述，而不是中断整轮。

### 4.4 确认卡片与内部事件

```
agent 工具 prepare（只生成草稿）→ Java 落 agent_actions（PENDING_CONFIRM 快照）
  → 用户在聊天卡片确认 → Java 用服务端快照执行（参数不来自模型）
  → Java 触发内部事件（order-confirmed / urge-shipment-confirmed / urge-refund-confirmed）
  → agent 执行图：调 Java 发送工具（或直接生成回复）→ 写一条 assistant 历史
```

**红线**：工具返回文本必须写明「尚未下单 / 尚未提交 / 草稿已生成」；模型不许声称已执行；
卡片与状态的 source of truth 在 Java（前端按 `sessionId` 拉取，不信任模型文本）。

### 4.5 图片与存储 URL

- 数据库只存**同源相对路径** `/its-my-go-product-images/...`；
  开发由 Vite、生产由 Nginx 反代到 MinIO。
- `MINIO_PUBLIC_ENDPOINT` 保持 `/`；配成绝对地址会把主机名固化进库，换 IP 后历史图片全部失效
  （2026-11-05 事故，HANDOFF §5.39）。
- 客服召回图走 `/rag-images/` → rag_helper 的 MinIO（9002），同样只在展示层转换。

### 4.6 售后接口（M30）

| 端 | 接口 | 权限 | 说明 |
|---|---|---|---|
| 顾客 | `GET /api/after-sales/reasons` | 登录 | 原因字典 |
| 顾客 | `POST /api/after-sales` | 登录 | 申请售后（订单、类型、订单项与数量、原因、凭证图） |
| 顾客 | `GET /api/after-sales` / `GET /api/after-sales/{id}` | 登录 | 我的售后列表 / 详情（含时间线） |
| 顾客 | `POST /api/after-sales/{id}/cancel` / `ship-back` | 登录 | 撤销申请 / 填退货单号 |
| 商家 | `GET /api/merchant/after-sales` / `{id}`、`POST /{id}/approve` `/{id}/reject` `/{id}/confirm-received` | MERCHANT | 审核 / 驳回（必填理由）/ 确认收货 |
| 管理 | `GET /api/admin/after-sales` / `{id}`、`POST /{id}/approve` `/{id}/reject` `/{id}/confirm-received` | ADMIN | 平台介入（同商家动作 + 审计） |
| 管理 | `GET/POST /api/admin/after-sale-reasons`、`PUT /api/admin/after-sale-reasons/{id}` | ADMIN | 原因字典维护 |

**幂等与并发**：审核类操作全部 CAS（`WHERE id=? AND status='PENDING_REVIEW'`），
重复点确认只会成功一次；申请时对订单行 `SELECT ... FOR UPDATE`，防止同一订单项并发超量申请。

### 4.7 下单接口的变化（M31）

对外 REST 形状**保持不变**：`POST /api/orders` 仍返回 `CheckoutView`，只是返回时订单状态为
`CREATING`（`statusLabel` 为「创建中」）。前端需要轮询订单详情/创建状态，直到
`PENDING_PAYMENT`（可支付）或 `CREATE_FAILED`（提示原因）。

| 接口 | 变化 |
|---|---|
| `POST /api/orders` | 同步段缩短：只落占位订单 + 明细；返回 `CREATING`；`async=false` 时退化为旧同步行为 |
| `GET /api/orders/{id}` / 列表 | 新增 `CREATING` / `CREATE_FAILED` 状态与中文名；`createError` 随订单返回 |
| `POST /api/orders/{id}/pay` | 仅 `PENDING_PAYMENT` 可支付；`CREATING` 返回业务错误「订单创建中，请稍后」 |
| `POST /api/orders/{id}/cancel` | 仅 `PENDING_PAYMENT` / `PAID` 可取消；`CREATING` 不可取消（由扫描兜底） |
| MQ 事件 | 内部契约，不对外暴露；rag_helper 无需改动 |

---

## 5. 关键设计取舍汇总

| 决策 | 取舍 | 代价 / 边界 |
|---|---|---|
| 单体 + 单模块 | 构建快、边界清晰、调试简单 | 不能水平扩容；多实例问题延后 |
| 库存两层防线（Redis 预扣 + DB 条件更新） | 挡流量 + 保证最终真相，并发不超卖 | 同一 SKU 行串行更新带来约百 ms 级延迟（实测 300ms 内） |
| 缓存与数据库不同事务 | 缓存不参与回滚，必须手动回补 | 回补只补已存在的键，防止凭空造库存 |
| 提交令牌 + 条件更新 | 防重复提交与重复状态流转 | 前端必须先领令牌，流程多一步 |
| 越权一律 404 | 不泄露数据存在性 | 前端要按「不存在」处理而非「无权限」 |
| JWT 只放 userId | 角色变更立即生效（含禁用 / 审核通过） | 每次请求多一次身份推导（带 60 秒缓存） |
| 图片同源相对路径 | 换 IP / 换域名不裂图 | 反代规则成为部署必需项 |
| AI 客服走 gRPC + 内部回调 | 浏览器不触达 agent，模型不持有身份 | 多一层票据与事件映射；agent 必须在内网 |
| 动作 prepare / 确认 / Java 执行 | 资损风险不在模型手里 | 多一套 `agent_actions` 状态机与内部事件 |
| 售后单独立模型 | 一个订单可有多条售后 / 支持按项部分退款 / 保留处理时间线 | 多两张表 + 状态机；与旧整单退款并存一段时间 |
| 部分退款优惠等比分摊、整单退完才退券 | 金额可解释、避免券重复使用 | 尾差要靠「最后一次退款」兜住；用户看到的退款额可能不是整项原价 |
| 售后超时用 `@Scheduled` 扫描 + Redis 锁 | 规则可频繁调、无需引入新 MQ 拓扑 | 单实例假设；处理延迟上限 = 扫描间隔 |
| 下单改为 D1 半同步占位 + 异步补账 | 缩短响应、保留订单立即可查（读己所写）；MQ 可重试 | 多两个订单状态、消费幂等与补偿、失败后置；`async=false` 可退回同步链路 |
| 变量：不用事务 Outbox，用 CREATING 扫描重投兜底 | 不新增 outbox 表与 relay，改动面小 | 事件非严格一次；依赖扫描兜底窗口（默认 2 分钟） |
| 文档分四层（requirements / architecture / implementation / HANDOFF） | 每份文档读者明确，减少互相漂移 | 改动要按分类落到对的文档（见 §6 维护规矩） |

---

## 6. 决策记录（D1–D34）

| # | 决策 | 理由 | 现状 |
|---|---|---|---|
| D1 | 单体 + 单模块 Maven | 练手项目重心在业务建模与 gRPC；分布式治理不是目标 | 一直沿用 |
| D2 | 技术栈定稿：Boot 4.1.1 + MyBatis-Plus + Flyway + MySQL/Redis/MinIO | 本机中间件已就绪、生态成熟；选型风险已在 M0–M1 实测 | 已在生产链路使用 |
| D3 | 中间件复用 + `its-my-go` 命名 + Redis key 必须 TTL | 本机多项目共用；`volatile-lru` 下无 TTL 的写入会直接失败 | 硬约束，有回归用例守着 |
| D4 | 库存：Redis Lua 预扣 + `WHERE stock >= ?` 条件更新 | 「先查再改」是并发丢更新的经典错误 | 已落地，压测 10 抢 5 恰好 5 成功 |
| D5 | 缓存三防：布隆过滤（穿透）/ 互斥锁（击穿）/ TTL 抖动（雪崩） | 不让请求打到 DB 才是缓存存在的意义 | 已落地，均有测试 |
| D6 | 下单提交令牌（一次性、原子消费） | 防重复提交不能靠前端 | 已落地，前端进结算页就领 |
| D7 | 注解式限流（AOP + Redis 固定窗口） | 统一入口、可一键关闭止血 | 已落地，`itsmygo.ratelimit.enabled` |
| D8 | 订单快照（商品名 / 单价 / 图片 / 收货信息冗余） | 商品改价、用户改地址不能改变历史订单 | 已落地；空图只对拍照兜底 |
| D9 | 多商户按店拆单：一个事务 + 事务回调回补 Redis | 提交阶段失败不会进业务 catch | 已落地，含券的全局去重与顺序校验 |
| D10 | JWT 只带 userId，角色按库实时推导；越权 404 | 旧 token 不能继续持有已撤销的角色 | 已落地，60 秒缓存 + 变更 evict |
| D11 | 超时关单用 RabbitMQ TTL + 死信，DB 扫描兜底 | 队列级 TTL 无队头阻塞；不依赖 MQ 可用性 | 从 RocketMQ 迁来；队列参数变更需删队列 |
| D12 | 聊天 / 客服流用 SSE + 一次性票据；单实例注册表 | EventSource 带不了 Authorization | 单实例假设，见 requirements §6 |
| D13 | 图片 URL 同源相对路径（`MINIO_PUBLIC_ENDPOINT=/`） | 绝对地址会把主机名固化进库，IP 一变全裂 | 2026-11-05 已迁移并验证 |
| D14 | AI 客服：Java 调用方 + 动作 prepare/确认/执行 + 卡片 Java 为准 | 资损与身份都不交给模型 | 已落地，真实链路联调完成 |
| D15 | 文档分四层并归并旧文件（本次） | 原 decisions-ecommerce 同时承担需求 / 选型 / 路线，边界不清 | 本次改造，见 §6 后维护规矩 |
| D16 | 售后单独立建表（`after_sales` + `after_sale_items`），不继续在 orders 上加字段 | 一单可多售后、支持部分退款与时间线；订单状态无法表达多路售后 | 已落地（V31） |
| D17 | 退款金额：项单价 × 数量，优惠按订单等比分摊，尾差归最后一次；**券仅整单退完才退回** | 金额可解释、对账简单；部分退款退券会造成券重复使用 | 已落地，有单测 |
| D18 | 商家超时用 `@Scheduled` 扫描 + Redis 锁（48h 自动同意 / 7 天自动确认收货） | 售后量小、规则可能频繁调；不引入新 MQ 拓扑 | 已落地，参数可配 |
| D19 | 退货物流走演示单号手填 + 商家确认收货 | 与现有虚拟物流一致，不接真实快递 | 已落地 |
| D20 | 旧整单退款接口保留、新售后单并存 | 历史数据、既有测试与 AI「催退款」依赖 `REFUNDING`；一次性切换风险大 | 并存期；后续统一下线（技术债 §7） |
| D21 | 下单采用 **D1 半同步占位 + 异步补账**，不采用纯异步落库 | 纯异步会让“读己所写”消失、支付门槛与失败不可见；占位订单同步落库保住可查性，把可重试写移出响应路径 | 已落地（M31，2026-11-06）；`async=false` 可退回同步链路 |
| D22 | 异步补账的可靠消息**不用事务 Outbox**，用「MQ 至少一次 + 消费幂等 + `CREATING` 超时扫描重投」 | 不引入 outbox 表、relay 与清理任务，复杂度可控；扫描本身兼具对账作用 | 已落地（M31）；代价是事件非严格一次、最坏延迟 = 扫描间隔 |
| D23 | 新增 `CREATING` / `CREATE_FAILED` 两个订单状态，支付 / 取消 / 退款只对既有可操作状态开放 | 状态机显式化，避免“异步中间态”被误当成可支付订单；失败终态可带原因让用户重下 | 已落地（M31） |
| D24 | 后台站内通知不新增常驻 SSE：**30s 可见轮询 + 铃铛下拉**；SSE 仍只服务聊天页 | 与 2026-11-07 多标签连接池治理一致，避免每标签常驻连接；未读延迟 ≤30s 可接受 | 已落地（2026-11-08，V35） |
| D25 | **发货权归店铺**：管理端只代发平台自营店（`merchant_id=1`）订单；第三方店铺订单管理员发货直接 403，前端不显示发货按钮；退款/售后仍保留平台介入 | 避免管理员替第三方商家发货造成权责错乱；自营店由平台运营，仍需管理端代发 | 已落地（2026-11-08） |
| D26 | 权限模型采用「**固定用户类型 + 管理端 RBAC1（子角色-权限码）+ 现有对象归属校验**」；可自定义角色 / Casbin / OPA / Keycloak / ReBAC 留到以后 | 复用现有 JWT + `AuthIdentityService`，成本低、可审计、与归属校验衔接自然；当前角色少、规则稳定，策略引擎/外部 IdP 收益不足 | P1 后端 + P2 基础前端已落地（2026-11-08，V36，开关默认关）；P2.1 邀请选角色已确认不做，开关保持关闭 |
| D27 | 长期下架商品采用「**自动归档 → 无引用才物理清理**」；默认关闭 + dry-run，先出报告再执行；图片暂不删 | 订单/售后快照、评价、问答、收藏都引用商品，物理删除会断链；归档可恢复且已满足“清理无效商品”的诉求；dry-run 避免误删 | 已落地（2026-11-08，V37，默认关） |
| D28 | **已支付订单不允许取消**，退款统一走售后（REFUND_ONLY/退货退款）；只有 `PENDING_PAYMENT` 可取消 | 取消只回滚库存/券、不产生退款流水，会形成“已付款又已取消”的坏账；售后已有审批/退款/通知链路，复用成本最低 | 已落地（2026-11-08） |
| D29 | 登录页演示账号提示改为构建期开关 `VITE_SHOW_DEMO_HINT`（DEV 默认显示，交付构建默认不显示）；演示账号固定 CUSTOMER，生产只保留 bootstrap 管理员 | 演示提示 + 弱密码账号混入交付库会成为管理员入口；构建期剔除最彻底，也保留 demo 演示能力 | 已落地（2026-11-08） |
| D30 | 商品硬删除加引用保护（订单/售后/购物车/评价/问答/收藏/足迹 → 409），与自动清理共用 `ProductReferenceChecker` | 物理删除有引用商品会导致历史数据孤儿；共用判断避免两处规则不一致 | 已落地（2026-11-08） |
| D31 | 运营“直达详情”采用**列表页 + 查询参数自动开抽屉/弹窗**，不新增独立详情路由；通知 link 直接带业务主键 | 现有详情都在抽屉/弹窗里，新增路由会复制页面与权限逻辑；query 参数改动小、可回退、历史 keyword 链接仍兼容 | 已落地（2026-11-08） |
| D32 | 商品「基础编辑」与「规格编辑」合并为一个弹窗（双页签 + 单保存），抽出 `SkuEditorPanel` 复用 | 两个弹窗来回切换、保存两次，运营容易漏存；合并后一个入口一次保存，逻辑仍复用原 SKU 编辑器 | 已落地（2026-11-08） |
| D33 | **RBAC P2.1（邀请管理员选子角色）与商户与商品运营二期（商品审核 / 券运营 / 对账）确认不做**；RBAC 开关保持默认关闭，二期需求移出路线图 | 用户明确不再投入；P2.1 缺口会让新管理员等同超管，因此不能开开关；商户二期不再排期，避免文档/评审继续挂着“未开始” | 已确认（2026-11-08） |
| D34 | 顾客端站内通知**复用 `notifications` 表与现有铃铛组件**，只补「订单已发货」「售后处理结果」两类事件；不建公告表、不做独立通知中心 | 表/接口/30s 轮询已具备，新增事件即可复用；公告属于运营内容、通知属于个人事件，混在一起会污染未读语义；移动端只加图标入口，避免再开页面 | 已落地（2026-11-09） |

**三条业务红线**（做任何功能都成立）：

1. **LLM 不直接写业务库** —— 只能「提议动作」，由 Java 校验并执行；
2. **风控不用 LLM** —— 规则判定，资损可解释、可复现；
3. **高危动作人工审批 + 全程审计** —— 退款审核、用户禁用、管理员变更都要留痕。

**维护规矩（防止文档再次漂移）**：

1. 需求 / 范围类改动只写 requirements；选型 / 架构 / 契约概览类改动只写 architecture；
   实现细节只写 implementation；**原 decisions-ecommerce 已删除，不要再新建重复的「需求 + 选型」文档**；
2. 状态、踩坑、操作步骤仍然只写 HANDOFF；
3. 契约以 `rag_helper/proto/agent.proto` 为准，本文 §4 只是说明性概览；改 proto 必须双端同步；
4. 四份文档若互相矛盾：状态以 HANDOFF 为准、实现以 implementation 为准，
   并回头修 requirements / architecture。

---

## 7. 已知限制与技术债

架构层面的已知问题只列在这里；完整清单（含高并发遗留项）见
[HANDOFF.md](../HANDOFF.md) §5 末尾。

| 限制 | 影响 | 备注 |
|---|---|---|
| 单实例 | SSE 在线状态、布隆预热、订单扫描都按单实例写 | 扩容前需 Redis Pub/Sub 或粘性会话 |
| 订单号冲突 | 高并发下会撞唯一索引，当前直接报错不重试 | 概率低，暂不处理 |
| 连接池默认 10 | 压测前必须调大 | 只在压测时用启动参数覆盖 |
| CI 无 MinIO | 4 条依赖 MinIO 的用例被跳过 | 已知缺口，非遗漏（`SKIP_MINIO_TESTS`） |
| 前端无自动化测试 | 回归依赖手工走查 | 每次发版前 `npm run build` + 浏览器过一遍 |
| 生产（演示）无 HTTPS / 备份 / 监控 | 内网演示可接受，不可当真实运营环境 | 缺口表见 deployment.md |
| 无集中日志 / 指标 / 链路追踪 | dev 看控制台或 `.dev-logs/`；prod 仅 `logs/its-my-go.log` JSON 文件（未采集），无 metrics / tracing | 演示可接受；接入方向见 §3.8 |
| 售后与旧整单退款并存 | 两套入口、两套状态语义（`REFUNDING` vs 售后单） | 旧接口仅兼容历史；新入口统一走售后单，后续下线（D20） |
| 售后退款无真实资金流水表 | 部分退款只累计 `orders.refunded_amount` + 售后单记录 | 演示环境可接受；接真实支付时需补退款流水表 |
| 售后无站内通知 | 用户只能看页面状态 | 列在 P3 / requirements §6 按需区 |
| 售后超时靠扫描 | 处理延迟上限 = 扫描间隔；单实例假设 | 参数可配；多实例时需分布式锁（现有 Redis 锁可复用） |
| 下单异步补账无 Outbox | MQ 发送失败/消息丢失时，靠 `CREATING` 超时扫描重投兜底，非严格一次 | 最坏延迟 = 扫描间隔（默认 2 分钟内重投）；接受“至少一次”语义 |
| `CREATING` 期间体验 | 订单可查但不可支付/取消，前端需轮询 | 正常窗口毫秒~秒级；补账失败会转 `CREATE_FAILED` 并显示原因 |
| async=false 降级模式 | 与异步模式是两条代码路径，测试需覆盖两者 | 默认测试用降级模式保持旧行为；异步路径单独测消费逻辑 |

---

## 8. 与详细实现文档的关系

- **本文**：选型、架构、边界、契约、决策 —— 读者是评审者、新加入者、要判断「为什么」的人；
- **[implementation.md](implementation.md)**：每条请求的流转、认证链路、每个机制的落地方式与坑
  —— 读者是改代码的人；
- 两者允许重复观点，但**细节只在 implementation**；本文若与 implementation 冲突，
  以实现为准并回头修本文。

---

## 9. 跨仓库协作（Java ↔ rag_helper）

**分工**：浏览器 ↔ Java 是 REST + SSE；Java ↔ agent 是 gRPC（问答链路）+ 内部 HTTP（工具回调）。
对接的「字段」以 rag_helper 的 `proto/agent.proto` 为准；「说明」在 rag_helper 的
`docs/architecture.md` §4；「前提」在其 `docs/requirements.md` §4。

**契约变更的固定顺序**（顺序错了必返工）：

1. 两边 HANDOFF 各读一遍现状；
2. **先定契约**（proto / 两个新文档的 §4）—— 改它 = 改双方；
3. rag_helper 侧实现 + 回归（`.venv/Scripts/python.exe -m app.core.regression`）+ `eval_judge check`；
4. Java 侧实现 + `./mvnw test`；
5. 两边分别同步文档（各自 requirements / architecture / implementation / HANDOFF）并 commit / push；
6. 端到端：`rag_helper/deploy.sh joint` → Java 侧真实聊一轮 / 上传一份 MD 验证导入。

> rag_helper 2026-11-05 做过一次文档归并：`evolution-plan.md` 与 `docs/decisions-evolution.md`
> **已删除**（内容并入 `docs/requirements.md` / `docs/architecture.md`）；原 evolution-plan
> §5 / §6 对应现 `docs/architecture.md` §4 与 `docs/requirements.md` §4，
> `proto/agent.proto` 字段未变。

**管理端代理三条线**（都不属于 gRPC 契约，新增时**不改 proto**，浏览器也不直连 agent）：

| 管理域 | Java 端点 | rag_helper 端点 | 密钥 |
|---|---|---|---|
| 知识库导入 | `/api/admin/knowledge/**` | 8001 `/upload`、`/documents`、`/status` 等 | `AGENT_IMPORT_KEY` |
| 智能体评测 | `/api/admin/agent-eval/**` | 8002 `/eval/**` | `AGENT_EVAL_KEY` |
| 知识缺口（2026-11-06） | `/api/admin/knowledge-gaps/**` | 8001 `/knowledge-gaps*`（5 个） | `AGENT_IMPORT_KEY` |

知识缺口的流程是「机器挖缺口 → **人工核实** → 发布入库」，rag_helper 侧的草稿接口只生成文本、
不落库；发布复用既有导入链路，返回 `taskId` 后由平台用知识库页的进度接口轮询。

