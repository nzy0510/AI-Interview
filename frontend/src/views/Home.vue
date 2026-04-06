<template>
  <div class="home-container">
    <!-- Dynamic Background Canvas -->
    <canvas ref="bgCanvas" class="bg-canvas"></canvas>

    <el-container class="main-layout">
      <el-header class="glass-header">
        <div class="logo-wrap">
          <span class="logo-icon">🤖</span>
          <span class="logo-text">AI Interview Master</span>
        </div>
        <div class="header-actions">
          <el-button class="glass-btn" @click="router.push('/history')">
            <el-icon><Histogram /></el-icon> 面试历史
          </el-button>
          <el-button type="danger" size="small" @click="handleLogout" circle title="退出登录">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </el-header>

      <el-main class="hero-main">
        <div class="hero-content">
          <div class="badge">Next-Gen Interview Prep</div>
          <h1 class="hero-title">
            打破传统，开启 <span class="gradient-text">AI-Interview</span> 新时代
          </h1>
          <p class="hero-subtitle">
            融合深度语义检索 (RAG) 与大语言模型，为您提供最真实的岗位实战演练与精准的能力画像评估。
          </p>

          <div class="role-grid">
            <div class="role-glass-card" @click="startInterview('Java后端开发')">
              <div class="card-glow"></div>
              <div class="role-icon-box java">☕</div>
              <h3>Java 后端开发</h3>
              <p>精通 Spring Cloud, JVM, 性能调优</p>
              <div class="card-footer">立即开始 <el-icon><ArrowRight /></el-icon></div>
            </div>

            <div class="role-glass-card" @click="startInterview('Web前端开发')">
              <div class="card-glow"></div>
              <div class="role-icon-box web">⚡</div>
              <h3>Web 前端开发</h3>
              <p>深挖 Vue/React 架构与现代 Web 性能</p>
              <div class="card-footer">立即开始 <el-icon><ArrowRight /></el-icon></div>
            </div>
          </div>

          <div class="feature-pills">
            <span class="pill"><el-icon><Microphone /></el-icon> 语音实时交互</span>
            <span class="pill"><el-icon><PieChart /></el-icon> 六维能力画像</span>
            <span class="pill"><el-icon><Connection /></el-icon> RAG 专业题库</span>
            <span class="pill"><el-icon><VideoCamera /></el-icon> 视频面试</span>
          </div>
        </div>
      </el-main>
    </el-container>

    <!-- Resume Ask Dialog -->
    <el-dialog v-model="showResumeDialog" title="面试准备" width="480" center :close-on-click-modal="false">
      <div class="resume-ask-content">
        <el-icon class="resume-icon"><Document /></el-icon>
        <h3 class="resume-title">是否提供个人简历？</h3>
        <p class="resume-desc">系统将使用 AI 解析您的简历，为您生成**专属画像**并对准您的项目发起**定制化深度提问**，极大还原真实大厂面试场景。</p>
        
        <!-- Upload Component -->
        <el-upload
          class="resume-upload"
          drag
          action="http://localhost:8080/api/resume/parse"
          :show-file-list="false"
          :on-success="handleResumeSuccess"
          :on-error="handleResumeError"
          :before-upload="beforeResumeUpload"
          accept=".pdf"
        >
          <div class="el-upload__text" v-if="!isParsing">
            <em>点击上传 PDF 简历</em>，生成定制化拷问
          </div>
          <div class="el-upload__text" v-else>
            <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
          </div>
        </el-upload>

        <div class="resume-divider">
          <span>或</span>
        </div>

        <el-button class="skip-btn" plain @click="skipResumeAndSelectMode">
          暂无简历，体验文字 or 视频模式
        </el-button>
      </div>
    </el-dialog>

    <!-- Mode Selection Dialog -->
    <el-dialog v-model="showModeDialog" title="选择面试模式" width="480" center :close-on-click-modal="false">
      <div class="mode-options">
        <div class="mode-card" @click="confirmMode('text')">
          <div class="mode-icon">📝</div>
          <h3>文字模式</h3>
          <p>通过文字/语音转文字与 AI 交流，适合安静环境</p>
          <el-tag type="info" size="small">经典模式</el-tag>
        </div>
        <div class="mode-card video" @click="confirmMode('video')">
          <div class="mode-icon">📹</div>
          <h3>视频模式</h3>
          <p>开启摄像头面对面交流，AI 语音对话 + 情感分析</p>
          <el-tag type="success" size="small">进阶模式</el-tag>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Histogram, SwitchButton, ArrowRight, Microphone, PieChart, Connection, VideoCamera, Document, Loading } from '@element-plus/icons-vue'

const router = useRouter()
const bgCanvas = ref(null)

// --- Particle Background Logic ---
let ctx, width, height, particles = []
const PARTICLE_COUNT = 80
const MOUSE_RADIUS = 150
let mouse = { x: -1000, y: -1000 }

class Particle {
  constructor() {
    this.init()
  }
  init() {
    this.x = Math.random() * width
    this.y = Math.random() * height
    this.vx = (Math.random() - 0.5) * 0.5
    this.vy = (Math.random() - 0.5) * 0.5
    this.radius = Math.random() * 2 + 1
  }
  update() {
    this.x += this.vx
    this.y += this.vy
    if (this.x < 0 || this.x > width) this.vx *= -1
    if (this.y < 0 || this.y > height) this.vy *= -1

    // Interaction
    const dx = mouse.x - this.x
    const dy = mouse.y - this.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    if (dist < MOUSE_RADIUS) {
      const force = (MOUSE_RADIUS - dist) / MOUSE_RADIUS
      this.x -= dx * force * 0.03
      this.y -= dy * force * 0.03
    }
  }
  draw() {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2)
    ctx.fillStyle = 'rgba(16, 185, 129, 0.4)' // Emerald particles
    ctx.fill()
  }
}

const resize = () => {
  width = bgCanvas.value.width = window.innerWidth
  height = bgCanvas.value.height = window.innerHeight
}

const animate = () => {
  ctx.clearRect(0, 0, width, height)
  particles.forEach(p => {
    p.update()
    p.draw()
  })

  // Draw lines
  ctx.strokeStyle = 'rgba(16, 185, 129, 0.08)' // Emerald lines
  ctx.lineWidth = 1
  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const dx = particles[i].x - particles[j].x
      const dy = particles[i].y - particles[j].y
      const dist = Math.sqrt(dx * dx + dy * dy)
      if (dist < 150) {
        ctx.beginPath()
        ctx.moveTo(particles[i].x, particles[i].y)
        ctx.lineTo(particles[j].x, particles[j].y)
        ctx.stroke()
      }
    }
  }
  requestAnimationFrame(animate)
}

onMounted(() => {
  ctx = bgCanvas.value.getContext('2d')
  resize()
  window.addEventListener('resize', resize)
  window.addEventListener('mousemove', e => {
    mouse.x = e.clientX
    mouse.y = e.clientY
  })

  for (let i = 0; i < PARTICLE_COUNT; i++) particles.push(new Particle())
  animate()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
})

const handleLogout = () => {
  localStorage.removeItem('token')
  ElMessage.success('已安全退出')
  router.push('/login')
}

const showModeDialog = ref(false)
const showResumeDialog = ref(false)
const isParsing = ref(false)
const selectedRole = ref('')

const startInterview = (role) => {
  selectedRole.value = role
  showResumeDialog.value = true
}

const beforeResumeUpload = (file) => {
  const isPdf = file.type === 'application/pdf'
  if (!isPdf) {
    ElMessage.error('只能上传 PDF 格式的简历！')
    return false
  }
  isParsing.value = true
  return true
}

const handleResumeSuccess = (response) => {
  isParsing.value = false
  showResumeDialog.value = false
  // 将解析得到的 AI 定制结果缓存到 localStorage
  localStorage.setItem('resume_analysis', JSON.stringify(response))
  ElMessage.success('简历专属画像生成完毕！')
  // 跳转到简历可视化页面
  router.push({ path: '/resume', query: { role: selectedRole.value } })
}

const handleResumeError = (err) => {
  isParsing.value = false
  ElMessage.error('简历解析失败，请检查文件后重试！')
}

const skipResumeAndSelectMode = () => {
  showResumeDialog.value = false
  showModeDialog.value = true
}

const confirmMode = (mode) => {
  showModeDialog.value = false
  if (mode === 'video') {
    router.push({ path: '/video-interview', query: { role: selectedRole.value } })
  } else {
    router.push({ path: '/interview', query: { role: selectedRole.value } })
  }
}
</script>

<style scoped>
.home-container {
  position: relative;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: #020617; /* Very deep dark navy */
}

.bg-canvas {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
}

.main-layout {
  position: relative;
  z-index: 2;
  height: 100%;
}

.glass-header {
  height: 70px !important;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logo-icon { font-size: 24px; }
.logo-text {
  font-size: 20px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #fff 0%, #94a3b8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.glass-btn {
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: #cbd5e1 !important;
  backdrop-filter: blur(4px);
  transition: all 0.3s;
}
.glass-btn:hover {
  background: rgba(255, 255, 255, 0.1) !important;
  border-color: #10b981 !important; /* Emerald */
  color: #fff !important;
}

.hero-main {
  display: flex;
  justify-content: center;
  align-items: center;
  padding-bottom: 50px;
}

.hero-content {
  text-align: center;
  max-width: 840px;
}

.badge {
  display: inline-block;
  padding: 6px 14px;
  background: rgba(16, 185, 129, 0.1); /* Emerald */
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 99px;
  color: #10b981;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 24px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.hero-title {
  font-size: 48px;
  font-weight: 850;
  color: #fff;
  line-height: 1.2;
  margin-bottom: 20px;
  letter-spacing: -1px;
}
.gradient-text {
  background: linear-gradient(90deg, #10b981, #0ea5e9, #f59e0b); /* Emerald -> Cyan -> Amber */
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: 18px;
  color: #94a3b8;
  max-width: 600px;
  margin: 0 auto 48px;
  line-height: 1.6;
}

.role-grid {
  display: flex;
  gap: 24px;
  margin-bottom: 48px;
}

.role-glass-card {
  position: relative;
  flex: 1;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  padding: 36px 28px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.23, 1, 0.32, 1);
  backdrop-filter: blur(12px);
}

.role-glass-card:hover {
  transform: translateY(-8px);
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(16, 185, 129, 0.4);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
}

.card-glow {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle at var(--mx, 50%) var(--my, 50%), rgba(16, 185, 129, 0.1) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.3s;
}
.role-glass-card:hover .card-glow { opacity: 1; }

.role-icon-box {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  margin-bottom: 24px;
}
.java { background: linear-gradient(135deg, rgba(245, 158, 11, 0.2), rgba(217, 119, 6, 0.1)); border: 1px solid rgba(245, 158, 11, 0.3); } /* Amber */
.web { background: linear-gradient(135deg, rgba(6, 182, 212, 0.2), rgba(8, 145, 178, 0.1)); border: 1px solid rgba(6, 182, 212, 0.3); } /* Cyan */

.role-glass-card h3 {
  color: #f8fafc;
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 8px;
}
.role-glass-card p {
  color: #64748b;
  font-size: 14px;
  margin-bottom: 24px;
  line-height: 1.5;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #10b981; /* Emerald */
  font-size: 14px;
  font-weight: 600;
  opacity: 0.8;
  transition: transform 0.3s;
}
.role-glass-card:hover .card-footer { transform: translateX(4px); opacity: 1; }

.feature-pills {
  display: flex;
  justify-content: center;
  gap: 24px;
}
.pill {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 14px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
}
.pill .el-icon { color: #475569; }

/* Mode Selection Dialog */
.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 10px 0;
}
.mode-card {
  padding: 28px 20px;
  text-align: center;
  border-radius: 16px;
  border: 2px solid #e5e7eb;
  cursor: pointer;
  transition: all 0.3s;
  background: #f9fafb;
}
.mode-card:hover {
  border-color: #409EFF;
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
}
.mode-card.video {
  border-color: #d1fae5;
  background: #f0fdf4;
}
.mode-card.video:hover {
  border-color: #67C23A;
  box-shadow: 0 8px 24px rgba(103, 194, 58, 0.15);
}
.mode-icon {
  font-size: 40px;
  margin-bottom: 12px;
}
.mode-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}
.mode-card p {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  margin-bottom: 12px;
}

/* Resume Ask Dialog CSS */
.resume-ask-content {
  text-align: center;
  padding: 10px 20px 20px;
}
.resume-icon {
  font-size: 48px;
  color: #10b981;
  margin-bottom: 16px;
}
.resume-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
}
.resume-desc {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 24px;
}
.resume-upload {
  margin-bottom: 20px;
}
.resume-upload :deep(.el-upload-dragger) {
  border-color: rgba(16, 185, 129, 0.3);
  background: #f0fdf4;
  transition: all 0.3s;
}
.resume-upload :deep(.el-upload-dragger:hover) {
  border-color: #10b981;
  background: #d1fae5;
}
.resume-divider {
  display: flex;
  align-items: center;
  margin: 20px 0;
  color: #cbd5e1;
  font-size: 13px;
}
.resume-divider::before, .resume-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e2e8f0;
}
.resume-divider span {
  padding: 0 10px;
}
.skip-btn {
  width: 100%;
  padding: 20px;
  border-radius: 12px;
  font-weight: 600;
}
</style>
