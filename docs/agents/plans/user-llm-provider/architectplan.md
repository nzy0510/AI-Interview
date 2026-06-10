# architectplan.md

Agent 名称：Architect Agent

计划状态：Approved

基线 commit：48e29f7

角色：架构设计与契约 Agent

目标：

- 输出用户自定义 OpenAI-compatible LLM 配置的架构方案。
- 冻结 Backend/Frontend 可并行实现的 API contract、DTO、数据模型和调用链边界。
- 明确密钥加密、脱敏、无兜底阻断和全覆盖范围。

非目标：

- 不写业务实现代码。
- 不创建数据库 migration。
- 不修改前端页面。

## 允许修改

- `docs/agents/plans/user-llm-provider/architectplan.md`
- 如主控批准，可新增最小 contract 文档：`docs/agents/plans/user-llm-provider/contract.md`

## 禁止修改

- `backend/**`
- `frontend/**`
- `.env*`
- `docker-compose*.yml`
- `AGENTS.md`
- `docs/agents/workflow.md`

## 输入材料

- `docs/agents/plans/user-llm-provider/controllerplan.md`
- `backend/src/main/java/com/interview/config/ChatConfig.java`
- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
- `backend/src/main/java/com/interview/service/EvaluationGenerator.java`
- `backend/src/main/java/com/interview/service/MentorService.java`
- `frontend/src/views/Settings.vue`
- `frontend/src/components/layout/AppShell.vue`

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：架构契约与设计文档

## 依赖前置任务

- 用户批准总执行包。

## 验收标准

- 明确 V1 支持 OpenAI-compatible Provider。
- 明确所有用户侧 LLM 调用点。
- 明确无启用配置时各入口的阻断方式。
- 明确 API DTO 不返回明文 API Key。
- 明确数据库表字段、索引、唯一约束和启用项策略。
- 明确后端模型工厂/服务抽象，不把动态模型逻辑堆到 Controller。
- 明确测试连接的请求、响应和脱敏策略。

## 必须运行的验证

```powershell
git status --short --branch
git worktree list --porcelain
```

## 预期交付物

- 设计摘要。
- 受影响模块。
- API contract。
- 数据模型/DTO。
- 共享文件清单。
- 并行拆分建议。
- 风险点。

## 风险提示

- 如果 Architect 发现 AI Mentor 或其他 LLM 调用点未在总执行包覆盖，必须回报主控更新范围。
- 如果需要引入新依赖或非 OpenAI-compatible 协议，必须回到用户审核。

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
