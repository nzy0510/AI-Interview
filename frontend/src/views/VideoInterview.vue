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

    <!-- Report Overlay (reuse style from Interview.vue) -->
    <div v-if="showReport" class="report-overlay">
      <el-card class="report-card">
        <template #header>
          <div class="rpt-header">
            <span class="rpt-title">📋 面试评估报告</span>
            <el-tag type="success" effect="dark" size="large" round>综合得分 {{ displayScore }} / 100</el-tag>
          </div>
        </template>

        <div class="report-body">
          <el-tabs v-model="activeTab" class="rpt-tabs">
            <!-- Tab 1: Radar -->
            <el-tab-pane label="🎯 能力雷达图" name="radar">
              <div class="radar-section">
                <canvas ref="radarRef" width="360" height="360" class="radar-canvas" />
                <div class="radar-legend">
                  <div v-for="(dim, key) in abilityDimensions" :key="key" class="legend-item">
                    <span class="legend-dot" :style="{ background: dim.color }"></span>
                    <span class="legend-label">{{ dim.label }}</span>
                    <el-tag :type="getGradeType(reportData.ability[key])" size="small">
                      {{ reportData.ability[key] || '—' }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab 2: Feedback + Emotion -->
            <el-tab-pane label="📝 面试反馈" name="feedback">
              <div class="feedback-section">
                <div class="metric-cards">
                  <div class="metric-card">
                    <div class="metric-value blue">{{ reportData.wpm || '—' }}</div>
                    <div class="metric-label">平均语速 (WPM)</div>
                  </div>
                  <div class="metric-card" v-if="emotionSummary">
                    <div class="metric-value green">{{ (emotionSummary.avgConfidence * 100).toFixed(0) }}%</div>
                    <div class="metric-label">自信指数</div>
                  </div>
                  <div class="metric-card" v-if="emotionSummary">
                    <div class="metric-value orange">{{ emotionLabel(emotionSummary.dominantEmotion) }}</div>
                    <div class="metric-label">主导情绪</div>
                  </div>
                  <div class="metric-card">
                    <div class="metric-value red">{{ totalRounds }}</div>
                    <div class="metric-label">对话轮次</div>
                  </div>
                </div>

                <!-- Emotion distribution bar (only in report) -->
                <div v-if="emotionSummary" class="emotion-chart">
                  <h4>📊 情绪分布</h4>
                  <div v-for="(val, key) in emotionSummary.emotionDistribution" :key="key" class="emotion-bar-row">
                    <span class="emotion-name">{{ emotionLabel(key) }}</span>
                    <div class="emotion-bar-bg">
                      <div class="emotion-bar-fill" :style="{ width: (val * 100) + '%', background: emotionColor(key) }"></div>
                    </div>
                    <span class="emotion-pct">{{ (val * 100).toFixed(0) }}%</span>
                  </div>
                </div>

                <h4 style="margin-top: 20px">AI 综合点评</h4>
                <p class="feedback-text">{{ reportData.feedback || '暂无反馈' }}</p>
              </div>
            </el-tab-pane>

            <!-- Tab 3: Recommendations -->
            <el-tab-pane label="🚀 提升计划" name="plan">
              <div v-if="reportData.recommendations?.length" class="recommendations">
                <el-timeline>
                  <el-timeline-item
                    v-for="(rec, i) in reportData.recommendations" :key="i"
                    :timestamp="rec.period"
                    placement="top"
                    :color="['#409EFF', '#E6A23C', '#67C23A'][i % 3]"
                  >
                    <el-card shadow="hover">
                      <h4>{{ rec.action }}</h4>
                      <p>{{ rec.detail }}</p>
                    </el-card>
                  </el-timeline-item>
                </el-timeline>
              </div>
              <el-empty v-else description="暂无建议数据" />
            </el-tab-pane>
          </el-tabs>

          <div class="report-actions">
            <el-button @click="router.push('/history')">查看历史</el-button>
            <el-button type="primary" @click="router.push('/')">返回大厅</el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'
import { initModels, analyzeFrame, getEmotionSummary, EMOTION_LABELS } from '@/utils/emotionAnalyzer'

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
const currentAgent = ref('面试组长')
const ttsEnabled = ref(false) // TTS 默认关闭
const activeTab = ref('radar')
const displayScore = ref(0)

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
  // 1. Open camera + microphone
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: true })
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
  try {
    const id = await startInterviewAPI({ position: position.value, mode: 'video' })
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
  else if (totalRounds.value < 5) currentAgent.value = '技术面试官'
  else if (totalRounds.value < 7) currentAgent.value = 'HR 面试官'
  else currentAgent.value = '面试组长'

  const url = `/api/interview/chatStream?recordId=${recordId.value}&message=${encodeURIComponent(message)}`
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
      activeTab.value = 'radar'
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

// ─── Radar Chart (same logic as Interview.vue) ───────────────────────────────
watch(activeTab, (tab) => {
  if (tab === 'radar') nextTick(() => animateRadar())
})

function animateScore(target) {
  let current = 0
  const step = Math.ceil(target / 40)
  const timer = setInterval(() => {
    current += step
    if (current >= target) { current = target; clearInterval(timer) }
    displayScore.value = current
  }, 25)
}

function animateRadar() {
  const canvas = radarRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const W = canvas.width, H = canvas.height, cx = W / 2, cy = H / 2, R = Math.min(W, H) / 2 - 40
  const dims = Object.keys(abilityDimensions)
  const n = dims.length
  const gradeMap = { A: 1.0, B: 0.8, C: 0.6, D: 0.4, E: 0.2 }

  ctx.clearRect(0, 0, W, H)

  // Grid
  for (let lv = 1; lv <= 5; lv++) {
    ctx.beginPath()
    const r = R * lv / 5
    for (let i = 0; i <= n; i++) {
      const angle = (Math.PI * 2 * i) / n - Math.PI / 2
      const x = cx + r * Math.cos(angle)
      const y = cy + r * Math.sin(angle)
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.strokeStyle = 'rgba(200,200,200,0.3)'
    ctx.stroke()
  }

  // Axes + labels
  for (let i = 0; i < n; i++) {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    ctx.beginPath(); ctx.moveTo(cx, cy)
    ctx.lineTo(cx + R * Math.cos(angle), cy + R * Math.sin(angle))
    ctx.strokeStyle = 'rgba(200,200,200,0.2)'; ctx.stroke()
    const lx = cx + (R + 24) * Math.cos(angle)
    const ly = cy + (R + 24) * Math.sin(angle)
    ctx.fillStyle = '#606266'; ctx.font = '13px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillText(abilityDimensions[dims[i]].label, lx, ly)
  }

  // Data polygon
  ctx.beginPath()
  for (let i = 0; i <= n; i++) {
    const idx = i % n
    const angle = (Math.PI * 2 * idx) / n - Math.PI / 2
    const grade = reportData.ability[dims[idx]] || 'D'
    const val = gradeMap[grade] || 0.2
    const x = cx + R * val * Math.cos(angle)
    const y = cy + R * val * Math.sin(angle)
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
  }
  ctx.closePath()
  ctx.fillStyle = 'rgba(64, 158, 255, 0.25)'; ctx.fill()
  ctx.strokeStyle = '#409EFF'; ctx.lineWidth = 2; ctx.stroke()

  // Data points
  for (let i = 0; i < n; i++) {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    const grade = reportData.ability[dims[i]] || 'D'
    const val = gradeMap[grade] || 0.2
    ctx.beginPath()
    ctx.arc(cx + R * val * Math.cos(angle), cy + R * val * Math.sin(angle), 4, 0, Math.PI * 2)
    ctx.fillStyle = abilityDimensions[dims[i]].color; ctx.fill()
  }
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
.status-badge.speaking { background: rgba(64, 158, 255, 0.7); }
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
.ai-label.coordinator { background: rgba(64, 158, 255, 0.8); }
.ai-label.technical { background: rgba(245, 108, 108, 0.8); }
.ai-label.hr { background: rgba(103, 194, 58, 0.8); }
.subtitle-text {
  font-size: 15px;
  line-height: 1.6;
  color: #eee;
  max-height: 120px;
  overflow-y: auto;
}
.subtitle-text :deep(p) { margin: 0; }
.blinking-cursor { animation: blink 0.8s infinite; color: #409EFF; }

@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
@keyframes fadeInDown { from { opacity: 0; transform: translateX(-50%) translateY(-10px); } to { opacity: 1; transform: translateX(-50%) translateY(0); } }

/* Report */
.report-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(8px);
  overflow-y: auto;
}
.report-card {
  width: 780px;
  max-width: 95vw;
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.3);
}
.rpt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rpt-title { font-size: 20px; font-weight: 700; }

.radar-section { display: flex; gap: 20px; flex-wrap: wrap; justify-content: center; align-items: center; padding: 10px; }
.radar-canvas { border: 1px solid #eee; border-radius: 12px; }
.radar-legend { display: flex; flex-direction: column; gap: 12px; }
.legend-item { display: flex; align-items: center; gap: 8px; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }
.legend-label { font-size: 14px; color: #606266; min-width: 70px; }

.feedback-section { padding: 10px; }
.metric-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; margin-bottom: 20px; }
.metric-card { text-align: center; padding: 16px 10px; background: #f5f7fa; border-radius: 12px; }
.metric-value { font-size: 28px; font-weight: 700; }
.metric-value.blue { color: #409EFF; }
.metric-value.green { color: #67C23A; }
.metric-value.orange { color: #E6A23C; }
.metric-value.red { color: #F56C6C; }
.metric-label { font-size: 12px; color: #909399; margin-top: 4px; }
.feedback-text { font-size: 15px; line-height: 1.8; color: #303133; background: #fafafa; padding: 16px; border-radius: 12px; }

.emotion-chart { margin-top: 16px; padding: 16px; background: #fafafa; border-radius: 12px; }
.emotion-chart h4 { margin: 0 0 12px; font-size: 15px; }
.emotion-bar-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.emotion-name { min-width: 50px; font-size: 13px; color: #606266; text-align: right; }
.emotion-bar-bg { flex: 1; height: 16px; background: #ebeef5; border-radius: 8px; overflow: hidden; }
.emotion-bar-fill { height: 100%; border-radius: 8px; transition: width 0.6s ease; }
.emotion-pct { min-width: 40px; font-size: 13px; color: #909399; }

.recommendations { padding: 10px; }
.report-actions { display: flex; justify-content: center; gap: 16px; margin-top: 20px; }

.rpt-tabs :deep(.el-tabs__header) { padding: 0 16px; }
</style>
