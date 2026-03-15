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
  canvas.height = 220
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, W, canvas.height)

  const H = canvas.height
  const pad = { top: 20, right: 30, bottom: 40, left: 50 }
  const plotW = W - pad.left - pad.right
  const plotH = H - pad.top - pad.bottom
  const data = chartData.value

  if (chartMode.value === 'score') {
    // ── Single score line ──
    const scores = data.map(r => r.score || 0)
    const minV = Math.max(0, Math.min(...scores) - 10)
    const maxV = Math.min(100, Math.max(...scores) + 10)
    const range = maxV - minV || 1

    // Grid lines
    for (let i = 0; i <= 4; i++) {
      const y = pad.top + plotH - (i / 4) * plotH
      const val = Math.round(minV + (range * i) / 4)
      ctx.strokeStyle = 'rgba(0,0,0,0.06)'
      ctx.setLineDash([4, 4])
      ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(pad.left + plotW, y); ctx.stroke()
      ctx.setLineDash([])
      ctx.fillStyle = '#909399'; ctx.font = '11px sans-serif'; ctx.textAlign = 'right'
      ctx.fillText(val, pad.left - 6, y + 4)
    }

    // Points
    const pts = data.map((r, i) => ({
      x: pad.left + (i / Math.max(data.length - 1, 1)) * plotW,
      y: pad.top + plotH - ((r.score || 0) - minV) / range * plotH
    }))

    // Gradient fill
    const grad = ctx.createLinearGradient(0, pad.top, 0, pad.top + plotH)
    grad.addColorStop(0, 'rgba(64,158,255,0.25)')
    grad.addColorStop(1, 'rgba(64,158,255,0)')
    ctx.beginPath()
    ctx.moveTo(pts[0].x, pad.top + plotH)
    pts.forEach(p => ctx.lineTo(p.x, p.y))
    ctx.lineTo(pts[pts.length - 1].x, pad.top + plotH)
    ctx.closePath(); ctx.fillStyle = grad; ctx.fill()

    // Line
    ctx.beginPath(); ctx.moveTo(pts[0].x, pts[0].y)
    pts.forEach(p => ctx.lineTo(p.x, p.y))
    ctx.strokeStyle = '#409eff'; ctx.lineWidth = 2.5; ctx.lineJoin = 'round'; ctx.stroke()

    // Dots + score labels
    pts.forEach((p, i) => {
      ctx.beginPath(); ctx.arc(p.x, p.y, 5, 0, Math.PI * 2)
      ctx.fillStyle = '#409eff'; ctx.fill()
      ctx.fillStyle = '#303133'; ctx.font = 'bold 12px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(data[i].score, p.x, p.y - 10)
    })

    // X axis labels (date)
    data.forEach((r, i) => {
      const x = pad.left + (i / Math.max(data.length - 1, 1)) * plotW
      ctx.fillStyle = '#909399'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(formatDate(r.createTime), x, H - 8)
    })

  } else {
    // ── Multi-line: one line per dimension ──
    const dimKeys = Object.keys(abilityDimensions)
    const dimColors = Object.values(abilityDimensions).map(d => d.color)

    dimKeys.forEach((key, di) => {
      const vals = data.map(r => {
        try { const ab = JSON.parse(r.abilityJson || '{}'); return gradeScore[ab[key]] || 0.2 }
        catch { return 0.2 }
      })
      const pts = data.map((_, i) => ({
        x: pad.left + (i / Math.max(data.length - 1, 1)) * plotW,
        y: pad.top + plotH - vals[i] * plotH
      }))
      ctx.beginPath(); ctx.moveTo(pts[0].x, pts[0].y)
      pts.forEach(p => ctx.lineTo(p.x, p.y))
      ctx.strokeStyle = dimColors[di]; ctx.lineWidth = 2; ctx.lineJoin = 'round'; ctx.stroke()
      pts.forEach(p => {
        ctx.beginPath(); ctx.arc(p.x, p.y, 4, 0, Math.PI * 2)
        ctx.fillStyle = dimColors[di]; ctx.fill()
      })
    })

    // Legend
    dimKeys.forEach((key, di) => {
      const lx = pad.left + di * (plotW / dimKeys.length)
      ctx.fillStyle = dimColors[di]
      ctx.fillRect(lx, H - 14, 12, 10)
      ctx.fillStyle = '#606266'; ctx.font = '10px sans-serif'; ctx.textAlign = 'left'
      ctx.fillText(abilityDimensions[key].label, lx + 14, H - 5)
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
.history-page { min-height: 100vh; background: #f0f4f8; display: flex; flex-direction: column; }
.page-header { display: flex; align-items: center; gap: 16px; padding: 16px 28px; background: #fff; border-bottom: 1px solid #ebeef5; flex-shrink: 0; }
.page-title { margin: 0; font-size: 20px; font-weight: 700; color: #1d2129; }
.header-spacer { flex: 1; }
.page-body { flex: 1; padding: 24px 28px; display: flex; flex-direction: column; gap: 20px; max-width: 1100px; margin: 0 auto; width: 100%; box-sizing: border-box; }
.chart-card, .list-card { border-radius: 12px; border: 1px solid #ebeef5; }
.chart-header { display: flex; justify-content: space-between; align-items: center; }
.growth-canvas { width: 100%; display: block; }
.table-row { cursor: pointer; transition: background .15s; }
.table-row:hover { background: #f0f6ff !important; }
.wpm-val { color: #303133; font-weight: 600; }
.feedback-excerpt { color: #606266; font-size: 13px; }

/* Drawer */
.drawer-body { padding: 0 4px; }
.detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.detail-date { color: #909399; font-size: 13px; margin-left: auto; }
.mini-radar-wrap { display: flex; align-items: center; gap: 20px; padding: 8px 0; flex-wrap: wrap; }
.mini-radar { flex-shrink: 0; }
.mini-legend { display: flex; flex-direction: column; gap: 10px; }
.legend-row { display: flex; align-items: center; gap: 8px; }
.l-dot { width: 11px; height: 11px; border-radius: 50%; flex-shrink: 0; }
.l-name { font-size: 13px; color: #303133; flex: 1; }
.feedback-box { background: #fafbfc; border-radius: 8px; padding: 14px; margin-bottom: 8px; }
.feedback-text { margin: 0; white-space: pre-wrap; font-size: 14px; line-height: 1.8; color: #303133; font-family: inherit; }
.rec-action { font-weight: 700; font-size: 14px; margin-bottom: 4px; }
.rec-detail { font-size: 13px; color: #606266; line-height: 1.6; }
</style>
