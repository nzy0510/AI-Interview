# AGENTS.md

This file is the lightweight entrypoint for Codex and compatible coding agents in this repository.

默认使用中文回答。除非用户明确要求英文，所有计划、风险、验证结果和总结都用中文说明。

## 先判断工作模式

开始任务前先执行：

```powershell
git status --short --branch
```

然后按任务类型选择规则文档：

| 场景 | 必读文档 |
| --- | --- |
| 普通单 Agent 开发、修 bug、文档更新、局部重构 | `docs/agents/non-multi-agent.md` |
| 多 Agent、并行开发、worktree 隔离、主控/子 Agent 协作 | `docs/agents/workflow.md` |
| 多 Agent 提示词模板 | `docs/agents/templates/*.md` |
| 题库导入包生成和审核 | `.agents/skills/interview-question-bank/SKILL.md` |
| 题库导入生命周期 | `docs/contracts/question-bank-import-lifecycle.md` |

如果用户明确提到“多 Agent”、“子线程”、“主控 Agent”、“并行”、“worktree 隔离”或“按工作流开发”，必须先阅读 `docs/agents/workflow.md`，再输出任务拆分、ownership、分支/worktree 映射和验证计划。

如果只是小改动或单线程任务，使用 `docs/agents/non-multi-agent.md`，不要强行套多 Agent 流程。

## 项目结构速览

```text
backend/                         # Spring Boot 后端
frontend/                        # Vue 3 前端
embedding-service/               # FastAPI multilingual-e5 向量服务
scripts/question_bank_import.py  # 题库导入包生成
scripts/retrieval_eval/          # RAG 离线评测工具链
tests/                           # Python 工具链测试
docs/agents/                     # Agent 协作协议、模板和运行记录
docs/superpowers/                # 重要实现计划与设计记录
image/架构图/                    # 系统架构图与 RAG 流程图
image/展示图/                    # 项目页面截图
```

`.codegraph/`、`.understand-anything/`、`.worktrees/` 属于本地 Agent / 代码智能工具产物，不应提交到 Git。

## 硬性规则

- 保护用户和其他 Agent 的未提交改动；不要回滚不是自己造成的改动。
- 不提交 `.env`、`application-local.yml`、密钥文件、私有部署文件、私有题库、临时导入包或本地视频产物。
- 不在日志或文档中暴露完整 API Key、access token、refresh token、密码或敏感请求头。
- 修改配置文件、认证授权、部署文件、数据库 migration 前，必须说明影响范围。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令，除非用户明确要求。
- 完成后必须说明修改文件、修改原因、验证命令、测试结果和遗留风险。

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
