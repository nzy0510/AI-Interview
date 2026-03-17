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
                <el-radio-button value="radar">六维能力</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <canvas ref="growthCanvasRef" class="growth-canvas" />
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
          <canvas ref="miniRadarRef" width="280" height="280" class="mini-radar" />
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getHistoryListAPI } from '@/api/interview'

const router = useRouter()
const loading = ref(true)
const historyList = ref([])      // Sorted newest-first for table
const chartMode = ref('score')
const growthCanvasRef = ref(null)
const miniRadarRef = ref(null)
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

const gradeScore = { S: 1.0, A: 0.8, B: 0.6, C: 0.4, D: 0.2 }
const getGradeType = g => ({ S: 'danger', A: 'success', B: 'primary', C: 'warning' }[g] || 'info')
const getScoreType = s => s >= 85 ? 'success' : s >= 70 ? 'primary' : s >= 55 ? 'warning' : 'danger'

const selectedAbility = computed(() => {
  try { return selected.value?.abilityJson ? JSON.parse(selected.value.abilityJson) : {} }
  catch { return {} }
})
const selectedRecs = computed(() => {
  try { return selected.value?.recommendations ? JSON.parse(selected.value.recommendations) : [] }
  catch { return [] }
})

// Chronological order for chart (oldest → newest = left → right)
const chartData = computed(() => [...historyList.value].reverse())

onMounted(async () => {
  try {
    historyList.value = await getHistoryListAPI()
  } catch {
    historyList.value = []
  } finally {
    loading.value = false
    nextTick(() => drawGrowthChart())
  }
})

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
  const canvas = growthCanvasRef.value
  if (!canvas || chartData.value.length === 0) return

  const W = canvas.parentElement.offsetWidth || 720
  canvas.width = W
  canvas.height = 240
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, W, canvas.height)

  const H = canvas.height
  const pad = { top: 30, right: 30, bottom: 40, left: 50 }
  const plotW = W - pad.left - pad.right
  const plotH = H - pad.top - pad.bottom
  const data = chartData.value

  if (chartMode.value === 'score') {
    const scores = data.map(r => r.score || 0)
    const minV = Math.max(0, Math.min(...scores) - 10)
    const maxV = Math.min(100, Math.max(...scores) + 10)
    const range = maxV - minV || 1

    // Horizontal grid
    ctx.setLineDash([5, 5])
    ctx.strokeStyle = 'rgba(255,255,255,0.05)'
    for (let i = 0; i <= 4; i++) {
      const y = pad.top + plotH - (i / 4) * plotH
      ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(pad.left + plotW, y); ctx.stroke()
      ctx.fillStyle = '#475569'; ctx.font = '10px sans-serif'; ctx.textAlign = 'right'
      ctx.fillText(Math.round(minV + (range * i) / 4), pad.left - 10, y + 4)
    }
    ctx.setLineDash([])

    const pts = data.map((r, i) => ({
      x: pad.left + (i / Math.max(data.length - 1, 1)) * plotW,
      y: pad.top + plotH - ((r.score || 0) - minV) / range * plotH
    }))

    // Area Gradient
    const grad = ctx.createLinearGradient(0, pad.top, 0, pad.top + plotH)
    grad.addColorStop(0, 'rgba(96, 165, 250, 0.2)')
    grad.addColorStop(1, 'rgba(96, 165, 250, 0)')
    ctx.beginPath()
    ctx.moveTo(pts[0].x, pad.top + plotH)
    pts.forEach(p => ctx.lineTo(p.x, p.y))
    ctx.lineTo(pts[pts.length - 1].x, pad.top + plotH)
    ctx.closePath(); ctx.fillStyle = grad; ctx.fill()

    // Main Line
    ctx.beginPath(); ctx.moveTo(pts[0].x, pts[0].y)
    pts.forEach(p => ctx.lineTo(p.x, p.y))
    ctx.strokeStyle = '#60a5fa'; ctx.lineWidth = 3; ctx.lineJoin = 'round'; ctx.stroke()

    // Dots
    pts.forEach((p, i) => {
      ctx.beginPath(); ctx.arc(p.x, p.y, 4, 0, Math.PI * 2)
      ctx.fillStyle = '#60a5fa'; ctx.fill()
      ctx.strokeStyle = '#0f172a'; ctx.lineWidth = 2; ctx.stroke()
      
      ctx.fillStyle = '#fff'; ctx.font = 'bold 11px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(data[i].score, p.x, p.y - 12)
    })

    // X axis labels
    data.forEach((r, i) => {
      const x = pad.left + (i / Math.max(data.length - 1, 1)) * plotW
      ctx.fillStyle = '#64748b'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(formatDate(r.createTime).split(' ')[0], x, H - 10)
    })

  } else {
    // Multi-line dimensions
    const dimKeys = Object.keys(abilityDimensions)
    dimKeys.forEach((key, di) => {
      const vals = data.map(r => {
        try { const ab = JSON.parse(r.abilityJson || '{}'); return gradeScore[ab[key]] || 0.2 }
        catch { return 0.2 }
      })
      const color = Object.values(abilityDimensions)[di].color
      const pts = data.map((_, i) => ({
        x: pad.left + (i / Math.max(data.length - 1, 1)) * plotW,
        y: pad.top + plotH - vals[i] * plotH
      }))
      ctx.beginPath(); ctx.moveTo(pts[0].x, pts[0].y)
      pts.forEach(p => ctx.lineTo(p.x, p.y))
      ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.stroke()
      pts.forEach(p => {
        ctx.beginPath(); ctx.arc(p.x, p.y, 3, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill()
      })
    })
  }
}

// ─── Mini Radar ───────────────────────────────────────────────────────────────
const drawMiniRadar = () => {
  const canvas = miniRadarRef.value
  if (!canvas || !selected.value) return
  const ability = selectedAbility.value
  const keys = Object.keys(abilityDimensions)
  const colors = Object.values(abilityDimensions).map(d => d.color)
  const labels = Object.values(abilityDimensions).map(d => d.label)
  const n = keys.length, W = canvas.width, H = canvas.height
  const cx = W / 2, cy = H / 2, maxR = Math.min(cx, cy) - 38
  const angle = (Math.PI * 2) / n
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, W, H)

  // Grid
  for (let ring = 1; ring <= 5; ring++) {
    const r = maxR * (ring / 5)
    ctx.beginPath()
    for (let i = 0; i < n; i++) {
      const a = angle * i - Math.PI / 2
      i === 0 ? ctx.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
               : ctx.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
    }
    ctx.closePath(); ctx.strokeStyle = ring === 5 ? 'rgba(64,158,255,.3)' : 'rgba(0,0,0,.07)'; ctx.lineWidth = 1; ctx.stroke()
  }
  // Axes
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + maxR * Math.cos(a), cy + maxR * Math.sin(a))
    ctx.strokeStyle = 'rgba(0,0,0,.1)'; ctx.stroke()
  }
  // Polygon
  const scores = keys.map(k => gradeScore[ability[k]] || 0.2)
  ctx.beginPath()
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2, r = maxR * scores[i]
    i === 0 ? ctx.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
             : ctx.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
  }
  ctx.closePath(); ctx.fillStyle = 'rgba(64,158,255,.18)'; ctx.fill()
  ctx.strokeStyle = '#409eff'; ctx.lineWidth = 2; ctx.stroke()
  // Labels & dots
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2, r = maxR * scores[i]
    ctx.beginPath(); ctx.arc(cx + r * Math.cos(a), cy + r * Math.sin(a), 4, 0, Math.PI * 2)
    ctx.fillStyle = colors[i]; ctx.fill()
    const lr = maxR + 28
    ctx.fillStyle = '#303133'; ctx.font = '12px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillText(labels[i], cx + lr * Math.cos(a), cy + lr * Math.sin(a))
  }
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
.page-title { margin: 0; font-size: 20px; font-weight: 800; background: linear-gradient(90deg, #60a5fa, #a78bfa); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }

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

.growth-canvas { width: 100%; display: block; filter: drop-shadow(0 0 10px rgba(96,165,250,0.1)); }
:deep(.el-table) { background: transparent !important; --el-table-tr-bg-color: transparent; --el-table-header-bg-color: rgba(255,255,255,0.02); --el-table-row-hover-bg-color: rgba(255,255,255,0.05); color: #cbd5e1; }
:deep(.el-table th) { border-bottom: 1px solid rgba(255,255,255,0.05) !important; }
:deep(.el-table td) { border-bottom: 1px solid rgba(255,255,255,0.05) !important; }

.table-row { cursor: pointer; }
.wpm-val { color: #f8fafc; font-weight: 700; }
.feedback-excerpt { color: #64748b; font-size: 13px; font-style: italic; }

/* Drawer */
:deep(.el-drawer) { background: #0f172a; color: #f8fafc; border-left: 1px solid rgba(255,255,255,0.1); }
:deep(.el-drawer__header) { margin-bottom: 0; padding-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.08); }
:deep(.el-drawer__title) { color: #60a5fa; font-weight: 800; }

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
</style>
