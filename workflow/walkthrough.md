# 🌟 AI 模拟面试系统 (AI Mock Interview System) 重建与踩坑记录

恭喜！在经历了一系列从零到一的架构搭建、前后端分离重构以及对 Spring 和大模型流式通信底层的反复“折腾”之后，我们的**AI 专业模拟面试系统**终于迎来了完美的终极版本！

在这个文档里，我们将回顾一下我们是如何一步步在这个项目中大刀阔斧地重构，并最终实现这个惊艳效果的。

## 🎯 我们实现了什么？

1. **前后端解耦重构**：抛弃了最初笨重的 JSP 和耦合严重的 Servlet，将应用成功划分为现代化、工程化、可维护的 `Vue 3 + Vite` 纯净前端和 `Spring Boot 3 + MyBatis-Plus` 强劲后端。
2. **多岗位面试大厅**：前端不再是毫无生机的白板，而是拥有高颜值卡片设计的面试岗位挑选大厅。
3. **沉浸式 AI 聊天界面**：打造了类似 ChatGPT 网页版的对话式面板，支持多轮上下文记忆，支持响应迅速的流式输出！
4. **AI 智能结课评估**：每次面试结束后，后台的 `OpenAiChatModel` 会自动对你的所有历史表现进行点评，打出一个具体的百分制分数。

## 🧗‍♂️ 踩过的坑与技术选型亮点

### 1. 致命的 `SseEmitter` 序列化风暴 (HttpMessageNotWritableException)
这是我们在开发中最痛苦、也最有收获的一战。
- **问题症状**：当使用 `dev.langchain4j` 的大模型对接 Spring 的 `SseEmitter` 往前端推送流式字符时，总是收到空字符，而后端疯狂报错“无转换器处理类”。
- **病因探索**：Spring Boot 在处理 `text/event-stream` 时，默认会调用内部所有的 MessageConverters 进行尝试解析。如果我们直接调用 `emitter.send(token)` 把原生字符抛出去，Spring 会因为它不认识这是什么复杂对象而宕机。
- **最终杀招**：在试了强行注入字符集、直接采用底层 `HttpServletResponse.getWriter()` 手动写数据均因为各种底层架构差异（如并发场景下的 NullPointerException）失败后。我们选择了**最精妙的战术：显式包装为 JSON**！
  - 后台在循环推流时：不发单纯的汉字，而是将其放入字典 `Map<String, String>`，再由 `Fastjson2` 转化为标准的 JSON String 抛出给 SseEmitter。
  - 这样 Spring 就会把它当作纯净干净的字符串安全放行！

### 2. Vue 的按字解析大法
在前端捕获那场“字符流雨”时：
```javascript
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  if (data.content) {
    messageList.value[index].content += data.content;
  }
}
```
结合 Vue 的 `ref` 双向绑定，页面上的字符就能像魔法一样一颗颗顺滑地蹦出来！

### 3. 被“暗杀”的数据：Axios 拦截器
在做最后“生成报告”功能时，哪怕后端写得再完美，Vue 也是读出 `undefined`。
最后发现竟然是我们早先配置在 [request.js](file:///e:/Java-web/interview/frontend/src/utils/request.js) 中的**全局拦截器**把 Axios 返回最外层的 `response.data` 给直接扒掉了。幸好及时发现了这个乌龙，成功接入了 `res.score` 取值。

---

## 🚀 如何运行项目 (How to Run)

### 1. 解决端口占用 (修复 IDEA 报错)
如果你在 IDEA 中运行时看到 `Port 8080 was already in use`，通常是因为后台已有进程（或是之前的调试进程）占用了该端口。
**解决方法**：
- **我刚才已经在后台帮你清理了残留进程**，你现在可以直接在 IDEA 中点击 **Run** 尝试重新启动。
- 如果以后再次遇到，可以打开终端输入 `netstat -ano | findstr :8080` 找到 PID，然后用 `taskkill /F /PID <PID>` 结束它。

### 2. 标准启动流程
- **后端 (Backend)**：在 IDEA 中找到 [AiInterviewApplication.java](file:///e:/Java-web/interview/backend/src/main/java/com/interview/AiInterviewApplication.java)，右键选择 **Run**。
- **前端 (Frontend)**：进入 `frontend` 目录，执行 `npm run dev`。

---

## 🧠 核心架构：RAG 语义检索与“岗位隔离”
目前的系统已通过 **Langchain4j** 升级为 RAG（检索增强生成）模式，并实现了行业级的**岗位知识隔离**：
1.  **目录层级隔离**：知识库划分为 [java](file:///e:/Java-web/interview/backend/src/main/java/com/interview/entity/User.java), `frontend`, `common` 三大子文件夹。
2.  **元数据自动打标**：[DataLoadingService](file:///e:/Java-web/interview/backend/src/main/java/com/interview/service/DataLoadingService.java#24-99) 在启动时会自动识别子文件夹，为每个知识片段注入 `category` 标签。
3.  **精准检索过滤**：在面试过程中，系统会根据你选择的岗位，动态构建 `Metadata Filter`。
    - 如果你面 Java，它只会看 [java](file:///e:/Java-web/interview/backend/src/main/java/com/interview/entity/User.java) 和 `common` 文件夹。
    - 如果你面前端，它只会看 `frontend` 和 `common` 文件夹。
    - **彻底解决了“牛头不对马嘴”的串台问题！**

## 🎙️ 语音黑科技：多维表达评估系统
为了进一步模拟真实感，系统现已集成**语音面试交互**功能：
1.  **实时 STT 识别**：通过 `Web Speech API` 实现毫秒级语音转文字。
2.  **可视化声波图**：前端 `Canvas` 实现在录音时展示动态频率波形，增强交互反馈。
3.  **表达力分析 (Metrics)**：
    - **语速监控**：系统会自动计算你的平均 `WPM` (每分钟字数)。
    - **心理画像**：AI 将基于你的语速、停顿和回答逻辑，评估你的**自信度**与**表达条理性**。
4.  **可视化评估报告**：面试结束后的报告中将包含专门的“表达表现”评价指标。

无论如何，这都是一场漂亮的技术仗！再次祝贺！🎉
