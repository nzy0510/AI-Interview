<template>
  <div class="interview-shell">
    <el-container class="interview-frame">
      <el-header class="interview-header">
        <div class="header-left">
          <el-button class="back-button" :icon="ArrowLeft" circle @click="router.back()" />
          <span class="title">{{ position }} · 模拟面试</span>
        </div>
        <div class="header-right">
          <el-switch
            v-model="autoSend"
            active-text="静音自动发送"
            inactive-text="手动发送"
            style="margin-right: 16px"
            :disabled="showReport"
          />
          <el-button type="danger" size="small" class="finish-button" @click="endInterview" :loading="isFinishing">
            结束并生成报告
          </el-button>
        </div>
      </el-header>

      <el-main class="chat-main" ref="chatMainRef">
        <div class="chat-scene">
          <div
            v-for="(msg, i) in messageList"
            :key="i"
            :class="['message-row', msg.role]"
          >
            <el-avatar v-if="msg.role === 'ai'" class="avatar ai-avatar">AI</el-avatar>
            <div class="message-content">
              <div class="text" v-html="renderMarkdown(msg.content)"></div>
              <span v-if="msg.streaming" class="blinking-cursor">▍</span>
            </div>
            <el-avatar v-if="msg.role === 'user'" class="avatar user-avatar" :icon="UserFilled" />
          </div>
        </div>
      </el-main>

      <el-footer class="chat-footer">
        <div class="voice-row">
          <el-button
            :type="isRecording ? 'danger' : 'success'"
            circle
            size="large"
            @click="toggleRecording"
            :disabled="isStreaming || showReport || !isSpeechSupported"
            class="mic-btn"
          >
            <el-icon size="20"><Microphone /></el-icon>
          </el-button>

          <div class="visualizer-wrap" :class="{ active: isRecording }">
            <canvas ref="canvasRef" width="200" height="46" class="wave-canvas" />
            <span class="rec-dot">● 录音中</span>
          </div>

          <div v-if="!isSpeechSupported" class="no-speech-tip">
            当前浏览器不支持语音识别，请使用 Chrome 或 Edge
          </div>
        </div>

        <div class="input-row">
          <el-input
            v-model="inputMsg"
            class="answer-input"
            type="textarea"
            :rows="3"
            :placeholder="isRecording ? '录音中，语音内容实时填入...' : '输入回答（Enter 发送），或点击麦克风语音作答'"
            resize="none"
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="isStreaming || showReport"
          />
          <el-button
            type="primary"
            class="send-btn"
            @click="sendMessage"
            :disabled="!inputMsg.trim() || isStreaming || showReport"
            :loading="isStreaming"
          >发送</el-button>
        </div>
      </el-footer>

      <InterviewReportOverlay
        v-if="showReport"
        :display-score="displayScore"
        :score="reportData.score"
        :score-color="scoreColor"
        :metrics="reportMetrics"
        :emotion-distribution="reportData.emotion?.emotionDistribution || null"
        :emotion-summary-text="reportData.emotion?.summary || ''"
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

    </el-container>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Microphone, ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'
import { getPreferenceAPI } from '@/api/user'
import * as echarts from 'echarts'
import { marked } from 'marked'
import { userKey } from '@/utils/auth'
import InterviewReportOverlay from '@/components/interview/InterviewReportOverlay.vue'
import { buildInterviewRadarOption, gradeToRadarScore } from '@/utils/chartOptions'
import { buildTextInterviewReportMetrics, parseInterviewFinishPayload } from '@/utils/interviewReport'
import { parseFocusAreas, loadTailoredResumeQuestions, loadInterviewPreferenceFallback } from '@/utils/interviewEntry'

const route = useRoute()
const router = useRouter()

const position = ref(route.query.role || '未指定岗位')
const difficultyLevel = ref(route.query.difficulty || 'mid')
const focusAreas = computed(() => parseFocusAreas(route.query.focus))
const effectiveFocusAreas = ref([])
const recordId = ref(null)
const messageList = ref([])
const inputMsg = ref('')
const isStreaming = ref(false)
const chatMainRef = ref(null)
const isFinishing = ref(false)
const showReport = ref(false)
const autoSend = ref(true) // auto-send on silence toggle
const displayScore = ref(0)
let radarChartInstance = null

const scoreColor = computed(() => {
  if (displayScore.value >= 90) return '#10b981'
  if (displayScore.value >= 75) return '#f59e0b'
  if (displayScore.value >= 60) return '#0ea5e9'
  return '#ef4444'
})

// ─── Report data ──────────────────────────────────────────────────────────────
const reportData = reactive({
  score: 0,
  feedback: '',
  ability: {},
  recommendations: [],
  wpm: 0,
  voiceRounds: 0,
  emotion: null
})
const totalUserRounds = computed(() => messageList.value.filter(m => m.role === 'user').length)
const reportMetrics = computed(() => {
  return buildTextInterviewReportMetrics({
    wpm: reportData.wpm,
    voiceRounds: reportData.voiceRounds,
    totalUserRounds: totalUserRounds.value,
    emotion: reportData.emotion,
    emotionLabel
  })
})

// ─── Emotion/Sentiment labels ────────────────────────────────────────────────
const EMOTION_LABELS = { neutral: '平静', happy: '积极', sad: '低落', angry: '紧张', fearful: '焦虑', disgusted: '不适', surprised: '惊讶' }
const emotionLabel = (key) => EMOTION_LABELS[key] || key
const emotionColor = (key) => ({ neutral: '#909399', happy: '#67C23A', sad: '#5B9BD5', angry: '#F56C6C', fearful: '#E6A23C', disgusted: '#C71585', surprised: '#409EFF' }[key] || '#909399')

// ─── Voice & Visualizer ───────────────────────────────────────────────────────
const isRecording = ref(false)
const isSpeechSupported = ref(false)
const canvasRef = ref(null)
const radarRef = ref(null)

let audioCtx = null, analyserNode = null, dataArray = null, micStream = null
let animFrameId = null, recognition = null

// Auto-send debounce
let silenceTimer = null
const SILENCE_MS = 2500

// WPM tracking
let voiceTurns = []
let turnStart = 0
let voiceRoundCount = 0

// --- Markdown Rendering ---
const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text)
}

// ─── Lifecycle ───────────────────────────────────────────────────────────────
onMounted(async () => {
  isSpeechSupported.value = !!(window.SpeechRecognition || window.webkitSpeechRecognition)
  
  const resumeQuestions = await loadTailoredResumeQuestions({
    isTailored: route.query.isTailored === 'true',
    storageKey: userKey('resume_analysis'),
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '',
    token: localStorage.getItem('token'),
  })

  // 兜底：通过非 Setup 入口（如 Dashboard 直接进入、书签）时，从偏好加载配置
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
      mode: 'text',
      difficultyLevel: difficultyLevel.value,
      focusAreas: effectiveFocusAreas.value.length ? effectiveFocusAreas.value : focusAreas.value,
      resumeQuestions
    })
    recordId.value = id
    triggerAiStart()
  } catch {
    ElMessage.error('连接失败，请确认后端已启动')
    router.back()
  }
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  cleanupAudio()
  if (silenceTimer) clearTimeout(silenceTimer)
  if (radarChartInstance) {
    radarChartInstance.dispose()
    radarChartInstance = null
  }
})

// ─── Voice ────────────────────────────────────────────────────────────────────
const toggleRecording = async () => {
  isRecording.value ? stopRecording() : await startRecording()
}

const startRecording = async () => {
  try {
    micStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch {
    ElMessage.error('请授权麦克风权限后重试')
    return
  }

  // Audio analyser for waveform
  audioCtx = new (window.AudioContext || window.webkitAudioContext)()
  analyserNode = audioCtx.createAnalyser()
  analyserNode.fftSize = 512
  audioCtx.createMediaStreamSource(micStream).connect(analyserNode)
  dataArray = new Uint8Array(analyserNode.frequencyBinCount)
  drawWave()

  // Speech recognition
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  recognition = new SR()
  recognition.continuous = true
  recognition.interimResults = true
  recognition.lang = 'zh-CN'
  recognition.maxAlternatives = 1

  // We store the confirmed text separately so interim doesn't cause duplicates
  let confirmedText = ''

  recognition.onresult = (event) => {
    let interim = ''
    let newFinal = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      if (event.results[i].isFinal) {
        newFinal += event.results[i][0].transcript
      } else {
        interim += event.results[i][0].transcript
      }
    }
    if (newFinal) confirmedText += newFinal
    // Show confirmed + current interim in input box (real-time feel)
    inputMsg.value = confirmedText + interim

    // Reset auto-send silence timer on every new spoken word
    if (autoSend.value) {
      if (silenceTimer) clearTimeout(silenceTimer)
      silenceTimer = setTimeout(() => {
        if (inputMsg.value.trim()) {
          // 先禁用 onend 自动重启，防止 recognition 在 stop 后又被重新启动
          if (recognition) recognition.onend = null
          stopRecording()
          sendMessage()
        }
      }, SILENCE_MS)
    }
  }

  recognition.onerror = (e) => {
    if (e.error !== 'no-speech') ElMessage.warning(`识别出错: ${e.error}`)
    stopRecording()
  }

  recognition.onend = () => {
    if (isRecording.value) recognition.start() // auto-restart on silence pause
  }

  recognition.start()
  isRecording.value = true
  turnStart = Date.now()
}

const stopRecording = () => {
  if (!isRecording.value) return
  isRecording.value = false
  if (silenceTimer) { clearTimeout(silenceTimer); silenceTimer = null }
  if (recognition) { recognition.onend = null; recognition.stop(); recognition = null }

  // WPM tracking
  const elapsed = (Date.now() - turnStart) / 1000
  const chars = inputMsg.value.trim().length
  if (elapsed > 1 && chars > 0) {
    voiceTurns.push({ chars, durationSec: elapsed })
    voiceRoundCount++
  }
  cleanupAudio()
}

const cleanupAudio = () => {
  if (animFrameId) { cancelAnimationFrame(animFrameId); animFrameId = null }
  if (micStream) { micStream.getTracks().forEach(t => t.stop()); micStream = null }
  if (audioCtx) { audioCtx.close(); audioCtx = null }
  analyserNode = null
  if (canvasRef.value) {
    canvasRef.value.getContext('2d').clearRect(0, 0, 200, 46)
  }
}

// ─── Waveform Canvas (Siri Style) ───────────────────────────────────────────
const drawWave = () => {
  if (!analyserNode || !canvasRef.value) return
  animFrameId = requestAnimationFrame(drawWave)
  analyserNode.getByteFrequencyData(dataArray)

  const c = canvasRef.value, ctx = c.getContext('2d'), W = c.width, H = c.height
  ctx.clearRect(0, 0, W, H)

  const time = Date.now() / 1000
  const layers = [
    { color: 'rgba(16, 185, 129, 0.4)', speed: 2, amp: 0.6 }, /* Emerald */
    { color: 'rgba(245, 158, 11, 0.3)', speed: 1.5, amp: 0.4 }, /* Amber */
    { color: 'rgba(6, 182, 212, 0.2)', speed: 2.5, amp: 0.5 } /* Cyan */
  ]

  // Calculate average volume for pulse
  let sum = 0
  for(let i=0; i<32; i++) sum += dataArray[i]
  const vol = sum / 32 / 255 // 0-1

  layers.forEach(layer => {
    ctx.beginPath()
    ctx.lineWidth = 2
    ctx.strokeStyle = layer.color
    
    for (let x = 0; x <= W; x += 5) {
      const normX = x / W
      const sinBase = Math.sin(normX * Math.PI * 2 + time * layer.speed)
      const noise = (Math.random() - 0.5) * 0.1 // subtle jitter
      const y = H/2 + (sinBase + noise) * (H/3) * layer.amp * (vol + 0.2)
      
      if (x === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.stroke()
  })
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

// ─── WPM Calculation ──────────────────────────────────────────────────────────
const calcAvgWpm = () => {
  if (!voiceTurns.length) return 0
  const totalChars = voiceTurns.reduce((s, t) => s + t.chars, 0)
  const totalSec = voiceTurns.reduce((s, t) => s + t.durationSec, 0)
  return Math.round((totalChars / 2.5) / (totalSec / 60))
}

// ─── Chat ─────────────────────────────────────────────────────────────────────
const scrollToBottom = async () => {
  await nextTick()
  if (chatMainRef.value?.$el) chatMainRef.value.$el.scrollTop = chatMainRef.value.$el.scrollHeight
}

let eventSource = null
let pendingEndType = null

const triggerAiStart = () => streamAiResponse('你好，我已准备好，请开始面试。')

const sendMessage = () => {
  const text = inputMsg.value.trim()
  if (!text || isStreaming.value) return
  if (isRecording.value) stopRecording()

  messageList.value.push({ role: 'user', content: text, streaming: false })
  inputMsg.value = ''
  scrollToBottom()
  streamAiResponse(text)
}

const streamAiResponse = (msg) => {
  isStreaming.value = true
  pendingEndType = null
  // Use reactive push via an object reference we keep
  const aiMsg = reactive({ role: 'ai', content: '', streaming: true })
  messageList.value.push(aiMsg)

  const token = localStorage.getItem('token') || ''
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const url = `${baseUrl}/api/interview/chatStream?recordId=${recordId.value}&message=${encodeURIComponent(msg)}&token=${token}`
  if (eventSource) eventSource.close()
  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    const rawData = event.data
    if (!rawData || rawData.trim() === '') return // skip heartbeat empty lines

    let d
    try {
      d = JSON.parse(rawData)
    } catch (err) {
      console.warn('[SSE] JSON parse failed:', rawData, err)
      return
    }

    if (d.error) {
      ElMessage.error('AI 错误: ' + d.error)
      aiMsg.streaming = false
      isStreaming.value = false
      eventSource.close()
      return
    }

    if (d.done === 'true' || d.done === true) {
      aiMsg.streaming = false
      isStreaming.value = false
      eventSource.close()
      if (pendingEndType) {
        const endType = pendingEndType
        pendingEndType = null
        const notice = endType === 'abnormal'
          ? '检测到面试异常中断，正在生成记录...'
          : '面试已正常结束，正在生成报告...'
        ElMessage[endType === 'abnormal' ? 'warning' : 'info'](notice)
        setTimeout(() => {
          performEndInterview(endType)
        }, 800)
      }
      return
    }

    if (d.content !== undefined && d.content !== null) {
      aiMsg.content += d.content
      
      if (aiMsg.content.includes('[SWITCH_TO_HR]')) {
        aiMsg.content = aiMsg.content.replace('[SWITCH_TO_HR]', '').trim()
      }

      if (aiMsg.content.includes('[AUTO_FINISH]')) {
        aiMsg.content = aiMsg.content.replace('[AUTO_FINISH]', '').trim()
        pendingEndType = 'normal'
      }

      // Check for termination marker
      if (aiMsg.content.includes('[TERMINATE]')) {
        aiMsg.content = aiMsg.content.replace('[TERMINATE]', '').trim()
        pendingEndType = 'abnormal'
      }
      
      scrollToBottom()
    }
  }
  eventSource.onerror = () => { aiMsg.streaming = false; isStreaming.value = false; eventSource.close() }
}

// ─── End Interview ────────────────────────────────────────────────────────────
const endInterview = async () => {
  if (totalUserRounds.value < 1) { ElMessage.warning('请至少完成一轮对话'); return }
  try {
    await ElMessageBox.confirm('确定结束面试？AI 将综合分析并生成详细报告（约30秒）。', '结束面试', {
      confirmButtonText: '确认结束', cancelButtonText: '继续面试', type: 'warning'
    })
  } catch { return }

  performEndInterview()
}

const performEndInterview = async (endType = 'manual') => {
  if (isRecording.value) stopRecording()
  isFinishing.value = true
  const wpm = calcAvgWpm()
  const isAutoAbnormal = endType === 'abnormal'
  const isAutoNormal = endType === 'normal'
 
  const loadingMsg = ElMessage({ 
    message: isAutoAbnormal
      ? '🚨 检测到异常中断，正在生成报告...'
      : isAutoNormal
        ? '✅ 面试已完成，正在生成报告...'
        : '🤖 正在深度分析，请稍候...',
    type: isAutoAbnormal ? 'warning' : 'info', 
    duration: 0 
  })
  
  try {
    const res = await finishInterviewAPI({ recordId: recordId.value, wpm, voiceRounds: voiceRoundCount })
    loadingMsg.close()

    if (res) {
      const parsedPayload = parseInterviewFinishPayload(res)
      reportData.score = res.score || 0
      reportData.feedback = res.feedback || ''
      reportData.wpm = wpm
      reportData.voiceRounds = voiceRoundCount
      reportData.ability = parsedPayload.ability
      reportData.recommendations = parsedPayload.recommendations
      reportData.emotion = parsedPayload.emotion

      showReport.value = true
      // Trigger animations
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
</script>

<style scoped>
.interview-shell {
  --bg: #f6f8fb;
  --surface: rgba(255, 255, 255, 0.84);
  --surface-strong: rgba(255, 255, 255, 0.96);
  --ink: #171a1f;
  --muted: #5d6673;
  --muted-strong: #35404d;
  --accent: #3a388b;
  --accent-soft: rgba(58, 56, 139, 0.13);
  min-height: 100vh;
  color: var(--ink);
  background:
    radial-gradient(circle at top left, rgba(58, 56, 139, 0.10), transparent 32%),
    radial-gradient(circle at top right, rgba(27, 129, 166, 0.08), transparent 26%),
    linear-gradient(180deg, #f9fbfd 0%, #eef2f6 100%);
}

.interview-frame {
  min-height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
}

.interview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72px;
  padding: 16px 28px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(18px);
  box-shadow: 0 1px 0 rgba(23, 26, 31, 0.04), 0 10px 30px rgba(23, 26, 31, 0.04);
  flex-shrink: 0;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.title {
  font-size: 18px;
  line-height: 1.2;
  font-weight: 650;
  letter-spacing: 0;
  color: var(--ink);
}

.back-button {
  box-shadow: 0 0 0 1px rgba(23, 26, 31, 0.08);
}

.finish-button {
  box-shadow: 0 0 0 1px rgba(58, 56, 139, 0.22);
}

.chat-main {
  flex: 1;
  overflow-y: auto;
  padding: 28px 28px 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.28), rgba(255, 255, 255, 0.08)),
    linear-gradient(180deg, #f7f9fb 0%, #edf2f6 100%);
}

.chat-scene {
  max-width: 1120px;
  margin: 0 auto;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 20px;
  animation: msgLift 0.35s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.message-row.user {
  flex-direction: row-reverse;
}

@keyframes msgLift {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.avatar {
  flex-shrink: 0;
  box-shadow: 0 10px 24px rgba(23, 26, 31, 0.10);
}

.ai-avatar {
  background: linear-gradient(135deg, #3a388b, #6260b4);
  color: #fff;
}

.user-avatar {
  background: linear-gradient(135deg, #d17b4c, #b95f33);
}

.message-content {
  max-width: min(72%, 760px);
  padding: 16px 18px;
  font-size: 15px;
  line-height: 1.75;
  color: var(--ink);
  background: var(--surface);
  backdrop-filter: blur(16px);
  box-shadow:
    0 10px 30px rgba(23, 26, 31, 0.05),
    0 0 0 1px rgba(23, 26, 31, 0.04);
}

.message-row.ai .message-content {
  border-radius: 18px 18px 18px 4px;
}

.message-row.user .message-content {
  border-radius: 18px 18px 4px 18px;
  background: rgba(58, 56, 139, 0.08);
  box-shadow:
    0 10px 30px rgba(58, 56, 139, 0.05),
    0 0 0 1px rgba(58, 56, 139, 0.06);
}

.text {
  margin: 0;
  word-break: break-word;
}

.text :deep(p) {
  margin: 0 0 8px 0;
}

.text :deep(p:last-child) {
  margin-bottom: 0;
}

.text :deep(strong) {
  color: var(--accent);
}

.text :deep(ul),
.text :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.text :deep(code) {
  background: rgba(23, 26, 31, 0.06);
  padding: 2px 6px;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 0.92em;
}

.blinking-cursor {
  animation: blink 0.8s step-end infinite;
  color: var(--accent);
  font-size: 18px;
  margin-left: 2px;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-footer {
  padding: 18px 28px 26px;
  background: rgba(247, 249, 251, 0.88);
  backdrop-filter: blur(18px);
  box-shadow: 0 -1px 0 rgba(23, 26, 31, 0.04);
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: auto !important;
  flex-shrink: 0;
}

.voice-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 54px;
}

.mic-btn {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  box-shadow:
    0 10px 24px rgba(23, 26, 31, 0.10),
    0 0 0 1px rgba(23, 26, 31, 0.06);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.mic-btn:hover {
  transform: translateY(-1px);
}

.visualizer-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  opacity: 0;
  transition: opacity 0.25s ease;
  pointer-events: none;
}

.visualizer-wrap.active {
  opacity: 1;
  pointer-events: auto;
}

.wave-canvas {
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(58, 56, 139, 0.08), rgba(58, 56, 139, 0.03));
  box-shadow: 0 0 0 1px rgba(58, 56, 139, 0.10) inset;
}

.rec-dot {
  font-size: 12px;
  color: #a53d3d;
  animation: blink 1.2s step-end infinite;
  white-space: nowrap;
}

.no-speech-tip {
  font-size: 12px;
  color: #a26a1b;
}

.input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: end;
}

.answer-input {
  min-width: 0;
}

.send-btn {
  height: 82px;
  width: 96px;
  font-size: 15px;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(58, 56, 139, 0.14);
}

:deep(.el-switch__label),
:deep(.el-switch__label.is-active) {
  color: var(--muted-strong);
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

@media (max-width: 860px) {
  .interview-header,
  .chat-footer,
  .chat-main {
    padding-left: 16px;
    padding-right: 16px;
  }

  .interview-header {
    gap: 12px;
    align-items: flex-start;
    flex-direction: column;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .input-row {
    grid-template-columns: 1fr;
  }

  .send-btn {
    width: 100%;
    height: 46px;
  }

  .message-content {
    max-width: 86%;
  }
}
</style>
