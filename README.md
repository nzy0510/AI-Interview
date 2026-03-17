# AI 模拟面试系统 (AI Mock Interview System)

基于**大语言模型 (LLM)** 构建的专业技术模拟面试平台。该系统能够让候选人体验沉浸式的真实面试流程，支持多岗位选择、实时打字机流式问答对话，并在面试结束后由 AI 面试官出具全方位的求职评估报告。

---

## 🚀 项目概览 (Project Overview)

本项目旨在利用 AI 技术帮助求职者降低面试紧张感，提升技术表达能力。系统通过角色扮演 (Role-Playing) 技术，使大模型化身为专业面试官。候选人可以在模拟环境中不断试错，并在最后得到一份包含具体评分、能力雷达图与改进建议的报告。

### 主要功能模块
1. **🎙️ 语音面试交互**：支持 Web Speech 实时转写 (STT)，具备自动停止感应与动态波形反馈。
2. **🧠 岗位精准 RAG**：基于本地知识库的检索增强生成，实现不同岗位的题库隔离与专业问答。
3. **💬 沉浸式流式对话**：采用 SSE 技术实现逐字生成的“打字机”效果，响应迅速。
4. **📊 6A 全方位评估**：面试结束后，从专业深度、思考能力、知识储备等 6 个维度生成可视化雷达图。
5. **📈 历程与历史记录**：自动持久化面试对话，并绘制账号专属的能力成长曲线。

### 适用受众 (Target Audience)
- **高校应届生**：春招/秋招前用于克服面试恐惧，整理八股文表述结构。
- **初/中级开发人员**：在跳槽前用来检测技术盲区，熟悉新岗位的常见面试套路。
- **非开发类岗位求职者**：可通过后台扩展岗位配置，快速平移至产品经理、HR 面试练习等场景。

---

## 🛠 技术栈与架构 (Tech Stack)

### 核心架构图 (Conceptual Architecture)
```mermaid
graph LR
    User(候选人) <--> Vue3[Vue 3 Frontend]
    Vue3 <--> SSE[SSE Stream Channel]
    SSE <--> SpringBoot[Spring Boot Service]
    SpringBoot <--> LC4J[Langchain4j Orchestrator]
    LC4J <--> LLM(DeepSeek AI)
    LC4J <--> RAG[Vector Knowledge Base]
```

### 技术实现深度
*   **后端 (Backend)**:
    *   **AI 编排**: 使用 `Langchain4j` 封装 RAG 流程。通过 `Metadata Filter` 实现岗位级别的知识路由（java/frontend/common 隔离）。
    *   **SSE 优化**: 针对 Spring Boot 的 `SseEmitter` 进行了 JSON 序列化转换，解决了原生字符串推送可能导致的 `HttpMessageNotWritableException`。
*   **前端 (Frontend)**:
    *   **响应式流处理**: 使用 Vue 3 的 `Reactive Proxy` 直接管理 SSE 数据流，实现高性能的 DOM 实时更新。
    *   **底层可视化**: 完全采用原生 `Canvas API` 渲染雷达图与音频波形，确保流畅的交互反馈体验。
    *   **时序分析**: 引入 `Chart.js` 绘制能力成长曲线。

---

---

---

## ⚡ 快速开始 (推荐使用 Docker)

如果你安装了 **Docker** 和 **Docker Desktop**，可以使用以下命令实现“秒级”部署，无需手动配置复杂的开发环境。

### 1. 准备配置文件
在项目根目录下，将 `.env.example` 复制并重命名为 `.env`：
```bash
cp .env.example .env
```
打开 `.env` 文件，填入你的 **DEEPSEEK_API_KEY**。

### 2. 一键启动
在终端执行以下命令：
```bash
docker-compose up -d
```
> [!TIP]
> 如果你是第一次运行或修改了代码，建议使用 `docker-compose up --build -d` 强制重新构建。

### 3. 开始使用
- **访问地址**: `http://localhost`
- **默认管理员账号**: `admin`
- **默认初始密码**: `123456`

---

## 💻 本地手动开发环境 (Manual Setup)

如果您需要修改代码并进行本地调试，可以参考以下步骤：

### 1. 环境依赖
- **JDK 17+** | **Maven 3.6+**
- **Node.js 20+** | **NPM**
- **MySQL 5.7+** (推荐使用 Docker 内部数据库或小皮面板)

### 2. 数据库准备
1. 创建数据库 `ai_interview_ds`。
2. 导入初始化脚本：`mysql/init/init.sql`。

### 3. 后端启动 (Spring Boot)
1. 用 IDE 打开 `backend` 目录。
2. 配置 `application.yml` 或在环境变量中设置 `DEEPSEEK_API_KEY`。
3. 运行主类，成功后看到 `====== AI Interview Backend Started ======`。

### 4. 前端启动 (Vue 3)
```bash
cd frontend
npm install
npm run dev
```

---

## 📂 项目结构
```text
.
├── backend          # Spring Boot 后端工程
├── frontend         # Vue 3 前端工程
├── mysql            # 数据库初始化脚本
├── docker-compose.yml
└── .env.example     # 环境配置模板
```

---

## 📝 扩展说明
- **岗位扩展**: 在 `backend/src/main/resources/knowledge` 下添加文件夹（如 `python`）并放入文档，系统会自动向量化。
- **背景定制**: 前端 `Login.vue` 和 `Home.vue` 采用了高性能 Canvas 动画，可灵活调整粒子效果。

---