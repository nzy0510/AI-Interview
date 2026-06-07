# InterWise v1.2.0 - 动态 RAG 与多语言向量检索稳定版

## 对比范围

本版本基于 `v1.1.0..v1.2.0` 发布，重点从“题库可维护”推进到“题库可评测、可观测、可稳定支撑面试追问”。

## 重要更新

### 1. 动态面试 RAG

本版本将 RAG 从传统知识库问答链路进一步收敛为面试过程中的决策信号。系统会结合候选人当前回答、上一轮问题、岗位、面试阶段、已使用知识点和题库召回结果，决定下一轮是继续深挖、补救追问还是切换知识点。

默认策略从固定 Top-K 调整为更贴近面试场景的动态候选集：

- 默认召回 Top-20 候选 Atom。
- 短技术回答或多技术点混杂回答可扩展到 Top-30。
- 最终只向提示词注入 Top-10 上下文。
- 低信息回答、弱召回和连续回避会影响是否消耗 Atom。

### 2. multilingual-e5-base 向量检索

Docker 部署默认切换到独立 `embedding-service`，使用 `intfloat/multilingual-e5-base` 生成 768 维中文/多语言向量。生产 Qdrant collection 默认变为 `interview_atoms_e5_base`，旧 384 维 collection 不会被混用。

后端增加 collection 维度校验、HTTP embedding 超时配置和 Qdrant 降级保护，避免向量维度不一致时继续产生错误召回。

### 3. 可观测的 RAG 检索链路

新增请求级 RAG 检索日志：

- 记录零命中、跳过、失败、检索策略、候选数量与耗时。
- 候选 Atom 日志通过 `request_id` 关联到请求级日志。
- 标记哪些 Atom 只是候选，哪些真正进入了面试提示词上下文。

这使后续人工评测、召回问题排查和 rerank 实验不再依赖猜测。

### 4. 离线评测与 rerank 验证

新增 `scripts/retrieval_eval` 工具链，并提交固定 AI 大模型岗位评测集：

- 导出脱敏 query。
- 构建候选池。
- 生成预标注。
- 计算 Recall、MRR、NDCG 等指标。
- 比较 embedding 基线与二阶段 rerank 效果。

当前结论是：rerank 已具备可验证路径，但生产链路暂不默认启用，避免在收益未稳定前增加延迟和复杂度。

### 5. 题库和追问生命周期加固

本版本修复和加强了多处题库/RAG 生产边界：

- 流式生成失败不再消耗 Atom。
- 同一面试记录不能并发启动多轮追问。
- Qdrant 故障不再被误记为普通零命中。
- 发布、归档和 reindex 失败保留可重试状态。
- MySQL 回退检索减少无效长对话干扰。

### 6. README 与项目展示更新

README 已按稳定发布重新编写，突出：

- 项目定位和核心创新。
- 动态面试 RAG 与传统 RAG 的差异。
- 模拟面试与数据库题库的打通方式。
- 题库运维、RAG 评测、云端部署和验证命令。
- 项目页面展示图与架构图。

## 当前主要功能

- 文字面试：SSE 流式生成、阶段推进、技术追问、HR 软技能阶段。
- 视频面试：摄像头、语音交互和情绪分析辅助。
- 简历画像：PDF 简历解析与结构化画像。
- AI Mentor：基于历史面试和知识覆盖生成训练建议。
- 数据库题库：MySQL 保存业务状态，Qdrant 保存可重建向量索引。
- Question Bank Admin：导入包校验、试运行、发布、归档、恢复、搜索和 reindex。
- RAG 离线评测：固定评测集、候选池、预标注、指标计算和 rerank 对比。
- 访问统计与成本保护：访问事件、反馈、限流和每日额度。
- Docker / Azure VM 部署：前端、后端、MySQL、Redis、Qdrant、embedding-service、Caddy。

## 部署与升级注意事项

从 `v1.1.0` 升级到本版本时，重点关注：

- Docker 默认 embedding 模型为 `intfloat/multilingual-e5-base`。
- Qdrant collection 应使用 `interview_atoms_e5_base`。
- `QDRANT_VECTOR_SIZE` 应为 `768`。
- 切换 embedding 或 collection 后，需要在 Question Bank Admin 执行全量 reindex。
- 验收标准是 reindex 失败数为 0，Qdrant points 数量与已发布 Atom 数量一致。
- `.env`、数据库、Redis、Qdrant 数据目录、上传文件和模型缓存不要提交到 Git。

## 验证结果

已在发布前完成：

- `cd backend && mvn test`：通过，116 个测试通过。
- `cd frontend && npm run build`：通过，存在 Vite 大 chunk 体积警告，不影响构建产物生成。
- `cd frontend && npx vitest run`：通过，3 个测试文件、15 个测试通过。
- `python -m unittest discover -s tests`：通过，29 个测试通过。
- `docker build -t interview-embedding-service:release-check ./embedding-service`：通过，验证 embedding-service 镜像构建路径可用。

## 完整变更

详见 `CHANGELOG.md` 的 `v1.2.0 - 2026-06-07` 区块。
