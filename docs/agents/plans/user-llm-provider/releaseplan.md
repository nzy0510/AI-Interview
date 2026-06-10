# releaseplan.md

Agent 名称：Release Agent

计划状态：Approved

基线 commit：48e29f7

角色：分支/发布 Agent

目标：

- 在用户批准最终集成结果后，整理提交、推送、PR 或清理 worktree。

非目标：

- 不在 review 通过前推送。
- 不清理有未提交改动的 worktree。
- 不合并未授权文件。

## 允许修改

- `CHANGELOG.md`（如用户要求）
- `docs/agents/runs/2026-06-10-user-llm-provider.md`
- Release notes

## 禁止修改

- `.env`
- 私有密钥
- 未授权业务代码
- 有争议的 `AGENTS.md`、`docs/agents/workflow.md` 未提交改动

## 输入材料

- Integration report。
- Review reports。
- 用户最终批准。

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：最终分支/发布收口

## 依赖前置任务

- Integration 和 Review 全部通过。
- 用户批准 merge/push/release。

## 验收标准

- `git status --short --branch` 清楚说明。
- staged diff 范围明确。
- 最终验证结果记录完整。
- worktree 清理前全部干净。

## 必须运行的验证

```powershell
git status --short --branch
git diff --cached --name-status
git worktree list --porcelain
```

## 预期交付物

- 最终 commit hash。
- push/PR/release 结果（如执行）。
- worktree cleanup 记录。

## 风险提示

- 当前 `master` ahead 5，发布前需确认目标远端策略。
- 不得把既有未提交文档改动误纳入本任务。

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
