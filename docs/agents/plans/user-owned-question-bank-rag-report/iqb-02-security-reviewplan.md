# IQB-02 Security Review Plan

Agent 名称：IQB-02 Security Review
计划状态：Pending worker delivery
基线 commit：7d8268e plus completed IQB-01 working tree changes
角色：Security Review

## 目标

审查 IQB-02 是否真正由 JWT + `user.role=ADMIN` 保护管理面，并且普通用户不能访问 public mutation / admin-only 路径。

## 关注范围

- `AdminGuardService`
- admin role grant/revoke service/controller
- `/api/user/me` role 暴露
- frontend admin token 移除
- Question Bank Admin、Analytics 管理入口

## 阻断条件

- 仍要求 `APP_ADMIN_TOKEN` 才能进入产品管理流。
- 仅前端隐藏但后端没有 role 校验。
- 普通用户可授予自己 admin 或撤销最后一个 admin。
- 新增日志/测试暴露 token、密码或密钥。

## 输出

按 `docs/agents/templates/review.md` 输出 Security Review 结论。
