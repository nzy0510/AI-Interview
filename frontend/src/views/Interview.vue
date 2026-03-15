<template>
  <div class="interview-container">
    <el-container>
      <!-- Header -->
      <el-header class="interview-header">
        <div class="header-left">
          <el-button :icon="ArrowLeft" circle @click="router.back()" />
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
          <el-button type="danger" size="small" @click="endInterview" :loading="isFinishing">
            结束并生成报告
          </el-button>
        </div>
      </el-header>

      <!-- Chat Area -->
      <el-main class="chat-main" ref="chatMainRef">
        <div
          v-for="(msg, i) in messageList"
          :key="i"
          :class="['message-row', msg.role]"
        >
          <el-avatar v-if="msg.role === 'ai'" class="avatar ai-avatar">AI</el-avatar>
          <div class="message-content">
            <pre class="text">{{ msg.content }}</pre>
            <span v-if="msg.streaming" class="blinking-cursor">▍</span>
          </div>
          <el-avatar v-if="msg.role === 'user'" class="avatar user-avatar" :icon="UserFilled" />
        </div>
      </el-main>

      <!-- Footer -->
      <el-footer class="chat-footer">
        <!-- Voice controls row -->
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

          <!-- Waveform canvas — always in DOM, CSS-toggled -->
          <div class="visualizer-wrap" :class="{ active: isRecording }">
            <canvas ref="canvasRef" width="200" height="46" class="wave-canvas" />
            <span class="rec-dot">● 录音中</span>
          </div>

          <div v-if="!isSpeechSupported" class="no-speech-tip">
            当前浏览器不支持语音识别，请使用 Chrome 或 Edge
          </div>
        </div>

        <!-- Text input row -->
        <div class="input-row">
          <el-input
            v-model="inputMsg"
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

      <!-- =================== Report Overlay =================== -->
      <div v-if="showReport" class="report-overlay">
        <el-card class="report-card">
          <template #header>
            <div class="rpt-header">
              <span class="rpt-title">📋 面试评估报告</span>
              <el-tag type="success" effect="dark" size="large" round>
                综合得分 {{ reportData.score }} / 100
              </el-tag>
            </div>
          </template>

          <div class="report-body">
            <!-- Tab navigation for sections -->
            <el-tabs v-model="activeTab" class="rpt-tabs">

              <!-- Tab 1: Ability Radar Chart -->
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

              <!-- Tab 2: Feedback + Behavioral -->
              <el-tab-pane label="📝 面试反馈" name="feedback">
                <div class="behavioral-row">
                  <div class="beh-card">
                    <div class="beh-val">{{ reportData.wpm > 0 ? reportData.wpm : '—' }}</div>
                    <div class="beh-lbl">平均语速 (WPM)</div>
                    <el-tag :type="getWpmTagType(reportData.wpm)" size="small">{{ getWpmText(reportData.wpm) }}</el-tag>
                  </div>
                  <div class="beh-card">
                    <div class="beh-val">{{ reportData.voiceRounds }}</div>
                    <div class="beh-lbl">语音作答轮次</div>
                    <el-tag type="info" size="small">共 {{ totalUserRounds }} 轮</el-tag>
                  </div>
                </div>
                <el-divider content-position="left">AI 综合点评</el-divider>
                <div class="feedback-box">
                  <pre class="feedback-text">{{ reportData.feedback }}</pre>
                </div>
              </el-tab-pane>

              <!-- Tab 3: Recommendations -->
              <el-tab-pane label="🚀 提升计划" name="plan">
                <el-timeline v-if="reportData.recommendations.length">
                  <el-timeline-item
                    v-for="(rec, i) in reportData.recommendations"
                    :key="i"
                    :timestamp="rec.period"
                    placement="top"
                    :type="['primary', 'success', 'warning'][i % 3]"
                  >
                    <el-card class="rec-card" shadow="never">
                      <div class="rec-action">{{ rec.action }}</div>
                      <div class="rec-detail">{{ rec.detail }}</div>
                    </el-card>
                  </el-timeline-item>
                </el-timeline>
                <el-empty v-else description="暂无建议数据" />
              </el-tab-pane>

            </el-tabs>
          </div>

          <div class="rpt-footer">
            <el-button @click="router.push('/history')" plain>查看历史</el-button>
            <el-button type="primary" @click="router.push('/')">返回大厅</el-button>
          </div>
        </el-card>
      </div>

    </el-container>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Microphone, ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'

const route = useRoute()
const router = useRouter()

const position = ref(route.query.role || '未指定岗位')
const recordId = ref(null)
const messageList = ref([])
const inputMsg = ref('')
const isStreaming = ref(false)
const chatMainRef = ref(null)
const isFinishing = ref(false)
const showReport = ref(false)
const activeTab = ref('radar')
const autoSend = ref(true) // auto-send on silence toggle

// ─── Report data ──────────────────────────────────────────────────────────────
const reportData = reactive({
  score: 0,
  feedback: '',
  ability: {},
  recommendations: [],
  wpm: 0,
  voiceRounds: 0
})
const totalUserRounds = computed(() => messageList.value.filter(m => m.role === 'user').length)

// ─── Ability dimension definitions ───────────────────────────────────────────
const abilityDimensions = {
  techDepth:      { label: '技术深度', color: '#409eff' },
  breadth:        { label: '知识广度', color: '#67c23a' },
  problemSolving: { label: '解题思路', color: '#e6a23c' },
  expression:     { label: '表达清晰', color: '#f56c6c' },
  logic:          { label: '逻辑思维', color: '#909399' },
  adaptability:   { label: '应变能力', color: '#c71585' }
}

const gradeScore = { S: 1.0, A: 0.8, B: 0.6, C: 0.4, D: 0.2 }
const getGradeType = (g) => ({ S: 'danger', A: 'success', B: 'primary', C: 'warning' }[g] || 'info')
const getWpmText = (w) => w === 0 ? '未使用语音' : w < 80 ? '节奏较慢' : w > 220 ? '语速偏快' : '语速适中'
const getWpmTagType = (w) => w === 0 ? 'info' : (w < 80 || w > 220) ? 'warning' : 'success'

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

// ─── Lifecycle ───────────────────────────────────────────────────────────────
onMounted(async () => {
  isSpeechSupported.value = !!(window.SpeechRecognition || window.webkitSpeechRecognition)
  try {
    const id = await startInterviewAPI({ position: position.value })
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
})

// Draw radar after tab switches to radar
watch(activeTab, (tab) => {
  if (tab === 'radar') nextTick(() => drawRadarChart())
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

// ─── Waveform Canvas ──────────────────────────────────────────────────────────
const drawWave = () => {
  if (!analyserNode || !canvasRef.value) return
  animFrameId = requestAnimationFrame(drawWave)
  analyserNode.getByteFrequencyData(dataArray)

  const c = canvasRef.value, ctx = c.getContext('2d'), W = c.width, H = c.height
  ctx.clearRect(0, 0, W, H)
  const bars = 50, bw = W / bars - 1, step = Math.floor(dataArray.length / bars)
  for (let i = 0; i < bars; i++) {
    const v = dataArray[i * step] / 255
    const bh = Math.max(3, v * H)
    const r = Math.round(v * 220), g = Math.round((1 - v) * 194 + 58)
    ctx.fillStyle = `rgb(${r},${g},58)`
    ctx.beginPath()
    if (ctx.roundRect) ctx.roundRect(i * (bw + 1), H - bh, bw, bh, 2)
    else ctx.rect(i * (bw + 1), H - bh, bw, bh)
    ctx.fill()
  }
}

// ─── Radar Chart ─────────────────────────────────────────────────────────────
const drawRadarChart = () => {
  const canvas = radarRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const W = canvas.width, H = canvas.height
  const cx = W / 2, cy = H / 2, maxR = Math.min(cx, cy) - 50
  const keys = Object.keys(abilityDimensions)
  const labels = Object.values(abilityDimensions).map(d => d.label)
  const colors = Object.values(abilityDimensions).map(d => d.color)
  const n = keys.length
  const angle = (Math.PI * 2) / n

  ctx.clearRect(0, 0, W, H)

  // Draw background grid rings (S/A/B/C/D)
  const grades = ['D', 'C', 'B', 'A', 'S']
  grades.forEach((g, gi) => {
    const r = maxR * ((gi + 1) / grades.length)
    ctx.beginPath()
    for (let i = 0; i < n; i++) {
      const a = angle * i - Math.PI / 2
      const x = cx + r * Math.cos(a)
      const y = cy + r * Math.sin(a)
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.strokeStyle = gi === grades.length - 1 ? 'rgba(64,158,255,0.3)' : 'rgba(0,0,0,0.08)'
    ctx.lineWidth = gi === grades.length - 1 ? 2 : 1
    ctx.stroke()
    // Grade label near the first axis
    const labelA = -Math.PI / 2
    ctx.fillStyle = '#909399'
    ctx.font = '11px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(g, cx + r * Math.cos(labelA) - 14, cy + r * Math.sin(labelA) + 4)
  })

  // Draw axes (lines from center to each vertex)
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.lineTo(cx + maxR * Math.cos(a), cy + maxR * Math.sin(a))
    ctx.strokeStyle = 'rgba(0,0,0,0.1)'
    ctx.lineWidth = 1
    ctx.stroke()
  }

  // Draw ability polygon with animation support via requestAnimationFrame
  const scores = keys.map(k => gradeScore[reportData.ability[k]] || 0.2)
  ctx.beginPath()
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    const r = maxR * scores[i]
    i === 0 ? ctx.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
             : ctx.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
  }
  ctx.closePath()
  ctx.fillStyle = 'rgba(64,158,255,0.18)'
  ctx.fill()
  ctx.strokeStyle = '#409eff'
  ctx.lineWidth = 2.5
  ctx.stroke()

  // Draw vertex dots and labels
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    const r = maxR * scores[i]
    // Dot on polygon
    ctx.beginPath()
    ctx.arc(cx + r * Math.cos(a), cy + r * Math.sin(a), 5, 0, Math.PI * 2)
    ctx.fillStyle = colors[i]
    ctx.fill()
    // Label at outer ring
    const la = angle * i - Math.PI / 2
    const lr = maxR + 36
    ctx.fillStyle = '#303133'
    ctx.font = 'bold 13px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(labels[i], cx + lr * Math.cos(la), cy + lr * Math.sin(la))
  }
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
  // Use reactive push via an object reference we keep
  const aiMsg = reactive({ role: 'ai', content: '', streaming: true })
  messageList.value.push(aiMsg)

  const url = `http://localhost:8080/api/interview/chatStream?recordId=${recordId.value}&message=${encodeURIComponent(msg)}`
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
      return
    }

    if (d.content !== undefined && d.content !== null) {
      aiMsg.content += d.content
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

  if (isRecording.value) stopRecording()
  isFinishing.value = true
  const wpm = calcAvgWpm()

  const loadingMsg = ElMessage({ message: '🤖 正在深度分析，请稍候...', type: 'info', duration: 0 })
  try {
    const res = await finishInterviewAPI({ recordId: recordId.value, wpm, voiceRounds: voiceRoundCount })
    loadingMsg.close()

    if (res) {
      reportData.score = res.score || 0
      reportData.feedback = res.feedback || ''
      reportData.wpm = wpm
      reportData.voiceRounds = voiceRoundCount

      // Parse ability JSON
      try { reportData.ability = typeof res.abilityJson === 'string' ? JSON.parse(res.abilityJson) : (res.abilityJson || {}) }
      catch { reportData.ability = {} }

      // Parse recommendations JSON
      try { reportData.recommendations = typeof res.recommendations === 'string' ? JSON.parse(res.recommendations) : (res.recommendations || []) }
      catch { reportData.recommendations = [] }

      showReport.value = true
      activeTab.value = 'radar'
      // Draw radar after DOM renders
      nextTick(() => drawRadarChart())
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
/* ── Layout ── */
.interview-container { height: 100vh; display: flex; background: #f0f4f8; }
.el-container { max-width: 1040px; width: 100%; margin: 0 auto; background: #fff; box-shadow: 0 8px 40px rgba(0,0,0,0.1); display: flex; flex-direction: column; }

/* ── Header ── */
.interview-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #ebeef5; padding: 0 20px; flex-shrink: 0; background: #fff; }
.header-left { display: flex; align-items: center; gap: 14px; }
.title { font-weight: 700; font-size: 17px; color: #1d2129; }
.header-right { display: flex; align-items: center; }

/* ── Chat ── */
.chat-main { flex: 1; overflow-y: auto; padding: 24px 20px; background: #fafbfc; }
.message-row { display: flex; margin-bottom: 20px; align-items: flex-start; }
.message-row.user { flex-direction: row-reverse; }
.avatar { flex-shrink: 0; }
.ai-avatar { background: linear-gradient(135deg, #52c41a, #237804); color: #fff; }
.user-avatar { background: linear-gradient(135deg, #1890ff, #005cbf); }
.message-content { max-width: 75%; padding: 12px 16px; border-radius: 12px; margin: 0 12px; font-size: 15px; line-height: 1.75; }
.message-row.ai .message-content { background: #fff; border: 1px solid #e8ecf2; box-shadow: 0 2px 8px rgba(0,0,0,0.04); border-radius: 4px 12px 12px 12px; }
.message-row.user .message-content { background: linear-gradient(135deg, #e6f4ff, #d0e9ff); border: 1px solid #91caff; color: #003a8c; border-radius: 12px 4px 12px 12px; }
.text { margin: 0; white-space: pre-wrap; font-family: inherit; word-break: break-word; }
.blinking-cursor { animation: blink .8s step-end infinite; color: #52c41a; font-size: 18px; }
@keyframes blink { 50% { opacity: 0; } }

/* ── Footer ── */
.chat-footer { padding: 14px 18px; background: #fff; border-top: 1px solid #ebeef5; display: flex; flex-direction: column; gap: 10px; height: auto !important; flex-shrink: 0; }
.voice-row { display: flex; align-items: center; gap: 14px; }
.mic-btn { width: 50px; height: 50px; flex-shrink: 0; box-shadow: 0 2px 10px rgba(0,0,0,0.12); transition: transform .15s; }
.mic-btn:hover { transform: scale(1.08); }
.visualizer-wrap { display: flex; align-items: center; gap: 8px; opacity: 0; transition: opacity .3s; pointer-events: none; }
.visualizer-wrap.active { opacity: 1; pointer-events: auto; }
.wave-canvas { border-radius: 8px; background: #f6ffed; }
.rec-dot { font-size: 12px; color: #f5222d; animation: blink 1.2s step-end infinite; white-space: nowrap; }
.no-speech-tip { font-size: 12px; color: #faad14; }
.input-row { display: flex; gap: 12px; align-items: flex-end; }
.send-btn { height: 80px; width: 88px; font-size: 15px; flex-shrink: 0; }

/* ── Report Overlay ── */
.report-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.65); z-index: 2000; display: flex; justify-content: center; align-items: center; backdrop-filter: blur(6px); }
.report-card { width: 760px; max-width: 96vw; max-height: 90vh; display: flex; flex-direction: column; border-radius: 16px; overflow: hidden; animation: slideUp .35s cubic-bezier(.34,1.56,.64,1); }
@keyframes slideUp { from { opacity: 0; transform: translateY(24px) scale(.96); } to { opacity: 1; transform: none; } }
.rpt-header { display: flex; justify-content: space-between; align-items: center; font-size: 18px; font-weight: 700; }
.report-body { flex: 1; overflow-y: auto; padding: 4px 0; }
.rpt-tabs :deep(.el-tabs__nav-wrap) { padding: 0 16px; }

/* Radar */
.radar-section { display: flex; align-items: center; gap: 24px; padding: 16px; flex-wrap: wrap; justify-content: center; }
.radar-canvas { flex-shrink: 0; }
.radar-legend { display: flex; flex-direction: column; gap: 10px; min-width: 160px; }
.legend-item { display: flex; align-items: center; gap: 8px; }
.legend-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
.legend-label { font-size: 13px; color: #303133; flex: 1; }

/* Behavioral metrics */
.behavioral-row { display: flex; gap: 16px; margin-bottom: 16px; }
.beh-card { flex: 1; background: linear-gradient(135deg, #f6ffed, #d9f7be); border: 1px solid #b7eb8f; border-radius: 12px; padding: 16px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.beh-val { font-size: 30px; font-weight: 700; color: #237804; }
.beh-lbl { font-size: 12px; color: #606266; }

/* Feedback */
.feedback-box { background: #fafbfc; border-radius: 8px; padding: 16px; }
.feedback-text { margin: 0; white-space: pre-wrap; font-family: inherit; font-size: 15px; line-height: 1.8; color: #1d2129; }

/* Recommendations */
.rec-card { border: none; background: #fafbfc; }
.rec-action { font-weight: 700; font-size: 14px; color: #1d2129; margin-bottom: 4px; }
.rec-detail { font-size: 13px; color: #606266; line-height: 1.6; }

/* Footer */
.rpt-footer { display: flex; justify-content: flex-end; gap: 12px; padding-top: 12px; }
</style>
