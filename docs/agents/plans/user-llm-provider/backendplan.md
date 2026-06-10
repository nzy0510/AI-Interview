# backendplan.md

Agent 名称：Backend Agent

计划状态：Approved

基线 commit：48e29f7

角色：后端实现 Agent

目标：

- 实现用户 LLM Provider 配置的加密存储、CRUD、启用、测试连接和状态查询。
- 将面试追问、报告评估、AI Mentor 的 LLM 调用切换为当前用户启用配置。
- 用户无启用配置时阻断用户侧 LLM 功能，不使用系统默认 API Key。

非目标：

- 不支持非 OpenAI-compatible 协议。
- 不实现前端页面。
- 不修改 embedding、Qdrant、RAG 检索。
- 不保留系统默认模型兜底。

## 允许修改

- `backend/src/main/java/com/interview/config/ChatConfig.java`
- `backend/src/main/java/com/interview/controller/**`
- `backend/src/main/java/com/interview/dto/**`
- `backend/src/main/java/com/interview/entity/**`
- `backend/src/main/java/com/interview/mapper/**`
- `backend/src/main/java/com/interview/service/**`
- `backend/src/main/resources/db/migration/**`
- `backend/src/test/**`
- `.env.example`、`.env.prod.example` 仅在 Integration/Docs 同意后更新示例变量

## 禁止修改

- `frontend/**`
- `.env`
- 私有密钥或本地配置文件
- `AGENTS.md`
- `docs/agents/workflow.md`
- `backend/src/main/resources/knowledge_base/**`

## 输入材料

- `docs/agents/plans/user-llm-provider/controllerplan.md`
- Architect contract（批准后）
- 当前 `ChatConfig`、`InterviewServiceImpl`、`EvaluationGenerator`、`MentorService`
- 当前 `UserController` / `UserServiceImpl` 风格

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：后端代码、migration、后端测试

## 依赖前置任务

- Architect Agent 完成并冻结 API/schema contract。

## 验收标准

- 新增 `user_llm_config` 或等价表，Flyway migration 可重复验证。
- API Key 使用服务端密钥加密存储；缺少加密密钥时不得静默明文降级。
- DTO 返回脱敏 key 摘要，不返回明文或密文。
- 当前用户只能访问自己的配置。
- 每用户最多一个启用配置。
- 测试连接支持已保存配置和临时输入，错误信息脱敏。
- `InterviewServiceImpl.chatStream` 使用用户启用配置创建 Streaming Chat Model。
- `EvaluationGenerator` 或等价服务按用户配置生成报告。
- `MentorService` 按用户配置生成 AI Mentor。
- 无启用配置时，受影响接口返回明确错误。
- 事件日志和异常日志不包含完整 API Key。

## 必须运行的验证

```powershell
cd backend
mvn -Dtest=UserLlmConfigServiceTest,UserLlmConfigControllerTest,InterviewServiceImplTest,MentorServiceTest test
mvn test
```

## 预期交付物

- 后端代码提交到 `codex/user-llm-provider-backend`。
- 修改文件清单。
- 测试结果。
- 安全边界说明。
- commit hash。

## 风险提示

- `ChatConfig` 当前为全局单例模型，不能继续让用户侧 LLM 依赖全局 API Key。
- `EvaluationGenerator` 当前构造器持有固定 `ChatLanguageModel`，可能需要改为接收模型工厂或在方法级传入模型。
- `MentorService` 有 24h 缓存，切换用户 LLM 配置后可能需要清理或按配置版本区分缓存。
- 测试连接不能记录完整 API Key。

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
