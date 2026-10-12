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
| 安全 | Spring Security + JWT（密码 BCrypt） |
| 客服链路 | 原生 `grpc-java` + `protobuf-maven-plugin`（不用 starter，Boot 4 未适配） |
| API 文档 | springdoc-openapi |
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router |
| 构建 | Maven（**单模块**） |

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
| Redis 7 | `redis` | `localhost:6379` |

> `mysql` 容器**没有 restart policy**，Docker Desktop 重启后需手动 `docker start mysql`。

## 进度

| 阶段 | 状态 |
|---|---|
| 需求分析 + 技术选型 | ✅ 完成（2026-10-12） |
| 本地中间件就绪 | ✅ 完成（2026-10-12） |
| **M0 骨架** | ✅ **完成**（2026-10-12）—— 4 个自动化测试全绿 |
| M1 账号体系 | ⬜ 未开始 |
| M2 商品与分类 → M7 查订单环回 | ⬜ 未开始 |

各里程碑的可验收产物见 [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md) §4。

## 本地运行

```bash
docker start mysql          # 共用 MySQL 容器无 restart policy，需手动拉起
./mvnw spring-boot:run
```

凭据放项目根目录的 `.env`（从 `.env.example` 复制填写），该文件已被 `.gitignore` 排除。

## 文档

| 文件 | 作用 |
|---|---|
| [HANDOFF.md](HANDOFF.md) | **本项目一切细节的权威源** —— 具体进度、下一步、踩过的坑 |
| [docs/decisions-ecommerce.md](docs/decisions-ecommerce.md) | 需求分析与选型决策的完整理由、分阶段路线 |
| [CLAUDE.md](CLAUDE.md) | 给 AI 助手的项目约定与协作偏好 |

> 本 README 是 [HANDOFF.md](HANDOFF.md) 的**精简报告**。
> 两者内容允许重复（一个详细、一个简洁），**如有出入一律以 HANDOFF 为准**。
