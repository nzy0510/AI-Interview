# 按岗位重构知识覆盖 — 开发文档

## Problem Statement

当前知识覆盖功能存在以下问题：

1. **无岗位边界**：`MentorService.buildKnowledgeCoverage` 查询的是全系统所有已发布原子，Java 后端、Web 前端、AI 大模型、系统运维的原子全部混在一起，玫瑰图和覆盖数据失去了岗位维度的参考意义。
2. **RAG 日志缺 position_id**：`rag_retrieval_log` 和 `rag_retrieval_request_log` 只存了旧格式的 `position` 字符串名，没有结构化的 `position_id`，无法按岗位过滤检索命中。
3. **展示位置单一**：覆盖数据仅出现在 History 页面，题库维护者无法在工作台看到岗位的知识覆盖状态。
4. **热力图评级兜底不鲁棒**：`normalizeAbility` 对 LLM 输出的键名格式（snake_case、中文键名、大小写混用）适配不足，且缺少评级值校验。

## Solution

以岗位为粒度重构知识覆盖的完整链路：

1. 给 RAG 日志表补 `position_id` 字段，写入链路同步补齐。
2. 覆盖查询按 `position_id` 过滤分母（已发布原子）和分子（面试命中）。
3. 在知识库工作台新增覆盖仪表盘（选中岗位后显示玫瑰图 + 分类概要）。
4. History 页覆盖区增加岗位选择器，支持切换查看不同岗位的覆盖状态。
5. 侧边栏文案从"知识库 / 题库"改为"岗位 / 题库维护"，与功能定位对齐。

## User Stories

1. 作为面试训练用户，我在 History 页面选择一个岗位后，能查看该岗位的知识领域覆盖玫瑰图和累计命中数，了解自己在该岗位的面试覆盖情况。
2. 作为题库维护者，我在知识库工作台选中私有岗位后，能在原子表格上方看到该岗位的知识覆盖仪表盘（玫瑰图 + 各分类原子数/命中数），据此判断题库哪些领域已有面试验证、哪些领域缺少实战覆盖。
3. 作为题库维护者，没有面试过的岗位仍能看到覆盖面板，各分类 covered 为 0，让我了解该岗位题库的结构完整性。
4. 作为用户，我在 History 页面的能力热力图能正确显示 A-E 评级，不会因为 LLM 输出了 snake_case 或中文键名而全部显示为 E。
5. 作为用户，侧边栏导航中看到"岗位 / 题库维护"而非"知识库 / 题库"，更直观地理解入口功能。

## Implementation Decisions

### 数据库

- Flyway V18 迁移：`rag_retrieval_log` 和 `rag_retrieval_request_log` 各增加 `position_id BIGINT NULL` 列，并创建索引。旧数据 `position_id` 为 NULL，覆盖查询通过 `position_id IS NOT NULL` 隐式过滤。

### RAG 日志写入链路

- `RagRetrievalLog` 和 `RagRetrievalRequestLog` 实体各增加 `positionId` 字段（`Long` 类型，可为空）。
- `InterviewRetrievalService.insertHitLogs` 和 `insertRequestLog` 方法签名增加 `Long positionId` 参数，写入时赋值。
- 调用方（`resolveSearchScope` 已返回包含 `positionId` 的 `SearchScope`，将其透传到日志写入方法）。

### 覆盖数据查询

- `MentorService.buildKnowledgeCoverage(Long userId)` 改为 `buildKnowledgeCoverage(Long userId, Long positionId)`。
- **分母**：`knowledge_atom` 中 `status = PUBLISHED AND position_id = ?` 按 `category` 分组计数。
- **分子**：`rag_retrieval_log` 中 `user_id = ? AND position_id = ? AND context_selected = true` 按 `retrieved_category` 分组去重计数 `retrieved_atom_id`。
- 当 `positionId` 为 null 时保持原有全局查询行为（向后兼容）。

### API

- 新增 `GET /api/knowledge-workspace/positions/{positionId}/coverage` — 工作台使用，返回指定岗位的覆盖数据。权限校验复用 `importScopeFor` 模式：私有岗位校验 owner，公共岗位仅管理员可查看覆盖。
- 旧 `GET /api/user/knowledge-coverage` 增加可选 `positionId` query 参数 — History 页使用。不传时保持全局行为。

### 前端：工作台覆盖仪表盘

- `KnowledgeWorkspace.vue` 选中岗位后，在原子表格上方渲染 coverage section。
- 复用 `KnowledgeCoverageChart` 组件展示玫瑰图。
- 覆盖数据不阻塞原子表格加载：coverage API 异步加载，加载中显示骨架占位。
- 分类分布详情（各分类原子总数）随玫瑰图一起展示，命中数为 0 时显示"尚未有面试命中"提示。

### 前端：History 页岗位选择器

- 覆盖 section 顶部新增 `<el-select>`，选项来自 `getKnowledgeWorkspaceAPI` 返回的岗位列表。
- 默认选中最近一次面试的岗位，无面试记录时选中第一个可用岗位。
- 切换岗位时重新请求覆盖数据，玫瑰图平滑更新。

### 侧边栏文案

- `AppShell.vue` 菜单 label：`知识库 / 题库` → `岗位 / 题库维护`
- `KnowledgeWorkspace.vue` 页面标题：同步修改
- `router/index.js` meta title：同步修改

### 热力图评级兜底（已修复）

- `normalizeAbility` 增加 `_cleanKey` 归一化（小写 + 去下划线/连字符/空格）做模糊匹配。
- 别名表增加中文别名。
- 评级值增加 `GRADE_PATTERN = /^[A-E]$/` 校验，非字母值丢弃。
- `buildHeatmapData` 增加双重校验，截断非预期长文本。

## Testing Decisions

- 后端测试重点覆盖 `MentorService.buildKnowledgeCoverage` 按 `positionId` 过滤的正确性（Mock `atomMapper` 和 `ragLogMapper` 返回指定 position 的数据）。
- 前端测试覆盖 `normalizeAbility` 的 snake_case / 中文键名 / 大小写混用 / 空值等边界场景。
- 集成测试：不在此 PRD 范围，依赖现有 Docker 环境。

## Out of Scope

- 不改变 Qdrant 向量检索逻辑（Qdrant payload 已有 position_id 字段）。
- 不改变面试流程中的 RAG 检索决策链路。
- 不新增数据库外键约束。
- 不在知识覆盖区域展示原子粒度的详细信息（仅展示分类级别聚合）。
- `RagRetrievalLog` 历史数据的 `position_id` 不回填（保持 NULL，覆盖查询通过 `IS NOT NULL` 过滤）。

## Further Notes

- 当 `position_id` 为 NULL 的历史 RAG 日志不回填时，该岗位的覆盖分子可能比实际偏低（历史面试的命中不计入）。这是可接受的权衡——新面试从本次部署开始正常写入 position_id，覆盖数据逐渐准确。
- 工作台覆盖面板和 History 覆盖区域共用同一 API 端点，只是权限校验路径不同（工作台走 workspace scope 校验，History 走用户 ownership 校验）。
- KnowledgeCoverageChart 组件已在之前移除分类列表细节，仅保留玫瑰图 + 累计命中数字，本次无需再改。
