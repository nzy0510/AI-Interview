# security_reviewplan.md

Agent 名称：Security Review Agent

计划状态：Approved

基线 commit：48e29f7

角色：安全审查 Agent

目标：

- 审查用户 API Key 加密、权限、脱敏、日志、管理员边界和配置测试接口。

非目标：

- 不做无关安全重构。
- 不提交真实密钥或测试密钥。

## 允许修改

- 默认只读。
- 如主控批准，可修改审查报告。

## 禁止修改

- 业务代码，除非进入 review-fix 阶段。
- `.env`
- 私有密钥。

## 输入材料

- Integration diff。
- Backend/Frontend 交付物。
- 总执行包。

## 上游依据

- 批准总执行包：`docs/agents/plans/user-llm-provider/controllerplan.md`
- 本 Agent ownership：安全审查报告

## 依赖前置任务

- Integration Agent 完成初步合并。

## 验收标准

- 明文 API Key 不进入数据库、响应、日志、事件、前端存储。
- 当前用户只能访问自己的配置。
- 管理员不能查看用户 API Key 明文或密文。
- 错误信息脱敏。
- 加密密钥缺失不导致明文降级。
- 测试连接不泄露输入 key。

## 必须运行的验证

```powershell
git diff --check
rg -n "sk-[A-Za-z0-9_-]{12,}|Bearer\\s+[A-Za-z0-9._-]+|apiKey\\s*[:=]\\s*['\\\"]" backend frontend docs
```

## 预期交付物

- Security Review report。

## 风险提示

- 任何明文密钥进入 Git 或响应均为阻断。
- 任何用户越权读取/修改他人配置均为阻断。

## 完成后返回格式

```text
状态: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
完成内容:
修改文件:
测试命令:
测试结果:
未覆盖风险:
是否触碰共享文件:
commit hash:
后续建议:
```
