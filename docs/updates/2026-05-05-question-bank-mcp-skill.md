# 2026-05-05 更新记录：数据库题库、Qdrant、MCP 与 Skill

## 背景

本次更新的目标是把原先偏静态的面试知识库升级为可维护、可检索、可被外部 AI 客户端调用的数据库题库体系，并保留开发者通过 Skill 批量维护题库的能力。

同时，项目部署环境切换到 MySQL 8.0，并引入 Qdrant 作为专业向量数据库。旧 MySQL 5.7 数据不再导入，新环境以当前 MySQL 8 数据目录为准。

## 主要改动

### 1. 题库从 JSON 种子升级为数据库题库

- 新增 `knowledge_atom`、`knowledge_atom_version`、`knowledge_atom_import_batch`、`knowledge_atom_review` 等题库表。
- 后端启动时可从 `knowledge_base/atoms/**/*.json` 导入旧题库种子。
- 导入逻辑会跳过旧 JSON 中重复的 `atom_id`，避免整批失败。
- 题库支持 `DRAFT`、`PUBLISHED`、`ARCHIVED` 状态。

### 2. 引入 Qdrant 向量库

- 新增 Qdrant Docker 服务。
- 已发布题目会同步到 Qdrant collection：`interview_atoms`。
- MySQL 保存题目正文、状态和版本；Qdrant 保存向量索引，用于语义搜索。
- 启动时支持重建未同步题目的向量索引。

### 3. 新增内部题库维护 API

新增 `/internal/question-bank/**` 维护入口：

- 搜索题目
- 查询单个题目
- 查看分类统计
- 导入题库包
- 重建 Qdrant 索引

这些接口面向开发者维护使用，依赖 `QUESTION_BANK_ADMIN_TOKEN`，不对普通用户开放。

### 4. 新增 MCP 服务

新增 `/mcp` JSON-RPC 端点，可服务外部 AI 客户端。当前支持：

- `search_interview_atoms`
- `get_interview_atom`
- `list_interview_categories`
- `generate_interview_context`
- `validate_atom_import_package`
- `submit_atom_import_package`
- `reindex_question_bank`

读操作使用 `MCP_READ_TOKEN`，写操作使用管理员 token。

### 5. 新增题库维护 Skill

新增 `skills/interview-question-bank`：

- 支持从 PDF、DOCX、TXT、MD、JSON 资料生成题库导入包。
- 支持人工评审后导入，也支持用户明确授权后的 `AUTO_PUBLISH` 直接发布。
- 支持通过内部 API 或 MCP 提交题库包。
- 支持发布后验证搜索结果和重建索引。

### 6. Docker 与环境配置更新

- MySQL 切换为 `mysql:8.0`。
- 新增 `qdrant/qdrant` 服务。
- 本地 MySQL 端口使用 `13307:3306`，避免 Windows 常见端口占用问题。
- `.env.example` 新增题库、MCP、Qdrant 相关变量。

### 7. 修复简历缓存误用问题

- 前端不再在后端没有简历画像时读取旧全局 localStorage 缓存。
- 简历状态以后端 `/api/resume/profile` 为准。
- 后端无画像时显示未上传，并清理当前用户的简历缓存。

## 验证结果

- 后端单测：`mvn test` 通过，54 个测试通过。
- 前端构建：`npm run build` 通过。
- 前端单测：`npm exec vitest -- --run` 通过，13 个测试通过。
- Docker 验证：MySQL 8、Redis、Qdrant、backend、frontend 均可启动。
- 题库验证：833 条唯一题目成功导入 MySQL，全部同步到 Qdrant。
- MCP 验证：`initialize`、`tools/list`、`search_interview_atoms` 可正常返回。

## 后续建议

- 为新岗位继续扩展 `interview.position-categories` 映射。
- 对题库导入包增加更细的质量评分与重复语义检测。
- 生产部署时只暴露前端入口，MCP 和内部题库维护接口放在受控网络或网关后。
- 生产环境轮换所有真实 API Key、SMTP 授权码、JWT Secret、题库维护 token。
