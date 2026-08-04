# AGENTS.md
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

- 开发优先采用tdd方式进行开发。
- 优先小步修改，避免一次性大范围重构。
- 如遇到docker部署失败，优先重试。还是失败再汇报。
- 如果存在多个方案，先推荐最适合当前项目结构的方案，并说明取舍。
- 保护用户和其他 Agent 的未提交改动；不要回滚不是自己造成的改动。
- 不提交 `.env`、密钥文件、私有部署文件、私有题库、临时导入包或本地视频产物。
- 不在日志或文档中暴露完整 API Key、access token、refresh token、密码或敏感请求头。
- 修改配置文件、认证授权、部署文件、数据库 migration 前，必须说明影响范围。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令，除非用户明确要求。
- 完成 feature、bugfix、refactor、deployment 或用户可见代码变更后，按 `post-delivery-analysis` skill 自动输出交付后分析和下一步建议；不得自动执行下一步建议，必须等待用户明确指令。
- 在功能开发基本完善后，在交付或向用户说明汇报前，调度一个或多个subagent进行代码审查、功能自测等等。汇总他们的输出，如果发现有明显bug，修复完善后再交付给用户。如此流程可进行多轮循环，直至无明显bug。

## Spring Boot 架构规则

- 新增接口时，保持与现有 REST API 风格一致。
- 不要在 Controller 中写复杂业务逻辑；Controller 方法体不超过 20 行。
- 不要在 Service 中直接拼接复杂 SQL；条件查询使用 MyBatis-Plus `QueryWrapper`，复杂聚合查询写入 Mapper XML。
- 涉及事务时，优先在 Service 层使用 `@Transactional`。
- **Service 规模约束**：单个 Service 类不超过 400 行；超出时按单一职责拆分为多个子 Service（如 `XxxQueryService`、`XxxImportService`）。
- **DTO/Entity 分工**：Controller 接收和返回 DTO，不对外暴露 Entity；Service 内部操作 Entity，转换在 Service 层完成。
- **config 包职责**：仅放置框架级配置（`@Configuration`、`@Bean`、`Interceptor`）；业务提示词、常量等放到对应的 `service/` 子包或单独的 `prompt/` 包，不要混入 `config/`。

## 前端规则

- 保持 Vue 3、现有组件、路由、API client 和样式组织方式。
- 页面改动必须考虑移动端和桌面布局，不允许明显重叠、溢出或按钮文字挤压。
- 面向后台、设置、题库、数据面板的页面保持工作台式信息密度，避免营销式大卡片堆叠。
- 涉及接口变更时，必须同步检查前后端 contract。
- **组件规模约束**：单个 `.vue` 文件不超过 500 行（`<template>` + `<script>` 合计）；超出时必须拆分。
- **Composable 拆分时机**：`<script setup>` 超过 150 行，或存在独立的数据获取、图表渲染、状态管理等逻辑块时，提取为 `src/composables/useXxx.js`。
- **组件拆分时机**：模板中出现可独立复用的 UI 块（如抽屉内容、图表容器、列表卡片）时，提取为 `src/components/` 下的独立组件。
- **禁止在视图层直接引用 mock 数据**；mock 仅用于开发联调，不应参与生产渲染逻辑。

## 测试规则

- 新增业务逻辑时，优先补充单元测试。
- 修复 bug 时，优先写能复现 bug 的测试，再修复。
- Service 层优先使用 JUnit + Mockito。
- Controller 层优先使用 MockMvc。
- 前端 `src/utils/` 和 `src/composables/` 中的纯函数与逻辑必须有对应 Vitest 测试；视图层（`views/`、`components/`）的交互逻辑可酌情覆盖。
- Python 工具链使用 `python -m unittest discover -s tests`。
- 不要为了让测试通过而删除有效断言。
- 修改测试前，先确认是测试过时，还是业务逻辑错误。
- 拆分 Service 或 Composable 时，原有测试须同步迁移或补充到新模块，不得因拆分导致已有覆盖丢失。
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

## 代码库理解

- 在需要探索代码库时，优先读取`.codegraph/`下的内容了解结构，若需要详细了解，应派发子agent探索。
- agent也应优先采取`.codegraph/` 中的代码库知识图谱来理解项目架构。
- 回答架构、RAG、题库、部署等问题时，以当前代码和配置为准，不凭历史记忆下结论。
- CHANGELOG也可作为系统理解信息来源。

## 本机部署相关

本地 Docker 默认启用 `local-admin` 认证模式，不需要配置 QQ 邮箱、SMTP 授权码、注册验证码或找回密码。请保留以下默认配置：

```env
APP_AUTH_MODE=local-admin
APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED=true
```

本地默认账号固定为 `admin / admin123`，只用于绑定到 `127.0.0.1` 的本机部署，不要用于公网服务器。

题库维护权限按角色和 owner 判断，不按用户名写死：`admin`、`nzy333` 或其他 `ADMIN` 账号拥有相同能力；普通账号可新增并维护自己的私有岗位 / 题库，不能操作公共题库或其他账号的私有数据。

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
