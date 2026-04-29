## Agent skills

### Issue tracker

Issues live as GitHub issues in `nzy0510/AI-Interview`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## 沟通与工作流规则

- 默认使用中文回答。
- 修改代码前，先简要说明计划。
- 不确定需求时，先提出关键问题，不要直接猜测实现。
- 不要一次性大范围重构，优先小步修改。
- 修改完成后，必须总结：
  - 修改了哪些文件
  - 每个文件为什么改
  - 是否运行了测试
  - 是否还有遗留风险
- 如果有多个方案，先推荐当前项目最合适的一个，并说明理由。

## Spring Boot 规则

- 新增接口时，保持与现有 REST API 风格一致。
- 不要在 Controller 中写复杂业务逻辑。
- 不要在 Service 中直接拼接复杂 SQL。
- 不要随意修改全局配置，例如 `application.yml`、`SecurityConfig`、`WebMvcConfig`，若要修改，先进行询问。
- 修改配置文件前，先说明影响范围。
- 涉及事务时，优先在 Service 层使用 `@Transactional`。
- 涉及认证授权时，必须检查 Spring Security / JWT / 拦截器相关逻辑。

## 功能添加要求

- 准备一份更新文档，在添加新功能时，记录下所做的修改，防止后续遗忘，导致重复出现相同的错误
- 更新每个功能前，详细询问确认用户需求，一次性考虑周全，而非将应用跑起来就"完事大吉"，要符合生产级应用的需要
- 更新后对每个更改内容进行说明并解释
- 功能更新后同步更新README文档

## 测试规则

- 新增业务逻辑时，优先补充单元测试。
- 修复 bug 时，先写能复现 bug 的测试，再修复。
- Service 层优先使用 JUnit + Mockito 测试。
- Controller 层优先使用 MockMvc 测试。
- 数据库相关逻辑可以使用 H2、Testcontainers 或项目已有测试方案。
- 不要为了让测试通过而删除有效断言。
- 修改测试前，先确认是测试过时，还是业务逻辑错误。
- 如果无法运行测试，必须说明原因。

## 安全规则

- 不要把 API Key、数据库密码、JWT Secret 写入代码。
- 不要把 `.env`、`application-local.yml`、密钥文件提交到 Git。
- 日志中不能打印完整 API Key、access token、refresh token、密码或敏感信息。
- 如果发现疑似密钥泄露，必须立即提醒我轮换密钥。

## git 操作要求
- 修改代码前，必须先确认当前分支和工作区状态。
- ai agent 可以自主commit 和 push 并合并到主分支，但是一定要在确认功能无误且已创建过新功能分支后再合并
- 注意保护隐私内容，并做好git_ignore工作，不上传没必要的文件
- 遇到 merge conflict 时，先说明冲突内容和解决建议，再等待我确认。
- 提交前必须展示修改文件、commit message 和测试结果。
- commit message 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。

## 记忆文件
- 完成一个较大的功能后，将其录入记忆文件中