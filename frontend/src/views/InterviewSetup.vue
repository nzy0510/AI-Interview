<template>
  <div class="setup-page">
    <el-container class="setup-layout">
      <el-header class="setup-header">
        <div class="header-left">
          <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.back()" />
          <div class="header-copy">
            <p class="eyebrow">Interview Setup</p>
            <h1 class="page-title">面试准备中心</h1>
            <p class="page-subtitle">
              把简历状态、岗位目标、经验等级和练习模式先配置好，再进入真正的面试流程。
            </p>
          </div>
        </div>

        <div class="header-actions">
          <el-tag effect="plain" type="info" class="status-pill">{{ resumeSnapshot.resumeLabel }}</el-tag>
          <el-button class="ghost-button" plain @click="router.push('/resume')">
            <el-icon><Document /></el-icon>
            查看画像
          </el-button>
        </div>
      </el-header>

      <el-main class="setup-main">
        <section class="surface-card hero-panel">
          <div class="hero-copy">
            <p class="section-kicker">Preparation Flow</p>
            <h2 class="section-title">先把关键变量固定下来</h2>
            <p class="section-desc">
              当前页面只负责前端配置与入口分流，未接入的环节会保留为待接入状态，不影响后续扩展。
            </p>
          </div>

          <div class="hero-meta">
            <div class="meta-chip">
              <span class="meta-label">简历状态</span>
              <strong>{{ resumeSnapshot.resumeSource }}</strong>
            </div>
            <div class="meta-chip">
              <span class="meta-label">登录状态</span>
              <strong>{{ hasToken ? '已登录' : '未登录' }}</strong>
            </div>
            <div class="meta-chip">
              <span class="meta-label">当前模式</span>
              <strong>{{ modeLabel }}</strong>
            </div>
            <div class="meta-chip">
              <span class="meta-label">大模型状态</span>
              <strong>{{ llmStatusText }}</strong>
            </div>
          </div>
        </section>

        <section v-if="showLlmConfigPrompt" class="surface-card llm-warning-card">
          <div>
            <p class="section-kicker">LLM Required</p>
            <h3 class="section-title">开始面试前先启用一个 Provider</h3>
            <p class="section-desc">当前账号还没有有效的大模型配置。完成配置后，文字面试和视频面试入口才会开放。</p>
          </div>
          <el-button type="primary" plain @click="goLlmSettings">去配置</el-button>
        </section>

        <div class="setup-grid">
          <div class="main-column">
            <section class="surface-card section-block">
              <div class="block-head">
                <div>
                  <p class="section-kicker">Profile</p>
                  <h3 class="section-title">简历状态</h3>
                </div>
                <el-tag :type="resumeReady ? 'success' : 'warning'" effect="light">
                  {{ resumeReady ? '已准备' : '待接入' }}
                </el-tag>
              </div>

              <div class="status-grid">
                <div class="status-item">
                  <span class="status-label">目标岗位</span>
                  <strong>{{ currentPositionName }}</strong>
                </div>
                <div class="status-item">
                  <span class="status-label">画像来源</span>
                  <strong>{{ resumeSnapshot.resumeSource }}</strong>
                </div>
                <div class="status-item">
                  <span class="status-label">画像状态</span>
                  <strong>{{ resumeReady ? '可直接使用' : '暂无可用画像' }}</strong>
                </div>
                <div class="status-item">
                  <span class="status-label">画像备注</span>
                  <strong>{{ resumeSnapshot.resumeTone }}</strong>
                </div>
              </div>

              <div v-if="resumeSummary.length" class="resume-summary">
                <div v-for="item in resumeSummary" :key="item.label" class="summary-row">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>

              <el-empty v-else description="待接入简历画像详情" />
            </section>

            <section class="surface-card section-block">
              <div class="block-head">
                <div>
                  <p class="section-kicker">Target</p>
                  <h3 class="section-title">目标岗位</h3>
                </div>
                <el-tag effect="plain" type="info">可编辑</el-tag>
              </div>

              <el-alert
                v-if="positionLoadState === 'error'"
                title="可用岗位加载失败，请刷新页面后重试"
                type="error"
                :closable="false"
                show-icon
              />
              <el-skeleton v-else-if="positionLoadState === 'loading'" :rows="2" animated />
              <el-empty v-else-if="!roleOptions.length" description="暂无可用的内置岗位，请联系管理员维护公共题库" />
              <div v-else class="target-grid">
                <div class="target-input">
                  <label>岗位名称</label>
                  <el-input
                    v-model="role"
                    placeholder="例如：Java 后端开发"
                    clearable
                  />
                </div>
                <div class="target-presets">
                  <button
                    v-for="option in roleOptions"
                    :key="option.id || option.name"
                    type="button"
                    class="preset-chip"
                    :class="{ active: selectedPositionId === option.id || (!selectedPositionId && role === option.name) }"
                    @click="selectRoleOption(option)"
                  >
                    {{ option.name }}
                  </button>
                </div>
              </div>
            </section>

            <section class="surface-card section-block">
              <div class="block-head">
                <div>
                  <p class="section-kicker">Experience</p>
                  <h3 class="section-title">经验等级</h3>
                </div>
                <el-tag effect="plain" type="info">建议项</el-tag>
              </div>

              <div class="experience-grid">
                <button
                  v-for="item in setupDefaults.experienceLevels"
                  :key="item.value"
                  type="button"
                  class="experience-card"
                  :class="{ active: experienceLevel === item.value }"
                  @click="experienceLevel = item.value"
                >
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.hint }}</span>
                </button>
              </div>
            </section>
          </div>

          <aside class="side-column">
            <section class="surface-card section-block sticky-panel">
              <div class="block-head">
                <div>
                  <p class="section-kicker">Mode</p>
                  <h3 class="section-title">面试模式选择</h3>
                </div>
                <el-tag effect="plain" type="success">入口</el-tag>
              </div>

              <div class="mode-stack">
                <button
                  v-for="item in setupDefaults.modeOptions"
                  :key="item.value"
                  type="button"
                  class="mode-card"
                  :class="{ active: mode === item.value }"
                  @click="mode = item.value"
                >
                  <div class="mode-card-head">
                    <strong>{{ item.title }}</strong>
                    <el-tag size="small" effect="plain">{{ item.tag }}</el-tag>
                  </div>
                  <p>{{ item.description }}</p>
                </button>
              </div>

              <div class="start-actions">
                <el-button
                  :type="mode === 'text' ? 'primary' : 'default'"
                  class="start-button"
                  :disabled="!canStartInterview"
                  @click="startInterview('text')"
                >
                  开始文字面试
                </el-button>
                <el-button
                  :type="mode === 'video' ? 'primary' : 'default'"
                  class="start-button"
                  :disabled="!canStartInterview"
                  @click="startInterview('video')"
                >
                  开始视频面试
                </el-button>
                <p v-if="positionAvailabilityMessage" class="start-blocked-hint">
                  {{ positionAvailabilityMessage }}
                </p>
              </div>
            </section>

            <section class="surface-card section-block">
              <div class="block-head">
                <div>
                  <p class="section-kicker">Focus</p>
                  <h3 class="section-title">重点能力</h3>
                </div>
                <el-tag effect="plain" type="info">{{ focusAreas.length }} 项已选</el-tag>
              </div>
              <p class="focus-hint">选中的能力会通过提问策略影响 AI 面试官的追问方向。</p>
              <el-checkbox-group v-model="focusAreas" class="focus-stack">
                <el-checkbox
                  v-for="item in setupDefaults.focusOptions"
                  :key="item.value"
                  :label="item.value"
                  border
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </section>

          </aside>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getLlmConfigStatusAPI } from '@/api/llm'
import { getVisiblePositionsAPI } from '@/api/position'
import { interviewSetupDefaults, buildSetupSnapshot } from '@/mock/setup'
import { getPreferenceAPI, updatePreferenceAPI } from '@/api/user'
import { getToken, userKey, withAuthHeaders } from '@/utils/auth'
import {
  buildLlmConfigRouteQuery,
  createUnknownLlmConfigStatus,
  normalizeLlmConfigStatus
} from '@/utils/llmConfig'
import {
  normalizeVisibleInterviewPositions,
  resolveVisibleInterviewPosition
} from '@/utils/interviewEntry'

const router = useRouter()
const route = useRoute()

const setupDefaults = interviewSetupDefaults
const hasToken = ref(Boolean(getToken()))
const resumeReady = ref(false)
const resumeAnalysis = ref(null)

const role = ref('')
const selectedPositionId = ref(null)
const workspacePositions = ref([])
const positionLoadState = ref('loading')
const experienceLevel = ref('mid')
const focusAreas = ref([])
const mode = ref('text')
const llmStatus = ref(createUnknownLlmConfigStatus())
let resumeProfileRequestSeq = 0

const selectedPosition = computed(() => {
  return workspacePositions.value.find((item) => item.id === selectedPositionId.value) || null
})
const currentPositionName = computed(() => selectedPosition.value?.name || '未选择')

const clearResumeState = () => {
  resumeAnalysis.value = null
  resumeReady.value = false
}

const loadResumeProfile = async () => {
  const requestSeq = ++resumeProfileRequestSeq
  clearResumeState()
  if (!selectedPositionId.value) return
  try {
    if (!getToken()) return
    const resp = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/resume/profile?positionId=${selectedPositionId.value}`, {
      headers: withAuthHeaders()
    })
    if (requestSeq !== resumeProfileRequestSeq) return
    if (!resp.ok) {
      localStorage.removeItem(userKey('resume_analysis'))
      return
    }
    const result = await resp.json()
    if (result.code === 200 && result.data) {
      // Phase 2: data is ResumeProfileResponse wrapper; extract analysis for display
      const profileData = result.data.analysis || result.data
      resumeAnalysis.value = profileData
      resumeReady.value = true
      localStorage.setItem(userKey('resume_analysis'), JSON.stringify(profileData))
    } else {
      localStorage.removeItem(userKey('resume_analysis'))
    }
  } catch {
    if (requestSeq !== resumeProfileRequestSeq) return
    localStorage.removeItem(userKey('resume_analysis'))
  }
}

const resumeSnapshot = computed(() => buildSetupSnapshot(resumeAnalysis.value, hasToken.value))

const modeLabel = computed(() => {
  return setupDefaults.modeOptions.find((item) => item.value === mode.value)?.title || '文字面试'
})

const showLlmConfigPrompt = computed(() => llmStatus.value.resolved && !llmStatus.value.hasActiveConfig)
const positionAvailabilityMessage = computed(() => {
  if (positionLoadState.value === 'loading') return '正在加载可用岗位'
  if (positionLoadState.value === 'error') return '可用岗位加载失败，暂时不能开始面试'
  if (!workspacePositions.value.length) return '暂无可用的内置岗位'
  if (!selectedPosition.value) return '请选择一个可用岗位'
  return ''
})
const canStartInterview = computed(() => {
  return !showLlmConfigPrompt.value && !positionAvailabilityMessage.value
})
const llmStatusText = computed(() => {
  if (!llmStatus.value.resolved) return '待检测'
  if (llmStatus.value.hasActiveConfig) {
    return llmStatus.value.activeDisplayName || llmStatus.value.activeProvider || '已启用'
  }
  return '待配置'
})

const normalizeSupportedRole = (value) => {
  if (workspacePositions.value.some((item) => item.name === value)) return value
  return workspacePositions.value[0]?.name || ''
}

const roleOptions = computed(() => {
  return workspacePositions.value.map((item) => ({
    id: item.id,
    name: item.name,
    scope: item.scope
  }))
})

const selectRoleOption = (option) => {
  role.value = option.name
  selectedPositionId.value = option.id || null
}

const resumeSummary = computed(() => {
  const data = resumeAnalysis.value
  if (!data || typeof data !== 'object') return []

  const summary = []
  const push = (label, value) => {
    if (value === undefined || value === null || value === '') return
    summary.push({ label, value: Array.isArray(value) ? value.join('、') : String(value) })
  }

  push('匹配评分', data.matchScore ? `${data.matchScore}%` : '')
  push('画像评价', data.evaluation)
  push('核心技能', data.coreSkills?.slice?.(0, 4)?.map?.((item) => item.name || item)?.join('、'))
  push('深挖问题', data.tailoredQuestions?.slice?.(0, 3)?.join('；'))
  push('项目摘要', data.projectSummary?.slice?.(0, 2)?.map?.((item) => item.name || item)?.join('、'))

  return summary
})

const syncFromQuery = () => {
  const { role: routeRole, positionId, focus, mode: routeMode } = route.query
  const requestedRole = typeof routeRole === 'string' && routeRole.trim()
    ? routeRole.trim()
    : role.value
  const resolvedPosition = resolveVisibleInterviewPosition(
    workspacePositions.value,
    positionId,
    requestedRole
  )
  selectedPositionId.value = resolvedPosition?.id || null
  role.value = resolvedPosition?.name || ''

  if (typeof routeMode === 'string' && ['text', 'video'].includes(routeMode)) {
    mode.value = routeMode
  }

  if (typeof focus === 'string' && focus.trim()) {
    focusAreas.value = focus.split(',').map((item) => item.trim()).filter(Boolean)
  } else if (!focusAreas.value.length) {
    focusAreas.value = ['projects', 'depth']
  }
}

const loadPreference = async () => {
  try {
    const p = await getPreferenceAPI()
    if (p) {
      if (p.defaultRole && !route.query.role) role.value = normalizeSupportedRole(p.defaultRole)
      if (p.defaultMode && !route.query.mode) mode.value = p.defaultMode
      if (p.difficultyLevel) experienceLevel.value = p.difficultyLevel
      if (p.focusAreas && !route.query.focus) {
        try {
          const areas = typeof p.focusAreas === 'string' ? JSON.parse(p.focusAreas) : p.focusAreas
          if (Array.isArray(areas) && areas.length) focusAreas.value = areas
        } catch { /* ignore */ }
      }
    }
  } catch { /* preference load optional */ }
}

const loadWorkspacePositions = async () => {
  positionLoadState.value = 'loading'
  try {
    const data = await getVisiblePositionsAPI({ silent: true })
    workspacePositions.value = normalizeVisibleInterviewPositions(data)
    positionLoadState.value = 'ready'
  } catch {
    workspacePositions.value = []
    selectedPositionId.value = null
    role.value = ''
    positionLoadState.value = 'error'
  }
}

const loadLlmStatus = async () => {
  try {
    const data = await getLlmConfigStatusAPI({ silent: true })
    llmStatus.value = normalizeLlmConfigStatus(data)
  } catch {
    llmStatus.value = createUnknownLlmConfigStatus()
  }
}

let prefSaveTimer = null
const autoSavePreference = () => {
  if (prefSaveTimer) clearTimeout(prefSaveTimer)
  prefSaveTimer = setTimeout(() => {
    updatePreferenceAPI({
      defaultMode: mode.value,
      defaultRole: role.value,
      difficultyLevel: experienceLevel.value,
      focusAreas: JSON.stringify(focusAreas.value)
    }).catch(() => {})
  }, 800)
}

onMounted(async () => {
  // Phase 2: 先加载岗位列表和偏好，解析 positionId，再按岗位加载简历画像
  await loadWorkspacePositions()
  await loadPreference()
  syncFromQuery()
  await loadResumeProfile()
  await loadLlmStatus()
})

watch(
  () => route.query,
  () => {
    syncFromQuery()
  }
)

watch(selectedPositionId, async (nextId, previousId) => {
  if (nextId === previousId) return
  await loadResumeProfile()
})

// Auto-save preference when selections change
watch([role, experienceLevel, focusAreas, mode], () => {
  autoSavePreference()
}, { deep: true })

const startInterview = (preferredMode) => {
  if (showLlmConfigPrompt.value) {
    ElMessage.warning('请先配置并启用一个大模型 Provider')
    goLlmSettings()
    return
  }
  const validPosition = selectedPosition.value
  if (!validPosition) {
    ElMessage.warning(positionAvailabilityMessage.value || '请选择一个可用岗位')
    return
  }
  const nextMode = preferredMode || mode.value || 'text'
  const query = {
    role: validPosition.name,
    focus: focusAreas.value.join(','),
    mode: nextMode,
    difficulty: experienceLevel.value
  }
  query.positionId = validPosition.id

  const path = nextMode === 'video' ? '/video-interview' : '/interview'
  router.push({ path, query })
}

const goLlmSettings = () => {
  router.push({ path: '/llm-providers', query: buildLlmConfigRouteQuery('interview-setup') })
}
</script>

<style scoped>
.setup-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(58, 56, 139, 0.08), transparent 32%),
    radial-gradient(circle at top right, rgba(4, 76, 69, 0.06), transparent 28%),
    #f7f9fb;
  color: #191c1e;
}

.setup-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.setup-header {
  position: sticky;
  top: 0;
  z-index: 12;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 32px;
  background: rgba(247, 249, 251, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(69, 70, 82, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.icon-button {
  flex: 0 0 auto;
}

.header-copy {
  min-width: 0;
}

.eyebrow,
.section-kicker {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title,
.section-title {
  margin: 0;
  color: #191c1e;
  font-weight: 800;
}

.page-title {
  font-size: 24px;
  line-height: 1.2;
}

.section-title {
  font-size: 20px;
  line-height: 1.25;
}

.page-subtitle,
.section-desc {
  margin: 6px 0 0;
  color: #454652;
  font-size: 14px;
  line-height: 1.6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.status-pill {
  border-color: rgba(58, 56, 139, 0.12);
  color: #3a388b;
  background: #eef0ff;
}

.ghost-button {
  border-radius: 12px;
}

.setup-main {
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 28px 32px 40px;
  box-sizing: border-box;
}

.surface-card {
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  padding: 24px;
}

.llm-warning-card {
  margin-top: 18px;
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  border: 1px solid rgba(230, 162, 60, 0.2);
  background: rgba(255, 248, 235, 0.92);
}

.hero-copy {
  min-width: 0;
  max-width: 640px;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.meta-chip {
  padding: 14px 16px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.meta-label {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  color: #454652;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.meta-chip strong {
  display: block;
  color: #191c1e;
  font-size: 15px;
  line-height: 1.4;
}

.setup-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: start;
  margin-top: 24px;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.section-block {
  padding: 24px;
}

.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-item {
  padding: 14px 16px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.status-label {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  color: #454652;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.status-item strong {
  color: #191c1e;
  font-size: 13px;
  line-height: 1.7;
}

.resume-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f7f9ff;
  border: 1px solid rgba(58, 56, 139, 0.08);
}

.summary-row span {
  color: #454652;
  font-size: 12px;
  flex-shrink: 0;
}

.summary-row strong {
  color: #191c1e;
  font-size: 13px;
  text-align: right;
}

.target-grid {
  display: grid;
  gap: 16px;
}

.target-input label {
  display: block;
  margin-bottom: 8px;
  color: #454652;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.target-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.preset-chip {
  border: 1px solid rgba(69, 70, 82, 0.12);
  background: #ffffff;
  color: #191c1e;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.preset-chip:hover,
.preset-chip.active {
  border-color: rgba(58, 56, 139, 0.32);
  background: #eef0ff;
  transform: translateY(-1px);
}

.experience-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.experience-card {
  text-align: left;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(69, 70, 82, 0.12);
  background: #faf9f5;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.experience-card:hover,
.experience-card.active {
  border-color: rgba(58, 56, 139, 0.32);
  box-shadow: 0 10px 24px rgba(25, 28, 30, 0.06);
  transform: translateY(-1px);
}

.experience-card strong,
.mode-card strong,
.placeholder-card strong {
  display: block;
  color: #191c1e;
  font-size: 14px;
  line-height: 1.4;
}

.experience-card span,
.mode-card p,
.placeholder-card p {
  display: block;
  color: #454652;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
}

.focus-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.focus-hint {
  margin: 0 0 14px;
  color: #5a6678;
  font-size: 13px;
  line-height: 1.6;
}

.focus-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sticky-panel {
  position: sticky;
  top: 96px;
}

.mode-stack {
  display: grid;
  gap: 12px;
}

.mode-card {
  text-align: left;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(69, 70, 82, 0.12);
  background: #ffffff;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.mode-card:hover,
.mode-card.active {
  border-color: rgba(58, 56, 139, 0.32);
  box-shadow: 0 12px 26px rgba(25, 28, 30, 0.06);
  transform: translateY(-1px);
}

.mode-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.start-actions {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.start-button {
  width: 100%;
  height: 46px;
  border-radius: 12px;
}

.start-blocked-hint {
  margin: 0;
  color: #8a3b32;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.start-button.secondary {
  border-color: rgba(58, 56, 139, 0.14);
}

.placeholder-grid {
  display: grid;
  gap: 12px;
}

.placeholder-card {
  padding: 16px;
  border-radius: 16px;
  background: #faf9f5;
  border: 1px dashed rgba(69, 70, 82, 0.14);
}

.placeholder-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

:deep(.el-checkbox) {
  margin-right: 0;
}

:deep(.el-checkbox.is-bordered) {
  height: auto;
  padding: 12px 14px;
  border-radius: 14px;
  border-color: rgba(69, 70, 82, 0.12);
  background: #ffffff;
}

:deep(.el-checkbox.is-bordered.is-checked) {
  border-color: rgba(58, 56, 139, 0.28);
  background: #eef0ff;
}

:deep(.el-input__wrapper) {
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(69, 70, 82, 0.12);
  border-radius: 12px;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    inset 0 0 0 1px rgba(58, 56, 139, 0.22),
    0 0 0 3px rgba(58, 56, 139, 0.08);
}

:deep(.el-empty) {
  padding: 18px 0 0;
}

@media (max-width: 1100px) {
  .setup-grid {
    grid-template-columns: 1fr;
  }

  .sticky-panel {
    position: static;
  }

  .llm-warning-card {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 960px) {
  .setup-header,
  .hero-panel {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .hero-meta,
  .status-grid,
  .resume-summary,
  .experience-grid,
  .focus-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .setup-header,
  .setup-main {
    padding-left: 16px;
    padding-right: 16px;
  }

  .setup-main {
    padding-top: 20px;
  }

  .section-block,
  .hero-panel {
    padding: 18px 16px;
  }

  .page-title {
    font-size: 20px;
  }
}
</style>
