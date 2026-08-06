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
        <el-select
          v-model="selectedPositionId"
          class="position-select"
          placeholder="选择岗位"
          :loading="positionLoading"
          @change="onPositionChange"
        >
          <el-option label="全部岗位" :value="ALL_POSITIONS_VALUE">
            <span>全部岗位</span>
            <span class="option-count">{{ totalHistoryCount }}</span>
          </el-option>
          <el-option
            v-for="pos in positionOptions"
            :key="pos.id"
            :label="pos.name"
            :value="pos.id"
          >
            <span>{{ pos.name }}</span>
            <span class="option-count">{{ pos.historyCount }}</span>
          </el-option>
        </el-select>
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

        <section class="surface-card section-shell coverage-section">
          <div class="section-head">
            <div>
              <p class="section-kicker">知识覆盖</p>
              <h2 class="section-title">知识领域覆盖</h2>
              <p class="section-desc">{{ activePositionId ? '当前岗位下 RAG 真实命中的知识原子覆盖度。' : '全部岗位下 RAG 真实命中的知识原子覆盖度。' }}</p>
            </div>
            <div class="coverage-actions">
              <el-tag v-if="knowledgeCoverage?.details?.length" effect="plain" type="info">
                {{ knowledgeCoverage.details.length }} 个领域
              </el-tag>
            </div>
          </div>
          <div v-loading="coverageLoading" class="coverage-chart-wrap">
            <KnowledgeCoverageChart v-if="knowledgeCoverage?.details?.length" :details="knowledgeCoverage.details" />
            <el-empty v-else-if="!coverageLoading" description="暂无该岗位的知识覆盖数据" :image-size="60" />
          </div>
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
          <div class="feedback-box"><pre class="feedback-text">{{ selectedFeedback }}</pre></div>
        </section>

        <section class="drawer-panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">详细报告</p>
              <h2 class="section-title">逐轮问答复盘</h2>
            </div>
            <el-tag size="small" effect="plain" :type="detailStatusType">{{ detailStatusText }}</el-tag>
          </div>
          <div v-if="detailLoading" class="detail-state">
            <el-skeleton :rows="4" animated />
          </div>
          <el-alert
            v-else-if="detailError"
            class="detail-alert"
            :title="detailError"
            type="info"
            show-icon
            :closable="false"
          />
          <el-alert
            v-else-if="detailFailureMessage"
            class="detail-alert"
            title="详细报告生成失败"
            type="error"
            show-icon
            :closable="false"
          >
            <template #default>
              <div class="detail-failure-actions">
                <span>{{ detailFailureMessage }}</span>
                <el-button
                  v-if="canRetrySelectedDetailedReport"
                  size="small"
                  type="danger"
                  plain
                  :loading="detailRetrying"
                  @click="retryDetailedReport"
                >
                  重新生成详细报告
                </el-button>
              </div>
            </template>
          </el-alert>
          <div v-else-if="selectedDetailedReport && detailedReportItems.length" class="detail-turn-list">
            <div class="detail-summary">
              <span>详细报告总分</span>
              <strong>{{ selectedDetailedReport.overallScore ?? selected.score ?? '--' }} / 100</strong>
            </div>
            <article v-for="item in detailedReportItems" :key="item.id || item.itemIndex" class="detail-turn">
              <div class="detail-turn-head">
                <div>
                  <span class="detail-turn-index">第 {{ item.itemIndex }} 轮</span>
                  <el-tag size="small" effect="plain">{{ phaseLabel(item.phase) }}</el-tag>
                </div>
                <strong>{{ formatItemScore(item.score) }}</strong>
              </div>
              <dl class="detail-turn-body">
                <div>
                  <dt>提问</dt>
                  <dd>{{ item.question || '暂无问题记录' }}</dd>
                </div>
                <div>
                  <dt>回答</dt>
                  <dd>{{ item.userAnswer || '暂无回答记录' }}</dd>
                </div>
                <div>
                  <dt>参考答案</dt>
                  <dd>{{ formatReferenceAnswer(item.referenceAnswer) }}</dd>
                </div>
                <div v-if="item.improvementSuggestion">
                  <dt>评分依据与改进建议</dt>
                  <dd>{{ item.improvementSuggestion }}</dd>
                </div>
                <div>
                  <dt>来源</dt>
                  <dd>
                    <span>{{ formatAnswerSource(item.answerSource) }}</span>
                    <pre v-if="formatSnapshot(item.matchedAtomSnapshotJson)" class="source-snapshot">{{ formatSnapshot(item.matchedAtomSnapshotJson) }}</pre>
                  </dd>
                </div>
              </dl>
            </article>
          </div>
          <el-empty v-else description="详细报告正在生成或尚无逐轮明细" :image-size="60" />
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
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, RefreshRight, Search } from '@element-plus/icons-vue'
import KnowledgeCoverageChart from '@/components/charts/KnowledgeCoverageChart.vue'
import { reportCenterConfig } from '@/mock/reports'
import {
  ALL_POSITIONS_VALUE,
  abilityDimensions,
  excerpt,
  formatDate,
  getGradeType,
  getScoreType,
  useHistoryData
} from '@/composables/history/useHistoryData'
import {
  emotionColor,
  emotionLabel,
  formatAnswerSource,
  formatItemScore,
  formatReferenceAnswer,
  formatSnapshot,
  phaseLabel,
  useDetailDrawer
} from '@/composables/history/useDetailDrawer'
import { useChartRenderer } from '@/composables/history/useChartRenderer'

const router = useRouter()
const reportCenter = reportCenterConfig

const {
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
} = useDetailDrawer()

let drawGrowthChart = () => {}
const {
  loading,
  historyList,
  chartMode,
  searchKeyword,
  modeFilter,
  knowledgeCoverage,
  positionOptions,
  selectedPositionId,
  positionLoading,
  coverageLoading,
  totalHistoryCount,
  visibleHistoryList,
  chartData,
  latestRecord,
  activePositionId,
  scoreDeltaText,
  summaryCards,
  overviewMetrics,
  strongestAbility,
  onPositionChange,
  initializeHistory,
  clearFilters
} = useHistoryData({
  selectedRecord: selected,
  redrawChart: () => drawGrowthChart()
})

const chartRenderer = useChartRenderer({
  abilityDimensions,
  chartData,
  chartMode,
  formatDate,
  selected,
  selectedAbility
})

const {
  growthChartRef,
  miniRadarRef,
  drawMiniRadar,
  refreshChart,
  handleResize,
  disposeCharts
} = chartRenderer

drawGrowthChart = chartRenderer.drawGrowthChart

onMounted(async () => {
  await initializeHistory()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})

watch([drawerOpen, selected], ([open]) => {
  if (open) nextTick(() => drawMiniRadar())
})

watch([chartMode, visibleHistoryList], () => {
  nextTick(() => drawGrowthChart())
}, { deep: true })
</script>

<style scoped>
.history-page {
  min-height: 100vh;
  background: #f5f4ed;
  color: #141413;
}

.page-header {
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

.position-select {
  width: 220px;
}

.option-count {
  float: right;
  margin-left: 16px;
  color: #87867f;
  font-size: 12px;
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-tile {
  min-width: 0;
  padding: 14px 16px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.summary-label,
.summary-hint {
  display: block;
  color: #5a6678;
  font-size: 12px;
  line-height: 1.5;
}

.summary-value {
  display: block;
  margin: 6px 0 4px;
  overflow-wrap: anywhere;
  color: #191c1e;
  font-size: 20px;
  line-height: 1.25;
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

.coverage-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.coverage-chart-wrap {
  min-height: 120px;
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

.detail-state,
.detail-alert {
  margin-top: 4px;
}

.detail-failure-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  line-height: 1.5;
}

.detail-turn-list {
  display: grid;
  gap: 14px;
}

.detail-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f4f3ff;
  color: #3a388b;
}

.detail-summary span {
  font-size: 13px;
  font-weight: 700;
}

.detail-summary strong {
  font-size: 18px;
}

.detail-turn {
  padding: 16px;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 8px;
  background: #faf9f5;
}

.detail-turn-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-turn-head > div {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-turn-head strong {
  color: #3a388b;
  font-size: 16px;
  white-space: nowrap;
}

.detail-turn-index {
  color: #191c1e;
  font-size: 13px;
  font-weight: 800;
}

.detail-turn-body {
  display: grid;
  gap: 12px;
  margin: 0;
}

.detail-turn-body div {
  display: grid;
  gap: 5px;
}

.detail-turn-body dt {
  color: #87867f;
  font-size: 12px;
  font-weight: 800;
}

.detail-turn-body dd {
  min-width: 0;
  margin: 0;
  color: #191c1e;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.source-snapshot {
  margin: 8px 0 0;
  max-height: 180px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  color: #454652;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
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

  .position-select {
    width: min(100%, 240px);
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
