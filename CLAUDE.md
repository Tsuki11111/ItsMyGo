# 项目约定（一次买够 · ItsMyGo）

## 项目是什么

面向多商户的电商交易平台「一次买够」：商品 / 店铺、跨店购物车、按商户拆单、支付、售后、优惠券、站内通知，配套 AI 客服「买小购」。个人练手 + 演示项目，分本机开发、Docker 演示、内网 VM 生产三套环境。**按高并发标准做**：缓存、并发、一致性选型不因「反正没流量」降低要求。

最重要的约束：MySQL / Redis / MinIO 是多项目共用的中间件，不能另起容器；本仓库公开，凭据绝不入库。

## 刚进目录先读什么

1. 读 [README.md](README.md) —— 项目是什么、技术栈、部署方式
2. 读 [HANDOFF.md](HANDOFF.md) —— 现在做到哪、下一步做什么、哪里有坑

顺序不要颠倒：README 给全局，HANDOFF 给落点。之后按问题选文档：

| 你的问题 | 读哪里 |
|---|---|
| 要做什么、做到什么程度、什么不做、怎么验收 | [docs/requirements.md](docs/requirements.md) |
| 选了什么技术、为什么、整体架构、契约、决策 D1–D36 | [docs/architecture.md](docs/architecture.md) |
| 每个机制具体怎么实现（认证、越权、库存、缓存、拆单…） | [docs/implementation.md](docs/implementation.md) |
| 接口清单、请求 / 响应、限流、SSE 事件 | [docs/api.md](docs/api.md) |
| 部署、端口、联合部署、生产 VM | [docs/deployment.md](docs/deployment.md) |

**要动代码之前必须先读 implementation.md**，别重复造已有的轮子。

> 原 `docs/decisions-ecommerce.md` 已于 2026-11-05 删除：需求并入 requirements、选型并入 architecture；需要原文用 `git log -- docs/decisions-ecommerce.md` 查。**不要再新建重复的「需求 + 选型」文档。**

## 设计原则（跨语言通用）

- 按职责 / 领域划分模块（controller / service / mapper / entity / dto / common / config / task），目录反映业务概念；一个模块只有一个变化理由。
- 依赖指向抽象：业务层不直接依赖框架、数据库、外部 HTTP；跨边界一律经接口 / 契约（如 `AgentGateway`、`StorageService`、`TokenIssuer`）。
- 变化点封装在边界后：外部系统、可替换策略放在接口后面，换实现不改调用方。
- 组合优于继承；行为与数据放在一起，避免上帝类 / 上帝模块。
- 跨模块只通过公开契约交互，不直接访问对方内部数据结构。
- 尊重语言惯用法：Java 用接口 + 构造器注入，Python（rag_helper）用 Protocol / ABC；不为了「像 Java」强行造类层次。

## 常用命令

- 装依赖：`./mvnw -q -DskipTests dependency:resolve` ｜ `npm --prefix frontend install`
- 本地运行：`npm run dev:joint`（中间件 + 前后端 + rag_helper 三进程）；或 `./mvnw spring-boot:run` + `cd frontend && npm run dev`
- 全部测试：`npm test`（= `./mvnw test`）；前端 `npm --prefix frontend run test`
- 单跑测试：`./mvnw test -Dtest=StockConcurrencyTest`
- 类型检查：`npm run type-check`
- 构建：`npm run build`（前端生产构建 + 后端 jar）
- 数据库迁移：`src/main/resources/db/migration/V{n}__*.sql`，Flyway 启动自动执行
- 起依赖：`docker start mysql redis minio`（缺哪个拉哪个）；`cd dev-infra && docker compose --env-file .env up -d`

## 架构（只写从代码里读不出来的）

- 请求链路：浏览器 → Vue SPA（Vite / Nginx 同源反代 `/api`）→ Spring MVC `controller` → `service` → MyBatis-Plus / MySQL；Redis 承担缓存、库存预扣、令牌、限流；RabbitMQ 负责超时关单；MinIO 存图片；客服经 gRPC 调 rag_helper，事件用 SSE 回浏览器。
- 关键约定：
  - 统一响应 `Result<T>`；异常由 `GlobalExceptionHandler` 转统一响应体
  - 认证：Spring Security + JWT，`JwtAuthenticationFilter` 解析，`AuthIdentityService` 按库实时推导角色
  - 外部调用统一经封装层，业务层不直接发请求
  - 测试目录与源码目录同构；测试配置在 `src/test/resources/application-test.yml`
- 生成代码 / 勿改目录：`target/generated-sources`（protobuf 生成）
- API 文档：`docs/api.md` + Apifox（项目 ID `9087916`）
- **技术栈已定**（Boot 4.1.1 + MyBatis-Plus + Flyway + MySQL / Redis / MinIO / RabbitMQ）：选型风险已在 M0–M1 实测通过，不要再回头质疑；真正会绊人的是 Boot 4 模块化差异，见 HANDOFF §5.1–§5.4、§5.9。

## 与默认习惯不同的约定

- 文档四层：requirements / architecture / implementation / HANDOFF；**状态与坑只写 HANDOFF**，别再新建重复文档。
- 提交信息：Conventional Commits，描述中文；`type` 取 `feat`/`fix`/`docs`/`style`/`refactor`/`perf`/`test`/`build`/`ci`/`chore`/`revert`，`scope` 用 `order`/`product`/`auth` 等短词。
- **改完代码主动同步文档，并自动 commit + push**，不等提醒。
- **收到用户补充的新约束，立刻写进文档**，不要只留在对话里。
- 分支：个人项目允许直接改 main。
- 语言：注释、文档、提交信息用中文；代码标识符用英文。

## 测试与环境

三套环境，入口统一收归本仓库：

- 开发：`dev-infra` 中间件 + 本机进程（`npm run dev:joint`）
- 演示：`./deploy.sh`（Docker 全栈，8088）；联合客服 `./deploy.sh joint`
- 生产（内网 VM）：`~/itsmygo-offline/start.sh`（详见 deployment.md）

数据分库：开发库 `its_mygo`、测试库 `its_mygo_test`（都在共用 `mysql` 容器里）。跑测试、造数据一律连测试库；`application-test.yml` 已把 MySQL 指向 `its_mygo_test`、Redis 指向 database 1。**禁止读写开发库。**

API 接口测试优先用 Apifox MCP：

- 接口按功能模块（目录）组织；本仓库新增 / 变更接口时，先更新 Apifox 文档，再生成 / 更新用例并跑通。
- 用例挂在**正向 / 负向 / 边界值 / 安全性**分类下：正常流、边界值、异常入参至少各一条。
- 测试环境「测试环境（测试库 8082）」指向 `http://localhost:8082`；该实例必须用 `its_mygo_test` + Redis db1 + **独立 JWT 密钥**，并关闭 MQ / 扫描，避免测试 token 打到开发库。
- 全量套件 ID `44197`；执行后附报告结果。

页面类改动仍以实际打开页面验证为准。

## 环境变量

- 根目录维护 `.env`（真实值，禁止提交）与 `.env.example`（键名 + 占位值，随代码提交）；两者缺失时主动创建。
- 创建时先盘点代码 / 配置中的全部配置项，生成 `.env.example`，再按本地 / 测试环境填写 `.env`。
- 新增或修改配置项时三处同步：代码默认值、`.env.example`、`.env`。
- `.gitignore` 必须包含 `.env`；`.env.example` 中不得出现真实密钥。
- 连接串、密钥一律从环境变量读取，禁止硬编码。

## Docker 与中间件

- **中间件复用，不另起容器**：`mysql`（库 `its_mygo` / `its_mygo_test`）、`redis`（key 前缀 `its-my-go:`）、`minio`（bucket `its-my-go-product-images`，别碰 rag_helper 的 `rag-helper-knowledge-files`）。`mysql` 里还有别的项目库（`dw` / `meta` / `pharma_db`），不要动。
- RabbitMQ 是本项目自有：容器 `its-my-go-rabbitmq`、命名卷 `its-my-go-rabbitmq-data`，端口 5672 / 15672，账号从 `.env` 的 `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` 读取。
- 本项目 Docker 资源名字一律以 `its-my-go` 开头，**不依赖 Compose 从目录名推导的默认前缀**（会得到 `itsmygo_`）。
- 按环境维护 compose：根 `docker-compose.yml`（应用全栈）、`dev-infra/docker-compose.yml`（本地共用中间件）；不要新增用途重复的 compose。
- 优先复用本机已有镜像，固定版本时在 compose 写 tag；数据卷显式命名；宿主机端口走环境变量，避开其他项目。
- **开工前确认 `mysql` / `redis` / `minio` 在跑**：`docker ps` 检查，缺哪个 `docker start`；`redis` / `minio` 的 restart policy 是 `no`，必须手动拉，`mysql` 是 `unless-stopped`（曾停摆过两个月）。
- Redis 以 `--maxmemory 256mb --maxmemory-policy volatile-lru` 运行：本项目所有 key **必须带 TTL**，否则内存打满时写入会直接失败。

## 关联仓库：rag_helper（客服智能体）

智能客服不在本仓库，在外部目录 **`D:\PycharmProjects\rag_helper`** —— Python 项目，RAG 客服智能体「买小购」，gRPC 服务端跑在 **8003**。涉及客服 / agent / gRPC 的改动，动手前先读那边：

| 读什么 | 为什么 |
|---|---|
| `proto/agent.proto` | **契约字段的唯一权威**；Java 侧由它生成 stub（`java_multiple_files = true`） |
| `docs/architecture.md` §4 | gRPC 接口 / 事件 / `Trace` / 内部回调 / 动作流程 |
| `docs/requirements.md` §4 | 平台对接前提：身份、票据、写操作边界、隔离边界 |
| `HANDOFF.md` | 那边的进度状态与踩坑 |

两者关系：浏览器只跟 Java 说话 → Java 经 gRPC 调 agent → 事件流转 SSE 回浏览器。Java 是调用方；agent 只在内网、只允许本单体访问；agent 需要数据时反向回调 Java 的 `/internal/agent/**`（凭证是 Java 每轮签发的短期票据，模型不参与身份）。**本仓库不能自行更改对接契约** —— 要改先跟用户确认，并同步改那边。那边的约定同样适用：中文回复、Conventional Commits、改完同步文档。

## 边界：这些事动手前先问我

- 改数据库 schema、删列、跑迁移（Flyway **只增不改**，已执行脚本改内容会启动失败）
- 改 CI、部署脚本、Dockerfile、升级依赖大版本
- 删除或修改公共 API 契约、鉴权 / 权限逻辑
- 涉及密钥、支付、用户隐私数据的代码
- 引入新依赖（先说明理由和一两个替代方案）
- 更改 rag_helper 的 gRPC 契约（要同步改那边）
- 高并发 / 一致性相关选型（本项目标准不降）

## 需求与设计门禁

新功能或大改（跨模块、改接口契约、改数据结构、改变现有行为）按 [HANDOFF.md](HANDOFF.md) **§4.9「新功能开发流程」** 走，不得跳步：

**先读需求 → 等用户敲定 → 补 requirements → 自评审需求 → 改 architecture → 写代码 → 改 implementation → 更新 HANDOFF / README → 自动 commit + push**

需求未敲定前不写代码（含原型与样例）；方案取舍写 architecture（D1–D36），实现细节写 implementation。**不要新建 `docs/design/` 或 `docs/adr/`**：本项目的设计取舍集中在 architecture 的决策记录里。小改动（bug 修复、文案、样式微调、依赖小版本升级）不走此流程，但仍须满足 DoD。

## 工作方式：小步迭代

- 一次只推进一个工作项，交付并验证一个可运行增量后，再开始下一个。
- 动手前确认验收标准（AC）：做到什么算完成；AC 含糊先问，不猜。
- 完成标准（DoD）：测试通过 + 新逻辑有测试 + lint / 类型检查无新增问题 + 改动不超任务范围。
- 接口改动用 Apifox MCP 更新用例并跑通；页面改动实际打开确认；附验证证据。
- 小步提交：一个逻辑自洽的改动一次提交，重构不混进功能改动。
- 每个增量结束汇报三件事：做了什么、怎么验证的、剩什么风险或待办。
- 范围外发现的问题记入 HANDOFF 的技术债 / 待办，不顺手修。
- 被打回或否决的改动，先理解原因再改，不重复同类错误。

## 文档地图（最小集）

- `README.md`：项目是什么、怎么跑（给人看）。
- `HANDOFF.md`：**当前状态与坑的唯一权威**；每次会话开始先读，完成一项更新状态与下一步。
- `docs/requirements.md`：需求分析——目标用户、使用场景、功能范围与验收标准。
- `docs/architecture.md`：选型理由、整体架构、契约对接、决策记录 D1–D36。
- `docs/implementation.md`：每个机制的具体实现（**代码的镜像，实现变了必须更新**）。
- `docs/api.md`：接口清单、请求 / 响应、限流、SSE 事件。
- `docs/deployment.md`：部署、端口、联合部署、生产 VM。
- 接口契约以 Apifox 为准，仓库内不重复维护；文档按需读取，不整份塞进上下文。代码变更时同步更新受影响文档——过期文档比没有更糟。

## 环境坑

- **Boot 4 模块化差异**：自动配置拆独立模块、Jackson 3 包名 `tools.jackson`、MyBatis-Plus 分页插件拆包、`spring-boot-starter-aspectj` 改名 —— 详见 HANDOFF §5.1–§5.4、§5.9。
- Windows PowerShell 5.1 对无 BOM 文件按系统 ANSI 解码，中文注释 / 字符串会乱码。
- `mysql` 曾停摆两个月；`redis` / `minio` 不会自动重启。
- 完整踩坑清单见 HANDOFF §5；**已知但尚未处理的高并发问题**见其末尾，动手前先看有没有已解决的。
