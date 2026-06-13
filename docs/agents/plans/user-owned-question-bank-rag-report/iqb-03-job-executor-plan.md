# IQB-03 Job Executor Agent Plan

Agent 名称：IQB-03 Job Executor Worker
计划状态：Completed in main worktree after controller-only continuation
基线 commit：7d8268e plus completed IQB-01/IQB-02 working tree changes
角色：Backend

## 目标

建立基于 `app_job` 表的统一异步任务生命周期基础设施：任务可创建、声明执行、完成、失败、手动重试、启动恢复，并可通过轮询 API 按当前用户可见性查询。执行器先提供本地 `TaskExecutor` 骨架和 handler registry，不提前实现后续 import、LLM、发布、报告等具体业务 handler。

## 非目标

- 不实现 IQB-04 的文件上传、存储或 MarkItDown 转换。
- 不实现 IQB-06 的 LLM 原子生成或二审。
- 不实现 IQB-07 的发布、版本和 Qdrant upsert。
- 不实现 IQB-12 的报告生成业务。
- 不修改 `app_job` 表结构或新增 migration，除非发现 V14 阻断缺陷并先回报主控。
- 不引入外部队列、Redis queue、Quartz、xxl-job 或新依赖。

## 允许修改

- `backend/src/main/java/com/interview/entity/AppJob.java`
- `backend/src/main/java/com/interview/mapper/AppJobMapper.java`
- `backend/src/main/java/com/interview/service/**Job**.java`
- `backend/src/main/java/com/interview/controller/**Job**.java`
- `backend/src/main/java/com/interview/dto/**Job**.java`
- `backend/src/main/java/com/interview/config/AsyncTaskConfig.java`
- `backend/src/test/java/com/interview/service/**Job**Test.java`
- `backend/src/test/java/com/interview/controller/**Job**Test.java`

如确需读取 `AdminRoleService`、`RequestUserResolver`、`GlobalExceptionHandler`，只能为 job polling 权限集成做小范围调用，不要重构 IQB-02 授权实现。

## 禁止修改

- `backend/src/main/resources/db/migration/**`
- `frontend/**`
- `docker-compose*.yml`、`.env*`、`application*.yml`
- 题库导入/发布、LLM、Qdrant、报告生成业务逻辑的大范围重构
- IQB-01/IQB-02 已完成文件的无关格式化或重写

## 输入材料

- `docs/superpowers/plans/2026-06-13-user-owned-question-bank-rag-report-issues.md` 的 IQB-03。
- `docs/agents/templates/development-worker.md`
- 当前 `AppJob` entity、`AppJobMapper`、V14 `app_job` schema。
- IQB-02 的 `AdminRoleService` 和 `RequestUserResolver`，用于 polling 可见性。

## TDD 行为切片

按垂直切片推进，不要一次性先写所有测试：

1. Job lifecycle：`PENDING -> RUNNING -> COMPLETED/FAILED`，claim 写入 `claimedBy` 与 `lockedUntil`。
2. Retry：只有 `FAILED` 且 `retryable=true` 的任务可由可见用户手动重试，重试后回到 `PENDING` 并递增 `retryCount`。
3. Recovery：启动恢复把过期 `RUNNING` 任务重新置为 `PENDING`，不自动重试 `FAILED`。
4. Visibility：普通用户只能读取自己的 private jobs 和公开可见 jobs；admin 可读取所有 jobs。
5. Executor skeleton：提供本地 `appJobTaskExecutor`、handler registry、dispatcher/runner 骨架；没有 handler 的 job 不应被错误标记成功。

## 验收标准

- Jobs 支持 `PENDING`、`RUNNING`、`FAILED`、`COMPLETED` 状态常量或等价枚举。
- Jobs 使用 V14 已有字段追踪 type、scope、owner、position、knowledge base、source file / interview record、stage、progress、sanitized error、retryable、retry count、lock owner、lock expiry。
- Backend startup 有恢复入口，能处理 pending jobs 和 expired running jobs；`FAILED` 不自动重试。
- Job polling API 只返回当前用户可见 job，admin 可见全部。
- 测试覆盖 claim、complete、fail、retry、restart recovery、visibility。
- 不暴露 API key、token、Authorization header 等敏感内容到 `errorMessage`。

## 必须运行的验证

```powershell
cd backend
mvn "-Dtest=AppJobServiceTest,AppJobControllerTest,AppJobRecoveryTest" test
mvn test
```

如果类名最终不同，返回实际运行的等价 job 相关测试命令和结果。若全量 `mvn test` 失败，必须返回完整失败摘要，不要自行跳过。

## 风险提示

- 这是后续导入、LLM 生成、发布和报告的共享基础设施；接口要小而稳定。
- 不要为了立即执行未来业务而把 handler 与具体题库/报告逻辑耦合。
- 并发 claim 在单元测试中可用条件 update 或 mapper mock 覆盖语义；若未做真实 DB 并发测试，要在未覆盖风险中说明。

## 完成摘要

- 主工作树已实现 `app_job` 生命周期服务、轮询 API、本地 `TaskExecutor` bean、handler registry/dispatcher 骨架和启动恢复入口。
- 具体导入、LLM 生成、发布、报告等业务 handler 未在 IQB-03 中实现；无 handler 的 job 会失败为可重试状态，不会被误标记成功。
- Codegraph MCP 状态检查失败，错误为 `Transport closed`；本阶段按要求记录失败后改用源码读取与测试验证收口。
- 用户要求不再继续派发子线程后，后续收口均在主工作树完成。

## 完成后返回格式

```text
状态:
完成内容:
修改文件:
TDD 记录:
测试命令:
测试结果:
未覆盖风险:
是否触碰共享文件:
commit hash:
后续建议:
```
