# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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
├── docs/superpowers/                # 重要实现计划与设计记录
├── .codegraph/                      # 代码库知识图谱
├── image/架构图/                    # 系统架构图与 RAG 流程图
├── image/展示图/                    # 项目页面截图
├── docker-compose.example.yml       # 本地 Compose 模板
├── docker-compose.prod.yml          # 生产 Compose
├── CONTEXT.md                       # 领域语言与边界
├── PLAN.md                          # 后续推进计划
└── CHANGELOG.md                     # 更新日志
```
## 验证命令

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

## 沟通与开发准备

- 默认使用中文回答。
- 当发现用户不确定需求时，对用户进行"面试追问"，确定方向及边界，不盲目开始。

## 开发规范

- 优先基于superpower插件提供的工作流程进行项目开发。
- 修改代码前，必须先确认当前分支和工作区状态，有问题或不规范先向我确认。
- 新增接口时，保持与现有 REST API 风格一致。
- 不要在 Controller 中写复杂业务逻辑。
- 不要在 Service 中直接拼接复杂 SQL。
- 不要让业务逻辑全部堆积于同一文件导致过于臃肿。
- 修改完成后，必须总结：
  - 修改了哪些文件
  - 每个文件为什么改
  - 是否运行了测试
  - 是否还有遗留风险
- 一个阶段开发交付后，按照Post Delivery analyis skill和 maintian-changelog skill做好相关总结和更新日志维护。

## 测试规则

- 新增业务逻辑时，优先补充单元测试。
- 修复 bug 时，先写能复现 bug 的测试，再修复。
- Service 层优先使用 JUnit + Mockito 测试。
- Controller 层优先使用 MockMvc 测试。
- 数据库相关逻辑可以使用 H2、Testcontainers 或项目已有测试方案。
- 不要为了让测试通过而删除有效断言。
- 修改测试前，先确认是测试过时，还是业务逻辑错误。
- 如果无法运行测试，必须说明原因。

## 安全规则

- 不要把 API Key、数据库密码、JWT Secret 写入代码。
- 不要把 `.env`、`application-local.yml`、`application.yml`、密钥文件提交到 Git。
- 日志中不能打印完整 API Key、access token、refresh token、密码或敏感信息。
- 每次提交前，必须确认安全问题。
- 如果发现疑似密钥泄露，必须立即提醒我轮换密钥。

## git 操作要求

- agent可主动提交，不过必须确认无误。
- 注意保护隐私内容，并做好git_ignore工作，不上传没必要的文件
- 遇到 merge conflict 时，先说明、总结冲突内容和解决建议，再等待我确认。
- 确认提交后，commit message 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。

## 其他规范
- 当发现用户需要理解某些代码逻辑时，若有.codegraph/目录，则优先根据该目录下的代码库知识图谱进行回答;若图谱知识不足以支撑完整回答，则重新审查代码库。