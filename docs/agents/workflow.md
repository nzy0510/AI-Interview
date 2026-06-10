# Agent 协作设计

这是一套可复现、线程隔离、worktree 隔离、可并行开发、可串行审查与集成的多 Agent 工作流。

主控 Agent 的默认运行入口是 `docs/agents/controller-runtime.md`。本文件是完整参考细则，用于查询角色职责、状态机、任务契约、worktree 清理、审查阻断规则和标准流程；不要默认把本文件全文传给子 Agent。

阅读策略：

- 主控启动时先读 `docs/agents/controller-runtime.md`。
- 只有需要细则时，主控再按章节读取本文件。
- 子 Agent 默认只接收对应角色模板、自己的任务卡、总执行包相关摘录和任务入口文件。
- 如需把本文件全文传给子 Agent，必须在总执行包或 run record 中说明原因。

核心原则：

- 主控 Agent 只做需求澄清、计划、任务拆分、调度和最终决策，不直接承担大块业务实现。
- 每个开发类 Agent 使用独立 Codex 线程和独立 Git worktree，避免上下文污染和文件写入冲突。
- 并行只发生在写入范围互不重叠的开发任务中；集成、测试、审查、安全和分支处理必须串行。
- 所有 Agent 都必须产出可审计交付物：修改范围、验证命令、测试结果、风险和后续动作。
- 默认不让子 Agent 合并到主分支；子 Agent 只提交到自己的任务分支，最终由 Integration Agent 收口。

---

## 1. 角色分工

### 1.1 主控 Agent

职责：

- 与用户交互，澄清需求、非目标、约束和验收标准。
- 分析需求是否适合并行拆分。
- 生成或更新 `PLAN.md` / `docs/superpowers/plans/YYYY-MM-DD-<feature>.md`。
- 输出总执行包：任务清单、文件所有权矩阵、分支/worktree 映射、验证计划、风险点和人工审核节点。
- 在创建子线程/worktree 前，为每个拟派发 Agent 生成任务卡或 `<agent_name>plan.md`，并将关键边界汇总到总执行包交由用户一次性审核。
- 创建或指派各开发 Agent 的线程与 worktree；各agent使用的模型强度根据任务类型自主判断。
- 跟踪任务状态，收集各 Agent 交付物。
- 判断是否进入集成、审查、合并或回滚。

禁止：

- 不应在并行开发阶段直接修改开发 Agent 已分配的文件。
- 不应在未定义所有权的情况下派发多个 Agent 修改同一模块。
- 不应把模糊需求直接丢给开发 Agent。

主控输出：

```text
目标：
非目标：
验收标准：
影响范围：
任务拆分：
文件所有权矩阵：
分支/worktree 映射：
验证命令：
审查关卡：
回滚方案：
待审核总执行包：
Agent 任务卡：
```

### 1.2 Architect Agent

职责：

- 审查需求和现有代码结构。
- 输出模块边界、接口契约、数据流和任务拆分建议。
- 指出共享文件、公共类型、API contract、数据库迁移、配置变更等冲突高风险点。
- 为后端、前端、RAG/Data、Docs Agent 提供清晰边界。

限制：

- 默认只产出设计文档和契约，不直接创建大量目录骨架或占位代码。
- 只有主控明确要求时，才可以创建最小 contract 文件。
- 不应为了“架构完整”引入 speculative abstraction。

交付物：

```text
设计摘要：
受影响模块：
接口契约：
数据模型/DTO：
共享文件清单：
并行拆分建议：
风险点：
```

### 1.3 Backend Agent

职责：

- 实现后端 Controller / Service / Mapper / DTO / 配置绑定。
- 补充 JUnit、Mockito、MockMvc 或项目已有测试。
- 维护事务、权限、限流、日志和异常处理一致性。

典型所有权：

```text
backend/src/main/java/**
backend/src/test/**
backend/src/main/resources/db/migration/**
```

限制：

- 不直接修改前端页面，除非任务明确包含 API client 示例。
- 不随意修改 `application.yml`、`SecurityConfig`、`WebMvcConfig` 等全局配置。
- 涉及认证授权时必须说明检查过 JWT / Spring Security / 拦截器链路。

### 1.4 Frontend Agent

职责：

- 实现 Vue 页面、组件、状态管理、API client 和前端测试。
- 保持现有 UI 风格、交互密度和组件规范。
- 对接后端 API contract，并在变更时反馈给 Integration Agent。

典型所有权：

```text
frontend/src/**
frontend/package.json
frontend/package-lock.json
```

限制：

- 不直接改后端接口实现。
- 不引入新的 UI 库或状态库，除非主控明确批准。
- 不提交未压缩的大型媒体或本地构建产物。

### 1.5 RAG/Data Agent

职责：

- 处理题库、知识原子、RAG 检索、Qdrant、embedding、rerank、离线评测等任务。
- 维护 `scripts/question_bank_import.py`、`scripts/retrieval_eval/`、题库 fixtures、atom JSON。
- 对向量维度、collection、索引状态、召回 topK、评测集指标负责。

典型所有权：

```text
backend/src/main/java/com/interview/service/questionbank/**
backend/src/main/java/com/interview/service/*Retrieval*
backend/src/main/resources/knowledge_base/**
scripts/question_bank_import.py
scripts/retrieval_eval/**
question_bank_imports/fixtures/**
```

限制：

- 不直接写生产数据库表。
- 发布题库必须走 Question Bank Admin 或既有服务接口。
- 大型临时导入包、私有题库和原始 PDF 不提交到 Git。

### 1.6 Docs Agent

职责：

- 更新 README、CHANGELOG、PLAN、ADR、使用说明、发布说明。
- 保证文档描述和代码实际行为一致。
- 明确本地部署、云端部署、题库边界和安全注意事项。

典型所有权：

```text
README.md
CHANGELOG.md
PLAN.md
docs/**
image/**
```

限制：

- 不改业务代码。
- 不提交私有运维文档、密钥、临时导入包或本地视频产物。

### 1.7 Integration Agent

职责：

- 串行收集各开发 Agent 分支。
- 检查分支是否基于同一基线。
- 检查文件所有权是否冲突。
- 合并子任务分支到 integration branch。
- 处理冲突，运行集成级测试。
- 输出最终集成报告。

禁止：

- 不应跳过子任务验证结果。
- 不应在未解释冲突原因的情况下强行覆盖任一 Agent 的修改。
- 不应合并未通过阻断级审查的问题。

### 1.8 Review Agents

#### Testing Review Agent

职责：

- 检查每个任务是否有对应测试或明确的免测理由。
- 复核测试命令是否真实执行。
- 判断局部测试和全量测试是否覆盖验收标准。

阻断条件：

- 核心业务逻辑无测试且无合理说明。
- 测试命令未执行却声称通过。
- 删除有效断言或绕过失败测试。

#### Security Review Agent

职责：

- 检查认证、授权、JWT、上传、输入校验、日志脱敏、密钥、CORS、限流。
- 检查 `.env`、API Key、JWT Secret、数据库密码是否泄露。
- 检查前后端拼接后的真实安全边界。

阻断条件：

- 明文密钥进入 Git。
- 新增管理接口缺少权限校验。
- 上传、下载、路径处理存在明显越权或路径穿越风险。
- 日志打印 token、密码或完整 API Key。

#### Maintainability Review Agent

职责：

- 检查是否过度抽象、职责混乱、文件膨胀、命名不一致。
- 检查是否符合项目现有风格。
- 检查是否存在不必要的大范围重构。

阻断条件：

- 大量无关重构。
- 业务逻辑堆积到 Controller。
- 新增难以维护的跨层耦合。

#### Performance Review Agent

职责：

- 仅在涉及数据库、RAG、embedding、批处理、并发、前端大列表、视频或高频接口时启用。
- 检查 N+1、无分页查询、重复向量化、阻塞调用、前端过度渲染。

阻断条件：

- 关键路径引入明显指数级或线性不可控开销。
- 大批量任务无分页、无重试边界、无失败记录。

### 1.9 Branch / Release Agent

职责：

- 确认目标分支、提交范围和远端状态。
- 生成 Conventional Commit。
- 更新 CHANGELOG 和必要文档。
- 推送分支、创建 PR 或发布 release notes。
- 清理本次任务已完成、已合入且干净的 worktree 和本地任务分支。

限制：

- 不在未通过集成测试和审查时推送主分支。
- 不清理有未提交更改的 worktree。
- 不清理未确认属于本次任务的历史分支、远端跟踪分支或其他 Agent 仍在使用的分支。

---

## 2. 任务状态机

```text
Draft
  ↓
Ready for Design
  ↓
Designed
  ↓
Ready for Plan Packet Review
  ↓
Ready for Development
  ↓
In Development
  ↓
Ready for Integration
  ↓
Integrated
  ↓
In Review
  ↓
Ready to Merge
  ↓
Merged
  ↓
Released
  ↓
Archived
```

| 状态 | 进入条件 | 退出条件 |
| --- | --- | --- |
| Draft | 只有想法或模糊需求 | 需求、非目标、约束基本写清楚 |
| Ready for Design | 需求清楚 | Architect 输出设计和拆分建议 |
| Designed | plan / contract / ownership 已完成 | 主控生成总执行包和必要的 Agent 任务卡 |
| Ready for Plan Packet Review | 总执行包已生成 | Human 审核并批准关键边界 |
| Ready for Development | 任务已拆分，总执行包已批准，分支和 worktree 方案确定 | worktree / branch / thread 创建完成 |
| In Development | 开发 Agent 正在实现 | 局部测试通过，交付物完整 |
| Ready for Integration | 子任务完成且无阻断问题 | Integration Agent 开始合并 |
| Integrated | integration branch 已合并子任务 | 集成测试完成，进入审查 |
| In Review | 测试/安全/维护性/性能审查进行中 | 阻断问题全部修复 |
| Ready to Merge | CI / review / human gate 通过 | 合并到目标分支 |
| Merged | 已进入目标分支 | 发布或归档 |
| Released | 已发布或已形成 release notes | 进入维护跟踪 |
| Archived | 任务完成且 worktree 清理 | 无 |

---

## 3. 任务契约

### 3.0 任务分级与调度决策

当用户没有明确指定是否启用多 Agent 时，主控必须先做调度判定，再制定和派发任务。调度判定必须写入总执行包，并先于 Agent 任务卡生成。

主控必须输出：

```text
任务等级：L0 / L1 / L2 / L3 / High-risk
调度模式：单 Agent / 轻量多 Agent / 标准多 Agent / 完整多 Agent
是否启用多 Agent：
启用的 Agent：
不启用完整链路的理由：
人工审核节点：
自动继续范围：
```

默认路由表：

| 等级 | 适用任务 | 默认调度 | 用户审核 |
| --- | --- | --- | --- |
| L0 单 Agent | README 小改、错别字、链接修复、轻量说明、局部文档整理 | 当前线程或单 Docs Agent；不创建多 Agent worktree | 不需要总执行包审核，除非用户要求 |
| L1 轻量多 Agent | 用户明确要求流程试运行、较大文档更新、发布说明、低风险工具脚本小改 | Controller + 1 个执行 Agent + 可选 1 个合并 Review | 审核总执行包 |
| L2 标准多 Agent | 同时涉及后端/前端/RAG/Data/Docs 中两个以上模块，但不触及高风险面 | Controller + Architect + 对应开发 Agent + Integration + Review | 审核总执行包和最终 merge/push |
| L3 完整多 Agent | 跨后端、前端、RAG、数据库、部署、发布的完整功能交付 | 完整角色链路 | 审核总执行包、范围升级、最终 merge/push/release |
| High-risk | 认证授权、数据库 migration、部署配置、密钥、生产数据、依赖源、计费、安全策略 | 在对应等级基础上强制加入 Security/Testing/Integration/Release 审查 | 必须审核高风险操作和最终发布 |

裁剪规则：

- 文档-only 且只改 `README.md`、`CHANGELOG.md` 或 `docs/**` 时，默认不得启用完整多 Agent 链路。
- L0 任务默认走 `docs/agents/non-multi-agent.md`，除非用户明确要求多 Agent 试运行。
- L1 任务可以创建任务卡和 run record，但不需要 Architect、Backend、Frontend、RAG/Data、Integration、Testing/Security/Maintainability 多个独立 Review 或 Release 全部参与；除非用户明确要求验证这些环节。
- L2/L3 才需要进入 `Plan Packet Review`。
- High-risk 是覆盖规则：即使改动很小，也必须加入相应审查和人工审核点。
- L1 的默认 Review 形态是一个合并 Review Agent，覆盖测试合理性、安全敏感信息和文档/维护性；只有 L2/L3/High-risk 才默认拆分 Testing / Security / Maintainability / Performance。
- Performance Review 只在数据库、RAG、embedding、批处理、并发、高频接口、前端大列表、视频或明显性能敏感路径变更时启用。
- Branch / Release Agent 只在准备 commit 整理、push、PR、merge、release notes 或清理已完成 worktree 时启用；本地试运行不默认启用。

### 3.1 总执行包审核门禁

默认采用“重要节点人工审核，细碎步骤自动推进”。主控不得在用户审核总执行包前创建开发类子线程或 worktree。进入 `Ready for Development` 前，必须先生成总执行包，并把各 Agent 的关键边界汇总给用户一次性审核。

总执行包必须包含：

```text
目标：
非目标：
验收标准：
是否适合多 Agent：
任务等级：
调度模式：
需要的 Agent：
不启用完整链路的理由：
任务拆分：
文件所有权矩阵：
分支/worktree 映射：
验证命令：
高风险操作：
自动继续范围：
必须回到用户审核的触发条件：
回滚方案：
```

每个 Agent 可以有独立任务卡或计划文件，用于执行和审计。L1 默认只需要总执行包加执行 Agent 任务卡；如启用 Review，使用一个合并 review 任务卡即可。L2/L3/High-risk 再按角色拆出多份任务卡。推荐路径：

```text
docs/agents/plans/<feature>/<agent_name>plan.md
```

命名规则：

```text
controllerplan.md
architectplan.md
backendplan.md
frontendplan.md
rag_dataplan.md
docsplan.md
integrationplan.md
testing_reviewplan.md
security_reviewplan.md
maintainability_reviewplan.md
performance_reviewplan.md
releaseplan.md
```

Agent 任务卡必须包含：

```text
Agent 名称：
计划状态：
基线 commit：
角色：
目标：
非目标：
允许修改：
禁止修改：
输入材料：
依赖前置任务：
验收标准：
必须运行的验证：
预期交付物：
风险提示：
完成后返回格式：
```

任务卡应避免重复总执行包的大段内容。总执行包负责全局目标、分支映射、自动继续范围和回滚方案；任务卡只保留该 Agent 需要执行的局部目标、ownership、输入、验证和返回格式。

### 3.1.1 子 Agent 上下文装配规则

`docs/agents/workflow.md` 是主控 Agent 的全量治理手册，不是子 Agent 的默认执行上下文。主控派发子 Agent 时必须裁剪上下文，避免把完整工作流、完整聊天历史或其他 Agent 的计划文件传入执行线程。

默认上下文：

- Controller Agent：可以读取完整 `docs/agents/workflow.md`、`docs/agents/templates/controller.md` 和相关计划/运行记录模板。
- 开发类 Agent：只接收 `docs/agents/templates/development-worker.md`、自己的 `<agent_name>plan.md`、批准总执行包中与本 Agent 有关的摘录、以及任务相关代码/文档入口。
- Integration Agent：只接收 `docs/agents/templates/integration.md`、`integrationplan.md`、ownership 矩阵、子分支列表和各 Agent 交付物。
- Review Agent：只接收 `docs/agents/templates/review.md`、对应 review plan、base/head diff、任务契约和测试结果。
- Branch / Release Agent：只接收 `docs/agents/templates/release.md`、releaseplan、验证和 review 结果、目标分支信息。

限制：

- 子 Agent 不默认读取完整 `docs/agents/workflow.md`。
- 子 Agent 不默认读取其他 Agent 的 plan。
- 子 Agent 不应根据聊天历史、完整总执行包或全局角色说明扩大 ownership。
- 如果确需向某个子 Agent 提供完整 `workflow.md`，主控必须在总执行包或 run record 中说明原因。
- 子 Agent prompt 中必须明确“任务卡是唯一执行依据；全局材料只作为边界参考，不能扩大执行范围”。

审核规则：

- 用户未明确批准总执行包前，不创建子线程/worktree。
- 用户批准总执行包后，低风险任务卡不再逐个停下来审核；主控可以按总执行包自动创建子线程/worktree 并派发任务。
- 如果用户要求调整任务边界，主控先修改总执行包和对应任务卡，再重新请求审核关键变更。
- 如果多个 Agent 的计划文件存在重叠写入范围，必须先调整 ownership。
- 被批准后的计划文件是子 Agent 的唯一执行依据；子 Agent 不应根据聊天历史自行扩大范围。
- 用户批准后、创建子线程/worktree 前，主控必须固化总执行包和任务卡：默认提交到主控或 integration 分支；如果用户明确不希望提交，必须把批准后的完整文本传入子 Agent prompt，并在 run record 中记录未提交原因。
- 子 Agent 启动后，主控必须回查 `git worktree list --porcelain` 和 `git -C <worktree> status --short --branch`，确认实际 worktree、分支和计划一致。

必须回到用户审核的触发条件：

- 需要修改未授权文件或扩大 ownership。
- 多个 Agent 出现写入范围冲突。
- 涉及数据库 migration、认证授权、安全策略、部署配置、密钥、计费、生产数据或外部依赖源变更。
- 测试失败、审查阻断问题未修复，或 Agent 想绕过既定验证。
- 需要切换实现方案、依赖来源、模型/服务提供方或降级方案。
- 需要执行破坏性 Git / 文件操作，或清理有未提交内容的 worktree。
- 准备 merge、push、发布 release 或变更目标分支历史。

可以自动继续的低风险步骤：

- 创建已批准范围内的任务分支和 worktree。
- 把批准后的任务卡传给子 Agent。
- 子 Agent 在授权文件范围内实现和自测。
- 修复局部 lint、格式、类型或测试失败。
- 提交到自己的任务分支。
- Integration Agent 做 ownership、冲突、diff 和验证结果检查。
- Review Agent 输出阻断/非阻断问题。
- 维护 run record。
- 清理已确认干净的 worktree。

### 3.2 子 Agent 任务契约

每个派发给子 Agent 的任务必须包含以下字段：

```text
Task ID:
目标:
非目标:
所属 Agent:
线程:
分支:
worktree:
允许修改:
禁止修改:
输入材料:
验收标准:
必须运行的验证:
预期交付物:
风险提示:
```

开发 Agent 完成后必须返回：

```text
状态: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
完成内容:
修改文件:
测试命令:
测试结果:
未覆盖风险:
是否触碰共享文件:
commit hash:
后续建议:
```

状态含义：

- `DONE`：任务完成且自测通过。
- `DONE_WITH_CONCERNS`：任务完成，但存在需要主控关注的风险。
- `NEEDS_CONTEXT`：缺少必要上下文，不能继续。
- `BLOCKED`：遇到无法自行解决的阻断问题。

---

## 4. 文件所有权矩阵

主控在派发任务前必须建立 ownership 表。

示例：

| 路径 | Owner | 允许并行修改 | 说明 |
| --- | --- | --- | --- |
| `backend/src/main/java/com/interview/controller/**` | Backend Agent | 否 | 后端接口入口，避免多个 Agent 同时改 |
| `backend/src/main/java/com/interview/service/questionbank/**` | RAG/Data Agent | 否 | 题库发布和 Qdrant 同步 |
| `frontend/src/components/settings/**` | Frontend Agent | 否 | 设置页组件 |
| `scripts/retrieval_eval/**` | RAG/Data Agent | 是，需按文件拆分 | 离线评测工具 |
| `README.md` | Docs Agent | 否 | 文档收口阶段统一改 |
| `CHANGELOG.md` | Branch / Release Agent | 否 | 最终提交前统一维护 |
| `.env.example` | Integration Agent | 否 | 配置面影响部署，必须串行改 |
| `docker-compose*.yml` | Integration Agent | 否 | 部署面必须串行改 |

规则：

- 同一文件只能有一个写 Owner。
- 共享 contract 文件由 Architect 或 Integration Agent 维护。
- 如果开发过程中需要修改非授权文件，必须先向主控申请 ownership 变更。

---

## 5. Worktree 与分支规则

### 5.1 命名规范

```text
主控分支: master 或 codex/<feature>-controller
集成分支: codex/<feature>-integration
后端分支: codex/<feature>-backend
前端分支: codex/<feature>-frontend
RAG/Data 分支: codex/<feature>-rag-data
Docs 分支: codex/<feature>-docs
Review 修复分支: codex/<feature>-review-fixes
```

### 5.2 Worktree 路径

优先使用 Codex 或当前执行环境提供的原生 worktree。原生 worktree 通常位于：

```text
C:\Users\nzy\.codex\worktrees\<id>\interview
```
若出现工作树配置相关的报错，先查询原因并修复。

只有当前环境没有原生 worktree 能力时，才使用项目内隐藏目录作为 fallback：

```text
.worktrees/<branch-name>
```

要求：

- 使用原生 worktree 时，记录实际路径和对应线程即可，不强制迁移到项目内 `.worktrees/`。
- 使用项目内 fallback 时，`.worktrees/` 必须加入 `.gitignore`。
- 创建前运行 `git worktree list --porcelain` 确认当前状态。
- 不在已有 linked worktree 中再创建嵌套 worktree。
- 每个 worktree 绑定一个任务分支。
- 每个 worktree 只运行自己任务需要的服务或测试。

### 5.3 创建流程

```powershell
git fetch origin
git worktree add .worktrees/codex-feature-backend -b codex/feature-backend origin/master
git -C .worktrees/codex-feature-backend status --short --branch
```

如果由 Codex 创建线程时自动创建 worktree，则以 Codex 返回的 worktree 路径为准，只需在 run record 中登记路径、分支和 ownership。

### 5.4 清理流程

任务完成后，Branch / Release Agent 负责判断本次任务创建的 worktree 和本地任务分支是否可清理；开发类 Agent 不应自行删除其他 Agent 的 worktree 或分支。Integration Agent 可以报告合并状态和干净状态，但除非总执行包明确授权，不默认执行删除。

可清理条件：

- 分支和 worktree 明确属于本次任务。
- 分支 commit 已被目标分支或已批准的 integration branch 包含。
- 对应 worktree 状态干净，没有未提交、未暂存或未跟踪的任务文件。
- 不属于其他任务、历史保留分支、远端仍需维护的分支或用户明确要求保留的分支。

清理前必须确认 worktree 干净：

```powershell
git -C .worktrees/codex-feature-backend status --short --branch
git worktree remove .worktrees/codex-feature-backend
git branch -d codex/feature-backend
git worktree prune --dry-run --verbose
```

Codex 原生 worktree 清理同样必须先确认干净：

```powershell
git -C C:\Users\nzy\.codex\worktrees\<id>\interview status --short --branch
git worktree remove C:\Users\nzy\.codex\worktrees\<id>\interview
git branch -d codex/feature-backend
git worktree prune --dry-run --verbose
```

禁止：

- 不删除有未提交更改的 worktree。
- 不删除未合入目标分支或 integration branch 的任务分支。
- 不删除不属于本次任务的 `codex/*` 分支。
- 不删除远端分支，除非用户明确要求并确认远端状态。
- 不用 `git reset --hard` 清理未知状态。
- 不让 IDE 扫描 `.worktrees/`，避免 Maven / frontend project model 混乱。

---

## 6. 并行与串行边界

允许并行：

- 后端业务实现和前端页面实现，前提是 API contract 已冻结。
- RAG/Data 评测工具和普通页面改动。
- Docs Agent 准备草稿，但最终 README / CHANGELOG 由 Integration 或 Release 阶段统一收口。
- 多个只读 Review Agent 并行审查同一个 integration diff。

必须串行：

- 数据库 migration。
- `.env.example`、Docker Compose、SecurityConfig、WebMvcConfig 等全局配置。
- API contract 最终定稿。
- 合并子分支。
- 全量测试。
- 安全审查修复。
- CHANGELOG 和 release notes。
- push / merge / release。

---

## 7. 标准执行流程

### Phase 0: Intake

主控输出：

```text
需求摘要
非目标
验收标准
任务等级
调度模式
是否适合多 Agent
需要哪些 Agent
不启用完整链路的理由
```

如果判定为 L0，默认使用单 Agent 或当前线程完成，不进入后续多 Agent phase。只有 L1/L2/L3/High-risk 才继续生成总执行包或任务卡。

### Phase 1: Design

Architect Agent 输出：

```text
模块边界
API contract
数据模型
共享文件
拆分建议
风险
```

主控确认后生成总执行包、ownership 矩阵和必要的 Agent 任务卡。

### Phase 1.5: Plan Packet Review

主控输出并等待用户审核总执行包：

```text
目标 / 非目标 / 验收标准
Agent 拆分
文件所有权矩阵
分支/worktree 映射
验证命令
高风险操作
自动继续范围
必须回到用户审核的触发条件
回滚方案
```

如需保留细粒度审计，主控同时生成但不逐个停审。L1 默认只生成必要任务卡，例如：

```text
docs/agents/plans/<feature>/controllerplan.md
docs/agents/plans/<feature>/docsplan.md
docs/agents/plans/<feature>/reviewplan.md
```

L2/L3/High-risk 再按实际启用角色拆分：

```text
docs/agents/plans/<feature>/architectplan.md
docs/agents/plans/<feature>/backendplan.md
docs/agents/plans/<feature>/frontendplan.md
docs/agents/plans/<feature>/rag_dataplan.md
docs/agents/plans/<feature>/docsplan.md
docs/agents/plans/<feature>/integrationplan.md
docs/agents/plans/<feature>/testing_reviewplan.md
docs/agents/plans/<feature>/security_reviewplan.md
docs/agents/plans/<feature>/maintainability_reviewplan.md
docs/agents/plans/<feature>/performance_reviewplan.md
docs/agents/plans/<feature>/releaseplan.md
```

用户批准前：

- 不创建子线程。
- 不创建 worktree。
- 不派发实现任务。
- 只允许修改总执行包、任务卡、ownership 矩阵和验证计划。

用户批准后，状态进入 `Ready for Development`。

### Phase 2: Worktree Setup

主控为每个开发 Agent 创建：

```text
独立线程
独立 worktree
独立分支
已批准总执行包内的 Agent 任务卡
```

创建后必须记录：

```text
实际 worktree 路径
实际分支
base commit
批准后的总执行包路径或原文
任务卡路径或原文
总执行包和任务卡是否已提交
```

每个 Agent 先运行 baseline 检查：

```powershell
git status --short --branch
git worktree list --porcelain
```

按任务类型选择：

```powershell
cd backend; mvn test
cd frontend; npm run build
python -m unittest discover -s tests
```

### Phase 3: Parallel Development

各开发 Agent 执行：

1. 阅读任务契约。
2. 确认允许修改范围。
3. 实现最小改动。
4. 运行局部测试。
5. 提交到任务分支。
6. 返回标准交付物。

### Phase 4: Integration

Integration Agent 执行：

1. 创建 integration branch。
2. 按风险从低到高合并子分支。
3. 先合并 contract / backend，再合并 frontend，再合并 docs。
4. 处理冲突并记录原因。
5. 运行集成验证。
6. 输出 integration report。

L1 默认不单独启用 Integration Agent；主控可以在当前线程完成只读 diff/ownership 检查，或由一个合并 Review Agent 审查最终 diff。只有用户明确要求验证 Integration 环节，或存在多个子分支、多个写 Owner、冲突风险、准备 merge/push/release 时，才创建 Integration Agent。

建议合并顺序：

```text
Architect contract
RAG/Data
Backend
Frontend
Docs
Review fixes
```

### Phase 5: Review

审查顺序：

```text
Testing Review
Security Review
Maintainability Review
Performance Review
Final Review
```

L1 默认使用一个合并 Review Agent，输出测试合理性、安全敏感信息、文档/维护性结论。L2/L3/High-risk 才默认拆分 Testing Review、Security Review、Maintainability Review；Performance Review 和 Final Review 均按需启用。

性能审查可按需启用。

### Phase 6: Branch / Release

Branch / Release Agent 执行：

1. 确认 `git status --short --branch`。
2. 确认 staged diff。
3. 运行最终验证。
4. 更新 CHANGELOG。
5. 提交或整理 commit。
6. 推送或创建 PR。
7. 确认计划文件、run record 和最终 diff 是否都已纳入提交。
8. 按 5.4 清理本次任务已合入且干净的 worktree 和本地任务分支。
9. 输出发布说明。

---

## 8. 审查与阻断规则

阻断级问题：

- 测试失败。
- 安全漏洞。
- 密钥泄露。
- 权限绕过。
- 数据库 migration 不可逆且无说明。
- 子 Agent 修改了未授权文件。
- 运行结果和报告不一致。
- README / CHANGELOG 描述与代码不一致。

非阻断但必须记录：

- 测试覆盖不足但风险较低。
- 性能影响较小且有后续优化计划。
- 可维护性问题不影响当前发布。
- 文档还可补充但不影响使用。

---

## 9. 可复现记录

每次多 Agent 工作流都应生成一份运行记录，建议路径：

```text
docs/agents/runs/YYYY-MM-DD-<feature>.md
```

记录模板：

```text
Feature:
Controller:
Base commit:
Target branch:
Agents:
  - name:
    role:
    thread:
    branch:
    worktree:
    ownership:
    status:
    commit:
Validation:
  - command:
    result:
Reviews:
  - type:
    result:
Merged commits:
Risks:
Follow-ups:
Worktree cleanup:
```

如果运行记录不适合提交到 Git，可保存在本地，但最终 release notes 必须包含关键验证结果。

---

## 10. Prompt 模板

### 10.1 开发 Agent Prompt

```text
你是 <Role> Agent，正在参与 InterWise 多 Agent 工作流。

你不独占代码库，其他 Agent 可能在并行修改不同 worktree。
不要修改未授权文件，不要回滚他人改动。

任务：
<Task ID / Goal>

允许修改：
<paths>

禁止修改：
<paths>

验收标准：
<acceptance criteria>

必须运行：
<commands>

完成后返回：
- 状态
- 修改文件
- 测试命令和结果
- 风险
- commit hash
```

### 10.2 Review Agent Prompt

```text
你是 <Testing/Security/Maintainability/Performance> Review Agent。

只审查 integration diff，不做无关重构。
优先输出阻断级问题，按严重程度排序。

输入：
- base commit
- head commit
- 相关任务契约
- 测试结果

输出：
- 阻断问题
- 非阻断问题
- 缺失测试
- 是否批准进入下一阶段
```

### 10.3 Integration Agent Prompt

```text
你是 Integration Agent。

你的职责是串行合并多个开发 Agent 的结果。
不要跳过冲突解释，不要覆盖未授权改动。

输入：
- base commit
- 子分支列表
- ownership 矩阵
- 各 Agent 交付物

步骤：
1. 检查每个分支状态。
2. 检查重叠文件。
3. 按指定顺序合并。
4. 处理冲突并记录原因。
5. 运行集成验证。
6. 输出 integration report。
```

---

## 11. 当前项目推荐 Agent 组合

L1 文档 / 低风险工具小改：

```text
Controller
Docs 或对应执行 Agent
Combined Review（可选）
```

常规功能：

```text
Controller
Architect
Backend
Frontend
Docs
Integration
Testing Review
Security Review
Branch / Release（准备 push / PR / merge / release 时）
```

RAG / 题库功能：

```text
Controller
Architect
RAG/Data
Backend
Frontend
Docs
Integration
Testing Review
Security Review
Performance Review
Branch / Release（准备 push / PR / merge / release 时）
```

纯文档 / Release：

```text
Controller
Docs
Combined Review（可选）
Branch / Release（仅发布说明、push、PR、merge 或 release 时）
```

安全修复：

```text
Controller
Security Review
Backend / Frontend / RAG/Data
Testing Review
Final Security Review
Branch / Release
```

---
