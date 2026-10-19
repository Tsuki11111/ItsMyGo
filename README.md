# 一次买够（ItsMyGo）

[![Tests](https://github.com/Tsuki11111/ItsMyGo/actions/workflows/test.yml/badge.svg)](https://github.com/Tsuki11111/ItsMyGo/actions/workflows/test.yml)
[![Stars](https://img.shields.io/github/stars/Tsuki11111/ItsMyGo?style=social)](https://github.com/Tsuki11111/ItsMyGo/stargazers)
[![Last commit](https://img.shields.io/github/last-commit/Tsuki11111/ItsMyGo)](https://github.com/Tsuki11111/ItsMyGo/commits/main)

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![MinIO](https://img.shields.io/badge/MinIO-object%20storage-C72E49?logo=minio&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotcom&logoColor=white)

**多商户**网购平台：商家自助入驻、店铺商品、跨店购物车按店拆单分别支付，
平台管理员负责商户审核与全平台监管（**不做佣金 / 分账 / 结算**）。
Spring Boot 单体 + Vue 3 前后端分离，经 **gRPC 接入 RAG 客服智能体**（外部项目 `rag_helper`）。

**定位**：学习练手 / 简历项目，不是真实运营 —— 无真实支付、无高并发要求。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 4.1.1 · JDK 17 · **Spring MVC** |
| 数据访问 | **MyBatis-Plus** 3.5.17 —— ✅ 已在 Boot 4.1.1 上验证通过 |
| 数据库 | MySQL 8 —— 复用本机共用容器，库名 `its_mygo` |
| 缓存 | Redis 7 —— 复用本机共用容器，key 前缀 `its-mygo:`（**必须带 TTL**） |
| 表结构迁移 | Flyway 12.4.0 —— **须用 `spring-boot-starter-flyway`**，详见 [HANDOFF §5.1](HANDOFF.md) |
| 安全 | Spring Security 7 + JWT（jjwt 0.13.0，HS256，密码 BCrypt）—— ✅ 已接入 |
| 对象存储 | MinIO 8.5.17 —— 复用本机共用容器，bucket `its-mygo-product-images`，✅ 已接商品图上传 |
| 客服链路 | 原生 `grpc-java` + `protobuf-maven-plugin`（不用 starter，Boot 4 未适配） |
| API 文档 | springdoc-openapi |
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
```

**三条边界**（来自 `rag_helper` 的规划，本项目不能自行更改）：

1. 浏览器只跟 Java 说话
2. Agent 服务只在内网、只允许本单体访问
3. 知识库上传是 agent 服务的管理端行为，本平台不做

**对接契约**以 `rag_helper/evolution-plan.md` §5/§6 与 `rag_helper/proto/agent.proto` 为唯一权威。

## 部署方式

**全本地，不部署云。** 中间件**复用本机已有容器**，不另起：

| 中间件 | 容器 | 连接 |
|---|---|---|
| MySQL 8.0 | `mysql` | `localhost:3306`，库 `its_mygo`（测试用 `its_mygo_test`） |
| Redis 7 | `redis` | `localhost:6379`，key 前缀 `its-mygo:` |
| MinIO | `minio` | `localhost:9000`（Console `:9001`），bucket `its-mygo-product-images` |

> `mysql` 容器**没有 restart policy**，Docker Desktop 重启后需手动 `docker start mysql`。

## 进度

| 阶段 | 状态 |
|---|---|
| 需求分析 + 技术选型 | ✅ 完成（2026-10-12） |
| 本地中间件就绪 | ✅ 完成（2026-10-12） |
| M0 骨架 | ✅ 完成（2026-10-12） |
| M1 账号体系 | ✅ 完成（2026-10-12） |
| M2 商品与分类 | ✅ 完成（2026-10-13） |
| M3 购物车与下单 | ✅ 完成（2026-10-15）—— 防超卖、缓存三防、Cache-Aside、防重复提交、接口限流 |
| M4 支付与售后 | ✅ 完成（2026-10-16）—— 支付流水、退款审核、超时取消、原子状态流转 |
| **M5 前端** | ✅ **完成（2026-10-18）** —— 顾客端：商品列表（滚动加载 + 品牌专区）、商品详情（含多规格选择栏）、登录注册、购物车、结算下单（购物车 / 立即购买）、订单详情（含取消 / 确认收货 / 申请退款）、用户中心；管理端：分类、商品（含传图 / 上下架）、规格与 SKU 编辑器、订单（发货 / 退款审核 / 超时取消） |
| **M5.5 用户中心增强** | ✅ **完成（2026-10-18）** —— 收藏、浏览足迹、满减优惠券（领券中心 / 下单抵扣 / 取消退款退券）、商品评价（管理端可隐藏）；结算页支持选券抵扣 |
| **M5.6 多商户** | ✅ **完成（2026-10-19）** —— 商家入驻/审核、店铺主页、商品归属、商家商品/订单/店铺券/评价后台、跨店拆单分别支付、平台监管；不做佣金/分账/结算 |
| M6 客服接入 → M7 查订单环回 | ⬜ 未开始 |

**交易闭环已通**：浏览商品 → 加购 → 下单（扣库存）→ 支付 → 发货 → 收货，
以及取消、超时自动取消、退款（申请 → 审核 → 回滚库存）。
商品支持**多规格 SKU**（每个规格组合独立价格与库存），也支持「立即购买」直达结算。
**多商户**：商家入驻后管理本店商品/订单，跨店购物车按店拆单、分别支付；
**管理端**具备商户审核、分类维护、商品管理、发货与退款审核、平台券/店铺券、评价管理。

## 本地起来看看

两个终端：后端 `./mvnw spring-boot:run`，前端 `cd frontend && npm run dev`，
然后开 http://localhost:5173（演示账号 `testuser` / `123456`）。
完整启动清单（含中间件、管理员提权、常见问题）见 [start.txt](start.txt)。
**商家**：注册普通账号后访问 `/merchant/apply` 提交入驻，平台管理员在 `/admin/merchants` 审核。
管理端在登录后顶栏用户面板的「管理后台」或直接访问 `/admin`；
管理员账号先注册再提权：`UPDATE users SET role='ADMIN' WHERE username='testuser';`，
**提权后重新登录**。种子数据见 [sql/seed-data.sql](sql/seed-data.sql)，详见 [HANDOFF §3.4](HANDOFF.md)。

**CI**：每次 push 自动在干净环境跑测试（[`.github/workflows/test.yml`](.github/workflows/test.yml)）。
本地 167 条全绿，CI 跳过 3 条依赖 MinIO 的、跑 164 条（MinIO 已停止公开分发镜像，详见 [HANDOFF §5.12](HANDOFF.md)）。
**测试跑在独立的库** `its_mygo_test`（Redis 用 db 1），和开发数据互不影响 —— 见 [HANDOFF §5.20](HANDOFF.md)。

各里程碑的可验收产物见 [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md) §4。

## 本地运行

中间件复用本机已有容器，**开工前先确认它们都在跑**（Docker Desktop 重启后不保证自动拉起）：

```bash
docker ps --filter "name=^(mysql|redis|minio)$" --format "table {{.Names}}\t{{.Status}}"
docker start mysql redis minio     # 缺哪个就拉哪个
```

然后启动应用：

```bash
./mvnw spring-boot:run
```

凭据放项目根目录的 `.env`（从 `.env.example` 复制填写），该文件已被 `.gitignore` 排除。

## 文档

| 文件 | 作用 |
|---|---|
| [HANDOFF.md](HANDOFF.md) | **本项目一切细节的权威源** —— 具体进度、下一步、踩过的坑 |
| [docs/implementation.md](docs/implementation.md) | **代码怎么实现的** —— 请求流转、认证链路、各模块实现要点与取舍 |
| [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md) | 需求分析与选型决策的完整理由、分阶段路线 |
| [CLAUDE.md](CLAUDE.md) | 给 AI 助手的项目约定与协作偏好 |

> 本 README 是 [HANDOFF.md](HANDOFF.md) 的**精简报告**。
> 两者内容允许重复（一个详细、一个简洁），**如有出入一律以 HANDOFF 为准**。
