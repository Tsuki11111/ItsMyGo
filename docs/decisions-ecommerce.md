# 已归并：决策记录（原 docs/decisions-ecommerce.md）

> **本文件内容已拆分归并，不再作为权威源维护。**
> 请不要在这里补内容 —— 改到下面两个文件，否则会立刻产生两份互相漂移的文档。

| 原章节 | 现位置 |
|---|---|
| §0 一句话、§1.1 定位与背景、§1.2 角色、§1.3 功能范围、§1.4 核心流程与状态机、§1.5 领域模型、§1.7 明确不做与变更记录 | [docs/requirements.md](requirements.md) §0–§2、§6 |
| §1.6 非功能需求 | [docs/requirements.md](requirements.md) §3（已按「高并发标准」重写，不再「降级」） |
| §2 技术选型（方案 A/B/C 对比、最终选型清单、中间件复用约定） | [docs/architecture.md](architecture.md) §2、§3.1、§6（决策记录 D1–D15） |
| §3 与智能体的对接契约（Java 侧必须遵守） | [docs/requirements.md](requirements.md) §4 + [docs/architecture.md](architecture.md) §4.2–§4.4；**字段权威仍是 `rag_helper/proto/agent.proto`** |
| §4 分阶段路线（M0–M7 最初规划） | [docs/requirements.md](requirements.md) §7（现状到 M29）+ [HANDOFF.md](../HANDOFF.md) §6（逐轮验收记录） |
| §5 待定问题（顾客 ID / deadline / 限流 / 支付形状 / SKU） | [docs/requirements.md](requirements.md) §8（均已定，结论见 architecture §6） |

**当前权威关系**：需求与范围 → [docs/requirements.md](requirements.md)；
选型与架构、接口契约概览 → [docs/architecture.md](architecture.md)；
状态 / 坑 / 操作 → [HANDOFF.md](../HANDOFF.md)；实现细节 → [docs/implementation.md](implementation.md)；
接口清单 → [docs/api.md](api.md)；跨仓库契约字段 → `rag_helper/proto/agent.proto`。

> 若确认不再需要旧链接，可用 `git rm docs/decisions-ecommerce.md` 删除本跳转页。
