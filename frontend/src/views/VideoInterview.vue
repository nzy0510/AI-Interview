<template>
  <div class="video-interview-container">
    <!-- Header -->
    <header class="vi-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="router.back()" />
        <span class="title">{{ position }} · 视频面试</span>
        <el-tag type="info" size="small" effect="plain" class="mode-tag">📹 视频模式</el-tag>
      </div>
      <div class="header-right">
        <el-switch
          v-model="ttsEnabled"
          active-text="AI 语音"
          inactive-text=""
          style="margin-right: 12px"
          :disabled="showReport"
        />
        <el-button type="danger" size="small" @click="endInterview" :loading="isFinishing" :disabled="totalRounds < 1">
          结束并生成报告
        </el-button>
      </div>
    </header>

    <!-- Main: Camera fullscreen -->
    <main class="vi-main" v-show="!showReport">
      <video ref="videoRef" autoplay muted playsinline class="camera-video" />
      <!-- Subtle status indicators (no emotion data shown) -->
      <div class="status-overlay">
        <span v-if="isSpeaking" class="status-badge speaking">🔊 AI 正在发言</span>
        <span v-else-if="isListening" class="status-badge listening">🎙️ 正在聆听</span>
        <span v-else-if="isStreaming" class="status-badge thinking">🤔 AI 思考中</span>
      </div>

      <!-- AI subtitle bar at bottom -->
      <div class="subtitle-bar">
        <div class="ai-label" :class="agentClass">{{ agentEmoji }} {{ currentAgent }}</div>
        <div class="subtitle-text" v-html="renderMarkdown(currentAiText)"></div>
        <span v-if="isStreaming" class="blinking-cursor">▍</span>
      </div>
    </main>

    <!-- Dashboard Overlay -->
    <div v-if="showReport" class="dashboard-overlay">
      <div class="dashboard-container">
        <div class="dash-header">
          <span class="dash-title">📋 智能面试深度体检报告</span>
          <div class="dash-actions">
            <el-button @click="router.push('/history')" plain>查看历史</el-button>
            <el-button type="primary" @click="router.push('/')">返回大厅</el-button>
          </div>
        </div>

        <div class="bento-grid">
          <!-- Top Left: Score -->
          <div class="bento-card bento-score">
            <h3 class="bento-card-title">综合能力评定</h3>
            <div class="score-display">
              <el-progress type="dashboard" :percentage="displayScore" :color="scoreColor" :width="160" :stroke-width="12">
                <template #default="{ percentage }">
                  <div class="score-val">{{ percentage }}</div>
                  <div class="score-lbl">总分</div>
                </template>
              </el-progress>
              <div class="grade-badge" :style="{ color: scoreColor }">评级: {{ reportData.score >= 90 ? '卓越 (A)' : reportData.score >= 75 ? '良好 (B)' : '及格 (C)' }}</div>
            </div>
          </div>

          <!-- Top Right: Radar Diagram -->
          <div class="bento-card bento-radar">
            <h3 class="bento-card-title">六维能力图谱</h3>
            <div ref="radarRef" class="echarts-container"></div>
          </div>

          <!-- Middle: KPIs -->
          <div class="bento-card bento-kpis">
            <div class="kpi-item">
              <div class="kpi-icon">🎤</div>
              <div class="kpi-data">
                <div class="kpi-val">{{ reportData.wpm || '—' }} <span class="unit">WPM</span></div>
                <div class="kpi-lbl">平均语速</div>
              </div>
            </div>
            <div class="kpi-item">
              <div class="kpi-icon">🗣️</div>
              <div class="kpi-data">
                <div class="kpi-val">{{ totalRounds }}</div>
                <div class="kpi-lbl">交流轮次</div>
              </div>
            </div>
            <div class="kpi-item" v-if="emotionSummary">
              <div class="kpi-icon">✨</div>
              <div class="kpi-data">
                <div class="kpi-val">{{ (emotionSummary.avgConfidence * 100).toFixed(0) }}<span class="unit">%</span></div>
                <div class="kpi-lbl">表现自信指数</div>
              </div>
            </div>
            <div class="kpi-item" v-if="emotionSummary">
              <div class="kpi-icon">🎭</div>
              <div class="kpi-data">
                <div class="kpi-val highlight">{{ emotionLabel(emotionSummary.dominantEmotion) }}</div>
                <div class="kpi-lbl">主导情绪</div>
              </div>
            </div>
          </div>

          <!-- Bottom Left: AI Eval -->
          <div class="bento-card bento-feedback">
            <h3 class="bento-card-title">🤖 面试官综合评价</h3>
            <div class="markdown-body custom-md" v-html="renderMarkdown(reportData.feedback || '暂无反馈')"></div>
          </div>

          <!-- Bottom Right: Roadmap -->
          <div class="bento-card bento-roadmap">
            <h3 class="bento-card-title">🚀 定制化提升路线</h3>
            <div v-if="reportData.recommendations?.length" class="timeline-wrapper">
              <el-timeline>
                <el-timeline-item
                  v-for="(rec, i) in reportData.recommendations" :key="i"
                  :timestamp="rec.period"
                  placement="top"
                  :type="i === 0 ? 'success' : 'primary'"
                  :hollow="i !== 0"
                >
                  <div class="roadmap-item">
                    <h4>{{ rec.action }}</h4>
                    <p>{{ rec.detail }}</p>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
            <el-empty v-else description="暂无针对性建议" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { marked } from 'marked'
import * as echarts from 'echarts'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'
import { initModels, analyzeFrame, getEmotionSummary, EMOTION_LABELS } from '@/utils/emotionAnalyzer'
import { userKey } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const position = ref(route.query.role || 'Java后端开发')

// ─── State ────────────────────────────────────────────────────────────────────
const videoRef = ref(null)
const radarRef = ref(null)
const recordId = ref(null)
const isStreaming = ref(false)
const isFinishing = ref(false)
const isListening = ref(false)
const isSpeaking = ref(false)
const showReport = ref(false)
const currentAiText = ref('')
const totalRounds = ref(0)
const hrOverridden = ref(false)
const currentAgent = ref('面试组长')
const ttsEnabled = ref(false) // TTS 默认关闭
const displayScore = ref(0)
let radarChartInstance = null

const scoreColor = computed(() => {
  if (displayScore.value >= 90) return '#10b981'
  if (displayScore.value >= 75) return '#f59e0b'
  if (displayScore.value >= 60) return '#0ea5e9'
  return '#ef4444'
})

const reportData = reactive({
  score: 0, feedback: '', wpm: 0,
  ability: {}, recommendations: []
})

// Emotion data (collected in background, not displayed during interview)
const emotionTimeline = ref([])
const emotionSummary = ref(null)
let emotionInterval = null

// Media
let mediaStream = null
let recognition = null
let silenceTimer = null
const SILENCE_MS = 2500

// TTS
let currentUtterance = null

// WPM tracking
let voiceTurns = []
let turnStart = 0

// ─── Ability dimensions (same as Interview.vue) ─────────────────────────────
const abilityDimensions = {
  techDepth:      { label: '技术深度', color: '#409EFF' },
  breadth:        { label: '知识广度', color: '#67C23A' },
  problemSolving: { label: '解题思路', color: '#E6A23C' },
  expression:     { label: '表达清晰', color: '#F56C6C' },
  logic:          { label: '逻辑思维', color: '#909399' },
  adaptability:   { label: '应变能力', color: '#C71585' }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
const renderMarkdown = (text) => text ? marked.parse(text) : ''

const emotionLabel = (key) => EMOTION_LABELS[key] || key

const emotionColor = (key) => {
  const colors = { neutral: '#909399', happy: '#67C23A', sad: '#5B9BD5', angry: '#F56C6C', fearful: '#E6A23C', disgusted: '#C71585', surprised: '#409EFF' }
  return colors[key] || '#909399'
}

const getGradeType = (grade) => {
  const map = { A: 'danger', B: 'success', C: 'primary', D: 'warning', E: 'info' }
  return map[grade] || 'info'
}

// Agent styling for subtitle label
const agentClass = ref('coordinator')
const agentEmoji = ref('👔')
watch(currentAgent, (v) => {
  if (v.includes('技术')) { agentClass.value = 'technical'; agentEmoji.value = '🔧' }
  else if (v.includes('HR')) { agentClass.value = 'hr'; agentEmoji.value = '💼' }
  else { agentClass.value = 'coordinator'; agentEmoji.value = '👔' }
})

// ─── Lifecycle ────────────────────────────────────────────────────────────────
onMounted(async () => {
  // 1. Open camera + microphone (Requesting 720p/1080p high resolution)
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: 'user',
        width: { ideal: 1920 },
        height: { ideal: 1080 }
      },
      audio: true
    })
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
    }
  } catch {
    ElMessage.error('无法访问摄像头/麦克风，请授权后重试')
    router.back()
    return
  }

  // 2. Init emotion models (async, non-blocking)
  initModels().then(ok => {
    if (ok) startEmotionSampling()
    else console.warn('[VideoInterview] 情感分析模型加载失败，将跳过情感采集')
  })

  // 3. Start interview session
  const isTailored = route.query.isTailored === 'true'
  let resumeQuestions = undefined
  if (isTailored) {
    try {
      const cached = localStorage.getItem(userKey('resume_analysis'))
      if (cached) {
        const parsed = JSON.parse(cached)
        if (parsed && parsed.tailoredQuestions) {
          resumeQuestions = parsed.tailoredQuestions
        }
      }
    } catch {}
    // localStorage 无数据时，静默尝试从后端获取
    if (!resumeQuestions) {
      try {
        const token = localStorage.getItem('token')
        const resp = await fetch((import.meta.env.VITE_API_BASE_URL || '') + '/api/resume/profile', {
          headers: { Authorization: `Bearer ${token}` }
        })
        if (resp.ok) {
          const result = await resp.json()
          if (result.code === 200 && result.data && result.data.tailoredQuestions) {
            resumeQuestions = result.data.tailoredQuestions
          }
        }
      } catch {}
    }
  }

  try {
    const id = await startInterviewAPI({ position: position.value, mode: 'video', resumeQuestions })
    recordId.value = id
    // 4. AI starts first — trigger opening
    triggerAiTurn()
  } catch {
    ElMessage.error('连接失败，请确认后端已启动')
    router.back()
  }
})

onBeforeUnmount(() => {
  stopListening()
  stopSpeaking()
  stopEmotionSampling()
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
})

// ─── Emotion Sampling (background, every 3 seconds) ──────────────────────────
function startEmotionSampling() {
  emotionInterval = setInterval(async () => {
    if (!videoRef.value || showReport.value) return
    const result = await analyzeFrame(videoRef.value)
    if (result) {
      emotionTimeline.value.push({
        timestamp: Date.now(),
        ...result
      })
    }
  }, 3000)
}

function stopEmotionSampling() {
  if (emotionInterval) { clearInterval(emotionInterval); emotionInterval = null }
}

// ─── AI Turn (SSE) ───────────────────────────────────────────────────────────
function triggerAiTurn() {
  const msg = '（面试开始，请开场）'
  sendToAI(msg)
}

function sendToAI(message) {
  if (!recordId.value || isStreaming.value) return
  isStreaming.value = true
  isListening.value = false
  currentAiText.value = ''

  // Determine current agent based on round count
  if (totalRounds.value === 0) currentAgent.value = '面试组长'
  else if (totalRounds.value >= 12) currentAgent.value = '面试组长'
  else if (hrOverridden.value || totalRounds.value >= 9) currentAgent.value = 'HR 面试官'
  else currentAgent.value = '技术面试官'

  const token = localStorage.getItem('token') || ''
  const url = `/api/interview/chatStream?recordId=${recordId.value}&message=${encodeURIComponent(message)}&token=${token}`
  const eventSource = new EventSource(url)

  let fullText = ''

  eventSource.onmessage = (e) => {
    let d
    try { d = JSON.parse(e.data) } catch { return }

    if (d.error) {
      ElMessage.error('AI 错误: ' + d.error)
      isStreaming.value = false
      eventSource.close()
      startListening()
      return
    }

    if (d.done === 'true' || d.done === true) {
      isStreaming.value = false
      eventSource.close()
      totalRounds.value++
      // Speak the AI response
      speakText(fullText)
      return
    }

    if (d.content !== undefined && d.content !== null) {
      fullText += d.content

      // Check for [SWITCH_TO_HR]
      if (fullText.includes('[SWITCH_TO_HR]')) {
        fullText = fullText.replace('[SWITCH_TO_HR]', '')
        hrOverridden.value = true
        // Switch immediately visually
        currentAgent.value = 'HR 面试官'
      }

      currentAiText.value = fullText

      // Check for [TERMINATE]
      if (fullText.includes('[TERMINATE]')) {
        currentAiText.value = fullText.replace('[TERMINATE]', '').trim()
        isStreaming.value = false
        eventSource.close()
        totalRounds.value++
        ElMessage.warning('面试即将结束，正在生成报告...')
        speakText(currentAiText.value, () => {
          performEndInterview()
        })
        return
      }
    }
  }

  eventSource.onerror = () => {
    isStreaming.value = false
    eventSource.close()
    startListening()
  }
}

// ─── TTS (Text-to-Speech with gender switching) ──────────────────────────────
function speakText(text, onDone = null) {
  // 如果 TTS 关闭，跳过朗读，直接进入下一步
  if (!ttsEnabled.value || !text || !window.speechSynthesis) {
    if (onDone) onDone()
    else startListening()
    return
  }

  stopSpeaking()
  isSpeaking.value = true

  // Clean markdown for speech
  const cleanText = text.replace(/[#*`\[\]()]/g, '').replace(/\n+/g, '。').substring(0, 500)

  currentUtterance = new SpeechSynthesisUtterance(cleanText)
  currentUtterance.lang = 'zh-CN'
  currentUtterance.rate = 1.05
  currentUtterance.pitch = 1.0

  // Gender by agent role: Male for Coordinator/Technical, Female for HR
  const voices = window.speechSynthesis.getVoices()
  const isHR = currentAgent.value.includes('HR')

  if (voices.length > 0) {
    // Try to find Chinese voices
    const zhVoices = voices.filter(v => v.lang.startsWith('zh'))
    if (zhVoices.length > 0) {
      if (isHR) {
        // Prefer female voice for HR
        const female = zhVoices.find(v => v.name.toLowerCase().includes('female') || v.name.includes('女') || v.name.includes('Yaoyao') || v.name.includes('Huihui'))
        currentUtterance.voice = female || zhVoices[zhVoices.length - 1]
      } else {
        // Prefer male voice for Coordinator/Technical
        const male = zhVoices.find(v => v.name.toLowerCase().includes('male') || v.name.includes('男') || v.name.includes('Kangkang') || v.name.includes('Yunxi'))
        currentUtterance.voice = male || zhVoices[0]
      }
    }
  }

  currentUtterance.onend = () => {
    isSpeaking.value = false
    if (onDone) onDone()
    else startListening()
  }

  currentUtterance.onerror = () => {
    isSpeaking.value = false
    if (onDone) onDone()
    else startListening()
  }

  window.speechSynthesis.speak(currentUtterance)
}

function stopSpeaking() {
  if (window.speechSynthesis) window.speechSynthesis.cancel()
  isSpeaking.value = false
  currentUtterance = null
}

// ─── Speech Recognition (background, auto-send on silence) ───────────────────
function startListening() {
  if (isStreaming.value || isSpeaking.value || showReport.value) return

  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) return

  isListening.value = true
  let confirmedText = ''

  recognition = new SR()
  recognition.continuous = true
  recognition.interimResults = true
  recognition.lang = 'zh-CN'
  recognition.maxAlternatives = 1

  recognition.onresult = (event) => {
    let interim = ''
    let newFinal = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      if (event.results[i].isFinal) newFinal += event.results[i][0].transcript
      else interim += event.results[i][0].transcript
    }
    if (newFinal) confirmedText += newFinal

    // Auto-send on silence
    if (silenceTimer) clearTimeout(silenceTimer)
    if ((confirmedText + interim).trim()) {
      silenceTimer = setTimeout(() => {
        if (confirmedText.trim()) {
          const msg = confirmedText.trim()
          // Track WPM
          const elapsed = (Date.now() - turnStart) / 1000
          if (elapsed > 1 && msg.length > 0) {
            voiceTurns.push({ chars: msg.length, durationSec: elapsed })
          }
          // Stop listening before sending
          if (recognition) recognition.onend = null
          stopListening()
          sendToAI(msg)
        }
      }, SILENCE_MS)
    }
  }

  recognition.onend = () => {
    if (isListening.value && !isStreaming.value && !isSpeaking.value) recognition.start()
  }

  recognition.onerror = (e) => {
    if (e.error !== 'no-speech') console.warn('[SR] Error:', e.error)
    if (e.error === 'aborted' || e.error === 'not-allowed') {
      stopListening()
      ElMessage.warning('录音被系统中断或无权限，请检查麦克风设置')
    }
  }

  turnStart = Date.now()
  recognition.start()
}

function stopListening() {
  isListening.value = false
  if (silenceTimer) { clearTimeout(silenceTimer); silenceTimer = null }
  if (recognition) { recognition.onend = null; recognition.stop(); recognition = null }
}

// ─── End Interview ───────────────────────────────────────────────────────────
const endInterview = async () => {
  if (totalRounds.value < 1) { ElMessage.warning('请至少完成一轮对话'); return }
  try {
    await ElMessageBox.confirm('确定结束面试？AI 将综合分析并生成详细报告（约30秒）。', '结束面试', {
      confirmButtonText: '确认结束', cancelButtonText: '继续面试', type: 'warning'
    })
  } catch { return }
  performEndInterview()
}

const performEndInterview = async () => {
  stopListening()
  stopSpeaking()
  stopEmotionSampling()
  isFinishing.value = true

  // Calculate WPM
  const wpm = calcAvgWpm()
  // Generate emotion summary
  emotionSummary.value = getEmotionSummary(emotionTimeline.value)

  const loadingMsg = ElMessage({ message: '🤖 正在深度分析，请稍候...', type: 'info', duration: 0 })

  try {
    const res = await finishInterviewAPI({
      recordId: recordId.value,
      wpm,
      emotionJson: emotionSummary.value ? JSON.stringify(emotionSummary.value) : null
    })
    loadingMsg.close()

    if (res) {
      reportData.score = res.score || 0
      reportData.feedback = res.feedback || ''
      reportData.wpm = wpm

      try { reportData.ability = typeof res.abilityJson === 'string' ? JSON.parse(res.abilityJson) : (res.abilityJson || {}) }
      catch { reportData.ability = {} }

      try { reportData.recommendations = typeof res.recommendations === 'string' ? JSON.parse(res.recommendations) : (res.recommendations || []) }
      catch { reportData.recommendations = [] }

      showReport.value = true
      nextTick(() => {
        animateScore(reportData.score)
        animateRadar()
      })
    }
  } catch (err) {
    loadingMsg.close()
    ElMessage.error('报告生成失败: ' + (err.message || '请检查后端连接'))
  } finally {
    isFinishing.value = false
  }
}

function calcAvgWpm() {
  if (voiceTurns.length === 0) return 0
  const totalChars = voiceTurns.reduce((s, t) => s + t.chars, 0)
  const totalSec = voiceTurns.reduce((s, t) => s + t.durationSec, 0)
  return totalSec > 0 ? Math.round((totalChars / totalSec) * 60) : 0
}

// ─── ECharts Radar ─────────────────────────────────────────────────────────────────
function animateScore(target) {
  let current = 0
  const step = Math.ceil(target / 40) || 1
  const timer = setInterval(() => {
    current += step
    if (current >= target) { current = target; clearInterval(timer) }
    displayScore.value = current
  }, 25)
}

function animateRadar() {
  if (!radarRef.value) return
  if (!radarChartInstance) radarChartInstance = echarts.init(radarRef.value)
  
  const rec = reportData
  if (!rec) return
  
  const gradeToNum = (grade) => {
    const map = { A: 95, B: 80, C: 65, D: 45, E: 20 }
    return map[grade] || 45
  }

  const scores = [
    gradeToNum(rec.ability.techDepth),
    gradeToNum(rec.ability.breadth),
    gradeToNum(rec.ability.logic),
    gradeToNum(rec.ability.expression),
    gradeToNum(rec.ability.adaptability),
    gradeToNum(rec.ability.problemSolving)
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
      axisName: { color: '#cbd5e1', fontSize: 13, fontWeight: 600 },
      splitNumber: 5,
      splitArea: { areaStyle: { color: ['rgba(16,185,129,0.06)', 'rgba(16,185,129,0.02)', 'transparent', 'transparent', 'transparent'] } },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } }
    },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'radar',
      data: [{
        value: scores,
        name: '综合评估',
        symbolSize: 6,
        itemStyle: { color: '#10b981', borderColor: '#fff', borderWidth: 2 },
        lineStyle: { color: '#10b981', width: 2 },
        areaStyle: { 
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16,185,129,0.6)' },
            { offset: 1, color: 'rgba(16,185,129,0.1)' }
          ])
        }
      }]
    }]
  }
  radarChartInstance.setOption(option)
}
</script>

<style scoped>
.video-interview-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #0a0a0a;
  color: #fff;
  overflow: hidden;
}

.vi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 10;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-right { display: flex; align-items: center; gap: 12px; }
.title { font-size: 16px; font-weight: 600; }
.mode-tag { margin-left: 8px; }

/* Camera */
.vi-main {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.camera-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1); /* Mirror */
}

/* Status indicators */
.status-overlay {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
}
.status-badge {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  backdrop-filter: blur(10px);
  animation: fadeInDown 0.3s ease;
}
.status-badge.speaking { background: rgba(16, 185, 129, 0.7); } /* Emerald */
.status-badge.listening { background: rgba(103, 194, 58, 0.7); }
.status-badge.thinking { background: rgba(230, 162, 60, 0.7); }

/* AI Subtitle bar */
.subtitle-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px 24px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.85));
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 70px;
  z-index: 5;
}
.ai-label {
  flex-shrink: 0;
  padding: 6px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  transition: background 0.3s;
}
.ai-label.coordinator { background: rgba(16, 185, 129, 0.8); } /* Emerald */
.ai-label.technical { background: rgba(245, 108, 108, 0.8); }
.ai-label.hr { background: rgba(245, 158, 11, 0.8); } /* Amber */
.subtitle-text {
  font-size: 15px;
  line-height: 1.6;
  color: #eee;
  max-height: 120px;
  overflow-y: auto;
}
.subtitle-text :deep(p) { margin: 0; }
.blinking-cursor { animation: blink 0.8s infinite; color: #10b981; }

@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
@keyframes fadeInDown { from { opacity: 0; transform: translateX(-50%) translateY(-10px); } to { opacity: 1; transform: translateX(-50%) translateY(0); } }

/* Dashboard Overlay / Bento Grid */
.dashboard-overlay {
  position: fixed; inset: 0; z-index: 100;
  display: flex; justify-content: center; align-items: flex-start;
  padding: 30px 20px;
  background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(16px);
  overflow-y: auto;
}
.dashboard-container {
  width: 1100px; max-width: 98vw;
  display: flex; flex-direction: column; gap: 20px;
}
.dash-header { display: flex; justify-content: space-between; align-items: center; }
.dash-title { font-size: 24px; font-weight: 800; color: #f8fafc; text-shadow: 0 2px 10px rgba(16,185,129,0.5); }
.dash-actions { display: flex; gap: 12px; }

.bento-grid {
  display: grid;
  grid-template-columns: 320px 1fr 1fr;
  grid-template-rows: auto auto auto;
  gap: 20px;
}
.bento-card {
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px;
  padding: 24px;
  display: flex; flex-direction: column;
}
.bento-card-title { margin: 0 0 16px 0; font-size: 16px; font-weight: 700; color: #cbd5e1; display: flex; align-items: center; gap: 8px; }

.bento-score { grid-column: 1 / 2; grid-row: 1 / 3; align-items: center; text-align: center; justify-content: center; }
.score-display { display: flex; flex-direction: column; align-items: center; gap: 12px; height: 100%; justify-content: center; }
.score-val { font-size: 38px; font-weight: 800; line-height: 1; }
.score-lbl { font-size: 13px; color: #94a3b8; }
.grade-badge { font-size: 18px; font-weight: 800; margin-top: 10px; padding: 6px 16px; background: rgba(255,255,255,0.05); border-radius: 12px; }

.bento-radar { grid-column: 2 / 4; grid-row: 1 / 2; min-height: 340px; }
.echarts-container { width: 100%; height: 100%; min-height: 300px; flex: 1; }

.bento-kpis { 
  grid-column: 2 / 4; grid-row: 2 / 3; 
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; padding: 16px; 
  align-items: center; justify-content: center; background: rgba(30, 41, 59, 0.4); 
}
.kpi-item { display: flex; align-items: center; gap: 12px; }
.kpi-icon { font-size: 28px; background: rgba(255,255,255,0.05); padding: 10px; border-radius: 12px; line-height: 1;}
.kpi-val { font-size: 20px; font-weight: 800; color: #f8fafc; }
.kpi-val.highlight { color: #f59e0b; }
.kpi-lbl { font-size: 12px; color: #94a3b8; }
.unit { font-size: 12px; font-weight: normal; color: #64748b; margin-left: 2px; }

.bento-feedback { grid-column: 1 / 3; grid-row: 3 / 4; min-height: 300px; max-height: 500px; overflow-y: auto; }
.bento-roadmap { grid-column: 3 / 4; grid-row: 3 / 4; min-height: 300px; max-height: 500px; overflow-y: auto; }

.custom-md { color: #e2e8f0; font-size: 15px; line-height: 1.7; }
.custom-md :deep(h1), .custom-md :deep(h2), .custom-md :deep(h3) { color: #f8fafc; border-bottom: 1px solid rgba(255,255,255,0.1); margin-top: 0; padding-bottom: 8px; }
.custom-md :deep(strong) { color: #10b981; }

.roadmap-item h4 { margin: 0 0 6px 0; color: #f8fafc; font-size: 15px; }
.roadmap-item p { margin: 0; color: #cbd5e1; font-size: 13.5px; line-height: 1.6; }
:deep(.el-timeline-item__content) { padding-bottom: 16px; }
</style>
