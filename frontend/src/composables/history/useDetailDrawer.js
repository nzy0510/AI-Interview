import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getInterviewReportAPI, retryJobAPI } from '@/api/interview'
import { normalizeKnowledgePoints } from '@/utils/reportMetrics'
import {
  canRetryDetailedReport,
  normalizeAbility,
  parseStructuredField,
  stripInterviewControlMarkers
} from '@/utils/interviewReport'

const EMOTION_LABELS = {
  neutral: '平静',
  happy: '积极',
  sad: '低落',
  angry: '紧张',
  fearful: '焦虑',
  disgusted: '不适',
  surprised: '惊讶'
}

export const emotionLabel = (key) => EMOTION_LABELS[key] || key
export const emotionColor = (key) => ({
  neutral: '#909399',
  happy: '#67C23A',
  sad: '#5B9BD5',
  angry: '#F56C6C',
  fearful: '#E6A23C',
  disgusted: '#C71585',
  surprised: '#409EFF'
}[key] || '#909399')

export const phaseLabel = (phase) => ({
  TECHNICAL: '技术轮',
  HR: 'HR 轮',
  OPENING: '开场',
  FINISHED: '结束'
}[phase] || phase || '未知轮次')

export const formatAnswerSource = (source) => ({
  KNOWLEDGE_BASE: '知识库命中',
  AI_GENERATED: 'AI 生成'
}[source] || source || '暂无来源')

export const formatItemScore = (score) => {
  const numeric = Number(score)
  return Number.isFinite(numeric) ? `${numeric.toFixed(1)} / 10` : '-- / 10'
}

export const formatSnapshot = (snapshotJson) => {
  if (!snapshotJson) return ''
  try {
    const parsed = JSON.parse(snapshotJson)
    return parsed.promptContext || ''
  } catch {
    return ''
  }
}

export const formatReferenceAnswer = (referenceAnswer) => {
  if (!referenceAnswer) return '暂无参考答案'
  try {
    const parsed = JSON.parse(referenceAnswer)
    return parsed.promptContext || referenceAnswer
  } catch {
    return referenceAnswer
  }
}

export function useDetailDrawer() {
  const drawerOpen = ref(false)
  const selected = ref(null)
  const selectedDetailedReport = ref(null)
  const detailLoading = ref(false)
  const detailRetrying = ref(false)
  const detailError = ref('')
  let detailRequestSeq = 0

  const selectedAbility = computed(() => {
    return normalizeAbility(selected.value?.abilityJson)
  })
  const selectedRecs = computed(() => {
    const recommendations = parseStructuredField(selected.value?.recommendations, [])
    return Array.isArray(recommendations) ? recommendations : []
  })
  const selectedEmotion = computed(() => {
    const emotion = parseStructuredField(selected.value?.emotionJson, null)
    return emotion && typeof emotion === 'object' ? emotion : null
  })
  const selectedFeedback = computed(() => stripInterviewControlMarkers(selected.value?.feedback || ''))
  const selectedKnowledgePoints = computed(() => {
    return normalizeKnowledgePoints(selected.value?.knowledgeJson)
  })
  const detailedReportItems = computed(() => {
    return Array.isArray(selectedDetailedReport.value?.items) ? selectedDetailedReport.value.items : []
  })
  const detailStatusText = computed(() => {
    if (detailLoading.value) return '加载中'
    if (detailError.value) return '未完成'
    const status = selectedDetailedReport.value?.status
    if (status === 'COMPLETED') return '已生成'
    if (status === 'FAILED') return '生成失败'
    if (status === 'RUNNING') return '生成中'
    if (status === 'PENDING') return '排队中'
    return '未生成'
  })
  const detailStatusType = computed(() => {
    if (selectedDetailedReport.value?.status === 'COMPLETED') return 'success'
    if (selectedDetailedReport.value?.status === 'FAILED') return 'danger'
    if (detailLoading.value || selectedDetailedReport.value?.status === 'RUNNING') return 'primary'
    return 'info'
  })
  const detailFailureMessage = computed(() => {
    if (selectedDetailedReport.value?.status !== 'FAILED') return ''
    return selectedDetailedReport.value?.errorMessage || '详细报告生成失败，请稍后重试'
  })
  const canRetrySelectedDetailedReport = computed(() => canRetryDetailedReport(selectedDetailedReport.value))

  const openDetail = async (row) => {
    selected.value = row
    selectedDetailedReport.value = null
    detailError.value = ''
    drawerOpen.value = true
    const requestSeq = ++detailRequestSeq
    detailLoading.value = true
    try {
      const report = await getInterviewReportAPI(row.id, { silent: true })
      if (requestSeq === detailRequestSeq) {
        selectedDetailedReport.value = report
      }
    } catch (err) {
      if (requestSeq === detailRequestSeq) {
        detailError.value = err?.message || '详细报告尚未生成，请稍后刷新查看'
      }
    } finally {
      if (requestSeq === detailRequestSeq) {
        detailLoading.value = false
      }
    }
  }

  const reloadSelectedDetailedReport = async () => {
    if (!selected.value?.id) return
    const report = await getInterviewReportAPI(selected.value.id, { silent: true })
    selectedDetailedReport.value = report
  }

  const retryDetailedReport = async () => {
    if (!canRetrySelectedDetailedReport.value || detailRetrying.value) return
    detailRetrying.value = true
    try {
      await retryJobAPI(selectedDetailedReport.value.jobId)
      ElMessage.success('已重新提交详细报告生成任务')
      await reloadSelectedDetailedReport()
    } catch (err) {
      ElMessage.error(err?.message || '重新生成失败，请稍后重试')
    } finally {
      detailRetrying.value = false
    }
  }

  return {
    drawerOpen,
    selected,
    selectedDetailedReport,
    detailLoading,
    detailRetrying,
    detailError,
    selectedAbility,
    selectedRecs,
    selectedEmotion,
    selectedFeedback,
    selectedKnowledgePoints,
    detailedReportItems,
    detailStatusText,
    detailStatusType,
    detailFailureMessage,
    canRetrySelectedDetailedReport,
    openDetail,
    retryDetailedReport
  }
}
