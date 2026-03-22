# AI 面试系统项目概述 (Project Overview)

## 1. 项目简介
本项目是一个基于 AI 技术的模拟面试平台，旨在通过人工智能技术辅助用户进行技术面试练习，涵盖 Java 后端、前端等多个技术领域。

## 2. 技术栈
### 后端 (Backend)
- **核心框架**: Spring Boot 2.x/3.x
- **持久层**: MyBatis-Plus
- **安全认证**: JWT (JSON Web Token)
- **数据库**: MySQL 8.0
- **AI 集成**: 预计集成大语言模型 (LLM) 进行面试问答处理
- **构建工具**: Maven

### 前端 (Frontend)
- **框架**: Vue.js 3
- **构建工具**: Vite
- **路由**: Vue Router
- **网络请求**: Axios
- **样式**: CSS (Vanilla/Scoped)

### 运维与部署
- **容器化**: Docker, Docker Compose
- **反向代理**: Nginx (用于前端部署)

## 3. 目录结构说明
- `/backend`: Java 后端源代码及配置
- `/frontend`: Vue 前端源代码
- `/mysql`: 数据库初始化脚本 (`init.sql`)
- `/workflow`: 项目开发与实施规划文档
- `/image`: 项目相关演示截图或资源

## 4. 核心功能模块
1.  **用户模块**: 支持用户登录与身份校验。
2.  **AI 面试模块**: 基于知识库（位于 `backend/src/main/resources/knowledge_base`）生成面试题目并与用户进行多轮对话。
3.  **历史记录模块**: 保存并展示用户的面试历程及 AI 评估。
4.  **知识库管理**: 预置了字节跳动等大厂的面试真题（Java、前端、行为面试）。

## 5. 快速启动建议
1.  使用 `docker-compose up -d` 快速启动 MySQL 容器。
2.  后端：导入 Maven 依赖并运行 `AiInterviewApplication`。
3.  前端：在 `frontend` 目录下运行 `npm install` 随后 `npm run dev`。

---
*生成日期: 2026年3月21日*
