# HR 软技能专项阶段

## 背景

旧版面试会把部分软技能知识原子放在 `common` 分类中，技术面试阶段可能召回软技能内容。现在将软技能考察收敛为独立的 HR 软技能专项阶段。

## 本次调整

- 面试阶段仍由 `InterviewPhase` 状态机驱动：`OPENING -> TECHNICAL -> HR -> CLOSING -> FINISHED`。
- `[AUTO_FINISH]` 只允许在收尾阶段结束面试，避免模型误输出标记导致跳过 HR。
- 技术阶段岗位分类移除旧 `common` 软技能来源。
- HR 阶段题库检索使用独立 `HR软技能` 分类。
- 技术面试官提示词强调项目深挖、场景化提问和岗位知识面覆盖。
- HR 提示词强调沟通表达、团队协作、价值观、稳定性、职业规划、压力应对和冲突处理。
- 报告评分维度暂不改 JSON 结构，仅在提示词中重新解释现有六维含义，保证历史报告兼容。

## 题库导入

`question_bank_imports/hr-soft-skill-draft.json` 是 HR 软技能题库的 DRAFT 导入包。发布前应先通过管理 MCP 的 `validate_atom_import_package` 校验，再人工审核内容质量。确认后再提交、发布并重建 Qdrant 索引。

