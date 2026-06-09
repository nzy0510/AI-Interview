# Non-Multi-Agent Workflow

用于普通单 Agent 开发、修 bug、文档更新、局部重构和小型维护任务。若用户明确要求多 Agent、并行开发或 worktree 隔离，改读 `docs/agents/workflow.md`。

## 基础工作流

- 修改代码前，必须先执行 `git status --short --branch`。
- 如果发现用户已有未提交改动，必须区分“本次任务相关”和“用户/其他 Agent 的改动”，不要回滚或覆盖无关内容。
- 需求不清楚时，先追问关键问题；不要猜测实现。
- 如果存在多个方案，先推荐最适合当前项目结构的方案，并说明取舍。
- 优先小步修改，避免一次性大范围重构。
- 完成后必须总结：
  - 修改了哪些文件
  - 每个文件为什么改
  - 运行了哪些测试
  - 是否还有遗留风险

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
