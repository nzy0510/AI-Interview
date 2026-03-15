<template>
  <div class="home-container">
    <el-container>
      <el-header>
        <div class="logo">🤖 AI 模拟面试平台</div>
        <div class="header-right">
          <el-button plain size="small" @click="router.push('/history')">📊 面试历史</el-button>
          <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main>
        <div class="hero-section">
          <div class="hero-text">
            <h1>欢迎来到 AI 面试工坊</h1>
            <p>由大语言模型驱动，支持 RAG 知识库检索、语音交互和多维能力评估</p>
          </div>

          <el-card class="role-card" shadow="hover">
            <div class="card-title">选择面试岗位</div>
            <div class="role-btns">
              <div class="role-btn-wrap" @click="startInterview('Java后端开发')">
                <div class="role-icon">☕</div>
                <div class="role-name">Java 后端开发</div>
                <div class="role-desc">Spring Boot · JVM · 分布式 · 数据库</div>
              </div>
              <div class="role-btn-wrap" @click="startInterview('Web前端开发')">
                <div class="role-icon">⚡</div>
                <div class="role-name">Web 前端开发</div>
                <div class="role-desc">Vue · React · 性能优化 · 计算机网络</div>
              </div>
            </div>
          </el-card>

          <div class="feature-grid">
            <div class="feat">🎙️ 语音识别</div>
            <div class="feat">📊 六维能力雷达</div>
            <div class="feat">🤖 RAG 知识检索</div>
            <div class="feat">📈 成长曲线追踪</div>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const handleLogout = () => {
  localStorage.removeItem('token')
  ElMessage.success('已退出登录')
  router.push('/login')
}

const startInterview = (role) => {
  router.push({ path: '/interview', query: { role } })
}
</script>

<style scoped>
.home-container { height: 100vh; background: linear-gradient(145deg, #0f172a, #1e3a5f); display: flex; }
.el-container { width: 100%; display: flex; flex-direction: column; }

.el-header {
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(255,255,255,0.1);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28px;
  flex-shrink: 0;
}

.logo { font-size: 20px; font-weight: 700; letter-spacing: 0.5px; }
.header-right { display: flex; gap: 10px; }

.el-main { display: flex; justify-content: center; align-items: center; flex: 1; }

.hero-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  max-width: 700px;
  width: 100%;
  padding: 0 20px;
}

.hero-text { text-align: center; color: white; }
.hero-text h1 { font-size: 34px; font-weight: 800; margin: 0 0 10px; background: linear-gradient(90deg, #60a5fa, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.hero-text p { margin: 0; color: rgba(255,255,255,0.65); font-size: 15px; }

.role-card { border-radius: 20px; width: 100%; padding: 8px; border: 1px solid rgba(255,255,255,0.12); background: rgba(255,255,255,0.06); backdrop-filter: blur(12px); }
.card-title { color: rgba(255,255,255,0.8); font-size: 13px; text-align: center; margin-bottom: 20px; text-transform: uppercase; letter-spacing: 1px; }
.role-btns { display: flex; gap: 16px; }
.role-btn-wrap {
  flex: 1;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 14px;
  padding: 24px 20px;
  text-align: center;
  cursor: pointer;
  transition: all .25s ease;
  color: white;
}
.role-btn-wrap:hover { background: rgba(255,255,255,0.16); transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.3); }
.role-icon { font-size: 36px; margin-bottom: 10px; }
.role-name { font-size: 18px; font-weight: 700; margin-bottom: 6px; }
.role-desc { font-size: 12px; color: rgba(255,255,255,0.55); }

.feature-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; width: 100%; }
.feat { background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); border-radius: 10px; padding: 10px; text-align: center; color: rgba(255,255,255,0.7); font-size: 13px; }
</style>
