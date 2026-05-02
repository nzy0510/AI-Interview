<template>
  <div class="resume-page">
    <el-container class="resume-layout">
      <el-header class="page-header">
        <div class="brand-cluster">
          <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
          <div class="header-copy">
            <p class="eyebrow">Architectural Intelligence</p>
            <h1 class="page-title">简历画像配置</h1>
            <p class="page-subtitle">上传解析结果后，系统会自动整理成更适合面试的画像、问题与建议。</p>
          </div>
        </div>

        <div class="header-actions">
          <el-tag effect="plain" type="info" class="status-pill">配置中心</el-tag>
          <el-button class="primary-cta" type="primary" @click="showModeDialog = true">基于画像开启面试</el-button>
        </div>
      </el-header>

      <el-main class="resume-main">
        <section v-if="!analysis" class="surface-card empty-shell">
          <div class="empty-copy">
            <p class="section-kicker">Resume Intake</p>
            <h2 class="section-title">无缓存的简历解析记录</h2>
            <p class="section-desc">请重新上传简历，系统会在这里重新生成画像与定制问题。</p>
          </div>
        </section>

        <div v-else class="resume-grid">
          <div class="main-column">
            <section class="surface-card section-shell hero-strip">
              <div class="hero-copy">
                <p class="section-kicker">Profile Brief</p>
                <h2 class="section-title">简历扫描透视</h2>
                <p class="section-desc">围绕当前 {{ role }} 岗位，输出匹配率、技能星云和追问路径。</p>
              </div>
              <div class="hero-metrics">
                <div class="hero-metric">
                  <span class="metric-label">画像状态</span>
                  <strong>已加载</strong>
                </div>
                <div class="hero-metric">
                  <span class="metric-label">目标岗位</span>
                  <strong>{{ role }}</strong>
                </div>
                <div class="hero-metric">
                  <span class="metric-label">进入面试</span>
                  <el-button size="small" type="primary" @click="showModeDialog = true">选择模式</el-button>
                </div>
              </div>
            </section>

            <div class="bento-grid">
              <section class="surface-card bento-item gauge-box">
                <h3 class="bento-title"><el-icon><DataAnalysis /></el-icon> AI 匹配率评估</h3>
                <div id="gaugeChart" class="chart-container"></div>
                <div class="evaluation-text">
                  <p><strong>智能洞察：</strong></p>
                  {{ analysis.evaluation }}
                </div>
              </section>

              <section class="surface-card bento-item cloud-box">
                <h3 class="bento-title"><el-icon><Star /></el-icon> 核心技能星云</h3>
                <div id="cloudChart" class="chart-container"></div>
              </section>

              <section class="surface-card bento-item questions-box">
                <h3 class="bento-title highlight"><el-icon><Warning /></el-icon> 预测深挖攻击面</h3>
                <p class="section-desc">结合此简历的特定技术栈与场景，大厂高 P 大概率会进行如下连环追问：</p>
                <div class="q-list">
                  <div v-for="(q, idx) in analysis.tailoredQuestions" :key="idx" class="q-item">
                    <div class="q-idx">{{ idx + 1 }}</div>
                    <div class="q-text">{{ q }}</div>
                  </div>
                </div>
              </section>

              <section class="surface-card bento-item projects-box">
                <h3 class="bento-title"><el-icon><Briefcase /></el-icon> 核心经验大纲萃取</h3>
                <div class="timeline-wrap">
                  <el-timeline>
                    <el-timeline-item
                      v-for="(proj, pIdx) in analysis.projectSummary"
                      :key="pIdx"
                      type="success"
                      size="large"
                      :timestamp="proj.name"
                      placement="top"
                    >
                      <el-card class="proj-card" shadow="never">
                        {{ proj.desc }}
                      </el-card>
                    </el-timeline-item>
                  </el-timeline>
                </div>
              </section>
            </div>
          </div>

          <aside class="side-column">
            <section class="surface-card sidebar-card">
              <div class="sidebar-head">
                <p class="section-kicker">Session Summary</p>
                <h3 class="section-title">会话摘要</h3>
              </div>
              <div class="summary-list">
                <div class="summary-row">
                  <span class="summary-label">目标岗位</span>
                  <strong>{{ role }}</strong>
                </div>
                <div class="summary-row">
                  <span class="summary-label">匹配评分</span>
                  <strong>{{ analysis.matchScore || 0 }}%</strong>
                </div>
                <div class="summary-row">
                  <span class="summary-label">建议模式</span>
                  <strong>文字 / 视频</strong>
                </div>
              </div>
              <div class="summary-foot">
                <p>{{ analysis.evaluation }}</p>
              </div>
            </section>

            <section class="surface-card sidebar-card mentor-card">
              <div class="sidebar-head">
                <p class="section-kicker">AI Mentor</p>
                <h3 class="section-title">面试官就绪</h3>
              </div>
              <div class="mentor-copy">
                <p>系统已准备好按你的画像启动专属面试。先选模式，再进入连环追问。</p>
              </div>
              <el-button class="mentor-cta" type="primary" plain @click="showModeDialog = true">打开模式选择</el-button>
            </section>
          </aside>
        </div>
      </el-main>
    </el-container>

    <el-dialog v-model="showModeDialog" title="选择专属面试模式" width="min(92vw, 560px)" center :close-on-click-modal="false">
      <div class="mode-options">
        <div class="mode-card" @click="confirmStart('text')">
          <div class="mode-icon">📝</div>
          <h3>文字定制模式</h3>
          <p>携此简历题库，通过文字交流</p>
          <el-tag type="info" size="small">经典模式</el-tag>
        </div>
        <div class="mode-card video" @click="confirmStart('video')">
          <div class="mode-icon">📹</div>
          <h3>视频定制模式</h3>
          <p>全面对抗，视频迎战定制连环问</p>
          <el-tag type="success" size="small">压力考核</el-tag>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, DataAnalysis, Star, Warning, Briefcase } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { userKey } from '@/utils/auth'
import { getPreferenceAPI } from '@/api/user'

const router = useRouter()
const route = useRoute()
const role = route.query.role || '软件开发'

const analysis = ref(null)
const showModeDialog = ref(false)
const pref = ref({ defaultRole: '', difficultyLevel: 'mid', focusAreas: '[]' })

let gaugeChartInstance = null
let cloudChartInstance = null

onMounted(async () => {
  // 加载偏好用于面试入口
  try {
    const p = await getPreferenceAPI()
    if (p) {
      pref.value.defaultRole = p.defaultRole || ''
      pref.value.difficultyLevel = p.difficultyLevel || 'mid'
      pref.value.focusAreas = p.focusAreas || '[]'
    }
  } catch {}

  let profileData = null
  try {
    // 静默请求：用原生 axios 避免触发拦截器的 ElMessage.error
    const token = localStorage.getItem('token')
    const resp = await fetch((import.meta.env.VITE_API_BASE_URL || '') + '/api/resume/profile', {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (resp.ok) {
      const result = await resp.json()
      if (result.code === 200 && result.data) {
        profileData = result.data
      }
    }
  } catch (error) {
    console.log('暂无简历画像:', error.message)
  }

  // 后端无数据时，尝试从 localStorage 读取
  if (!profileData) {
    try {
      const cached = localStorage.getItem(userKey('resume_analysis'))
      if (cached) {
        profileData = JSON.parse(cached)
      }
    } catch (e) {
      console.log('本地缓存解析失败:', e.message)
    }
  }

  if (profileData) {
    analysis.value = profileData
    setTimeout(() => {
      initGaugeChart()
      initWordCloud()
    }, 100)
    window.addEventListener('resize', handleResize)
  }
})

onBeforeUnmount(() => {
  if (gaugeChartInstance) gaugeChartInstance.dispose()
  if (cloudChartInstance) cloudChartInstance.dispose()
  window.removeEventListener('resize', handleResize)
})

const handleResize = () => {
  if (gaugeChartInstance) gaugeChartInstance.resize()
  if (cloudChartInstance) cloudChartInstance.resize()
}

// === 1. 初始化仪表盘 ===
const initGaugeChart = () => {
  const dom = document.getElementById('gaugeChart')
  if (!dom) return
  gaugeChartInstance = echarts.init(dom)
  
  const score = analysis.value.matchScore || 0
  let color = '#10b981'
  if (score < 60) color = '#f43f5e'
  else if (score < 80) color = '#f59e0b'

  const option = {
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        center: ['50%', '78%'],
        radius: '95%',
        min: 0,
        max: 100,
        splitNumber: 10,
        itemStyle: {
          color: color,
          shadowColor: 'rgba(16, 185, 129, 0.45)',
          shadowBlur: 10,
          shadowOffsetX: 2,
          shadowOffsetY: 2
        },
        progress: {
          show: true,
          roundCap: true,
          width: 18
        },
        pointer: {
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '12%',
          width: 20,
          offsetCenter: [0, '-60%'],
          itemStyle: { color: 'auto' }
        },
        axisLine: {
          roundCap: true,
          lineStyle: { width: 18, color: [[1, 'rgba(58, 56, 139, 0.12)']] }
        },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '-8%'],
          fontSize: 52,
          fontWeight: 'bolder',
          formatter: '{value}%',
          color: '#191c1e'
        },
        data: [{ value: score }]
      }
    ]
  }
  gaugeChartInstance.setOption(option)
}

// === 2. 初始化词云 ===
const initWordCloud = () => {
  const dom = document.getElementById('cloudChart')
  if (!dom) return
  cloudChartInstance = echarts.init(dom)

  const skills = analysis.value.coreSkills || []
  
  // Transform skills to WordCloud format
  const wordData = skills.map((s, idx) => {
    // 随机一个权重大小组合，这里根据 index 简单递减
    return {
      name: s.name,
      value: 100 - (idx * 5) + Math.random() * 20
    }
  })

  // Add dummy padding words if too few
  if (wordData.length < 15) {
    const filler = ['Git', 'Maven', 'Linux', 'Teamwork', 'Agile', 'SVN', 'Docker']
    filler.forEach(f => {
      wordData.push({ name: f, value: 20 + Math.random() * 30 })
    })
  }

  const option = {
    tooltip: {
      show: true,
      backgroundColor: '#faf9f5',
      borderColor: '#e8e6dc',
      borderWidth: 1,
      textStyle: {
        color: '#191c1e'
      },
      extraCssText: 'box-shadow: 0 12px 30px rgba(25, 28, 30, 0.08); border-radius: 12px;'
    },
    series: [{
      type: 'wordCloud',
      shape: 'diamond',
      keepAspect: false,
      left: 'center',
      top: 'center',
      width: '100%',
      height: '100%',
      right: null,
      bottom: null,
      sizeRange: [14, 60],
      rotationRange: [-45, 45],
      rotationStep: 45,
      gridSize: 8,
      drawOutOfBound: false,
      layoutAnimation: true,
      textStyle: {
        fontFamily: 'sans-serif',
        fontWeight: 'bold',
        color: function () {
          // Color Palette: Emerald, Cyan, Slate
          const colors = ['#10b981', '#34d399', '#0ea5e9', '#38bdf8', '#818cf8', '#94a3b8', '#e2e8f0']
          return colors[Math.floor(Math.random() * colors.length)]
        }
      },
      emphasis: {
        focus: 'self',
        textStyle: {
          textShadowBlur: 0,
          textShadowColor: 'transparent'
        }
      },
      data: wordData
    }]
  }
  cloudChartInstance.setOption(option)
}

// === 跳转面试 ===
const confirmStart = (mode) => {
  showModeDialog.value = false
  const effectiveRole = role !== '软件开发' ? role : (pref.value.defaultRole || 'Java 后端开发')
  const focus = (() => {
    try { const areas = JSON.parse(pref.value.focusAreas || '[]'); return Array.isArray(areas) ? areas.join(',') : '' }
    catch { return '' }
  })()
  const path = mode === 'video' ? '/video-interview' : '/interview'
  router.push({ path, query: {
    role: effectiveRole,
    isTailored: 'true',
    difficulty: pref.value.difficultyLevel || 'mid',
    focus
  }})
}

</script>

<style scoped>
.resume-page {
  min-height: 100vh;
  background: #f7f9fb;
  color: #191c1e;
}

.resume-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 12;
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

.resume-main {
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 28px 32px 40px;
  box-sizing: border-box;
}

.empty-shell {
  padding: 48px 24px;
}

.empty-copy {
  max-width: 640px;
}

.resume-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  align-items: start;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.section-shell,
.sidebar-card {
  padding: 24px;
}

.surface-card {
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.hero-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.hero-copy {
  min-width: 0;
  max-width: 640px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.hero-metric {
  padding: 14px 16px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.metric-label {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  color: #454652;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.hero-metric strong {
  display: block;
  color: #191c1e;
  font-size: 15px;
  line-height: 1.4;
}

.bento-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 24px;
}

.bento-item {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px;
}

.bento-item:hover {
  background: #ffffff;
  border-color: rgba(58, 56, 139, 0.14);
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.06);
}

.bento-title {
  font-size: 18px;
  font-weight: 800;
  color: #191c1e;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.bento-title.highlight {
  color: #3a388b;
}

.chart-container {
  flex: 0 0 220px;
  width: 100%;
  min-height: 220px;
}

.gauge-box {
  grid-column: span 4;
}

.cloud-box {
  grid-column: span 8;
}

.questions-box {
  grid-column: span 8;
}

.projects-box {
  grid-column: span 4;
}

.evaluation-text {
  margin-top: 8px;
  padding: 14px;
  background: #faf9f5;
  border-radius: 12px;
  font-size: 13px;
  color: #454652;
  line-height: 1.7;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.q-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-right: 4px;
}

.q-item {
  display: flex;
  gap: 14px;
  background: #faf9f5;
  padding: 18px;
  border-radius: 14px;
  border-left: 4px solid #3a388b;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.q-item:hover {
  transform: translateY(-1px);
  background: #faf9f5;
  border-left-color: #3a388b;
  box-shadow: 0 8px 20px rgba(25, 28, 30, 0.04);
}

.q-idx {
  background: rgba(58, 56, 139, 0.1);
  color: #3a388b;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  flex-shrink: 0;
}

.q-text {
  font-size: 15px;
  line-height: 1.7;
  color: #191c1e;
}

.timeline-wrap {
  overflow-y: auto;
  padding: 6px 0 0;
}

.timeline-wrap :deep(.el-timeline-item__timestamp) {
  color: #454652;
}

.proj-card {
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
  color: #191c1e;
  font-size: 13px;
  line-height: 1.6;
}

.timeline-wrap :deep(.el-card.proj-card:hover) {
  background: #faf9f5;
  border-color: rgba(58, 56, 139, 0.14);
  box-shadow: 0 8px 20px rgba(25, 28, 30, 0.04);
}

.sidebar-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sidebar-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.summary-label {
  color: #454652;
  font-size: 12px;
}

.summary-row strong {
  color: #191c1e;
  font-size: 13px;
  text-align: right;
}

.summary-foot {
  padding: 14px;
  border-radius: 12px;
  background: #f4f6fc;
  border: 1px solid rgba(58, 56, 139, 0.08);
}

.summary-foot p,
.mentor-copy p {
  margin: 0;
  color: #454652;
  font-size: 13px;
  line-height: 1.7;
}

.mentor-card {
  position: sticky;
  top: 96px;
}

.mentor-cta {
  width: 100%;
  border-radius: 12px;
}

.mode-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 6px 0 4px;
}

.mode-card {
  padding: 24px 18px;
  text-align: center;
  border-radius: 16px;
  border: 1px solid rgba(69, 70, 82, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  background: #ffffff;
}

.mode-card:hover {
  border-color: rgba(58, 56, 139, 0.28);
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(25, 28, 30, 0.06);
}

.mode-card.video {
  background: #f4fbf8;
}

.mode-card h3 {
  font-size: 18px;
  font-weight: 800;
  color: #191c1e;
  margin: 10px 0 8px;
}

.mode-card p {
  font-size: 13px;
  color: #454652;
  line-height: 1.6;
  margin: 0 0 12px;
}

.mode-icon {
  font-size: 40px;
  line-height: 1;
}

@media (max-width: 1100px) {
  .resume-grid {
    grid-template-columns: 1fr;
  }

  .mentor-card {
    position: static;
  }
}

@media (max-width: 960px) {
  .page-header,
  .hero-strip {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }

  .bento-grid {
    grid-template-columns: 1fr;
  }

  .gauge-box,
  .cloud-box,
  .questions-box,
  .projects-box {
    grid-column: auto;
  }

  .mode-options {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-header,
  .resume-main {
    padding-left: 16px;
    padding-right: 16px;
  }

  .resume-main {
    padding-top: 20px;
  }

  .section-shell,
  .sidebar-card,
  .bento-item {
    padding: 18px 16px;
  }

  .page-title {
    font-size: 20px;
  }
}

</style>
