# User-Owned Question Bank / RAG / Report Run Record

Feature: user-owned question bank, scoped RAG, async reports
Controller: current Codex thread
Base commit: 7d8268e
Target branch: master local checkout unless integration branch is created later

## Agents

- name: IQB-01 Backend/Data Worker
  role: Backend/Data
  branch: master local checkout
  worktree: E:\Develop\interview
  ownership: backend entity, mapper, migration, backend tests for IQB-01
  status: completed
  commit: 6cfcb95

- name: IQB-02 Backend/Auth Worker
  role: Backend/Frontend
  branch: master local checkout
  worktree: E:\Develop\interview
  ownership: admin role service/controller, admin-token frontend removal, backend/frontend tests
  status: completed
  commit: 6cfcb95

- name: IQB-02 Testing Review Worktree
  role: Testing Review
  branch: detached worktree
  worktree: C:\Users\nzy\.codex\worktrees\b8bd\interview
  ownership: read-only IQB-02 test coverage review
  status: completed
  thread: 019ec06d-029a-75d3-b6dc-5e9037c8f555

- name: IQB-02 Security Review Worktree
  role: Security Review
  branch: detached worktree
  worktree: C:\Users\nzy\.codex\worktrees\a243\interview
  ownership: read-only IQB-02 auth/security review
  status: completed
  thread: 019ec06d-632b-7121-a024-3f40f5cbe125

- name: IQB-03 Job Executor Worker
  role: Backend
  branch: detached worktree from current working tree
  worktree: C:\Users\nzy\.codex\worktrees\9e3a\interview
  ownership: job lifecycle service/controller/recovery/executor tests for IQB-03
  status: stalled; no IQB-03 file changes produced, replacement dispatched
  thread: 019ec071-b214-7560-928d-a40865bd56df
  commit: pending

- name: IQB-03 Replacement Worker
  role: Backend
  branch: detached worktree from current working tree
  worktree: C:\Users\nzy\.codex\worktrees\6697\interview
  ownership: narrowed IQB-03 first lifecycle slice
  status: completed first slice; integrated by controller
  thread: 019ec074-6be2-7ab1-a5d1-1f06f4a54091
  commit: pending

- name: IQB-03 Remaining Worker
  role: Backend
  branch: detached worktree from current working tree
  worktree: C:\Users\nzy\.codex\worktrees\8f98\interview
  ownership: remaining IQB-03 service lifecycle/recovery/polling/executor skeleton
  status: blocked; wrote service draft in worktree only, not integrated
  thread: 019ec07a-f4c2-7c10-97f5-6efa294f7dd3
  commit: pending

- name: IQB-03 Completion Worker
  role: Backend
  branch: detached worktree from current working tree
  worktree: C:\Users\nzy\.codex\worktrees\3738\interview
  ownership: complete IQB-03 service/controller/recovery/executor tests and implementation
  status: stopped after user requested no further subagent dispatch; final IQB-03 implementation completed by controller in main worktree
  thread: 019ec081-b2b6-72e1-a5f1-de29879d0628
  commit: pending

## Validation

- command: `cd backend; mvn "-Dtest=UserServiceTest,QuestionBankImportContractTest" test`
  result: passed before IQB-01 implementation, 21 tests, 0 failures
- command: `cd backend; mvn "-Dtest=UserOwnedQuestionBankMigrationContractTest" test`
  result: passed in worker handoff before controller seed guard edit
- command: `cd backend; mvn "-Dtest=UserOwnedQuestionBankMigrationContractTest,UserServiceTest" test`
  result: passed after review fixes, 19 tests, 0 failures
- command: `cd backend; mvn test`
  result: passed after review fixes, 136 tests, 0 failures
- command: `git diff --check`
  result: passed for tracked edits; Git reported CRLF normalization warnings only
- command: `cd frontend; npx vitest run`
  result: passed before IQB-02 implementation, 5 files, 26 tests
- command: `cd frontend; npm run build`
  result: passed before IQB-02 implementation, existing chunk-size warning only
- command: `cd backend; mvn "-Dtest=AdminGuardServiceTest,AdminRoleServiceTest,AdminRoleControllerTest,UserControllerTest,QuestionBankAdminControllerTest" test`
  result: passed after IQB-02 implementation, 13 tests, 0 failures
- command: `cd backend; mvn test`
  result: passed after IQB-02 implementation, 144 tests, 0 failures
- command: `cd frontend; npx vitest run`
  result: passed after IQB-02 implementation, 5 files, 26 tests
- command: `cd frontend; npm run build`
  result: passed after IQB-02 implementation, existing chunk-size warning only
- command: `git diff --check`
  result: passed after IQB-02 implementation; Git reported CRLF normalization warnings only
- command: `cd backend; mvn "-Dtest=AppJobServiceTest" test`
  result: passed for IQB-03 first lifecycle slice in replacement worktree after sandbox escalation, 1 test, 0 failures
- command: `cd backend; mvn "-Dtest=AppJobServiceTest" test`
  result: passed after controller integrated IQB-03 first lifecycle slice into main working tree, 1 test, 0 failures
- command: `cd backend; mvn "-Dtest=AppJobControllerTest" "-Dsurefire.useFile=false" test`
  result: red first after adding public job visibility expectation; failed because list query lacked scope condition and public detail returned 403
- command: `cd backend; mvn "-Dtest=AppJobControllerTest" "-Dsurefire.useFile=false" test`
  result: passed after public job visibility fix, 5 tests, 0 failures
- command: `cd backend; mvn "-Dtest=AppJobServiceTest,AppJobControllerTest,AppJobRecoveryTest" "-Dsurefire.useFile=false" test`
  result: passed after IQB-03 completion, 13 tests, 0 failures
- command: `cd backend; mvn test`
  result: failed inside sandbox before escalation; failures were missing parent fixtures/config files and Surefire report write permission
- command: `cd backend; mvn test`
  result: passed in real backend worktree after sandbox escalation, 157 tests, 0 failures

## Reviews

- type: Testing
  result: APPROVED_WITH_NOTES; strengthened preserved-table destructive-statement test helper after note
- type: Security
  result: APPROVED_WITH_NOTES; added custom bootstrap-admin registration coverage after note
- type: IQB-02 Testing Review
  result: APPROVED_WITH_NOTES; worktree thread 019ec06d-029a-75d3-b6dc-5e9037c8f555 found no blocker. Notes: legacy localStorage token cleanup constant is intentional; concurrent double-revoke admin hardening can be revisited later.
- type: IQB-02 Security Review
  result: APPROVED_WITH_NOTES; worktree thread 019ec06d-632b-7121-a024-3f40f5cbe125 found no blocker. Notes: `app.admin-token` remains in example config but is not read by the admin authorization chain and is tracked as IQB-14 cleanup risk.

## Gates

- IQB-01 is HITL and approved by user before development.
- Next HITL gate: IQB-14.
- AFK loop begins only after IQB-01 completes.

## Risks

- IQB-01 contains destructive migration for old interview/report/RAG-log data.
- User accounts, user LLM configs, resume profiles, and feedback must be preserved.
- V14 promotes the default existing bootstrap account row; non-default bootstrap promotion for existing rows remains an IQB-02/config-initialization follow-up. New registrations honor configurable bootstrap username/email now.
- Real MySQL/Flyway Docker migration execution was not run in this phase; coverage is SQL contract tests plus backend compilation/unit tests.
- IQB-02 preserves old `app.admin-token` config keys for now because its task card forbids changing `application*.yml`; runtime admin authorization no longer reads them.
- IQB-02 front-end route `/admin/analytics` is still login-guarded rather than front-end role-guarded, but its visible entry is hidden by `isAdmin` and backend APIs enforce admin role.
