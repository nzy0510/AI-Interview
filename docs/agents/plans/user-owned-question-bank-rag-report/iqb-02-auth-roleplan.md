# IQB-02 Auth/Role Agent Plan

Agent 名称：IQB-02 Auth/Role Worker
计划状态：Approved for AFK development
基线 commit：7d8268e plus completed IQB-01 working tree changes
角色：Backend/Frontend

## 目标

把管理员能力纳入正常 JWT 用户模型：后端管理接口使用 `user.role=ADMIN` 授权，不再依赖产品流 `APP_ADMIN_TOKEN`；管理员可以授予/撤销其他用户 admin 角色且保留至少一个 admin；前端根据 `/api/user/me` 的 `isAdmin` 隐藏普通用户不可见的管理入口。

## 非目标

- 不移除旧开发者服务本身，`DeveloperAccessService` 仍可服务开发者白名单/额度等历史用途。
- 不实现完整知识库管理页面、文件上传、岗位复制、导入 pipeline 或 Qdrant 严格检索。
- 不删除 `QuestionBankAdmin.vue`，只把产品流授权从 token 改成 JWT admin role。
- 不实现 IQB-14 的旧流程最终收口文档。

## 允许修改

- `backend/src/main/java/com/interview/service/AdminGuardService.java`
- `backend/src/main/java/com/interview/service/**Admin**.java`
- `backend/src/main/java/com/interview/controller/**Admin**.java`
- `backend/src/main/java/com/interview/controller/UserController.java`
- `backend/src/main/java/com/interview/dto/**`
- `backend/src/test/java/**`
- `frontend/src/api/analytics.js`
- `frontend/src/api/questionBankAdmin.js`
- `frontend/src/views/AdminAnalytics.vue`
- `frontend/src/views/Settings.vue`
- `frontend/src/components/settings/QuestionBankAdmin.vue`
- 必要时可小幅调整 `frontend/src/components/layout/AppShell.vue` 或 `frontend/src/router/index.js` 保护 admin 导航。

## 禁止修改

- `backend/src/main/resources/db/migration/**`，除非发现 IQB-01 阻断缺陷并先回报主控。
- `frontend/package.json` / lockfile；不引入新依赖。
- `docker-compose*.yml`、`.env*`、`application*.yml`。
- 题库导入/发布业务逻辑的大范围重构。

## 输入材料

- `docs/superpowers/plans/2026-06-13-user-owned-question-bank-rag-report-issues.md` 的 IQB-02。
- `docs/agents/templates/development-worker.md`
- 当前 IQB-01 已完成的实体/migration 工作区状态。

## 验收标准

- Admin authorization 使用 JWT 当前用户 + `user.role=ADMIN`，产品流不再要求 `APP_ADMIN_TOKEN`。
- Admin 可以授予/撤销其他用户 admin 角色，撤销时必须保留至少一个 admin。
- 普通用户无法访问 admin-only 后端接口。
- `/api/user/me` 返回 `role` 和 `isAdmin`，前端管理入口按 `isAdmin` 隐藏。
- 前端 admin analytics/question bank admin API 不再传 `X-Admin-Token`。
- 测试覆盖 admin guard、grant/revoke、至少一个 admin、`/me` role 字段、普通用户拒绝 admin 接口。

## 必须运行的验证

```powershell
cd backend
mvn "-Dtest=AdminGuardServiceTest,AdminRoleServiceTest,AdminRoleControllerTest,UserControllerTest,QuestionBankAdminControllerTest" test
mvn test
```

```powershell
cd frontend
npx vitest run
npm run build
```

如果前端依赖不可用或既有测试环境失败，返回具体失败证据。

## 风险提示

这是认证授权改动。不得降低 JWT 校验，不得把 admin role 判断交给前端，不得保留产品流必须输入 admin token 的路径。旧开发者/token 相关 UI 或文档如未完全删除，必须标记为后续 IQB-14 收口风险。

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
