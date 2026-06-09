# Agent 协作设计

目标：在当前项目基础上建立一套可复现、线程隔离、worktree 隔离、可并行开发、可串行审查与集成的多 Agent 工作流。

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
- 输出任务清单、文件所有权矩阵、分支/worktree 映射和验证计划。
- 在创建子线程/worktree 前，为每个拟派发 Agent 生成一份 `<agent_name>plan.md`，交由用户审核。
- 创建或指派各开发 Agent 的线程与 worktree。
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
Agent 计划文件：
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
- 清理已完成 worktree。

限制：

- 不在未通过集成测试和审查时推送主分支。
- 不清理有未提交更改的 worktree。

---

## 2. 任务状态机

```text
Draft
  ↓
Ready for Design
  ↓
Designed
  ↓
Ready for Agent Plan Review
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
| Designed | plan / contract / ownership 已完成 | 主控生成每个 Agent 的 `<agent_name>plan.md` |
| Ready for Agent Plan Review | 每个 Agent 的计划文件已生成 | Human 审核并批准计划文件 |
| Ready for Development | 任务已拆分，计划文件已批准，分支和 worktree 方案确定 | worktree / branch / thread 创建完成 |
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

### 3.1 Agent 计划文件审核门禁

主控不得在用户审核前创建开发类子线程或 worktree。进入 `Ready for Development` 前，必须先为每个拟派发 Agent 生成一份具体计划文件。

推荐路径：

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

计划文件必须包含：

```text
Agent 名称：
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

审核规则：

- 用户未明确批准前，不创建对应子线程/worktree。
- 如果用户要求调整任务边界，主控先修改对应 `<agent_name>plan.md`，再重新请求审核。
- 如果多个 Agent 的计划文件存在重叠写入范围，必须先调整 ownership。
- 被批准后的计划文件是子 Agent 的唯一执行依据；子 Agent 不应根据聊天历史自行扩大范围。

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

清理前必须确认 worktree 干净：

```powershell
git -C .worktrees/codex-feature-backend status --short --branch
git worktree remove .worktrees/codex-feature-backend
git worktree prune --dry-run --verbose
```

Codex 原生 worktree 清理同样必须先确认干净：

```powershell
git -C C:\Users\nzy\.codex\worktrees\<id>\interview status --short --branch
git worktree remove C:\Users\nzy\.codex\worktrees\<id>\interview
git worktree prune --dry-run --verbose
```

禁止：

- 不删除有未提交更改的 worktree。
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
是否适合多 Agent
需要哪些 Agent
```

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

主控确认后生成任务契约、ownership 矩阵和每个 Agent 的 `<agent_name>plan.md`。

### Phase 1.5: Agent Plan Review

主控输出并等待用户审核：

```text
docs/agents/plans/<feature>/architectplan.md
docs/agents/plans/<feature>/backendplan.md
docs/agents/plans/<feature>/frontendplan.md
docs/agents/plans/<feature>/rag_dataplan.md
docs/agents/plans/<feature>/docsplan.md
docs/agents/plans/<feature>/integrationplan.md
docs/agents/plans/<feature>/testing_reviewplan.md
docs/agents/plans/<feature>/security_reviewplan.md
```

用户批准前：

- 不创建子线程。
- 不创建 worktree。
- 不派发实现任务。
- 只允许修改计划文件、ownership 矩阵和验证计划。

用户批准后，状态进入 `Ready for Development`。

### Phase 2: Worktree Setup

主控为每个开发 Agent 创建：

```text
独立线程
独立 worktree
独立分支
已批准的 <agent_name>plan.md
```

每个 Agent 先运行 baseline 检查：

```powershell
git status --short --branch
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

性能审查可按需启用。

### Phase 6: Branch / Release

Branch / Release Agent 执行：

1. 确认 `git status --short --branch`。
2. 确认 staged diff。
3. 运行最终验证。
4. 更新 CHANGELOG。
5. 提交或整理 commit。
6. 推送或创建 PR。
7. 清理 worktree。
8. 输出发布说明。

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
Branch / Release
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
Branch / Release
```

纯文档 / Release：

```text
Controller
Docs
Review
Branch / Release
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

## 12. 后续搭建建议

建议按三步落地：

1. 文档协议阶段
   - 将本文件迁移或同步到 `docs/agents/workflow.md`。
   - 新增 `docs/agents/templates/`，保存各类 Agent Prompt。
   - 用 1 个小功能手动跑完整流程。

2. 半自动阶段
   - 固定 worktree 创建脚本。
   - 固定运行记录模板。
   - 固定 Review checklist。
   - 主控手动创建线程，Agent 按模板执行。

3. 自动调度阶段
   - 让主控根据 ownership 矩阵自动创建 Codex worktree thread。
   - 子 Agent 自动提交任务分支。
   - Integration Agent 自动合并到 integration branch。
   - Review Agent 自动输出阻断报告。
   - Human 只在 Designed、Ready to Merge、Released 三个节点审批。
