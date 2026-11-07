# 本地开发统一基础设施（dev-infra）

把 ItsMyGo 与 rag_helper 开发时用到的所有基础设施容器，收进**一个 Docker Compose 项目**，
Docker Desktop 的 Containers 页面只显示一个 `dev-infra` 分组。

> 本目录在 ItsMyGo 仓库内：`docker-compose.yml` 与 `README.md` 入库；
> `.env`（凭据）和 `backup/`（数据库备份）已被仓库 `.gitignore` 排除，不会提交。

## 包含的容器

| 容器 | 端口 | 用途 |
|---|---|---|
| `mysql` | 3306 | ItsMyGo 开发库（`its_mygo` / `its_mygo_test`）+ 其他项目库 |
| `redis` | 6379 | ItsMyGo / rag_helper 共用缓存与任务状态 |
| `minio` | 9000/9001 | ItsMyGo 商品图 + rag_helper/Milvus 对象存储 |
| `its-my-go-rabbitmq` | 5672/15672 | ItsMyGo M31 下单异步补账 |
| `mongo` | 27017 | rag_helper 会话 / 运行记录 |
| `mongo-express` | 8081 | Mongo 管理界面 |
| `milvus-etcd` + `milvus-standalone` | 19530/9091 | rag_helper 向量库 |
| `attu` | 8000 | Milvus 管理界面 |
| `neo4j` | 7474/7687 | rag_helper 知识图谱 |
| `elasticsearch` / `kibana` / `qdrant` | 9200/5601/6333 | legacy（默认不启动，`--profile legacy` 才起） |

## 关键说明

- 所有数据卷都是 `external: true`，指向**原有已存在的卷**（如 `docker_mysql_data`、
  `rag_helper-mongo-data`、`its-my-go-rabbitmq-data`），切换容器**不会丢数据**；
- `docker compose down` **不会**删除 external 卷；
- 容器名、端口保持不变，两个项目的 `.env` / 脚本不用改；
- `.env` 保存与现有数据卷匹配的密码（MySQL / MinIO / RabbitMQ / Neo4j），
  **不要提交到 git**（本目录在 ItsMyGo 仓库内，`.env` / `backup/` 已加入 `.gitignore`）。

## 常用命令

```bash
cd dev-infra      # 仓库根 / ItsMyGo 下

# 启动全部基础设施
docker compose --env-file .env up -d

# 只起/重启单个
docker compose --env-file .env up -d mysql redis minio
docker compose --env-file .env restart rabbitmq

# 查看 / 停掉（保留数据卷）
docker compose --env-file .env ps
docker compose --env-file .env down

# legacy 设施（当前两个项目都不依赖）
docker compose --env-file .env --profile legacy up -d
```

两个应用仍按各自项目的方式本地跑：

```bash
# ItsMyGo
cd C:\Users\28470\IdeaProjects\ItsMyGo && ./mvnw spring-boot:run
cd frontend && npm run dev

# rag_helper（本地 venv）
cd D:\PycharmProjects\rag_helper
.venv\Scripts\python.exe -m app.import_process.api.file_import_service   # 8001
.venv\Scripts\python.exe -m app.agent.api.query_service                  # 8002
.venv\Scripts\python.exe -m app.agent.api.grpc_service                   # 8003
```
