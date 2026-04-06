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

      <!-- =================== Report Overlay (Bento Grid) =================== -->
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
                  <div class="kpi-val">{{ reportData.voiceRounds }}</div>
                  <div class="kpi-lbl">语音互动轮次</div>
                </div>
              </div>
              <div class="kpi-item">
                <div class="kpi-icon">⌨️</div>
                <div class="kpi-data">
                  <div class="kpi-val">{{ totalUserRounds }}</div>
                  <div class="kpi-lbl">总发信轮次</div>
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

    </el-container>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Microphone, ArrowLeft, UserFilled, PieChart } from '@element-plus/icons-vue'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'
import * as echarts from 'echarts'
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

const gradeScore = { A: 1.0, B: 0.8, C: 0.6, D: 0.4, E: 0.2 }
const getGradeType = (g) => ({ A: 'danger', B: 'success', C: 'primary', D: 'warning' }[g] || 'info')
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

// Remove watch(activeTab)

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
.ai-avatar { background: linear-gradient(135deg, #10b981, #059669); color: #fff; } /* Emerald */
.user-avatar { background: linear-gradient(135deg, #f59e0b, #d97706); } /* Amber */

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
  background: rgba(245, 158, 11, 0.08); /* Amber tinted bg */
  border: 1px solid rgba(245, 158, 11, 0.15); 
  color: #b45309; /* Warm dark orange */
  border-radius: 16px 4px 16px 16px; 
}

.text { margin: 0; word-break: break-word; }
.text :deep(p) { margin: 0 0 8px 0; }
.text :deep(p:last-child) { margin-bottom: 0; }
.text :deep(strong) { color: #0ea5e9; } /* Cyan */
.text :deep(ul), .text :deep(ol) { padding-left: 20px; margin: 8px 0; }
.text :deep(code) { background: rgba(0,0,0,0.05); padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }

.blinking-cursor { animation: blink .8s step-end infinite; color: #10b981; font-size: 18px; margin-left: 2px; }
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
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; padding: 16px; 
  align-items: center; justify-content: center; background: rgba(30, 41, 59, 0.4); 
}
.kpi-item { display: flex; align-items: center; gap: 12px; }
.kpi-icon { font-size: 28px; background: rgba(255,255,255,0.05); padding: 10px; border-radius: 12px; line-height: 1;}
.kpi-val { font-size: 20px; font-weight: 800; color: #f8fafc; }
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
