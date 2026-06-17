# PRD: 按岗位隔离简历画像与面试启动链路

## Problem Statement

当前简历画像是用户级单例：每个用户只有一份最新简历画像，上传新简历会覆盖旧画像。用户如果针对 Java 后端、Web 前端、AI 大模型应用开发或私有岗位准备不同简历，系统无法按岗位存储和调用这些画像。

这会造成面试启动时的简历定制题串岗：当前岗位没有画像时，前端可能仍从旧的全局缓存或全局接口拿到其他岗位的简历画像，从而影响提问策略。

用户需要每个岗位都能上传对应简历，多个岗位的简历画像互相隔离，并且开始面试时只使用当前岗位的简历画像。

## Solution

将 `/resume` 重构为岗位简历画像管理页。用户先选择岗位，再上传、查看、覆盖或删除该岗位的简历画像。每个用户在每个岗位最多保留一份当前画像；重复上传更新同一画像记录。

简历画像 API 强制要求 `positionId`。不传 `positionId` 返回 400，提示选择岗位。公共岗位和用户自己的私有岗位都允许用户上传自己的简历画像；其他用户私有岗位不可见、不可操作。

开始面试时，后端按 `userId + positionId` 自动加载该岗位简历画像，并把定制问题保存到面试会话中。前端不再负责传递简历定制题，也不再使用旧的全局 `resume_analysis` 缓存作为面试输入来源。

## User Stories

1. As a logged-in user, I want a dedicated resume profile management page, so that I can manage resumes by position.
2. As a logged-in user, I want the resume page to list all currently visible positions, so that I can choose where to upload a profile.
3. As a logged-in user, I want the position list to include public positions and my private positions, so that I can prepare for any role I can use.
4. As a logged-in user, I want each position row to show public/private scope, resume profile status, and interview count, so that I can scan preparation state quickly.
5. As a logged-in user, I want position rows sorted by current context, profile status, history status, public/private scope, and name, so that the most relevant positions are easiest to find.
6. As a logged-in user, I want the resume page to default from URL `positionId`, so that deep links from setup or dashboard open the right role.
7. As a logged-in user, I want the resume page to fall back to my latest interview position, so that it opens a useful role when no URL context exists.
8. As a logged-in user, I want the resume page to fall back to the most recently updated resume profile, so that recent work remains accessible.
9. As a logged-in user, I want an empty state when no position context exists, so that I know to select a position before uploading.
10. As a logged-in user, I want upload disabled until a position is selected, so that I cannot create unscoped resume data.
11. As a logged-in user, I want uploading a PDF for one position to create or update only that position’s profile, so that other profiles are untouched.
12. As a logged-in user, I want uploading a new PDF for the same position to overwrite the current profile, so that I can update my preparation.
13. As a logged-in user, I want same-position upload to update the same row, so that the position profile slot remains stable.
14. As a logged-in user, I want to delete the current position’s resume profile, so that I can remove private data for that role.
15. As a logged-in user, I want deleting one position’s profile to leave other positions’ profiles untouched, so that role isolation is preserved.
16. As a logged-in user, I want deleting a private position to delete its resume profile, so that unreachable assets are cleaned up.
17. As a logged-in user, I want historical interview reports to remain after resume deletion, so that past reports are preserved.
18. As a logged-in user, I want the profile response to include position metadata, so that I know exactly which role the displayed analysis belongs to.
19. As a logged-in user, I want the profile response to include the upload-time position snapshot, so that renamed positions remain explainable.
20. As a logged-in user, I want the main display name to use the current position name, so that selectors reflect current system state.
21. As a logged-in user, I want “基于画像开启面试” to route to setup for the selected position, so that I can choose mode, difficulty, and focus areas.
22. As a logged-in user, I want the setup page to show whether the selected position has a profile, so that I know if resume-tailored questions will be used.
23. As a logged-in user, I want to start an interview without a profile, so that resume upload remains optional.
24. As a logged-in user, I want no confirmation dialog when starting without a profile, so that interview startup remains low-friction.
25. As a logged-in user, I do not want another position’s resume profile to be reused, so that the interview context stays clean.
26. As a logged-in user, I want the backend to load resume questions for the current position automatically, so that the frontend cannot send the wrong profile.
27. As a logged-in user, I want legacy global resume data to be preserved but not shown as a concrete position profile, so that migration is non-destructive.
28. As a developer, I want resume APIs to require `positionId`, so that global singleton behavior cannot return accidentally.
29. As a developer, I want resume APIs to validate visible positions, so that users cannot operate on another user’s private position.
30. As a developer, I want `resume_profile` to support `positionId`, so that profiles are stored by structured role context.
31. As a developer, I want a uniqueness rule for `userId + positionId`, so that each user has one current profile per role.
32. As a developer, I want `interview_record.resumeProfileId`, so that the system can record whether a position profile was used at interview start.
33. As a developer, I want `resumeProfileId` to be a slot reference, not a version snapshot, so that first-version scope remains simple.
34. As a maintainer, I want Dashboard resume management to route to `/resume`, so that upload logic is centralized in the dedicated page.

## Implementation Decisions

- Reuse `/resume` as the position resume profile management page.
- The page uses a position list plus selected-position detail layout.
- Position list comes from the shared visible-position summary API. This PRD is responsible for making `hasResumeProfile` and `resumeUpdatedAt` accurate on that API.
- Position list displays:
  - position name
  - public/private label
  - profile status
  - interview count
  - selected state
  - resume updated time when available
- Default selection priority:
  - URL `positionId`
  - latest interview position
  - most recently updated resume profile
  - empty state requiring selection
- Upload requires selected structured `positionId`.
- Public positions and owned private positions allow user-owned resume profiles.
- Other users’ private positions are inaccessible.
- Existing legacy profile rows remain but are not auto-bound to concrete positions.
- Resume profile response is a wrapper:
  - profile ID
  - position ID
  - current position name
  - upload-time position snapshot
  - updated time
  - `analysis` JSON
- Resume APIs require `positionId`; missing values return 400.
- Same-position upload updates the existing profile row.
- Same-position upload does not create multiple versions.
- Delete removes only the selected position’s profile.
- Deleting a private position deletes its resume profile.
- Historical interview reports are not deleted.
- Add nullable `resumeProfileId` to interview records.
- Start interview loads the current position’s profile server-side.
- Frontend no longer uses old global `resume_analysis` cache for resume-tailored questions.
- Old request field for `resumeQuestions` may remain temporarily, but it is not the new main path.
- Dashboard resume upload management is reduced or redirected to the dedicated `/resume` page.
- Upload success routing:
  - from `/resume`: stay on `/resume?positionId=...`
  - from Dashboard management: route to `/resume?positionId=...`
  - from setup: return to or remain on setup for the selected position
- “基于画像开启面试” routes to `/setup?positionId=...`.
- `CONTEXT.md` and `CHANGELOG.md` should be updated.

## Testing Decisions

- Migration contract tests should verify `resume_profile.position_id`, the `userId + positionId` uniqueness rule, legacy nullable data handling, and `interview_record.resume_profile_id`.
- Backend controller/service tests should cover upload/query/delete with `positionId`, missing `positionId` 400 behavior, public-position access, owned-private-position access, foreign-private-position rejection, and same-position overwrite.
- Start-interview tests should cover loading current-position resume questions, not loading other-position profiles, and recording `resumeProfileId` when present.
- Frontend tests should cover position selection, empty state, profile status display, upload parameter behavior, delete behavior, setup page profile status, and removal of global-cache dependency where feasible.
- Browser verification should cover `/resume` position switching, empty profile state, profile metadata display, delete UI, setup status, and starting an interview with and without a current-position profile.
- Docker restart verification is required because schema migration is included.

## Out of Scope

- Multiple versions per position.
- Snapshotting full resume JSON into interview records.
- Automatic binding of legacy global resume profiles to positions.
- Cross-position resume reuse prompts.
- Admin management of ordinary users’ resume profiles.
- AI Mentor position-specific behavior.
- Copying a resume profile when copying a public position into a private position.

## Further Notes

This PRD depends on the shared visible-position summary API described in the history-growth PRD. The history-growth phase only requires accurate history counts; this PRD completes the resume-facing fields on that endpoint. The key invariant is that `positionId` is the isolation key; `position` text is only a display snapshot.
