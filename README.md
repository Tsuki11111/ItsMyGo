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

```
浏览器 ──HTTPS──> Java 单体（登录、权限、页面、SSE 转发）
                    │  gRPC（内网，问答链路）
                    ▼
              Agent 服务（rag_helper）· 8003
                    │  HTTP 工具回调（X-Agent-Ticket，内网）
                    └──────────> Java /internal/agent/**
```

需要实时数据时（如查订单），agent 会反向回调 Java 的内部工具接口；
票据由 Java 每次对话签发，只走 gRPC metadata，不进模型上下文。

**三条边界**（来自 `rag_helper` 的规划，本项目不能自行更改）：

1. 浏览器只跟 Java 说话
2. Agent 服务只在内网、只允许本单体访问
3. 知识库导入与智能体评测是 agent 服务的管理端行为，由 Java 管理端代理，浏览器不直连 agent

**对接契约**：字段以 `rag_helper/proto/agent.proto` 为**唯一权威**；说明性概览见
`rag_helper/docs/architecture.md` §4，对接前提见 `rag_helper/docs/requirements.md` §4
（rag_helper 2026-11-05 文档归并后，原 `evolution-plan.md` §5/§6 已迁到这两个位置，旧文件已删除）。

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
- Java 经 gRPC 调外部 RAG agent，SSE 回传答案 / 执行流 / 商品卡。
- 查订单与物流、导购推荐、生成下单 / 退款草稿（高危动作仍需人工确认）、催发货 / 催退款；
  下单草稿支持在卡片内改规格，智能体换规格时会自动作废旧待确认卡。
- 会话内表单：退款原因、修改收货地址（结果经下一轮回传，Redis 一次性票据）。
- 客服偏好：智能推广 / 联网搜索 / 流式输出；智能推广按价格历史 + 相似购买 + 销量 / 评分推荐。
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
- **测试**：后端 **357 条**（CI 353 条，跳过 4 条依赖 MinIO 的用例）；前端 Vitest **11 条** + `type-check` + `build`。
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
