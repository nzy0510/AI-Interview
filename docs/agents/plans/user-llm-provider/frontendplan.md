# frontendplan.md

Agent 名称：Frontend Agent

计划状态：Approved

基线 commit：48e29f7

角色：前端实现 Agent

目标：

- 新增侧边栏“大模型配置”模块。
- 实现用户 LLM Provider 配置页面，支持多个配置、一个启用配置、测试连接、脱敏展示。
- 在无启用配置时，为面试和 Mentor 等入口提供清晰引导。

非目标：

- 不实现后端接口。
- 不保存 API Key 到 localStorage/sessionStorage。
- 不实现非 OpenAI-compatible 协议。
- 不实现按模块分模型。

## 允许修改

- `frontend/src/router/**`
- `frontend/src/components/layout/AppShell.vue`
- `frontend/src/views/**`
- `frontend/src/api/**`
- `frontend/src/utils/**`
- `frontend/src/**/__tests__/**`
- `frontend/package.json`、`frontend/package-lock.json` 仅在确有必要且经主控批准时修改

## 禁止修改

- `backend/**`
- `.env`
- 私有配置或密钥
- `AGENTS.md`
- `docs/agents/workflow.md`

## 输入材料

- `docs/agents/plans/user-llm-provider/controllerplan.md`
- Architect API contract（批准后）
- Backend Agent 提供的 DTO/API 行为
- 当前 `Settings.vue`、`AppShell.vue`、`frontend/src/api/user.js` 风格

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：前端路由、页面、API client、前端测试

## 依赖前置任务

- Architect contract 完成。
- Backend API contract 冻结；实现可并行，最终由 Integration 校验。

## 验收标准

- 侧边栏出现“大模型配置”入口。
- 页面可查看配置列表，明文 API Key 不回显。
- 可新增/编辑/删除配置。
- 可启用一个配置，启用后 UI 状态明确。
- 可测试连接，展示成功/失败和脱敏错误。
- Provider 预设至少包含 DeepSeek、Kimi/Moonshot、GLM/Zhipu、Qwen、自定义。
- 未配置时面试和 Mentor 入口能引导用户去配置页面。
- 页面在桌面和常见窄屏下不出现按钮文字溢出或布局重叠。

## 必须运行的验证

```powershell
cd frontend
npx vitest run
npm run build
```

## 预期交付物

- 前端代码提交到 `codex/user-llm-provider-frontend`。
- 修改文件清单。
- 测试/构建结果。
- 交互风险说明。
- commit hash。

## 风险提示

- API Key 输入框必须只向后端发送，不能写入浏览器持久存储。
- 错误消息不能显示完整 API Key。
- 不要引入新 UI 库，使用现有 Vue + Element Plus 风格。

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
