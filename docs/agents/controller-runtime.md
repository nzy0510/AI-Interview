# Controller Runtime

这是主控 Agent 的短版运行入口。目标是先做正确调度，再按需读取长规范，避免每次把完整工作流喂给主控和子 Agent。

完整规则仍以 `docs/agents/workflow.md` 为准；当本文件和长规范冲突时，优先执行更保守、更能保护用户改动和安全边界的规则。

---

## 1. 启动检查

每次开始先执行：

```powershell
git status --short --branch
```

然后确认：

- 当前任务是否明确要求多 Agent、子线程、worktree 隔离或按工作流开发。
- 工作区是否已有未提交改动；先区分本次相关和用户/其他 Agent 改动。
- 是否涉及高风险面：认证授权、数据库 migration、部署配置、密钥、生产数据、依赖源、计费、安全策略。

---

## 2. 调度决策

主控必须先输出调度判断，再继续执行。

```text
任务等级：L0 / L1 / L2 / L3 / High-risk
调度模式：单 Agent / 轻量多 Agent / 标准多 Agent / 完整多 Agent
是否启用多 Agent：
启用的 Agent：
不启用完整链路的理由：
人工审核节点：
自动继续范围：
```

默认判定：

| 等级 | 适用任务 | 默认调度 |
| --- | --- | --- |
| L0 | 小文档、小 bug、局部展示修复、轻量说明 | 当前线程或单 Agent，不建多 Agent worktree |
| L1 | 流程试运行、较大文档、低风险工具小改 | Controller + 1 个执行 Agent + 可选合并 Review |
| L2 | 同时涉及两个以上模块，但不触及高风险面 | Architect + 对应开发 Agent + Integration + Review |
| L3 | 跨前端、后端、数据库、部署、文档的完整功能 | 完整角色链路 |
| High-risk | 触及安全、密钥、部署、数据、migration 等 | 在对应等级上强制 Testing/Security/Integration/Release 审查 |

不要因为用户说“按工作流”就机械启动完整链路。任务很小时，应说明为什么不启用完整多 Agent。

---

## 3. 总执行包 Gate

L2/L3/High-risk，或用户明确要求多 Agent 试运行时，必须先生成总执行包并等待用户批准。

总执行包最少包含：

```text
目标：
非目标：
验收标准：
任务等级和调度模式：
Agent 拆分：
文件所有权矩阵：
分支/worktree 映射：
验证命令：
高风险操作：
自动继续范围：
必须回到用户审核的触发条件：
回滚方案：
```

用户批准前：

- 不创建子线程。
- 不创建 worktree。
- 不派发实现任务。
- 只允许调整总执行包、任务卡、ownership 和验证方案。

用户批准后：

- 低风险任务卡不再逐个停审。
- 创建 worktree 后必须回查实际路径、分支、base commit 和 `git status`。
- 子 Agent 不直接合并主分支。

---

## 4. 子 Agent 上下文裁剪

子 Agent 默认不接收完整 `docs/agents/workflow.md`、完整聊天历史或其他 Agent 的 plan。

默认只传：

- 对应角色模板。
- 自己的任务卡或 plan。
- 总执行包中与自己有关的摘录。
- 任务相关代码/文档入口。

子 Agent prompt 必须明确：

```text
任务卡是唯一执行依据。
全局材料只作为边界参考。
不得扩大 ownership 或任务范围。
```

确需传入完整 `workflow.md` 时，必须在总执行包或 run record 中说明原因。

---

## 5. 必须串行的阶段

以下阶段不得并行：

- API contract 最终定稿。
- 数据库 migration。
- `.env.example`、Docker Compose、SecurityConfig、WebMvcConfig 等全局配置。
- 合并子分支。
- 集成测试。
- Security Review 修复。
- CHANGELOG / release notes。
- merge / push / release。
- worktree 和本地任务分支清理。

---

## 6. Review / Test / Security 触发规则

默认规则：

- L0：主控自查和必要测试即可。
- L1：可用一个合并 Review 覆盖测试合理性、安全敏感信息和维护性。
- L2/L3：至少启用 Integration + Review；按影响范围加入 Testing/Security。
- High-risk：必须加入 Security Review 和 Testing Review。

Security Review 必须触发于：

- API Key、token、密码、JWT、密钥管理或日志脱敏。
- 认证授权、管理接口、用户隔离、越权风险。
- 上传下载、路径处理、外部依赖源、部署配置。

Testing Review 必须触发于：

- 核心业务流程变更。
- 前后端 contract 变更。
- 数据库 migration。
- 多 Agent 集成后的最终 diff。

---

## 7. 收口和清理

提交、推送、PR、release 或清理资源前，先确认：

```powershell
git status --short --branch
git diff --stat
git log --oneline -5
```

只清理满足全部条件的 worktree / 本地任务分支：

- 明确属于本次任务。
- 已合入目标分支或已批准 integration branch。
- worktree 干净。
- 不是历史保留分支、远端仍需维护分支或其他 Agent 正在使用的分支。

禁止删除远端分支，除非用户明确要求并确认远端状态。

---

## 8. 按需读取长规范

只有在需要细则时再读取：

| 需要 | 读取 |
| --- | --- |
| 角色完整职责、状态机、ownership 示例 | `docs/agents/workflow.md` |
| 主控提示词 | `docs/agents/templates/controller.md` |
| 开发 Agent 提示词 | `docs/agents/templates/development-worker.md` |
| Integration | `docs/agents/templates/integration.md` |
| Review | `docs/agents/templates/review.md` |
| Release / 清理 | `docs/agents/templates/release.md` |
| run record | `docs/agents/templates/run-record.md` |

## 9. 子agent探索代码库
- 开发类子线程进入 worktree 后必须确认 .codegraph/ 是否存在。
- 不存在则初始化索引。
- codegraph 不可用时不能无限卡住，必须改用源码读取并在最终报告说明。
