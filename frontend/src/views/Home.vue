<template>
  <div class="home-container">
    <!-- Dynamic Background Canvas -->
    <canvas ref="bgCanvas" class="bg-canvas"></canvas>

    <el-container class="main-layout">
      <el-header class="glass-header">
        <div class="logo-wrap">
          <span class="logo-icon">◼</span>
          <span class="logo-text">Architectural Intelligence</span>
        </div>
        <div class="header-actions">
          <!-- 简历管理入口 -->
          <el-button class="glass-btn resume-btn" @click="openResumeManager">
            <el-icon><Document /></el-icon>
            {{ hasResume ? '📄 简历已就绪' : '上传简历' }}
          </el-button>
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
          <div class="badge">Architectural Intelligence</div>
          <h1 class="hero-title">
            让每一次面试，都像一次架构评审
          </h1>
          <p class="hero-subtitle">
            结合简历画像、岗位角色和即时追问，生成更接近真实技术面试的训练路径与能力反馈。
          </p>

          <div class="role-grid">
            <div class="role-glass-card" @click="startInterview('Java后端开发')">
              <div class="card-glow"></div>
              <div class="role-icon-box java">J</div>
              <h3>Java 后端开发</h3>
              <p>聚焦 Spring Boot、JVM、并发与服务治理</p>
              <div class="card-footer">立即开始 <el-icon><ArrowRight /></el-icon></div>
            </div>

            <div class="role-glass-card" @click="startInterview('Web前端开发')">
              <div class="card-glow"></div>
              <div class="role-icon-box web">W</div>
              <h3>Web 前端开发</h3>
              <p>聚焦 Vue、工程化、性能优化与交互设计</p>
              <div class="card-footer">立即开始 <el-icon><ArrowRight /></el-icon></div>
            </div>
          </div>

          <div class="feature-pills">
            <span class="pill"><el-icon><Microphone /></el-icon> 语音实时交互</span>
            <span class="pill"><el-icon><PieChart /></el-icon> 架构能力画像</span>
            <span class="pill"><el-icon><Connection /></el-icon> 专业题库</span>
            <span class="pill"><el-icon><VideoCamera /></el-icon> 视频面试</span>
          </div>
        </div>
      </el-main>
    </el-container>

    <!-- Resume Ask Dialog (选岗后弹出) -->
    <el-dialog v-model="showResumeDialog" title="面试准备" width="480" center :close-on-click-modal="false">
      <div class="resume-ask-content">
        <el-icon class="resume-icon"><Document /></el-icon>

        <!-- 已有简历时 -->
        <template v-if="hasResume">
          <h3 class="resume-title">检测到已有简历画像 ✅</h3>
          <p class="resume-desc">系统将自动使用上次上传的简历进行定制化面试，无需重复上传。</p>
          <el-button class="use-existing-btn" type="success" @click="useExistingResume">
            使用已有简历，选择面试模式
          </el-button>
          <div class="resume-divider"><span>或</span></div>
          <el-upload
            class="resume-upload"
            drag
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleResumeSuccess"
            :on-error="handleResumeError"
            :before-upload="beforeResumeUpload"
            accept=".pdf"
          >
            <div class="el-upload__text" v-if="!isParsing">
              <em>重新上传新简历</em>，覆盖旧画像
            </div>
            <div class="el-upload__text" v-else>
              <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
            </div>
          </el-upload>
        </template>

        <!-- 无简历时 -->
        <template v-else>
          <h3 class="resume-title">是否提供个人简历？</h3>
          <p class="resume-desc">系统将使用 AI 解析您的简历，为你生成专属画像，并对准项目经历发起定制化深度提问。</p>
          <el-upload
            class="resume-upload"
            drag
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleResumeSuccess"
            :on-error="handleResumeError"
            :before-upload="beforeResumeUpload"
            accept=".pdf"
          >
            <div class="el-upload__text" v-if="!isParsing">
              <em>点击上传 PDF 简历</em>，生成定制化画像
            </div>
            <div class="el-upload__text" v-else>
              <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
            </div>
          </el-upload>
        </template>

        <div class="resume-divider" v-if="!hasResume">
          <span>或</span>
        </div>

        <el-button class="skip-btn" plain @click="skipResumeAndSelectMode" v-if="!hasResume">
          暂无简历，直接选择文字 / 视频模式
        </el-button>
      </div>
    </el-dialog>

    <!-- Resume Manager Dialog (右上角管理入口) -->
    <el-dialog v-model="showResumeManager" title="简历管理" width="480" center>
      <div class="resume-ask-content">
        <el-icon class="resume-icon"><Document /></el-icon>
        <template v-if="hasResume">
          <h3 class="resume-title">当前简历画像 ✅</h3>
          <p class="resume-desc">你已有简历画像，开始面试时将自动使用，也可以上传新简历覆盖。</p>
          <div class="resume-manager-actions">
            <el-button type="primary" @click="showResumeManager = false; router.push({ path: '/resume' })">
              查看画像详情
            </el-button>
          </div>
          <div class="resume-divider"><span>更新简历</span></div>
        </template>
        <template v-else>
          <h3 class="resume-title">尚未上传简历</h3>
          <p class="resume-desc">上传简历后，面试时将自动使用 AI 定制化提问。</p>
        </template>
        <el-upload
          class="resume-upload"
          drag
          :action="uploadUrl"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleResumeManagerSuccess"
          :on-error="handleResumeError"
          :before-upload="beforeResumeUpload"
          accept=".pdf"
        >
          <div class="el-upload__text" v-if="!isParsing">
            <em>{{ hasResume ? '上传新简历覆盖' : '点击上传 PDF 简历' }}</em>
          </div>
          <div class="el-upload__text" v-else>
            <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
          </div>
        </el-upload>
      </div>
    </el-dialog>

    <!-- Mode Selection Dialog -->
    <el-dialog v-model="showModeDialog" title="选择面试模式" width="480" center :close-on-click-modal="false">
        <div class="mode-options">
          <div class="mode-card" @click="confirmMode('text')">
          <div class="mode-icon">T</div>
          <h3>文字模式</h3>
          <p>通过文字 / 语音转文字与 AI 交流，适合安静环境。</p>
          <el-tag type="info" size="small">经典模式</el-tag>
        </div>
        <div class="mode-card video" @click="confirmMode('video')">
          <div class="mode-icon">V</div>
          <h3>视频模式</h3>
          <p>开启摄像头面对面交流，AI 语音对话 + 情感分析。</p>
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
import { userKey } from '@/utils/auth'

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
const showResumeManager = ref(false)
const isParsing = ref(false)
const selectedRole = ref('')
const hasResume = ref(false)

const uploadUrl = (import.meta.env.VITE_API_BASE_URL || '') + '/api/resume/parse'

const uploadHeaders = ref({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
})

// 页面加载时检查是否已有简历
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

  checkExistingResume()
})

const checkExistingResume = async () => {
  // 先检查 localStorage（按用户隔离）
  if (localStorage.getItem(userKey('resume_analysis'))) {
    hasResume.value = true
    return
  }
  // 再静默检查后端
  try {
    const token = localStorage.getItem('token')
    const resp = await fetch(uploadUrl.replace('/parse', '/profile'), {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (resp.ok) {
      const result = await resp.json()
      if (result.code === 200 && result.data) {
        localStorage.setItem(userKey('resume_analysis'), JSON.stringify(result.data))
        hasResume.value = true
      }
    }
  } catch {}
}

const startInterview = (role) => {
  selectedRole.value = role
  showResumeDialog.value = true
}

const useExistingResume = () => {
  showResumeDialog.value = false
  showModeDialog.value = true
}

const openResumeManager = () => {
  showResumeManager.value = true
}

const beforeResumeUpload = (file) => {
  const isPdf = file.type === 'application/pdf'
  if (!isPdf) {
    ElMessage.error('只能上传 PDF 格式的简历！')
    return false
  }
  isParsing.value = true
  uploadHeaders.value = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
  return true
}

const handleResumeSuccess = (response) => {
  isParsing.value = false
  showResumeDialog.value = false
  if (response && response.code === 200) {
    if (response.data) {
      localStorage.setItem(userKey('resume_analysis'), JSON.stringify(response.data))
      hasResume.value = true
    }
    ElMessage.success('简历专属画像生成完毕！')
    router.push({ path: '/resume', query: { role: selectedRole.value } })
  } else {
    ElMessage.error('简历解析异常：' + (response?.msg || '未知错误'))
  }
}

const handleResumeManagerSuccess = (response) => {
  isParsing.value = false
  if (response && response.code === 200) {
    if (response.data) {
      localStorage.setItem(userKey('resume_analysis'), JSON.stringify(response.data))
      hasResume.value = true
    }
    showResumeManager.value = false
    ElMessage.success('简历画像已更新！')
    router.push({ path: '/resume' })
  } else {
    ElMessage.error('简历解析异常：' + (response?.msg || '未知错误'))
  }
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
  const isTailored = hasResume.value ? 'true' : 'false'
  if (mode === 'video') {
    router.push({ path: '/video-interview', query: { role: selectedRole.value, isTailored } })
  } else {
    router.push({ path: '/interview', query: { role: selectedRole.value, isTailored } })
  }
}
</script>

<style scoped>
.home-container {
  position: relative;
  min-height: 100vh;
  width: 100vw;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(247, 249, 251, 0.9), rgba(247, 249, 251, 0.98)),
    radial-gradient(circle at top left, rgba(58, 56, 139, 0.08), transparent 30%),
    radial-gradient(circle at top right, rgba(4, 76, 69, 0.06), transparent 28%),
    #f7f9fb;
}

.bg-canvas {
  position: fixed;
  inset: 0;
  z-index: 0;
  opacity: 0.22;
  pointer-events: none;
}

.main-layout {
  position: relative;
  z-index: 1;
  min-height: 100vh;
}

.glass-header {
  height: 76px !important;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 0 32px;
  background: rgba(250, 249, 245, 0.9);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid #e8e6dc;
}

.logo-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.logo-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: linear-gradient(135deg, #3a388b, #5250a4);
  font-size: 16px;
  flex: 0 0 auto;
}

.logo-text {
  font-size: 18px;
  font-weight: 800;
  color: #141413;
  letter-spacing: 0;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.glass-btn {
  background: #ffffff !important;
  border: 1px solid #e8e6dc !important;
  color: #3d3d3a !important;
  backdrop-filter: blur(4px);
  box-shadow: 0 0 0 1px rgba(209, 207, 197, 0.65) inset;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.glass-btn:hover {
  border-color: #3a388b !important;
  transform: translateY(-1px);
}

.hero-main {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 36px 28px 42px;
}

.hero-content {
  width: min(1120px, 100%);
  text-align: left;
  display: grid;
  gap: 28px;
  padding: 28px;
  background: rgba(250, 249, 245, 0.9);
  border: 1px solid #e8e6dc;
  border-radius: 28px;
  box-shadow: 0 18px 50px rgba(20, 20, 19, 0.06);
}

.badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(58, 56, 139, 0.08);
  border: 1px solid rgba(58, 56, 139, 0.14);
  color: #3a388b;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.hero-title {
  font-size: clamp(34px, 4vw, 56px);
  font-weight: 800;
  color: #141413;
  line-height: 1.08;
  margin: 0;
  letter-spacing: 0;
  max-width: 760px;
}

.gradient-text {
  color: #3a388b;
}

.hero-subtitle {
  font-size: 17px;
  color: #5e5d59;
  max-width: 720px;
  margin: 0;
  line-height: 1.68;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 0;
}

.role-glass-card {
  position: relative;
  background: #ffffff;
  border: 1px solid #e8e6dc;
  border-radius: 22px;
  padding: 24px 22px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  box-shadow: 0 12px 32px rgba(20, 20, 19, 0.05);
}

.role-glass-card:hover {
  transform: translateY(-4px);
  border-color: #c3c0ff;
  box-shadow: 0 18px 44px rgba(58, 56, 139, 0.1);
}

.card-glow {
  display: none;
}

.role-icon-box {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 18px;
  color: #ffffff;
}

.java {
  background: linear-gradient(135deg, #3a388b, #5250a4);
}

.web {
  background: linear-gradient(135deg, #004c45, #00665d);
}

.role-glass-card h3 {
  color: #141413;
  font-size: 20px;
  font-weight: 800;
  margin: 0 0 8px;
}

.role-glass-card p {
  color: #5e5d59;
  font-size: 14px;
  margin: 0 0 18px;
  line-height: 1.6;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #3a388b;
  font-size: 14px;
  font-weight: 700;
  opacity: 0.92;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.role-glass-card:hover .card-footer {
  transform: translateX(4px);
  opacity: 1;
}

.feature-pills {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 12px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #5e5d59;
  font-size: 14px;
  padding: 10px 14px;
  background: #faf9f5;
  border: 1px solid #e8e6dc;
  border-radius: 999px;
}

.pill .el-icon {
  color: #3a388b;
}

.mode-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 10px 0;
}

.mode-card {
  padding: 22px 18px;
  text-align: center;
  border-radius: 18px;
  border: 1px solid #e8e6dc;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  background: #faf9f5;
}

.mode-card:hover {
  border-color: #c3c0ff;
  transform: translateY(-4px);
  box-shadow: 0 16px 34px rgba(58, 56, 139, 0.1);
}

.mode-card.video {
  background: #f4fbf9;
}

.mode-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
  font-size: 22px;
  font-weight: 800;
  background: #f0eee6;
  color: #3a388b;
}

.mode-card.video .mode-icon {
  color: #004c45;
  background: rgba(4, 76, 69, 0.12);
}

.mode-card h3 {
  font-size: 18px;
  font-weight: 800;
  color: #141413;
  margin: 0 0 8px;
}

.mode-card p {
  font-size: 13px;
  color: #5e5d59;
  line-height: 1.6;
  margin: 0 0 12px;
}

.resume-ask-content {
  text-align: center;
  padding: 10px 18px 20px;
}

.resume-icon {
  font-size: 48px;
  color: #3a388b;
  margin-bottom: 16px;
}

.resume-title {
  font-size: 20px;
  font-weight: 800;
  color: #141413;
  margin-bottom: 12px;
}

.resume-desc {
  font-size: 14px;
  color: #5e5d59;
  line-height: 1.6;
  margin-bottom: 20px;
}

.resume-upload {
  margin-bottom: 16px;
}

.resume-upload :deep(.el-upload-dragger) {
  border-color: #d1cfc5;
  background: #faf9f5;
  border-radius: 16px;
  transition: all 0.2s ease;
}

.resume-upload :deep(.el-upload-dragger:hover) {
  border-color: #3a388b;
  background: #f4f3ff;
}

.resume-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
  color: #b0aea5;
  font-size: 13px;
}

.resume-divider::before,
.resume-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e8e6dc;
}

.resume-divider span {
  padding: 0;
}

.skip-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-weight: 700;
}

.resume-btn.glass-btn {
  border-color: #c3c0ff !important;
}

.use-existing-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-weight: 700;
  font-size: 15px;
}

.resume-manager-actions {
  margin-bottom: 16px;
}

.resume-manager-actions .el-button {
  border-radius: 12px;
  padding: 12px 24px;
}

:deep(.el-dialog) {
  border-radius: 20px;
  overflow: hidden;
}

:deep(.el-dialog__header) {
  margin-right: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0eee6;
}

:deep(.el-dialog__body) {
  background: #faf9f5;
}

:deep(.el-upload-dragger) {
  box-shadow: 0 0 0 1px rgba(209, 207, 197, 0.4) inset;
}

@media (max-width: 1100px) {
  .hero-content {
    padding: 24px;
  }

  .role-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .glass-header {
    height: auto !important;
    padding: 16px 20px;
    flex-wrap: wrap;
  }

  .header-actions {
    width: 100%;
  }

  .hero-main {
    padding: 20px;
  }

  .hero-content {
    gap: 20px;
    border-radius: 24px;
  }

  .feature-pills,
  .mode-options {
    width: 100%;
  }

  .feature-pills {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 640px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-content {
    padding: 18px;
  }

  .role-glass-card {
    padding: 20px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
