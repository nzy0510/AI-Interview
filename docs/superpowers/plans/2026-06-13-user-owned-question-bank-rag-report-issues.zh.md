# 用户自有题库、RAG 与报告重设计 Issue 拆分

状态：可发布到 issue tracker
日期：2026-06-13
父级规格：`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

本文档将已批准的 tracer-bullet 拆分扩展为可直接发布的 issue 正文。
当前尚未配置 issue tracker 或 triage label 词表，因此文中的 issue 编号仍使用临时本地标识。

## 发布说明

- 按依赖顺序发布。
- 创建真实 issue 后，将 `IQB-*` 标识替换为实际 tracker issue ID。
- 保留 `HITL` issue 作为明确的人工审查闸门。
- 每个 `AFK` issue 应该在其阻塞项完成后，可由 Agent 在不新增产品决策的前提下独立实现。

## IQB-01：建立岗位/知识库/原子作用域模型与破坏性迁移

类型：HITL

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

为岗位、知识库、知识原子、用户角色、面试轮次、报告和任务引入新的公共/私有 ownership 模型。
将现有内置题库迁移到三个明确的公共岗位中，保留面向用户的账户数据，并按照已批准的破坏性迁移策略清理旧面试和旧报告数据。

### 验收标准

- [ ] 系统有明确的公共岗位：`Java 后端开发`、`Web 前端开发`、`AI 大模型应用开发`。
- [ ] 每个公共岗位都有一个默认公共知识库。
- [ ] 私有岗位和私有知识库可以用 owner scope 表达。
- [ ] 现有公共题库原子迁移到新的作用域模型中。
- [ ] 旧面试记录、旧报告衍生数据、旧面试绑定的 RAG 日志被清理。
- [ ] 用户账户、用户 LLM Provider 配置、简历画像和反馈被保留。
- [ ] bootstrap 管理员账户可配置为用户名 `nzy333`、邮箱 `1525764737@qq.com`。
- [ ] 迁移行为有文档说明，并可在本地 Docker 测试中重复执行。

### 依赖

无，可立即开始。

## IQB-02：管理员角色与公共/私有资源权限闭环

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

将管理员能力纳入正常的登录用户模型。
管理员可以管理公共岗位、公共知识库和管理员授权。
普通用户可以查看公共内容、复制到自己的工作区，并且只能管理自己的私有内容。

### 验收标准

- [ ] 管理员授权使用 JWT 加用户角色，而不是产品流程中的 `APP_ADMIN_TOKEN`。
- [ ] 管理员可以授予和撤销其他用户的管理员角色，同时保证至少保留一个管理员。
- [ ] 普通用户不能创建、编辑、发布、重建索引或归档公共资源。
- [ ] 普通用户不能读取或修改其他用户的私有岗位、文件、原子、任务或报告。
- [ ] 管理员专属导航对普通用户隐藏，并且后端授权也会保护。
- [ ] 权限测试覆盖公共、私有、跨用户和管理员专属路径。

### 依赖

- IQB-01

## IQB-03：统一异步任务表与本地 TaskExecutor 执行器

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

为导入、原子生成、原子审查、发布、重建索引和报告生成创建可恢复的本地任务系统。
任务持久化到数据库，由后端 worker 声明执行，通过轮询 API 暴露状态，并且在失败原因可由用户处理时支持重试。

### 验收标准

- [ ] 任务支持 `PENDING`、`RUNNING`、`FAILED` 和 `COMPLETED` 状态。
- [ ] 任务记录类型、作用域、owner、岗位、知识库、源文件或面试记录、阶段、进度、脱敏错误消息、是否可重试、重试次数、锁 owner 和锁过期时间。
- [ ] 后端启动时会重新入队 pending 任务和已过期的 running 任务。
- [ ] 失败任务不会自动重试，除非用户主动请求重试。
- [ ] 任务轮询只返回当前用户或管理员可见的任务。
- [ ] 任务生命周期测试覆盖声明、完成、失败、重试和启动恢复。

### 依赖

- IQB-01

## IQB-04：文件存储与 MarkItDown 文档转换服务

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

添加第一条文档导入 tracer 路径：上传受支持的知识文件，通过存储抽象保存原文件，通过 Python MarkItDown 服务转换为 Markdown，保存转换后的 Markdown，并通过任务/status 模型暴露转换结果。

### 验收标准

- [ ] 支持上传类型：PDF、DOCX、Markdown/MD 和 TXT。
- [ ] 大于 20 MB 的文件会被拒绝，并返回清晰的用户可见错误。
- [ ] 原始文件和转换后的 Markdown 都通过 `FileStorageService` 抽象存储。
- [ ] 第一版实现使用适合 Docker Compose 的本地挂载存储路径。
- [ ] 后端调用 `document-converter` 服务，而不是在 Java 请求处理中直接启动 Python。
- [ ] 上传、下载和读取接口会校验 ownership 或公共读取规则。
- [ ] 路径穿越和不支持文件类型测试已覆盖。

### 依赖

- IQB-03

## IQB-05：私有岗位知识库管理页面 MVP

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

添加一级知识库/题库页面，使普通用户可以创建私有岗位、查看默认知识库、上传文件，并跟踪转换或导入进度。
页面应让公共岗位只读，让私有岗位由用户自己管理。

### 验收标准

- [ ] 侧边栏为登录用户暴露 `知识库 / 题库` 入口。
- [ ] 用户可以创建、查看和归档自己的私有岗位。
- [ ] 新私有岗位会自动拥有一个默认知识库。
- [ ] 用户可以向自己的岗位知识库上传受支持文件。
- [ ] 页面展示文件状态、转换状态和关联任务状态。
- [ ] 公共岗位可见，但普通用户不可编辑。
- [ ] 在可行范围内，UI 状态测试或组件测试覆盖普通用户可见性和公共编辑操作禁用状态。

### 依赖

- IQB-01
- IQB-02
- IQB-03
- IQB-04

## IQB-06：LLM 原子生成与二审审查流程

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

使用当前用户的 active LLM Provider，将转换后的 Markdown 生成可用于面试的原子草稿，然后对这些草稿执行 LLM 审查。
流程应向用户展示生成和审查结果，同时阻止被拒绝内容发布。

### 验收标准

- [ ] 原子生成使用私有导入 owner 的 active LLM Provider，或执行公共导入的管理员的 active LLM Provider。
- [ ] 如果没有 active Provider，原子生成和审查会被阻断，并给出清晰的配置提示。
- [ ] 单次导入最多创建 100 个原子草稿，达到上限时标记 `atomLimitReached`。
- [ ] LLM 审查保存 `PASS`、`NEEDS_REVIEW` 或 `REJECT`，并在适用时保存原因、置信度和建议补丁。
- [ ] 第一版中 `REJECT` 原子不能发布。
- [ ] LLM 错误会被脱敏，不暴露 API key、token 或敏感请求头。
- [ ] 测试覆盖 Provider 必配拦截、原子数量上限、审查状态解析和失败消息脱敏。

### 依赖

- IQB-05

## IQB-07：原子人工确认、版本发布与 Qdrant 索引

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

允许用户审查生成的原子、接受建议补丁、做轻量人工编辑、手动创建原子、发布符合条件的原子，并用严格 ownership payload 将向量同步到 Qdrant。

### 验收标准

- [ ] `PASS` 原子可以由 owner 或管理员直接发布。
- [ ] `NEEDS_REVIEW` 原子需要接受补丁或进行人工处理后才能发布。
- [ ] 编辑已发布原子会创建草稿修订版，而不是原地覆盖已发布版本。
- [ ] 发布会替换当前可检索版本，并将旧版本保留为只读历史。
- [ ] 已发布原子会被 embedding 并 upsert 到 Qdrant，payload 包含 scope、owner、岗位、知识库、原子 ID 和状态。
- [ ] 向量状态能反映 pending、synced、failed 或 reindex-required。
- [ ] 测试覆盖发布资格、版本替换、索引失败和 payload 过滤字段。

### 依赖

- IQB-06

## IQB-08：公共题库管理员导入与维护流程

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

复用同一套导入、审查、发布和重建索引流水线来维护管理员管理的公共岗位。
管理员可以维护公共 starter 内容，普通用户继续以只读方式查看公共内容。

### 验收标准

- [ ] 管理员可以向公共岗位知识库上传文件。
- [ ] 公共导入使用执行该操作的管理员的 active LLM Provider。
- [ ] 管理员可以审查、发布、归档和重建公共原子索引。
- [ ] 普通用户可以查看公共岗位和公共可用状态，但不能修改公共内容。
- [ ] 公共岗位和公共原子操作由管理员与普通用户权限测试覆盖。
- [ ] 正常公共维护不再依赖旧的 developer-only 导入工具。

### 依赖

- IQB-02
- IQB-06
- IQB-07

## IQB-09：公共岗位复制为我的岗位

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

允许普通用户将任何公共岗位复制到自己的工作区。
复制后的岗位变为私有岗位，拥有默认私有知识库，并获得已发布公共原子的私有副本和独立私有 Qdrant 索引。

### 验收标准

- [ ] 用户可以将任何 active 公共岗位复制到自己的私有岗位中。
- [ ] 被复制的原子变为私有、归复制用户所有，并保持已发布状态。
- [ ] 复制不会重新执行 LLM 审查。
- [ ] 复制会触发私有 embedding 和带私有 scope payload 的 Qdrant upsert。
- [ ] 公共内容更新不会静默修改用户已有的私有副本。
- [ ] 测试覆盖复制隔离、重复复制命名或冲突行为，以及私有索引 payload。

### 依赖

- IQB-07
- IQB-08

## IQB-10：面试准备页切换到结构化岗位选择与可用性校验

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

用结构化公共/私有岗位选择替换自由文本或旧版面试岗位选择。
准备流程必须清晰展示岗位是否可用于面试，并在质量前置条件不满足时阻止开始面试。

### 验收标准

- [ ] 面试设置分别列出公共岗位和当前用户的私有岗位。
- [ ] 岗位状态包含 usable、no knowledge、indexing、index failed 和 archived。
- [ ] 已归档岗位不能开始新面试。
- [ ] 没有已发布且已同步原子的私有岗位不能开始面试。
- [ ] Qdrant 或索引不可用状态会阻止开始面试，而不是静默降级检索质量。
- [ ] 前端和后端 guard 都执行同一套开始面试规则。

### 依赖

- IQB-01
- IQB-07
- IQB-09

## IQB-11：面试 RAG 检索切换为严格作用域过滤

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

将面试检索切换到新的 scoped Qdrant payload 模型。
公共面试只能检索所选公共岗位的原子；私有面试只能检索当前用户在所选私有岗位下拥有的原子。

### 验收标准

- [ ] 公共检索始终按 public scope 和所选公共岗位过滤。
- [ ] 私有检索始终按 private scope、当前用户和所选私有岗位过滤。
- [ ] 面试检索绝不执行无作用域的向量查询。
- [ ] 当 Qdrant 不可用或为空时，面试检索不会静默回退到 MySQL。
- [ ] 检索过滤条件由后端服务构造，不依赖可选前端输入。
- [ ] 测试覆盖公共检索、私有检索、跨用户隔离和无回退行为。

### 依赖

- IQB-10

## IQB-12：结构化记录每轮面试对话与检索快照

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

将每一轮面试对话持久化为结构化数据，而不是只依赖原始 chat history。
每轮需要保留问题、用户回答、阶段、检索到的原子引用、prompt context snapshot、检索策略，以及稳定报告生成所需的时间戳。

### 验收标准

- [ ] 新面试记录绑定所选岗位。
- [ ] 每个技术或 HR 轮次保存 AI 问题、用户回答、阶段和轮次索引。
- [ ] 技术轮次保存检索到的原子 ID，以及历史报告所需的足够快照数据。
- [ ] 即使原子后续被编辑或归档，报告生成也能使用已保存的轮次数据。
- [ ] 现有面试 UX 在写入结构化轮次数据的同时继续可用。
- [ ] 测试覆盖轮次持久化、阶段分类和原子快照保留。

### 依赖

- IQB-11

## IQB-13：异步报告生成与逐题评分报告

类型：AFK

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

将面试结束后的报告生成迁移到统一任务系统。
报告应展示生成中、已完成或失败状态；已完成报告应包含逐题评分、参考答案或参考方向、改进建议，以及可见的答案来源标签。

### 验收标准

- [ ] 结束面试会创建 `GENERATE_REPORT` 任务，而不是阻塞等待完整报告生成。
- [ ] UI 可以展示报告生成中、已完成、失败和重试状态。
- [ ] 报告生成失败时保留面试数据，并允许重试。
- [ ] 技术题和 HR 题都获得 0-10 的综合逐题评分。
- [ ] 开场和收尾轮次不作为单独报告项评分。
- [ ] 报告项包含问题、用户回答、得分、参考答案或方向、改进建议和来源标签。
- [ ] 来源标签区分知识库参考、AI 生成技术参考和 HR 指导。
- [ ] 测试覆盖报告任务生命周期、分数解析、来源标签和历史快照稳定性。

### 依赖

- IQB-03
- IQB-12

## IQB-14：旧流程收口、文档更新与回归验证

类型：HITL

### 父级

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.zh.md`

### 构建内容

通过移除或降级旧 developer-only 路径来完成迁移收口，更新用户与开发者文档，并运行面向本地自部署和未来云平台就绪性的端到端验证矩阵。

### 验收标准

- [ ] 旧 admin-token 题库工作流从产品路径中移除，或明确标记为 developer-only。
- [ ] 文档说明公共岗位、私有岗位、复制公共岗位、用户 LLM Provider 要求、导入限制和报告生成状态。
- [ ] README、CHANGELOG 和相关架构文档已更新。
- [ ] 后端测试覆盖权限、migration、任务、Qdrant filter、文件上传和报告解析。
- [ ] 前端测试或手动验证覆盖知识库导航、面试设置 guard 和报告状态。
- [ ] 安全审查覆盖路径穿越、跨用户访问、管理员角色校验、密钥脱敏和无作用域检索。
- [ ] 本地 Docker 验证展示上传、原子审查、发布、面试和异步报告生成。

### 依赖

- IQB-01
- IQB-02
- IQB-03
- IQB-04
- IQB-05
- IQB-06
- IQB-07
- IQB-08
- IQB-09
- IQB-10
- IQB-11
- IQB-12
- IQB-13
