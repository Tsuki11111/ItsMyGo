# 一次买够（ItsMyGo）

单商户自营的**电子设备**网购平台。Spring Boot 单体 + Vue 3 前后端分离，
经 **gRPC 接入 RAG 客服智能体**（外部项目 `rag_helper`）。

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
| MySQL 8.0 | `mysql` | `localhost:3306`，库 `its_mygo` |
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
| **M3 购物车与下单** | ✅ **完成**（2026-10-15）—— **库存并发防超卖**、**缓存击穿/穿透/雪崩防护**、**Cache-Aside 缓存 + 实时库存接口**、订单状态机、订单快照，共 53 个测试全绿 |
| M4 支付与售后 | ⬜ 未开始 |
| M5 前端 → M7 查订单环回 | ⬜ 未开始 |

**交易闭环已通**：浏览商品 → 加购 → 下单（扣库存）→ 支付 → 发货 → 收货，以及取消回滚库存。

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
