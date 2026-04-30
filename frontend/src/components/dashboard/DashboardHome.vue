<template>
  <div class="dashboard-home">
    <section class="hero-slab">
      <div class="hero-copy">
        <div class="eyebrow">Architectural Intelligence</div>
        <div class="hero-headline">
          <h1>{{ username }}</h1>
          <p>把面试拆成可以演练、复盘、再优化的工作台。</p>
        </div>
        <p class="hero-description">{{ statusText }}</p>

        <div class="hero-actions">
          <el-button type="primary" class="primary-cta" @click="goSetup">
            <el-icon><Operation /></el-icon>
            进入面试准备
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
            <span class="status-value">{{ lastActiveText }}</span>
          </div>
          <div class="status-title">{{ displayTitle }}</div>
          <div class="status-note" v-if="mentorInsight?.diagnosis?.overview">
            {{ mentorInsight.diagnosis.overview }}
          </div>
          <div class="status-note" v-else>
            完成首次面试后，AI Mentor 将为你生成个性化分析。
          </div>
        </div>

        <div class="overview-grid">
          <div class="overview-card" data-tone="primary">
            <span class="overview-label">历史面试</span>
            <div class="overview-value">{{ historyTotal }}<span class="overview-unit">场</span></div>
          </div>
          <div class="overview-card" data-tone="success">
            <span class="overview-label">最近得分</span>
            <div class="overview-value">{{ latestScore }}<span class="overview-unit">分</span></div>
          </div>
          <div class="overview-card" data-tone="neutral">
            <span class="overview-label">简历状态</span>
            <div class="overview-value">{{ hasResume ? '已解析' : '未上传' }}</div>
          </div>
          <div class="overview-card" data-tone="accent">
            <span class="overview-label">知识覆盖</span>
            <div class="overview-value">{{ knowledgeCats }}<span class="overview-unit">领域</span></div>
          </div>
        </div>
      </div>
    </section>

    <div class="dashboard-grid">
      <div class="dashboard-main">
        <section class="section-block mentor-section" v-if="mentorInsight">
          <div class="section-head">
            <div>
              <div class="section-kicker">AI Mentor</div>
              <h2>智能教练分析</h2>
            </div>
            <el-button size="small" :icon="RefreshRight" :loading="refreshing" @click="refreshMentor">
              刷新分析
            </el-button>
          </div>

          <div class="mentor-content">
            <div class="mentor-columns">
              <div class="mentor-col">
                <h4 class="mentor-subtitle">优势</h4>
                <ul v-if="mentorInsight.diagnosis?.strengths?.length">
                  <li v-for="s in mentorInsight.diagnosis.strengths" :key="s">{{ s }}</li>
                </ul>
                <p v-else class="no-data-hint">暂无优势分析</p>
              </div>
              <div class="mentor-col">
                <h4 class="mentor-subtitle">待提升</h4>
                <ul v-if="mentorInsight.diagnosis?.weaknesses?.length">
                  <li v-for="w in mentorInsight.diagnosis.weaknesses" :key="w">{{ w }}</li>
                </ul>
                <p v-else class="no-data-hint">暂无弱点分析</p>
              </div>
            </div>

            <div v-if="mentorInsight.riskAlerts?.length" class="risk-section">
              <h4 class="mentor-subtitle">风险预警</h4>
              <div v-for="alert in mentorInsight.riskAlerts" :key="alert.message"
                   :class="['risk-badge', alert.severity]">
                {{ alert.message }}
              </div>
            </div>

            <div v-if="mentorInsight.actions?.length" class="actions-section">
              <h4 class="mentor-subtitle">建议行动</h4>
              <div v-for="act in mentorInsight.actions.slice(0, 4)" :key="act.message"
                   class="action-item">
                <el-tag :type="act.priority === 1 ? 'danger' : act.priority === 2 ? 'warning' : 'info'" size="small">
                  {{ act.priority === 1 ? '立即' : act.priority === 2 ? '短期' : '长期' }}
                </el-tag>
                <div>
                  <strong>{{ act.category }}</strong>
                  <p>{{ act.message }}</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="section-block recent-section">
          <div class="section-head">
            <div>
              <div class="section-kicker">Recent Interviews</div>
              <h2>最近面试记录</h2>
            </div>
            <el-button size="small" plain @click="router.push('/history')">查看全部</el-button>
          </div>

          <div v-if="recentInterviews.length" class="recent-list">
            <article v-for="item in recentInterviews" :key="item.id" class="recent-item" @click="router.push('/history')">
              <div class="recent-main">
                <div class="recent-title">{{ item.position }}</div>
                <div class="recent-meta">
                  <el-tag size="small" effect="plain">{{ item.interviewMode === 'video' ? '视频' : '文字' }}</el-tag>
                  <span>{{ formatTime(item.createTime) }}</span>
                </div>
              </div>
              <div class="recent-result">
                <el-tag :type="getScoreType(item.score)" effect="dark">{{ item.score }} 分</el-tag>
              </div>
            </article>
          </div>
          <el-empty v-else description="还没有面试记录，先开始一场面试吧" />
        </section>

        <section v-if="mentorInsight?.knowledgeCoverage?.details?.length" class="section-block coverage-section">
          <div class="section-head">
            <div>
              <div class="section-kicker">Knowledge Coverage</div>
              <h2>知识领域覆盖</h2>
            </div>
          </div>
          <div class="coverage-list">
            <div v-for="item in mentorInsight.knowledgeCoverage.details.slice(0, 10)" :key="item.category"
                 class="coverage-row">
              <span class="coverage-name">{{ item.category }}</span>
              <div class="coverage-bar-bg">
                <div class="coverage-bar-fill" :style="{ width: `${Math.min(item.percent, 100)}%` }" />
              </div>
              <span class="coverage-num">{{ item.covered }}</span>
            </div>
          </div>
        </section>
      </div>

      <aside class="dashboard-aside">
        <div class="aside-block">
          <div class="section-kicker">快捷操作</div>
          <h2>常用入口</h2>
          <div class="shortcut-list">
            <button type="button" class="shortcut-item" @click="goSetup">
              <div>
                <strong>进入准备页</strong>
                <span>配置角色、模式和岗位</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </button>
            <button type="button" class="shortcut-item" @click="goTextInterview">
              <div>
                <strong>文字面试</strong>
                <span>安静环境下的文字交流</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </button>
            <button type="button" class="shortcut-item" @click="goVideoInterview">
              <div>
                <strong>视频面试</strong>
                <span>面对面 AI 对话训练</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </button>
            <button type="button" class="shortcut-item" @click="openResumeManager">
              <div>
                <strong>简历管理</strong>
                <span>上传或更新简历画像</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </button>
            <button type="button" class="shortcut-item" @click="router.push('/history')">
              <div>
                <strong>历史报告</strong>
                <span>查看过往面试评估</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </aside>
    </div>

    <!-- Resume Dialog -->
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
          <el-upload class="resume-upload" drag :action="uploadUrl"
            :headers="uploadHeaders" :show-file-list="false"
            :on-success="handleResumeSuccess" :on-error="handleResumeError"
            :before-upload="beforeResumeUpload" accept=".pdf">
            <div class="el-upload__text" v-if="!isParsing"><em>重新上传新简历</em>，覆盖旧画像</div>
            <div class="el-upload__text" v-else>
              <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
            </div>
          </el-upload>
        </template>
        <template v-else>
          <h3>是否提供个人简历？</h3>
          <p>系统会先解析简历，再结合角色和模式生成更贴近真实面试的追问。</p>
          <el-upload class="resume-upload" drag :action="uploadUrl"
            :headers="uploadHeaders" :show-file-list="false"
            :on-success="handleResumeSuccess" :on-error="handleResumeError"
            :before-upload="beforeResumeUpload" accept=".pdf">
            <div class="el-upload__text" v-if="!isParsing"><em>点击上传 PDF 简历</em>，生成定制化画像</div>
            <div class="el-upload__text" v-else>
              <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
            </div>
          </el-upload>
          <div class="dialog-divider"><span>或</span></div>
          <el-button class="dialog-secondary" plain @click="skipResumeAndSelectMode">
            暂无简历，直接选择文字 / 视频模式
          </el-button>
        </template>
      </div>
    </el-dialog>

    <!-- Resume Manager Dialog -->
    <el-dialog v-model="showResumeManager" title="简历管理" width="480" center>
      <div class="dialog-panel">
        <el-icon class="dialog-icon"><TrendCharts /></el-icon>
        <template v-if="hasResume">
          <h3>当前简历画像已就绪</h3>
          <p>面试时会自动复用该画像，也可以上传新的简历覆盖。</p>
          <el-button type="primary" class="dialog-primary" @click="goResumePage">查看画像详情</el-button>
          <div class="dialog-divider"><span>更新简历</span></div>
        </template>
        <template v-else>
          <h3>尚未上传简历</h3>
          <p>上传简历后，面试时将自动使用 AI 定制化提问。</p>
        </template>
        <el-upload class="resume-upload" drag :action="uploadUrl"
          :headers="uploadHeaders" :show-file-list="false"
          :on-success="handleResumeManagerSuccess" :on-error="handleResumeError"
          :before-upload="beforeResumeUpload" accept=".pdf">
          <div class="el-upload__text" v-if="!isParsing">
            <em>{{ hasResume ? '上传新简历覆盖' : '点击上传 PDF 简历' }}</em>
          </div>
          <div class="el-upload__text" v-else>
            <el-icon class="is-loading"><Loading /></el-icon> 正在深度解析简历，请稍候...
          </div>
        </el-upload>
      </div>
    </el-dialog>

    <!-- Mode Dialog -->
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight, Document, Loading, Operation, RefreshRight,
  TrendCharts, VideoCamera
} from '@element-plus/icons-vue'
import { getHistoryListAPI } from '@/api/interview'
import { getMentorInsightAPI, getKnowledgeCoverageAPI, refreshMentorInsightAPI } from '@/api/user'
import { getUsername, userKey } from '@/utils/auth'

const router = useRouter()

const username = ref(getUsername() || '用户')
const hasResume = ref(false)
const resumeProfile = ref(null)
const historyTotal = ref(0)
const latestScore = ref('--')
const knowledgeCats = ref('--')
const recentInterviews = ref([])
const mentorInsight = ref(null)
const refreshing = ref(false)

const showModeDialog = ref(false)
const showResumeDialog = ref(false)
const showResumeManager = ref(false)
const isParsing = ref(false)
const selectedRole = ref('')
const uploadUrl = `${import.meta.env.VITE_API_BASE_URL || ''}/api/resume/parse`
const uploadHeaders = ref({ Authorization: `Bearer ${localStorage.getItem('token') || ''}` })

const getScoreType = (s) => s >= 85 ? 'success' : s >= 70 ? 'primary' : s >= 55 ? 'warning' : 'danger'
const formatTime = (d) => d ? new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '--'

const statusText = computed(() => {
  if (recentInterviews.value.length === 0) return '今日建议：先上传简历，再进入文字模式热身'
  const latest = recentInterviews.value[0]
  return `最近一次面试：${latest.position} · ${latest.score}分 · ${latest.interviewMode === 'video' ? '视频' : '文字'}模式`
})

const lastActiveText = computed(() => {
  if (recentInterviews.value.length === 0) return '暂无记录'
  return formatTime(recentInterviews.value[0].createTime)
})

const displayTitle = computed(() => {
  if (recentInterviews.value.length === 0) return '准备首次面试'
  return `${recentInterviews.value[0].position}方向`
})

const readCachedResume = () => {
  const cached = localStorage.getItem(userKey('resume_analysis'))
  if (cached) {
    try {
      const parsed = JSON.parse(cached)
      if (parsed) { hasResume.value = true; resumeProfile.value = parsed; return }
    } catch { /* ignore */ }
  }
  hasResume.value = false
  resumeProfile.value = null
}

const checkExistingResume = async () => {
  readCachedResume()
  if (hasResume.value) return
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
        resumeProfile.value = result.data
      }
    }
  } catch { /* Dashboard still works without resume */ }
}

const loadHistory = async () => {
  try {
    const list = await getHistoryListAPI()
    if (list && list.length) {
      recentInterviews.value = list.slice(0, 3)
      historyTotal.value = list.length
      const latest = list[0]
      latestScore.value = latest.score != null ? String(latest.score) : '--'
    }
  } catch { /* Dashboard still works without history */ }
}

const loadMentor = async () => {
  // 快速加载知识覆盖数（纯 DB 查询，不调 LLM）
  try {
    const cov = await getKnowledgeCoverageAPI()
    if (cov?.knowledgeCoverage?.details?.length) {
      knowledgeCats.value = String(cov.knowledgeCoverage.details.length)
    }
  } catch { /* optional */ }
  // 异步加载 AI 洞察（含 LLM，可能慢但不阻塞页面）
  try {
    const data = await getMentorInsightAPI()
    if (data) mentorInsight.value = data
  } catch { /* Mentor unavailable */ }
}

const refreshMentor = async () => {
  refreshing.value = true
  try {
    const data = await refreshMentorInsightAPI()
    if (data) mentorInsight.value = data
    if (data?.knowledgeCoverage?.details?.length) {
      knowledgeCats.value = String(data.knowledgeCoverage.details.length)
    }
    ElMessage.success('AI Mentor 分析已刷新')
  } catch {
    ElMessage.error('刷新失败，请稍后重试')
  }
  refreshing.value = false
}

const goSetup = () => router.push('/interview/setup')
const goTextInterview = () => router.push('/interview')
const goVideoInterview = () => router.push('/video-interview')
const goResumePage = () => { showResumeManager.value = false; router.push({ path: '/resume' }) }
const openResumeManager = () => { showResumeManager.value = true }
const useExistingResume = () => { showResumeDialog.value = false; showModeDialog.value = true }
const skipResumeAndSelectMode = () => { showResumeDialog.value = false; showModeDialog.value = true }

const beforeResumeUpload = (file) => {
  if (file.type !== 'application/pdf') { ElMessage.error('只能上传 PDF 格式的简历！'); return false }
  isParsing.value = true
  uploadHeaders.value = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
  return true
}

const handleResumeSuccess = (response) => {
  isParsing.value = false; showResumeDialog.value = false
  if (response?.code === 200) {
    if (response.data) { localStorage.setItem(userKey('resume_analysis'), JSON.stringify(response.data)); hasResume.value = true }
    ElMessage.success('简历专属画像生成完毕！')
    router.push({ path: '/resume', query: { role: selectedRole.value } })
  } else { ElMessage.error(`简历解析异常：${response?.msg || '未知错误'}`) }
}

const handleResumeManagerSuccess = (response) => {
  isParsing.value = false
  if (response?.code === 200) {
    if (response.data) { localStorage.setItem(userKey('resume_analysis'), JSON.stringify(response.data)); hasResume.value = true }
    showResumeManager.value = false; ElMessage.success('简历画像已更新！'); router.push({ path: '/resume' })
  } else { ElMessage.error(`简历解析异常：${response?.msg || '未知错误'}`) }
}

const handleResumeError = () => { isParsing.value = false; ElMessage.error('简历解析失败，请检查文件后重试！') }

const confirmMode = (mode) => {
  showModeDialog.value = false
  const isTailored = hasResume.value ? 'true' : 'false'
  const path = mode === 'video' ? '/video-interview' : '/interview'
  router.push({ path, query: { role: selectedRole.value, isTailored } })
}

onMounted(async () => {
  await Promise.all([checkExistingResume(), loadHistory()])
  // 页面核心数据已就绪，Mentor 异步加载不阻塞渲染
  loadMentor()
})
</script>

<style scoped>
.dashboard-home {
  min-height: calc(100vh - 0px);
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 28px;
  background: transparent;
}

.hero-slab, .section-block, .aside-block {
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

.hero-copy { min-width: 0; }

.eyebrow, .section-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #3a388b;
}

.hero-headline { margin-top: 12px; }

.hero-headline h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 54px);
  line-height: 1.05;
  color: #191c1e;
}

.hero-headline p, .hero-description, .status-note, .recent-result, .no-data-hint, .action-item p {
  color: #5e5d59;
  line-height: 1.65;
}

.hero-headline p { margin: 10px 0 0; font-size: 17px; }
.hero-description { margin: 18px 0 0; max-width: 720px; font-size: 15px; }

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.primary-cta, .secondary-cta, .ghost-cta {
  min-height: 44px;
  border-radius: 12px;
}

.secondary-cta, .ghost-cta { background: #fff; color: #191c1e; }

.hero-panel { display: grid; gap: 14px; min-width: 0; }

.status-card {
  padding: 20px;
  border-radius: 20px;
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

.status-note { margin-top: 10px; }

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
}

.overview-label { display: block; color: #6e6d67; font-size: 13px; }

.overview-value {
  margin-top: 10px;
  color: #191c1e;
  font-size: 32px;
  font-weight: 800;
}

.overview-unit { margin-left: 4px; font-size: 15px; font-weight: 700; color: #5e5d59; }

.overview-card[data-tone='primary'] .overview-value { color: #3a388b; }
.overview-card[data-tone='success'] .overview-value { color: #004c45; }
.overview-card[data-tone='accent'] .overview-value { color: #5250a4; }

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
  gap: 20px;
  margin-top: 20px;
}

.dashboard-main, .dashboard-aside { display: grid; gap: 20px; }

.section-block, .aside-block { padding: 22px; border-radius: 20px; }

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-head h2, .aside-block h2 {
  margin: 6px 0 0;
  font-size: 22px;
  line-height: 1.15;
  color: #191c1e;
}

/* ─── Mentor ─── */
.mentor-section { background: linear-gradient(180deg, rgba(58, 56, 139, 0.04), rgba(255, 255, 255, 0.94)); }
.mentor-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-bottom: 16px; }
.mentor-subtitle { font-size: 14px; font-weight: 700; color: #3a388b; margin: 0 0 8px; }
.mentor-content ul { margin: 0; padding-left: 18px; }
.mentor-content li { color: #191c1e; font-size: 14px; line-height: 1.7; margin-bottom: 4px; }
.risk-section { margin-bottom: 14px; }
.risk-badge { padding: 8px 12px; border-radius: 10px; font-size: 13px; margin-bottom: 6px; line-height: 1.5; }
.risk-badge.warning { background: rgba(245, 158, 11, 0.1); color: #9a6b17; border: 1px solid rgba(245, 158, 11, 0.2); }
.risk-badge.danger { background: rgba(239, 68, 68, 0.08); color: #9a2c2c; border: 1px solid rgba(239, 68, 68, 0.15); }
.risk-badge.info { background: rgba(58, 56, 139, 0.06); color: #454652; border: 1px solid rgba(58, 56, 139, 0.1); }

.actions-section .action-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 0;
  border-bottom: 1px solid rgba(69, 70, 82, 0.06);
}
.action-item strong { display: block; color: #191c1e; font-size: 14px; }
.action-item p { margin: 4px 0 0; font-size: 13px; }
.no-data-hint { font-size: 13px; color: #94a3b8; }

/* ─── Recent ─── */
.recent-list { display: grid; gap: 12px; }
.recent-item {
  padding: 18px 20px;
  border-radius: 20px;
  background: #faf9f5;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.recent-item:hover { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(25, 28, 30, 0.05); }
.recent-title { font-size: 16px; font-weight: 800; color: #191c1e; }
.recent-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
  color: #6e6d67;
  font-size: 13px;
}
.recent-result { flex-shrink: 0; }

/* ─── Coverage ─── */
.coverage-list { display: grid; gap: 10px; }
.coverage-row { display: flex; align-items: center; gap: 12px; }
.coverage-name { width: 80px; font-size: 12px; color: #454652; text-align: right; flex-shrink: 0; }
.coverage-bar-bg { flex: 1; height: 10px; border-radius: 999px; overflow: hidden; background: rgba(58, 56, 139, 0.08); }
.coverage-bar-fill { height: 100%; border-radius: inherit; background: linear-gradient(90deg, #3a388b, #5250a4); transition: width 0.6s ease; }
.coverage-num { width: 36px; font-size: 12px; color: #454652; text-align: right; }

/* ─── Shortcuts ─── */
.shortcut-list { display: grid; gap: 12px; margin-top: 14px; }
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
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.shortcut-item:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(25, 28, 30, 0.08); }
.shortcut-item strong { display: block; color: #191c1e; font-size: 14px; }
.shortcut-item span { display: block; margin-top: 4px; color: #6e6d67; font-size: 12px; }

/* ─── Dialogs ─── */
.dialog-panel { text-align: center; padding: 8px 18px 20px; }
.dialog-icon { font-size: 44px; color: #3a388b; margin-bottom: 14px; }
.dialog-panel h3 { margin: 0 0 10px; font-size: 20px; color: #191c1e; }
.dialog-panel p { margin: 0 auto 18px; color: #5e5d59; line-height: 1.65; }
.dialog-primary, .dialog-secondary { width: 100%; height: 46px; border-radius: 12px; font-weight: 700; }
.dialog-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
  color: #b0aea5;
  font-size: 13px;
}
.dialog-divider::before, .dialog-divider::after { content: ''; flex: 1; height: 1px; background: #e8e6dc; }
.resume-upload { margin-top: 6px; }
.resume-upload :deep(.el-upload-dragger) { border-radius: 16px; background: #faf9f5; }

.mode-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; padding: 8px 0 4px; }
.mode-card {
  border: 0;
  border-radius: 18px;
  padding: 22px 18px;
  cursor: pointer;
  background: #faf9f5;
  text-align: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.mode-card.video { background: #f4fbf9; }
.mode-card:hover { transform: translateY(-3px); box-shadow: 0 12px 30px rgba(25, 28, 30, 0.08); }
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
.mode-card.video .mode-icon { background: rgba(4, 76, 69, 0.12); color: #004c45; }
.mode-card h3 { margin: 0 0 8px; font-size: 18px; color: #191c1e; }
.mode-card p { margin: 0 0 12px; color: #5e5d59; line-height: 1.6; }

@media (max-width: 1180px) {
  .hero-slab, .dashboard-grid { grid-template-columns: 1fr; }
  .mentor-columns { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .dashboard-home { padding: 16px; }
  .hero-slab, .section-block, .aside-block { padding: 18px; }
  .overview-grid { grid-template-columns: 1fr; }
  .recent-item { flex-direction: column; }
  .mode-options { grid-template-columns: 1fr; }
  .hero-actions { flex-direction: column; align-items: stretch; }
}
</style>
