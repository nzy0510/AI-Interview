# AGENTS.md

This file is the lightweight entrypoint for Codex and compatible coding agents in this repository.

默认使用中文回答。除非用户明确要求英文，所有计划、风险、验证结果和总结都用中文说明。

## 项目结构

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
├── docs/adr/                        # 架构决策记录
├── docs/superpowers/                # 重要实现计划与设计记录
├── image/架构图/                    # 系统架构图与 RAG 流程图
├── image/展示图/                    # 项目页面截图
├── docker-compose.example.yml       # 本地 Compose 模板
├── docker-compose.prod.yml          # 生产 Compose
├── CONTEXT.md                       # 领域语言与边界
└── CHANGELOG.md                     # 更新日志
```

## 基础规则

- 开发优先采用tdd方式进行开发，并且开发前应先创建功能分支。
- 保护用户和其他 Agent 的未提交改动；不要回滚不是自己造成的改动。
- 不提交 `.env`、`application-local.yml`、密钥文件、私有部署文件、私有题库、临时导入包或本地视频产物。
- 不在日志或文档中暴露完整 API Key、access token、refresh token、密码或敏感请求头。
- 修改配置文件、认证授权、部署文件、数据库 migration 前，必须说明影响范围。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令，除非用户明确要求。
- 完成 feature、bugfix、refactor、deployment 或用户可见代码变更后，按 `post-delivery-analysis` skill 自动输出交付后分析和下一步建议；不得自动执行下一步建议，必须等待用户明确指令。
- 在需要探索代码库时，优先读取`.codegraph/`下的内容了解结构，若需要详细了解，应派发子agent探索。
- 在功能开发基本完善后，在交付或向用户说明汇报前，调度一个或多个subagent进行代码审查、功能自测等等。汇总他们的输出，如果发现有明显bug，修复完善后再交付给用户。如此流程可进行多轮循环，直至无明显bug。

## 基础工作流

- 如果发现用户已有未提交改动，必须区分“本次任务相关”和“用户/其他 Agent 的改动”，不要回滚或覆盖无关内容。
- 需求不清楚时，先追问关键问题；不要猜测实现。
- 如果存在多个方案，先推荐最适合当前项目结构的方案，并说明取舍。
- 优先小步修改，避免一次性大范围重构。
- 如遇到docker部署失败，优先重试。还是失败再汇报。

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

## Git 规则

- 修改前必须确认分支和工作区状态。
- 可以在用户授权下自主 commit / push；最终 merge / release 前需要确认。
- 遇到 merge conflict 时，先说明冲突文件、冲突原因和建议方案，再处理。
- 提交前展示修改文件、commit message 和测试结果。
- commit message 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令，除非用户明确要求。
- `.codegraph/`、`.understand-anything/`、`.worktrees/` 属于本地 Agent / 代码智能工具产物，不应提交到 Git。

## 文档与交付

- 新增功能或用户可见行为变化时，用maintain-changelog skill维护更新日志文档。
- 重要架构和流程变更优先写入 `docs/agents/`、`docs/adr/` 或 `docs/superpowers/`。

## 代码理解

- 当用户需要理解某段代码逻辑时，优先使用 `.codegraph/` 中的代码库知识图谱；如果图谱不足，再审查源码。
- agent也应优先采取`.codegraph/` 中的代码库知识图谱来理解项目架构。
- 回答架构、RAG、题库、部署等问题时，以当前代码和配置为准，不凭历史记忆下结论。

## 常用验证命令

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

无法运行测试时，必须说明原因。

## 本地测试运行

### 后端
```powershell
cd E:\Develop\interview\backend
mvn spring-boot:run
```

### 前端
```powershell
cd E:\Develop\interview\frontend
npm run dev
```

## Agent skills

### Issue tracker

Issues and PRDs live as GitHub Issues. The `gh` CLI is the interface. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage labels use the default vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` at the repo root, `docs/adr/` for architecture decisions. See `docs/agents/domain.md`.
