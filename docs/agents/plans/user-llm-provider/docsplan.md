# docsplan.md

Agent 名称：Docs Agent

计划状态：Approved

基线 commit：48e29f7

角色：文档 Agent

目标：

- 更新用户自定义 LLM API 配置说明。
- 说明 OpenAI-compatible Provider、加密密钥、无兜底行为和本地/Docker 配置方式。

非目标：

- 不修改业务代码。
- 不提交真实 `.env` 或任何私有 API Key。

## 允许修改

- `README.md`
- `DEPLOYMENT.md`
- `.env.example`
- `.env.prod.example`
- 必要时 `docs/**`

## 禁止修改

- `backend/**`
- `frontend/**`
- `.env`
- 私有部署文件
- `AGENTS.md`
- `docs/agents/workflow.md`

## 输入材料

- `docs/agents/plans/user-llm-provider/controllerplan.md`
- Architect contract
- Backend/Frontend 最终行为说明

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：文档和示例配置

## 依赖前置任务

- Backend/Frontend 行为稳定后再最终落文档。

## 验收标准

- 文档说明用户必须配置自己的 API 后才能使用用户侧 LLM 功能。
- 文档说明支持 OpenAI-compatible API，列出 DeepSeek/Kimi/GLM/Qwen/自定义。
- 文档说明 API Key 后端加密存储，管理员不可见明文。
- 示例配置只包含加密密钥变量名，不包含真实密钥。
- 文档与实际 UI/API 行为一致。

## 必须运行的验证

```powershell
git diff --check
rg -n "sk-[A-Za-z0-9_-]{12,}|Bearer\\s+[A-Za-z0-9._-]+|DEEPSEEK_API_KEY=.*[A-Za-z0-9]" README.md DEPLOYMENT.md .env.example .env.prod.example docs
```

## 预期交付物

- 文档提交到 `codex/user-llm-provider-docs`。
- 修改文件清单。
- 验证结果。
- commit hash。

## 风险提示

- 不要把用户自己的私有 API Key 写入示例。
- 如果后端最终配置变量名改变，必须同步文档。

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
