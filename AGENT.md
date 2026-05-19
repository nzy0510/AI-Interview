# AGENT.md — InterWise AI 模拟面试系统

> 渐进式上下文：先读本文件即可开展大多数任务；需要深入时再按末尾指引加载对应文档。

## 1. 项目身份（10 秒了解）

InterWise 是一个 **AI 模拟面试平台**，支持文字面试、视频面试、简历画像、RAG 追问、面试复盘与 AI Mentor。
- **后端**：Java 17 / Spring Boot 3.2 / MyBatis-Plus / LangChain4j / DeepSeek / MySQL 8 / Redis 7 / Qdrant
- **前端**：Vue 3.5 / Vite 7 / Element Plus / ECharts / face-api.js / Web Speech API
- **部署**：Docker Compose 一键编排（frontend + backend + mysql + redis + qdrant + mcp-skill）
- **生产环境**：Azure Ubuntu VM + `docker-compose.prod.yml`，当前为内测阶段（HTTPS，Azure DNS 名称访问）

## 2. 核心架构

```
User → Vue3 前端 → HTTP/SSE → Spring Boot 后端 → DeepSeek LLM
                                    ├── MySQL 8  (用户、面试记录、题库)
                                    ├── Redis    (会话、Mentor 缓存)
                                    └── Qdrant   (向量检索, RAG)
```

面试流程由 `InterviewPhase` 状态机驱动：`OPENING → TECHNICAL → HR → CLOSING → FINISHED`。

## 3. 关键路径速查

### 后端 `backend/src/main/java/com/interview/`
| 包 | 职责 | 重要文件 |
|---|------|---------|
| `controller/` | REST API 入口 | `InterviewController`, `UserController`, `ResumeController`, `McpTokenController` |
| `service/impl/` | 核心业务 | `InterviewServiceImpl`(面试主逻辑+SSE), `UserServiceImpl`, `ResumeServiceImpl` |
| `service/` | 接口与辅助 | `MentorService`, `SessionStore`, `RagRetriever`, `EvaluationGenerator`, `EmailService` |
| `service/questionbank/` | 题库子系统 | `QuestionBankService`, `QdrantVectorService`, `QuestionBankBootstrapService` |
| `entity/` | MySQL 实体 | `User`, `InterviewRecord`, `KnowledgeAtom`, `ResumeProfile`, `InterviewPhase` |
| `config/` | 全局配置 | `ChatConfig`(LLM), `RedisConfig`, `JwtInterceptor`, `WebMvcConfig`, `InterviewPrompts` |
| `common/` | 统一响应 | `Result.java` |

### 前端 `frontend/src/`
| 目录 | 内容 |
|-----|------|
| `views/` | `Login`, `Home`, `InterviewSetup`, `Interview`, `VideoInterview`, `History`, `Resume`, `Mentor`, `Settings` |
| `components/` | `charts/`, `common/`, `dashboard/`, `interview/`, `layout/` |
| `api/` | `user.js`(用户认证), `interview.js`(面试接口) |
| `router/` | Vue Router, 路由守卫检查 `localStorage.token` |

### 数据库
- 初始化：`mysql/init/init.sql` + Flyway `backend/src/main/resources/db/migration/V1~V6`
- 配置：`backend/src/main/resources/application.yml`（敏感值从环境变量注入）

## 4. 数据流要点

- **面试**：前端 SSE 连接 → `InterviewController` → `InterviewServiceImpl` 按 Phase 调度 → DeepSeek + RAG 追问
- **简历**：PDF 上传 → `ResumeController` → `ResumeServiceImpl` → 解析写入 `resume_profile`
- **题库**：PDF/DOCX 等 → `scripts/question_bank_import.py` → `/internal/question-bank/import` API → MySQL `knowledge_atom` → Qdrant 向量索引
- **MCP**：外部 AI 客户端通过独立 MCP-Skill 服务的 `/mcp` 调用只读题库检索与上下文生成；主项目只负责用户 token 生命周期
- **Mentor**：`MentorService` 聚合面试历史 + 知识覆盖 + 风险提醒，Redis 缓存 24h

## 5. 开发约定

### 编码规范
- Controller 不写业务逻辑，Service 不拼复杂 SQL
- 新接口保持现有 REST 风格；事务用 `@Transactional` 在 Service 层
- 修改 `application.yml` / `SecurityConfig` / `WebMvcConfig` 前须确认影响范围

### 安全红线
- API Key / 密码 / JWT Secret **永不**硬编码，全部走 `.env` 注入
- `.env`、`application-local.yml`、数据目录已在 `.gitignore` 中
- 日志禁止打印完整密钥或 token

### 测试
- Service：JUnit + Mockito；Controller：MockMvc
- 修 bug 先写复现测试再修复；不为通过测试而删断言
- 验证命令：`cd backend && mvn test` / `cd frontend && npm run build` / `npm exec vitest -- --run`

### Git 工作流
- 功能开发创建分支，Conventional Commits（`feat:` / `fix:` / `docs:` 等）
- Agent 可自主 commit/push/merge，但须确认功能无误后再合并
- Merge conflict 先说明再等确认

## 6. 环境与启动

### 配置文件体系
| 文件 | 用途 | 是否提交 Git |
|------|------|--------------|
| `.env.example` | 本地开发环境变量模板 | ✅ |
| `.env.prod.example` | 生产（Azure VM）环境变量模板 | ✅ |
| `.env` | 实际使用的环境变量 | ❌ |
| `docker-compose.example.yml` | 本地 Docker Compose 模板 | ✅ |
| `docker-compose.prod.yml` | 生产 Docker Compose（不暴露内部端口） | ✅ |
| `docker-compose.yml` | 本地实际使用的 Compose | ❌ |

### 本地开发
```powershell
# Docker 一键启动
cp .env.example .env          # 补齐 DB_PASSWORD, DEEPSEEK_API_KEY, JWT_SIGN_KEY 等
docker compose up -d --build  # 前端:80  后端:8080  MySQL:13307  Redis:6379  Qdrant:6333

# 或直接本地运行
cd backend  && mvn spring-boot:run
cd frontend && npm install && npm run dev   # VITE_API_BASE_URL 指向 localhost:8080
```

### Azure 生产环境
- VM：Ubuntu 22.04，项目目录 `/opt/interwise`
- 配置：`cp .env.prod.example .env`，替换所有 `replace_with_...` 占位符
- 启动：`docker compose --env-file .env -f docker-compose.prod.yml --profile https up -d --build`
- 仅暴露端口 80/443（Caddy 反向代理），后端/MySQL/Redis/Qdrant 仅 Docker 内网可达
- 视频面试依赖 HTTPS，需通过 `https://interwise.japaneast.cloudapp.azure.com` 或后续自有域名访问

## 7. 深入阅读索引

需要更多细节时，按需加载以下文档（不要一次全读）：

| 场景 | 文档 |
|------|------|
| 完整功能说明与 API 列表 | `README.md` |
| Azure 部署步骤 | `DEPLOYMENT.md` |
| Azure 日常运维（命令、备份、安全、HTTPS 路线） | `AZURE_OPERATIONS.md` |
| 生产环境变量模板 | `.env.prod.example` |
| AI Agent 协作规则 | `CLAUDE.md` |
| 项目更新记录 | `docs/updates/` |
| Agent 工作流配置 | `docs/agents/` |
| 题库维护 Skill | `skills/interview-question-bank/` |
| Flyway 迁移历史 | `backend/src/main/resources/db/migration/` |
