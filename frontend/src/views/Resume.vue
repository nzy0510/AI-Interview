<template>
  <div class="resume-container">
    <el-container class="main-layout">
      <el-header class="glass-header">
        <div class="header-left" @click="router.push('/')">
          <el-icon class="back-icon"><ArrowLeft /></el-icon> 返回大厅
        </div>
        <div class="logo-wrap">
          <span class="logo-text">AI 简历扫描透视</span>
        </div>
        <div class="header-right">
          <el-button class="launch-btn" @click="showModeDialog = true">
            🚀 基于此画像开启面试
          </el-button>
        </div>
      </el-header>

      <el-main class="dashboard-main">
        <div v-if="!analysis" class="empty-state">
          无缓存的简历解析记录，请重新上传简历。
        </div>
        
        <div v-else class="bento-grid">
          <!-- 1. AI 匹配度 (Gauge) -->
          <div class="bento-item gauge-box">
            <h3 class="bento-title"><el-icon><DataAnalysis /></el-icon> AI 匹配率评估</h3>
            <div id="gaugeChart" class="chart-container"></div>
            <div class="evaluation-text">
              <p><strong>智能洞察：</strong></p>
              {{ analysis.evaluation }}
            </div>
          </div>

          <!-- 2. 技能词云 (WordCloud) -->
          <div class="bento-item cloud-box">
            <h3 class="bento-title"><el-icon><Star /></el-icon> 核心技能星云</h3>
            <div id="cloudChart" class="chart-container"></div>
          </div>

          <!-- 3. 定制深挖预测题 (List) -->
          <div class="bento-item questions-box">
            <h3 class="bento-title highlight"><el-icon><Warning /></el-icon> 预测深挖攻击面</h3>
            <p class="section-desc">结合此简历的特定技术栈与场景，大厂高P大概率会进行如下连环追问：</p>
            <div class="q-list">
              <div v-for="(q, idx) in analysis.tailoredQuestions" :key="idx" class="q-item">
                <div class="q-idx">{{ idx + 1 }}</div>
                <div class="q-text">{{ q }}</div>
              </div>
            </div>
          </div>

          <!-- 4. 经验解构 (Timeline) -->
          <div class="bento-item projects-box">
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
                  <el-card class="proj-card" shadow="hover">
                    {{ proj.desc }}
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>
        </div>
      </el-main>
    </el-container>

    <!-- 模式选择对话框 (专属) -->
    <el-dialog v-model="showModeDialog" title="选择专属面试模式" width="480" center :close-on-click-modal="false">
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

const router = useRouter()
const route = useRoute()
const role = route.query.role || '软件开发'

const analysis = ref(null)
const showModeDialog = ref(false)

let gaugeChartInstance = null
let cloudChartInstance = null

onMounted(() => {
  const cached = localStorage.getItem('resume_analysis')
  if (cached) {
    try {
      analysis.value = JSON.parse(cached)
    } catch {}
  }

  if (analysis.value) {
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
          lineStyle: { width: 18, color: [[1, 'rgba(255,255,255,0.05)']] }
        },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '0%'],
          fontSize: 60,
          fontWeight: 'bolder',
          formatter: '{value}',
          color: '#f8fafc'
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
    tooltip: { show: true },
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
      emphasis: { focus: 'self', textStyle: { textShadowBlur: 10, textShadowColor: '#333' } },
      data: wordData
    }]
  }
  cloudChartInstance.setOption(option)
}

// === 跳转面试 ===
const confirmStart = (mode) => {
  showModeDialog.value = false
  const path = mode === 'video' ? '/video-interview' : '/interview'
  
  // 把特别定制题通过 query 或 ls 带过去 (由于过长，选用 localstorage 或直接依赖已有的 resume_analysis)
  // 此处原先代码可以直接取 localStorage.getItem('resume_analysis') 在 Interview.vue 里作为 SystemPrompt 注入
  router.push({ path, query: { role, isTailored: 'true' } })
}

</script>

<style scoped>
.resume-container {
  height: 100vh;
  width: 100vw;
  background: #0f172a; /* Deep Slate Background */
  overflow-y: auto;
  color: #f8fafc;
}

.glass-header {
  height: 70px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  background: rgba(15, 23, 42, 0.8);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #cbd5e1;
  cursor: pointer;
  transition: color 0.3s;
}
.header-left:hover {
  color: #10b981;
}

.logo-text {
  font-size: 20px;
  font-weight: 800;
  background: linear-gradient(135deg, #10b981, #0ea5e9);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.launch-btn {
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  border-radius: 99px;
  font-weight: 600;
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
  transition: transform 0.3s, box-shadow 0.3s;
  color: white;
  padding: 10px 24px;
}
.launch-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.4);
}

.dashboard-main {
  padding: 40px;
  max-width: 1400px;
  margin: 0 auto;
}

.bento-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  grid-auto-rows: 320px;
  gap: 24px;
}

.bento-item {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 24px;
  padding: 24px;
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  transition: transform 0.3s, background 0.3s;
  overflow: hidden;
}
.bento-item:hover {
  background: rgba(30, 41, 59, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

.bento-title {
  font-size: 18px;
  font-weight: 700;
  color: #f1f5f9;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.bento-title.highlight {
  color: #f59e0b;
}

.chart-container {
  flex: 1;
  width: 100%;
}

/* Grid Layout Assignments */
.gauge-box { grid-column: span 4; }
.cloud-box { grid-column: span 8; }
.questions-box { grid-column: span 8; grid-row: span 2; }
.projects-box { grid-column: span 4; grid-row: span 2; }

/* Specific Inner Styling */
.evaluation-text {
  margin-top: -20px;
  padding: 12px;
  background: rgba(0,0,0,0.2);
  border-radius: 12px;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.6;
}

.section-desc {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 20px;
}

.q-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 12px;
}
.q-list::-webkit-scrollbar { width: 4px; }
.q-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }

.q-item {
  display: flex;
  gap: 16px;
  background: rgba(15, 23, 42, 0.6);
  padding: 20px;
  border-radius: 16px;
  border-left: 4px solid #f59e0b;
  transition: transform 0.2s;
}
.q-item:hover {
  transform: translateX(4px);
  background: rgba(15, 23, 42, 0.8);
}
.q-idx {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  flex-shrink: 0;
}
.q-text {
  font-size: 15px;
  line-height: 1.6;
  color: #e2e8f0;
}

.timeline-wrap {
  overflow-y: auto;
  padding: 10px;
}
.timeline-wrap::-webkit-scrollbar { width: 0; }
.proj-card {
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.05);
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.5;
}

/* Dialog Styles */
.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 10px 0;
}
.mode-card {
  padding: 28px 20px;
  text-align: center;
  border-radius: 16px;
  border: 2px solid #e5e7eb;
  cursor: pointer;
  transition: all 0.3s;
  background: #f9fafb;
}
.mode-card:hover { border-color: #409EFF; transform: translateY(-4px); }
.mode-card.video { border-color: #d1fae5; background: #f0fdf4; }
.mode-card.video:hover { border-color: #67C23A; }
.mode-icon { font-size: 40px; margin-bottom: 12px; }
.mode-card h3 { font-size: 18px; font-weight: 700; color: #1f2937; margin-bottom: 8px; }
.mode-card p { font-size: 13px; color: #6b7280; line-height: 1.5; margin-bottom: 12px; }

</style>
