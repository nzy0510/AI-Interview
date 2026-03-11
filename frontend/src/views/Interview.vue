<template>
  <div class="interview-container">
    <el-container>
      <el-header>
        <div class="header-left">
          <el-button @click="router.back()" icon="ArrowLeft" circle />
          <span class="title">正在进行：{{ position }} 模拟面试</span>
        </div>
        <div class="header-right">
          <el-button type="danger" size="small" @click="endInterview">结束并生成报告</el-button>
        </div>
      </el-header>

      <el-main class="chat-main" ref="chatMainRef">
        <div v-for="(msg, index) in messageList" :key="index" :class="['message-row', msg.role]">
          <el-avatar v-if="msg.role === 'ai'" class="avatar ai-avatar">AI</el-avatar>
          <div class="message-content">
            <!-- Format AI message stream via markdown (simplistic approach here, or plain text for now) -->
            <pre class="text">{{ msg.content }}</pre>
            <span v-if="msg.streaming" class="blinking-cursor">▍</span>
          </div>
          <el-avatar v-if="msg.role === 'user'" class="avatar user-avatar" icon="User"></el-avatar>
        </div>
      </el-main>

      <el-footer class="chat-footer">
        <el-input
          v-model="inputMsg"
          type="textarea"
          :rows="3"
          placeholder="请输入你的回答... (按 Enter 发送，Shift+Enter 换行)"
          resize="none"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="isStreaming || showReport"
        />
        <el-button type="primary" class="send-btn" @click="sendMessage" :disabled="!inputMsg.trim() || isStreaming || showReport" :loading="isStreaming">
          发送
        </el-button>
      </el-footer>

      <!-- Evaluation Report Overlay -->
      <div v-if="showReport" class="report-overlay">
        <el-card class="report-card">
          <template #header>
            <div class="card-header">
              <span>面试评估报告</span>
              <el-tag type="success" effect="dark" round>得分: {{ reportData.score }} / 100</el-tag>
            </div>
          </template>
          <div class="report-content">
            <pre class="feedback-text">{{ reportData.feedback }}</pre>
          </div>
          <div class="report-footer">
            <el-button type="primary" @click="router.push('/')">返回大厅</el-button>
          </div>
        </el-card>
      </div>

    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { startInterviewAPI, finishInterviewAPI } from '@/api/interview'

const route = useRoute()
const router = useRouter()

const position = ref(route.query.role || '未指定岗位')
const recordId = ref(null)
const messageList = ref([])
const inputMsg = ref('')
const isStreaming = ref(false)
const chatMainRef = ref(null)

const showReport = ref(false)
const reportData = ref({ score: 0, feedback: '' })
const isFinishing = ref(false)

let eventSource = null

onMounted(async () => {
  // 1. Ask backend to initialize a new session
  try {
    const resId = await startInterviewAPI({ position: position.value })
    recordId.value = resId
    
    // Add an initial message to instruct the user. The AI handles the real first message, 
    // but we can ask the AI to start via a hidden prompt, or just wait for user to say hello.
    // For this design, let's trigger the AI to start speaking automatically by sending a hidden 'hello'.
    triggerAiStart()
  } catch (error) {
    ElMessage.error('无法初始化面试，返回大厅')
    router.back()
  }
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})

const scrollToBottom = async () => {
  await nextTick()
  if (chatMainRef.value && chatMainRef.value.$el) {
    chatMainRef.value.$el.scrollTop = chatMainRef.value.$el.scrollHeight
  }
}

const triggerAiStart = () => {
  // Silently trigger AI to introduce itself based on the system prompt
  streamAiResponse("你好，我已经准备好开始面试了。")
}

const sendMessage = () => {
  if (!inputMsg.value.trim() || isStreaming.value) return

  // 1. Add User message to UI
  messageList.value.push({ role: 'user', content: inputMsg.value.trim(), streaming: false })
  const userText = inputMsg.value.trim()
  inputMsg.value = ''
  scrollToBottom()

  // 2. Stream AI Response
  streamAiResponse(userText)
}

const streamAiResponse = (msgText) => {
  isStreaming.value = true
  
  // Create an empty AI message for streaming
  const aiMessageIndex = messageList.value.length
  messageList.value.push({ role: 'ai', content: '', streaming: true })
  
  // Use SSE
  const encodedMsg = encodeURIComponent(msgText)
  const url = `http://localhost:8080/api/interview/chatStream?recordId=${recordId.value}&message=${encodedMsg}`
  
  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      
      if (data.error) {
        if (data.error === 'session_expired') {
          ElMessage.error('会话已过期')
        } else {
          ElMessage.error('AI服务异常: ' + data.error)
        }
        eventSource.close()
        return
      }

      if (data.done) {
        messageList.value[aiMessageIndex].streaming = false
        isStreaming.value = false
        eventSource.close()
        return
      }

      if (data.content) {
        messageList.value[aiMessageIndex].content += data.content
        scrollToBottom()
      }
    } catch (err) {
      console.error('SSE 消息解析失败:', err, event.data)
    }
  }

  eventSource.onerror = (event) => {
    console.error('SSE Error:', event)
    messageList.value[aiMessageIndex].streaming = false
    isStreaming.value = false
    eventSource.close()
  }
}

const endInterview = async () => {
  if (messageList.value.length <= 1) {
    ElMessage.warning('面试尚未开始或没有对话记录。可以返回大厅。')
    return
  }
  
  try {
    await ElMessageBox.confirm('确定要结束面试并生成最终评价报告吗？分析可能需要几十秒时间。', '提示', {
      confirmButtonText: '确定提交',
      cancelButtonText: '暂不',
      type: 'warning',
    })
    
    isStreaming.value = true // Block input
    const loading = ElMessage({
      message: 'AI面试官正在综合评估你的表现...',
      type: 'info',
      duration: 0
    })

    const res = await finishInterviewAPI({ recordId: recordId.value })
    loading.close()
    
    if (res) {
      reportData.value = {
        score: res.score,
        feedback: res.feedback
      }
      showReport.value = true
    }
  } catch (err) {
    // If request failed, ElMessage already triggered in request.js
    if (loading) loading.close()
    isStreaming.value = false
  }
}
</script>

<style scoped>
.interview-container {
  height: 100vh;
  display: flex;
  background-color: #f5f7fa;
}

.el-container {
  max-width: 1000px;
  margin: 0 auto;
  background: white;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.el-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  background-color: #fff;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.title {
  font-weight: bold;
  font-size: 18px;
}

.chat-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #fafbfc;
}

.message-row {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
}

.ai-avatar {
  background-color: #67c23a;
  color: white;
}

.user-avatar {
  background-color: #409eff;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
  margin: 0 15px;
  font-size: 15px;
  line-height: 1.6;
  position: relative;
}

.message-row.ai .message-content {
  background-color: white;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
}

.message-row.user .message-content {
  background-color: #ecf5ff;
  border: 1px solid #b3d8ff;
  color: #409eff;
}

.text {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  word-break: break-all;
}

.blinking-cursor {
  animation: blink 1s step-end infinite;
  color: #67c23a;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-footer {
  padding: 20px;
  background-color: white;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 15px;
  align-items: flex-end;
  height: auto !important; /* Override default 60px height */
}

.send-btn {
  height: 75px;
  width: 100px;
}

/* Report Overlay Styles */
.report-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.6);
  z-index: 2000;
  display: flex;
  justify-content: center;
  align-items: center;
  backdrop-filter: blur(5px);
}

.report-card {
  width: 600px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: bold;
}

.report-content {
  flex: 1;
  overflow-y: auto;
  min-height: 200px;
  padding: 10px 0;
}

.feedback-text {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  word-break: break-all;
}

.report-footer {
  margin-top: 20px;
  text-align: center;
}
</style>
