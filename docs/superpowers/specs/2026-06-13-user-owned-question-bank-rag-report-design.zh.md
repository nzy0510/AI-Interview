# 用户自主管理题库、RAG 与报告重构设计

状态：供用户审阅的设计草案
日期：2026-06-13
范围：InterWise 的题库归属、文档导入、RAG 检索、面试报告、公共/私有平台边界。

## 1. 背景

InterWise 需要从“开发者维护题库”的模式，重构为普通用户也能独立运行和维护的面试练习平台：

- 本地自部署用户可以部署项目并管理自己的面试知识库。
- 云端部署可以服务多个普通用户，每个用户拥有隔离的岗位、知识文件、LLM 配置、面试记录和报告。
- 现有内置题库继续作为公共 starter 内容，但必须结构化、明确作用域，并受到权限保护。

当前实现以开发者专用的 Question Bank Admin 面板为中心，通过 `APP_ADMIN_TOKEN` 保护；题库导入包在应用外生成，`knowledge_atom` 是全局数据，岗位到分类的映射依赖静态配置。本次重构将其替换为一等公民的“用户岗位 + 用户知识库”模型，同时保留现有动态 RAG 面试流程的核心思想。

## 2. 目标

- 让普通登录用户可以管理自己的题库。
- 保留内置公共题库，并迁移为三个明确的公共岗位。
- 允许用户创建私有岗位并管理私有知识文件。
- 支持在应用内导入 PDF、DOCX、Markdown、TXT。
- 上传文件后转 Markdown，生成知识原子，执行 LLM 二审，并由用户确认后发布。
- 所有 LLM 调用统一使用用户当前启用的 Provider。
- 公共题库和私有题库的 RAG 检索严格隔离。
- 所选岗位没有已发布且索引成功的知识时，禁止开始面试。
- 面试报告异步生成，并包含逐题评分和参考答案来源。
- 同时支持本地 Docker 自部署和未来云端开放平台部署。

## 3. 第一版非目标

- 不引入 RabbitMQ、Kafka 等外部消息队列。
- 不做上传后自动发布。
- 不支持 PPT、Excel、图片 OCR、网页 URL 抓取、压缩包批量导入。
- 不做面试时的标签级筛选。
- 不做一个岗位绑定多个知识库的组合检索。
- 不做按任务类型选择不同 LLM Provider。
- 不做团队/组织空间。
- 不做完整商业化配额和计费系统。
- 正常产品流程中不物理删除岗位，只做归档。

## 4. 已确认产品决策

### 4.1 公共岗位与私有岗位

内置公共题库迁移为三个明确的公共岗位：

1. `Java 后端开发`
2. `Web 前端开发`
3. `AI 大模型应用开发`

每个公共岗位绑定自己的公共知识库。普通用户可以直接使用公共岗位练习面试，但不能编辑公共内容。

用户可以创建私有岗位。私有岗位只属于当前用户，只检索该用户自己的私有知识原子。

### 4.2 复制公共岗位

普通用户可以将公共岗位复制到自己的工作区：

- 创建私有岗位。
- 创建私有默认知识库。
- 将公共 atom 复制为私有 atom。
- 复制后的 atom 保持已发布状态。
- 后台重建带私有作用域 payload 的 Qdrant 向量。
- 公共题库后续更新不会自动覆盖用户的私有副本。

复制流程不重新执行 LLM 二审，因为公共内容已经由管理员发布。

### 4.3 管理员角色

移除旧的 `APP_ADMIN_TOKEN` 产品流程。

管理员是带有管理员角色的普通用户账号：

- 初始管理员默认：
  - 用户名：`nzy333`
  - 邮箱：`1525764737@qq.com`
- 该配置应通过初始化配置或 seed 实现，不应硬编码在业务逻辑中。
- 管理员可以授权其他管理员。
- 管理员可以维护公共岗位和公共知识库。
- 普通用户只能管理自己的私有内容。

### 4.4 单一当前启用 LLM Provider

第一版保留现有模型：

- 用户可以保存多个 Provider 配置。
- 同一时间只有一个 active Provider。
- 所有 LLM 操作都使用当前 active Provider。
- 不使用系统兜底 API Key。
- 没有 active Provider 时，所有依赖 LLM 的功能都阻止执行并提示先配置。

适用范围包括：面试追问、atom 生成、atom 二审、报告生成、AI Mentor 分析。

### 4.5 文件导入限制

第一版采用保守限制：

- 单文件最大 20 MB。
- 单次导入最多生成 100 个 atom。
- 支持格式：PDF、DOCX、Markdown/MD、TXT。
- 达到 atom 上限时，标记 `atomLimitReached=true`，并提示文档可能未完全覆盖。

系统应解析全文，并要求 LLM 抽取最适合面试的前 100 个核心 atom，不能静默丢弃内容。

## 5. 目标信息架构

### 5.1 用户导航

左侧新增一级入口：

```text
知识库 / 题库
```

普通用户看到：

- 只读公共岗位，以及复制入口。
- 我的岗位。
- 当前岗位下的文件。
- 当前岗位下的草稿、已二审、已发布 atom。
- 导入任务和重试操作。

管理员额外看到：

- 公共题库管理。
- 公共岗位管理。
- 用户角色管理。

### 5.2 知识库页面区域

第一版页面分为四个区域：

1. 岗位列表
   - 公共岗位。
   - 我的岗位。
   - 状态：可面试、无题库、转换中、生成中、二审中、索引中、失败、已归档。

2. 知识库文件
   - 上传文件。
   - 展示原始文件、Markdown、转换状态。
   - 支持重新解析、重新生成 atom、删除文件资产。

3. 知识原子
   - 草稿、二审结果、已发布、需重索引、已归档。
   - 二审状态：PASS、NEEDS_REVIEW、REJECT。
   - 按来源文件、标签、二审状态、发布状态筛选。
   - 支持手动新增和编辑 atom。

4. 任务
   - 展示导入进度。
   - 展示失败阶段。
   - 展示脱敏后的错误信息。
   - 支持重试。

### 5.3 岗位与知识库关系

第一版采用“一个岗位一个默认知识库”。

一个知识库可以包含多个文件、主题、标签、导入批次和 atom：

```text
岗位
  -> 默认知识库
      -> 源文件
      -> 导入批次
      -> 知识原子
```

第一版不暴露“一个岗位多个命名知识库”的能力。知识库内部通过标签组织内容。

## 6. 数据模型方向

实际表名可以在实现时微调，但必须保留以下概念。

### 6.1 用户角色

在现有用户模型中增加角色能力。

建议字段：

- `role`：`USER` / `ADMIN`
- `admin_granted_by`
- `admin_granted_at`

初始化规则：

- 注册用户匹配配置中的初始管理员用户名/邮箱时，赋予管理员角色。
- 管理员可以授予或撤销其他用户的管理员角色。
- 系统应避免最后一个管理员被撤销。

### 6.2 岗位

新表：`interview_position`

核心字段：

- `id`
- `scope`：`PUBLIC` / `PRIVATE`
- `owner_user_id`：公共岗位为空，私有岗位为用户 ID
- `name`
- `description`
- `status`：`ACTIVE` / `ARCHIVED`
- `default_knowledge_base_id`
- `created_by`
- `create_time`
- `update_time`

规则：

- 公共岗位由管理员维护。
- 私有岗位由所属用户维护。
- 已归档岗位不能开始新面试。

### 6.3 知识库

新表：`knowledge_base`

核心字段：

- `id`
- `scope`
- `owner_user_id`
- `position_id`
- `name`
- `status`：`ACTIVE` / `ARCHIVED`
- `created_by`
- `create_time`
- `update_time`

第一版每个岗位使用一个默认知识库。

### 6.4 源文件

新表：`knowledge_source_file`

核心字段：

- `id`
- `scope`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `original_filename`
- `content_type`
- `file_size`
- `file_hash`
- `storage_key`
- `markdown_storage_key`
- `domain_tags_json`
- `status`：`UPLOADED` / `CONVERTING` / `CONVERTED` / `FAILED` / `ARCHIVED`
- `error_message`
- `created_by`
- `create_time`
- `update_time`

原始文件和 Markdown 通过 `FileStorageService` 存在数据库外部。

### 6.5 知识原子

现有 `knowledge_atom` 模型应迁移，而不是长期保留一套旧结构。

需要新增或体现的概念：

- `scope`：`PUBLIC` / `PRIVATE`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `source_file_id`
- `current_version_no`
- `review_status`：`PASS` / `NEEDS_REVIEW` / `REJECT` / `UNREVIEWED`
- `review_reason`
- `review_confidence`
- `suggested_patch_json`
- `publication_status`：`DRAFT` / `PUBLISHED` / `ARCHIVED`
- `vector_status`：`PENDING` / `SYNCED` / `FAILED` / `REINDEX_REQUIRED`
- `tags_json`
- `source_ref`
- `checksum`

原有的 subject、difficulty、principles、pitfalls、follow-up paths 等字段继续保留。

### 6.6 Atom 版本化

已发布 atom 不直接覆盖。

规则：

- 编辑已发布 atom 时创建 draft revision。
- 草稿 revision 可二审和发布。
- 发布后替换当前可检索版本。
- 旧版本只读保留。
- 报告保存快照，因此 atom 变更不会影响历史报告。

### 6.7 统一任务表

新表：`app_job`

任务类型：

- `IMPORT_FILE`
- `REGENERATE_ATOMS`
- `REVIEW_ATOMS`
- `PUBLISH_ATOMS`
- `REINDEX_POSITION`
- `GENERATE_REPORT`

核心字段：

- `id`
- `job_type`
- `scope`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `source_file_id`
- `record_id`
- `status`：`PENDING` / `RUNNING` / `FAILED` / `COMPLETED`
- `stage`
- `progress`
- `payload_json`
- `result_json`
- `failed_stage`
- `error_message`
- `retryable`
- `retry_count`
- `claimed_by`
- `locked_until`
- `created_by`
- `create_time`
- `update_time`

启动恢复：

- 重新入队 `PENDING`。
- 重新入队锁过期的 `RUNNING`。
- `FAILED` 不自动重试，由用户手动重试。
- Handler 应尽量幂等，能承受重试。

### 6.8 面试回合与报告

迁移时清理旧面试和报告数据。

保留：

- 用户账号。
- 用户 LLM 配置。
- 简历画像。
- 反馈。
- 迁移后的公共题库内容。

清理：

- 旧面试记录。
- 旧面试相关 RAG 检索日志。
- 旧报告派生数据。
- 如果存在基于旧面试历史的 Mentor 持久化缓存，也应清理。

新面试记录必须绑定 `position_id`。

建议新增：

- `interview_turn`
- `interview_report`
- `interview_report_item`

每个报告项保存快照：

- 问题
- 用户回答
- 阶段：`TECHNICAL` / `HR`
- 评分，建议 0-10
- 参考答案或参考回答方向
- 改进建议
- 答案来源：`KNOWLEDGE_BASE` / `AI_GENERATED` / `HR_GUIDE`
- 命中 atom 快照
- 生成时的模型/Provider 元信息
- 生成时间

历史报告不能动态依赖最新 atom 内容。

## 7. 文件存储

第一版使用本地挂载目录。

默认路径示例：

```text
data/uploads/knowledge-base/
```

数据库只保存元数据和 storage key。业务代码依赖 `FileStorageService` 抽象：

- `save`
- `read`
- `delete`
- `exists`
- `openStream`

第一版实现：本地文件系统。

未来实现：MinIO、S3、Azure Blob 或其他对象存储。

这个抽象对同时支持本地自部署和云端平台部署很重要。

## 8. 文档转换服务

新增 Python `document-converter` 服务封装 MarkItDown。

职责：

- 接收上传文件或内部文件引用。
- 将 PDF、DOCX、Markdown、TXT 转为 Markdown。
- 返回 Markdown 和转换元数据。
- 对不支持或转换失败的文件返回明确错误。

Spring Boot 仍然负责流程编排：

- 上传文件。
- 保存文件元数据。
- 创建 job。
- 调用 document-converter。
- 保存 Markdown。
- 继续 atom 生成。

第一版不要在 Java 后端中用 `ProcessBuilder` 直接调用 Python 脚本。

## 9. 导入与二审流程

### 9.1 自动准备

用户上传支持的文件后：

1. 保存原始文件。
2. 转换为 Markdown。
3. 使用用户 active Provider 生成 atom 草稿。
4. 对生成的 atom 执行 LLM 二审。
5. 标记为等待用户审核。

### 9.2 LLM 二审

LLM 二审检查：

- atom 是否只覆盖一个清晰知识点。
- atom 是否适合面试追问。
- 参考答案是否足够具体。
- 是否能追溯到来源材料。
- 是否明显重复。
- 难度和标签是否合理。
- 是否存在明显幻觉。

二审输出：

- `PASS`
- `NEEDS_REVIEW`
- `REJECT`
- 原因
- 建议修正
- 置信度

### 9.3 用户确认

发布规则：

- `PASS`：可发布。
- `NEEDS_REVIEW`：接受 LLM 修正或手动处理后可发布。
- `REJECT`：第一版不可发布。

发布动作：

- atom 状态改为已发布。
- 生成 embedding。
- upsert Qdrant points。
- 标记 vector 状态。
- 岗位只有在有足够已发布且 `SYNCED` 的 atom 后才可面试。

## 10. Qdrant 策略

使用一个共享 collection，例如：

```text
interview_atoms_e5_base
```

每个 point 的 payload 必须包含：

- `scope`
- `ownerUserId`
- `positionId`
- `knowledgeBaseId`
- `atomId`
- `status`

面试检索必须使用严格 filter：

- 公共岗位：
  - `scope = PUBLIC`
  - `positionId = selected public position`

- 私有岗位：
  - `scope = PRIVATE`
  - `ownerUserId = current user`
  - `positionId = selected private position`

禁止无作用域面试检索。

面试链路不做静默 MySQL fallback。Qdrant 不可用或岗位没有 synced atom 时，应阻断或明确失败，因为检索质量是产品保证的一部分。

管理/诊断搜索可以有独立行为，但不能影响面试链路。

## 11. 面试流程改造

### 11.1 结构化岗位选择

面试准备页从自由输入岗位改为结构化岗位选择：

- 公共岗位。
- 我的岗位。
- 每个岗位显示状态。

状态：

- 可用
- 无题库
- 索引中
- 索引失败
- 已归档

私有岗位没有已发布且已同步的 atom 时，不能开始面试。

### 11.2 结构化回合记录

系统应记录结构化 turn 数据：

- 面试记录 ID
- turn index
- phase
- AI 问题
- 用户回答
- 检索到的 atom ids
- 进入 prompt 的 atom 快照或摘要
- 检索策略
- 答案来源元数据
- 时间戳

报告生成不应再只依赖 `interview_record.chat_history`。

## 12. 报告生成

结束面试时创建 `GENERATE_REPORT` job，而不是阻塞等待完整报告。

体验：

- 用户结束面试。
- 后端返回 record id 和 report job id。
- 页面显示报告生成中。
- 用户可以离开页面。
- 历史列表显示生成中、已完成、生成失败。
- 失败报告可重新生成。
- 报告失败时保留面试数据。

评分：

- 对 TECHNICAL 和 HR 问题评分。
- OPENING 和 CLOSING 不做逐题评分。
- 每个评分项包含：
  - 问题
  - 用户回答
  - 综合得分
  - 参考答案或参考回答方向
  - 改进建议
  - 用户可见的答案来源

参考来源：

- 技术题命中 atom：显示来自知识库。
- 技术题未命中 atom：显示 AI 生成参考。
- HR 题：显示 AI 生成 HR 建议/参考。

推荐分值：

- 单题：0-10。
- 总报告：可继续使用 0-100。

## 13. 迁移策略

这是对旧面试/报告数据的破坏性迁移，但不删除用户和题库资产。

### 13.1 保留

- 用户账号。
- 用户 LLM 配置。
- 简历画像。
- 反馈。
- 迁移后的现有公共题库内容。

### 13.2 清理

- 旧面试记录。
- 旧 RAG request/hit 日志。
- 旧报告字段/数据。
- 如果 Mentor 持久化缓存依赖旧面试历史，也清理。

### 13.3 转换现有题库

现有 `knowledge_atom` 迁移到新模型：

- `scope = PUBLIC`
- `owner_user_id = null`
- `position_id = 三个公共岗位之一`
- `knowledge_base_id = 公共岗位默认知识库`
- 旧状态为已发布时，`publication_status = PUBLISHED`
- `vector_status` 可迁移或重建

迁移后执行全量 reindex，确保 Qdrant payload 包含新的 scope 字段。

## 14. 安全与隐私要求

- 普通用户不能读写、删除、reindex 其他用户的私有内容。
- 普通用户不能编辑公共岗位或公共 atom。
- 管理员接口要求 JWT + 管理员角色。
- 从普通产品流程中移除 `APP_ADMIN_TOKEN`。
- 上传文件路径必须防止 path traversal。
- 文件读写接口必须校验 ownership 或公共只读规则。
- LLM 错误必须脱敏并截断。
- 日志不能打印 API key、token、密码或敏感 header。
- Qdrant filter 必须由后端强制构造，不能依赖前端可选参数。

## 15. 分阶段实施

### Phase 1：数据模型与迁移

- 增加角色模型。
- 增加岗位、知识库、源文件、job、turn、report 表。
- 将公共题库迁移为三个公共岗位。
- 清理旧面试/报告数据。
- 增加 Qdrant payload 模型和 reindex 方案。

### Phase 2：用户知识库管理

- 增加左侧页面。
- 创建私有岗位。
- 上传文件。
- 保存原始文件和 Markdown。
- 接入 document-converter 服务。
- 增加 job 轮询。

### Phase 3：Atom 生成、二审、发布

- 通过 active Provider 生成 atom 草稿。
- 执行 LLM 二审。
- 展示 PASS/NEEDS_REVIEW/REJECT。
- 支持手动新增/编辑 atom。
- 发布合格 atom。
- embedding 并同步 Qdrant。

### Phase 4：RAG 面试切换

- 结构化岗位选择。
- 阻止不可用岗位开始面试。
- 严格作用域 Qdrant 检索。
- 结构化 turn 记录。
- 面试链路移除静默 MySQL fallback。

### Phase 5：异步报告

- 结束面试时创建 report job。
- 生成总报告和逐题 item。
- 显示每个参考答案来源。
- 支持失败报告重试。

### Phase 6：管理员公共题库管理

- 管理员角色 UI。
- 管理公共岗位和公共知识库。
- 授权/撤销管理员。
- 公共导入流水线使用管理员 active Provider。

## 16. 测试策略

后端：

- 公共岗位和 atom scope 的 migration 测试。
- 公共/私有/管理员接口权限测试。
- job 生命周期、重试、重启恢复测试。
- Qdrant filter 构造测试。
- 报告生成解析测试。
- 文件上传校验测试。

前端：

- 知识库页面状态测试。
- 普通用户和管理员的路由/侧边栏可见性。
- 面试准备页岗位可用性保护。
- 报告生成中/已完成/失败状态。

集成：

- 上传 Markdown -> 生成 atom -> 二审 -> 发布 -> Qdrant -> 面试检索。
- 复制公共岗位 -> 私有索引副本 -> 私有岗位面试。
- 结束面试 -> 异步报告 -> 展示逐题来源。

安全审查：

- Path traversal。
- 跨用户数据访问。
- 管理员角色校验。
- 敏感信息脱敏。
- Qdrant 无作用域检索。

## 17. 主要风险

- 不同 Provider 的 atom 生成质量差异较大。
- 生成和二审使用同一个 active 模型时，二审可能漏掉错误。
- 即使限制文件大小，大文档仍可能超出实际 token 处理能力。
- migration 和 reindex 风险高，必须在本地可重复验证。
- 严格禁止 fallback 能保证质量，但 Qdrant 异常会变成用户可见失败。
- 云端平台化还需要第一版之外的配额和存储控制。

## 18. 实施备注

- 可以保留导入包 contract 中有价值的概念，但生成和发布应进入应用内。
- 不应长期保留两套并行题库模型。
- 旧 scripts/skills 只有在适配新 schema 后才能继续作为开发者工具。
- 实现后要更新 README 和文档，解释公共岗位与私有岗位。
- 本任务属于多 Agent L3/High-risk，因为涉及数据库、RAG、文件上传、LLM、前端和部署。
