# security_reviewplan.md

Agent 名称：Security Review Agent

计划状态：Approved

基线 commit：442ddf29d86a20c32a4c67626dc18b930c0b006a

角色：多 Agent 工作流试运行中的安全审查 Agent，只审查 integration diff 是否引入密钥、私有配置、敏感路径或误导性安全风险。

目标：确认 README-only 试运行说明不包含密钥、token、密码、私有配置、私有题库、临时产物路径或敏感请求头，并确认变更未触碰禁止路径。

非目标：不做全仓安全扫描；不修改文件；不修复文档；不合并分支；不 push；不清理 worktree。

## 允许修改

- 无。Security Review Agent 只读审查。

## 禁止修改

- `README.md`
- `backend/**`
- `frontend/**`
- `embedding-service/**`
- `scripts/**`
- `tests/**`
- `docs/**`
- `image/**`
- `CHANGELOG.md`
- `PLAN.md`
- `.env`
- `application-local.yml`
- `application.yml`
- `docker-compose*.yml`
- `question_bank_imports/**`
- `.codegraph/**`
- `.understand-anything/**`
- `.worktrees/**`
- 任何密钥、私有配置、私有题库、临时产物或本地视频产物

## 输入材料

- `AGENTS.md`
- `docs/agents/workflow.md`
- `docs/agents/templates/review.md`
- `docs/agents/plans/multi-agent-dry-run/docsplan.md`
- Integration Agent 集成报告
- Testing Review 报告
- 集成分支：`codex/multi-agent-dry-run-integration`

## 依赖前置任务

- Integration Agent 已完成并输出集成报告。
- Testing Review Agent 已完成或主控确认可并行审查同一 integration diff。
- 用户明确批准本 `security_reviewplan.md`。
- 主控 Agent 分配独立 Security Review Agent 线程和只读审查 worktree；在用户批准前不得创建对应线程或 worktree。

## 验收标准

- integration diff 只包含 `README.md`。
- diff 中不包含完整 API key、access token、refresh token、密码、私钥、敏感请求头或本地私有配置。
- diff 不新增 `.env`、`application-local.yml`、私有题库、临时导入包或本地工具产物引用作为可提交内容。
- 若发现敏感信息或禁止路径，输出阻断问题并拒绝进入下一阶段。

## 必须运行的验证

```powershell
git status --short --branch
git diff --name-status 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD
git diff 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD -- README.md
git diff 442ddf29d86a20c32a4c67626dc18b930c0b006a..HEAD | Select-String -Pattern 'API_KEY|SECRET|TOKEN|PASSWORD|PASSWD|PRIVATE KEY|BEGIN RSA|BEGIN OPENSSH|AUTHORIZATION|APP_ADMIN_TOKEN|JWT_SIGN_KEY|DEEPSEEK_API_KEY' -CaseSensitive:$false
```

预期结果：

- `git diff --name-status` 只显示 `M README.md`。
- 敏感词扫描无真实密钥、token、密码、私钥或敏感请求头。

## 预期交付物

- Security Review 报告，包含结论、阻断问题、非阻断问题、缺失测试、残余风险和是否批准进入 Maintainability Review。
- 不产生 commit。

## 风险提示

- 关键字扫描可能命中文档中对“密钥”等概念的泛化描述，Security Review Agent 必须区分概念性描述和真实敏感值。
- 本审查是 integration diff 安全审查，不替代完整仓库安全审计。

## 完成后返回格式

```text
状态: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
结论:
阻断问题:
非阻断问题:
缺失测试:
测试命令:
测试结果:
残余风险:
是否批准进入下一阶段:
commit hash:
后续建议:
```
