# PRD: 按岗位隔离历史成长曲线与能力统计

## Problem Statement

历史报告页当前将用户所有岗位的历史面试记录聚合到同一个成长曲线、顶部统计、最近表现和报告列表中。用户在 Java 后端、Web 前端、AI 大模型应用开发以及私有岗位之间切换练习时，不同岗位的能力曲线会混在一起，导致趋势不可解释。

用户需要以岗位为上下文查看成长变化，同时保留“全部岗位”作为对照视图。

## Solution

历史报告页新增岗位上下文。默认选择最近一次面试所属岗位；岗位选择器只展示有历史记录的当前可见岗位，并额外提供“全部岗位”。选中具体岗位后，页面主体统一按该岗位过滤，包括顶部指标、能力成长曲线、最近表现与能力画像、知识覆盖和历史报告列表。

后端历史列表接口支持可选 `positionId` 参数。新增轻量用户可见岗位列表接口，第一阶段只要求返回当前用户可见岗位的基础信息和准确的历史记录数。历史页使用 `historyCount` 构建岗位选择器；简历画像状态和简历更新时间由后续简历画像阶段补齐。

## User Stories

1. As a logged-in user, I want the history page to default to my latest interview position, so that I start from the most relevant role context.
2. As a logged-in user, I want to switch between positions with interview history, so that I can review each role independently.
3. As a logged-in user, I want an “全部岗位” option, so that I can still inspect global performance.
4. As a logged-in user, I want each position option to show interview count, so that I understand how much data supports the chart.
5. As a logged-in user, I want top metrics to follow the selected position, so that averages and totals match the role context.
6. As a logged-in user, I want the score growth curve to use only selected-position records, so that the trend is meaningful.
7. As a logged-in user, I want the ability heatmap mode to use only selected-position records, so that role-specific ability movement is visible.
8. As a logged-in user, I want “较上一场” to compare against the previous record in the same position, so that the delta is meaningful.
9. As a logged-in user, I want “全部岗位” to compare globally, so that the global view remains an aggregate comparison.
10. As a logged-in user, I want the latest ability profile to come from the selected position’s newest record, so that the profile matches the filter.
11. As a logged-in user, I want the “全部岗位” latest ability profile to display the source position name, so that I understand where it came from.
12. As a logged-in user, I want the knowledge coverage panel to follow the selected position, so that coverage gaps are role-specific.
13. As a logged-in user, I want the “全部岗位” knowledge coverage panel to show global coverage, so that it remains useful as an aggregate view.
14. As a logged-in user, I want the report list to follow the selected position, so that the visible records match the chart.
15. As a logged-in user, I want details to open from the filtered list only, so that I do not accidentally inspect unrelated role reports.
16. As a logged-in user, I want old records without `positionId` to stay visible in “全部岗位”, so that legacy data is not lost.
17. As a logged-in user, I do not want old records without `positionId` in concrete role views, so that role-specific charts remain clean.
18. As a logged-in user, I want deleted or invisible private positions to be excluded from the selector, so that only current actionable positions appear.
19. As a developer, I want specific position filtering to use structured `positionId`, so that same-name positions do not collide.
20. As a developer, I want the backend to validate position visibility, so that users cannot filter by another user’s private position.
21. As a developer, I want the visible-position summary API to return `historyCount`, so that the frontend does not duplicate aggregation logic.
22. As a developer, I want the visible-position summary API shape to allow later resume status fields, so that the companion resume PRD can extend it without changing the endpoint purpose.

## Implementation Decisions

- Add optional `positionId` to history list retrieval.
- Omitting `positionId` means “全部岗位”.
- Passing `positionId` means strict structured-position filtering after permission validation.
- No `position` text fallback is allowed.
- Records without `positionId` appear only in global history.
- Deleted or invisible private-position records appear only in global history.
- Add a lightweight visible-position summary API for normal user pages.
- The visible-position API is separate from the knowledge workspace API.
- In this history-growth phase, the visible-position API must return accurate `historyCount`; `hasResumeProfile` and `resumeUpdatedAt` are not required to be accurate until the resume-profile phase.
- The selector contains “全部岗位” plus visible positions with `historyCount > 0`.
- Default selected position is the latest scored interview’s visible `positionId`; otherwise “全部岗位”.
- The selected position context drives metrics, charts, latest profile, knowledge coverage, report list, and details entry.
- Knowledge coverage uses omitted `positionId` for global and concrete `positionId` for role-specific coverage.
- Update `CONTEXT.md` to describe position context across history growth views.
- Update `CHANGELOG.md` for user-visible behavior.

## Testing Decisions

- Backend tests should cover global history, filtered history, invisible private-position rejection, and legacy records without `positionId`.
- Visible-position API tests should cover public positions, owned private positions, hidden foreign private positions, and accurate `historyCount`.
- Frontend tests should cover default selection, selector options, “全部岗位”, specific filtering, zero-data states, and request parameters for history and coverage.
- Browser verification should open the history page, switch between a concrete position and “全部岗位”, and confirm metrics/chart/list/coverage move together.
- Closeout should include backend tests, frontend build, whitespace check, and Docker health verification.

## Out of Scope

- Resume profile upload/query/delete behavior.
- Start-interview resume profile loading.
- AI Mentor by-position analysis.
- Dedicated backend growth-statistics API.
- Text matching or migration for old history records.
- Showing deleted private positions as archived selectable groups.

## Further Notes

This PRD should usually be implemented before the resume-profile PRD because the visible-position summary API is shared. To keep the phase strict, this PRD only requires accurate history counts from that API; resume profile state is completed by the companion PRD. The implementation must treat backend permission checks as part of the feature, not as frontend-only filtering.
