# testing_reviewplan.md

Agent 名称：Testing Review Agent

计划状态：Approved

基线 commit：48e29f7

角色：测试审查 Agent

目标：

- 审查 integration diff 的测试覆盖和真实执行结果。
- 判断是否覆盖无配置阻断、加密存储、配置 CRUD、测试连接、面试/评估/Mentor 全覆盖。

非目标：

- 不实现功能。
- 不修改测试，除非主控明确派发 review-fix。

## 允许修改

- 默认只读。
- 如主控批准，可修改 `docs/agents/runs/2026-06-10-user-llm-provider.md` 的审查记录。

## 禁止修改

- `backend/**`
- `frontend/**`
- `.env`
- 私有密钥

## 输入材料

- Integration diff。
- 总执行包。
- Backend/Frontend 测试结果。

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：测试审查报告

## 依赖前置任务

- Integration Agent 完成初步合并。

## 验收标准

- 明确阻断/非阻断测试问题。
- 明确缺失测试。
- 结论为批准或不批准进入下一阶段。

## 必须运行的验证

```powershell
cd backend
mvn test
cd ..\frontend
npx vitest run
npm run build
```

## 预期交付物

- Testing Review report。

## 风险提示

- 核心 LLM 调用链缺少测试属于阻断。
- 未配置用户仍能调用 LLM 属于阻断。

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
