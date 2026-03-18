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
            <div class="text" v-html="renderMarkdown(msg.content)"></div>
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
                综合得分 {{ displayScore }} / 100
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
import { Microphone, ArrowLeft, UserFilled, PieChart } from '@element-plus/icons-vue'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'
import { marked } from 'marked'

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

// --- Markdown Rendering ---
const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text)
}

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
  if (tab === 'radar') nextTick(() => animateRadar())
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

// ─── Waveform Canvas (Siri Style) ───────────────────────────────────────────
const drawWave = () => {
  if (!analyserNode || !canvasRef.value) return
  animFrameId = requestAnimationFrame(drawWave)
  analyserNode.getByteFrequencyData(dataArray)

  const c = canvasRef.value, ctx = c.getContext('2d'), W = c.width, H = c.height
  ctx.clearRect(0, 0, W, H)

  const time = Date.now() / 1000
  const layers = [
    { color: 'rgba(96, 165, 250, 0.4)', speed: 2, amp: 0.6 },
    { color: 'rgba(192, 132, 252, 0.3)', speed: 1.5, amp: 0.4 },
    { color: 'rgba(45, 212, 191, 0.2)', speed: 2.5, amp: 0.5 }
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

// ─── Radar Chart (Animated) ──────────────────────────────────────────────────
const drawRadarChart = (progress = 1.0) => {
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
    ctx.strokeStyle = gi === grades.length - 1 ? 'rgba(96,165,250,0.3)' : 'rgba(0,0,0,0.08)'
    ctx.lineWidth = 1
    ctx.stroke()
  })

  // Draw axes
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + maxR * Math.cos(a), cy + maxR * Math.sin(a))
    ctx.strokeStyle = 'rgba(0,0,0,0.08)'; ctx.stroke()
  }

  // Draw animated polygon
  const scores = keys.map(k => (gradeScore[reportData.ability[k]] || 0.2) * progress)
  ctx.beginPath()
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    const r = maxR * scores[i]
    i === 0 ? ctx.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a)) : ctx.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a))
  }
  ctx.closePath()
  ctx.fillStyle = 'rgba(96, 165, 250, 0.15)'
  ctx.fill()
  ctx.strokeStyle = 'rgba(59, 130, 246, 0.8)'
  ctx.lineWidth = 2.5
  ctx.stroke()

  // Labels & dots
  for (let i = 0; i < n; i++) {
    const a = angle * i - Math.PI / 2
    const r = maxR * scores[i]
    ctx.beginPath(); ctx.arc(cx + r * Math.cos(a), cy + r * Math.sin(a), 5, 0, Math.PI * 2)
    ctx.fillStyle = colors[i]; ctx.fill()

    if (progress === 1.0) { // Labels only on final frame
      const la = angle * i - Math.PI / 2, lr = maxR + 36
      ctx.fillStyle = '#1e293b'; ctx.font = 'bold 13px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(labels[i], cx + lr * Math.cos(la), cy + lr * Math.sin(la))
    }
  }
}

const animateRadar = () => {
  let startTime = null
  const duration = 800
  const step = (now) => {
    if (!startTime) startTime = now
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1.0)
    const eased = 1 - Math.pow(1 - progress, 3) // easeOutCubic
    drawRadarChart(eased)
    if (progress < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

const displayScore = ref(0)
const animateScore = (finalScore) => {
  displayScore.value = 0
  let startTime = null
  const duration = 1500
  const step = (now) => {
    if (!startTime) startTime = now
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1.0)
    const eased = 1 - Math.pow(1 - progress, 4) // easeOutQuart
    displayScore.value = Math.floor(eased * finalScore)
    if (progress < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
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
      
      // Check for termination marker
      if (aiMsg.content.includes('[TERMINATE]')) {
        aiMsg.content = aiMsg.content.replace('[TERMINATE]', '').trim()
        aiMsg.streaming = false
        isStreaming.value = false
        eventSource.close()
        
        // Auto trigger end interview
        ElMessage.warning('检测到面试异常中断，正在生成记录...')
        setTimeout(() => {
          // Pass true to bypass confirmation dialog
          performEndInterview(true)
        }, 1500)
        return
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

const performEndInterview = async (isAuto = false) => {
  if (isRecording.value) stopRecording()
  isFinishing.value = true
  const wpm = calcAvgWpm()

  const loadingMsg = ElMessage({ 
    message: isAuto ? '🚨 检测到异常中断，正在锁定评分...' : '🤖 正在深度分析，请稍候...', 
    type: isAuto ? 'warning' : 'info', 
    duration: 0 
  })
  
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
/* ── Layout ── */
.interview-container { height: 100vh; display: flex; background: #f0f4f8; }
.el-container { max-width: 1040px; width: 100%; margin: 0 auto; background: #fff; box-shadow: 0 8px 40px rgba(0,0,0,0.1); display: flex; flex-direction: column; }

/* ── Header ── */
.interview-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #ebeef5; padding: 0 20px; flex-shrink: 0; background: #fff; }
.header-left { display: flex; align-items: center; gap: 14px; }
.title { font-weight: 700; font-size: 17px; color: #1d2129; }
.header-right { display: flex; align-items: center; }

/* ── Chat ── */
.chat-main { flex: 1; overflow-y: auto; padding: 24px 20px; background: radial-gradient(circle at top right, #f8fafc, #f1f5f9); }
.message-row { display: flex; margin-bottom: 24px; align-items: flex-start; animation: msgSlideUp 0.4s cubic-bezier(0.2, 0.8, 0.2, 1) both; }
.message-row.user { flex-direction: row-reverse; }

@keyframes msgSlideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.avatar { flex-shrink: 0; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 2px solid #fff; }
.ai-avatar { background: linear-gradient(135deg, #60a5fa, #3b82f6); color: #fff; }
.user-avatar { background: linear-gradient(135deg, #6366f1, #4f46e5); }

.message-content { 
  max-width: 78%; 
  padding: 14px 18px; 
  margin: 0 14px; 
  font-size: 15px; 
  line-height: 1.7; 
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 32px rgba(0,0,0,0.03);
}

.message-row.ai .message-content { 
  background: rgba(255, 255, 255, 0.7); 
  border: 1px solid rgba(255,255,255,0.4); 
  border-radius: 4px 16px 16px 16px; 
  color: #1e293b;
}

.message-row.user .message-content { 
  background: rgba(99, 102, 241, 0.08); 
  border: 1px solid rgba(99, 102, 241, 0.15); 
  color: #3730a3; 
  border-radius: 16px 4px 16px 16px; 
}

.text { margin: 0; word-break: break-word; }
.text :deep(p) { margin: 0 0 8px 0; }
.text :deep(p:last-child) { margin-bottom: 0; }
.text :deep(strong) { color: #2563eb; }
.text :deep(ul), .text :deep(ol) { padding-left: 20px; margin: 8px 0; }
.text :deep(code) { background: rgba(0,0,0,0.05); padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }

.blinking-cursor { animation: blink .8s step-end infinite; color: #3b82f6; font-size: 18px; margin-left: 2px; }
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
