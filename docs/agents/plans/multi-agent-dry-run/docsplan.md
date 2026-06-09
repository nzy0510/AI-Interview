# docsplan.md

Agent 名称：Docs Agent

计划状态：Approved

基线 commit：442ddf29d86a20c32a4c67626dc18b930c0b006a

角色：多 Agent 工作流试运行中的文档 Agent，只负责 README 文档小改动。

目标：为 `README.md` 增加“多 Agent 工作流试运行说明”小节，说明主控线程、Docs 子线程、worktree 隔离、交付物、集成和审查如何在最小试运行中被验证。

非目标：不做复杂业务开发；不修改后端、前端、配置、部署、题库、图片、测试代码或 CHANGELOG；不创建子线程；不创建 worktree；不合并 `master`；不 push。

## 允许修改

- `README.md`

## 禁止修改

- `backend/**`
- `frontend/**`
- `embedding-service/**`
- `scripts/**`
- `tests/**`
- `docs/**`
- `image/**`
- `CHANGELOG.md`
- `PLAN.md`
- `.env`
- `application-local.yml`
- `application.yml`
- `docker-compose*.yml`
- `question_bank_imports/**`
- `.codegraph/**`
- `.understand-anything/**`
- `.worktrees/**`
- 任何密钥、私有配置、私有题库、临时产物或本地视频产物

## 输入材料

- `AGENTS.md`
- `docs/agents/workflow.md`
- `docs/agents/templates/development-worker.md`
- `README.md`

## 依赖前置任务

- 主控 Agent 已生成本计划文件。
- 用户明确审核并批准本计划文件。
- 主控 Agent 分配独立 Docs Agent 线程、任务分支和 worktree；在用户批准前不得创建子线程或 worktree。

## 验收标准

- `README.md` 新增小节标题为“多 Agent 工作流试运行说明”。
- 新增内容简洁说明本次是流程试运行，不是复杂业务交付。
- 新增内容明确主控线程负责拆分任务、分配 ownership、收集交付物并决定后续集成。
- 新增内容明确 Docs 子线程只能在自己的任务分支和独立 worktree 中修改授权文件。
- 新增内容明确子 Agent 不直接合并 `master`。
- 新增内容明确开发类 Agent 默认使用独立 Codex 线程、独立 Git worktree 和独立任务分支。
- 新增内容明确 Integration Agent 串行收口，Review Agent 串行审查。
- 变更范围仅包含 `README.md`。
- 不包含 `.env`、密钥、私有配置、临时导入包或其他禁止路径内容。

## 必须运行的验证

```powershell
git status --short --branch
git diff -- README.md
git diff --check -- README.md
git diff --name-status
```

预期结果：

- `git status --short --branch` 显示当前任务分支，且只有 `README.md` 处于修改状态。
- `git diff -- README.md` 只显示新增“多 Agent 工作流试运行说明”小节。
- `git diff --check -- README.md` 无输出。
- `git diff --name-status` 只显示 `M README.md`。

## 预期交付物

- 修改后的 `README.md`
- Docs Agent 任务分支上的一次 Conventional Commit，推荐提交信息：`docs: document multi-agent dry run`
- 完整返回执行状态、修改文件、验证命令、验证结果、未覆盖风险和 commit hash。

## 风险提示

- `README.md` 是共享文档，本任务阶段必须由 Docs Agent 独占写 ownership。
- 如果发现 `README.md` 已有未提交改动或需要修改其他文件，Docs Agent 必须停止并向主控请求 ownership 变更。
- 本任务只验证文档层面的工作流说明，不验证后端、前端、部署或 RAG 功能。
- 如果 Windows 换行产生 LF/CRLF 提示，需要确认最终 diff 没有扩大到整文件格式化。

## 完成后返回格式

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
