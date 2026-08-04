<template>
  <div class="dashboard-home">
    <section class="hero-slab">
      <div class="hero-copy">
        <div class="eyebrow">AI 面试工作台</div>
        <div class="hero-headline">
          <h1>{{ displayName }}</h1>
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
            <div class="overview-value">{{ resumeStatusValue }}</div>
          </div>
          <div class="overview-card" data-tone="accent">
            <span class="overview-label">知识覆盖</span>
            <div class="overview-value">{{ knowledgeCats }}<span class="overview-unit">领域</span></div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="showLlmConfigPrompt" class="llm-callout">
      <div>
        <div class="section-kicker">LLM Ready</div>
        <h2>先完成大模型配置</h2>
        <p>当前账号还没有启用可用的 Provider。文字面试、视频面试和 AI Mentor 需要先完成配置。</p>
      </div>
      <el-button type="primary" plain @click="goLlmSettings">去配置</el-button>
    </section>

    <div class="dashboard-grid">
      <div class="dashboard-main">
        <section class="section-block recent-section">
          <div class="section-head">
            <div>
              <div class="section-kicker">最近练习</div>
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
  ArrowRight, Document, Operation,
  TrendCharts, VideoCamera
} from '@element-plus/icons-vue'
import { getLlmConfigStatusAPI } from '@/api/llm'
import { getHistoryListAPI } from '@/api/interview'
import { getMentorInsightAPI, getKnowledgeCoverageAPI, getPreferenceAPI, getCurrentUserAPI } from '@/api/user'
import { getVisiblePositionsAPI } from '@/api/position'
import { getUsername, getNickname, setNickname } from '@/utils/auth'
import {
  buildLlmConfigRouteQuery,
  createUnknownLlmConfigStatus,
  normalizeLlmConfigStatus
} from '@/utils/llmConfig'
import { normalizeVisibleInterviewPositions } from '@/utils/interviewEntry'
import { interviewSetupDefaults } from '@/mock/setup'

const router = useRouter()

const displayName = ref(getNickname() || getUsername() || '用户')
const historyTotal = ref(0)
const latestScore = ref('--')
const knowledgeCats = ref('--')
const recentInterviews = ref([])
const mentorInsight = ref(null)
const llmStatus = ref(createUnknownLlmConfigStatus())
const workspacePositions = ref([])
const visiblePositionTotal = ref(0)
const resumeReadyCount = ref(0)

const showModeDialog = ref(false)
const selectedRole = ref('')

const pref = ref({
  defaultMode: 'text',
  defaultRole: interviewSetupDefaults.roleOptions[0],
  difficultyLevel: 'mid',
  focusAreas: '[]'
})

const getScoreType = (s) => s >= 85 ? 'success' : s >= 70 ? 'primary' : s >= 55 ? 'warning' : 'danger'
const formatTime = (d) => d ? new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '--'

const statusText = computed(() => {
  if (showLlmConfigPrompt.value) return '尚未启用大模型配置，请先完成 Provider 设置后再开始 AI 功能。'
  if (recentInterviews.value.length === 0) return '今日建议：先上传简历，再进入文字模式热身'
  const latest = recentInterviews.value[0]
  return `最近一次面试：${latest.position} · ${latest.score}分 · ${latest.interviewMode === 'video' ? '视频' : '文字'}模式`
})

const showLlmConfigPrompt = computed(() => llmStatus.value.resolved && !llmStatus.value.hasActiveConfig)

const lastActiveText = computed(() => {
  if (recentInterviews.value.length === 0) return '暂无记录'
  return formatTime(recentInterviews.value[0].createTime)
})

const displayTitle = computed(() => {
  if (recentInterviews.value.length === 0) return '准备首次面试'
  return `${recentInterviews.value[0].position}方向`
})

const resumeStatusValue = computed(() => {
  if (!visiblePositionTotal.value) return '按岗位管理'
  return `${resumeReadyCount.value}/${visiblePositionTotal.value}`
})

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

const loadNickname = async () => {
  const cached = getNickname()
  if (cached) {
    displayName.value = cached
    return
  }
  try {
    const user = await getCurrentUserAPI()
    if (user?.nickname) {
      displayName.value = user.nickname
      setNickname(user.nickname)
    } else if (user?.username) {
      displayName.value = user.username
    }
  } catch { /* fallback to getUsername() already set */ }
}

const loadMentor = async () => {
  // 快速加载知识覆盖数（纯 DB 查询，不调 LLM）
  try {
    const cov = await getKnowledgeCoverageAPI()
    if (cov?.knowledgeCoverage?.details?.length) {
      knowledgeCats.value = String(cov.knowledgeCoverage.details.length)
    }
  } catch { /* optional */ }
  if (showLlmConfigPrompt.value) return
  // 异步加载 AI 洞察（含 LLM，可能慢但不阻塞页面）
  try {
    const data = await getMentorInsightAPI()
    if (data) mentorInsight.value = data
  } catch { /* Mentor unavailable */ }
}

const loadLlmStatus = async () => {
  try {
    const data = await getLlmConfigStatusAPI({ silent: true })
    llmStatus.value = normalizeLlmConfigStatus(data)
  } catch {
    llmStatus.value = createUnknownLlmConfigStatus()
  }
}

const buildInterviewQuery = (mode) => {
  const roleName = selectedRole.value || pref.value.defaultRole || interviewSetupDefaults.roleOptions[0]
  const positionId = resolvePositionId(roleName)
  const query = {
    role: roleName,
    focus: (() => {
      try { const areas = JSON.parse(pref.value.focusAreas || '[]'); return Array.isArray(areas) ? areas.join(',') : '' }
      catch { return '' }
    })(),
    mode,
    difficulty: pref.value.difficultyLevel || 'mid'
  }
  if (positionId) query.positionId = positionId
  return query
}

const resolvePositionId = (roleName) => {
  if (!roleName || !workspacePositions.value.length) return null
  const matched = workspacePositions.value.find((item) => item.name === roleName)
  return matched?.id || workspacePositions.value[0]?.id || null
}

const loadPositions = async () => {
  try {
    const data = await getVisiblePositionsAPI({ silent: true })
    workspacePositions.value = normalizeVisibleInterviewPositions(data)
    visiblePositionTotal.value = workspacePositions.value.length
    const visiblePositionIds = new Set(workspacePositions.value.map((item) => item.id))
    resumeReadyCount.value = (data || [])
      .filter((item) => visiblePositionIds.has(Number(item.id)) && item.hasResumeProfile)
      .length
  } catch {
    workspacePositions.value = []
    visiblePositionTotal.value = 0
    resumeReadyCount.value = 0
  }
}

const goSetup = () => router.push('/interview/setup')
const goLlmSettings = () => router.push({ path: '/llm-providers', query: buildLlmConfigRouteQuery('dashboard') })
const ensureLlmReady = () => {
  if (!showLlmConfigPrompt.value) return true
  ElMessage.warning('请先配置并启用一个大模型 Provider')
  goLlmSettings()
  return false
}
const goTextInterview = () => {
  if (!ensureLlmReady()) return
  router.push({ path: '/interview', query: buildInterviewQuery('text') })
}
const goVideoInterview = () => {
  if (!ensureLlmReady()) return
  router.push({ path: '/video-interview', query: buildInterviewQuery('video') })
}
const goResumePage = () => {
  const positionId = selectedRole.value ? resolvePositionId(selectedRole.value) : null
  router.push({ path: '/resume', query: positionId ? { positionId } : {} })
}

const openResumeManager = () => { goResumePage() }

const confirmMode = (mode) => {
  showModeDialog.value = false
  let role = selectedRole.value
  if (!role) role = pref.value.defaultRole || interviewSetupDefaults.roleOptions[0]
  const positionId = resolvePositionId(role)
  const focus = (() => {
    try { const areas = JSON.parse(pref.value.focusAreas || '[]'); return Array.isArray(areas) ? areas.join(',') : '' }
    catch { return '' }
  })()
  const path = mode === 'video' ? '/video-interview' : '/interview'
  const query = { role, isTailored: 'false', difficulty: pref.value.difficultyLevel || 'mid', focus }
  if (positionId) query.positionId = positionId
  router.push({ path, query })
}

const loadPreference = async () => {
  try {
    const p = await getPreferenceAPI()
    if (p) {
      pref.value.defaultMode = p.defaultMode || 'text'
      pref.value.defaultRole = p.defaultRole || interviewSetupDefaults.roleOptions[0]
      pref.value.difficultyLevel = p.difficultyLevel || 'mid'
      pref.value.focusAreas = p.focusAreas || '[]'
    }
  } catch { /* dashboard works without preferences */ }
}

onMounted(async () => {
  await Promise.all([loadHistory(), loadPreference(), loadNickname(), loadLlmStatus(), loadPositions()])
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

.llm-callout {
  margin-top: 18px;
  padding: 20px 22px;
  border-radius: 20px;
  border: 1px solid rgba(230, 162, 60, 0.18);
  background: rgba(255, 248, 235, 0.9);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.llm-callout h2 {
  margin: 6px 0 0;
  font-size: 20px;
  line-height: 1.3;
}

.llm-callout p {
  margin: 8px 0 0;
  color: #5e5d59;
  line-height: 1.65;
}

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
  .llm-callout { flex-direction: column; align-items: flex-start; }
  .overview-grid { grid-template-columns: 1fr; }
  .recent-item { flex-direction: column; }
  .mode-options { grid-template-columns: 1fr; }
  .hero-actions { flex-direction: column; align-items: stretch; }
}
</style>
