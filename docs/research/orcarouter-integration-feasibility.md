# OrcaRouter 接入可行性与准备清单

> 调研日期：2026-09-03
>
> 来源边界：仅使用 OrcaRouter 官方文档、官网公开页面和官方法律文件。邮件截图仅作为用户提供的线索，不作为 OrcaRouter 的正式合同或技术事实来源。

## 结论

**技术上可行。** OrcaRouter 提供 OpenAI 兼容网关，标准 Base URL 为 `https://api.orcarouter.ai/v1`，鉴权使用 `Authorization: Bearer sk-orca-...`。官方同时声明支持 Chat Completions、Responses API、SSE 流式响应和 OpenAI 风格的工具调用，因此适合作为现有“OpenAI 兼容 Provider”的一个**可选**网关，而不应替换或隐式接管其他 Provider。[Quickstart](https://docs.orcarouter.ai/getting-started/quickstart) · [OpenAI-compatible HTTP](https://docs.orcarouter.ai/native-formats/openai-compat) · [Tool calling](https://docs.orcarouter.ai/advanced/tool-calling)

**商务合作尚不能按“长期返还消费额 5%”视为已确认。** OrcaRouter 的公开 OSS 页面只明确：开源维护者把它作为一个可选 Provider 后，可按其用户消费持续获得积分或现金分成；非独家，用户不选择 OrcaRouter 时工具不受影响。公开页面没有给出 5% 数字、结算口径或合同细则。[OSS Program / Built with OrcaRouter](https://www.orcarouter.ai/built-with)

**安全上应先核验邀请真实性。** 截图发件人域名是 `orcaroutermail.lol`，与官网和法律文件使用的 `orcarouter.ai` 不同。这不等于已判定为诈骗，但在 OrcaRouter 从官方域名书面确认前，不应点击邮件中的注册/授权链接，不应向发件人发送 API Key、密码、验证码或仓库权限。官方公开的产品支持地址是 `support@orcarouter.ai`，法律/合同地址是 `legal@orcarouter.ai`。[Terms of Service](https://www.orcarouter.ai/terms.html)

## 已确认的技术契约

| 项目 | 官方已确认内容 | 接入含义 |
|---|---|---|
| OpenAI 兼容 Base URL | `https://api.orcarouter.ai/v1`。[OpenAI SDK](https://docs.orcarouter.ai/compatibility/openai-sdk) | Provider 配置中使用该 URL；不要把 API Key 写入仓库。 |
| 鉴权 | API Key 从官方 Dashboard 创建，前缀为 `sk-orca-`；所有端点使用 `Authorization: Bearer <key>`。创建时可设置名称、总消费额度和有效期。[Get an API key](https://docs.orcarouter.ai/getting-started/get-api-key) | 为项目单独创建最小额度、可撤销、有到期时间的密钥。 |
| Chat Completions | `POST /v1/chat/completions`，请求与响应采用 OpenAI JSON 形状。[OpenAI-compatible HTTP](https://docs.orcarouter.ai/native-formats/openai-compat) | 可复用项目现有 OpenAI 兼容聊天调用链。 |
| Responses API | `POST /v1/responses`；官方称目录中的聊天模型均可通过此接口调用，非原生 Responses 模型由网关转换协议。[OpenAI-compatible HTTP](https://docs.orcarouter.ai/native-formats/openai-compat) | 若当前项目只依赖 Chat Completions，不必为了接入额外迁移到 Responses；若使用 Responses，仍应做真实模型冒烟测试。 |
| 流式输出 | 设置 `stream: true` 后使用 SSE；Chat Completions 流以 `data: {...}` 传输并以 `data: [DONE]` 结束，可用 `stream_options.include_usage` 获取最终用量。[Streaming](https://docs.orcarouter.ai/advanced/streaming) | 需要验证当前客户端对 SSE、最终 usage 和流中错误的处理。 |
| 工具 / Function Calling | OpenAI 风格的 `tools` / `tool_choice` 可用于所有“chat-capable provider”，网关会转换为 Anthropic/Gemini 等上游格式。[Tool calling](https://docs.orcarouter.ai/advanced/tool-calling) | “Provider 支持”不等于每个具体模型都稳定支持；应对候选模型逐一验证工具调用、参数 JSON 和流式 tool-call delta。 |
| 模型列表 | `GET /v1/models`，只返回当前账号可访问且已配置价格比例的模型；默认 ID 带 Provider 前缀，例如 `openai/...`、`anthropic/...`、`google/...`、`deepseek/...`。裸模型名可能仅在管理员配置别名时可用。[Models](https://docs.orcarouter.ai/getting-started/models) | 不应把邮件举例中的模型名直接硬编码；取得真实 Key 后以 `/v1/models` 返回值为准。 |
| 免费模型 | 免费模型 ID 以 `-free` 结尾且目录价格为 `$0`。官方文档当前列出 DeepSeek V4 Flash/Pro 免费 ID，但明确要求以实时目录为准；免费额度限制数值不公开并可能变化。[Free Models](https://docs.orcarouter.ai/routing/free-models) | 免费模型适合开发冒烟，不应直接当成生产 SLA；必须处理免费层特有的 429。 |
| 普通限流 | 以 workspace 为单位共享限额，不是每个 Key 单独计数；429 通常带秒数单位的 `Retry-After`，官方建议等待后重试，再次 429 时指数退避，最多 60 秒。没有 `X-RateLimit-Remaining/Reset`。[Rate Limits](https://docs.orcarouter.ai/operations/rate-limits) | 多个用户 Key 不能天然增加工作区吞吐；客户端需按 `Retry-After` 处理。 |
| 错误格式 | 多数错误使用 OpenAI 兼容的 `{ "error": { "message", "type", "code" } }`；但快速路径 429 可能没有 JSON Body。常见 HTTP 状态包含 400、401、403、404、425、429、500、502、503。[Errors](https://docs.orcarouter.ai/operations/errors) | 先按 HTTP 状态处理，再优先匹配 `error.code`、其次 `error.type`；不要依赖本地化 message 完整文本。 |
| 流中错误 | Chat/Responses 已开始流式传输后，错误以内嵌 `data: {"error":...}` 出现，随后 `[DONE]`；发出任意响应字节后不能再切换到 fallback。[Errors](https://docs.orcarouter.ai/operations/errors) · [Streaming](https://docs.orcarouter.ai/advanced/streaming) | 不能把收到 HTTP 200 当成整次流成功；客户端必须检测流内 `error` 并把部分输出标记为失败/不完整。 |
| 计费 | 平台流量按上游公开 token 价格计费，官方声称无 token 加价；每个 chat/responses 响应有 `usage`。具体模型、价格与可用性可能变化，自动故障转移仅为 best effort。[Billing & usage](https://docs.orcarouter.ai/operations/billing-and-usage) · [Terms of Service](https://www.orcarouter.ai/terms.html) | 接入时应保留用户选择模型和 Provider 的明确性，并设置密钥额度上限；不能向用户承诺固定模型、固定价格或 SLA。 |

## 已确认的数据与隐私边界

- OrcaRouter 官方文档和 Privacy Policy 均声明：不持久化 prompt、模型输出、工具调用参数/结果或上传给音频/图片接口的文件；内容在内存中转发后丢弃，也不用于训练模型。[Data Handling](https://docs.orcarouter.ai/operations/data-handling) · [Privacy Policy](https://www.orcarouter.ai/privacy.html)
- 网关仍会保存请求元数据，包括时间、API Key 标识（非密钥值）、目标模型/Provider、输入输出 token 数、延迟、HTTP 状态、截断的上游错误信息和源 IP。官方 Privacy Policy 写明 usage metadata 滚动保留 13 个月，server/security logs 保留 90 天，账单记录保留 7 年。[Privacy Policy](https://www.orcarouter.ai/privacy.html)
- “零数据保留”仅覆盖 OrcaRouter 自身服务器；被选中的上游 Provider 仍会收到请求并按其自己的条款与保留政策处理。OrcaRouter 的服务条款还规定上游 Provider 的使用政策同时约束调用者。[Zero Data Retention](https://docs.orcarouter.ai/operations/zero-data-retention) · [Terms of Service](https://www.orcarouter.ai/terms.html)
- 官方条款声明用户保留输入与输出的权利，OrcaRouter 不主张内容所有权，也不拿内容训练模型；但用户仍须自行评估模型输出。[Terms of Service](https://www.orcarouter.ai/terms.html)

因此，若项目会发送简历、面试记录、邮箱、手机号或其他个人信息，不能仅凭“OrcaRouter ZDR”就宣称端到端零保留。正式上线前仍需核对所选上游模型的隐私政策、数据地区、DPA/跨境传输要求，并在产品隐私说明中披露新增的网关和潜在上游接收方。

## OSS 计划：已确认与未确认

### 官方公开页面已确认

- OrcaRouter 必须作为**可选 Provider**；可以保留其他 Provider，用户不选择 OrcaRouter 时产品不受影响。
- 开源项目作者可因其用户的 OrcaRouter 消费获得持续分成，形式可为 credit 或 cash。
- 被官网收录只是 attribution，不代表 OrcaRouter 对项目进行 endorsement。

来源：[Built with OrcaRouter](https://www.orcarouter.ai/built-with)

### 尚未从官方公开材料确认

以下内容不能仅凭邮件正文当成已签署条款：

1. **5% 是否为固定比例**，还是可调整、分层或限时比例。
2. 5% 的计算基数：用户实际支付额、token 消费额、平台毛利，还是扣除退款、税费、折扣后的净额。
3. “你的用户”的归因方式：邀请链接、项目标识、API Header、首次注册、Last Click，及归因窗口和跨设备规则。
4. 结算币种、最低提现额、支付渠道、付款周期、税务/KYC 要求与手续费承担方。
5. 用户退款、拒付、赠送额度、免费模型、BYOK、订阅费是否计入分成，以及 clawback 规则。
6. 项目资格、开源许可证要求、集成验收标准、必须保留多久、是否允许 fork 或镜像项目参与。
7. OrcaRouter 商标/Logo 的使用授权、官网收录方式，以及对项目名和截图的宣传权限。
8. 计划终止或项目移除后，历史已归因用户是否继续分成。
9. 可审计的消费/结算报表、争议处理流程和适用合同版本。

官网还有一个独立的 Points/Rewards 页面，其中“Storm 等级按平台 margin 分池”的收益计划标为 **planned**、上线日期 TBD、池比例写成 `x%`。这不能用来证明邮件里的 OSS 维护者 5% 计划，也不应将两套计划混为一谈。[Rewards](https://www.orcarouter.ai/rewards)

## 在开始接入前需要用户准备

### 现在可以安全准备

1. 确认项目要继续保留现有 Provider，并把 OrcaRouter 作为用户主动选择的 Provider。
2. 确定首批验收模型与能力矩阵，至少包括：普通非流式聊天、流式聊天、工具调用、结构化 JSON；模型 ID 最终以真实账号的 `/v1/models` 返回为准。
3. 确定数据边界：测试阶段只用脱敏/虚构的简历和面试数据；生产前完成隐私披露和所选上游 Provider 的保留政策核查。
4. 定义成本保护：测试 Key 的低额度上限、到期时间、超时、429 退避、最大 token 与失败后是否允许模型 fallback。

### 应在邀请核验通过后准备

1. 从浏览器手动输入 `https://www.orcarouter.ai/` 注册，不经邮件跳转；在官方 Dashboard 创建项目专用测试 Key。
2. Key 仅放本机 `.env` / 密钥管理系统，不发到聊天、Issue、PR、日志或截图，也不提交到 Git。
3. 从官方 Console 或 API 导出/记录可用模型列表、账号所属 workspace、费率/余额和 workspace 级限流信息。
4. 要求 OrcaRouter 从 `@orcarouter.ai` 官方邮箱或官方 Console 提供 OSS 计划书面条款，并回答上一节九项未确认问题。

## 建议的最小验收门槛

在没有真实 Key 的情况下，只能完成代码层适配，不能宣称网关“已接入成功”。拿到 Key 后至少应验证：

1. `GET /v1/models` 成功且返回首批候选模型。
2. Chat Completions 非流式调用成功，并记录实际 `model`、`usage`、请求 ID 和费用（不记录 prompt/response 敏感内容）。
3. SSE 流完整结束；能识别 `[DONE]`、最终 usage 与流内 error。
4. 工具调用能生成合法 arguments，并由项目自身受控执行；网关只生成 tool call，不获得本地执行权限。
5. 人为触发或模拟 401、403、429、5xx，确认错误分类、`Retry-After`、退避和不重复扣费/重复执行策略。
6. 验证 OrcaRouter 仅是可选 Provider，切换失败时不会静默改用其他 Provider 或模型。
