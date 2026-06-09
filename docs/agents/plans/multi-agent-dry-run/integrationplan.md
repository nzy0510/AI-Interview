# integrationplan.md

Agent 名称：Integration Agent

计划状态：Approved

基线 commit：442ddf29d86a20c32a4c67626dc18b930c0b006a

角色：多 Agent 工作流试运行中的串行集成 Agent，负责收口 Docs Agent 交付物并确认最终集成分支状态。

目标：在用户批准本计划后，基于 `442ddf29d86a20c32a4c67626dc18b930c0b006a` 创建独立集成分支，合入 Docs Agent 分支 `codex/multi-agent-dry-run-docs` 的提交 `c9b083f53537ddd5b87bf30c18002f1a0ca87ea7`，确认最终 diff 只包含 `README.md` 的试运行说明。

非目标：不修改 README 内容；不修改后端、前端、配置、部署、题库、测试、图片或 CHANGELOG；不直接合并 `master`；不 push；不清理其他 Agent worktree；不提交私有配置、临时产物或密钥。

## 允许修改

- Git 集成分支历史：`codex/multi-agent-dry-run-integration`
- 通过合并 Docs Agent 提交引入的 `README.md` 变更
- 集成报告中记录的命令结果和风险说明

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
- `docs/agents/templates/integration.md`
- `docs/agents/plans/multi-agent-dry-run/docsplan.md`
- Docs Agent 分支：`codex/multi-agent-dry-run-docs`
- Docs Agent commit：`c9b083f53537ddd5b87bf30c18002f1a0ca87ea7`

## 依赖前置任务

- Docs Agent 已完成并提交 `c9b083f53537ddd5b87bf30c18002f1a0ca87ea7`。
- 用户明确批准本 `integrationplan.md`。
- 主控 Agent 分配独立 Integration Agent 线程、任务分支和 worktree；在用户批准前不得创建对应线程或 worktree。
- Integration Agent 启动后，主控必须回查 `git worktree list --porcelain` 和 Integration worktree 中的 `git status --short --branch`。

## 验收标准

- Integration worktree 位于独立任务分支 `codex/multi-agent-dry-run-integration`。
- 集成分支以 `442ddf29d86a20c32a4c67626dc18b930c0b006a` 为基线。
- Docs Agent 分支是基线的后代，且只修改 `README.md`。
- 合并后最终 diff 只显示 `README.md`。
- `git diff --check` 无空白错误。
- Integration Agent 不手工编辑任何文件内容。
- 不直接合并 `master`，不 push。

## 必须运行的验证

```powershell
git status --short --branch
git worktree list --porcelain
git fetch origin
git merge-base --is-ancestor 442ddf29d86a20c32a4c67626dc18b930c0b006a codex/multi-agent-dry-run-docs
git diff --name-status 442ddf29d86a20c32a4c67626dc18b930c0b006a..codex/multi-agent-dry-run-docs
git switch -c codex/multi-agent-dry-run-integration 442ddf29d86a20c32a4c67626dc18b930c0b006a
git merge --ff-only codex/multi-agent-dry-run-docs
git diff --name-status 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git diff --check 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git status --short --branch
```

预期结果：

- Docs Agent 分支为基线后代。
- Docs Agent diff 和集成后 diff 都只显示 `M README.md`。
- `git merge --ff-only` 成功。
- `git diff --check` 无输出。
- 最终工作区干净，分支为 `codex/multi-agent-dry-run-integration`。

## 预期交付物

- Integration worktree、分支和 head commit 信息。
- 集成报告，包含合入分支、文件范围、验证命令、验证结果、冲突情况和残余风险。
- 如 fast-forward 合并成功，不需要新增 commit；如不能 fast-forward，必须停止并向主控报告原因。

## 风险提示

- 主工作区当前存在既有 `docs/agents/workflow.md` 未提交改动，Integration Agent 不得触碰或覆盖该改动。
- 如果 Docs Agent 分支不再是基线后代，必须停止并请求主控重新决策。
- 如果 diff 超出 `README.md`，必须停止并报告 ownership 违规。
- 本次集成只验证 README 文档变更，不验证后端、前端、部署或 RAG 行为。

## 完成后返回格式

```text
状态: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
完成内容:
合并分支:
集成分支:
worktree:
修改文件:
测试命令:
测试结果:
冲突情况:
未覆盖风险:
是否触碰共享文件:
commit hash:
后续建议:
```
