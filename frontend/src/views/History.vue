<template>
  <div class="history-page">
    <header class="page-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">面试报告</p>
          <h1 class="page-title">面试评估报告</h1>
          <p class="page-subtitle">把每一次面试整理成可回看的趋势、能力与反馈档案。</p>
        </div>
      </div>

      <div class="header-actions">
        <el-tag effect="plain" type="info" class="status-pill">历史归档</el-tag>
        <el-button type="primary" class="primary-cta" @click="router.push('/interview/setup')">开始面试</el-button>
      </div>
    </header>

    <el-main class="page-body" v-loading="loading">
      <section class="surface-card hero-shell">
        <div class="hero-copy">
          <p class="section-kicker">{{ reportCenter.hero.kicker }}</p>
          <h2 class="hero-title">{{ reportCenter.hero.title }}</h2>
          <p class="hero-desc">{{ reportCenter.hero.description }}</p>
          <div class="hero-tags">
            <el-tag v-for="tag in reportCenter.hero.tags" :key="tag" effect="plain" class="hero-tag">
              {{ tag }}
            </el-tag>
          </div>
        </div>

        <div class="hero-side">
          <div class="recent-box">
            <div class="recent-label">最近表现</div>
            <div v-if="latestRecord" class="recent-main">
              <strong>{{ latestRecord.score }}</strong>
              <span>分</span>
            </div>
            <div v-else class="recent-main empty">
              <strong>--</strong>
            </div>
            <div class="recent-sub">
              <span>{{ latestRecord ? latestRecord.position : '暂无记录' }}</span>
              <span>{{ latestRecord ? formatDate(latestRecord.createTime) : '等待新的面试结果' }}</span>
            </div>
          </div>

          <div class="summary-grid">
            <article v-for="item in summaryCards" :key="item.label" class="summary-tile">
              <span class="summary-label">{{ item.label }}</span>
              <strong class="summary-value">{{ item.value }}</strong>
              <span class="summary-hint">{{ item.hint }}</span>
            </article>
          </div>
        </div>
      </section>

      <template v-if="!loading && historyList.length === 0">
        <section class="surface-card empty-shell">
          <el-empty :description="reportCenter.emptyStates.all">
            <el-button type="primary" class="primary-cta" @click="router.push('/interview/setup')">开始面试</el-button>
          </el-empty>
        </section>
      </template>

      <template v-else>
        <section class="surface-card section-shell overview-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">数据概览</p>
              <h2 class="section-title">筛选与摘要</h2>
              <p class="section-desc">先从模式和关键字收窄范围，再看趋势和列表。</p>
            </div>
            <div class="toolbar">
              <el-input
                v-model="searchKeyword"
                class="search-input"
                clearable
                :prefix-icon="Search"
                placeholder="搜索岗位、反馈或摘要"
              />
              <el-radio-group v-model="modeFilter" size="small" class="mode-switch">
                <el-radio-button v-for="item in reportCenter.filters" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <div class="metric-grid">
            <article v-for="metric in overviewMetrics" :key="metric.label" class="metric-card">
              <div class="metric-head">
                <span class="metric-kicker">{{ metric.kicker }}</span>
                <el-tag size="small" effect="plain" :type="metric.tagType">{{ metric.trend }}</el-tag>
              </div>
              <strong class="metric-value">{{ metric.value }}</strong>
              <span class="metric-label">{{ metric.label }}</span>
              <p class="metric-desc">{{ metric.description }}</p>
            </article>
          </div>
        </section>

        <section class="surface-card section-shell chart-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">成长趋势</p>
              <h2 class="section-title">能力成长曲线</h2>
              <p class="section-desc">在评分和能力热力图之间切换，观察长期变化趋势。</p>
            </div>
            <div class="chart-actions">
              <el-radio-group v-model="chartMode" size="small" class="mode-switch" @change="drawGrowthChart">
                <el-radio-button value="score">综合得分</el-radio-button>
                <el-radio-button value="radar">能力热力图</el-radio-button>
              </el-radio-group>
              <el-button :icon="RefreshRight" plain size="small" @click="refreshChart">刷新图表</el-button>
            </div>
          </div>
          <div class="chart-wrap">
            <div ref="growthChartRef" class="echarts-growth-container"></div>
            <el-empty
              v-if="!loading && visibleHistoryList.length === 0"
              class="chart-empty"
              :description="reportCenter.emptyStates.filtered"
            />
          </div>
        </section>

        <section class="surface-card section-shell performance-shell">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">近期表现</p>
              <h2 class="section-title">最近表现与能力画像</h2>
            </div>
          </div>

          <div v-if="latestRecord" class="performance-grid">
            <article class="performance-block">
              <span class="performance-label">最近岗位</span>
              <strong>{{ latestRecord.position }}</strong>
              <p>{{ latestRecord.interviewMode === 'video' ? '视频面试' : '文字面试' }} · {{ formatDate(latestRecord.createTime) }}</p>
            </article>
            <article class="performance-block">
              <span class="performance-label">最近得分</span>
              <strong>{{ latestRecord.score }}</strong>
              <p>{{ scoreDeltaText }}</p>
            </article>
            <article class="performance-block">
              <span class="performance-label">重点能力</span>
              <strong>{{ strongestAbility.label }}</strong>
              <p>{{ strongestAbility.grade }} 级 · {{ strongestAbility.description }}</p>
            </article>
          </div>
          <el-empty v-else :description="reportCenter.emptyStates.all" />
        </section>

        <section v-if="knowledgeCoverage?.details?.length" class="surface-card section-shell coverage-section">
          <div class="section-head">
            <div>
              <p class="section-kicker">知识覆盖</p>
              <h2 class="section-title">知识领域覆盖</h2>
              <p class="section-desc">基于所有历史面试中 RAG 真实命中的知识原子，按分类统计覆盖度。</p>
            </div>
            <el-tag effect="plain" type="info">{{ knowledgeCoverage.details.length }} 个领域</el-tag>
          </div>
          <KnowledgeCoverageChart :details="knowledgeCoverage.details" />
        </section>

        <section class="surface-card section-shell list-shell">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">面试记录</p>
              <h2 class="section-title">历史面试记录</h2>
            </div>
            <div class="list-meta">
              <el-tag effect="plain" type="info">{{ visibleHistoryList.length }} 条结果</el-tag>
            </div>
          </div>
          <div v-if="!loading && visibleHistoryList.length === 0" class="list-empty">
            <el-empty :description="reportCenter.emptyStates.filtered">
              <el-button type="primary" class="primary-cta" @click="clearFilters">清空筛选</el-button>
            </el-empty>
          </div>
          <div v-else class="table-shell">
            <el-table :data="visibleHistoryList" stripe @row-click="openDetail" row-class-name="table-row">
              <el-table-column label="日期" width="170">
                <template #default="{ row }">
                  {{ formatDate(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="position" label="面试岗位" width="160" />
              <el-table-column label="模式" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.interviewMode === 'video' ? 'success' : 'info'" size="small" effect="plain">
                    {{ row.interviewMode === 'video' ? '视频' : '文字' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="综合得分" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="getScoreType(row.score)" effect="dark">{{ row.score }} 分</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="语速 WPM" width="110" align="center">
                <template #default="{ row }">
                  <span class="wpm-val">{{ row.voiceWpm > 0 ? row.voiceWpm : '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="AI 点评摘要">
                <template #default="{ row }">
                  <span class="feedback-excerpt">{{ excerpt(row.feedback) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" plain @click.stop="openDetail(row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </template>
    </el-main>

    <el-drawer v-model="drawerOpen" title="面试报告详情" size="clamp(320px, 92vw, 620px)" direction="rtl">
      <div v-if="selected" class="drawer-body">
        <div class="drawer-hero">
          <div class="drawer-heading">
            <el-tag size="large" type="info" plain>{{ selected.position }}</el-tag>
            <span class="detail-date">{{ formatDate(selected.createTime) }}</span>
          </div>
          <div class="score-badge">
            <span class="score-label">综合得分</span>
            <strong>{{ selected.score }}</strong>
            <span>/ 100</span>
          </div>
        </div>

        <section class="drawer-panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">能力雷达</p>
              <h2 class="section-title">六维能力评级</h2>
            </div>
          </div>
          <div class="mini-radar-wrap">
            <div ref="miniRadarRef" class="echarts-mini-radar"></div>
            <div class="mini-legend">
              <div v-for="(dim, key) in abilityDimensions" :key="key" class="legend-row">
                <span class="l-dot" :style="{ background: dim.color }"></span>
                <span class="l-name">{{ dim.label }}</span>
                <el-tag :type="getGradeType(selectedAbility[key])" size="small">{{ selectedAbility[key] || '—' }}</el-tag>
              </div>
            </div>
          </div>
        </section>

        <section class="drawer-panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">AI 点评</p>
              <h2 class="section-title">综合反馈</h2>
            </div>
          </div>
          <div class="feedback-box"><pre class="feedback-text">{{ selected.feedback }}</pre></div>
        </section>

        <template v-if="selectedEmotion && Object.keys(selectedEmotion).length > 0">
          <section class="drawer-panel">
            <div class="section-head compact">
              <div>
                <p class="section-kicker">情绪分析</p>
                <h2 class="section-title">情感分析</h2>
              </div>
              <el-tag size="small" :type="selectedEmotion.source === 'video' ? 'success' : 'primary'" effect="plain">
                {{ selectedEmotion.source === 'video' ? '视频模式' : '文本分析' }}
              </el-tag>
            </div>
            <div class="emotion-section">
              <div class="emotion-metrics">
                <div class="em-metric">
                  <span class="em-val green">{{ (selectedEmotion.avgConfidence * 100).toFixed(0) }}%</span>
                  <span class="em-label">自信指数</span>
                </div>
                <div class="em-metric">
                  <span class="em-val orange">{{ emotionLabel(selectedEmotion.dominantEmotion) }}</span>
                  <span class="em-label">主导情绪</span>
                </div>
                <div class="em-metric" v-if="selectedEmotion.sampleCount">
                  <span class="em-val blue">{{ selectedEmotion.sampleCount }}</span>
                  <span class="em-label">采样次数</span>
                </div>
              </div>
              <div v-if="selectedEmotion.emotionDistribution" class="emotion-bars">
                <div v-for="(val, key) in selectedEmotion.emotionDistribution" :key="key" class="em-bar-row">
                  <span class="em-name">{{ emotionLabel(key) }}</span>
                  <div class="em-bar-bg">
                    <div class="em-bar-fill" :style="{ width: (val * 100) + '%', background: emotionColor(key) }"></div>
                  </div>
                  <span class="em-pct">{{ (val * 100).toFixed(0) }}%</span>
                </div>
              </div>
              <div v-if="selectedEmotion.summary" class="emotion-summary-text">
                <p>{{ selectedEmotion.summary }}</p>
              </div>
            </div>
          </section>
        </template>

        <template v-if="selectedKnowledgePoints.length">
          <section class="drawer-panel">
            <div class="section-head compact">
              <div>
                <p class="section-kicker">知识点评估</p>
                <h2 class="section-title">本场考察知识点</h2>
              </div>
              <el-tag size="small" effect="plain" type="primary">{{ selectedKnowledgePoints.length }} 个知识点</el-tag>
            </div>
            <div class="knowledge-bars">
              <div v-for="k in selectedKnowledgePoints" :key="`${k.concept}-${k.category}`" class="knowledge-bar-row">
                <div class="knowledge-bar-meta">
                  <strong>{{ k.concept }}</strong>
                  <span>{{ k.category }}</span>
                </div>
                <div class="knowledge-bar-track" aria-hidden="true">
                  <div class="knowledge-bar-fill" :style="{ width: `${k.percent}%` }"></div>
                </div>
                <span class="knowledge-bar-value">掌握度 {{ k.percent }}%</span>
              </div>
            </div>
          </section>
        </template>

        <section class="drawer-panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">后续行动</p>
              <h2 class="section-title">提升建议</h2>
            </div>
          </div>
          <el-timeline v-if="selectedRecs.length">
            <el-timeline-item
              v-for="(r, i) in selectedRecs"
              :key="i"
              :timestamp="r.period"
              placement="top"
              :type="['primary', 'success', 'warning'][i % 3]"
            >
              <div class="rec-action">{{ r.action }}</div>
              <div class="rec-detail">{{ r.detail }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无建议" :image-size="60" />
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, RefreshRight, Search } from '@element-plus/icons-vue'
import { getHistoryListAPI } from '@/api/interview'
import { getKnowledgeCoverageAPI } from '@/api/user'
import { reportCenterConfig } from '@/mock/reports'
import * as echarts from 'echarts'
import KnowledgeCoverageChart from '@/components/charts/KnowledgeCoverageChart.vue'
import { buildTooltipConfig, buildHeatmapVisualMap, buildHeatmapData } from '@/utils/chartOptions'
import { normalizeKnowledgePoints } from '@/utils/reportMetrics'

const router = useRouter()
const loading = ref(true)
const historyList = ref([])
const chartMode = ref('score')
const searchKeyword = ref('')
const modeFilter = ref('all')
const growthChartRef = ref(null)
const miniRadarRef = ref(null)
const knowledgeCoverage = ref(null)
let growthChartInstance = null
let miniRadarInstance = null
const drawerOpen = ref(false)
const selected = ref(null)
const reportCenter = reportCenterConfig

const abilityDimensions = {
  techDepth:      { label: '技术深度', color: '#409eff' },
  breadth:        { label: '知识广度', color: '#67c23a' },
  problemSolving: { label: '解题思路', color: '#e6a23c' },
  expression:     { label: '表达清晰', color: '#f56c6c' },
  logic:          { label: '逻辑思维', color: '#909399' },
  adaptability:   { label: '应变能力', color: '#c71585' }
}

const gradeScore = { A: 1.0, B: 0.8, C: 0.6, D: 0.4, E: 0.2 }
const getGradeType = g => ({ A: 'danger', B: 'success', C: 'primary', D: 'warning' }[g] || 'info')
const getScoreType = s => s >= 85 ? 'success' : s >= 70 ? 'primary' : s >= 55 ? 'warning' : 'danger'

const selectedAbility = computed(() => {
  try { return selected.value?.abilityJson ? JSON.parse(selected.value.abilityJson) : {} }
  catch { return {} }
})
const selectedRecs = computed(() => {
  try { return selected.value?.recommendations ? JSON.parse(selected.value.recommendations) : [] }
  catch { return [] }
})
const selectedEmotion = computed(() => {
  try { return selected.value?.emotionJson ? JSON.parse(selected.value.emotionJson) : null }
  catch { return null }
})

const EMOTION_LABELS = { neutral: '平静', happy: '积极', sad: '低落', angry: '紧张', fearful: '焦虑', disgusted: '不适', surprised: '惊讶' }
const emotionLabel = (key) => EMOTION_LABELS[key] || key
const emotionColor = (key) => ({ neutral: '#909399', happy: '#67C23A', sad: '#5B9BD5', angry: '#F56C6C', fearful: '#E6A23C', disgusted: '#C71585', surprised: '#409EFF' }[key] || '#909399')

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
    { label: '累计报告', value: total || '--', hint: total ? '所有已归档记录' : '等待面试结束后生成' },
    { label: '平均得分', value: total ? `${averageScore.value}` : '--', hint: total ? '基于全部历史记录' : '暂无可计算数据' },
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
    description: '所有归档面试记录都会在这里汇总。'
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
  const source = selected.value || latestRecord.value
  let ability = {}
  try { ability = source?.abilityJson ? JSON.parse(source.abilityJson) : {} } catch { ability = {} }
  const entries = Object.entries(ability)
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

onMounted(async () => {
  try {
    historyList.value = await getHistoryListAPI()
  } catch { historyList.value = [] }
  finally {
    loading.value = false
    nextTick(() => { drawGrowthChart() })
  }
  // 知识覆盖异步加载，不阻塞页面渲染
  try {
    const insight = await getKnowledgeCoverageAPI()
    if (insight?.knowledgeCoverage) knowledgeCoverage.value = insight.knowledgeCoverage
  } catch { /* optional */ }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (growthChartInstance) growthChartInstance.dispose()
  if (miniRadarInstance) miniRadarInstance.dispose()
})

const handleResize = () => {
  if (growthChartInstance) growthChartInstance.resize()
  if (miniRadarInstance) miniRadarInstance.resize()
}

watch(drawerOpen, (v) => {
  if (v) nextTick(() => drawMiniRadar())
})

watch([chartMode, visibleHistoryList], () => {
  nextTick(() => drawGrowthChart())
}, { deep: true })

// ─── Helpers ──────────────────────────────────────────────────────────────────
const formatDate = (d) => d ? new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'
const excerpt = (t) => t ? (t.length > 60 ? t.slice(0, 60) + '...' : t) : ''

const openDetail = (row) => {
  selected.value = row
  drawerOpen.value = true
}

const clearFilters = () => {
  modeFilter.value = 'all'
  searchKeyword.value = ''
}

const refreshChart = () => {
  nextTick(() => drawGrowthChart())
}

// ─── Growth Trend Chart ───────────────────────────────────────────────────────
const drawGrowthChart = () => {
  const container = growthChartRef.value
  if (!container || chartData.value.length === 0) {
    if (growthChartInstance) growthChartInstance.clear()
    return
  }

  if (!growthChartInstance) {
    growthChartInstance = echarts.init(container)
  }

  const data = chartData.value
  const xAxisData = data.map(r => formatDate(r.createTime).split(' ')[0])

  if (chartMode.value === 'score') {
    const scores = data.map(r => r.score || 0)

    const option = {
      grid: { top: 40, right: 30, bottom: 40, left: 50 },
      tooltip: buildTooltipConfig({ trigger: 'axis' }),
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLine: { lineStyle: { color: '#cfcdc4' } },
        axisLabel: { color: '#5e5d59' }
      },
      yAxis: {
        type: 'value',
        min: 'dataMin',
        max: 'dataMax',
        splitLine: { lineStyle: { color: '#e8e6dc', type: 'dashed' } },
        axisLabel: { color: '#5e5d59' }
      },
      series: [
        {
          name: '综合得分',
          data: scores,
          type: 'line',
          smooth: true,
          symbolSize: 8,
          itemStyle: { color: '#c96442' },
          lineStyle: { color: '#c96442', width: 3 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(201, 100, 66, 0.35)' },
              { offset: 1, color: 'rgba(201, 100, 66, 0.0)' }
            ])
          }
        }
      ]
    }
    growthChartInstance.setOption(option, true)
  } else if (chartMode.value === 'radar') {
    // Heatmap mode
    const dimKeys = Object.keys(abilityDimensions)
    const dimLabels = Object.values(abilityDimensions).map(d => d.label)
    const { data: hData, yAxisData } = buildHeatmapData(data, dimKeys, dimLabels)

    const option = {
      grid: { top: 30, right: 30, bottom: 50, left: 80 },
      tooltip: {
        ...buildTooltipConfig(),
        position: 'top',
        formatter: (params) => `${params.name} <br/> 维度: <b>${yAxisData[params.value[1]]}</b> <br/> 评级: <b>${params.value[3]}</b>`
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLine: { lineStyle: { color: '#cfcdc4' } },
        axisLabel: { color: '#5e5d59' },
        splitArea: { show: true, areaStyle: { color: ['rgba(0,0,0,0.02)', 'transparent'] } }
      },
      yAxis: {
        type: 'category',
        data: yAxisData,
        axisLine: { lineStyle: { color: '#cfcdc4' } },
        axisLabel: { color: '#5e5d59' }
      },
      visualMap: buildHeatmapVisualMap(),
      series: [{
        name: '能力评级',
        type: 'heatmap',
        data: hData,
        label: {
          show: true,
          formatter: (p) => p.data[3],
          color: '#141413',
          fontSize: 12,
          fontWeight: 'bold'
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(201, 100, 66, 0.3)'
          }
        },
        itemStyle: {
          borderColor: '#faf9f5',
          borderWidth: 2,
          borderRadius: 4
        }
      }]
    }
    growthChartInstance.setOption(option, true)
  }
}

// ─── Drawer Derived Data ─────────────────────────────────────────────────────
const selectedKnowledgePoints = computed(() => {
  return normalizeKnowledgePoints(selected.value?.knowledgeJson)
})

// ─── Mini Radar ───────────────────────────────────────────────────────────────
const drawMiniRadar = () => {
  const container = miniRadarRef.value
  if (!container || !selected.value) return
  if (!miniRadarInstance) miniRadarInstance = echarts.init(container)

  const ability = selectedAbility.value
  const gradeToNum = (grade) => {
    const map = { A: 95, B: 80, C: 65, D: 45, E: 20 }
    return map[grade] || 20
  }
  const scores = [
    gradeToNum(ability.techDepth),
    gradeToNum(ability.breadth),
    gradeToNum(ability.logic),
    gradeToNum(ability.expression),
    gradeToNum(ability.adaptability),
    gradeToNum(ability.problemSolving)
  ]

  const option = {
    radar: {
      indicator: [
        { name: '技术深度', max: 100 },
        { name: '知识广度', max: 100 },
        { name: '逻辑思维', max: 100 },
        { name: '表达清晰', max: 100 },
        { name: '应变能力', max: 100 },
        { name: '解题思路', max: 100 }
      ],
      shape: 'polygon',
      axisName: { color: '#5e5d59', fontSize: 12 },
      splitNumber: 4,
      splitArea: { areaStyle: { color: ['rgba(201, 100, 66, 0.05)', 'transparent'] } },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.1)' } },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: scores,
        symbolSize: 4,
        itemStyle: { color: '#c96442' },
        lineStyle: { width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(201, 100, 66, 0.45)' },
            { offset: 1, color: 'rgba(201, 100, 66, 0.08)' }
          ])
        }
      }]
    }]
  }
  miniRadarInstance.setOption(option)
}
</script>

<style scoped>
.history-page {
  min-height: 100vh;
  background: #f5f4ed;
  color: #141413;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 32px;
  background: rgba(247, 249, 251, 0.88);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(69, 70, 82, 0.08);
}

.brand-cluster {
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

.page-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
  color: #191c1e;
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
  flex: 0 0 auto;
}

.status-pill {
  border-color: rgba(58, 56, 139, 0.12);
  color: #3a388b;
  background: #eef0ff;
}

.primary-cta {
  border-radius: 12px;
}

.page-body {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-sizing: border-box;
}

.surface-card {
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.section-shell {
  padding: 24px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.section-head.compact {
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.25;
  font-weight: 800;
  color: #191c1e;
}

.hero-shell {
  padding: 24px;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.9fr);
  gap: 20px;
}

.hero-copy {
  min-width: 0;
}

.hero-title {
  font-size: 28px;
  line-height: 1.15;
  max-width: 720px;
}

.hero-desc {
  max-width: 720px;
}

.hero-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.hero-tag {
  border-color: rgba(58, 56, 139, 0.12);
  background: #f4f3ff;
  color: #3a388b;
}

.hero-side {
  display: grid;
  gap: 14px;
  align-content: start;
}

.recent-box {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.recent-label {
  font-size: 12px;
  color: #5a6678;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 8px;
}

.recent-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.recent-main strong {
  font-size: 44px;
  line-height: 1;
  color: #3a388b;
}

.recent-main span {
  color: #5a6678;
  font-size: 14px;
}

.recent-main.empty strong {
  font-size: 30px;
  color: #94a3b8;
}

.recent-sub {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  color: #5a6678;
  font-size: 13px;
}

.overview-shell {
  padding-top: 24px;
}

.toolbar {
  display: grid;
  gap: 12px;
  justify-items: end;
  min-width: min(100%, 440px);
}

.search-input {
  width: min(100%, 340px);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.metric-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.metric-value {
  display: block;
  font-size: 28px;
  line-height: 1;
  color: #191c1e;
  margin-bottom: 6px;
}

.metric-label {
  display: block;
  color: #454652;
  font-weight: 700;
  margin-bottom: 6px;
}

.metric-desc {
  margin: 0;
  color: #5a6678;
  font-size: 13px;
  line-height: 1.6;
}

.mode-switch {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chart-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chart-wrap {
  position: relative;
}

.echarts-growth-container {
  width: 100%;
  height: 340px;
}

.chart-empty {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(4px);
  display: grid;
  place-items: center;
}

.performance-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.performance-block {
  padding: 18px;
  border-radius: 14px;
  border: 1px solid rgba(69, 70, 82, 0.08);
  background: #faf9f5;
}

.performance-label {
  display: block;
}

.performance-block strong {
  display: block;
  font-size: 20px;
  line-height: 1.25;
  color: #191c1e;
  margin-bottom: 8px;
}

.performance-block p {
  margin: 0;
  color: #5a6678;
  font-size: 13px;
  line-height: 1.6;
}

.list-meta {
  display: flex;
  align-items: center;
}

.list-empty {
  padding: 18px 0 6px;
}

.table-shell {
  overflow-x: auto;
}

.table-shell :deep(.el-table) {
  min-width: 920px;
  background: transparent !important;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #faf9f5;
  --el-table-row-hover-bg-color: #f3f4f6;
  color: #191c1e;
}

.table-shell :deep(.el-table th),
.table-shell :deep(.el-table td) {
  border-bottom: 1px solid rgba(69, 70, 82, 0.08) !important;
}

.table-row {
  cursor: pointer;
}

.wpm-val {
  color: #191c1e;
  font-weight: 700;
}

.feedback-excerpt {
  color: #454652;
  font-size: 13px;
  font-style: italic;
}

.empty-shell {
  padding: 48px 24px;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 0 8px;
}

.drawer-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.drawer-heading {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.score-badge {
  min-width: 110px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f7f9fb;
  border: 1px solid rgba(69, 70, 82, 0.08);
  text-align: right;
}

.score-label {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: #454652;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.score-badge strong {
  font-size: 32px;
  line-height: 1;
  color: #3a388b;
}

.score-badge span:last-child {
  color: #454652;
  font-size: 12px;
}

.drawer-panel {
  padding-top: 4px;
}

.mini-radar-wrap {
  display: flex;
  align-items: center;
  gap: 24px;
  justify-content: space-between;
}

.echarts-mini-radar {
  width: 260px;
  height: 260px;
  flex-shrink: 0;
}

.mini-legend {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 160px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.l-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.l-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #454652;
}

.feedback-box {
  background: #faf9f5;
  border-radius: 14px;
  padding: 18px;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.feedback-text {
  margin: 0;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.8;
  color: #191c1e;
  font-family: inherit;
}

.rec-action {
  font-weight: 700;
  font-size: 14px;
  color: #3a388b;
  margin-bottom: 4px;
}

.rec-detail {
  font-size: 13px;
  color: #454652;
  line-height: 1.6;
}

:deep(.el-drawer) {
  background: #ffffff;
  color: #191c1e;
}

:deep(.el-drawer__header) {
  margin-bottom: 0;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(69, 70, 82, 0.08);
}

:deep(.el-drawer__title) {
  color: #191c1e;
  font-weight: 800;
}

:deep(.el-divider__text) {
  background: #ffffff;
  color: #454652;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

:deep(.el-timeline-item__content) {
  color: #191c1e;
}

.emotion-section {
  padding: 6px 0 0;
}

.emotion-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.em-metric {
  text-align: center;
  padding: 14px 10px;
  background: #faf9f5;
  border-radius: 14px;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.em-val {
  display: block;
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 4px;
}

.em-val.green {
  color: #3c7c5d;
}

.em-val.orange {
  color: #9a6b17;
}

.em-val.blue {
  color: #3a388b;
}

.em-label {
  font-size: 11px;
  color: #454652;
}

.emotion-bars {
  padding: 14px 16px;
  background: #faf9f5;
  border-radius: 14px;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.em-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.em-name {
  min-width: 46px;
  font-size: 12px;
  color: #454652;
  text-align: right;
}

.em-bar-bg {
  flex: 1;
  height: 14px;
  background: #e9edf3;
  border-radius: 999px;
  overflow: hidden;
}

.em-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.6s ease;
}

.em-pct {
  min-width: 36px;
  font-size: 12px;
  color: #454652;
}

.emotion-summary-text {
  margin-top: 12px;
}

.emotion-summary-text p {
  color: #454652;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
  padding: 12px 14px;
  background: #ffffff;
  border-radius: 10px;
  border-left: 3px solid #3a388b;
}

/* ─── Knowledge Points ─── */
.knowledge-bars {
  display: grid;
  gap: 14px;
}

.knowledge-bar-row {
  display: grid;
  grid-template-columns: minmax(150px, 220px) minmax(0, 1fr) 82px;
  gap: 12px;
  align-items: center;
}

.knowledge-bar-meta {
  min-width: 0;
}

.knowledge-bar-meta strong {
  display: block;
  overflow: hidden;
  color: #191c1e;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-bar-meta span {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: #87867f;
  font-size: 12px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-bar-track {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #eceff3;
}

.knowledge-bar-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #f4b46f, #c9542f);
  transition: width 0.45s ease;
}

.knowledge-bar-value {
  color: #454652;
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}

@media (max-width: 960px) {
  .page-header,
  .hero-shell,
  .section-head,
  .drawer-hero,
  .mini-radar-wrap {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .mode-switch {
    justify-content: flex-start;
  }

  .toolbar,
  .chart-actions {
    width: 100%;
    justify-items: start;
    justify-content: flex-start;
  }

  .metric-grid,
  .summary-grid,
  .performance-grid,
  .emotion-metrics,
  .knowledge-bar-row {
    grid-template-columns: 1fr;
  }

  .knowledge-bar-value {
    text-align: left;
  }

  .echarts-growth-container {
    height: 300px;
  }

  .mini-radar-wrap {
    align-items: center;
  }

  .echarts-mini-radar {
    width: min(100%, 260px);
  }
}

@media (max-width: 640px) {
  .page-header,
  .page-body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .page-body {
    padding-top: 20px;
  }

  .hero-shell,
  .section-shell {
    padding: 18px 16px;
  }

  .page-title {
    font-size: 20px;
  }

  .hero-title {
    font-size: 20px;
  }

  .search-input {
    width: 100%;
  }

  .mini-legend {
    min-width: 0;
    width: 100%;
  }
}
</style>
