# IQB-01 Backend/Data Agent Plan

Agent 名称：IQB-01 Backend/Data Worker
计划状态：Approved for development
基线 commit：7d8268e
角色：Backend/Data

## 目标

建立岗位、知识库、源文件、统一任务、结构化面试轮次和报告的第一版持久化模型，并把现有 `knowledge_atom` 扩展为 public/private 作用域模型。

## 非目标

- 不实现前端页面。
- 不实现文件上传、文档转换、LLM 原子生成、人工审核发布、复制公共岗位、严格检索切换或异步报告业务流。
- 不移除 `APP_ADMIN_TOKEN` 产品路径；这是 IQB-02。

## 允许修改

- `backend/src/main/java/com/interview/entity/**`
- `backend/src/main/java/com/interview/mapper/**`
- `backend/src/main/java/com/interview/service/impl/UserServiceImpl.java`
- `backend/src/main/resources/db/migration/**`
- `backend/src/test/java/**`
- 仅当测试需要时，可新增测试 fixture。

## 禁止修改

- `frontend/**`
- `docker-compose*.yml`
- `.env*`
- `backend/src/main/resources/application*.yml`
- 与 IQB-01 无关的业务服务重构。
- 管理员授予/撤销接口和 admin 导航；这是 IQB-02。

## 输入材料

- `docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`
- `docs/superpowers/plans/2026-06-13-user-owned-question-bank-rag-report-issues.md`
- `docs/agents/templates/development-worker.md`

## 验收标准

- `user` 表支持 `USER` / `ADMIN` 角色字段和 bootstrap admin 审计字段。
- 注册用户匹配可配置 bootstrap admin username/email 时获得 `ADMIN` 角色；默认值为 `nzy333` / `1525764737@qq.com`。
- 存在 public/private `interview_position` 和 `knowledge_base` 表。
- 存在 `knowledge_source_file`、`app_job`、`interview_turn`、`interview_report`、`interview_report_item` 表。
- `knowledge_atom` 支持 scope、owner、position、knowledge base、source file、review/publication/vector/version 元数据。
- 旧公共题库数据可迁移到 `Java 后端开发`、`Web 前端开发`、`AI 大模型应用开发` 三个 public position 的默认 public knowledge base。
- 迁移清理旧 `interview_record`、`rag_retrieval_log`、`rag_retrieval_request_log` 数据，但保留用户、LLM 配置、简历和反馈相关表。
- 新增实体/mapper 与新增表保持一致。
- 测试覆盖 migration 文本关键契约和实体映射基本字段。

## 必须运行的验证

```powershell
cd backend
mvn "-Dtest=UserOwnedQuestionBankMigrationContractTest" test
mvn test
```

如果全量 `mvn test` 因环境或既有问题失败，必须返回具体失败证据。

## 风险提示

这是破坏性数据库 migration。不得把清理范围扩大到用户账号、用户 LLM 配置、简历资料或反馈。不得提交密钥、私有题库或本地数据产物。

## 完成后返回格式

```text
状态:
完成内容:
修改文件:
测试命令:
测试结果:
未覆盖风险:
是否触碰共享文件:
commit hash:
后续建议:
```
