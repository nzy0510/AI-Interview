# AI 模拟面试与能力提升平台 - 重建实施计划 (Implementation Plan)

## 📌 目标概述
本项目旨在从零开始，使用现代化的前后端分离架构重构当前的 Java Web 项目。项目将升级为基于 **Spring Boot** 的后端服务和基于 **Vue 3** 的前端界面，并接入大语言模型 (LLM) 以实现“角色扮演式”的多模态模拟面试环节与智能反馈。考虑到你是第一次做此类项目，本计划将把复杂的任务拆解为清晰的模块，并尽量采用对新手友好的技术栈搭建。

## ⚠️ 需要确认的事项 (User Review Required)
> [!IMPORTANT]
> 在我们正式开始写代码之前，请告诉我以下几点你的想法：
> 1. **AI 大模型选择**：你打算使用哪家的大模型 API？(例如：DeepSeek, 阿里通义千问, 智谱 ChatGLM，或者国外的 OpenAI/Claude)。如果你还没有去申请对应的 API Key，我也可以教你怎么申请。
> 2. **知识库 (RAG) 的实现难度**：如果你是初学者，接入专业的向量数据库(如 Milvus/Chroma)可能有一定学习成本。我建议初期我们可以使用 Spring AI 配合**内存级向量存储 (In-Memory Vector Store)** 或通过普通数据库结合高级提示词 (Prompt) 来降低入门门槛，后续再做升级。你觉得可以吗？
> 3. **语音交互**：前端是否需要立刻支持语音识别转文字的功能？（这可以通过浏览器自带的 Web Speech API 或者第三方语音服务实现，但建议放在二期完成）。
> 4. **环境要求**：你的电脑上是否已经安装了 **Java (推荐 17 或 21)**, **Maven**, **Node.js**, 和 **MySQL**？如果没有，请告诉我。

---

## 🛠️ 推荐技术栈 (Proposed Tech Stack)

### 后端 (Backend)
*   **核心框架**：Spring Boot 3 + Java 17/21
*   **项目管理**：Maven
*   **数据库接入**：MyBatis-Plus 或 Spring Data JPA + MySQL
*   **AI 框架**：Spring AI 或 LangChain4j (用于快速对接大模型并构建 RAG 知识库)
*   **安全认证**：JWT (实现用户登录保持)

### 前端 (Frontend)
*   **核心框架**：Vue 3 (Composition API) + Vite 构建
*   **UI 组件库**：Element Plus
*   **网络请求**：Axios (结合 WebSocket或SSE 用于流式大模型对话)
*   **路由**：Vue Router

---

## 🚀 实施步骤 (Proposed Changes & Milestones)

我们将把项目分为五个阶段来逐步实现。这样不会让你觉得压力太大，每一步完成后我们都能看到可见的成果。

### 阶段一：基础设施构建 (项目骨架)
1. 删除旧项目代码，重新在 `e:\Java-web\interview` 目录下初始化 **Spring Boot** 后端项目和 **Vue 3** 前端项目。
2. 搭建好 MySQL 数据库，并配置后端的连接。

### 阶段二：数据库与用户系统
1. 设计 `用户表(user)` 以及 `面试记录表(interview_record)`。
2. 实现基础的**注册**、**登录**接口。
3. 前端搭建登录页面和工作台首页。

### 阶段三：AI 对话与面试核心链路
1. 封装 AI 大模型接口调用。
2. 设定**提示词模板 (Prompt Template)**，告诉 AI 它现在是一个严厉或温和的面试官，并提供岗位背景信息。
3. 后端开放**流式文本对话接口 (Server-Sent Events)**，使得前端文字像打字机一样逐字展示（提升体验）。
4. 前端构建类似微信/ChatGPT的对话聊天窗口界面。

### 阶段四：RAG 知识库与能力评估
1. 在数据库中录入少量“Java后端”或“Web前端”的八股文/知识点，结合向量检索，当用户回答不上来时提供正确的指引。
2. 在面试结束主动触发一个大语言模型的评估任务，根据用户的历史聊天记录打分（考量技术、逻辑、表达），并在前端展示评估雷达图或详细报告。

### 阶段五：测试与完善
1. 模拟真实面试流程，调优 AI 的语气和追问逻辑。
2. 优化前端的美观度。

---

## 🌟 PHASE 2: 面试题库与 RAG 知识库进化 (New!)

我们将为系统注入“字节跳动”专属的面试灵魂，使 AI 从“无神”的漫谈转变为“有备而来”的专业考官。

### RAG (检索增强生成) 架构设计
为了保证最快的上手速度并节约成本，我们将采用完全本地化的轻量级架构：
1. **知识入库 (Document Ingestion)**:
   - 在后端的 `src/main/resources/knowledge_base/` 目录下创建具体的分类 Markdown 文档（例如：`bytedance_java_backend.md`, `bytedance_frontend.md`）。
   - 内容涵盖：专业知识、项目深挖指导、场景题解析、行为题（HR面）规范、字节核心技术栈要求及优秀范例。
2. **向量化与存储 (Embedding & Vector Store)**:
   - 使用 Langchain4j 提供的本地内存级向量数据库 `InMemoryEmbeddingStore`。无需安装任何额外的中间件（如 Redis 或 Milvus）。
   - 使用开源的轻量级本地嵌入模型（如 `AllMiniLmL6V2EmbeddingModel` 或 `bge-small-zh`），项目启动时自动将 TXT/MD 文件进行分块（Document Splitter）并映射为多维向量。
3. **检索与生成 (Retrieval & Generation)**:
   - 重构现有的 `chatModel.generate` 逻辑，采用 Langchain4j 强大的 `AiServices` 高级抽象接口。
   - 当用户发送回答时，首先在检索器（ContentRetriever）中进行相似度搜索（Similarity Search），找到知识库中最相关的“标准答案”或“追问方向”。
   - 将检索到的知识点偷偷织入 `SystemMessage` 的上下文中，再一同发给 DeepSeek，让模型“看着标准答案”来点评或刁难候选人。

### 新增依赖
后端 `pom.xml` 将引入处理本地 Embedding 的 Langchain4j 扩展包：
- `langchain4j-embeddings-all-minilm-l6-v2` (本地计算向量化，不消耗远程 API)
- `langchain4j-document-parser-apache-tika` (如果需要解析 PDF 等其他格式，可选)

---

## 🚀 PHASE 3: 项目运行与环境维护 (Operation & Maintenance)

### 1. 解决端口冲突 (Port 8080 Conflict)
- 识别并结束占用 8080 端口的进程。
- 可选：在 `application.yml` 中将后端端口修改为 8081 以避开常用端口。

### 2. 标准运行流程
- **后端**：IDEA 中点击 `AiInterviewApplication` 运行，或终端执行 `mvn spring-boot:run`。
- **前端**：终端执行 `npm run dev`。

---

## 🏗️ PHASE 4: 岗位隔离与知识对齐 (Role Isolation)

### 1. 知识库目录结构化 (Structure)
- 将 `knowledge_base` 划分为子目录：`java`, `frontend`, `behavioral` (通用)。

### 2. 自动元数据注入 (Metadata Ingestion)
- 在 `DataLoadingService` 遍历文件时，根据父文件夹名称为 `TextSegment` 添加 `category` 元数据。

### 3. 检索端条件过滤 (Retrieval Filter)
- 在 `InterviewServiceImpl` 中，根据当前面试的 `position` 参数，构建 `Filter` 对象。
- 让 `ContentRetriever` 仅在对应分类或通用分类（behavioral）中检索，彻底杜绝“牛头不对马嘴”的情况。

---

## 🎙️ PHASE 5: 语音交互与多维表达评估 (Voice & Analysis)

### 1. 语音转文字 (STT)
- **技术选型**：使用浏览器原生 `Web Speech API` 实现零成本、实时语音转写。
- **降级方案**：若浏览器不支持，保留手动输入模式。

### 2. 情感与表达分析 (Behavioral Metrics)
- **语序统计**：前端记录说话时长与最终转写字数，计算每轮对话的 `WPM` (语速)。
- **情感与自信度 (Hybrid Approach)**：
    - **技术手段**：分析文本中的语气助词频率与重复率评估停顿感。
    - **AI 赋能**：在生成报告时，由 AI 基于转写文本的逻辑严密性进行“心理画像”。
- **可视化评分**：在最终报告中增加 **“表达力雷达图”** (自信度、逻辑性、语速适配、发音清晰度)。

### 3. 前端视觉盛宴 (Vibrant UI)
- **波形图**：使用 `Web Audio API` 的 `AnalyserNode` 获取频率数据，在录音时绘制动态波形，给用户极强的实时交互感。
- **状态切换**：设计一个“电台/通话”风格的面试模式，支持点击或长按说话。

---

## ✅ 验证计划 (Verification Plan)
每一次代码完成后，我将：
1. **自动执行**相关后端的 API 测试，确保能成功连通。
2. **指导你**在本地浏览器打开 `http://localhost:5174` 等地址查看真实界面并测试。
3. **分步骤教学**：对于你不懂的地方，我会给出详细的解释（特别是 Spring Boot 的注解和 Vue 3 的概念）。

---

## 🚀 PHASE 6: 智能评估可视化与历史系统 (Advanced Dashboard)

### F1. 静音自动发送 (Auto-Submit on Silence)
- 复用 `SpeechRecognition.onend` 检测 + 一个 **2-3 秒防抖计时器**。
- 若检测到静音（识别器停止输出且 `inputMsg` 有内容），**自动触发 `sendMessage()`**，无需点击。
- 可通过开关按钮开启/关闭此功能（用户有控制权）。

### F2. 录音延迟优化
- Web Speech API 的延迟来自于**分段识别+网络往返**，本质是 Cloud API 属性，无法完全消除。
- 优化策略：`interimResults=true` 下，将中间结果立即显示在输入框，给用户**实时反馈的"假象"**（视觉上无延迟）；最终结果确认后微调文字。

### F3. 丰富的情感分析指标 (Behavioral Score)
前端计算并发送以下指标给后端 AI：
- **语速 WPM**：字符数 / 时长
- **停顿比率**：`(总录音时长 - 说话时长) / 总录音时长`（通过分析语音能量检测）
- **语气词频率**：统计"嗯/那个/就是"等词出现次数
- AI 根据以上 3 个维度对 `清晰度`、`自信度`、`流利度` 打分（均在 Prompt 中要求返回）

### F4. 六边形能力雷达图 (JoJo-style Ability Chart)
- **技术**：前端使用原生 Canvas 绘制正六边形雷达图（不引入图表库，保持轻量）
- **6 个维度**（替换举例，用专业面试评判标准）：
  - 技术深度、解题思路、知识广度、表达清晰度、逻辑思维、应变能力
- **等级**：S/A/B/C/D 五级（类 JoJo），后端 AI 返回 JSON 中包含每个维度的等级
- **动画**：雷达图绘制时带渐变填充和 Entry 动画

### F5. 具体提升建议 (Actionable Recommendations)
- 在 AI Prompt 中要求额外返回 `recommendations` 数组，包含 3-5 条具体行动建议（附时间线，如"本周"、"两周内"）
- 在报告界面以时间线 `el-timeline` 形式展示

### F6. 面试历史与能力趋势 (History & Trend Chart)
- **数据库**：`interview_record` 表已有 `score/feedback`，新增 `ability_json` 字段存储六维能力 JSON。
- **新页面**：`History.vue` — 显示历史面试记录列表，可点击查看报告
- **趋势折线图**：使用 Canvas 绘制多次面试的综合分/各维度能力随时间的趋势曲线
- **路由**：`Home.vue` 新增入口按钮"面试历史"→ `/history`

