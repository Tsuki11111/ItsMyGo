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
> 归并说明：原 `docs/decisions-ecommerce.md` 的技术选型、中间件约定与对接契约并入本文；
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

### 3.2 一次下单的数据流

```
浏览器 → POST /api/orders（带提交令牌）
  → Security：JWT → AuthIdentityService 推导 role/merchantId
  → OrderService：校验令牌（原子消费）→ 校验商品/库存/券 → 按 store 分组拆单
      → StockService：Redis Lua 预扣（挡流量）
      → DB：UPDATE product_skus SET stock = stock - ? WHERE stock >= ?（最终真相）
      → 写 orders / order_items（快照）/ payments 的起点
      → 事务提交后：发送 RabbitMQ 延迟消息（30 分钟）；Redis 回补/令牌归还挂事务回调
  → 返回 CheckoutView（orders 数组 + 兼容字段）
```

超时未支付：RabbitMQ 延迟队列到期 → `cancelIfExpired`（状态 + 数据库 `NOW()` 幂等判断）→
回滚库存与券；MQ 不可用时 `OrderTimeoutScanJob` 兜底。

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

---

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
| 文档分四层（requirements / architecture / implementation / HANDOFF） | 每份文档读者明确，减少互相漂移 | 改动要按分类落到对的文档（见 §6 维护规矩） |

---

## 6. 决策记录（D1–D15）

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

**三条业务红线**（做任何功能都成立）：

1. **LLM 不直接写业务库** —— 只能「提议动作」，由 Java 校验并执行；
2. **风控不用 LLM** —— 规则判定，资损可解释、可复现；
3. **高危动作人工审批 + 全程审计** —— 退款审核、用户禁用、管理员变更都要留痕。

**维护规矩（防止文档再次漂移）**：

1. 需求 / 范围类改动只写 requirements；选型 / 架构 / 契约概览类改动只写 architecture；
   实现细节只写 implementation；**不要再往跳转页（旧 decisions-ecommerce）里补内容**；
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
> 已变为跳转页；原 evolution-plan §5 / §6 对应现 `docs/architecture.md` §4 与
> `docs/requirements.md` §4，`proto/agent.proto` 字段未变。
