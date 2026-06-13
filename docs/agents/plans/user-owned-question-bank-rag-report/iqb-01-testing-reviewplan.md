# IQB-01 Testing Review Plan

Agent 名称：IQB-01 Testing Review
计划状态：Pending worker delivery
基线 commit：7d8268e
角色：Testing Review

## 目标

审查 IQB-01 integration diff 是否用测试覆盖了破坏性迁移契约、实体字段映射和 bootstrap admin 注册行为。

## 关注范围

- `backend/src/main/resources/db/migration/V14__*.sql`
- `backend/src/main/java/com/interview/entity/**`
- `backend/src/main/java/com/interview/mapper/**`
- `backend/src/main/java/com/interview/service/impl/UserServiceImpl.java`
- `backend/src/test/java/**`

## 阻断条件

- 没有测试约束旧数据清理范围。
- 没有测试约束保留用户、LLM 配置、简历和反馈数据。
- 没有测试约束三类 public position/bootstrap knowledge base。
- 没有测试覆盖 bootstrap admin 默认 username/email 授权。
- 声称测试通过但没有真实命令结果。

## 输出

按 `docs/agents/templates/review.md` 输出 Testing Review 结论。
