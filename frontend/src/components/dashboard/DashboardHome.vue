<template>
  <div class="dashboard-home">
    <section class="hero-slab">
      <div class="hero-copy">
        <div class="eyebrow">Architectural Intelligence</div>
        <div class="hero-headline">
          <h1>{{ dashboard.user.name }}</h1>
          <p>把面试拆成可以演练、复盘、再优化的工作台。</p>
        </div>
        <p class="hero-description">
          {{ dashboard.user.status }}
        </p>

        <div class="hero-actions">
          <el-button type="primary" class="primary-cta" @click="goSetup">
            <el-icon><Operation /></el-icon>
            {{ dashboard.recommendation.primaryAction }}
          </el-button>
          <el-button class="secondary-cta" @click="goTextInterview">
            <el-icon><Document /></el-icon>
            文字面试
          </el-button>
          <el-button class="secondary-cta" @click="goVideoInterview">
            <el-icon><VideoCamera /></el-icon>
            视频面试
          </el-button>
          <el-button class="ghost-cta" @click="openResumeManager">
            <el-icon><TrendCharts /></el-icon>
            简历管理
          </el-button>
        </div>
      </div>

      <div class="hero-panel">
        <div class="status-card">
          <div class="status-top">
            <span class="status-label">最近活跃</span>
            <span class="status-value">{{ dashboard.user.lastActive }}</span>
          </div>
          <div class="status-title">{{ dashboard.user.title }}</div>
          <div class="status-note">{{ dashboard.recommendation.description }}</div>
          <div class="status-path">{{ dashboard.recommendation.pathHint }}</div>
        </div>

        <div class="overview-grid">
          <div
            v-for="item in dashboard.overview"
            :key="item.label"
            class="overview-card"
            :data-tone="item.tone"
          >
            <span class="overview-label">{{ item.label }}</span>
            <div class="overview-value">
              {{ item.value }}<span v-if="item.unit" class="overview-unit">{{ item.unit }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="dashboard-grid">
      <div class="dashboard-main">
        <div class="section-block">
          <div class="section-head">
            <div>
              <div class="section-kicker">推荐下一步</div>
              <h2>{{ dashboard.recommendation.title }}</h2>
            </div>
            <el-tag type="info" effect="plain">待接入</el-tag>
          </div>

          <div class="recommendation-shell">
            <div class="recommendation-copy">
              <p>{{ dashboard.recommendation.description }}</p>
              <div class="recommendation-actions">
                <el-button type="primary" @click="goSetup">
                  进入面试准备
                </el-button>
                <el-button @click="goTextInterview">
                  直接进入文字面试
                </el-button>
                <el-button @click="goVideoInterview">
                  直接进入视频面试
                </el-button>
              </div>
            </div>

            <div class="route-cards">
              <button
                v-for="route in routePresets"
                :key="route.title"
                class="route-card"
                type="button"
                @click="openRoleFlow(route.role)"
              >
                <div class="route-top">
                  <span class="route-badge">{{ route.badge }}</span>
                  <span class="route-state">推荐路径</span>
                </div>
                <h3>{{ route.title }}</h3>
                <p>{{ route.description }}</p>
                <div class="route-footer">
                  <span>沿用该路径</span>
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </button>
            </div>
          </div>
        </div>

        <div class="section-block">
          <div class="section-head">
            <div>
              <div class="section-kicker">最近面试</div>
              <h2>最近记录和结果摘要</h2>
            </div>
          </div>

          <div class="recent-list">
            <article
              v-for="item in dashboard.recentInterviews"
              :key="item.title"
              class="recent-item"
            >
              <div class="recent-main">
                <div class="recent-title">{{ item.title }}</div>
                <div class="recent-meta">
                  <el-tag size="small" effect="plain">{{ item.tag }}</el-tag>
                  <span>{{ item.time }}</span>
                </div>
              </div>
              <div class="recent-result">{{ item.result }}</div>
            </article>
          </div>
        </div>

        <div class="section-block">
          <div class="section-head">
            <div>
              <div class="section-kicker">预留模块</div>
              <h2>职业路径与后续功能位</h2>
            </div>
          </div>

          <div class="roadmap-grid">
            <article
              v-for="item in dashboard.roadmap"
              :key="item.title"
              class="roadmap-item"
            >
              <div class="roadmap-status">{{ item.status }}</div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
            </article>
          </div>
        </div>
      </div>

      <aside class="dashboard-aside">
        <div class="aside-block mentor-block">
          <div class="section-kicker">AI Mentor</div>
          <h2>{{ dashboard.mentor.title }}</h2>
          <p>{{ dashboard.mentor.summary }}</p>
          <div class="mentor-note">
            <el-icon><Finished /></el-icon>
            <span>{{ dashboard.mentor.nextFocus }}</span>
          </div>
        </div>

        <div class="aside-block maturity-block">
          <div class="section-kicker">能力成熟度</div>
          <h2>当前状态概览</h2>
          <div class="maturity-list">
            <div v-for="item in dashboard.maturity" :key="item.label" class="maturity-item">
              <div class="maturity-top">
                <span>{{ item.label }}</span>
                <strong>{{ item.score }}<em>/100</em></strong>
              </div>
              <el-progress :percentage="item.score" :show-text="false" />
              <div class="maturity-delta">{{ item.delta }} 本周</div>
            </div>
          </div>
        </div>

        <div class="aside-block skills-block">
          <div class="section-kicker">技能卡</div>
          <h2>核心能力成熟度</h2>
          <div class="skill-list">
            <div v-for="skill in dashboard.skills" :key="skill.name" class="skill-item">
              <div class="skill-meta">
                <span>{{ skill.name }}</span>
                <strong>{{ skill.value }}%</strong>
              </div>
              <div class="skill-bar">
                <div class="skill-fill" :style="{ width: `${skill.value}%` }" />
              </div>
            </div>
          </div>
        </div>

        <div class="aside-block action-block">
          <div class="section-kicker">快捷操作</div>
          <h2>常用入口</h2>
          <div class="shortcut-list">
            <button
              v-for="shortcut in dashboard.shortcuts"
              :key="shortcut.label"
              type="button"
              class="shortcut-item"
              @click="runShortcut(shortcut.action)"
            >
              <div>
                <strong>{{ shortcut.label }}</strong>
                <span>{{ shortcut.hint }}</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </aside>
    </section>

    <el-dialog v-model="showResumeDialog" title="面试准备" width="480" center :close-on-click-modal="false">
      <div class="dialog-panel">
        <el-icon class="dialog-icon"><Document /></el-icon>

        <template v-if="hasResume">
          <h3>检测到已有简历画像</h3>
          <p>系统将复用上次上传的简历进行定制化面试，也可以重新上传覆盖。</p>
          <el-button type="success" class="dialog-primary" @click="useExistingResume">
            使用已有简历，选择面试模式
          </el-button>
          <div class="dialog-divider"><span>或</span></div>
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

        <template v-else>
          <h3>是否提供个人简历？</h3>
          <p>系统会先解析简历，再结合角色和模式生成更贴近真实面试的追问。</p>
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

        <div class="dialog-divider" v-if="!hasResume">
          <span>或</span>
        </div>

        <el-button v-if="!hasResume" class="dialog-secondary" plain @click="skipResumeAndSelectMode">
          暂无简历，直接选择文字 / 视频模式
        </el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="showResumeManager" title="简历管理" width="480" center>
      <div class="dialog-panel">
        <el-icon class="dialog-icon"><TrendCharts /></el-icon>
        <template v-if="hasResume">
          <h3>当前简历画像已就绪</h3>
          <p>面试时会自动复用该画像，也可以上传新的简历覆盖。</p>
          <el-button type="primary" class="dialog-primary" @click="goResumePage">
            查看画像详情
          </el-button>
          <div class="dialog-divider"><span>更新简历</span></div>
        </template>
        <template v-else>
          <h3>尚未上传简历</h3>
          <p>上传简历后，面试时将自动使用 AI 定制化提问。</p>
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

    <el-dialog v-model="showModeDialog" title="选择面试模式" width="480" center :close-on-click-modal="false">
      <div class="mode-options">
        <button class="mode-card" type="button" @click="confirmMode('text')">
          <div class="mode-icon">T</div>
          <h3>文字模式</h3>
          <p>通过文字 / 语音转文字与 AI 交流，适合安静环境。</p>
          <el-tag type="info" size="small">经典模式</el-tag>
        </button>
        <button class="mode-card video" type="button" @click="confirmMode('video')">
          <div class="mode-icon">V</div>
          <h3>视频模式</h3>
          <p>开启摄像头面对面交流，AI 语音对话 + 情感分析。</p>
          <el-tag type="success" size="small">进阶模式</el-tag>
        </button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Document,
  Finished,
  Loading,
  Operation,
  TrendCharts,
  VideoCamera
} from '@element-plus/icons-vue'
import { dashboardMock as dashboard } from '@/mock/dashboard'
import { userKey } from '@/utils/auth'

const router = useRouter()

const showModeDialog = ref(false)
const showResumeDialog = ref(false)
const showResumeManager = ref(false)
const isParsing = ref(false)
const selectedRole = ref('')
const hasResume = ref(false)
const resumeProfile = ref(null)

const uploadUrl = `${import.meta.env.VITE_API_BASE_URL || ''}/api/resume/parse`
const uploadHeaders = ref({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
})

const routePresets = [
  {
    title: 'Java 后端开发',
    badge: '后端',
    role: 'Java后端开发',
    description: '从项目经历、分布式、JVM 和服务治理切入，适合系统性复盘。'
  },
  {
    title: 'Web 前端开发',
    badge: '前端',
    role: 'Web前端开发',
    description: '从工程化、组件设计、性能优化和交互体验切入，适合偏产品化岗位。'
  }
]

const readCachedResume = () => {
  const cached = localStorage.getItem(userKey('resume_analysis'))
  if (!cached) return null
  try {
    return JSON.parse(cached)
  } catch {
    return cached
  }
}

const checkExistingResume = async () => {
  const cached = readCachedResume()
  if (cached) {
    hasResume.value = true
    resumeProfile.value = cached
    return
  }

  try {
    const token = localStorage.getItem('token')
    const resp = await fetch(uploadUrl.replace('/parse', '/profile'), {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!resp.ok) return

    const result = await resp.json()
    if (result.code === 200 && result.data) {
      localStorage.setItem(userKey('resume_analysis'), JSON.stringify(result.data))
      hasResume.value = true
      resumeProfile.value = result.data
    }
  } catch {
    // 保持静默，Dashboard 仍然可用。
  }
}

const handleLogout = () => {
  localStorage.removeItem('token')
  ElMessage.success('已安全退出')
  router.push('/login')
}

const goSetup = () => {
  router.push('/interview/setup')
}

const goTextInterview = () => {
  router.push('/interview')
}

const goVideoInterview = () => {
  router.push('/video-interview')
}

const goResumePage = () => {
  showResumeManager.value = false
  router.push({ path: '/resume' })
}

const openResumeManager = () => {
  showResumeManager.value = true
}

const openRoleFlow = (role) => {
  selectedRole.value = role
  showResumeDialog.value = true
}

const useExistingResume = () => {
  showResumeDialog.value = false
  showModeDialog.value = true
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
      resumeProfile.value = response.data
    }
    ElMessage.success('简历专属画像生成完毕！')
    router.push({ path: '/resume', query: { role: selectedRole.value } })
  } else {
    ElMessage.error(`简历解析异常：${response?.msg || '未知错误'}`)
  }
}

const handleResumeManagerSuccess = (response) => {
  isParsing.value = false
  if (response && response.code === 200) {
    if (response.data) {
      localStorage.setItem(userKey('resume_analysis'), JSON.stringify(response.data))
      hasResume.value = true
      resumeProfile.value = response.data
    }
    showResumeManager.value = false
    ElMessage.success('简历画像已更新！')
    router.push({ path: '/resume' })
  } else {
    ElMessage.error(`简历解析异常：${response?.msg || '未知错误'}`)
  }
}

const handleResumeError = () => {
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

const runShortcut = (action) => {
  if (action === 'setup') return goSetup()
  if (action === 'text') return goTextInterview()
  if (action === 'video') return goVideoInterview()
  if (action === 'resume') return openResumeManager()
}

onMounted(() => {
  checkExistingResume()
})
</script>

<style scoped>
.dashboard-home {
  min-height: calc(100vh - 0px);
  padding: 28px;
  background:
    linear-gradient(180deg, rgba(247, 249, 251, 0.96), rgba(247, 249, 251, 0.98)),
    radial-gradient(circle at 0% 0%, rgba(58, 56, 139, 0.08), transparent 28%),
    radial-gradient(circle at 100% 0%, rgba(4, 76, 69, 0.06), transparent 24%),
    #f7f9fb;
}

.hero-slab,
.section-block,
.aside-block {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(14px);
  box-shadow: 0 12px 40px rgba(25, 28, 30, 0.06);
}

.hero-slab {
  border-radius: 28px;
  padding: 28px;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 22px;
}

.hero-copy {
  min-width: 0;
}

.eyebrow,
.section-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #3a388b;
}

.hero-headline {
  margin-top: 12px;
}

.hero-headline h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 54px);
  line-height: 1.05;
  color: #191c1e;
  letter-spacing: 0;
}

.hero-headline p,
.hero-description,
.status-note,
.route-card p,
.recent-result,
.roadmap-item p,
.mentor-block p {
  color: #5e5d59;
  line-height: 1.65;
}

.hero-headline p {
  margin: 10px 0 0;
  font-size: 17px;
}

.hero-description {
  margin: 18px 0 0;
  max-width: 720px;
  font-size: 15px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.primary-cta,
.secondary-cta,
.ghost-cta {
  min-height: 44px;
  border-radius: 12px;
}

.secondary-cta,
.ghost-cta {
  background: #fff;
  color: #191c1e;
}

.hero-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.status-card,
.overview-card,
.route-card,
.recent-item,
.roadmap-item,
.mentor-block,
.maturity-block,
.skills-block,
.action-block {
  border-radius: 20px;
}

.status-card {
  padding: 20px;
  background: linear-gradient(135deg, rgba(58, 56, 139, 0.08), rgba(82, 80, 164, 0.04));
}

.status-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  color: #5e5d59;
  font-size: 13px;
}

.status-title {
  margin-top: 10px;
  font-size: 20px;
  font-weight: 800;
  color: #191c1e;
}

.status-note {
  margin-top: 10px;
}

.status-path {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.62);
  color: #3a388b;
  font-size: 13px;
  font-weight: 700;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  padding: 18px;
  background: rgba(255, 255, 255, 0.92);
}

.overview-label {
  display: block;
  color: #6e6d67;
  font-size: 13px;
}

.overview-value {
  margin-top: 10px;
  color: #191c1e;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 0;
}

.overview-unit {
  margin-left: 4px;
  font-size: 15px;
  font-weight: 700;
  color: #5e5d59;
}

.overview-card[data-tone='primary'] .overview-value {
  color: #3a388b;
}

.overview-card[data-tone='success'] .overview-value {
  color: #004c45;
}

.overview-card[data-tone='accent'] .overview-value {
  color: #5250a4;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 20px;
  margin-top: 20px;
}

.dashboard-main,
.dashboard-aside {
  display: grid;
  gap: 20px;
}

.section-block,
.aside-block {
  padding: 22px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-head h2,
.aside-block h2 {
  margin: 6px 0 0;
  font-size: 22px;
  line-height: 1.15;
  color: #191c1e;
}

.recommendation-shell {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(280px, 1.05fr);
  gap: 18px;
}

.recommendation-copy p {
  margin: 0;
  font-size: 15px;
}

.recommendation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.route-cards {
  display: grid;
  gap: 12px;
}

.route-card {
  padding: 18px;
  text-align: left;
  border: 0;
  background: rgba(247, 249, 251, 0.92);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.route-card:hover,
.shortcut-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.08);
}

.route-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.route-badge,
.route-state,
.mentor-note,
.maturity-delta,
.roadmap-status {
  font-size: 12px;
  font-weight: 700;
}

.route-badge {
  color: #3a388b;
}

.route-state {
  color: #8a887f;
}

.route-card h3,
.roadmap-item h3 {
  margin: 12px 0 8px;
  font-size: 18px;
  color: #191c1e;
}

.route-card p,
.roadmap-item p {
  margin: 0;
  font-size: 14px;
}

.route-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  color: #3a388b;
  font-weight: 700;
}

.recent-list {
  display: grid;
  gap: 12px;
}

.recent-item {
  padding: 18px 20px;
  background: #faf9f5;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.recent-title {
  font-size: 16px;
  font-weight: 800;
  color: #191c1e;
}

.recent-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
  color: #6e6d67;
  font-size: 13px;
}

.recent-result {
  max-width: 280px;
  text-align: right;
  font-size: 14px;
}

.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.roadmap-item {
  padding: 18px;
  background: #faf9f5;
}

.roadmap-status {
  color: #5250a4;
}

.mentor-block {
  background: linear-gradient(180deg, rgba(58, 56, 139, 0.06), rgba(255, 255, 255, 0.94));
}

.mentor-note {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 16px;
  color: #004c45;
}

.maturity-list,
.skill-list,
.shortcut-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.maturity-item,
.skill-item {
  display: grid;
  gap: 8px;
}

.maturity-top,
.skill-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  color: #191c1e;
}

.maturity-top strong,
.skill-meta strong {
  font-size: 14px;
}

.maturity-top em {
  font-style: normal;
  color: #6e6d67;
  font-weight: 600;
}

.maturity-delta {
  color: #6e6d67;
}

.skill-bar {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(58, 56, 139, 0.1);
}

.skill-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #3a388b, #5250a4);
}

.shortcut-item {
  border: 0;
  background: #faf9f5;
  border-radius: 16px;
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  text-align: left;
  cursor: pointer;
}

.shortcut-item strong {
  display: block;
  color: #191c1e;
  font-size: 14px;
}

.shortcut-item span {
  display: block;
  margin-top: 4px;
  color: #6e6d67;
  font-size: 12px;
}

.dialog-panel {
  text-align: center;
  padding: 8px 18px 20px;
}

.dialog-icon {
  font-size: 44px;
  color: #3a388b;
  margin-bottom: 14px;
}

.dialog-panel h3 {
  margin: 0 0 10px;
  font-size: 20px;
  color: #191c1e;
}

.dialog-panel p {
  margin: 0 auto 18px;
  color: #5e5d59;
  line-height: 1.65;
}

.dialog-primary,
.dialog-secondary {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-weight: 700;
}

.dialog-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
  color: #b0aea5;
  font-size: 13px;
}

.dialog-divider::before,
.dialog-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e8e6dc;
}

.resume-upload {
  margin-top: 6px;
}

.resume-upload :deep(.el-upload-dragger) {
  border-radius: 16px;
  background: #faf9f5;
}

.mode-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 8px 0 4px;
}

.mode-card {
  border: 0;
  border-radius: 18px;
  padding: 22px 18px;
  cursor: pointer;
  background: #faf9f5;
  text-align: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.mode-card.video {
  background: #f4fbf9;
}

.mode-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.08);
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
  background: rgba(58, 56, 139, 0.08);
  color: #3a388b;
}

.mode-card.video .mode-icon {
  background: rgba(4, 76, 69, 0.12);
  color: #004c45;
}

.mode-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #191c1e;
}

.mode-card p {
  margin: 0 0 12px;
  color: #5e5d59;
  line-height: 1.6;
}

@media (max-width: 1180px) {
  .hero-slab,
  .recommendation-shell,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .roadmap-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .dashboard-home {
    padding: 16px;
  }

  .hero-slab,
  .section-block,
  .aside-block {
    padding: 18px;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .recent-item {
    flex-direction: column;
  }

  .recent-result {
    max-width: none;
    text-align: left;
  }

  .mode-options {
    grid-template-columns: 1fr;
  }

  .hero-actions,
  .recommendation-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
