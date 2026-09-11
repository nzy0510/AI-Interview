# ADR 0004：采用稳定规则面试主线

- 状态：已接受
- 日期：2026-09-11
- 取代：[ADR 0001：采用有边界的单轮面试 Agent](0001-bounded-interview-agent.md)

## 背景

比赛版在阶段状态机和动态 RAG 之外增加了 Tool Calling 规划、工具契约、超时回退、会话状态与前端决策展示。用户决定保留稳定规则版面试，移除比赛 Agent。现有规则链路已能按阶段检索岗位题库，并根据真实回答与召回分数生成补救、换题或自然推进的提示，因此可以保留训练与复盘闭环。

本次目标是收拢运行路径和维护边界。追问质量、实际 Token 用量与延迟变化需要另行评测，不据此宣称效果提升或量化节省。

## 决策

1. `InterviewOrchestrator` 只保留 `RuleBasedInterviewOrchestrator` 实现，由现有 `InterviewTurnPlanner` 计算阶段；只有计算出的阶段为 `TECHNICAL` 或 `HR` 时才检索题库。
2. 检索 query 只使用上一轮 AI 问题与候选人真实最新回答。低信息判断和动态候选预算基于真实回答，作用域继续严格绑定 `userId + positionId`、知识库及公共 / 私有 owner 条件。
3. `InterviewRetrievalService` 直接返回 `CONTINUE_PHASE`、`REMEDIATE` 或 `SWITCH_TOPIC`。动作与 Prompt 指令来自同一规则分支，不再解析中文 Prompt 推断动作；阶段推进仍由状态机负责。
4. `InterviewTurnPlan` 仅保留 `phase`、`action`、`systemPrompt`、`evidenceContext`、`evidenceAtomIds`、`consumedAtomIds`。正常回答且召回可用时注入证据，并列入成功生成后的消费集合；单次低信息且召回高置信时注入补救证据，消费集合为空；连续低信息、弱召回和零分 MySQL 降级结果不注入 Atom。
5. 阶段、对话历史与本场 `usedAtomIds` 只在模型流式 `onComplete` 后提交。保留 `interview_turn` 的问答、证据快照，以及岗位级覆盖、详细报告和 AI Mentor；补救证据不用于本场排重，但成功落库后仍可计入知识暴露覆盖。
6. 移除 Tool Calling 规划器、回退编排器、专用异常、规划 Prompt、Agent 配置与线程池、超时会话状态、`APP_INTERVIEW_AGENT_*` 配置、前端决策状态条和 SSE `orchestration` 事件。
7. 保留 V20 历史迁移与数据库旧列、旧值。新轮次的 `orchestration_mode` 固定写入 `RULE`，`decision_action` 保存规则动作，`decisionJson` 只保存 `evidenceAtomIds` 与 `consumedAtomIds`；历史记录继续可读，不做数据清理或改写。
8. 保留用户 Provider、岗位简历定制、题库入库与终审发布流程。README、领域文档与 RAG 说明描述当前规则主线；ADR 0001 和比赛 Demo 标注为历史资料。

## 结果与取舍

- 面试运行时只有一条规则编排路径，不再需要 Tool Calling 兼容性、规划超时与自动回退状态。
- RAG 的岗位隔离、证据门槛、会话排重与成功轮次复盘边界继续保留。
- 移除模型自主选择工具和阶段的能力；下一问文本仍由用户启用的模型生成，规则与 Prompt 不能保证追问质量。
- 历史结构化轮次继续兼容，因此数据库仍保留旧编排字段和历史值。

## 验证边界

代码验收应覆盖阶段推进、真实回答 query、结构化动作、证据与消费分离、生成失败不提交状态，以及历史报告读取。前端需验收移除状态条后的文字 / 视频面试与 SSE 流式流程；完成结果以本次交付记录为准，本 ADR 不充当测试通过证明。
