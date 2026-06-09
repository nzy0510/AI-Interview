# maintainability_reviewplan.md

Agent 名称：Maintainability Review Agent

计划状态：Approved

基线 commit：442ddf29d86a20c32a4c67626dc18b930c0b006a

角色：多 Agent 工作流试运行中的可维护性审查 Agent，只审查 README 文档变更是否清晰、简洁并符合 `AGENTS.md` 与 `docs/agents/workflow.md`。

目标：确认新增“多 Agent 工作流试运行说明”准确描述最小试运行边界、ownership、子 Agent 隔离、Integration 串行收口和 Review 串行审查，不引入误导性流程承诺或过度复杂说明。

非目标：不修改文件；不改写 README；不审查后端、前端、部署或 RAG 实现；不合并分支；不 push；不清理 worktree。

## 允许修改

- 无。Maintainability Review Agent 只读审查。

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
- Integration Agent 集成报告
- Testing Review 报告
- Security Review 报告
- 集成分支：`codex/multi-agent-dry-run-integration`

## 依赖前置任务

- Integration Agent 已完成并输出集成报告。
- Testing Review 和 Security Review 已完成，或主控确认可并行审查同一 integration diff。
- 用户明确批准本 `maintainability_reviewplan.md`。
- 主控 Agent 分配独立 Maintainability Review Agent 线程和只读审查 worktree；在用户批准前不得创建对应线程或 worktree。

## 验收标准

- README 新增小节标题为“多 Agent 工作流试运行说明”。
- 新增内容明确本次是流程试运行，不是复杂业务交付。
- 新增内容明确主控线程、Docs Agent、Integration Agent、Review Agent 的 ownership。
- 新增内容明确子 Agent 不直接合并 `master`。
- 新增内容与 `AGENTS.md` 的中文沟通、保护未提交改动、禁止敏感文件规则一致。
- 新增内容与 `docs/agents/workflow.md` 的 worktree 隔离、计划审核门禁、串行集成和串行审查规则一致。
- 文案简洁，不把未执行的发布、部署或测试说成已完成。

## 必须运行的验证

```powershell
git status --short --branch
git diff --name-status 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git diff 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD -- README.md
Select-String -Path README.md -Pattern '多 Agent 工作流试运行说明|主控|worktree|Integration Agent|Review Agent|master'
```

预期结果：

- `git diff --name-status` 只显示 `M README.md`。
- `Select-String` 能定位试运行小节中的关键流程角色和约束。
- 文档未声称已完成未实际执行的 release、push、部署或业务测试。

## 预期交付物

- Maintainability Review 报告，包含结论、阻断问题、非阻断问题、缺失测试、残余风险和是否批准进入最终主控决策。
- 不产生 commit。

## 风险提示

- README 是用户入口文档，过长会降低可维护性；审查时应优先保证说明短小、准确。
- 本审查不替代性能审查。由于本次是 README-only 文档变更，Performance Review 默认不启用。

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
