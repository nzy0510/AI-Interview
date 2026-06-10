# User LLM Provider Architect Notes

Feature: `user-llm-provider`

Agent: Architect Agent

Thread: delegated from `019eac57-da63-79d0-be40-d5e6e77e5a1e`

Worktree: `C:\Users\nzy\.codex\worktrees\ee59\interview`

Branch: `codex/user-llm-provider-architect`

Observed status:

- Initial `git status --short --branch`: `## HEAD (no branch)`.
- `git worktree list --porcelain` showed this Codex worktree was detached at `27c6d99`, while main checkout `E:/Develop/interview` was on `master`.
- Switched only this Codex worktree to `codex/user-llm-provider-architect`; no merge, push, or main checkout changes.
- Current task cards record base commit `48e29f7`, but this worktree HEAD is `27c6d99`. Integration must re-check branch ancestry before merging.

## 设计摘要

V1 应把“用户自定义 OpenAI-compatible LLM Provider”作为用户级运行时配置，而不是全局系统配置的补充。用户侧所有文本生成入口必须先解析当前登录用户的 active 配置；没有 active 配置时直接阻断并返回可处理错误，不得回退到 `DEEPSEEK_API_KEY` 或 `langchain4j.open-ai.chat-model.*`。

推荐后端边界：

- `UserLlmConfigController`: 只处理 HTTP DTO、当前用户 ID、权限边界。
- `UserLlmConfigService`: CRUD、启用事务、配置归属校验、密钥加密/脱敏、状态查询。
- `UserLlmModelFactory`: 根据当前用户 active 配置创建 `OpenAiChatModel` / `OpenAiStreamingChatModel`，对调用方隐藏解密细节。
- `LlmConfigCryptoService`: 服务端加密/解密 API Key，不记录明文或密文。
- `LlmConnectionTestService`: 测试临时输入或已保存配置，输出脱敏结果。
- `LlmProviderRequiredException`: 统一表达“用户未配置 active LLM Provider”，由全局异常映射为固定错误语义。

`ChatConfig` 不应继续提供用户侧全局 `OpenAiChatModel` / `OpenAiStreamingChatModel` 单例。它可以保留 embedding、`SessionStore` 等非用户 LLM bean；用户侧模型必须从 `UserLlmModelFactory` 按 userId 创建。

## 受影响 LLM 调用点

必须覆盖：

- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
  - `startInterview(...)`: 文字/视频面试创建前先确认用户有 active 配置，否则不创建记录、不消耗开始面试额度。
  - `chatStream(...)`: SSE 追问使用当前用户 streaming model；无配置时返回 SSE 可识别错误。
  - `completeInterview(...)`: 结束面试生成报告前使用当前用户 chat model；无配置时失败语义必须清晰，不写入伪报告。
  - 后台 Mentor 预计算要继承同一用户配置语义，不能吞掉配置缺失后生成旧缓存。
- `backend/src/main/java/com/interview/service/EvaluationGenerator.java`
  - 当前构造器持有固定 `ChatLanguageModel`，应改成方法级 userId 或 model 参数，或注入 `UserLlmModelFactory` 后按 record.userId 取模型。
- `backend/src/main/java/com/interview/service/MentorService.java`
  - 当前直接注入 `ChatLanguageModel`，应改为按 userId 创建模型。
  - 24h 缓存需要按 active 配置版本隔离或在配置变更后清理，否则切换 provider 后可能继续展示旧模型生成内容。
- `backend/src/main/java/com/interview/service/impl/ResumeServiceImpl.java`
  - 当前直接注入 `ChatLanguageModel` 生成简历画像。虽然原计划未显式列出，但这是用户触发的文本生成能力，必须纳入动态 Provider 和无配置阻断。
  - `ResumeService.parseAndAnalyze(...)` 需要接收 userId，或 Controller 先检查 active 配置并把模型/上下文传入 Service。

不应覆盖：

- `InterviewRetrievalService`、Qdrant、embedding-service、题库导入和 reindex。它们不是用户自定义聊天模型调用。
- `MentorService.getKnowledgeCoverageOnly(...)`，它只查库，不调 LLM，应在无配置时仍可访问。

## 后端 API Contract

建议路由统一放在 `/api/llm/configs`，所有接口走现有 JWT 用户身份，不提供管理员查看用户配置的接口。

### Presets

`GET /api/llm/providers/presets`

返回内置预设，不含密钥：

```json
[
  {
    "provider": "deepseek",
    "label": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/v1",
    "defaultModel": "deepseek-chat",
    "models": ["deepseek-chat"],
    "supportsCustomModel": true
  }
]
```

V1 预设至少包含：

- `deepseek`: `https://api.deepseek.com/v1`, `deepseek-chat`
- `moonshot`: `https://api.moonshot.cn/v1`, `moonshot-v1-8k`
- `zhipu`: `https://open.bigmodel.cn/api/paas/v4`, `glm-4-flash`
- `qwen`: `https://dashscope.aliyuncs.com/compatible-mode/v1`, `qwen-plus`
- `custom`: baseUrl/model 由用户输入

### List

`GET /api/llm/configs`

返回当前用户自己的配置列表，不返回明文或密文 API Key：

```json
[
  {
    "id": 1,
    "provider": "deepseek",
    "displayName": "DeepSeek main",
    "baseUrl": "https://api.deepseek.com/v1",
    "modelName": "deepseek-chat",
    "temperature": 0.3,
    "active": true,
    "apiKeyHint": "sk-...abcd",
    "lastTestStatus": "SUCCESS",
    "lastTestMessage": "连接成功",
    "lastTestTime": "2026-06-10T18:30:00",
    "createdAt": "2026-06-10T18:00:00",
    "updatedAt": "2026-06-10T18:30:00"
  }
]
```

### Status

`GET /api/llm/configs/status`

用于前端入口阻断：

```json
{
  "configured": true,
  "activeConfigId": 1,
  "provider": "deepseek",
  "displayName": "DeepSeek main",
  "modelName": "deepseek-chat"
}
```

无配置：

```json
{
  "configured": false,
  "activeConfigId": null,
  "provider": null,
  "displayName": null,
  "modelName": null
}
```

### Create

`POST /api/llm/configs`

```json
{
  "provider": "deepseek",
  "displayName": "DeepSeek main",
  "baseUrl": "https://api.deepseek.com/v1",
  "modelName": "deepseek-chat",
  "apiKey": "sk-...",
  "temperature": 0.3,
  "activate": true
}
```

规则：

- `apiKey` 必填。
- `baseUrl` 必须是 `https://`，本地开发如需 `http://localhost` 需要显式代码白名单，不默认放开公网 http。
- `activate=true` 时同一事务内关闭该用户其他 active 配置。
- 响应返回脱敏后的配置对象。

### Update

`PUT /api/llm/configs/{id}`

```json
{
  "provider": "moonshot",
  "displayName": "Kimi",
  "baseUrl": "https://api.moonshot.cn/v1",
  "modelName": "moonshot-v1-8k",
  "apiKey": null,
  "temperature": 0.2
}
```

规则：

- 只能更新当前用户自己的配置。
- `apiKey` 缺省、null 或空字符串表示保留原密钥。
- 如果传入新 `apiKey`，重新加密并刷新 `apiKeyHint`。
- 配置变更后清理该用户 Mentor 缓存，或更新 `configVersion` 让缓存 key 自动隔离。

### Activate

`POST /api/llm/configs/{id}/activate`

规则：

- 只能启用当前用户自己的配置。
- 每个用户最多一个 active；同一事务中先关闭本用户其他配置，再启用目标配置。
- 启用后清理该用户 Mentor 缓存。

### Delete

`DELETE /api/llm/configs/{id}`

规则：

- 只能删除当前用户自己的配置。
- 删除 active 配置后用户进入无配置状态；不得自动启用其他配置，避免意外切换模型。
- 删除后清理该用户 Mentor 缓存。

### Test

`POST /api/llm/configs/test`

支持两种模式：

```json
{
  "configId": 1
}
```

或：

```json
{
  "provider": "custom",
  "baseUrl": "https://example.com/v1",
  "modelName": "custom-model",
  "apiKey": "sk-...",
  "temperature": 0.3
}
```

响应：

```json
{
  "success": true,
  "message": "连接成功",
  "latencyMs": 842,
  "modelName": "deepseek-chat"
}
```

失败响应仍必须脱敏：

```json
{
  "success": false,
  "message": "连接失败：认证失败或模型不可用",
  "latencyMs": 1200,
  "modelName": "deepseek-chat"
}
```

测试连接不得写日志输出完整 API Key、Authorization header、密文或供应商返回中的敏感片段。建议只发送极短 prompt，并限制 timeout。

## 数据模型

建议新增表：`user_llm_config`

字段：

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `user_id BIGINT NOT NULL`
- `provider VARCHAR(32) NOT NULL`
- `display_name VARCHAR(80) NOT NULL`
- `base_url VARCHAR(255) NOT NULL`
- `model_name VARCHAR(120) NOT NULL`
- `encrypted_api_key TEXT NOT NULL`
- `api_key_hint VARCHAR(32) NOT NULL`
- `temperature DECIMAL(3,2) NOT NULL DEFAULT 0.30`
- `active TINYINT(1) NOT NULL DEFAULT 0`
- `config_version BIGINT NOT NULL DEFAULT 1`
- `last_test_status VARCHAR(20) NULL`
- `last_test_message VARCHAR(255) NULL`
- `last_test_time DATETIME NULL`
- `create_time DATETIME NOT NULL`
- `update_time DATETIME NOT NULL`

索引/约束：

- `idx_user_llm_config_user_id (user_id)`
- `idx_user_llm_config_user_active (user_id, active)`
- `uk_user_llm_config_display_name (user_id, display_name)`

MySQL 不能简单用跨数据库可移植的 partial unique index 保证“每用户一个 active”。V1 用事务保证：

1. `UPDATE user_llm_config SET active = 0 WHERE user_id = ?`
2. `UPDATE user_llm_config SET active = 1, config_version = config_version + 1 WHERE id = ? AND user_id = ?`

若要更强约束，可增加 `active_slot` 生成列或单独 `user_llm_active_config` 表，但 V1 不建议增加复杂度。

## 加密与不可见约束

- 新增环境变量建议命名 `APP_LLM_CONFIG_ENCRYPTION_KEY`。
- 后端启动时必须校验密钥存在且满足长度要求；缺失时启动失败。不能降级为明文，也不能复用 JWT secret、admin token 或 analytics salt。
- 加密建议使用 JDK 标准 `AES/GCM/NoPadding`，每条 API Key 独立随机 IV；存储格式可为 `v1:<base64(iv)>:<base64(ciphertext)>`。
- DTO、日志、事件、异常响应、前端状态都不得包含明文 API Key 或 `encrypted_api_key`。
- 管理员接口不得新增“查看用户 LLM 配置”的能力；即使开发者白名单账号也只能管理自己的配置。
- `apiKeyHint` 只用于用户识别自己的 key，例如 `sk-...abcd`。不要保存过长前缀。

## 无配置失败语义

建议统一错误：

- HTTP status: `409 Conflict`
- Result code: `409`
- Message: `请先配置大模型 API`
- Optional data:

```json
{
  "errorCode": "LLM_CONFIG_REQUIRED",
  "action": "OPEN_LLM_CONFIG"
}
```

适用普通 JSON 接口：

- `POST /api/interview/start`
- `POST /api/interview/finish`
- `GET /api/user/mentor-insight`
- `POST /api/user/mentor-insight/refresh`
- `POST /api/resume/parse`

SSE 接口 `GET /api/interview/chatStream` 无法可靠使用普通 JSON Result，应发送一次 JSON event 后 complete：

```json
{
  "error": "LLM_CONFIG_REQUIRED",
  "message": "请先配置大模型 API",
  "action": "OPEN_LLM_CONFIG"
}
```

阻断顺序建议：

- `startInterview`: 先检查 active 配置，再消耗 `INTERVIEW_START` 额度，再创建面试记录。
- `chatStream`: 先检查 active 配置，再消耗 `AI_CHAT_TURN` 额度。
- `finishInterview`: 先检查 active 配置，再生成评估；失败不要写入伪评分。
- `mentor refresh`: 先检查 active 配置，再消耗 `MENTOR_GENERATE` 额度。
- `resume parse`: 文件基础校验后先检查 active 配置，再消耗 `RESUME_PARSE` 额度和调用 LLM。

## 前端页面边界

新增页面建议：

- 路由：`/llm-providers`
- 侧边栏入口：`AppShell.vue` 新增“大模型配置”，图标使用现有 Element Plus 图标。
- API client：新增 `frontend/src/api/llmConfig.js`。
- Provider 预设：可放 `frontend/src/utils/llmProviderPresets.js`，但以后端 `GET /api/llm/providers/presets` 为准。

页面能力：

- 列表展示多个配置，突出 active 配置。
- 新增/编辑表单支持 provider preset、displayName、baseUrl、modelName、apiKey、temperature、是否保存后启用。
- 编辑已保存配置时 API Key 输入框为空，提示“留空表示不更换”，不得回显明文、不得写 localStorage/sessionStorage。
- 连接测试支持保存前临时测试和已保存配置测试。
- 删除 active 配置后 UI 立即进入未配置状态。

入口阻断：

- `InterviewSetup.vue` 开始文字/视频面试前调用 status 或使用全局缓存状态；无配置时引导到 `/llm-providers`。
- `Interview.vue` / `VideoInterview.vue` 对 `LLM_CONFIG_REQUIRED` SSE error 做明确跳转。
- `Mentor.vue` 对 409 / `LLM_CONFIG_REQUIRED` 展示配置入口，不显示通用网络错误。
- `Resume.vue` 上传解析前或收到 409 后引导配置。

## Backend Agent 最小实现契约

- 新增 `user_llm_config` Flyway migration，版本号接在当前最新 `V12` 之后。
- 新增实体、Mapper、DTO 和 Service，所有查询必须带 `user_id`。
- 实现 `APP_LLM_CONFIG_ENCRYPTION_KEY` 校验和 `AES/GCM/NoPadding` 加密服务。
- 实现 `/api/llm/providers/presets` 与 `/api/llm/configs/**` API。
- 删除或停止用户侧依赖 `ChatConfig` 中全局 chat model bean；保留非 LLM bean。
- 新增 `UserLlmModelFactory`，集中创建 chat/streaming model。
- 替换 `InterviewServiceImpl`、`EvaluationGenerator`、`MentorService`、`ResumeServiceImpl` 的全局模型依赖。
- `ResumeService.parseAndAnalyze` 契约需要携带 userId 或由 Controller 先获得用户模型。
- 无 active 配置时抛 `LlmProviderRequiredException`，普通接口映射 409，SSE 返回一次结构化 error。
- 日志和 `AppEventService` 记录必须脱敏，不能落完整 key、token、密文。
- 配置新增、更新、启用、删除后清理或隔离 Mentor 缓存。
- 测试至少覆盖：CRUD 归属、只返回脱敏 key、启用唯一、删除 active 后无配置、无配置阻断、保存 key 加密、测试连接脱敏、四个 LLM 调用点改用 factory。

## Frontend Agent 最小实现契约

- 新增 `/llm-providers` 路由和侧边栏“大模型配置”入口。
- 新增 `llmConfig.js` API client，对接 Architect API contract。
- 新增配置页面：列表、新增、编辑、删除、启用、测试连接、active 状态、无配置状态。
- Provider 预设至少 DeepSeek、Kimi/Moonshot、GLM/Zhipu、Qwen、自定义；最终显示以后端 presets 为准。
- API Key 只存在表单内存，提交后清空；不写 localStorage/sessionStorage，不回显。
- 处理 `LLM_CONFIG_REQUIRED`：面试准备、文字面试 SSE、视频面试 SSE、Mentor、简历画像都引导到 `/llm-providers`。
- 保持现有 Vue + Element Plus 风格，不引入新 UI/状态库。
- 测试/构建至少覆盖页面主要状态、API client、无配置引导、构建通过。

## 共享文件与并行风险

高冲突共享文件：

- `backend/src/main/java/com/interview/config/ChatConfig.java`
- `backend/src/main/java/com/interview/config/GlobalExceptionHandler.java`
- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
- `backend/src/main/java/com/interview/service/EvaluationGenerator.java`
- `backend/src/main/java/com/interview/service/MentorService.java`
- `backend/src/main/java/com/interview/service/impl/ResumeServiceImpl.java`
- `frontend/src/components/layout/AppShell.vue`
- `frontend/src/router/index.js`
- `frontend/src/views/InterviewSetup.vue`
- `frontend/src/views/Interview.vue`
- `frontend/src/views/VideoInterview.vue`
- `frontend/src/views/Mentor.vue`
- `frontend/src/views/Resume.vue`

并行建议：

- Backend 和 Frontend 可基于本 contract 并行。
- Backend 先冻结 DTO 字段名和错误语义；Frontend 不等待业务实现也可先 mock client。
- Integration 必须串行处理 `ChatConfig`、全局异常、前端无配置错误处理，以及 `.env.example` / Docker 示例变量。
- Security Review 必须重点审查加密密钥、日志脱敏、管理员不可见、测试连接错误泄漏。

## 遗留风险

- 当前计划卡漏列 `ResumeServiceImpl`，需要主控同步给 Backend/Frontend/Testing/Security，否则用户侧 LLM 覆盖不完整。
- 当前任务卡基线 `48e29f7` 与 Architect worktree HEAD `27c6d99` 不一致，Integration 前必须确认所有子分支基线和变更来源。
- 若运行环境缺少 `APP_LLM_CONFIG_ENCRYPTION_KEY`，按本设计后端会启动失败；Docs/Integration 需要更新示例环境变量，但不能提交私有密钥。
- 如果供应商返回错误中包含敏感信息，测试连接和全局异常记录必须二次脱敏。
