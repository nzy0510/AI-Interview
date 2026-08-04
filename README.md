# InterWise AI 模拟面试系统

InterWise 是一个面向技术面试训练的 AI 模拟面试平台。项目把有边界的面试 Agent、简历画像、文字面试、视频面试、数据库题库、动态 RAG 追问、面试复盘和 AI Mentor 打通到同一条学习闭环中，重点解决“只会单轮问答、题库与模拟面试割裂、追问缺少依据、训练结果难以复盘”的问题。

后端基于 `Spring Boot 3 + MyBatis-Plus + LangChain4j + MySQL + Redis + Qdrant`，前端基于 `Vue 3 + Vite + Element Plus`。Docker 部署默认使用独立 `embedding-service` 加载 `intfloat/multilingual-e5-base`，通过 Qdrant 为面试追问提供可重建的语义索引。

## 项目亮点

- 动态面试 RAG：不是传统知识库问答式“用户问题 -> 检索 -> 摘要回答”，而是在每一轮面试中把候选人回答、岗位、阶段、历史已问知识点和题库召回结果转成“下一问决策信号”。
- 有边界的单轮 Agent：技术面每轮可按需调用岗位知识、当前简历和学习覆盖三个只读工具，自主选择深挖、补救、换题、项目追问或阶段切换；工具次数、作用域、输出格式和失败回退均受服务端约束。
- 模拟面试与题库打通：MySQL 保存可审核、可发布、可归档的知识原子，Qdrant 只作为可重建的语义索引；已发布 Atom 才能进入面试追问链路。
- 追问路径更贴近真实面试：技术阶段按岗位和难度召回，结合低信息回答、弱召回、连续回避、已用 Atom 排除等信号，决定补救追问、切换知识点或继续深挖。
- 多模式训练闭环：支持文字面试、视频面试、简历画像、历史报告、AI Mentor 分析和知识覆盖率复盘。
- 公共与私有题库并存：所有本地账号可使用公共 starter 岗位，也可创建并维护自己的私有岗位 / 题库；`ADMIN` 账号额外负责公共内容发布，私有数据按账号隔离。
- 可评测的 RAG 链路：内置离线检索评测工具链，固定 AI 大模型岗位评测集，支持比较 embedding、候选集大小和 rerank 效果。
- 稳定性与运营观测：内置访问事件、限流、反馈记录、RAG 请求级日志和 Qdrant 失败降级路径。

## 技术栈

### 后端

| 技术 | 版本 / 说明 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.4 |
| MyBatis-Plus | 3.5.5 |
| LangChain4j | 0.29.1 |
| MySQL | 8.0 |
| Redis | 7 |
| Qdrant | 向量检索 |
| Flyway | 9.22.3 |
| PDFBox | 简历 PDF 解析 |
| OpenAI-compatible Chat API | 用户自配 DeepSeek / Kimi / GLM / Qwen / 自定义兼容模型 |
| multilingual-e5-base | Docker 默认 embedding 模型 |

### 前端

| 技术 | 版本 / 说明 |
| --- | --- |
| Vue | 3.5 |
| Vite | 7 |
| Element Plus | 2.13 |
| Axios | HTTP 客户端 |
| ECharts | 图表与词云 |
| face-api.js | 视频面试情绪分析辅助 |
| Web Speech API | 浏览器语音能力 |


### 基础设施

| 组件 | 用途 |
| --- | --- |
| Docker Compose | 本地与云端多容器编排 |
| embedding-service | FastAPI 向量服务 |
| Caddy | 生产 HTTPS 入口 |

## 效果展示

### 工作台与准备流程

![工作台主页面](image/展示图/工作台主页面.png)

![面试准备页](image/展示图/面试准备页.png)

### 文字面试与视频面试

![文字面试页](image/展示图/文字面试页.png)

![视频面试页](image/展示图/视频面试页.png)

### 简历画像、历史报告与 AI Mentor

![简历画像页](image/展示图/简历画像页.png)

![历史报告页](image/展示图/历史报告页.png)

![AI Mentor 分析页](<image/展示图/ai mentor分析页.png>)

### 岗位/题库维护

![岗位/题库维护页](image/展示图/岗位，题库维护页.png)

### 偏好设置、大模型配置与题库工作台

![偏好设置页](image/展示图/偏好设置页.png)

![大模型配置页](image/展示图/llm配置界面.png)

![题库维护页](image/展示图/题库维护.png)

## 系统架构

![InterWise 系统架构图](image/架构图/InterWise-系统架构图.png)

```mermaid
graph LR
    User["候选人 / 训练用户"] --> Frontend["Vue 3 前端"]
    Frontend -->|"HTTP / SSE"| Backend["Spring Boot 后端"]
    Backend --> Orchestrator["有边界面试编排器"]
    Orchestrator --> Planner["规划 Agent"]
    Orchestrator --> Tools["岗位知识 / 简历 / 覆盖率只读工具"]
    Orchestrator --> Generator["流式面试模型"]
    Planner --> LLM["用户启用的 OpenAI-compatible 模型"]
    Generator --> LLM
    Tools --> MySQL
    Tools --> Qdrant
    Backend --> MySQL[("MySQL: 业务真相")]
    Backend --> Redis[("Redis: 会话缓存 / 限流 / Mentor 缓存")]
    Backend --> Qdrant[("Qdrant: 语义索引")]
    Backend --> Embed["embedding-service: multilingual-e5-base"]
    Embed --> Qdrant
    Workspace["知识库 / 题库工作台"] --> Backend
    Admin["ADMIN 角色"] --> Workspace
```

核心边界：

- MySQL 是用户、面试、报告、题库、导入批次和同步状态的业务真相。
- Qdrant 是可重建的向量索引，不直接承载题库发布状态。
- embedding-service 只负责文本向量化，默认输出 768 维 multilingual-e5 向量。
- 前端不直接访问数据库、Redis 或 Qdrant，所有维护动作走后端 API 与用户 ownership / `ADMIN` 角色校验。
- Agent 工具的用户、岗位和记录作用域由后端绑定，模型不能传入任意 ID；失败后同一面试会话回退稳定规则。
- 本地 Docker 默认开放账号自有的私有题库维护；公共资源仍只允许 `ADMIN` 修改，私有资源由后端按 owner 独立校验。

## 项目结构

```text
.
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/com/interview/
│   │   ├── controller/              # REST API
│   │   ├── service/                 # 面试、简历、Mentor、RAG、题库服务
│   │   │   └── orchestration/       # Agent 契约、只读工具与稳定规则回退
│   │   ├── entity/                  # MySQL 实体
│   │   └── config/                  # LLM、Redis、Embedding、JWT 等配置
│   └── src/main/resources/db/migration/
├── frontend/                        # Vue 3 前端
│   └── src/views/                   # 工作台、准备页、面试页、历史、Mentor、设置
├── embedding-service/               # FastAPI multilingual-e5 向量服务
├── scripts/question_bank_import.py  # 本机题库导入包生成脚本
├── skills/interview-question-bank/  # 本机题库维护 skill
├── docs/adr/                        # 关键架构决策
├── docs/superpowers/                # 重要实现计划与设计记录
├── image                            # 系统架构图与 RAG 流程图
├── docker-compose.example.yml       # 本地 Compose 模板
├── docker-compose.prod.yml          # 生产 Compose
├── CONTEXT.md                       # 领域语言与边界
└── CHANGELOG.md                     # 更新日志
```

## 当前功能

### 基础功能

- 文字面试：SSE 流式生成，按面试阶段推进，支持技术追问、HR 软技能阶段和结束总结。
- 视频面试：摄像头与语音交互入口，结合浏览器能力进行更接近真实场景的训练。
- 面试 Agent：技术阶段先生成受约束的下一问计划，再驱动真实 Prompt 和阶段；页面展示安全决策摘要，异常时不中断整场面试。
- 大模型配置：用户可以在侧边栏配置自己的大模型 Provider，并用加密保存的 API Key 驱动面试、报告和 AI Mentor 等用户侧 LLM 功能。
- 岗位/题库维护：本地账号可新增自己的私有岗位并维护私有题库；所有 `ADMIN` 角色拥有一致的公共题库维护能力，同时也能维护各自的私有内容。
- 面试准备：选择岗位、难度、重点方向和简历信息，为后续追问提供上下文。
- 历史报告：保存面试记录、评分、反馈和复盘建议。
- 岗位隔离：现在用户可为不同岗位上传不同简历，并进行针对简历的定制面试。

### 简历与 Mentor

- 简历画像：解析 PDF 简历并生成结构化画像。
- AI Mentor：基于历史面试、知识覆盖率和风险点给出训练建议。
- 知识覆盖：以已发布题库 Atom 为分母，以实际进入面试上下文的 Atom 为分子，避免只统计“看似召回”的候选。

## 有边界 Agent 与动态 RAG

技术面每轮先由 `InterviewOrchestrator` 形成一个可验证计划。规划 Agent 可以调用三个服务端绑定作用域的只读工具，最终只能返回限定动作；动作和证据会进入实际下一问 Prompt。开场、HR、收尾或 Agent 不可用时，系统继续走稳定规则。

```mermaid
flowchart LR
    Answer["候选人最新回答"] --> Orchestrator["InterviewOrchestrator"]
    Orchestrator -->|"技术阶段且可用"| Agent["Tool Calling Agent"]
    Agent --> Knowledge["岗位知识检索"]
    Agent --> Resume["当前岗位简历证据"]
    Agent --> Coverage["同岗位学习覆盖"]
    Agent --> Plan["受约束动作 + 安全证据"]
    Orchestrator -->|"非技术阶段或失败"| Rule["稳定规则"]
    Plan --> Prompt["实际下一问 Prompt / 阶段"]
    Rule --> Prompt
    Prompt --> SSE["文字 / 视频面试 SSE"]
```

单轮工具调用上限为 3。输出必须符合严格 JSON 契约；提前进入 HR 有轮次门槛；超时、Provider 不兼容、工具失败或输出非法时，当前面试会话固定回退规则模式。持久化与前端事件只包含模式、动作、工具名、安全摘要和证据原子 ID，不保存思维链或原始 Provider 错误。详细取舍见 [ADR 0001](docs/adr/0001-bounded-interview-agent.md)。

### 动态 RAG 链路

![InterWise RAG 流程图](image/架构图/InterWise-RAG流程图.png)

InterWise 的 RAG 不是独立知识库问答模块，而是嵌入模拟面试流程中的“追问决策层”。

```mermaid
graph TD
    Answer["候选人当前回答"] --> Query["结合上一问、岗位、阶段构造检索 query"]
    Query --> Route["岗位 / 阶段分类路由"]
    Route --> Search["Qdrant 召回已发布 Atom"]
    Search --> Signals["低信息回答、弱召回、连续回避、已用 Atom 排除"]
    Signals --> Decision{"下一问策略"}
    Decision -->|"召回可靠"| Deepen["注入 Top-N Atom 深挖"]
    Decision -->|"回答空泛"| Remedy["补救追问"]
    Decision -->|"召回弱或连续低信息"| Switch["切换知识点"]
    Deepen --> Prompt["面试官 Prompt"]
    Remedy --> Prompt
    Switch --> Prompt
```

召回结果不会直接拼成“参考答案”，而是影响 AI 面试官下一轮追问方式。系统会记录候选 Atom、实际进入上下文的 Atom、零命中、失败原因和检索策略，便于后续人工评测与 rerank 验证。

### 题库与 RAG

- 知识库 / 题库工作台：本地账号均可创建私有岗位、导入自己的结构化 JSON 题库包并管理私有知识原子；`ADMIN` 账号还能维护公共 starter 岗位及其发布、归档和索引状态。
- 题库导入包生成：用户可在本机使用 `interview-question-bank` skill 或 `scripts/question_bank_import.py` 处理结构化资料，再导入自己有权限的公共或私有题库，完成审查、显式发布和重建索引。
- 同步状态：Qdrant 写入或删除失败会保留可重试状态，不让数据库事务和外部索引状态悄悄分叉。
- 离线评测：`scripts/retrieval_eval` 支持导出、构建候选池、预标注、计算指标和 rerank 对比。
- 内置基础题库：仓库随代码内置可运行公共 starter 题库，覆盖 Java 后端、Web 前端、AI 大模型应用等方向；本地启动会幂等导入 `backend/src/main/resources/knowledge_base/imports/public/**/*.json` 公共导入包并建立 Qdrant 索引。普通用户直接选择这些岗位训练，不需要维护或导入题库。

### 题库维护

仓库内置公共 starter 题库，用于空库首次初始化。本地 Docker 默认设置 `APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED=true`：每个登录账号都能新增自己的私有岗位，并对自己的题库执行 JSON 导入、查询、发布、归档和重建索引；不能读取或修改其他账号的私有内容。

所有 `ADMIN` 账号遵循同一角色规则：既能维护公共 starter 题库，也能创建和维护各自的私有岗位。生产 Compose 仍默认关闭普通用户维护能力，需要上线时再结合租户、配额和存储治理显式评估。

## 快速启动(本地部署)

### 环境要求

- Docker Desktop / Docker Compose
- 用户自备 OpenAI-compatible API 账号（如 DeepSeek、Kimi、GLM、Qwen 或自定义兼容供应商）

裸跑后端、前端或检索评测工具链时，再分别安装 JDK 17、Node.js 20+、Python 3.10+。

### 创建配置

```powershell
Copy-Item .env.example .env
Copy-Item docker-compose.example.yml docker-compose.yml
```

至少修改 `.env` 中的数据库密码、JWT 签名密钥、用户 API Key 加密密钥和统计盐值：

```env
DB_PASSWORD=your_mysql_password
APP_LLM_CONFIG_ENCRYPTION_KEY=your_base64_or_high_entropy_encryption_key
JWT_SIGN_KEY=your_jwt_signing_key_at_least_32_characters
APP_ANALYTICS_HASH_SALT=your_strong_analytics_hash_salt
```

本地 Docker 默认启用 `local-admin` 认证模式，不需要配置 QQ 邮箱、SMTP 授权码、注册验证码或找回密码。请保留以下默认配置：

```env
APP_AUTH_MODE=local-admin
APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED=true
```

本地默认账号固定为 `admin / admin123`，只用于绑定到 `127.0.0.1` 的本机部署，不要用于公网服务器。

题库维护权限按角色和 owner 判断，不按用户名写死：`admin`、`nzy333` 或其他 `ADMIN` 账号拥有相同能力；普通账号可新增并维护自己的私有岗位 / 题库，不能操作公共题库或其他账号的私有数据。

大模型配置说明：

- 项目不提供系统兜底 API Key，也不依赖全局 `DEEPSEEK_API_KEY` 作为普通用户兜底。
- 默认管理员首次登录后，需要进入侧边栏“大模型配置”，新建 DeepSeek 或其他兼容 Provider，填写自己的 API Key，测试连接并启用该配置。
- 没有有效的启用配置时，文字面试、视频面试、报告生成和 AI Mentor 等用户侧 LLM 功能会引导用户先完成配置。
- 当前支持 OpenAI-compatible Provider 预设与自定义兼容端点，文档默认覆盖 DeepSeek、Kimi/Moonshot、GLM/Zhipu、Qwen 和自定义。
- 服务端只需要 `APP_LLM_CONFIG_ENCRYPTION_KEY` 这类加密密钥来加密保存用户 API Key；不要在 `.env`、示例配置、日志或文档里写入任何真实供应商密钥。
- 用户可以保存多个 Provider 配置，但同一时间只能启用一个 active 配置；管理员不能查看用户 API Key 明文。
- 本地配置默认启用有边界 Agent 和账号自有题库维护；对应变量见 `.env.example` 的 `APP_INTERVIEW_AGENT_*` 与 `APP_QUESTION_BANK_USER_MAINTENANCE_ENABLED`。

不要提交 `.env`、真实 API Key、JWT Secret、邮箱授权码或数据库密码。

### Docker 部署

```powershell
docker compose up -d --build
```

容器全部启动后，打开 `http://localhost`，使用本地默认管理员登录：

```text
用户名：admin
密码：admin123
```

登录后的首次使用顺序：

1. 打开侧边栏“大模型配置”。
2. 选择 DeepSeek，填写自己的 API Key。
3. 点击“测试连接”，测试成功后保存并启用。
4. 可直接选择内置岗位开始模拟面试，也可进入“岗位 / 题库维护”新增自己的私有岗位并导入题库。

首次启用 `local-admin` 且数据库中不存在同名 `admin` 时，系统会创建默认管理员；这不要求整库为空，也不会删除已有账号、题库或面试数据。如果已经存在 `ADMIN` 角色的 `admin` 账号，启动过程不会覆盖其密码或资料；如果同名账号不是管理员，后端会明确拒绝启动，避免静默提权。修改密码后，重启容器也不会重置为 `admin123`。

默认访问：

- 前端：`http://localhost`
- 后端：`http://localhost:8080`
- Qdrant：`http://localhost:6333`
- MySQL：`localhost:3307`
- Redis：`localhost:6379`

本地 Compose 的这些端口只监听 `127.0.0.1`，局域网中的其他设备不能直接访问。停止容器但保留账号、题库和面试数据：

```powershell
docker compose stop
```

首次构建 embedding-service 会下载 PyTorch、sentence-transformers 和 multilingual-e5 模型，耗时取决于网络质量。若切换过 embedding 模型或 Qdrant collection，启动后需要通过知识库 / 题库维护流程重建索引。

未来如果部署到公网，应改用 `docker-compose.prod.yml` 的 `email-verified` 模式，配置独立管理员、SMTP、HTTPS 和强密码；不要直接公开本地默认管理员，也不要把包含该账号的本地 `mysql_data` 原样迁移到公网。

### RAG 评测工具

```powershell
python -m pip install -r scripts/retrieval_eval/requirements.txt
python -m scripts.retrieval_eval.validate_dataset --help
python -m scripts.retrieval_eval.calculate_metrics --help
python -m scripts.retrieval_eval.rerank_candidates --help
```

固定评测集位于：

```text
backend/src/test/resources/retrieval-eval/
  ai-model-v1-atoms.jsonl
  ai-model-v1.jsonl
  ai-model-v1-metadata.json
```

原始导出和未审核候选池默认写入 `output/retrieval-eval/`，不提交到 Git。

## 本地开发验证

```powershell
cd backend
mvn test
```

```powershell
cd frontend
npm run build
npx vitest run
```

```powershell
python -m unittest discover -s tests
```

## 相关文档

- [领域上下文](CONTEXT.md)
- [GOAI 比赛 Demo 指南](docs/goai-demo.md)
- [ADR 0001：有边界的单轮面试 Agent](docs/adr/0001-bounded-interview-agent.md)
- [后续优化计划](docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-followups.zh.md)
- [RAG 检索评测设计](docs/superpowers/specs/2026-06-03-rag-retrieval-evaluation-design.md)
- [RAG 链路总结](docs/rag-chain-summary.md)

## 版本

当前稳定版本以 GitHub Releases 为准。更新内容见 [CHANGELOG.md](CHANGELOG.md)。

## 题库来源

内置题库内容来自 mianshiya.com，使用前请按自己的部署、岗位和授权边界复核。

## 贡献说明

欢迎为本项目贡献代码、文档或提出改进建议！你可以通过 Issue 或 Pull Request 参与项目建设。
详见 [贡献指南](docs/贡献指南.md)
