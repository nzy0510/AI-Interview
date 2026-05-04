# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

### 后端

```powershell
# 运行全部测试
cd backend && mvn test

# 运行单个测试类
cd backend && mvn test -Dtest=UserServiceTest

# 启动后端（需要先配置 application.yml）
cd backend && mvn spring-boot:run
```

### 前端

```powershell
# 安装依赖
cd frontend && npm install --legacy-peer-deps

# 启动开发服务器（含 Vite 热更新）
cd frontend && npm run dev

# 构建生产包
cd frontend && npm run build
```

### Git 操作（项目根目录在 `E:\Java-web\interview`，前端子目录操作需指定 git 根目录）

```powershell
git -C /e/Java-web/interview add frontend/src/...
git -C /e/Java-web/interview commit -m "..."
git -C /e/Java-web/interview push
```

## 架构概览

```
App.vue
  ├── /login → Login.vue（无外壳，独立路由）
  └── 其他路径 → AppShell.vue（侧边栏 + 顶栏 + <router-view>）
        ├── 侧边栏导航: Dashboard / Interview Setup / AI Mentor / Resume / Reports / Settings
        └── <router-view>
              ├── / → Home.vue → DashboardHome.vue
              ├── /interview/setup → InterviewSetup.vue
              ├── /interview → Interview.vue（文字面试，SSE 流式）
              ├── /video-interview → VideoInterview.vue
              ├── /mentor → Mentor.vue（AI 教练分析）
              ├── /history → History.vue（面试报告）
              ├── /resume → Resume.vue（简历管理）
              └── /settings → Settings.vue（用户资料 + 偏好 + 头像 + 登出）
```

### 后端分层

- `controller/` — REST API，通过 `request.getAttribute("currentUserId")` 获取当前用户
- `service/` — 业务逻辑，`InterviewServiceImpl` 拆分为 `SessionStore`（会话缓存）、`RagRetriever`（知识检索）、`EvaluationGenerator`（评估生成）
- `service/impl/` — Service 实现
- `mapper/` — MyBatis-Plus Mapper 接口
- `entity/` — 数据实体 + `InterviewPhase` 面试阶段枚举
- `dto/` — 请求/响应 DTO
- `config/` — `InterviewPrompts`（AI 提示词配置）、`PositionCategoryConfig`（岗位-分类映射）
- `utils/` — `JwtUtils`（Spring Bean）、JWT 拦截器

### 面试阶段状态机

```
OPENING → TECHNICAL → HR → CLOSING → FINISHED
```

- 阶段持久化到 `interview_record` 表，SSE 断连不丢状态
- AI 态度监控：候选人不礼貌或敷衍时警告，严重时 `[TERMINATE]`
- 组长致闭幕词后输出 `[AUTO_FINISH]` 标记结束

### 认证流程

- JWT 签名密钥通过环境变量 `JWT_SIGN_KEY` 注入
- 前端 token 存 `localStorage`，axios 拦截器自动附加 `Authorization: Bearer`
- SSE 不支持自定义 Header，token 通过查询参数 `?token=` 传递，后端拦截器双模兼容
- 路由守卫：非 `/login` 路径无 token 时重定向登录页

## 前端关键约定

### Axios 响应拦截器

`src/utils/request.js` 自动解包 `res.data`（当 `res.code === 200` 时），所以调用 API 函数拿到的直接是内部 data：

```js
// 后端返回 { code: 200, data: { username: "...", nickname: "..." } }
const user = await getCurrentUserAPI()
// user 直接是 { username: "...", nickname: "..." }，无需 .data
```

### el-upload 注意事项

`el-upload` 使用原生 XHR（非 axios），不会经过拦截器。需要手动传 `:headers` 带 token，`on-success` 收到的 response 是**已解析的 JSON 对象**（Element Plus 内部做了 `JSON.parse`）。

### 跨页面数据同步

项目不使用 Pinia/Vuex，跨页面共享用 `localStorage` 缓存 + API 双模：
- `auth.js` 提供 `getNickname()` / `setNickname()` / `logout()` 等工具函数
- Settings 保存资料后同步缓存 → Dashboard `onMounted` 优先读缓存 → 缓存未命中再调 API

### Vite 代理

`/api` 和 `/uploads` 路径代理到 `http://localhost:8080`。`.env` 中 `VITE_API_BASE_URL` 可选覆盖。

## 已知陷阱

- **`application.yml` 被 gitignore**：修改配置需要通过环境变量或 `application.yml.example` 模板传达，不能直接提交
- **前端子目录 git 操作**：git 根目录在 `E:\Java-web\interview`，`frontend/` 下操作需用 `git -C /e/Java-web/interview`
- **Vue ref vs 普通变量**：`const role = route.query.role || '默认值'` 不是 ref，模板中用 `{{ role }}` 不用 `.value`
- **头像上传路径**：相对路径在 Tomcat 中会解析到临时目录，代码已做 `user.dir` 绝对路径解析
- **Settings 按钮布局**：`section-head` 右侧按钮用 `.section-actions` 包裹，`display: flex; gap: 8px`

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

- 按照生产级项目规范修改，增加功能
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
- 每次更新功能时都新创建功能分支
- ai agent 可以自主commit 和 push 并合并到主分支，但是一定要在确认功能无误后再合并
- 注意保护隐私内容，并做好git_ignore工作，不上传没必要的文件
- 遇到 merge conflict 时，先说明冲突内容和解决建议，再等待我确认。
- 提交前必须展示修改文件、commit message 和测试结果。
- commit message 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。

## 功能开发规则
- 在进行难度较大的开发工作时，优先采用tdd工作流进行测试开发。

## 记忆文件
- 完成一个较大的功能后，将其录入记忆文件中