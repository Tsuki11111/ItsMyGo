# 一次买够（ItsMyGo）

[![Tests](https://github.com/Tsuki11111/ItsMyGo/actions/workflows/test.yml/badge.svg)](https://github.com/Tsuki11111/ItsMyGo/actions/workflows/test.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Stars](https://img.shields.io/github/stars/Tsuki11111/ItsMyGo?style=social)](https://github.com/Tsuki11111/ItsMyGo/stargazers)
[![Last commit](https://img.shields.io/github/last-commit/Tsuki11111/ItsMyGo)](https://github.com/Tsuki11111/ItsMyGo/commits/main)

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4-FF6600?logo=rabbitmq&logoColor=white)
![MinIO](https://img.shields.io/badge/MinIO-object%20storage-C72E49?logo=minio&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotcom&logoColor=white)

**多商户**网购平台：商家自助入驻、店铺商品、跨店购物车按店拆单分别支付，
平台管理员负责商户审核与全平台监管（**不做佣金 / 分账 / 结算**）。
Spring Boot 单体 + Vue 3 前后端分离，经 **gRPC 接入 RAG 客服智能体**（外部项目 `rag_helper`）。

**定位**：学习练手 / 简历项目，不是真实运营 —— 无真实支付；但**按高并发标准做**
（用户 2026-10-15 要求，缓存 / 并发 / 一致性不因「没流量」降要求，清单见 [HANDOFF §5 末尾](HANDOFF.md)）。

---

## 界面预览

**商城首页（顾客端）**

<img src="docs/screenshots/home.webp" alt="商城首页" width="100%">

**商品详情 · 商品评价与问大家**

<img src="docs/screenshots/product-community.webp" alt="商品详情与问大家" width="100%">

**管理后台 · 数据概览**

<img src="docs/screenshots/admin-dashboard.webp" alt="管理后台数据概览" width="100%">

**订单详情与物流轨迹**

<img src="docs/screenshots/order-logistics.webp" alt="订单详情与物流轨迹" width="100%">

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 4.1.1 · JDK 17 · **Spring MVC** |
| 数据访问 | **MyBatis-Plus** 3.5.17 —— ✅ 已在 Boot 4.1.1 上验证通过 |
| 数据库 | MySQL 8 —— 复用本机共用容器，库名 `its_mygo` |
| 缓存 | Redis 7 —— 复用本机共用容器，key 前缀 `its-my-go:`（**必须带 TTL**） |
| 表结构迁移 | Flyway 12.4.0 —— **须用 `spring-boot-starter-flyway`**，详见 [HANDOFF §5.1](HANDOFF.md) |
| 安全 | Spring Security 7.1 + JWT（jjwt 0.13.0，HS256，密码 BCrypt）—— ✅ 已接入 |
| 对象存储 | MinIO 8.5.17 —— 复用本机共用容器，bucket `its-my-go-product-images`，✅ 已接商品图上传 |
| 消息队列 | RabbitMQ 4（AMQP + 管理台）—— 超时未支付订单自动取消，✅ 已接 |
| 客服链路 | 原生 `grpc-java` + `protobuf-maven-plugin`（不用 starter，Boot 4 未适配） |
| API 文档 | **未集成 springdoc**；接口清单见手写的 [docs/api.md](docs/api.md) |
| 日志 | SLF4J + Logback；控制台带 traceId/userId，prod 输出 JSON 文件并保留 14 天 |
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router |
| 构建 | Maven（**单模块**） |

> ⚠️ **Spring Boot 4 的坑不少** —— 自动配置被拆成独立模块（Flyway 要换官方 starter）、
> Jackson 升到 **3**（包名从 `com.fasterxml.jackson` 变成 `tools.jackson`）、
> `AutoConfigureMockMvc` 换了包路径。动手前先读 [HANDOFF §5](HANDOFF.md)。

## 架构

### 1. 形态：模块化单体 + 外部 Agent

```
浏览器 ──HTTPS──> ItsMyGo Java 单体（REST / SSE / 权限 / 业务）
                    ├─ Vue 3 SPA（开发 5173，部署由 Nginx 托管）
                    ├─ MySQL / Redis / MinIO / RabbitMQ
                    └─ gRPC 8003 ──> rag_helper Agent（买小购）
                                        └─ HTTP + X-Agent-Ticket
                                           → Java /internal/agent/**
```

- **单体优先**：单模块 Maven、一个进程、一个数据库；先把交易闭环、并发一致性和 Agent 接好，不拆微服务。
- **前后端分离**：Vue 3 + Vite + Element Plus + Pinia；Nginx 同源反代 `/api`、商品图和 `/rag-images`。
- **Agent 外置**：客服智能体在 `rag_helper`（Python/LangGraph），Java 是调用方；浏览器不直连 Agent。

**三条边界**（来自 `rag_helper`，本仓库不能自行改）：

1. 浏览器只跟 Java 说话；
2. Agent 只在内网、只允许本单体访问；
3. 知识库导入与智能体评测由 Java 管理端代理，浏览器不直连 Agent。

### 2. 代码分层

```
controller/  46 个   参数接收、响应包装；不写业务判断
service/     27 个接口 + impl/   业务规则、事务、越权校验
mapper/      32 个   MyBatis-Plus BaseMapper + 少量聚合 SQL
entity/      35 个   表实体 + 状态枚举
dto/                请求/响应体，按 agent / eval / knowledge 分组
common/              Result/异常/审计/RateLimit/Agent 客户端/traceId
security/            JWT、CurrentUser、AuthIdentityService
config/              Security、MyBatis-Plus、MinIO、RabbitMQ、Agent
```

约定：Controller 薄、Service 厚；取当前用户统一用 `@AuthenticationPrincipal CurrentUser`；**越权校验只在 Service 做，别人的数据一律返回 404，不用 403**。

### 3. 领域模块

| 模块 | 负责什么 |
|---|---|
| 认证与用户 | 注册/登录、JWT、身份快照、地址、头像 |
| 商品与类目 | 商品/SKU/规格、分类、品牌、搜索、评价、问答、收藏、足迹 |
| 购物车与优惠券 | 按 SKU 加购、勾选、领券、门槛/有效期/每店券校验 |
| 订单与支付 | 下单、拆单、支付、取消、发货、收货、完成、CSV 导出 |
| 库存与并发 | Redis Lua 预扣、DB 条件扣减、SKU 调库、补账/回滚 |
| 售后与履约 | 仅退款/退货退款、审核、寄回、确认收货、超时自动处理、物流 |
| 商家与结算 | 入驻审核、商家商品/订单/售后/优惠券/数据看板 |
| 消息与通知 | 站内通知、未读角标、聊天 SSE、聊天图片 |
| AI 客服 | gRPC 桥接、事件流转 SSE、订单/商品卡片、动作草稿与确认 |
| 运营后台 | 用户/商家/商品/订单/售后/评价/优惠券/知识库/评测/审计 |

### 4. 一次下单的主链路

```
POST /api/orders（带提交令牌）
  → 身份/地址校验 → 商品、SKU、店铺、优惠券校验 → 按商户拆单
  → Redis Lua 预扣库存（挡流量）
  → 写 orders（CREATING + 金额/地址快照）+ order_items（商品快照）
  → 事务提交后发 MQ「订单已创建」，返回 CheckoutView（创建中）

异步补账（MQ 消费，一个事务）
  → DB 条件扣库存：UPDATE product_skus SET stock = stock - ? WHERE stock >= ?
  → 优惠券条件占用 → 按快照清购物车 → orders CAS CREATING → PENDING_PAYMENT
  → 提交后为每张订单发延迟关单消息（30 分钟）
  → 任一步失败：回滚库存/券 + orders 置 CREATE_FAILED（写原因）
```

- 超时未支付：RabbitMQ TTL + 死信 → `cancelIfExpired`（状态 + `NOW()` 幂等）→ 回滚库存和券。
- MQ 不可用：`OrderTimeoutScanJob` 兜底；`CREATING` 超时：`OrderCreationScanJob` 重投，仍失败转 `CREATE_FAILED`。
- `itsmygo.order-create.async=false` 时退回同步补账，便于测试/无 MQ 环境；行为与旧链路一致。

### 5. 一致性与高并发

| 问题 | 做法 |
|---|---|
| 不超卖 | Redis Lua「先 GET 再 DECRBY」原子预扣；最终以 DB `stock >= ?` 条件更新为准 |
| 支付/下单幂等 | 提交令牌一次性消费；支付记录按订单唯一；通知 `biz_key` 唯一键 |
| 超时关单 | RabbitMQ TTL + DLX；DB 扫描兜底；状态与 `NOW()` 条件保证重复消费安全 |
| 异步失败 | `CREATING` 占位 + MQ 补账 + 扫描重投；失败写 `create_error`，可重试/回滚 |
| 缓存 | 商品/详情缓存带 TTL + 随机抖动；空值缓存、布隆过滤器、互斥重建防穿透/击穿/雪崩 |
| 限流 | `@RateLimit` + Redis 固定窗口；登录、下单、聊天等热点接口 |
| 事务后副作用 | 通知在事务 `afterCommit` 后写库，回滚不推送；发布/消费都带幂等条件 |
| 越权 | 服务层按 `userId/merchantId` 过滤；跨租户一律 404；管理端审计 `@AdminAudit` |

### 6. 售后与退款

```
PENDING_REVIEW ──同意(仅退款)──────────────> REFUNDED
      │   └──同意(退货退款)──> WAITING_RETURN ──买家填单号──> RETURN_SHIPPED
      │                                                     └──商家确认收货──> REFUNDED
      ├──驳回(必填理由)──> REJECTED（可再次申请）
      └──买家撤销──> CANCELLED
```

- 金额按订单项计算，优惠按整单**等比分摊**，尾差归最后一次退款；券只在整单全部退完时退回。
- 商家 48h 未审核自动同意；退货寄出 7 天未确认自动确认收货退款；扫描任务 + Redis 锁，不引入 MQ。
- 旧整单退款接口保留；新旧售后共用同一套库存/优惠券/支付流水补偿。

### 7. 数据与中间件

| 组件 | 用途 | 关键约定 |
|---|---|---|
| MySQL 8 | 交易、商品、用户、售后、通知、审计 | Flyway 单脚本只增不改，当前 schema **v38**；测试库 `its_mygo_test` |
| Redis 7 | 库存预扣、缓存、限流、令牌、票据、权限码 | key 前缀 `its-my-go:`，**必须带 TTL**；共用实例 `volatile-lru` |
| RabbitMQ 4 | 超时关单、订单创建补账 | 本项目自有 `its-my-go-rabbitmq`；TTL + DLX；消费失败不无限重投 |
| MinIO | 商品图、头像、聊天图 | bucket `its-my-go-product-images`；不碰 rag_helper 的桶 |
| rag_helper 存储 | 向量库/会话/知识库 | 在 Agent 仓库，Java 只通过 gRPC / 内部接口访问 |

### 8. 认证与授权

- JWT 里**只放 userId**；role / merchantId 由 `AuthIdentityService` 按数据库实时推导（60 秒缓存 + 变更 evict），旧 token 不会保留旧权限。
- 粗粒度：`SecurityConfig` 按路径前缀 `hasRole`；细粒度：管理端 `@PreAuthorize("hasAuthority('admin:...')")`。
- RBAC 已落 V36（4 个内置角色 + 权限码 + 角色映射），但 `itsmygo.rbac.enabled` **默认 false**：关闭时保持 ADMIN 全通；商家/顾客 v1 不迁移。
- 内部接口 `/internal/agent/**` 凭 `X-Agent-Ticket`（Java 每轮签发、Redis 10 分钟），只允许内网访问。

### 9. Agent 桥接

```
浏览器 → POST /api/agent/converse（SSE）
  → Java：登录校验 → 签发工具票据 → gRPC Converse
  → agent：输入护栏 → 主 Agent 编排 subagent → 必要时回调 Java 工具接口
  → Java：start/trace/delta/usage/final/error 逐条转 SSE
  → 前端：执行流 / 正文 / 商品卡；动作卡按 sessionId 从 Java 拉取
```

- 动作类只 `prepare`：Agent 生成待确认草稿 → Java 落 `agent_actions` → 用户确认 → Java 用服务端快照执行，模型不直接写业务库。
- 契约字段以 `rag_helper/proto/agent.proto` 为唯一权威；Java 不能自行改契约。

### 10. 部署拓扑

| 环境 | 入口 | 说明 |
|---|---|---|
| 开发 | 前端 5173 / 后端 8080 / agent 8001-8003 | 本机进程 + `dev-infra/` 共用中间件 |
| 演示 | `http://localhost:8088` | `./deploy.sh joint` 全栈 Docker，独立 `its-my-go-*` 卷 |
| 生产 | `http://10.184.171.106:8088` | 内网 VM 离线镜像包，`start.sh` / `status.sh` / `stop.sh` |

三种环境**只能同时启动一个**（见 HANDOFF §4.0.0）；完整命令、端口和环境变量见 [docs/deployment.md](docs/deployment.md)。

### 11. 已知边界

- 单实例部署；多实例会碰到 SSE 路由、本地任务状态和定时任务重复执行，暂不展开。
- RBAC 开关默认关闭；商家员工子账号、邀请管理员选角色已确认本期不做。
- 无集中 metrics/tracing/日志平台；有 `traceId` 和 JSON 日志，但未接 Prometheus/OTel/ELK。
- 前端只有 Vitest 纯逻辑测试，没有 Playwright/E2E；支付与物流为演示模拟。

完整限制与技术债见 [docs/architecture.md §7](docs/architecture.md#7-已知限制与技术债)。

## 部署方式（三选一）

> 三种环境**只能同时启动一个**，切换前先停当前环境（原因与停止方式见
> [HANDOFF.md](HANDOFF.md) §4.0.0）。开发环境的完整操作也收在本节，不再单列「本地开发」。

| 环境 | 一键启动 | 一键停止 | 入口 | 数据 |
|---|---|---|---|---|
| **开发**（本机进程 + 中间件） | `npm run dev:joint` | `npm run dev:joint:stop` | 前端 `5173` / 后端 `8080` / rag_helper `8001-8003` | 共享 MySQL `its_mygo`、`rag_helper-*` 卷 |
| **演示**（本地 Docker 全栈） | `./deploy.sh joint`（= `npm run deploy:joint`；不含客服用 `./deploy.sh`） | `./deploy.sh joint-down`（或 `./deploy.sh down`） | `http://localhost:8088` | 独立的 `its-my-go-*` 卷 |
| **生产**（内网 VM 离线包） | VM 上 `~/itsmygo-offline/start.sh` | VM 上 `~/itsmygo-offline/stop.sh` | `http://10.184.171.106:8088` | VM 上的 `its-my-go-*` 卷 |

### 开发环境（本机进程，热更新）

前置：Docker Desktop、`JAVA_HOME`、rag_helper 的 `.venv`；rag_helper 路径用
`.rag-helper-dir` 文件或 `RAG_HELPER_DIR` 环境变量指定（默认 `../rag_helper`）。

本地基础设施（ItsMyGo + rag_helper 共用）统一收在仓库内的 **`dev-infra/`** 目录
（一个 compose 项目，Docker Desktop 只显示一个 `dev-infra` 分组）：

```bash
cd dev-infra
docker compose --env-file .env up -d      # mysql/redis/minio/mongo/milvus/rabbitmq/...
docker compose --env-file .env ps        # 查看状态
docker compose --env-file .env down      # 停止（external 数据卷不会删）
# neo4j 是可选组件，默认不启动；需要知识图谱时：
# docker compose --env-file .env --profile kg up -d neo4j
```

> 不要在 ItsMyGo 仓库根跑全量 `docker compose up`：那会再起一套
> `its-my-go-mysql` / `its-my-go-redis` / `its-my-go-minio`（独立数据卷、抢同样端口），
> 造成“两套数据”的错觉（2026-11-07 踩过，见 HANDOFF §5.41/§5.42）。
> 全量 `./deploy.sh` 只在“不用本机开发库”的全栈演示场景使用。

启动应用（二选一）：

```bash
# 方式 A：一键拉起开发环境（中间件 + ItsMyGo 前后端 + rag_helper 三进程）
npm run dev:joint            # 首次按提示配置 .rag-helper-dir / RAG_HELPER_DIR
npm run dev:joint:status     # 查看状态
npm run dev:joint:stop       # 停止应用进程（中间件保留）

# 方式 B：手动分进程
./mvnw spring-boot:run
cd frontend && npm run dev     # 另开终端
```

也可以在**仓库根**用 npm 转发（零依赖，脚本会自动选平台对应的命令）：

```bash
npm run dev:api                # = ./mvnw spring-boot:run（Windows 自动用 mvnw.cmd）
npm run dev                    # 另开终端，= cd frontend && npm run dev
npm run dev:joint              # 开发环境联合启动（dev-infra + 前后端 + rag_helper venv）
npm run dev:joint:stop         # 停止应用进程
npm run build                  # 前端生产构建 + 后端 jar（跳过测试）
npm run type-check             # 前端 vue-tsc
npm test                       # = ./mvnw test（后端全量）
npm run test:web               # 前端 Vitest（纯逻辑/组件测试）
```

打开 `http://localhost:5173`；本地开发库演示账号 `testuser` / `123456`（仅 DEV seed，生产不创建）。
中间件、RabbitMQ、客服智能体、管理员开通等完整清单见 [start.txt](start.txt)；
根目录 npm 脚本只是转发，真正的命令仍在 `mvnw` / `frontend/package.json` / `deploy.sh`。

### 演示环境（本机 Docker 全栈，8088）

```bash
./deploy.sh joint            # 一键：ItsMyGo + rag_helper 联合栈（Windows: .\deploy.ps1 joint）
npm run deploy:joint         # 同上，npm 入口
./deploy.sh                  # 只起 ItsMyGo（不含客服）

./deploy.sh joint-down       # 停止两套（保留数据卷）
```

首次会自动生成 `.env`、管理员密码并导入 44 个演示商品；想跳过演示数据用
`./deploy.sh up --no-seed`。`rag_helper` 仓的 `deploy.sh joint` 现在只转发到这里。

### 生产环境（内网 VM 离线包）

```bash
cd ~/itsmygo-offline
./start.sh                   # 一键启动（= install.sh，幂等：导入离线镜像 → 起两套栈 → 等 health → 库空时恢复数据）
./status.sh                  # 查看容器
./stop.sh                    # 停止两套（保留数据卷），清理构建缓存/悬空镜像
```

> `start.sh` 只负责启动/恢复，**不负责换镜像**；发版流程（`docker save` → scp →
> `docker load` → compose up）见 [docs/deployment.md](docs/deployment.md)。

端口、环境变量、手工 Compose、镜像源、生产 VM 缺口等细节见 [docs/deployment.md](docs/deployment.md)。

## 功能介绍

### 顾客端（商城）
- **账号与资料**：注册 / 登录（JWT）、头像、昵称、修改密码。
- **逛商品**：分类、品牌专区、关键词搜索、商品详情（多规格 SKU、实时库存、评价、问大家）。
- **购物车与下单**：跨店购物车按店铺分组、优惠券抵扣、按店拆单、一次支付多店订单。
- **订单与售后**：模拟支付、物流轨迹、确认收货；取消（仅未支付）、退款 / 售后（仅退款、退货退款、
  部分退款）、评价。
- **用户资产**：优惠券领取与使用、收藏、浏览足迹、个人消费报告。
- **消息与通知**：与商家实时聊天（SSE）、AI 客服「买小购」入口；商家消息与买家消息按工作台隔离；
  会话列表与聊天窗口带骨架屏加载，切换会话不闪空白；顶栏通知铃铛接收订单发货、售后处理结果，
  点击直达订单/售后详情。

### 商家端（商家后台）
- **入驻与店铺**：入驻申请、驳回后修改重提、店铺资料 / Logo / 发货地址。
- **商品**：商品与多规格 SKU 维护、价格与库存调整、上下架。
- **交易**：本店订单查看、发货、退款处理、售后处理。
- **经营**：优惠券、评价管理、经营看板、商家消息（仅商家收件箱）。

### 平台管理端
- **数据概览**：今日成交 / 订单 / 待发货 / 待退款 / 待审核商户 / 低库存；待办可直达详情。
- **商户与商品**：入驻审核、启停、经营详情、代设发货地址；商品 / 分类管理、批量上下架、
  图片回填、布隆重建、长期下架商品归档清理。
- **交易与售后**：订单查询 / 导出、平台自营订单代发货、退款审核、超时关单；售后单审核、原因字典。
- **用户与权限**：用户查询 / 禁用启用；管理员邀请与审批；RBAC 子角色 + 权限码（V36，默认开关关闭，
  P2.1 邀请选角色已确认不做，因此开关保持关闭）。
- **内容与运营**：优惠券、评价、客服会话、操作审计、站内通知（订单 / 售后 / 入驻 / 评测）。
- **AI 运营**：知识库导入、知识缺口人工核实发布、智能体评测（LLM-as-Judge）。

### AI 客服「买小购」
- Java 经 gRPC 调外部 RAG agent，SSE 回传答案 / 执行流（仅中文动作，不暴露工具名与参数）/ 商品卡。
- 查订单与物流、导购推荐、生成下单 / 退款草稿（高危动作仍需人工确认）、催发货 / 催退款；
  下单草稿支持在卡片内改规格，智能体换规格时会自动作废旧待确认卡。
- 会话内表单：退款原因、修改收货地址（结果经下一轮回传，Redis 一次性票据）。
- 客服偏好：智能推广 / 联网搜索 / 流式输出；智能推广按价格历史 + 相似购买 + 销量 / 评分推荐。
- 平台政策问答：用户协议 / 隐私 / 退换货与售后 / 商家规范以 MySQL 章节原文为准，
  Milvus 做语义召回；回答注明政策标题与章节，管理端知识库可分库查看政策。
- 三条边界：浏览器只跟 Java 说话、agent 只在内网、知识库 / 评测由 Java 管理端代理。

### 交易与工程底座
- **下单异步补账**：占位订单 CREATING + MQ 补账 + 扫描兜底，失败可重试 / 回滚。
- **库存**：Redis Lua 预扣 + DB 条件更新，防止超卖；支付幂等、超时关单。
- **缓存与限流**：商品缓存三防、布隆过滤器、接口限流、JWT 身份实时推导。
- **数据一致性**：订单 / 售后 / 退款金额 BigDecimal，越权统一 404；操作审计 + traceId 日志。
- **部署**：开发（本机进程）/ 演示（Docker 8088）/ 生产（VM 离线包）三套环境，一键启停。
- **不做**：真实支付、佣金 / 分账 / 结算、真实物流；完整边界见 requirements §6。

## 进度

| 阶段 | 状态 |
|---|---|
| 基础交易闭环 + 顾客 / 管理端前端（M0–M5.5） | ✅ 2026-10 |
| 多商户 + 在线客服 + AI 客服接入（M5.6–M13） | ✅ 2026-10 |
| 运营后台 + 商品社区 + 平台政策 + 压测（M15–M29） | ✅ 2026-10 ~ 11 |
| 售后 + 下单异步补账（M30–M31） | ✅ 2026-11 |
| 交付增强：站内通知 / RBAC P1·P2 / 商品归档 / P0 整改 / 编辑体验 / 质量整改 | ✅ 2026-11 |
| 已确认不做：RBAC 邀请选角色（P2.1）、商户与商品运营二期 | ⏸️ 移出范围 2026-11-08 |

- **交易闭环**：浏览 → 加购 → 按店拆单 → 支付 → 发货 → 收货；取消 / 超时 / 退款回滚库存与优惠券。
- **测试**：后端 **357 条**（CI 353 条，跳过 4 条依赖 MinIO 的用例）；前端 Vitest **13 条** + `type-check` + `build`。
- **压测基线**（2026-11-02）：读接口 2600–3500 QPS、分类 / 令牌 7000+ QPS、
  10 并发抢 5 件库存恰好 5 单、同一订单 5 并发支付只产生 1 条支付记录。
- 完整里程碑与验收记录见 [HANDOFF.md](HANDOFF.md) §6。

## 文档

| 文件 | 作用 |
|---|---|
| [docs/requirements.md](docs/requirements.md) | **需求分析** —— 产品定位、角色与场景、功能 / 非功能需求、平台对接前提、验收标准、范围外、里程碑与待定问题 |
| [docs/architecture.md](docs/architecture.md) | **架构选型** —— 技术栈与理由、总体架构、接口契约概览、关键取舍、决策记录 D1–D36、跨仓库协作 |
| [docs/implementation.md](docs/implementation.md) | **代码怎么实现的** —— 请求流转、认证链路、各模块实现要点与取舍 |
| [HANDOFF.md](HANDOFF.md) | **状态与坑的权威源** —— 具体进度、下一步、踩过的坑、环境与操作 |
| [docs/api.md](docs/api.md) | **后端接口文档** —— 全部接口、请求 / 响应、权限、限流与 SSE 事件 |
| [docs/deployment.md](docs/deployment.md) | **部署细节** —— 端口、环境变量、联合部署、手工 Compose、镜像源、**生产（内网 VM）环境与缺口** |
| [CLAUDE.md](CLAUDE.md) | 给 AI 助手的项目约定与协作偏好 |

> **文档分层**（2026-11-05 归并）：需求看 requirements、选型看 architecture、
> 「怎么做」看 implementation、「现在什么状态 / 哪里有坑」看 HANDOFF、接口看 api、部署看 deployment。
> 原 `docs/decisions-ecommerce.md` 已删除（内容全部并入上面两份，原文可用 `git log` 查）。
>
> 本 README 是 [HANDOFF.md](HANDOFF.md) 的**精简报告**。
> 两者内容允许重复（一个详细、一个简洁），**如有出入一律以 HANDOFF 为准**（需求以 requirements 为准）。
