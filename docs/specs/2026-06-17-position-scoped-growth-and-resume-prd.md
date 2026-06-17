# PRD: 岗位上下文贯穿成长曲线与简历画像

## Problem Statement

InterWise 已经在知识库 / 题库工作台中建立了公共岗位与私有岗位的隔离，但历史成长曲线、能力统计、简历画像和面试启动链路仍存在用户级全局聚合或全局单例简历的问题。

这会导致两个核心偏差：

- 用户在多个岗位之间练习时，成长曲线、平均分、最近表现和能力画像会混合不同岗位的数据，趋势含义不稳定。
- 用户针对不同岗位上传不同简历时，当前系统只能保存一份最新简历画像，容易在 Java 后端、Web 前端、AI 大模型应用开发或私有岗位之间串用简历定制问题。

用户希望把“岗位上下文”从知识库继续扩展到历史成长曲线和简历画像：每个岗位有自己的历史成长视图，每个岗位有自己的简历画像，开始面试时只使用当前岗位的画像。

## Solution

本次目标分成两个实现边界，但保持同一个产品方向：

1. 历史成长曲线按岗位隔离。
2. 简历画像按岗位隔离，并接入面试准备和开始面试链路。

系统新增轻量“用户可见岗位列表”能力，作为两个边界共用的岗位上下文基础。第一阶段先保证当前用户可见岗位与历史记录数准确可用；简历画像状态与简历更新时间在第二阶段简历画像隔离中补齐为真实数据。

历史页默认进入最近一次面试所属岗位，允许切换有历史记录的岗位，也保留“全部岗位”作为全局对照。选中具体岗位后，顶部统计、成长曲线、最近表现、知识覆盖、历史报告列表统一按该岗位过滤。

简历画像页重构为岗位简历画像管理页。用户必须先选择岗位再上传简历。每个用户在每个岗位下最多有一份当前简历画像，重复上传覆盖该岗位画像。开始面试时，后端按 `userId + positionId` 自动加载当前岗位简历画像；如果当前岗位没有画像，不复用其他岗位画像。

## User Stories

1. As a logged-in user, I want the history page to default to my latest interview position, so that I can immediately review growth in the most relevant role context.
2. As a logged-in user, I want to switch history views between positions that have interview records, so that I can evaluate role-specific progress.
3. As a logged-in user, I want an “全部岗位” view, so that I can still see my overall interview activity.
4. As a logged-in user, I want top history metrics to follow the selected position, so that average score and score deltas do not mix unrelated roles.
5. As a logged-in user, I want the growth chart to use only the selected position’s records, so that the trend is meaningful.
6. As a logged-in user, I want the ability heatmap to use only the selected position’s records, so that role-specific ability changes are visible.
7. As a logged-in user, I want the latest ability profile to come from the selected position, so that the profile matches the current context.
8. As a logged-in user, I want the “全部岗位” latest profile to display the role name of the latest record, so that I know which role it came from.
9. As a logged-in user, I want knowledge coverage to follow the selected position, so that coverage gaps match the current role.
10. As a logged-in user, I want global knowledge coverage in the “全部岗位” view, so that I can use it as an aggregate comparison.
11. As a logged-in user, I want the history report list to follow the selected position, so that every visible report belongs to the current context.
12. As a logged-in user, I want records without structured `positionId` to remain visible in “全部岗位”, so that legacy data is not lost.
13. As a logged-in user, I do not want records without structured `positionId` to appear in specific position views, so that role-specific charts remain trustworthy.
14. As a logged-in user, I want deleted private-position history to remain visible only in “全部岗位”, so that deleted contexts do not pollute the current selector.
15. As a logged-in user, I want a dedicated resume profile management page, so that I can manage different resumes for different positions.
16. As a logged-in user, I want to upload a resume only after choosing a position, so that the resume is stored under the correct role context.
17. As a logged-in user, I want public positions and my private positions to both accept my own resume profile, so that I can prepare for any visible position.
18. As a logged-in user, I want one current resume profile per position, so that uploading a new PDF updates that position’s current profile.
19. As a logged-in user, I want uploading a resume for one position to never overwrite another position’s resume profile, so that my role-specific preparation stays isolated.
20. As a logged-in user, I want to delete the current position’s resume profile, so that I can remove private resume data for that role.
21. As a logged-in user, I want deleting a private position to delete that position’s resume profile, so that unreachable role assets are cleaned up.
22. As a logged-in user, I want historical interview reports to remain after deleting a resume profile or private position, so that past reports are preserved.
23. As a logged-in user, I want the resume page to show which positions already have profiles, so that I can see preparation coverage at a glance.
24. As a logged-in user, I want the resume page position list to show public/private labels and interview counts, so that I understand each role’s status.
25. As a logged-in user, I want “基于画像开启面试” to go to the setup page for the selected position, so that I can still choose mode, difficulty, and focus areas.
26. As a logged-in user, I want the setup page to show whether the selected position has a resume profile, so that I know whether resume-tailored questions will be used.
27. As a logged-in user, I want to start an interview even when the current position has no resume profile, so that resume upload remains optional.
28. As a logged-in user, I do not want the app to reuse another position’s resume profile, so that position-specific interviews stay clean.
29. As a logged-in user, I want old global resume data to be preserved but not auto-bound to a position, so that migration is non-destructive.
30. As a developer, I want `positionId` to be the only isolation key for specific position views, so that text name collisions do not cause data mixing.
31. As a developer, I want resume APIs to reject missing `positionId`, so that the old global-singleton behavior cannot silently return.
32. As a developer, I want start interview to load resume questions from the backend by `userId + positionId`, so that the frontend cannot pass questions from the wrong position.
33. As a developer, I want a nullable `resumeProfileId` on interview records, so that we can know whether a position resume profile was used at start time.
34. As a maintainer, I want this behavior documented in `CONTEXT.md` and `CHANGELOG.md`, so that future features preserve the position boundary.

## Implementation Decisions

- Split implementation and commits into two feature boundaries:
  - `feat: add position-scoped history growth views`
  - `feat: add position-scoped resume profiles`
- Add a shared visible-position summary API for normal users. It returns visible public positions and owned private positions, not knowledge-workspace maintenance data.
- Visible-position summary is phased:
  - `id`
  - `name`
  - `scope`
  - `ownerUserId`
  - `historyCount`
  - `hasResumeProfile` and `resumeUpdatedAt` are optional or nullable during the history-growth phase, then become accurate fields in the resume-profile phase.
- “全部岗位” is represented by omitting `positionId`.
- Specific position filters use structured `positionId` only. No text fallback.
- Historical records without `positionId` stay in global views only.
- Deleted or invisible private positions do not appear as selectable concrete positions.
- Resume profiles are keyed by `userId + positionId`.
- Existing legacy resume rows are preserved but not automatically bound to positions.
- Resume APIs require `positionId`:
  - upload / parse profile for a position
  - query profile for a position
  - delete profile for a position
- Resume profile response returns a wrapper object with metadata and `analysis`, not the raw analysis JSON directly.
- Start interview loads the current position’s resume profile server-side and records `resumeProfileId` when present.
- Same-position upload updates the existing resume profile row instead of deleting and recreating.
- Dashboard resume management should be simplified and route users to `/resume`.
- `/resume` becomes the dedicated position resume profile management page.
- AI Mentor position isolation is not included in this PRD.

## Testing Decisions

- Backend tests should focus on external behavior: permission checks, position filtering, resume profile isolation, missing-position errors, and start-interview profile loading.
- Migration contract tests should verify schema support for `resume_profile.position_id`, uniqueness by user and position, and `interview_record.resume_profile_id`.
- Frontend tests should cover selection defaults, empty states, and request parameter behavior where existing test infrastructure supports it.
- Browser verification should cover history page position switching, resume page position switching, empty profile state, profile state display, and setup page profile status.
- Docker restart verification is required because this work includes Flyway migrations and startup-facing API changes.

## Out of Scope

- AI Mentor by-position analysis.
- Multiple resume versions per same position.
- Automatic text matching for legacy resume profiles or legacy interview records.
- Dedicated backend growth-statistics API.
- Global resume fallback when `positionId` is missing.
- Admin management of ordinary users’ resume profiles.
- Copying resume profiles when copying public positions into private positions.

## Further Notes

This PRD is the parent product direction. The two companion PRDs should be used for implementation planning and review. The shared visible-position summary API is a dependency across both slices.
