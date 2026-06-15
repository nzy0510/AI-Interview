# 用户自主管理题库、RAG 与报告重构设计

状态：按 2026-06-15 受控开放方向修订
日期：2026-06-13，修订：2026-06-15
范围：InterWise 的题库归属、导入包、RAG 检索、面试报告、公共/私有平台边界。
当前领域边界：`CONTEXT.md`

## 1. 背景

InterWise 需要从“开发者维护题库”的模式，重构为普通用户也能独立运行和维护的面试练习平台：

- 本地自部署用户可以部署项目并管理自己的面试题库。
- 云端部署可以服务多个普通用户，每个用户拥有隔离的岗位、题库、LLM 配置、面试记录和报告。
- 现有内置题库继续作为公共 starter 内容，但必须结构化、明确作用域，并受到权限保护。
- 题库开放采用受控开放路线：用户在本机让 Agent 利用题库维护 skill 处理文档并生成导入包，再在应用内仿照原管理员维护题库的流程导入、审查、发布和索引自己的私有题库。

当前实现曾以开发者专用的 Question Bank Admin 面板为中心，通过 `APP_ADMIN_TOKEN` 保护；题库导入包在应用外生成，`knowledge_atom` 是全局数据，岗位到分类的映射依赖静态配置。
本次重构不是另起一套导入平台，而是把原管理员题库维护面板中的导入、编辑、发布和索引维护能力按 ownership 开放给普通用户维护私有题库；管理员只额外维护公共 starter 题库。面试 RAG 和报告改造继续保留。

## 2. 目标

- 让普通登录用户可以管理自己的私有岗位和私有题库。
- 保留内置公共题库，并迁移为三个明确的公共岗位。
- 允许用户在本机让 Agent 利用题库维护 skill 处理结构化文档并生成 JSON 导入包。
- 将原管理员题库维护面板的导入、编辑、发布和索引维护流程开放给普通用户，用于维护自己的私有题库。
- 支持用户在知识库 / 题库工作台中导入包、查看草稿、编辑原子、手动创建原子、审查并显式发布。
- 支持管理员使用同一套面板流程维护公共 starter 内容；普通用户只能维护自己的私有内容。
- 公共题库和私有题库的 RAG 检索严格隔离。
- 所选岗位没有已发布且索引成功的知识时，禁止开始面试。
- 面试报告异步生成，并包含逐题评分和参考答案来源。
- 同时支持本地 Docker 自部署和未来云端受控开放平台部署。

## 3. 第一版非目标

- 不做应用内任意 PDF/DOCX/Markdown/TXT 上传后自动生成高质量题库。
- 不引入 MarkItDown / document-converter 作为 MVP 主流程。
- 不做应用内批量 LLM 原子生成和 LLM 二审流水线。
- 不做上传或导入后自动发布。
- 不支持 PPT、Excel、图片 OCR、网页 URL 抓取、压缩包批量导入。
- 不引入 RabbitMQ、Kafka 等外部消息队列。
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

### 4.2 本机 skill 生成导入包与面板发布

题库维护主路径仿照原管理员维护题库流程，但把 scope 从全局/公共扩展为用户私有：

1. 用户在本机准备结构化资料。
2. 用户让本机 Agent 利用 `interview-question-bank` 或等价题库维护 skill 生成 JSON 导入包。
3. 用户登录 InterWise，进入知识库 / 题库工作台。
4. 用户使用与原管理员维护面板相同的导入入口，把导入包导入到自己的私有岗位。
5. 主应用校验导入包和 ownership，将 atom 落为私有草稿或待审查状态。
6. 用户在面板中人工审查、修订并显式发布。
7. 已发布 atom 才同步到 Qdrant 并进入面试 RAG。

题库维护 skill 是本机生产工具，不是主应用运行时依赖：

- 可以使用 Agent / LLM 生成高质量结构化原子。
- 只生成导入包，不直接写主应用数据库。
- 不带发布授权语义。
- 不绕过主应用 ownership 或 `ADMIN` 权限。

导入包沿用并规范化原维护流程需要的结构，至少应包含：

- package version
- position metadata
- knowledge base metadata
- atoms
- tags
- difficulty
- answer guide
- common mistakes
- follow-up path
- source reference

### 4.3 复制公共岗位

普通用户可以将公共岗位复制到自己的工作区：

- 创建私有岗位。
- 创建私有默认知识库。
- 将公共 atom 复制为私有 atom。
- 复制后的 atom 保持已发布状态。
- 后台重建带私有作用域 payload 的 Qdrant 向量。
- 公共题库后续更新不会自动覆盖用户的私有副本。

复制流程不重新执行 LLM 审查或本机题库维护 skill，因为公共内容已经由管理员发布。

### 4.4 管理员角色

移除旧的 `APP_ADMIN_TOKEN` 产品流程。

管理员是带有管理员角色的普通用户账号：

- 初始管理员默认：
  - 用户名：`nzy333`
  - 邮箱：`1525764737@qq.com`
- 该配置应通过初始化配置或 seed 实现，不应硬编码在业务逻辑中。
- 当前题库维护方向中，管理员主要维护公共岗位和公共知识库。
- 管理员授权可通过初始化配置或后续独立管理能力解决，不是本轮题库面板开放的核心目标。
- 普通用户只能管理自己的私有内容。

### 4.5 单一当前启用 LLM Provider

第一版保留现有模型：

- 用户可以保存多个 Provider 配置。
- 同一时间只有一个 active Provider。
- 面试追问、报告生成、AI Mentor 分析等成本型 LLM 操作使用当前 active Provider。
- 不使用系统兜底 API Key。
- 没有 active Provider 时，依赖 LLM 的用户侧功能应阻止执行并提示先配置。

MVP 中题库 atom 批量生成和质量审查主要在本机 Agent + 题库维护 skill 中完成，不作为主应用内置批处理能力。
后续如果加入应用内单原子辅助改写或质量检查，也必须使用当前 active Provider，并保持人工发布边界。

### 4.6 导入限制

第一版采用保守限制：

- 单个导入包有明确大小上限。
- 单次导入最多创建 100 个 atom 草稿。
- 导入包必须通过 schema 校验。
- 超过上限时整包拒绝或采用明确可见的截断策略，不能静默丢失内容。
- 导入成功后不自动发布，不自动进入 Qdrant。

## 5. 目标信息架构

### 5.1 用户导航

左侧新增一级入口：

```text
知识库 / 题库
```

普通用户看到：

- 只读公共岗位，以及复制入口。
- 我的岗位。
- 当前岗位下的导入包 / 导入批次，入口沿用原管理员维护面板的导入流程。
- 当前岗位下的草稿、待审查、已发布 atom。
- 发布、重建索引和失败重试操作。

管理员额外看到：

- 公共题库维护。
- 公共岗位管理。

### 5.2 知识库页面区域

第一版页面分为四个区域：

1. 岗位列表
   - 公共岗位。
   - 我的岗位。
   - 状态：可面试、无题库、待审查、索引中、索引失败、已归档。

2. 导入包 / 导入批次
   - 复用原题库维护面板的导入入口导入 JSON 包。
   - 展示 package version、atom 数量、导入状态、错误摘要。
   - 展示 schema 校验失败位置。
   - 支持失败后重新导入。

3. 知识原子
   - 草稿、待审查、已发布、需重索引、已归档。
   - 按导入批次、标签、发布状态、索引状态筛选。
   - 支持手动新增、编辑、删除未发布草稿。
   - 支持发布草稿和编辑已发布 atom 生成新草稿版本。

4. 任务
   - 展示导入、发布、重建索引、报告生成进度。
   - 展示失败阶段。
   - 展示脱敏后的错误信息。
   - 支持重试。

页面不保留旧开发者统计入口，不把普通用户引向 admin-token 或脚本发布路径。公共题库维护只对管理员开放；普通用户看到的是同一套维护能力的私有 scope 版本。

### 5.3 岗位与知识库关系

第一版采用“一个岗位一个默认知识库”。

一个知识库可以包含多个导入批次、主题、标签和 atom：

```text
岗位
  -> 默认知识库
      -> 导入批次
      -> 知识原子
      -> 发布版本
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

### 6.4 导入批次

新表：`knowledge_import_batch`

核心字段：

- `id`
- `scope`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `package_version`
- `package_name`
- `package_checksum`
- `atom_count`
- `status`：`PENDING` / `VALIDATING` / `IMPORTED` / `FAILED` / `ARCHIVED`
- `error_summary`
- `error_detail_json`
- `source_ref`
- `created_by`
- `create_time`
- `update_time`

规则：

- 普通用户只能向自己的私有岗位导入。
- 管理员可以向公共岗位导入。
- 导入批次只表示草稿落库，不表示发布。
- 如果保存原始导入包，应只保存必要内容或受控 storage key，不保存敏感本机路径。

### 6.5 知识原子

现有 `knowledge_atom` 模型应迁移，而不是长期保留一套旧结构。

需要新增或体现的概念：

- `scope`：`PUBLIC` / `PRIVATE`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `import_batch_id`
- `current_version_no`
- `publication_status`：`DRAFT` / `PUBLISHED` / `ARCHIVED`
- `vector_status`：`PENDING` / `SYNCED` / `FAILED` / `REINDEX_REQUIRED`
- `tags_json`
- `source_ref`
- `checksum`
- `created_by`
- `updated_by`

原有的 subject、difficulty、principles、pitfalls、follow-up paths、answer guide 等字段继续保留。

第一版不要求 `PASS` / `NEEDS_REVIEW` / `REJECT` 这类 LLM 二审状态作为发布前置条件。
如果保留审查字段，应以人工审查和结构化校验为准，不能阻塞没有 LLM 二审的受控导入路径。

### 6.6 Atom 版本化

已发布 atom 不直接覆盖。

规则：

- 编辑已发布 atom 时创建 draft revision。
- 草稿 revision 可人工编辑和发布。
- 发布后替换当前可检索版本。
- 旧版本只读保留。
- 报告保存快照，因此 atom 变更不会影响历史报告。

### 6.7 统一任务表

新表：`app_job`

任务类型：

- `IMPORT_PACKAGE`
- `PUBLISH_ATOMS`
- `REINDEX_POSITION`
- `GENERATE_REPORT`

后续增强可增加：

- `CONVERT_DOCUMENT`
- `ASSIST_ATOM_REWRITE`
- `REVIEW_ATOMS`

核心字段：

- `id`
- `job_type`
- `scope`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `import_batch_id`
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

## 7. 面板导入与发布流程

### 7.1 导入入口

主应用在知识库 / 题库工作台提供受保护的导入入口，该入口应尽量复用原管理员题库维护面板的交互和服务能力：

- 普通用户只能导入到自己的私有岗位。
- 管理员可以导入到公共岗位。
- 导入入口接受由本机 Agent + 题库维护 skill 生成的 JSON 导入包。
- 导入前执行 schema 校验和权限校验。
- 导入后 atom 只进入草稿或待审查状态。

导入入口不执行：

- 任意文档转换。
- 批量 LLM 生成 atom。
- 批量 LLM 二审。
- 自动发布。
- 自动同步 Qdrant。

### 7.2 Schema 校验

校验必须覆盖：

- package version。
- required fields。
- enum 值。
- 字段长度。
- atom 数组上限。
- 重复 atom key。
- difficulty / tags / follow-up path 的结构。
- source reference 格式。

错误必须能定位到：

- package 级别。
- atom index。
- 字段路径。
- 人可读错误信息。

错误消息必须脱敏，不暴露 API key、token、绝对私有路径或敏感请求头。

### 7.3 人工审查与发布

发布规则：

- 私有草稿 atom 可以由 owner 发布；公共草稿 atom 只能由管理员发布。
- 发布前必须通过必填字段、结构、长度、标签和追问路径格式校验。
- 普通用户不能发布公共 atom。
- 管理员可以发布公共 atom。
- 导入包不能声明“自动发布”或绕过发布权限。

发布动作：

- atom 状态改为已发布。
- 生成 embedding。
- upsert Qdrant points。
- 标记 vector 状态。
- 岗位只有在有足够已发布且 `SYNCED` 的 atom 后才可面试。

## 8. 文件存储与文档转换边界

第一版不把应用内原始文档上传和 MarkItDown 转换作为 MVP 主路径。

因此：

- 不要求新增 `document-converter` 服务。
- 不要求主应用保存 PDF/DOCX 原文件和转换后 Markdown。
- 不要求在 Java 后端中调用 Python 文档转换。
- 不要求文件上传型安全扫描作为本轮前置。

如果为了保存导入包或导入日志需要文件存储，应通过 `FileStorageService` 或等价抽象保存受控 artifact。
对象存储、原始文件生命周期、文档转换、安全扫描进入 followups。

## 9. Qdrant 策略

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
- `versionNo`
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

## 10. 面试流程改造

### 10.1 结构化岗位选择

面试准备页从自由输入岗位改为结构化岗位选择：

- 公共岗位。
- 我的岗位。
- 每个岗位显示状态。

状态：

- 可用
- 无题库
- 待审查
- 索引中
- 索引失败
- 已归档

私有岗位没有已发布且已同步的 atom 时，不能开始面试。

### 10.2 结构化回合记录

系统应记录结构化 turn 数据：

- 面试记录 ID
- turn index
- phase
- AI 问题
- 用户回答
- 检索到的 atom ids
- 检索到的 atom version
- 进入 prompt 的 atom 快照或摘要
- 检索策略
- 答案来源元数据
- 时间戳

报告生成不应再只依赖 `interview_record.chat_history`。

## 11. 报告生成

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

## 12. 迁移策略

这是对旧面试/报告数据的破坏性迁移，但不删除用户和题库资产。

### 12.1 保留

- 用户账号。
- 用户 LLM 配置。
- 简历画像。
- 反馈。
- 迁移后的现有公共题库内容。

### 12.2 清理

- 旧面试记录。
- 旧 RAG request/hit 日志。
- 旧报告字段/数据。
- 如果 Mentor 持久化缓存依赖旧面试历史，也清理。

### 12.3 转换现有题库

现有 `knowledge_atom` 迁移到新模型：

- `scope = PUBLIC`
- `owner_user_id = null`
- `position_id = 三个公共岗位之一`
- `knowledge_base_id = 公共岗位默认知识库`
- 旧状态为已发布时，`publication_status = PUBLISHED`
- `vector_status` 可迁移或重建

迁移后执行全量 reindex，确保 Qdrant payload 包含新的 scope 字段。

## 13. 安全与隐私要求

- 普通用户不能读写、删除、reindex 其他用户的私有内容。
- 普通用户不能编辑公共岗位或公共 atom。
- 管理员接口要求 JWT + 管理员角色。
- 从普通产品流程中移除 `APP_ADMIN_TOKEN`。
- 导入包字段必须做 schema 校验，不能信任前端传入的 scope、owner 或发布状态。
- 导入包错误消息不能暴露本机绝对路径、API key、token、密码或敏感 header。
- LLM 错误必须脱敏并截断。
- 日志不能打印 API key、token、密码或敏感 header。
- Qdrant filter 必须由后端强制构造，不能依赖前端可选参数。
- 题库维护 skill 只能生成导入包，不能绕过主应用权限发布原子。

## 14. 分阶段实施

### Phase 1：数据模型与迁移

- 增加角色模型。
- 增加岗位、知识库、导入批次、job、turn、report 表。
- 将公共题库迁移为三个公共岗位。
- 清理旧面试/报告数据。
- 增加 Qdrant payload 模型和 reindex 方案。

### Phase 2：题库维护 skill 与导入包契约

- 从历史中恢复题库维护 skill。
- 恢复或重建导入包生成脚本。
- 固定 JSON 导入包 schema。
- 提供导入包示例和校验说明。
- 明确 skill 只生成导入包，不发布。

### Phase 3：开放原题库维护面板为用户工作台

- 将原管理员题库维护面板中导入、编辑、发布、索引状态维护能力迁移到知识库 / 题库页面。
- 创建私有岗位。
- 导入 JSON 导入包。
- 校验导入包并落草稿。
- 展示导入批次、草稿、发布状态和索引状态。
- 支持手动新增/编辑 atom。

### Phase 4：发布、版本与索引

- 发布合格 atom。
- 编辑已发布 atom 时创建草稿版本。
- embedding 并同步 Qdrant。
- 标记 vector 状态。
- 支持 reindex。

### Phase 5：RAG 面试切换

- 结构化岗位选择。
- 阻止不可用岗位开始面试。
- 严格作用域 Qdrant 检索。
- 结构化 turn 记录。
- 面试链路移除静默 MySQL fallback。

### Phase 6：异步报告

- 结束面试时创建 report job。
- 生成总报告和逐题 item。
- 显示每个参考答案来源。
- 支持失败报告重试。

### Phase 7：管理员公共题库管理

- 管理公共岗位和公共知识库。
- 管理员通过导入包维护公共 starter 内容。
- 普通用户只读公共内容。

## 15. 测试策略

后端：

- 公共岗位和 atom scope 的 migration 测试。
- 公共/私有/管理员接口权限测试。
- 导入包 schema 校验测试。
- 导入批次和 job 生命周期、重试、重启恢复测试。
- 发布资格、版本替换和 Qdrant 同步失败测试。
- Qdrant filter 构造测试。
- 报告生成解析测试。

前端：

- 知识库页面状态测试。
- 普通用户和管理员的路由/侧边栏可见性。
- 导入包错误展示。
- 原子草稿编辑和发布入口状态。
- 面试准备页岗位可用性保护。
- 报告生成中/已完成/失败状态。

集成：

- 导入包 -> 草稿 atom -> 人工审查 -> 发布 -> Qdrant -> 面试检索。
- 公共 starter 导入包 -> 管理员发布 -> 普通用户只读。
- 复制公共岗位 -> 私有索引副本 -> 私有岗位面试。
- 结束面试 -> 异步报告 -> 展示逐题来源。

安全审查：

- 跨用户数据访问。
- 管理员角色校验。
- 导入包字段注入。
- 敏感信息脱敏。
- Qdrant 无作用域检索。

## 16. 主要风险

- 题库维护 skill 输出质量会直接影响面试质量，需要导入包 lint 和人工审查兜底。
- 导入包 schema 太宽会降低质量，太窄会降低用户维护效率。
- migration 和 reindex 风险高，必须在本地可重复验证。
- 严格禁止 fallback 能保证质量，但 Qdrant 异常会变成用户可见失败。
- 云端平台化还需要第一版之外的配额、存储和安全控制。

## 17. 实施备注

- `CONTEXT.md` 是当前领域边界的最高优先级文档。
- issue 拆分以 `docs/superpowers/plans/2026-06-13-user-owned-question-bank-rag-report-issues.zh.md` 为执行入口。
- followups 以 `docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-followups.zh.md` 为后续增强池。
- 不应长期保留两套并行题库模型。
- 旧 scripts/skills 应恢复并适配新 schema，作为本机题库维护工具，而不是旧开发者发布后门。
- 实现后要更新 README、CHANGELOG 和相关架构文档，解释公共岗位、私有岗位、导入包和题库维护 skill。
- 本任务属于多 Agent L3/High-risk，因为涉及数据库、RAG、导入包契约、前端、权限和部署。
