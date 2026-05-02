<template>
  <div class="video-interview-shell">
    <header class="vi-header">
      <div class="header-left">
        <el-button class="back-button" :icon="ArrowLeft" circle @click="router.back()" />
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
        <el-button type="danger" size="small" class="finish-button" @click="endInterview" :loading="isFinishing" :disabled="totalRounds < 1">
          结束并生成报告
        </el-button>
      </div>
    </header>

    <main class="vi-main" v-show="!showReport">
      <div class="camera-stage">
        <video ref="videoRef" autoplay muted playsinline class="camera-video" />
        <div class="camera-sheen"></div>

        <div class="status-overlay">
          <span v-if="isSpeaking" class="status-badge speaking">🔊 AI 正在发言</span>
          <span v-else-if="isListening" class="status-badge listening">🎙️ 正在聆听</span>
          <span v-else-if="isStreaming" class="status-badge thinking">🤔 AI 思考中</span>
        </div>

        <div class="subtitle-bar">
          <div class="ai-label" :class="agentClass">{{ agentEmoji }} {{ currentAgent }}</div>
          <div class="subtitle-text" v-html="renderMarkdown(currentAiText)"></div>
          <span v-if="isStreaming" class="blinking-cursor">▍</span>
        </div>
      </div>
    </main>

    <InterviewReportOverlay
      v-if="showReport"
      :display-score="displayScore"
      :score="reportData.score"
      :score-color="scoreColor"
      :metrics="reportMetrics"
      :emotion-distribution="resolvedEmotionDistribution"
      :emotion-summary-text="reportData.emotionSummaryText"
      emotion-tag="视频模式"
      :feedback-html="renderMarkdown(reportData.feedback || '暂无反馈')"
      :recommendations="reportData.recommendations"
      :emotion-label-fn="emotionLabel"
      :emotion-color-fn="emotionColor"
      @history="router.push('/history')"
      @home="router.push('/')"
    >
      <template #radar>
        <div ref="radarRef" class="radar-host"></div>
      </template>
    </InterviewReportOverlay>
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
import { getPreferenceAPI } from '@/api/user'
import { initModels, analyzeFrame, getEmotionSummary, EMOTION_LABELS } from '@/utils/emotionAnalyzer'
import { userKey } from '@/utils/auth'
import InterviewReportOverlay from '@/components/interview/InterviewReportOverlay.vue'
import { buildInterviewRadarOption, gradeToRadarScore } from '@/utils/chartOptions'
import { buildVideoInterviewReportMetrics, parseInterviewFinishPayload } from '@/utils/interviewReport'
import { parseFocusAreas, loadTailoredResumeQuestions, loadInterviewPreferenceFallback } from '@/utils/interviewEntry'

const router = useRouter()
const route = useRoute()
const position = ref(route.query.role || 'Java后端开发')
const difficultyLevel = ref(route.query.difficulty || 'mid')
const focusAreas = computed(() => parseFocusAreas(route.query.focus))
const effectiveFocusAreas = ref([])

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
const resolvedEmotionSource = computed(() => emotionSummary.value || reportData.emotionFromAI || null)
const resolvedEmotionDistribution = computed(() => resolvedEmotionSource.value?.emotionDistribution || null)
const reportMetrics = computed(() => {
  return buildVideoInterviewReportMetrics({
    wpm: reportData.wpm,
    totalRounds: totalRounds.value,
    emotionSummary: resolvedEmotionSource.value,
    emotionLabel
  })
})

const reportData = reactive({
  score: 0, feedback: '', wpm: 0,
  ability: {}, recommendations: [],
  emotionFromAI: null, emotionSummaryText: ''
})

// Emotion data (collected in background, not displayed during interview)
const emotionTimeline = ref([])
const emotionSummary = ref(null)
let emotionInterval = null
let eventSource = null

// Media
let mediaStream = null
let recognition = null
let silenceTimer = null
const SILENCE_MS = 2500

// TTS
let currentUtterance = null
let pendingEndType = null

// WPM tracking
let voiceTurns = []
let turnStart = 0

// ─── Helpers ──────────────────────────────────────────────────────────────────
const renderMarkdown = (text) => text ? marked.parse(text) : ''

const emotionLabel = (key) => EMOTION_LABELS[key] || key

const emotionColor = (key) => {
  const colors = { neutral: '#909399', happy: '#67C23A', sad: '#5B9BD5', angry: '#F56C6C', fearful: '#E6A23C', disgusted: '#C71585', surprised: '#409EFF' }
  return colors[key] || '#909399'
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
  const resumeQuestions = await loadTailoredResumeQuestions({
    isTailored: route.query.isTailored === 'true',
    storageKey: userKey('resume_analysis'),
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '',
    token: localStorage.getItem('token'),
  })

  // 兜底：通过非 Setup 入口时，从偏好加载配置
  const preferenceFallback = await loadInterviewPreferenceFallback({
    query: route.query,
    getPreference: getPreferenceAPI
  })
  if (preferenceFallback) {
    if (preferenceFallback.position) position.value = preferenceFallback.position
    if (preferenceFallback.difficultyLevel) difficultyLevel.value = preferenceFallback.difficultyLevel
    if (preferenceFallback.focusAreas.length) effectiveFocusAreas.value = preferenceFallback.focusAreas
  }

  try {
    const id = await startInterviewAPI({
      position: position.value,
      mode: 'video',
      difficultyLevel: difficultyLevel.value,
      focusAreas: effectiveFocusAreas.value.length ? effectiveFocusAreas.value : focusAreas.value,
      resumeQuestions
    })
    recordId.value = id
    // 4. AI starts first — trigger opening
    triggerAiTurn()
  } catch {
    ElMessage.error('连接失败，请确认后端已启动')
    router.back()
  }
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  stopListening()
  stopSpeaking()
  stopEmotionSampling()
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
  if (radarChartInstance) {
    radarChartInstance.dispose()
    radarChartInstance = null
  }
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
  pendingEndType = null

  // Determine current agent based on round count
  if (totalRounds.value === 0) currentAgent.value = '面试组长'
  else if (totalRounds.value >= 12) currentAgent.value = '面试组长'
  else if (hrOverridden.value || totalRounds.value >= 9) currentAgent.value = 'HR 面试官'
  else currentAgent.value = '技术面试官'

  const token = localStorage.getItem('token') || ''
  const url = `/api/interview/chatStream?recordId=${recordId.value}&message=${encodeURIComponent(message)}&token=${token}`
  if (eventSource) eventSource.close()
  eventSource = new EventSource(url)

  let fullText = ''

  eventSource.onmessage = (e) => {
    let d
    try { d = JSON.parse(e.data) } catch { return }

    if (d.error) {
      ElMessage.error('AI 错误: ' + d.error)
      isStreaming.value = false
      eventSource.close()
      eventSource = null
      startListening()
      return
    }

    if (d.done === 'true' || d.done === true) {
      isStreaming.value = false
      eventSource.close()
      eventSource = null
      totalRounds.value++
      if (pendingEndType) {
        const endType = pendingEndType
        const cleanedText = currentAiText.value || fullText
        pendingEndType = null
        ElMessage[endType === 'abnormal' ? 'warning' : 'info'](
          endType === 'abnormal' ? '检测到面试异常中断，正在生成记录...' : '面试已正常结束，正在生成报告...'
        )
        speakText(cleanedText, () => {
          performEndInterview(endType)
        })
      } else {
        // Speak the AI response
        speakText(fullText)
      }
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

      if (fullText.includes('[AUTO_FINISH]')) {
        fullText = fullText.replace('[AUTO_FINISH]', '').trim()
        pendingEndType = 'normal'
      }

      currentAiText.value = fullText

      // Check for [TERMINATE]
      if (fullText.includes('[TERMINATE]')) {
        currentAiText.value = fullText.replace('[TERMINATE]', '').trim()
        fullText = currentAiText.value
        pendingEndType = 'abnormal'
      }
    }
  }

  eventSource.onerror = () => {
    isStreaming.value = false
    eventSource.close()
    eventSource = null
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

const performEndInterview = async (endType = 'manual') => {
  stopListening()
  stopSpeaking()
  stopEmotionSampling()
  isFinishing.value = true

  // Calculate WPM
  const wpm = calcAvgWpm()
  // Generate emotion summary
  emotionSummary.value = getEmotionSummary(emotionTimeline.value)

  const loadingMsg = ElMessage({
    message: endType === 'abnormal'
      ? '🚨 检测到异常中断，正在生成报告...'
      : endType === 'normal'
        ? '✅ 面试已完成，正在生成报告...'
        : '🤖 正在深度分析，请稍候...',
    type: endType === 'abnormal' ? 'warning' : 'info',
    duration: 0
  })

  try {
    const res = await finishInterviewAPI({
      recordId: recordId.value,
      wpm,
      emotionJson: emotionSummary.value ? JSON.stringify(emotionSummary.value) : null
    })
    loadingMsg.close()

    if (res) {
      const parsedPayload = parseInterviewFinishPayload(res)
      reportData.score = res.score || 0
      reportData.feedback = res.feedback || ''
      reportData.wpm = wpm
      reportData.ability = parsedPayload.ability
      reportData.recommendations = parsedPayload.recommendations
      reportData.emotionFromAI = parsedPayload.emotion
      reportData.emotionSummaryText = parsedPayload.emotion?.summary || ''

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

  const scores = [
    gradeToRadarScore(rec.ability.techDepth),
    gradeToRadarScore(rec.ability.breadth),
    gradeToRadarScore(rec.ability.logic),
    gradeToRadarScore(rec.ability.expression),
    gradeToRadarScore(rec.ability.adaptability),
    gradeToRadarScore(rec.ability.problemSolving)
  ]
  radarChartInstance.setOption(buildInterviewRadarOption(echarts, scores))
}
</script>

<style scoped>
.video-interview-shell {
  --surface: rgba(255, 255, 255, 0.78);
  --surface-strong: rgba(255, 255, 255, 0.94);
  --ink: #171a1f;
  --muted: rgba(230, 235, 242, 0.72);
  --accent: #3a388b;
  --accent-soft: rgba(58, 56, 139, 0.14);
  min-height: 100vh;
  min-height: 100dvh;
  overflow: hidden;
  color: #f8fafc;
  background:
    radial-gradient(circle at top, rgba(58, 56, 139, 0.28), transparent 36%),
    radial-gradient(circle at 12% 20%, rgba(14, 165, 233, 0.10), transparent 24%),
    linear-gradient(180deg, #121521 0%, #0d1018 100%);
}

.vi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 72px;
  padding: 16px 28px;
  background: rgba(16, 18, 26, 0.72);
  backdrop-filter: blur(18px);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.04), 0 12px 30px rgba(0, 0, 0, 0.20);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title {
  font-size: 18px;
  line-height: 1.2;
  font-weight: 650;
  letter-spacing: 0;
}

.back-button {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08);
}

.finish-button {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08);
}

.mode-tag {
  margin-left: 4px;
}

.vi-main {
  flex: 1;
  display: flex;
  align-items: stretch;
  justify-content: center;
  padding: 18px 18px 20px;
}

.camera-stage {
  position: relative;
  width: min(100%, 1400px);
  height: calc(100vh - 122px);
  height: calc(100dvh - 122px);
  overflow: hidden;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.03);
  box-shadow:
    0 24px 70px rgba(0, 0, 0, 0.32),
    0 0 0 1px rgba(255, 255, 255, 0.04);
}

.camera-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.camera-sheen {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 28%),
    radial-gradient(circle at top right, rgba(58, 56, 139, 0.14), transparent 34%);
  pointer-events: none;
}

.status-overlay {
  position: absolute;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 8px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
  backdrop-filter: blur(16px);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08);
  animation: fadeInDown 0.24s ease;
}

.status-badge.speaking { background: rgba(21, 185, 129, 0.62); }
.status-badge.listening { background: rgba(79, 170, 238, 0.58); }
.status-badge.thinking { background: rgba(230, 162, 60, 0.58); }

.subtitle-bar {
  position: absolute;
  inset: auto 0 0 0;
  padding: 18px 22px 20px;
  background: linear-gradient(180deg, transparent, rgba(8, 10, 15, 0.92));
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 78px;
  z-index: 5;
}

.ai-label {
  flex-shrink: 0;
  padding: 8px 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.ai-label.coordinator { background: rgba(58, 56, 139, 0.78); }
.ai-label.technical { background: rgba(220, 103, 103, 0.72); }
.ai-label.hr { background: rgba(209, 123, 76, 0.74); }

.subtitle-text {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  line-height: 1.72;
  color: #eef2f7;
  max-height: 120px;
  overflow-y: auto;
}

.subtitle-text :deep(p) { margin: 0; }

.blinking-cursor {
  animation: blink 0.8s infinite;
  color: #b7b0ff;
}

@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
@keyframes fadeInDown {
  from { opacity: 0; transform: translateX(-50%) translateY(-10px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

:deep(.el-input__wrapper) {
  background: var(--surface-strong);
  box-shadow: 0 0 0 1px rgba(23, 26, 31, 0.06) inset;
  border-radius: 16px;
  padding: 12px 16px;
}

:deep(.el-textarea__inner) {
  background: transparent;
  color: var(--ink);
  line-height: 1.7;
  min-height: 92px !important;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(58, 56, 139, 0.24) inset,
    0 0 0 3px rgba(58, 56, 139, 0.08);
}

@media (max-width: 1100px) {
  .camera-stage {
    height: calc(100vh - 110px);
    height: calc(100dvh - 110px);
  }
}

@media (max-width: 860px) {
  .vi-header,
  .vi-main {
    padding-left: 16px;
    padding-right: 16px;
  }

  .vi-header {
    gap: 12px;
    align-items: flex-start;
    flex-direction: column;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .camera-stage {
    height: calc(100vh - 144px);
    height: calc(100dvh - 144px);
    border-radius: 18px;
  }

  .subtitle-bar {
    flex-direction: column;
    gap: 10px;
  }

  .subtitle-text {
    max-height: 100px;
  }

  .kpi-item {
    min-width: 0;
  }
}
</style>
