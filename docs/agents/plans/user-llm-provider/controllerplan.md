# User LLM Provider Controller Plan

Agent 名称：Controller Agent

计划状态：Approved

基线 commit：48e29f7

角色：主控 Agent

## 调度判定

任务等级：L3 + High-risk

调度模式：完整多 Agent，裁剪 RAG/Data 与 Performance Review

是否启用多 Agent：是

启用的 Agent：

- Controller Agent
- Architect Agent
- Backend Agent
- Frontend Agent
- Docs Agent
- Integration Agent
- Testing Review Agent
- Security Review Agent
- Maintainability Review Agent
- Release Agent（仅最终提交、推送、PR 或清理 worktree 时启用）

不启用 RAG/Data 的理由：V1 不修改 embedding、Qdrant、题库检索或知识原子。

不启用 Performance Review 的理由：V1 不引入批量推理、高频轮询或大规模数据处理；若测试连接或配置加载实现为高频路径，再升级启用。

人工审核节点：

- 用户批准本总执行包后，才创建子线程和 worktree。
- 涉及 API Key 加密密钥、数据库 migration、全局 LLM 调用链替换、无配置时阻断范围变更时，必须回到用户审核。
- 最终 merge、push、发布前必须回到用户审核。

自动继续范围：

- 在批准的 ownership 内创建分支/worktree。
- 派发任务卡给对应 Agent。
- 子 Agent 在授权文件范围内实现、自测、提交到任务分支。
- Integration Agent 串行合并并运行验证。
- Review Agent 只读审查 integration diff。

## 目标

为 InterWise 增加用户自定义 OpenAI-compatible 大模型 API 配置能力：

- 侧边栏新增“大模型配置”模块。
- 每个用户可保存多个 Provider 配置。
- 每个用户同一时间只有一个当前启用配置。
- 无启用配置时，阻止所有用户侧 LLM 功能并引导配置。
- 支持 DeepSeek、Kimi/Moonshot、GLM/Zhipu、Qwen 和自定义 OpenAI-compatible 预设。
- API Key 后端加密存储，前端和管理员不可见明文。
- 提供测试连接能力。
- 面试追问、报告评估、AI Mentor 等用户触发文本生成能力使用当前用户启用配置。

## 非目标

- 不支持非 OpenAI-compatible 协议（Claude/Gemini 原生协议等）。
- 不支持按模块选择不同模型。
- 不修改 embedding、Qdrant、RAG 检索、题库数据。
- 不让管理员查看或导出 API Key 明文。
- 不保留系统默认模型作为普通用户兜底。
- 不提交 `.env`、私有密钥、临时产物或本地部署配置。

## 验收标准

- 未配置启用 LLM Provider 的用户无法开始文字/视频面试，无法生成报告，无法生成 AI Mentor；接口返回明确可处理错误。
- 用户可在“大模型配置”页面新增、编辑、删除、启用 Provider 配置。
- 保存 API Key 后，前端只能看到脱敏摘要和配置状态。
- 测试连接可验证当前输入或已保存配置，错误信息脱敏。
- DeepSeek、Kimi、GLM、Qwen、自定义 OpenAI-compatible 均以预设模板形式可选。
- 后端数据库只存加密后的 API Key。
- 日志、事件、异常响应不包含完整 API Key、Bearer token 或密文。
- 现有用户偏好、面试流程、报告、Mentor 页面不出现无关回归。
- 后端、前端、集成测试和安全审查通过。

## 影响范围

- 后端 LLM 配置和模型工厂。
- 后端用户 Provider 配置 CRUD、测试连接 API。
- 后端面试、评估、Mentor 调用链。
- 数据库 migration 与实体/Mapper/DTO。
- 前端侧边栏、路由、配置页面、API client。
- 文档与部署说明中的环境变量和安全边界。

## 任务拆分

1. Architect Agent：输出用户 LLM 配置架构、API contract、数据库 schema、调用链替换方案和风险清单。
2. Backend Agent：实现 schema、加密、配置 CRUD、测试连接、按用户创建模型、替换面试/评估/Mentor 调用链、后端测试。
3. Frontend Agent：实现侧边栏入口、路由、配置页面、Provider 预设、表单校验、测试连接交互、前端测试/构建。
4. Docs Agent：更新 README/部署说明/安全说明，说明自定义 API 配置、加密密钥和无兜底行为。
5. Integration Agent：串行合并 Backend、Frontend、Docs，处理 contract 偏差并运行集成验证。
6. Testing Review Agent：复核测试覆盖与真实执行结果。
7. Security Review Agent：审查 API Key 加密、脱敏、权限、日志、管理员边界。
8. Maintainability Review Agent：审查模型工厂抽象、Controller/Service 职责、前端组件复杂度。
9. Release Agent：最终提交整理、push/PR/release 或 worktree 清理时启用。

## 文件所有权矩阵

| 路径 | Owner | 允许并行修改 | 说明 |
| --- | --- | --- | --- |
| `backend/src/main/java/com/interview/config/ChatConfig.java` | Backend Agent | 否 | 拆除用户侧 LLM 对全局单例模型的依赖，保留 embedding/session 配置 |
| `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java` | Backend Agent | 否 | 用户面试追问必须使用当前用户启用配置 |
| `backend/src/main/java/com/interview/service/EvaluationGenerator.java` | Backend Agent | 否 | 评估报告按用户配置生成 |
| `backend/src/main/java/com/interview/service/MentorService.java` | Backend Agent | 否 | AI Mentor 按用户配置生成 |
| `backend/src/main/java/com/interview/controller/**` | Backend Agent | 否 | 新增 LLM 配置 API，必要时调整入口阻断 |
| `backend/src/main/java/com/interview/dto/**` | Backend Agent | 否 | 新增配置 DTO，避免返回密钥明文 |
| `backend/src/main/java/com/interview/entity/**` | Backend Agent | 否 | 新增 LLM 配置实体 |
| `backend/src/main/java/com/interview/mapper/**` | Backend Agent | 否 | 新增 Mapper |
| `backend/src/main/resources/db/migration/**` | Backend Agent | 否 | 新增配置表 migration |
| `backend/src/test/**` | Backend Agent | 否 | 后端单元/控制器/安全测试 |
| `frontend/src/router/**` | Frontend Agent | 否 | 新增页面路由 |
| `frontend/src/components/layout/AppShell.vue` | Frontend Agent | 否 | 侧边栏入口 |
| `frontend/src/views/**` | Frontend Agent | 否 | 新增大模型配置页，必要时接入阻断提示 |
| `frontend/src/api/**` | Frontend Agent | 否 | 新增 LLM 配置 API client |
| `frontend/src/utils/**` | Frontend Agent | 否 | Provider 预设和表单辅助逻辑 |
| `frontend/src/**/__tests__/**` | Frontend Agent | 否 | 前端测试 |
| `README.md`, `DEPLOYMENT.md`, `.env.example`, `.env.prod.example`, `docker-compose*.yml` | Docs/Integration Agent | 否 | 仅文档/示例配置；实际配置变更需人工审核 |
| `docs/agents/plans/user-llm-provider/**` | Controller Agent | 否 | 计划与任务卡 |
| `docs/agents/runs/2026-06-10-user-llm-provider.md` | Controller/Integration Agent | 否 | 运行记录 |

## 分支/worktree 映射

目标基线：48e29f7

目标分支：`master` 当前状态，但最终合并前需确认 `AGENTS.md`、`docs/agents/workflow.md` 未提交改动的归属。

拟建分支：

| Agent | Branch | Worktree |
| --- | --- | --- |
| Architect | read-only 或 `codex/user-llm-provider-architect` | Codex 原生 worktree 或只读线程 |
| Backend | `codex/user-llm-provider-backend` | Codex 原生 worktree |
| Frontend | `codex/user-llm-provider-frontend` | Codex 原生 worktree |
| Docs | `codex/user-llm-provider-docs` | Codex 原生 worktree |
| Integration | `codex/user-llm-provider-integration` | Codex 原生 worktree |
| Reviews | read-only integration diff | 不写 worktree |
| Release | integration 或 release branch | 按最终发布动作决定 |

创建前必须运行：

```powershell
git worktree list --porcelain
git status --short --branch
```

创建后必须记录：

```powershell
git -C <worktree> status --short --branch
```

## API Contract 草案

后端路由建议：

- `GET /api/llm/providers/presets`：返回内置 Provider 预设，不含密钥。
- `GET /api/llm/configs`：当前用户配置列表，返回脱敏 key 摘要。
- `POST /api/llm/configs`：新增配置。
- `PUT /api/llm/configs/{id}`：更新配置；未传 key 时保留原 key。
- `DELETE /api/llm/configs/{id}`：删除当前用户配置；删除启用项后用户进入未配置状态。
- `POST /api/llm/configs/{id}/activate`：启用某个配置。
- `POST /api/llm/configs/test`：测试临时输入或已保存配置。
- `GET /api/llm/configs/status`：返回当前用户是否已配置、启用 provider、模型名。

DTO 必须不返回明文 API Key。

## 数据模型草案

新表建议：`user_llm_config`

字段草案：

- `id`
- `user_id`
- `provider`
- `display_name`
- `base_url`
- `model_name`
- `encrypted_api_key`
- `api_key_hint`
- `temperature`
- `enabled`
- `last_test_status`
- `last_test_message`
- `last_test_time`
- `create_time`
- `update_time`

约束：

- `user_id + display_name` 或 `user_id + provider + model_name` 唯一策略由 Architect 最终确认。
- 每个用户最多一个 `enabled=true`，MySQL 可通过事务更新保证。

配置项：

- 新增 `APP_LLM_CONFIG_ENCRYPTION_KEY` 或等价环境变量。
- 缺失加密密钥时应用应启动失败，避免明文降级。

## 验证命令

后端：

```powershell
cd backend
mvn -Dtest=UserLlmConfigServiceTest,UserLlmConfigControllerTest,InterviewServiceImplTest,MentorServiceTest test
mvn test
```

前端：

```powershell
cd frontend
npx vitest run
npm run build
```

集成：

```powershell
docker compose build backend frontend
docker compose up -d
curl.exe -s http://127.0.0.1:18080/api/health
```

安全检查：

```powershell
git diff --check
rg -n "sk-[A-Za-z0-9_-]{12,}|Bearer\\s+[A-Za-z0-9._-]+|apiKey\\s*[:=]\\s*['\\\"]" backend frontend docs
```

## 高风险操作

- 数据库 migration。
- API Key 加密密钥配置。
- 替换全局 LLM 单例为按用户动态模型。
- 错误日志和事件日志脱敏。
- 前端/管理员不可见密钥边界。

## 必须回到用户审核的触发条件

- 需要支持非 OpenAI-compatible Provider。
- 需要保留系统默认模型兜底。
- 需要按模块选择不同模型。
- 需要修改认证授权、JWT、拦截器或管理员权限。
- 需要修改 `.env` 实际私有配置。
- 需要新增外部依赖或更换 LLM SDK。
- 需要变更数据库 schema 草案中的核心字段。
- 测试失败或安全审查阻断。
- 准备 merge、push、发布或清理有未提交内容的 worktree。

## 回滚方案

- 代码回滚：回退 integration branch 或 revert 子分支提交。
- 数据库：新增表不影响现有表；如需回滚，保留表但代码不引用，避免破坏用户密钥数据。
- 配置：移除侧边栏入口和 API 调用后，前端不再触发该模块。
- 运行时：若动态 LLM 失败，接口返回“请检查大模型配置”，不切回系统默认 key。

## 待审核总执行包

状态：Approved，用户已在主控线程明确回复“审核通过，开始下一步”。

用户批准前不得：

- 创建子线程。
- 创建 worktree。
- 派发实现任务。
- 修改业务代码。
- 提交或 push 计划文件。
