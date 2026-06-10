# integrationplan.md

Agent 名称：Integration Agent

计划状态：Approved

基线 commit：48e29f7

角色：集成 Agent

目标：

- 串行合并 Architect contract、Backend、Frontend、Docs 分支。
- 检查 ownership 冲突、API contract 偏差和安全边界。
- 运行集成级验证并输出报告。

非目标：

- 不跳过子 Agent 的验证结果。
- 不覆盖未授权改动。
- 不直接 push 或 merge master。

## 允许修改

- Integration branch 上为解决冲突所需的已授权文件。
- `docs/agents/runs/2026-06-10-user-llm-provider.md`

## 禁止修改

- 未授权文件。
- `.env`
- 私有密钥。
- `AGENTS.md`
- `docs/agents/workflow.md`

## 输入材料

- 总执行包和所有 Agent 任务卡。
- 子分支列表和 commit hash。
- 各 Agent 标准交付物。

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：integration branch、运行记录、冲突解决

## 依赖前置任务

- Backend/Frontend/Docs Agent 完成。
- 子分支状态干净。

## 验收标准

- 合并顺序有记录。
- 文件所有权无未解释冲突。
- 后端/前端 API contract 对齐。
- 全量验证命令已执行并记录结果。
- 安全敏感信息检查已执行。

## 必须运行的验证

```powershell
git worktree list --porcelain
git status --short --branch
git diff --check
cd backend
mvn test
cd ..\frontend
npx vitest run
npm run build
```

## 预期交付物

- Integration branch commit hash。
- Integration report。
- 验证结果。
- 冲突与解决说明。

## 风险提示

- 当前主工作区存在 `AGENTS.md`、`docs/agents/workflow.md` 未提交改动，集成前必须确认这些改动归属，不得误合入。
- 数据库 migration 冲突必须回主控审核。

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
