import { computed, nextTick, ref } from 'vue'
import { getHistoryListAPI } from '@/api/interview'
import { getKnowledgeCoverageAPI } from '@/api/user'
import { getVisiblePositionsAPI } from '@/api/position'
import { normalizeAbility, stripInterviewControlMarkers } from '@/utils/interviewReport'

export const ALL_POSITIONS_VALUE = 'ALL'

export const abilityDimensions = {
  techDepth: { label: '技术深度', color: '#409eff' },
  breadth: { label: '知识广度', color: '#67c23a' },
  problemSolving: { label: '解题思路', color: '#e6a23c' },
  expression: { label: '表达清晰', color: '#f56c6c' },
  logic: { label: '逻辑思维', color: '#909399' },
  adaptability: { label: '应变能力', color: '#c71585' }
}

const gradeScore = { A: 1.0, B: 0.8, C: 0.6, D: 0.4, E: 0.2 }

export const getGradeType = g => ({ A: 'danger', B: 'success', C: 'primary', D: 'warning' }[g] || 'info')
export const getScoreType = s => s >= 85 ? 'success' : s >= 70 ? 'primary' : s >= 55 ? 'warning' : 'danger'

export const formatDate = (d) => d
  ? new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  : '—'

export const excerpt = (t) => {
  const cleaned = stripInterviewControlMarkers(t || '')
  return cleaned ? (cleaned.length > 60 ? cleaned.slice(0, 60) + '...' : cleaned) : ''
}

export function useHistoryData({ redrawChart, selectedRecord } = {}) {
  const loading = ref(true)
  const historyList = ref([])
  const chartMode = ref('score')
  const searchKeyword = ref('')
  const modeFilter = ref('all')
  const knowledgeCoverage = ref(null)
  const positionOptions = ref([])
  const allVisiblePositions = ref([])
  const selectedPositionId = ref(ALL_POSITIONS_VALUE)
  const positionLoading = ref(false)
  const coverageLoading = ref(false)
  const totalHistoryCount = ref(0)

  const sortedHistoryList = computed(() => {
    return [...historyList.value].sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
  })
  const filteredHistoryList = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    return sortedHistoryList.value.filter((row) => {
      const matchesMode = modeFilter.value === 'all'
        ? true
        : modeFilter.value === 'video'
          ? row.interviewMode === 'video'
          : row.interviewMode !== 'video'
      const matchesKeyword = !keyword
        ? true
        : [row.position, row.feedback, row.score, row.voiceWpm]
          .filter(Boolean)
          .some((field) => String(field).toLowerCase().includes(keyword))
      return matchesMode && matchesKeyword
    })
  })
  const visibleHistoryList = computed(() => filteredHistoryList.value)
  const chartData = computed(() => [...visibleHistoryList.value].reverse())
  const latestRecord = computed(() => sortedHistoryList.value[0] || null)
  const previousRecord = computed(() => sortedHistoryList.value[1] || null)
  const activePositionId = computed(() => selectedPositionId.value === ALL_POSITIONS_VALUE ? null : selectedPositionId.value)
  const selectedScopeLabel = computed(() => activePositionId.value ? '当前岗位' : '全部岗位')
  const averageScore = computed(() => {
    if (!sortedHistoryList.value.length) return 0
    const total = sortedHistoryList.value.reduce((sum, row) => sum + (Number(row.score) || 0), 0)
    return Math.round(total / sortedHistoryList.value.length)
  })
  const scoreDelta = computed(() => {
    if (!latestRecord.value || !previousRecord.value) return null
    return Number(latestRecord.value.score || 0) - Number(previousRecord.value.score || 0)
  })
  const scoreDeltaText = computed(() => {
    if (scoreDelta.value == null) return '暂无前后对比'
    const prefix = scoreDelta.value > 0 ? '+' : ''
    return `较上一场 ${prefix}${scoreDelta.value} 分`
  })
  const summaryCards = computed(() => {
    const total = sortedHistoryList.value.length
    const videoCount = sortedHistoryList.value.filter((row) => row.interviewMode === 'video').length
    const textCount = total - videoCount
    const latest = latestRecord.value
    return [
      { label: '累计报告', value: total || '--', hint: total ? `${selectedScopeLabel.value}已归档记录` : '等待面试结束后生成' },
      { label: '平均得分', value: total ? `${averageScore.value}` : '--', hint: total ? `基于${selectedScopeLabel.value}记录` : '暂无可计算数据' },
      { label: '视频 / 文字', value: total ? `${videoCount} / ${textCount}` : '--', hint: '按面试模式拆分' },
      { label: '最近更新', value: latest ? formatDate(latest.createTime) : '--', hint: latest ? latest.position : '尚未有新报告' }
    ]
  })
  const overviewMetrics = computed(() => [
    {
      kicker: '报告数',
      label: '累计报告',
      value: sortedHistoryList.value.length || '--',
      trend: sortedHistoryList.value.length ? '稳步积累' : '待开始',
      tagType: 'info',
      description: `${selectedScopeLabel.value}的归档面试记录汇总。`
    },
    {
      kicker: '平均分',
      label: '平均得分',
      value: sortedHistoryList.value.length ? `${averageScore.value}` : '--',
      trend: sortedHistoryList.value.length ? '基线' : '暂无',
      tagType: sortedHistoryList.value.length ? 'success' : 'info',
      description: '用来快速判断整体稳定性。'
    },
    {
      kicker: '模式分布',
      label: '视频 / 文字',
      value: sortedHistoryList.value.length
        ? `${sortedHistoryList.value.filter((row) => row.interviewMode === 'video').length} / ${sortedHistoryList.value.filter((row) => row.interviewMode !== 'video').length}`
        : '--',
      trend: '结构',
      tagType: 'primary',
      description: '帮助查看训练模式的分布。'
    },
    {
      kicker: '变化趋势',
      label: '最近变化',
      value: scoreDelta.value == null ? '--' : `${scoreDelta.value > 0 ? '+' : ''}${scoreDelta.value}`,
      trend: scoreDelta.value == null ? '无对比' : scoreDelta.value > 0 ? '上升' : scoreDelta.value < 0 ? '回落' : '持平',
      tagType: scoreDelta.value == null ? 'info' : scoreDelta.value > 0 ? 'success' : scoreDelta.value < 0 ? 'warning' : 'info',
      description: '和上一场面试对比的分数变化。'
    }
  ])
  const strongestAbility = computed(() => {
    const source = selectedRecord?.value || latestRecord.value
    const ability = normalizeAbility(source?.abilityJson)
    const entries = Object.entries(ability).filter(([key]) => abilityDimensions[key])
    if (!entries.length) {
      return { label: '暂无画像', grade: '--', description: '等到报告详情展开后，会在这里显示主能力项。' }
    }

    let bestKey = entries[0][0]
    let bestVal = entries[0][1]
    entries.forEach(([key, grade]) => {
      if ((gradeScore[grade] || 0) > (gradeScore[bestVal] || 0)) {
        bestKey = key
        bestVal = grade
      }
    })

    return {
      label: abilityDimensions[bestKey]?.label || bestKey,
      grade: bestVal,
      description: '当前最稳定的能力项'
    }
  })

  const fetchPositions = async () => {
    positionLoading.value = true
    try {
      const data = await getVisiblePositionsAPI()
      positionOptions.value = (data || []).filter(p => p.historyCount > 0)
      allVisiblePositions.value = data || []
      totalHistoryCount.value = (data || []).reduce((sum, p) => sum + p.historyCount, 0)
    } catch {
      positionOptions.value = []
      allVisiblePositions.value = []
      totalHistoryCount.value = 0
    } finally {
      positionLoading.value = false
    }
  }

  const fetchHistory = async () => {
    loading.value = true
    try {
      const params = activePositionId.value ? { positionId: activePositionId.value } : undefined
      historyList.value = await getHistoryListAPI(params)
    } catch {
      historyList.value = []
    } finally {
      loading.value = false
      nextTick(() => { redrawChart?.() })
    }
  }

  const fetchCoverage = async () => {
    coverageLoading.value = true
    try {
      const params = activePositionId.value ? { positionId: activePositionId.value } : undefined
      const insight = await getKnowledgeCoverageAPI(params)
      knowledgeCoverage.value = insight?.knowledgeCoverage || null
    } catch {
      knowledgeCoverage.value = null
    } finally {
      coverageLoading.value = false
    }
  }

  const onPositionChange = async () => {
    await fetchHistory()
    await fetchCoverage()
  }

  const initializeHistory = async () => {
    await Promise.all([fetchPositions(), fetchHistory()])

    if (positionOptions.value.length && latestRecord.value?.positionId) {
      const matched = positionOptions.value.find(p => p.id === latestRecord.value.positionId)
      if (matched) {
        selectedPositionId.value = matched.id
        await fetchHistory()
      }
    }

    await fetchCoverage()
  }

  const clearFilters = () => {
    modeFilter.value = 'all'
    searchKeyword.value = ''
  }

  return {
    loading,
    historyList,
    chartMode,
    searchKeyword,
    modeFilter,
    knowledgeCoverage,
    positionOptions,
    allVisiblePositions,
    selectedPositionId,
    positionLoading,
    coverageLoading,
    totalHistoryCount,
    sortedHistoryList,
    visibleHistoryList,
    chartData,
    latestRecord,
    activePositionId,
    averageScore,
    scoreDelta,
    scoreDeltaText,
    summaryCards,
    overviewMetrics,
    strongestAbility,
    fetchHistory,
    fetchCoverage,
    onPositionChange,
    initializeHistory,
    clearFilters
  }
}
