<template>
  <div class="history-page">
    <div class="page-header">
      <el-button :icon="ArrowLeft" circle @click="router.push('/')" />
      <h1 class="page-title">📊 我的面试历史</h1>
      <div class="header-spacer"></div>
    </div>

    <el-main class="page-body" v-loading="loading">
      <!-- Empty state -->
      <el-empty v-if="!loading && historyList.length === 0" description="还没有完成过面试，快去挑战一场吧！">
        <el-button type="primary" @click="router.push('/')">开始面试</el-button>
      </el-empty>

      <template v-else>
        <!-- ══ Ability Growth Chart ══ -->
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="chart-header">
              <span>📈 能力成长曲线（综合得分趋势）</span>
              <el-radio-group v-model="chartMode" size="small" @change="drawGrowthChart">
                <el-radio-button value="score">综合得分</el-radio-button>
                <el-radio-button value="radar">能力热力图</el-radio-button>
                <el-radio-button value="graph">🪐 知识星图</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="growthChartRef" class="echarts-growth-container"></div>
        </el-card>

        <!-- ══ History List ══ -->
        <el-card class="list-card" shadow="never">
          <template #header><span>📋 历史面试记录</span></template>
          <el-table :data="historyList" stripe @row-click="openDetail" row-class-name="table-row">
            <el-table-column label="日期" width="170">
              <template #default="{ row }">
                {{ formatDate(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="position" label="面试岗位" width="160" />
            <el-table-column label="模式" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.interviewMode === 'video' ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.interviewMode === 'video' ? '📹 视频' : '📝 文字' }}
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
        </el-card>
      </template>
    </el-main>

    <!-- ══ Detail Drawer ══ -->
    <el-drawer v-model="drawerOpen" title="面试报告详情" size="620px" direction="rtl">
      <div v-if="selected" class="drawer-body">
        <div class="detail-meta">
          <el-tag size="large" type="info" plain>{{ selected.position }}</el-tag>
          <el-tag size="large" type="success" effect="dark" style="margin-left:8px">
            {{ selected.score }} / 100 分
          </el-tag>
          <span class="detail-date">{{ formatDate(selected.createTime) }}</span>
        </div>

        <!-- Mini Radar -->
        <el-divider content-position="left">六维能力评级</el-divider>
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

        <!-- Feedback -->
        <el-divider content-position="left">AI 综合反馈</el-divider>
        <div class="feedback-box"><pre class="feedback-text">{{ selected.feedback }}</pre></div>

        <!-- Emotion Analysis (video interview only) -->
        <template v-if="selectedEmotion && Object.keys(selectedEmotion).length > 0">
          <el-divider content-position="left">
            📹 情感分析
            <el-tag size="small" type="success" effect="plain" style="margin-left: 8px">视频模式</el-tag>
          </el-divider>
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
              <div class="em-metric">
                <span class="em-val blue">{{ selectedEmotion.sampleCount || 0 }}</span>
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
          </div>
        </template>

        <!-- Recommendations -->
        <el-divider content-position="left">提升建议</el-divider>
        <el-timeline v-if="selectedRecs.length">
          <el-timeline-item
            v-for="(r, i) in selectedRecs"
            :key="i"
            :timestamp="r.period"
            placement="top"
            :type="['primary','success','warning'][i % 3]"
          >
            <div class="rec-action">{{ r.action }}</div>
            <div class="rec-detail">{{ r.detail }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无建议" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getHistoryListAPI } from '@/api/interview'
import * as echarts from 'echarts'

const router = useRouter()
const loading = ref(true)
const historyList = ref([])      // Sorted newest-first for table
const chartMode = ref('score')
const growthChartRef = ref(null)
const miniRadarRef = ref(null)
let growthChartInstance = null
let miniRadarInstance = null
const drawerOpen = ref(false)
const selected = ref(null)

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

// Chronological order for chart (oldest → newest = left → right)
const chartData = computed(() => [...historyList.value].reverse())

onMounted(async () => {
  try {
    historyList.value = await getHistoryListAPI()
  } catch {
    historyList.value = []
  } finally {
    loading.value = false
    nextTick(() => {
      drawGrowthChart()
      window.addEventListener('resize', handleResize)
    })
  }
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

// ─── Helpers ──────────────────────────────────────────────────────────────────
const formatDate = (d) => d ? new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'
const excerpt = (t) => t ? (t.length > 60 ? t.slice(0, 60) + '...' : t) : ''

const openDetail = (row) => {
  selected.value = row
  drawerOpen.value = true
}

// ─── Growth Trend Chart ───────────────────────────────────────────────────────
const drawGrowthChart = () => {
  const container = growthChartRef.value
  if (!container || chartData.value.length === 0) return

  if (!growthChartInstance) {
    growthChartInstance = echarts.init(container)
  }

  const data = chartData.value
  const xAxisData = data.map(r => formatDate(r.createTime).split(' ')[0])

  if (chartMode.value === 'score') {
    const scores = data.map(r => r.score || 0)
    
    const option = {
      grid: { top: 40, right: 30, bottom: 40, left: 50 },
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(15, 23, 42, 0.9)', borderColor: '#10b981', textStyle: { color: '#f8fafc' } },
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLine: { lineStyle: { color: '#475569' } },
        axisLabel: { color: '#94a3b8' }
      },
      yAxis: {
        type: 'value',
        min: 'dataMin',
        max: 'dataMax',
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)', type: 'dashed' } },
        axisLabel: { color: '#94a3b8' }
      },
      series: [
        {
          name: '综合得分',
          data: scores,
          type: 'line',
          smooth: true,
          symbolSize: 8,
          itemStyle: { color: '#10b981' },
          lineStyle: { color: '#10b981', width: 3 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, 0.4)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.0)' }
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
    const yAxisData = dimLabels.reverse() // Reverse so top is first dimension

    const gradeVal = { A: 4, B: 3, C: 2, D: 1, E: 0 }
    const heatmapData = []
    
    const revKeys = [...dimKeys].reverse()
    
    data.forEach((r, xIndex) => {
      let ab = {}
      try { ab = JSON.parse(r.abilityJson || '{}') } catch {}
      revKeys.forEach((key, yIndex) => {
        const grade = ab[key] || 'E'
        heatmapData.push([xIndex, yIndex, gradeVal[grade], grade])
      })
    })

    const option = {
      grid: { top: 30, right: 30, bottom: 40, left: 80 },
      tooltip: {
        position: 'top',
        backgroundColor: 'rgba(15, 23, 42, 0.9)', borderColor: '#10b981', textStyle: { color: '#f8fafc' },
        formatter: (params) => `${params.name} <br/> 维度: <b>${yAxisData[params.value[1]]}</b> <br/> 评级: <b>${params.value[3]}</b>`
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLine: { lineStyle: { color: '#475569' } },
        axisLabel: { color: '#94a3b8' },
        splitArea: { show: true, areaStyle: { color: ['rgba(255,255,255,0.02)', 'transparent'] } }
      },
      yAxis: {
        type: 'category',
        data: yAxisData,
        axisLine: { lineStyle: { color: '#475569' } },
        axisLabel: { color: '#94a3b8' }
      },
      visualMap: {
        min: 0, max: 4,
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        show: false,
        inRange: {
          color: ['rgba(16,185,129,0.05)', 'rgba(16,185,129,0.3)', 'rgba(16,185,129,0.5)', 'rgba(16,185,129,0.8)', 'rgba(16,185,129,1.0)']
        }
      },
      series: [{
        name: '能力评级',
        type: 'heatmap',
        data: heatmapData,
        label: {
          show: true,
          formatter: (p) => p.data[3],
          color: '#fff',
          fontSize: 12,
          fontWeight: 'bold'
        },
        itemStyle: {
          borderColor: '#0f172a',
          borderWidth: 2,
          borderRadius: 4
        }
      }]
    }
    growthChartInstance.setOption(option, true)
  } else if (chartMode.value === 'graph') {
    // 🌌 Knowledge Graph Force Directed
    const nodeMap = new Map()
    const edges = []
    
    data.forEach(r => {
      let kPoints = []
      try { kPoints = JSON.parse(r.knowledgeJson || '[]') } catch {}
      
      if (kPoints && kPoints.length > 0) {
        for (let i = 0; i < kPoints.length; i++) {
          const p = kPoints[i]
          const name = p.concept || '未知知识点'
          const mastery = p.mastery != null ? parseFloat(p.mastery) : 0.5
          const cat = p.category || '综合'
          
          if (nodeMap.has(name)) {
            const nd = nodeMap.get(name)
            nd.masterySum += mastery
            nd.count += 1
          } else {
            nodeMap.set(name, { name, masterySum: mastery, count: 1, category: cat })
          }
          
          // Connect to subsequent concepts in the SAME interview session
          for (let j = i + 1; j < kPoints.length; j++) {
            const TargetName = kPoints[j].concept || '未知知识点'
            edges.push({ source: name, target: TargetName })
          }
        }
      }
    })

    const nodes = []
    const categories = []
    const categoryMap = new Map()

    nodeMap.forEach((val, key) => {
      const avg = val.masterySum / val.count
      if (!categoryMap.has(val.category)) {
        categoryMap.set(val.category, categories.length)
        categories.push({ name: val.category })
      }

      let color = '#5B9BD5' 
      if (avg >= 0.8) color = '#10b981' // Green (Good mastery)
      else if (avg >= 0.6) color = '#E6A23C' // Yellow (Avg)
      else color = '#F56C6C' // Red (Poor)

      nodes.push({
        name: key,
        value: (avg * 100).toFixed(1),
        symbolSize: Math.min(80, 20 + val.count * 12),
        category: categoryMap.get(val.category),
        itemStyle: {
          color: color,
          shadowBlur: 20,
          shadowColor: color
        },
        label: { show: true, formatter: '{b}', textStyle: { color: '#e2e8f0', textBorderColor: '#0f172a', textBorderWidth: 2 } }
      })
    })

    // If completely empty across all history, show dummy data so user isn't bored
    if (nodes.length === 0) {
      nodes.push({ name: '暂无知识点', value: 0, symbolSize: 50, itemStyle: { color: '#94a3b8' } })
    }

    const option = {
      grid: { top: 30, right: 30, bottom: 50, left: 30 },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.95)', borderColor: '#10b981', textStyle: { color: '#f8fafc' },
        formatter: (params) => {
          if (params.dataType === 'node') {
            const catStr = categories.length > 0 && params.data.category != null ? categories[params.data.category].name : '无'
            return `<div style="font-weight:bold;font-size:16px;margin-bottom:4px;border-bottom:1px solid rgba(255,255,255,0.2)">${params.name}</div>
                    领域类别: <span style="color:#60a5fa">${catStr}</span> <br/> 
                    评估熟练度: <span style="color:${params.color};font-weight:bold">${params.value}%</span>`
          }
          return ''
        }
      },
      legend: [{
        data: categories.map(a => a.name),
        textStyle: { color: '#94a3b8' },
        bottom: 10
      }],
      animationDuration: 1500,
      animationEasingUpdate: 'quinticInOut',
      series: [
        {
          name: '星系知识图谱',
          type: 'graph',
          layout: 'force',
          data: nodes,
          links: edges,
          categories: categories,
          roam: true,
          label: { position: 'right' },
          force: { repulsion: 300, edgeLength: 120, layoutAnimation: true },
          lineStyle: { color: 'source', curveness: 0.2, opacity: 0.2, width: 1.5 }
        }
      ]
    }
    growthChartInstance.setOption(option, true)
  }
}

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
      axisName: { color: '#94a3b8', fontSize: 12 },
      splitNumber: 4,
      splitArea: { areaStyle: { color: ['rgba(16,185,129,0.05)', 'transparent'] } },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: scores,
        symbolSize: 4,
        itemStyle: { color: '#10b981' },
        lineStyle: { width: 2 },
        areaStyle: { 
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16,185,129,0.5)' },
            { offset: 1, color: 'rgba(16,185,129,0.1)' }
          ])
        }
      }]
    }]
  }
  miniRadarInstance.setOption(option)
}
</script>

<style scoped>
.history-page { min-height: 100vh; background: #0f172a; display: flex; flex-direction: column; color: #f8fafc; }

.page-header { 
  display: flex; 
  align-items: center; 
  gap: 16px; 
  padding: 16px 40px; 
  background: rgba(255,255,255,0.03); 
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255,255,255,0.08); 
  flex-shrink: 0; 
}
.page-title { margin: 0; font-size: 20px; font-weight: 800; background: linear-gradient(90deg, #10b981, #0ea5e9); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }

.page-body { 
  flex: 1; 
  padding: 32px 40px; 
  display: flex; 
  flex-direction: column; 
  gap: 24px; 
  max-width: 1200px; 
  margin: 0 auto; 
  width: 100%; 
  box-sizing: border-box; 
}

.chart-card, .list-card { 
  background: rgba(255,255,255,0.03) !important; 
  border: 1px solid rgba(255,255,255,0.08) !important; 
  border-radius: 20px !important; 
}
.chart-card :deep(.el-card__header), .list-card :deep(.el-card__header) { 
  border-bottom: 1px solid rgba(255,255,255,0.08);
  color: #94a3b8;
  font-weight: 600;
  font-size: 14px;
}
.chart-header { display: flex; justify-content: space-between; align-items: center; }
.echarts-growth-container { width: 100%; height: 320px; }
.echarts-mini-radar { width: 280px; height: 280px; flex-shrink: 0; }
:deep(.el-table) { background: transparent !important; --el-table-tr-bg-color: transparent; --el-table-header-bg-color: rgba(255,255,255,0.02); --el-table-row-hover-bg-color: rgba(255,255,255,0.05); color: #cbd5e1; }
:deep(.el-table th) { border-bottom: 1px solid rgba(255,255,255,0.05) !important; }
:deep(.el-table td) { border-bottom: 1px solid rgba(255,255,255,0.05) !important; }

.table-row { cursor: pointer; }
.wpm-val { color: #f8fafc; font-weight: 700; }
.feedback-excerpt { color: #64748b; font-size: 13px; font-style: italic; }

/* Drawer */
:deep(.el-drawer) { background: #0f172a; color: #f8fafc; border-left: 1px solid rgba(255,255,255,0.1); }
:deep(.el-drawer__header) { margin-bottom: 0; padding-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.08); }
:deep(.el-drawer__title) { color: #10b981; font-weight: 800; }

.drawer-body { padding: 20px 0; }
.detail-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; padding: 0 4px; }
.detail-date { color: #475569; font-size: 12px; margin-left: auto; }

.mini-radar-wrap { display: flex; align-items: center; gap: 32px; padding: 16px 0; justify-content: center; }
.mini-legend { display: flex; flex-direction: column; gap: 12px; }
.legend-row { display: flex; align-items: center; gap: 10px; width: 150px; }
.l-dot { width: 10px; height: 10px; border-radius: 50%; }
.l-name { font-size: 13px; color: #94a3b8; flex: 1; }

.feedback-box { background: rgba(255,255,255,0.02); border-radius: 12px; padding: 20px; border: 1px solid rgba(255,255,255,0.05); }
.feedback-text { margin: 0; white-space: pre-wrap; font-size: 14px; line-height: 1.8; color: #cbd5e1; font-family: inherit; }

.rec-action { font-weight: 700; font-size: 14px; color: #60a5fa; margin-bottom: 4px; }
.rec-detail { font-size: 13px; color: #64748b; line-height: 1.6; }

:deep(.el-divider__text) { background: #0f172a; color: #475569; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; }
:deep(.el-timeline-item__content) { color: #cbd5e1; }

/* Emotion Section in Drawer */
.emotion-section { padding: 12px 0; }
.emotion-metrics { display: flex; gap: 16px; margin-bottom: 16px; }
.em-metric { flex: 1; text-align: center; padding: 14px 8px; background: rgba(255,255,255,0.03); border-radius: 12px; border: 1px solid rgba(255,255,255,0.06); }
.em-val { display: block; font-size: 24px; font-weight: 700; margin-bottom: 4px; }
.em-val.green { color: #67C23A; }
.em-val.orange { color: #E6A23C; }
.em-val.blue { color: #409EFF; }
.em-label { font-size: 11px; color: #64748b; }
.emotion-bars { padding: 12px 16px; background: rgba(255,255,255,0.02); border-radius: 12px; }
.em-bar-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.em-name { min-width: 40px; font-size: 12px; color: #94a3b8; text-align: right; }
.em-bar-bg { flex: 1; height: 14px; background: rgba(255,255,255,0.06); border-radius: 7px; overflow: hidden; }
.em-bar-fill { height: 100%; border-radius: 7px; transition: width 0.6s ease; }
.em-pct { min-width: 35px; font-size: 12px; color: #64748b; }
</style>
