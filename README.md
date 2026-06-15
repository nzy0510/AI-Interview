# InterWise AI 模拟面试系统

InterWise 是一个本地可部署的 AI 模拟面试平台。当前版本的核心方向是受控开放：仓库提供公共 starter 题库，登录用户可以维护自己的私有岗位题库，公共题库由 `ADMIN` 角色维护。

后端使用 `Spring Boot 3 + MyBatis-Plus + LangChain4j + MySQL + Redis + Qdrant`，前端使用 `Vue 3 + Vite + Element Plus`。Docker 默认启动独立 `embedding-service`，使用 `intfloat/multilingual-e5-base` 生成题库向量。

## 当前能力

- 文字面试和视频面试：按岗位、难度、阶段推进，技术轮会结合已发布题库原子做 RAG 追问。
- 用户自配大模型：用户登录后在“大模型配置”中保存 OpenAI-compatible Provider。项目不提供系统兜底 API Key。
- 知识库 / 题库工作台：普通用户维护自己的私有岗位题库；管理员维护公共 starter 题库。
- 受控题库导入：本机 Agent 使用 `interview-question-bank` skill 和 `scripts/question_bank_import.py` 生成 JSON 导入包，应用内导入为草稿，经人工审查后发布并同步 Qdrant。
- 历史报告：面试结束立即展示初步报告，后台生成可在历史记录查看的逐轮详细报告。
- 简历画像和 AI Mentor：基于简历、历史面试和知识覆盖情况生成训练建议。

## 系统边界

- MySQL 是业务真相，保存用户、面试、报告、题库原子、导入批次和发布状态。
- Qdrant 是可重建的语义索引，只索引已发布原子。
- embedding-service 只负责向量化，不保存业务状态。
- 应用内不再支持任意文档上传后自动切分生成原子；题库生产主路径是本机生成导入包，再到工作台人工导入、审查、发布。
- 私有题库只存在于当前部署的 MySQL/Qdrant 数据中，不会提交到 Git，也不会同步到其他部署。

## 本机 Docker 部署

### 1. 准备环境

- Docker Desktop 或 Docker Engine + Docker Compose
- 可访问 Hugging Face / PyPI / Maven / npm 的网络环境
- 一个 OpenAI-compatible 模型账号，用于用户登录后在前端配置
- SMTP 授权码，用于注册和找回密码

仅做后端或前端裸跑开发时再安装 JDK 17、Node.js 20+、Python 3.10+。

### 2. 创建本地配置

```powershell
Copy-Item .env.example .env
Copy-Item docker-compose.example.yml docker-compose.yml
```

至少修改 `.env`：

```env
DB_PASSWORD=replace_with_mysql_password
MYSQL_ROOT_PASSWORD=replace_with_root_password
JWT_SIGN_KEY=replace_with_at_least_32_random_chars
APP_LLM_CONFIG_ENCRYPTION_KEY=replace_with_strong_random_secret
APP_ANALYTICS_HASH_SALT=replace_with_random_salt
MAIL_HOST=smtp.qq.com
MAIL_PORT=587
MAIL_USERNAME=your_email@qq.com
MAIL_PASSWORD=your_smtp_authorization_code
```

说明：

- `APP_LLM_CONFIG_ENCRYPTION_KEY` 用于加密保存用户在前端填写的模型 API Key。
- Docker Compose 内部会把后端的 `QDRANT_URL` 指向 `http://qdrant:6333`，通常不需要手动改。
- 如果不使用 Docker 跑后端，才需要把本机 Qdrant 地址设为 `http://localhost:6333`。

### 3. 启动

```powershell
docker compose up -d --build
```

查看状态：

```powershell
docker compose ps
docker logs -f interview-backend
```

默认端口：

| 服务 | 地址 |
| --- | --- |
| 前端 | `http://localhost` |
| 后端 | `http://localhost:8080` |
| Qdrant | `http://localhost:6333` |
| MySQL | `localhost:13307` |
| Redis | `localhost:6379` |

首次启动会构建前后端镜像、下载 embedding 模型、初始化公共 starter 题库并写入 Qdrant。模型下载和 900+ 条公共原子索引重建可能需要数分钟。

### 4. 首次使用

1. 打开 `http://localhost` 注册并登录。
2. 进入“大模型配置”，添加并启用自己的 OpenAI-compatible Provider。
3. 进入“面试准备”，选择有已发布原子的岗位开始训练。
4. 面试结束后先看初步报告；详细报告稍后在“历史报告”中查看。

### 5. 设置管理员

私有题库维护不需要管理员。维护公共 starter 题库需要 `ADMIN` 角色。

本机部署后可先注册一个账号，再执行一次 SQL 授权：

```powershell
docker exec -it interview-db mysql -uroot -p ai_interview_ds
```

```sql
UPDATE `user`
SET role = 'ADMIN', admin_granted_at = CURRENT_TIMESTAMP
WHERE username = 'your_username';
```

后续可以在应用设置页的管理员区域授予或撤销其他管理员。

## 题库维护

### 私有题库

1. 登录应用，进入“知识库 / 题库”。
2. 创建私有岗位，或选择已有私有岗位。
3. 在本机使用题库维护 skill 生成 JSON 导入包。
4. 回到工作台上传导入包，校验后导入为草稿。
5. 人工审查和修订原子。
6. 发布原子或一键发布全部草稿，发布后才会进入面试 RAG。

生成导入包示例：

```powershell
$env:DEEPSEEK_API_KEY="your_deepseek_key"
python scripts/question_bank_import.py --input .\materials\java --category java --mode DRAFT
```

脚本默认使用：

- `DEEPSEEK_BASE_URL=https://api.deepseek.com/v1`
- `DEEPSEEK_MODEL=deepseek-chat`
- `DEEPSEEK_API_KEY`

处理 PDF 或 DOCX 时需要额外依赖：

```powershell
pip install PyPDF2 python-docx
```

### 公共题库

- 公共 starter 内容位于 `backend/src/main/resources/knowledge_base/atoms/**/*.json`。
- 空库首次启动会自动导入公共题库；已有旧公共原子缺少岗位归属时，启动会补齐 `position_id`、`knowledge_base_id` 并重建 Qdrant payload。
- 管理公共题库必须使用 `ADMIN` 账号，在同一个“知识库 / 题库”工作台操作。

## 本地开发

后端：

```powershell
cd backend
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

常用验证：

```powershell
cd backend
mvn test
```

```powershell
cd frontend
npx vitest run
npm run build
```

```powershell
python -m unittest discover -s tests
```

## 生产 Compose

生产部署可基于 `docker-compose.prod.yml`：

```powershell
Copy-Item .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

生产环境需要额外确认：

- 使用强随机 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD`、`JWT_SIGN_KEY`、`APP_LLM_CONFIG_ENCRYPTION_KEY`、`APP_ANALYTICS_HASH_SALT`。
- 如果 `.env` 来自 `.env.example`，将 `QDRANT_URL` 改为 `http://qdrant:6333`，或删除该变量使用 Compose 默认值。
- 配置 `APP_CORS_ALLOWED_ORIGINS` 为实际域名。
- 配置 `FRONTEND_HTTP_BIND`，避免端口冲突。
- 如启用 HTTPS profile，配置 `DOMAIN_NAME` 并准备 `Caddyfile`。
- 备份 `mysql_data/`、`qdrant_data/`、`redis_data/`、`uploads/`、`knowledge_storage/`、`embedding_model_cache/`。

## 排错

| 现象 | 处理 |
| --- | --- |
| 首次启动很慢 | 查看 `docker logs -f interview-embedding-service` 和 `docker logs -f interview-backend`，通常是模型下载或公共题库索引重建。 |
| 面试提示没有可用题库 | 确认岗位下存在 `PUBLISHED` 且 `SYNCED` 的原子；在“知识库 / 题库”中发布并重建索引。 |
| 用户侧 LLM 功能不可用 | 登录后到“大模型配置”添加并启用 Provider；项目没有系统兜底 key。 |
| 注册邮件失败 | 检查 `MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD` 是否为 SMTP 授权码。 |
| 切换 embedding 模型后检索异常 | 确认 `QDRANT_VECTOR_SIZE` 与模型输出维度一致，并重建题库索引。 |

## 项目结构

```text
.
├── backend/                         # Spring Boot 后端
├── frontend/                        # Vue 3 前端
├── embedding-service/               # FastAPI embedding 服务
├── scripts/question_bank_import.py  # 本机题库导入包生成脚本
├── skills/interview-question-bank/  # Codex 题库维护 skill
├── scripts/retrieval_eval/          # RAG 离线评测工具链
├── docs/                            # 架构、计划与设计文档
├── image/                           # 架构图和页面截图
├── docker-compose.example.yml       # 本机 Compose 模板
├── docker-compose.prod.yml          # 生产 Compose 模板
├── CONTEXT.md                       # 领域上下文
└── CHANGELOG.md                     # 更新日志
```

## 相关文档

- [领域上下文](CONTEXT.md)
- [当前设计文档](docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md)
- [后续事项](docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-followups.zh.md)
- [更新日志](CHANGELOG.md)

## 题库来源

内置题库包含自整理内容，部分知识点参考公开面试资料与 mianshiya.com。使用前请按自己的部署、岗位和授权边界复核内容。
