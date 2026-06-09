# testing_reviewplan.md

Agent 名称：Testing Review Agent

计划状态：Approved

基线 commit：442ddf29d86a20c32a4c67626dc18b930c0b006a

角色：多 Agent 工作流试运行中的测试审查 Agent，只审查 integration diff 和验证证据，不做实现修改。

目标：确认集成结果是 README-only 文档变更，Docs Agent 与 Integration Agent 的验证命令足以覆盖本次最小试运行，不需要额外后端、前端或 Python 测试。

非目标：不修改文件；不修复文档；不运行长耗时业务测试；不合并分支；不 push；不清理 worktree。

## 允许修改

- 无。Testing Review Agent 只读审查。

## 禁止修改

- `README.md`
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
- `docs/agents/templates/review.md`
- `docs/agents/plans/multi-agent-dry-run/docsplan.md`
- `docs/agents/plans/multi-agent-dry-run/integrationplan.md`
- Integration Agent 集成报告
- 集成分支：`codex/multi-agent-dry-run-integration`

## 依赖前置任务

- Integration Agent 已完成并输出集成报告。
- 用户明确批准本 `testing_reviewplan.md`。
- 主控 Agent 分配独立 Testing Review Agent 线程和只读审查 worktree；在用户批准前不得创建对应线程或 worktree。

## 验收标准

- integration diff 只包含 `README.md`。
- `git diff --check` 无空白错误。
- Docs Agent 和 Integration Agent 的验证结果与实际 diff 一致。
- 若 diff 仍为 README-only，则明确说明后端 `mvn test`、前端 `npm run build` / `npx vitest run`、Python `unittest` 对本次文档小改不是必要验证。
- 若 diff 超出 README 或验证证据缺失，输出阻断问题。

## 必须运行的验证

```powershell
git status --short --branch
git diff --stat 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git diff --name-status 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git diff --check 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git diff 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD -- README.md
```

预期结果：

- `git diff --name-status` 只显示 `M README.md`。
- `git diff --check` 无输出。
- README diff 只新增“多 Agent 工作流试运行说明”相关内容。

## 预期交付物

- Testing Review 报告，包含结论、阻断问题、非阻断问题、缺失测试、残余风险和是否批准进入 Security Review。
- 不产生 commit。

## 风险提示

- 本审查只覆盖文档变更测试合理性，不替代安全、可维护性或发布审查。
- 如果 Integration Agent 未提供完整验证证据，Testing Review Agent 必须标记为 `DONE_WITH_CONCERNS` 或 `BLOCKED`。

## 完成后返回格式

```text
状态: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
结论:
阻断问题:
非阻断问题:
缺失测试:
测试命令:
测试结果:
残余风险:
是否批准进入下一阶段:
commit hash:
后续建议:
```
