# IQB-01 Security Review Plan

Agent 名称：IQB-01 Security Review
计划状态：Pending worker delivery
基线 commit：7d8268e
角色：Security Review

## 目标

审查 IQB-01 integration diff 是否正确处理权限角色、数据保留边界和敏感信息风险。

## 关注范围

- 新增 `user.role` / admin bootstrap 相关逻辑。
- public/private scope、owner_user_id、position_id、knowledge_base_id 字段是否为后续权限隔离提供强约束。
- migration 是否只清理批准范围内的旧面试、报告和 RAG 日志数据。
- 是否意外删除或暴露用户账号、用户 LLM Provider、简历、反馈或密钥字段。

## 阻断条件

- migration 清理范围超过 IQB-01 批准边界。
- bootstrap admin 逻辑硬编码到不可配置业务判断。
- 新增表/字段无法表达 private owner 隔离。
- 日志、测试或文档暴露密钥、token、密码或敏感请求头。

## 输出

按 `docs/agents/templates/review.md` 输出 Security Review 结论。
