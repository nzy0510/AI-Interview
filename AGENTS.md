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

- 不要对我的观点一味地认可，我仍在学习阶段，很多地方很可能理解不深入，你可以提出不同的见解。
- 不从头造轮子，优先借鉴、学习成熟项目的实现方式（但不是简单复制）以及成熟的框架；对于当前项目，暂定为interview-guide 项目。
- 开发优先采用tdd方式进行开发，但不需要进行过细的测试(你可自行判断，小任务从简)。
- 如遇到docker部署失败，优先重试。还是失败再汇报。
- 不提交 `.env`、密钥文件、私有部署文件、私有题库、临时导入包或本地视频产物。
- 不在日志或文档中暴露完整 API Key、access token、refresh token、密码或敏感请求头。
- 完成 feature、bugfix、refactor、deployment 或用户可见代码变更后，按 `post-delivery-analysis` skill 自动输出交付后分析和下一步建议；不得自动执行下一步建议，必须等待用户明确指令。
- 在功能开发基本完善后，在交付或向用户说明汇报前，调度一个或多个subagent进行代码审查、功能自测等等(若任务难度大，涉及面很广才调用；小型任务不要如此操作)。如果发现有明显bug，修复完善后再交付给用户。如此流程可进行多轮循环，直至无明显bug。

## Git 规则

- 修改前必须确认分支和工作区状态。
- 保护用户和其他 Agent 的未提交改动；不要回滚不是自己造成的改动。
- 可以根据开发的功能模块自主 commit / push；
- commit message 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。
- `.codegraph/`、`.understand-anything/`、`.worktrees/` 属于本地 Agent / 代码智能工具产物，不应提交到 Git。

## 文档与交付

- 新增功能或用户可见行为变化时，用maintain-changelog skill维护更新日志文档。
- 重要架构和流程变更优先写入`docs/adr/`、`CONTEXT.md`。

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

本机浏览器端到端验收默认使用用户已登录并保存凭据的 `nzy333` 账号，不使用 `admin` 代替业务用户。

Windows 本地完整部署统一从仓库根目录运行 `./scripts/deploy-local.ps1`。不要手工设置 `DOCKER_HOST`；脚本固定使用 Docker Desktop `desktop-linux` context、保留 BuildKit/Maven 缓存并在构建失败时自动重试一次。Redis 与 Qdrant 默认只在 Compose 网络内开放；只有宿主机调试时才使用 `-ExposeDataServices` 暴露可配置端口。

## 验收说明

- 涉及前端页面改动时，一定要操控内置浏览器进行实际页面验收，不合格则返工，成功后才交付。

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
