# WisdomHub AI 模块收敛改造方案

> 版本：v1.2  
> 日期：2026-11-11  
> 范围：`wisdomhub-agent-engine`、`wisdomhub-web` AI 相关页面，以及 content / knowledge / search / notification 的 AI 契约  
> 定位：这是一份**收口路线图**，不是功能路线图。目标不是加更多 AI 能力，而是把现有 5 条链路收敛成一套契约、一份状态、一个入口、一套治理边界和一张会话图。  
> 修订记录：v1.2 将 AI-16 提前到 AI-02 之后（P0 内），同步更新上线顺序、验收、风险与里程碑；v1.1 新增 AI-16「会话图统一路由」与 §2.7 合图设计；v1.0 为初版。

---

## 0. 摘要

### 0.1 改造目标

1. **一套事件契约**：问答、学习路线统一通过 SSE 输出结构化数据（步骤、路线、引用），前端不再解析 Markdown。
2. **一份会话状态**：会话、消息、步骤、路线、引用统一落在 PostgreSQL，并提供读接口；前端不再依赖 localStorage 作为主存储。
3. **一个产品入口**：工作台、文章详情、技术详情、管理端共享同一套 AI 入口和页面上下文。
4. **一套治理边界**：标签/关系提案从聊天工具中拆出，AI 提案与系统升级提案统一管理。
5. **一张会话图**：问答与学习路线从 API 层的 `if` 分流收敛为单张 LangGraph 条件边；入口继续显式传 `workflow`，图内 `router` 只做确定性映射，不做意图识别。

### 0.2 非目标

- 不重写 `agent-engine`，不更换 LangChain / LangGraph / 模型供应商。
- 不引入 Milvus、独立向量库或新的中间件。
- 不恢复 B3.19 已撤销的“意图识别自动分流”，继续由前端显式选择 `workflow`。
- 不合并 Java 微服务，不改变“Python 只通过 `/internal/**` 访问业务数据”的边界。
- 不改变现有幂等、接管、SSE 空闲兜底、文章分析 claim 等已经验证过的机制。
- 不重写 QA / 学习路线的节点内部逻辑；AI-16 只合并拓扑与状态，不改变两条分支的业务行为。

### 0.3 一句话结论

当前 AI 不是“一个模块”，而是 5 条各自闭环的能力链路，外加一张会话图被拆成两半。最划算的改造顺序是：**先统一契约，紧接着合并会话图，再接入内容闭环，最后清理工程债**。

---

## 1. 现状诊断

### 1.1 五条 AI 链路

| 能力 | 入口 | 实现方式 | 数据落点 | 用户可见位置 |
| --- | --- | --- | --- | --- |
| 技术问答 | `/ai` → `POST /api/agent/chat`（SSE） | LangGraph ReAct + 知识图谱工具 | PG 会话/消息 | AI 工作台 |
| 学习路线 | 同一接口 `workflow=learning_path` | 先直连 knowledge 取前置链，再让 LLM 输出 Markdown | PG 会话（仅作上下文） | AI 工作台 + 技术详情页 |
| 文章分析 | MQ `article.created/updated` + 详情页懒触发 | 结构化输出 + claim/重试 | PG `ai_analysis_result` → MQ `knowledge.extract` → Neo4j | 文章详情 `AnalysisCard` |
| 标签/关系提案 | 聊天里的 `propose_tag` / `propose_relation`；另有 content 定时任务把未收录实体升级成提案 | LLM 自检 + 限流 + 提交 | content MySQL `tag_proposal` | 管理端提案收件箱 |
| AI 分析完成通知 | 应有 `ai.completed` 事件 | 消费端存在，生产端缺失 | — | 实际不会出现 |

### 1.2 关键代码位置

| 位置 | 说明 |
| --- | --- |
| `wisdomhub-agent-engine/api/chat.py:196` | 唯一的聊天 SSE 入口；`workflow` 字段决定走 QA 还是学习路线 |
| `wisdomhub-agent-engine/api/chat.py:129` | `get_chat_streamer` 在 API 层按 `workflow` 返回不同 graph，是双图分叉点 |
| `wisdomhub-agent-engine/api/sse.py:16` | 当前协议只有 `meta/delta/done/error` |
| `wisdomhub-agent-engine/graphs/qa.py:29` | QA LangGraph：agent ↔ 图谱工具 |
| `wisdomhub-agent-engine/graphs/learning.py:36` | 学习路线结构化 `route` 的构建逻辑 |
| `wisdomhub-agent-engine/graphs/learning.py:69` | 学习路线图：`fetch → agent`，与 QA 图独立编译 |
| `wisdomhub-agent-engine/agents/workflow.py:99` | 工作流流式包装；`steps` 只写进 `TurnTrace`，没有对外事件 |
| `wisdomhub-agent-engine/agents/workflow.py:176` | 按 `langgraph_node == "agent"` 过滤 token；合图时节点改名会直接影响它 |
| `wisdomhub-agent-engine/agents/analysis_job.py:36` | 文章分析 Job；MQ 与懒触发共用 |
| `wisdomhub-agent-engine/tools/java_api.py:112` | 知识图谱工具；`get_java_tools` 已无调用方 |
| `wisdomhub-agent-engine/tools/proposals.py:121` | 提案服务；440 行，是整个服务最大的文件 |
| `wisdomhub-agent-engine/app/main.py:82` | `java_tools` 实际混入了提案工具 |
| `wisdomhub-agent-engine/app/main.py:90` | `qa_graph` 与 `learning_graph` 分别构建，是双图并存的初始化点 |
| `wisdomhub-agent-engine/db/models.py:39` | `agent_message` 已有 `steps` JSONB |
| `wisdomhub-agent-engine/db/repository.py:192` | 只有“取最近消息给模型”的查询，没有会话列表/详情 |
| `wisdomhub-web/app/src/views/ai/AiWorkbenchView.vue:869` | “参考资料”由前端独立调搜索接口 |
| `wisdomhub-web/app/src/views/ai/AiWorkbenchView.vue:907` | 前端用正则从 Markdown 解析学习阶段 |
| `wisdomhub-web/app/src/views/ai/AiWorkbenchView.vue:1032` | 技术链接按名字回查标签 ID |
| `wisdomhub-web/app/src/views/tag/TechDetailView.vue:75` | 技术详情页传了 `workflow/technology` query |
| `wisdomhub-common/.../MqRoutingKeyConstants.java:55` | `ai.completed` 路由键 |
| `wisdomhub-notification/.../NotificationEventConsumer.java:137` | AI 分析完成通知消费逻辑 |
| `wisdomhub-search/.../InternalSearchController.java:35` | `/internal/search/recall`，目前 agent 未调用 |
| `wisdomhub-content/.../TagCandidatePromoteService.java:38` | 未收录实体 → 提案的系统侧升级 |

### 1.3 六个核心问题

#### 问题一：契约不统一，前端在替后端补契约

学习路线后端本来就有结构化 `stages` 和 `tagId`，但随后被序列化成 JSON 塞进 prompt，让模型写成 Markdown；前端再用正则解析阶段、按技术名回查标签 ID。模型换一种措辞，阶段锚点和参考文章就会失效。

工具轨迹 `steps` 已经写进 PG 的 `agent_message.steps`，但没有 SSE 事件、没有读接口，前端完全看不到 AI 调用了什么。

#### 问题二：AI 能力之间不共享数据

- 问答只查知识图谱，不查站内文章；
- 学习路线只查前置链；
- 文章分析产出的 `topic/knowledgePoints` 只在详情页卡片展示，只有 `entities` 被 knowledge-service 消费；
- 搜索召回接口存在，但 agent 从未调用；
- 提案走独立的内容服务链路。

图谱、搜索、分析结果、提案是四条互不相通的数据回路。

#### 问题三：会话有两份真相

PG 有完整的 conversation / message / steps，但没有会话列表和消息详情 API；前端把会话存在 localStorage，最多 30 个，换设备就丢。后端历史只服务模型上下文，前端历史只服务本地 UI，两边都不完整。

#### 问题四：入口和契约有断点

- 技术详情页的“让 AI 规划学习路线”传了 query，但工作台从未读取 `route.query`；
- 文章分析卡片是孤立模块，没有“就这篇文章问 AI”的衔接；
- `ai.completed` 有消费端、没有生产端，AI 分析完成通知是死链路；
- content 有未收录实体查询接口，前端没有对应管理页。

#### 问题五：治理逻辑混进聊天，且有清理债

- `proposals.py` 把 LLM 自检、限流、失败修正、标签名解析、payload 组装全部塞进 QA 工具；
- 提案依赖聊天请求里的 contextvar 获取用户身份，进程内限流，多实例口径不一致；
- `get_chat_model` / `get_analysis_model` 读取的 `app.state.chat_model` 从未初始化；
- `get_java_tools` 无调用方；
- `java_tools` 名字叫 Java 工具，实际包含提案工具；
- `pyproject.toml` 里有一个 2010 年的 `v2-theme` 包；
- 仓库没有 `tests` 目录，但 `.pytest_cache` 里还残留 `test_chat_api` / `test_sse_transport` 的用例名。

#### 问题六：同一条链路，两种编排入口

问答和学习路线在 API 层就分叉：`get_chat_streamer` 按 `workflow` 返回两张不同的图，`app/main.py` 分别 `compile()`。两张图的 state 不同（`QaState` / `LearningState`），prompt 不同，但流式包装、步骤提取、持久化又共用同一套 `WorkflowStreamer`，形成“上层分叉、下层复用”的错位。

后果不是功能缺失，而是结构上的：

- 增加第三种工作流，只能在 API 层继续加 `if`；
- 整条会话链路没有一张完整的图，trajectory / tracing 被切成两半；
- `route`、`steps` 只能由 streamer 从 `values` 里“猜”，而不是由图在确定的位置产出；
- 两张图共享节点名 `agent`，一旦合并，token 过滤、状态字段、prompt 选择会集中暴露。

这不是“功能碎”，是“编排入口碎”。

### 1.4 已有基础（保留，不重写）

- 会话幂等、接管与断线重放：`db/turn.py`、`agents/persistence.py`
- SSE 空闲兜底与终态收口：`api/sse.py`
- 上下文轮数/token 双预算：`agents/context.py`
- 文章分析 claim、版本比较与失败重试：`db/analysis_repository.py`
- Python → Java 内部调用 HMAC 签名与服务发现：`tools/internal_client.py`
- 前端 SSE 客户端与 401 单飞刷新：`api/sse.ts`

这些说明底层是认真做的，问题集中在拼装层和产品契约。

---

## 2. 目标架构

### 2.1 分层

```text
┌─────────────────────────────────────────────────────────────┐
│ 前端入口                                                    │
│ AI 工作台 / 文章详情 / 技术详情 / 管理端提案                  │
└───────────────────────────┬─────────────────────────────────┘
                            │ 统一 SSE + REST
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ agent-engine 接入层                                         │
│ /api/agent/chat、/api/agent/conversations、/api/agent/analysis│
└───────────────────────────┬─────────────────────────────────┘
                            │ 统一事件 + 统一任务模型
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 编排层                                                      │
│ Chat Graph（router → QA / Learning）/ Analysis Job / Proposal│
└───────────────────────────┬─────────────────────────────────┘
                            │ 工具按域分组
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 能力层                                                      │
│ knowledge_tools / search_tools / proposal_tools / content   │
└───────────────────────────┬─────────────────────────────────┘
                            │ 内部 HTTP + HMAC / MQ
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Java 服务                                                   │
│ content / knowledge / search / notification                 │
└─────────────────────────────────────────────────────────────┘
```

会话图只承载同步聊天；文章分析仍是独立的 MQ + claim 异步 Job，不塞进聊天图。

### 2.2 收敛原则

1. **后端是结构的唯一来源**：阶段、标签 ID、步骤、引用都由后端产出，前端只负责渲染。
2. **会话只有一份主数据**：PG 是主存储，localStorage 最多做草稿缓存。
3. **能力按域分组**：知识图谱、站内检索、提案各自成组，不再混在一个 `java_tools` 列表里。
4. **入口共享上下文**：工作台、文章页、技术页通过统一的 `pageContext` 传递上下文。
5. **治理与对话解耦**：提案是独立治理能力，聊天只是它的一个触发入口，不是它的归属地。
6. **分流下沉、入口显式**：API 层只做校验与参数装配，`workflow` 的分流由图内 `router` 节点完成；入口仍然显式声明模式，不做意图识别。

### 2.3 统一 SSE 事件

在现有 `meta/delta/done/error` 基础上新增三个事件，保持向后兼容：

| 事件 | 时机 | 作用 |
| --- | --- | --- |
| `meta` | 流开始 | 会话 ID、消息 ID、workflow |
| `route` | 学习路线取数完成后、首个 delta 前 | 结构化阶段与 tagId |
| `steps` | 工具开始/结束、或最终状态变化时 | 工具时间线快照 |
| `delta` | 模型逐 token 输出 | 正文增量 |
| `citations` | 回答结束前 | 真实检索到的站内文章 |
| `done` | 正常结束 | 终止事件 |
| `error` | 异常结束 | 终止事件 |

事件顺序约定：

```text
meta → (route)? → (steps)* → (delta)* → (citations)? → done
error 可在任意阶段出现并终止流
```

事件顺序与图的拓扑无关。AI-16 合图后仍保持同一顺序；学习路线的 `route` 由 `fetch` 节点的 `values` 快照触发，先于 `agent` 的 `delta`。

字段示例见第 4 节。

### 2.4 统一会话模型

在现有 `agent_conversation` / `agent_message` 基础上做最小扩展：

- `agent_message.workflow`：记录该消息属于 `qa` 还是 `learning_path`；
- `agent_message.artifacts`：JSONB，保存 `route` / `citations` 等结构化产物；
- `steps` 继续使用现有 JSONB 列；
- 新增会话读/删 API，前端以服务端为准。

不新增独立“AI 任务表”。文章分析仍使用 `ai_analysis_result`，聊天仍使用 `agent_message`，避免为了统一而统一。

### 2.5 统一能力层

| 工具组 | 内容 | 使用方 |
| --- | --- | --- |
| `knowledge_tools` | `query_knowledge_graph`、`query_prerequisite_chain` | QA、学习路线 |
| `search_tools` | `search_articles`（P1） | QA |
| `proposal_tools` | `propose_tag`、`propose_relation` | 独立治理入口或显式工具组 |

工具输出保持现有结构，不强行引入新的“工具结果协议”；只在编排层增加引用收集逻辑。

### 2.6 统一入口

| 入口 | 传递内容 | 行为 |
| --- | --- | --- |
| AI 工作台 | `workflow`、`technology`、`tagId` | 读取 `route.query`，预选模式/技术 |
| 文章详情 | `articleId`、可选 `selectedText` | “就本文问 AI”，带页面上下文 |
| 技术详情 | `tagId`、技术名 | 直接生成学习路线，不再按名字回查 |
| 管理端 | 提案 ID / 状态 | 查看 AI 与系统提案，统一审批 |

### 2.7 统一会话图（AI-16）

现状是“API 层分叉 + 两张独立编译的图”：

```text
POST /api/agent/chat
  → get_chat_streamer(workflow)
      ├─ "qa"            → qa_graph（agent ⇄ tools）
      └─ "learning_path" → learning_graph（fetch → agent）
```

目标是“一个入口 + 一张图 + 一次 astream”：

```text
START
  → router（读 state.workflow）
      ├─ "qa"            → qa_agent ⇄ tools → END
      └─ "learning_path" → learning_fetch → learning_agent → END
```

节点职责：

| 节点 | 职责 |
| --- | --- |
| `router` | 按 `workflow` 构造初始 messages（选择 QA / LEARNING prompt），把 `workflow`、`technology`、`known` 写入 state；不做意图识别 |
| `qa_agent` | 复用现有 QA agent，绑定 `knowledge_tools`（P1 加 `search_tools`），最多 3 轮工具 |
| `tools` | 现有 `ToolNode`，只服务 QA 分支 |
| `learning_fetch` | 复用现有 `fetch`：查前置链、构建 `route`、写 `fetch_status` |
| `learning_agent` | 复用现有 `agent`：按 `fetch_status` 输出兜底文案或讲解正文 |

状态设计：

```python
from typing import NotRequired

class ChatState(AgentState):
    workflow: str
    technology: NotRequired[str]
    known: NotRequired[list[str]]
    route: NotRequired[dict]
    steps: NotRequired[list[dict]]
    fetch_status: NotRequired[str]
    fetch_error: NotRequired[str]
    tool_rounds: NotRequired[int]
```

- 用“超集 state”换取单图；学习字段在 QA 轮为空，不参与判断；
- `workflow` 由请求显式传入，`router` 只做确定性映射，不做分类；
- `messages` 继续使用 `MessagesState` 的 reducer，历史注入方式不变。

关键约束：

1. **校验留在 API**：`technology` 必填、依赖可用性继续返回 HTTP 400/503；`router` 不承担协议错误，否则会退化成 SSE `error`。
2. **节点必须改名**：同一张图不能有两个 `agent`。token 过滤从 `langgraph_node == "agent"` 改为 `{qa_agent, learning_agent}` 集合，否则一条 token 都流不出来。
3. **prompt 组装进图**：现在由 `WorkflowStreamer` 按构造参数选择 prompt，合图后由 `router`（或各分支首节点）完成；streamer 不再持有 prompt / `extra_state`。
4. **`route` 事件时机**：单图下 `fetch` 的 `values` 快照先于学习分支的 `delta`，streamer 在这里发 `route`。
5. **`recursion_limit` 统一为 12**：学习分支远小于该值，QA 的 `MAX_TOOL_ROUNDS=3` 保持不变。
6. **`steps` 提取不变**：继续走 `_state_steps`，优先读 `state.steps`，缺失时回落到 `messages`。
7. **文章分析不进图**：仍是 MQ + claim 的异步 Job，避免把异步任务塞进会话图。

为什么不用“父图 + 子图”：

- 父子图可以复用两张已编译的图，但父图要等子图整体结束后才能看到输出，学习路线的 `route` 会晚于正文；
- 想提前拿到 `route`，就得开 `subgraphs=True` 并处理 `checkpoint_ns` 命名空间，复杂度和收益不匹配；
- 单图只多一个超集 state 和节点改名，事件时机更可控。

排期：

- 固定在 AI-02 之后立即执行（P0 内），与 AI-03 / AI-04 可并行；
- AI-01 / AI-02 / AI-16 分成三个独立提交：先事件、再结构化 route、最后合图，便于单独灰度和回滚；
- 不并入 AI-02：契约变更与拓扑变更保持可分离。

---

## 3. 改造任务清单

### 3.1 P0：统一契约、状态与会话图（打地基）

| ID | 任务 | 主要改动 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| AI-01 | 统一 SSE 事件模型 | `api/sse.py` 增加 `route/steps/citations` 构造器；`agents/workflow.py` 负责发射；`api/chat.py` 保持事件顺序 | — | 事件顺序符合 2.3；旧客户端忽略新事件不受影响 |
| AI-02 | 学习路线结构化直出 | `graphs/learning.py` 的 `route` 直接进入事件；前端按 tagId 渲染阶段 | AI-01 | 前端不再正则解析 Markdown；阶段技术链接直接跳技术详情 |
| AI-16 | 会话图统一路由 | 新增 `graphs/chat.py`；合并 `QaState/LearningState` 为 `ChatState`；节点改名与条件边；`WorkflowStreamer` 节点过滤改为集合；`api/chat.py` / `app/main.py` 改为单图 | AI-01/AI-02 | QA/学习路线行为不变；单次 astream；`route` 仍先于 delta；旧前端无感 |
| AI-03 | 会话读/删 API | 新增 `api/conversations.py`；`db/repository.py` 增加列表/详情/删除；`db/models.py` 增加 `workflow/artifacts`；迁移 `0005` | — | 列表/详情/删除可用；越权返回 404；分页正确 |
| AI-04 | 前端改用服务端会话 | `AiWorkbenchView.vue` 会话列表/消息从 API 加载；localStorage 降级为草稿缓存 | AI-03 | 刷新、换设备能恢复；删除同步 |
| AI-05 | 分析版本与完成通知 | `agents/analysis.py` 返回 `authorId`；`AnalysisJob` 发布 `ai.completed`；`api/analysis.py` 返回 `sourceUpdatedAt/analyzedAt`；`ArticleDetailVO` 补 `updateTime` | — | 作者收到一次通知；前端能显示分析版本 |
| AI-06 | 修复技术详情深链 | 工作台读取 `route.query`；`TechDetailView` 传 `tagId` | AI-02 | 点击“让 AI 规划学习路线”后目标技术已预选 |

### 3.2 P1：产品整合与知识闭环

| ID | 任务 | 主要改动 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| AI-07 | 站内检索工具 + citations | 新增 `tools/search_api.py`，调用 `/internal/search/recall`；QA 绑定；从工具结果收集引用 | AI-01 | 回答引用真实搜索结果；检索失败不影响回答 |
| AI-08 | 引用安全与付费墙 | 只把公开元数据（标题/摘要/标签）作为引用；若注入正文，先补 locked 判断 | AI-07 | 付费正文不进入 prompt 和引用 |
| AI-09 | 统一 AI 入口 | 文章页“问 AI”；`pageContext`；技术页 tagId；工作台读 query | AI-02/AI-04 | 从文章/技术页进入时带上下文 |
| AI-10 | 提案治理拆分 | `proposal_tools` 独立；管理端统一 AI/系统提案；补未收录实体页或删后端接口 | — | 聊天工具不混治理；管理员能区分来源 |
| AI-11 | 分析结果复用（可选） | 把 `topic/knowledgePoints` 作为检索/问答的辅助上下文 | AI-07 | 不改变现有卡片展示；有明确收益再上线 |

### 3.3 P2：工程清债与可观测

| ID | 任务 | 主要改动 | 依赖 | 验收 |
| --- | --- | --- | --- | --- |
| AI-12 | 清理死代码与依赖 | 删 `get_chat_model/get_analysis_model/get_java_tools`；移除 `v2-theme`；重命名 `java_tools` | — | 全仓无引用；锁文件更新 |
| AI-13 | 契约测试与回归 | 恢复 `tests/`；覆盖 SSE 顺序、会话 API、分析通知、学习路线 route、合图后单图 | AI-01~AI-05、AI-16 | CI 通过；新增契约测试 |
| AI-14 | Token 计量与评测 | `stream_options.include_usage`；记录 token/成本；建 50 条回归问题 | — | 每轮有 token 记录；评测可重复 |
| AI-15 | 错误兜底收口 | 统一 `guard_events`、`WorkflowStreamer`、学习图状态、`degradation` 的用户文案 | — | 同类错误在不同入口文案一致 |

### 3.4 最小闭环建议

如果只做一个迭代，优先完成 **AI-01 + AI-02 + AI-03 + AI-04**：

- 后端把已有结构直接发出来；
- 前端停止解析 Markdown、停止二次搜索；
- 会话从 PG 读，localStorage 退居缓存。

这四项不需要新增 AI 能力，却能消掉工作台里最大一块“补契约”的代码。按新排期，AI-16 紧跟在 AI-02 之后执行，可与 AI-03 / AI-04 并行；它不新增产品能力，但会扩大 P0 的改动面，必须独立提交、独立灰度。

---

## 4. 接口契约

### 4.1 SSE

所有 `data` 为单行 JSON，沿用现有 `encode` 规则。新增字段只增不改，旧客户端忽略未知事件。

#### meta

```json
{
  "conversationId": "0f3d9f8e-...",
  "messageId": "3f6c...",
  "workflow": "qa"
}
```

#### route

学习路线取数完成后立即发送，先于正文：

```json
{
  "target": { "tagId": "10", "name": "Redis" },
  "known": ["Java", "Spring Boot"],
  "truncated": false,
  "stages": [
    {
      "depth": 2,
      "tags": [
        { "tagId": "21", "name": "计算机网络" },
        { "tagId": "22", "name": "操作系统" }
      ]
    },
    {
      "depth": 1,
      "tags": [{ "tagId": "30", "name": "MySQL" }]
    },
    {
      "depth": 0,
      "tags": [{ "tagId": "10", "name": "Redis" }]
    }
  ]
}
```

#### steps

工具状态变化时发送完整快照，前端直接替换列表：

```json
{
  "steps": [
    {
      "id": "s1",
      "tool": "query_knowledge_graph",
      "label": "查询知识图谱",
      "status": "done",
      "summary": "返回 3 个关系组"
    },
    {
      "id": "s2",
      "tool": "search_articles",
      "label": "检索站内文章",
      "status": "running",
      "summary": ""
    }
  ]
}
```

`status` 取值：`running | done | error`。`summary` 只放对用户有意义的短句，不放工具原始 JSON。

#### delta

保持不变：

```json
{ "text": "Redis 的跳表主要用在..." }
```

#### citations

回答结束前发送，只包含公开元数据：

```json
{
  "items": [
    {
      "articleId": "123",
      "title": "Redis 跳表实现原理",
      "summary": "从 zset 的底层结构讲起...",
      "tags": [{ "tagId": "10", "name": "Redis" }]
    }
  ]
}
```

规则：

- 只来自本次回答实际调用的 `search_articles` 结果；
- 不包含正文，不包含付费内容；
- 没有检索结果时不发该事件，前端显示“暂无引用”。

#### done / error

保持现有字段不变。

### 4.2 会话 REST

#### 列表

```http
GET /api/agent/conversations?page=1&pageSize=20
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "conversationId": "0f3d9f8e-...",
        "title": "Redis 跳表为什么快",
        "updatedAt": "2026-11-11T10:00:00Z"
      }
    ],
    "total": 12,
    "page": 1,
    "pageSize": 20
  }
}
```

#### 详情

```http
GET /api/agent/conversations/{conversationId}
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "0f3d9f8e-...",
    "title": "Redis 跳表为什么快",
    "updatedAt": "2026-11-11T10:00:00Z",
    "messages": [
      {
        "messageId": "u-1",
        "role": "user",
        "content": "Redis 为什么使用跳表？",
        "status": "done",
        "workflow": "qa",
        "createdAt": "2026-11-11T09:59:00Z",
        "steps": null,
        "artifacts": null
      },
      {
        "messageId": "a-1",
        "role": "assistant",
        "content": "因为跳表在范围查询和实现复杂度之间...",
        "status": "done",
        "workflow": "qa",
        "createdAt": "2026-11-11T09:59:05Z",
        "steps": [
          { "tool": "query_knowledge_graph", "args": { "technology": "Redis" } }
        ],
        "artifacts": {
          "citations": [
            { "articleId": "123", "title": "Redis 跳表实现原理" }
          ]
        }
      }
    ]
  }
}
```

#### 删除

```http
DELETE /api/agent/conversations/{conversationId}
```

- 只允许删除自己的会话；
- 不存在或非本人一律返回 404，避免泄露会话是否存在；
- 依赖现有外键级联删除消息。

### 4.3 文章分析 REST

在现有响应上增加版本字段：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "articleId": "123",
    "status": "done",
    "topic": "Redis 跳表",
    "entities": ["Redis", "跳表", "zset"],
    "knowledgePoints": ["跳表与平衡树的取舍", "范围查询的实现"],
    "sourceUpdatedAt": "2026-11-11T08:00:00Z",
    "analyzedAt": "2026-11-11T08:01:00Z"
  }
}
```

- `sourceUpdatedAt` 对应文章快照的 `updateTime`；
- `analyzedAt` 对应分析完成时间；
- `trigger` 语义保持不变：仅无记录、失败或租约过期时补跑。

### 4.4 ChatRequest 扩展（P1）

```json
{
  "conversationId": null,
  "messageId": "3f6c...",
  "workflow": "qa",
  "question": "这篇文章里的缓存一致性怎么理解？",
  "pageContext": {
    "articleId": "123",
    "tagId": "10",
    "selectedText": "只截取用户选中的一小段，最多 2000 字"
  }
}
```

约束：

- `pageContext` 只作为提示，不作为可信输入；
- 服务端要校验文章可见性；
- 不把客户端传来的正文当作文章全文使用；
- 需要全文时由服务端重新取快照，并先做付费墙判断。

---

## 5. 数据模型与迁移

### 5.1 迁移 0005

```sql
ALTER TABLE agent_message
    ADD COLUMN workflow TEXT,
    ADD COLUMN artifacts JSONB;

ALTER TABLE ai_analysis_result
    ADD COLUMN notified_at TIMESTAMPTZ;
```

说明：

- `workflow` 只允许 `qa` / `learning_path` 或 NULL，可在应用层校验，暂不加 DB 约束，避免历史数据阻塞；
- `artifacts` 保存 `{ "route": {...}, "citations": [...] }`，不参与查询，不需要索引；
- `notified_at` 用于防止分析完成通知在重投时重复发送。

### 5.2 写入时机

- 用户消息：`workflow` 写入请求值，`artifacts` 为 NULL；
- assistant 占位行：`workflow` 写入请求值，`artifacts` 为 NULL；
- assistant 完成：`finish_assistant_message` 增加 `artifacts` 参数，一次写入 `steps` 与 `artifacts`；
- 分析通知：`KnowledgeExtractEvent` 发布成功且 `notified_at` 为空时，发布 `ai.completed`，成功后写 `notified_at`。

### 5.3 兼容旧数据

- 历史消息 `workflow/artifacts` 为 NULL；
- 前端遇到 NULL 时按纯 Markdown 渲染，不显示阶段/引用；
- 迁移可在线执行，不需要回填。

### 5.4 AI-16 无迁移

合图只改 Python 内部编排，不新增表、不新增列、不改变事件与 REST 契约，因此没有数据库迁移，也不需要数据回填。

---

## 6. 文件级改动

### 6.1 agent-engine

| 文件 | 改动 |
| --- | --- |
| `api/sse.py` | 增加 `route_event`、`steps_event`、`citations_event` 构造器 |
| `agents/workflow.py` | 从 `values` 事件中提取 `route` 并只发一次；从最终 state 提取 `steps` 快照；从工具结果提取 `citations`；`done` 前补发；AI-16 后节点过滤改为集合 |
| `api/chat.py` | `meta` 增加 `workflow`；把 artifacts 传给持久化层；AI-16 后注入单张 `chat_graph`，保留请求校验与 `workflow` 透传 |
| `graphs/learning.py` | 保持 `route` 结构；确认 `stages` 中每个 tag 都有 `tagId`、`name`；AI-16 时抽出 `fetch` / `learning_agent` 供合图复用 |
| `graphs/qa.py` | P1 绑定 `search_articles` 工具；AI-16 时抽出 `qa_agent` / 路由逻辑供合图复用 |
| `graphs/base.py` | AI-16 时新增 `ChatState` 超集（学习字段用 `NotRequired`） |
| `graphs/chat.py` | AI-16 新增：`router` + 条件边 + 统一 `compile()` |
| `tools/search_api.py` | 新增，封装 `/internal/search/recall` |
| `tools/java_api.py` | 拆成 `build_knowledge_tools`；删除 `get_java_tools` |
| `tools/proposals.py` | 拆成 `build_proposal_tools` 独立组；限流后续可迁 Redis |
| `app/main.py` | 分别初始化 `knowledge_tools` / `proposal_tools` / `search_tools`；注册会话路由；AI-16 后只构建一张 `chat_graph` |
| `api/conversations.py` | 新增会话列表/详情/删除 |
| `db/models.py` | `agent_message` 增加 `workflow`、`artifacts`；`ai_analysis_result` 增加 `notified_at` |
| `db/repository.py` | 新增会话分页、消息列表、删除；`finish_assistant_message` 支持 artifacts |
| `db/turn.py` | 写入用户/assistant 占位行时带 `workflow` |
| `db/migrations/versions/0005_agent_artifacts.py` | 新增迁移 |
| `agents/analysis.py` | `ArticleAnalysis` 增加 `author_id` |
| `agents/analysis_job.py` | 分析成功并发布知识事件后，发布 `ai.completed`；失败只记日志 |
| `mq/publisher.py` | 增加 `publish_analysis_completed(articleId, authorId)` |
| `api/analysis.py` | 返回 `sourceUpdatedAt`、`analyzedAt` |
| `models/llm.py` | 删除两个死 getter；P2 再接入 usage 统计 |
| `pyproject.toml` | 移除 `v2-theme` |

### 6.2 Java

| 文件 | 改动 |
| --- | --- |
| `ArticleDetailVO.java` | 增加 `updateTime`，供前端判断分析是否过期（可选） |
| `NotificationEventConsumer.java` | 无需改动，现有 `ai.completed` 消费逻辑可直接复用 |
| `InternalArticleController.java` | 无需改动，`ArticleInternalVO` 已包含 `authorId/updateTime` |
| `InternalSearchController.java` | 无需改动，`/internal/search/recall` 已提供公开元数据 |
| `TagCandidatePromoteService.java` | P1 与管理端统一提案来源，不在本次改核心逻辑 |

### 6.3 Web

| 文件 | 改动 |
| --- | --- |
| `api/modules/agent.ts` | `KNOWN_EVENTS` 增加 `route/steps/citations`；新增会话 API |
| `types/agent.ts` | 增加 `AgentRoute`、`AgentStep`、`AgentCitation`、`ConversationVO`、`ConversationMessageVO` |
| `views/ai/AiWorkbenchView.vue` | 删除 `parseRoute`、`loadReferences`、`loadStageReferences`；改为消费事件；会话列表/详情走 API；读取 `route.query` |
| `components/business/AnalysisCard.vue` | 展示 `analyzedAt/sourceUpdatedAt`；增加“问 AI”入口 |
| `views/article/ArticleDetailView.vue` | 增加“就本文问 AI”，传 `pageContext` |
| `views/tag/TechDetailView.vue` | 传 `tagId`，不再只传技术名 |
| `views/admin/TagProposalInboxView.vue` | 区分 AI / 系统来源；补未收录实体管理入口（P1 阶段评估使用率后决定是否下线） |

AI-16 不涉及 Web 改动：事件、REST、DB 均不变，前端无感。

---

## 7. 兼容与迁移顺序

### 7.1 兼容策略

1. **事件只增不改**：新增 `route/steps/citations`，旧客户端通过 `onUnknownEvent` 忽略，不会报错。
2. **字段只增不改**：`meta` 增加 `workflow` 是增量字段；旧前端不读也不影响。
3. **数据库先加列后写入**：迁移 0005 先上线，旧代码不写新列，回滚安全。
4. **前端保留 Markdown 回退**：历史消息没有 `artifacts` 时继续按正文渲染；新消息走结构化渲染。
5. **localStorage 只做过渡**：先读服务端，失败时可回退本地缓存；稳定一个版本后删除本地会话主逻辑。
6. **合图不改变契约**：AI-16 只改内部编排，SSE、REST、DB 全部不变；可以独立发布、独立回滚，前端不需要同步发版。

### 7.2 上线顺序

1. 数据库迁移 0005；
2. 后端发布新事件、学习路线 `route` 与会话 API（AI-01/02/03；旧前端不受影响）；
3. 紧接着执行 AI-16 合图：独立提交，先用 QA 与学习路线各一条样例做灰度对比，确认事件顺序、token 流、兜底文案一致，再全量；
4. 前端接入新事件与会话 API（AI-04/06，保留 Markdown 回退）；
5. 灰度发布后观察 **至少 3 天且无 P0/P1 级问题**，删除 `parseRoute` / `loadReferences` / 本地会话主逻辑；
6. 再接 P1 的检索引用、统一入口和提案治理。

AI-16 的回滚方式是恢复 `qa_graph` / `learning_graph` 两处构建与 API 层分流，不涉及数据迁移。

---

## 8. 验收标准

### 8.1 P0

- [ ] QA 问答流事件顺序为 `meta → steps* → delta* → citations? → done`，`error` 可在任意阶段终止；
- [ ] 学习路线在正文前收到 `route`，阶段技术链接使用 `tagId` 跳转，不再按名字搜索；
- [ ] 刷新页面或换设备后，会话列表和消息能从服务端恢复；
- [ ] 删除会话后，服务端和前端列表同步消失；
- [ ] 文章分析完成后，作者收到一次“AI 分析完成”通知，重投不重复；
- [ ] 分析响应包含 `sourceUpdatedAt/analyzedAt`；
- [ ] 技术详情页“让 AI 规划学习路线”能预选目标技术；
- [ ] 旧前端（只认识 `meta/delta/done/error`）仍能正常显示回答；
- [ ] 问答与学习路线共用一张编译图，`/api/agent/chat` 只有一次 `astream` 调用；
- [ ] 合图前后同一问题的回答结构一致，学习路线的 `route` 仍先于首个 `delta`；
- [ ] token 流、`steps`、兜底文案、`recursion_limit` 在两条分支都正常；
- [ ] 旧前端与旧会话数据在合图后无感。

### 8.2 P1

- [ ] 问答的引用来自本次实际检索结果，能点回站内文章；
- [ ] 付费文章正文不进入 prompt、不出现在引用里；
- [ ] 文章页“问 AI”进入工作台时带上文章上下文；
- [ ] 管理端能区分 AI 提案与系统升级提案；
- [ ] 聊天工具列表不再混入治理工具。

### 8.3 P2

- [ ] 全仓无 `get_chat_model/get_analysis_model/get_java_tools` 引用；
- [ ] `v2-theme` 从依赖和锁文件移除；
- [ ] SSE、会话 API、学习路线 route、分析通知有自动化测试；
- [ ] 每轮对话有 token 使用记录；
- [ ] 同类错误在不同入口的文案一致。

---

## 9. 风险与回滚

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 新事件导致旧前端报错 | 聊天不可用 | 事件只增不改；前端 `onUnknownEvent` 忽略；后端可配置关闭新事件 |
| 学习路线 prompt 漂移 | 阶段与正文不一致 | `route` 结构化直出后，正文只作为解释；前端不再依赖 Markdown 结构 |
| 引用质量差 | 用户不信任 | 只展示实际检索结果；加相关度阈值；无结果时显示“暂无引用” |
| 付费内容泄露 | 合规风险 | 引用只返回公开元数据；正文注入前必须补 locked 判断 |
| 分析通知重复 | 打扰作者 | `notified_at` + 版本幂等；发布失败只记日志 |
| 会话迁移丢失本地历史 | 用户投诉 | 先在本地保留一个版本；可选提供一次性导入接口 |
| 多实例提案限流不准 | 管理员被刷通知 | P1 迁 Redis 令牌桶；当前 content 侧 pending 唯一键兜底 |
| 数据库迁移失败 | 服务不可用 | 迁移只加列；先迁移后发版；回滚保留列 |
| 合图后 state 超集导致字段误用 | QA 分支读到学习字段，出现脏逻辑 | 学习字段用 `NotRequired`；各分支只读写本分支字段；契约测试覆盖 |
| 节点改名后 token 过滤失效 | 前端只有步骤没有正文 | 过滤条件改为节点集合；加流式回归测试 |
| 合图让 `route` 晚于正文 | 阶段卡错位、前端闪烁 | 用单图 + `values` 快照先发 `route`；不做父子图嵌套；测试事件顺序 |
| AI-16 提前后与 AI-01/02 连续改同一批文件 | 出问题难以定位 | 拆成 AI-01 / AI-02 / AI-16 三个独立提交；AI-16 单独灰度、单独回滚；不把合图和数据迁移放在同一提交 |
| 合图改变递归上限 | QA 工具轮次被截断 | 统一 `recursion_limit=12`，保持 `MAX_TOOL_ROUNDS=3`；加回归用例 |

回滚原则：

- 代码回滚不影响已加列；
- 新事件可以后端开关关闭；
- 前端回退到 Markdown 渲染路径；
- 会话 API 回滚不影响聊天写入；
- AI-16 回滚只需恢复双图构建与 API 层分流，不涉及数据。

---

## 10. 建议实施顺序

```text
里程碑 M1（P0）：契约、状态与会话图
  AI-01 事件模型
  AI-02 学习路线结构化
  AI-16 会话图统一路由
  AI-03 会话读/删 API + 迁移
  AI-04 前端服务端会话
  AI-05 分析版本与通知
  AI-06 技术详情深链

里程碑 M2（P1）：内容闭环
  AI-07 检索工具 + citations
  AI-08 引用安全
  AI-09 统一入口
  AI-10 提案治理拆分
  AI-11 分析结果复用（可选）

里程碑 M3（P2）：清债与可观测
  AI-12 死代码/依赖清理
  AI-13 契约测试（覆盖单图）
  AI-14 token 计量与评测
  AI-15 错误兜底收口
```

依赖关系：

- AI-02 依赖 AI-01；
- AI-04 依赖 AI-03；
- AI-06 依赖 AI-02；
- AI-07 依赖 AI-01；
- AI-09 依赖 AI-02/AI-04；
- AI-16 依赖 AI-01/AI-02；固定在 AI-02 之后执行，与 AI-03 / AI-04 可并行；
- AI-13 依赖 AI-01~AI-05，并覆盖 AI-16 合图后的单图；
- AI-10 可与 M2 并行，但不要在 P0 动提案主逻辑。

AI-16 固定在 AI-02 之后执行，不并入 AI-02：契约变更与拓扑变更分开提交、分开灰度、分开回滚。

---

## 11. 决策记录与不做清单

### 11.1 已决定

1. **保留前端显式 `workflow`**：不恢复意图识别自动分流，避免引入分类模型和新失败点。
2. **文章分析保留 MQ + 懒触发双通道**：MQ 负责正常发布/改稿，懒触发负责读路径补跑；两者共用 `AnalysisJob` 和 claim。
3. **提案继续由 content-service 拥有**：agent 只是提交方，不直接写标签/关系表。
4. **引用先只返回公开元数据**：不把付费正文注入模型；后续需要正文时再补 locked 判断。
5. **会话主存储迁到 PG 读接口**：localStorage 只做过渡和草稿缓存。
6. **不新增独立 AI 任务表**：聊天用 `agent_message`，分析用 `ai_analysis_result`，避免重复建模。
7. **分流下沉到图内，但入口仍显式传 `workflow`**：`router` 只做确定性映射，不做意图识别；工作台和技术详情继续显式指定模式。
8. **合图采用单图 + 条件边，不用父子图**：父子图会让学习路线的 `route` 晚于正文，除非引入 `subgraphs=True` 与 namespace 处理，复杂度和收益不匹配。
9. **文章分析不进会话图**：它是异步 Job，有独立的 claim、重试和过期语义，塞进图只会让状态更杂。
10. **保留未收录实体管理页**：content-service 已有查询接口，管理端补入口统一审批 AI 提案与系统提案；P1 阶段评估使用率后再决定是否下线。
11. **AI-16 固定在 AI-02 之后执行**：先把 `route` 事件跑通，再合图；AI-01 / AI-02 / AI-16 分三个独立提交，便于单独灰度和回滚。

### 11.2 明确不做

- 不重写工作流节点内部逻辑（AI-16 只合并拓扑与状态，不改 QA/学习节点的业务行为）；
- 不引入向量数据库或 Embedding 服务；
- 不把 agent-engine 拆成多个 Python 服务；
- 不把 content/knowledge/search 合并；
- 不在本次改造中做模型微调或提示词平台；
- 不在 P0 阶段动提案审批的核心业务逻辑。

---

## 12. 附：最小可交付示例

如果只做一件事，交付下面这条链路即可显著降低“碎”的感觉：

```text
用户提问
  → 后端 QA 调用知识图谱（可选检索站内文章）
  → SSE: meta → steps → delta* → citations → done
  → 前端直接渲染步骤、正文、引用
  → 会话与 artifacts 落 PG，刷新可恢复
```

这条链路完成后，工作台不再需要 `parseRoute`、按名字回查标签、独立搜索参考资料，AI 才真正从“外挂工作台”变成内容与知识网络的一部分。

AI-16 不改这条链路的产品行为，只让 QA 和学习路线共用一张图；按新排期，它紧跟 AI-02 之后执行，并单独提交、单独灰度。
