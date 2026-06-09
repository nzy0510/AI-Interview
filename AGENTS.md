# AGENTS.md

This file provides guidance to Codex and compatible coding agents when working in this repository.

默认使用中文回答。除非用户明确要求英文，所有计划、风险、验证结果和总结都用中文说明。

## 当前项目结构

```text
.
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/com/interview/
│   │   ├── controller/              # REST API
│   │   ├── service/                 # 面试、简历、Mentor、RAG、题库服务
│   │   ├── service/questionbank/    # 题库发布、检索、Qdrant 同步
│   │   ├── entity/                  # MySQL 实体
│   │   └── config/                  # LLM、Redis、Embedding、JWT 等配置
│   └── src/main/resources/db/migration/
├── frontend/                        # Vue 3 前端
│   └── src/views/                   # 工作台、准备页、面试页、历史、Mentor、设置
├── embedding-service/               # FastAPI multilingual-e5 向量服务
├── scripts/question_bank_import.py  # 题库导入包生成
├── scripts/retrieval_eval/          # RAG 离线评测工具链
├── tests/                           # Python 工具链测试
├── docs/agents/                     # Agent 协作协议、模板和运行记录
├── docs/superpowers/                # 重要实现计划与设计记录
├── image/架构图/                    # 系统架构图与 RAG 流程图
├── image/展示图/                    # 项目页面截图
├── docker-compose.example.yml       # 本地 Compose 模板
├── docker-compose.prod.yml          # 生产 Compose
├── CONTEXT.md                       # 领域语言与边界
├── PLAN.md                          # 后续推进计划
└── CHANGELOG.md                     # 更新日志
```

`.codegraph/`、`.understand-anything/`、`.worktrees/` 属于本地 Agent / 代码智能工具产物，不应提交到 Git。

## 基础工作流

- 修改代码前，必须先执行 `git status --short --branch`，确认当前分支、未提交变更和未跟踪文件。
- 如果发现用户已有未提交改动，必须区分“本次任务相关”和“用户/其他 Agent 的改动”，不要回滚或覆盖无关内容。
- 需求不清楚时，先追问关键问题；不要猜测实现。
- 如果存在多个方案，先推荐最适合当前项目结构的方案，并说明取舍。
- 优先小步修改，避免一次性大范围重构。
- 修改配置文件、认证授权、部署文件、数据库 migration 前，必须先说明影响范围。
- 完成后必须总结：
  - 修改了哪些文件
  - 每个文件为什么改
  - 运行了哪些测试
  - 是否还有遗留风险

## 多 Agent 工作流规范

当用户要求多 Agent、并行开发、worktree 隔离或按 `agents_design.md` 执行时，按以下规则工作。

### 主控 Agent

主控 Agent 负责需求澄清、计划、任务拆分、文件所有权、worktree / branch 映射、结果收集、集成决策和最终汇报。

主控输出任务前必须明确：

```text
目标：
非目标：
验收标准：
任务拆分：
文件所有权矩阵：
分支/worktree 映射：
验证命令：
审查关卡：
回滚方案：
```

主控不得在并行开发阶段直接修改已分配给子 Agent 的文件，除非先更新 ownership 并说明原因。

### 子 Agent

每个子 Agent 必须获得清晰任务契约：

```text
Task ID:
角色:
目标:
允许修改:
禁止修改:
输入材料:
验收标准:
必须运行的验证:
预期交付物:
风险提示:
```

子 Agent 完成后必须返回：

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

子 Agent 不独占代码库。不得回滚他人改动，不得修改未授权路径，不得擅自合并到主分支。

### 文件所有权

- 同一文件同一阶段只能有一个写 Owner。
- `README.md`、`CHANGELOG.md`、`.env.example`、`docker-compose*.yml`、全局安全配置、数据库 migration 默认不允许并行修改。
- 如果开发中必须修改非授权文件，先向主控申请 ownership 变更。
- RAG / 题库 / Qdrant / embedding 相关任务优先交给 RAG/Data Agent，而不是简单归入普通 Backend Agent。

### Worktree 与分支

- 每个开发类 Agent 使用独立线程和独立 Git worktree。
- 默认 worktree 路径：`.worktrees/<branch-name>`。
- `.worktrees/` 必须被 `.gitignore` 忽略。
- 创建 worktree 前先执行 `git worktree list --porcelain`。
- 不在已有 linked worktree 中再创建嵌套 worktree。
- 一个 worktree 绑定一个任务分支。
- 不让 IDE 扫描 `.worktrees/`，避免 Maven / frontend project model 混乱。

建议分支命名：

```text
codex/<feature>-backend
codex/<feature>-frontend
codex/<feature>-rag-data
codex/<feature>-docs
codex/<feature>-integration
codex/<feature>-review-fixes
```

### 集成与审查

- 并行开发完成后，必须串行进入 Integration Agent。
- Integration Agent 负责合并子分支、处理冲突、运行集成测试和输出 integration report。
- 合并顺序优先：contract / RAG-Data / Backend / Frontend / Docs / Review fixes。
- Review Agent 顺序优先：Testing Review -> Security Review -> Maintainability Review -> Performance Review -> Final Review。
- Performance Review 只在涉及数据库、RAG、embedding、批处理、并发、视频或高频接口时启用。
- 阻断级问题未解决前，不得进入 merge / push / release。

阻断级问题包括：

- 测试失败。
- 密钥或敏感配置泄露。
- 新增管理接口缺少权限校验。
- 上传、路径、下载逻辑存在明显越权风险。
- 子 Agent 修改了未授权文件。
- 数据库 migration 不可逆且无说明。
- 文档描述和代码真实行为不一致。

## Spring Boot 规则

- 新增接口时，保持与现有 REST API 风格一致。
- 不要在 Controller 中写复杂业务逻辑。
- 不要在 Service 中直接拼接复杂 SQL。
- 涉及事务时，优先在 Service 层使用 `@Transactional`。
- 涉及认证授权时，必须检查 Spring Security / JWT / 拦截器相关逻辑。
- 不要随意修改 `application.yml`、`SecurityConfig`、`WebMvcConfig` 等全局配置；确需修改时先说明影响范围。

## 前端规则

- 保持 Vue 3、现有组件、路由、API client 和样式组织方式。
- 不引入新的 UI 库、状态库或大型依赖，除非用户明确批准。
- 页面改动必须考虑移动端和桌面布局，不允许明显重叠、溢出或按钮文字挤压。
- 面向后台、设置、题库、数据面板的页面保持工作台式信息密度，避免营销式大卡片堆叠。
- 涉及接口变更时，必须同步检查前后端 contract。

## RAG / 题库规则

- 题库发布、导入、归档、恢复、reindex 必须走项目已有 Question Bank Admin 或 Service API，不直接写数据库表。
- `question_bank_imports/` 是本地运维目录，默认不提交到 Git，fixtures 除外。
- `backend/src/main/resources/knowledge_base/atoms/**/*.json` 是随仓库发布的内置基础题库，面向空库首次初始化。
- 云端私有题库、临时导入包、原始 PDF、运维数据不应提交。
- 修改 embedding 模型、Qdrant collection、向量维度、召回 topK、rerank 逻辑时，必须说明迁移和 reindex 影响。

## 测试规则

- 新增业务逻辑时，优先补充单元测试。
- 修复 bug 时，优先写能复现 bug 的测试，再修复。
- Service 层优先使用 JUnit + Mockito。
- Controller 层优先使用 MockMvc。
- 前端逻辑优先使用 Vitest 或项目已有测试方式。
- Python 工具链使用 `python -m unittest discover -s tests`。
- 不要为了让测试通过而删除有效断言。
- 修改测试前，先确认是测试过时，还是业务逻辑错误。
- 如果无法运行测试，必须说明原因。

常用验证命令：

```powershell
cd backend
mvn test
```

```powershell
cd frontend
npm run build
npx vitest run
```

```powershell
python -m unittest discover -s tests
```

## 安全规则

- 不要把 API Key、数据库密码、JWT Secret、邮箱授权码写入代码。
- 不要提交 `.env`、`application-local.yml`、`application.yml`、密钥文件、私有部署文件。
- 日志中不能打印完整 API Key、access token、refresh token、密码或敏感请求头。
- 每次提交前必须检查 staged diff 中是否包含敏感信息。
- 如果发现疑似密钥泄露，必须立即提醒用户轮换密钥。

## Git 规则

- 修改前必须确认分支和工作区状态。
- 保护用户未提交改动；不要回滚不是自己造成的改动。
- 可以在用户授权下自主 commit / push；最终 merge / release 前需要确认。
- 遇到 merge conflict 时，先说明冲突文件、冲突原因和建议方案，再处理。
- 提交前展示修改文件、commit message 和测试结果。
- commit message 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令，除非用户明确要求。

## 文档与交付

- 新增功能或用户可见行为变化时，同步更新 README、CHANGELOG 或相关 docs。
- 重要架构和流程变更优先写入 `docs/agents/`、`docs/adr/` 或 `docs/superpowers/`。
- 一个阶段开发交付后，按 post-delivery analysis 和 maintain-changelog 的思路总结修改、验证和风险。

## 代码理解

- 当用户需要理解某段代码逻辑时，优先使用 `.codegraph/` 中的代码库知识图谱；如果图谱不足，再审查源码。
- 回答架构、RAG、题库、部署等问题时，以当前代码和配置为准，不凭历史记忆下结论。
