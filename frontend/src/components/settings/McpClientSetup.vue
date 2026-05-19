<template>
  <section class="surface-card section-shell">
    <div class="section-head mcp-head">
      <div>
        <p class="section-kicker">MCP</p>
        <h2 class="section-title">AI 客户端题库接入</h2>
        <p class="section-desc">为 Claude Code 等 MCP 客户端生成专属只读 token，用于检索已发布题库摘要。</p>
      </div>
      <div class="section-actions">
        <el-button :icon="Refresh" size="small" :loading="loadingMcp" @click="loadMcpToken">刷新</el-button>
        <el-button type="primary" :icon="Key" size="small" :loading="generatingMcp" @click="generateMcpToken">
          {{ mcp.hasActiveToken ? '重置 token' : '生成 token' }}
        </el-button>
        <el-button v-if="mcp.hasActiveToken" type="danger" plain :icon="Delete" size="small" :loading="revokingMcp" @click="revokeMcpToken">
          撤销
        </el-button>
      </div>
    </div>

    <div class="mcp-grid">
      <div class="settings-block mcp-status">
        <div class="mcp-row">
          <span class="muted-label">状态</span>
          <el-tag :type="mcp.hasActiveToken ? 'success' : 'info'">
            {{ mcp.hasActiveToken ? '已启用' : '未生成' }}
          </el-tag>
        </div>
        <div class="mcp-row">
          <span class="muted-label">角色</span>
          <span>{{ roleText }}</span>
        </div>
        <div class="mcp-row">
          <span class="muted-label">当前前缀</span>
          <span class="mono-text">{{ mcp.tokenPrefix || '生成后显示' }}</span>
        </div>
        <div class="mcp-row">
          <span class="muted-label">服务地址</span>
          <span class="mono-text wrap-text">{{ mcp.endpointUrl || '-' }}</span>
        </div>
        <div class="quota-list">
          <div v-for="item in mcp.quotas" :key="item.type" class="quota-item">
            <div>
              <span class="quota-label">{{ item.label }}</span>
              <span class="quota-meta">{{ item.used || 0 }}/{{ item.limit }}</span>
            </div>
            <el-progress
              :percentage="quotaPercent(item)"
              :show-text="false"
              :stroke-width="8"
              :status="quotaPercent(item) >= 90 ? 'exception' : undefined"
            />
          </div>
        </div>
      </div>

      <div class="settings-block mcp-config">
        <template v-if="mcp.token">
          <div class="token-once">
            <div>
              <div class="settings-label">本次生成的 token</div>
              <p class="section-desc">明文只显示一次，请立即复制到你的 MCP 客户端。</p>
            </div>
            <el-button :icon="CopyDocument" size="small" @click="copyText(mcp.token)">复制 token</el-button>
          </div>
          <pre class="code-box">{{ mcp.token }}</pre>
          <div class="copy-actions">
            <el-button :icon="CopyDocument" size="small" @click="copyText(mcp.claudeCommand)">复制 Claude 命令</el-button>
            <el-button :icon="CopyDocument" size="small" @click="copyText(mcp.jsonConfig)">复制 JSON 配置</el-button>
          </div>
          <pre class="code-box">{{ mcp.claudeCommand }}</pre>
        </template>
        <template v-else>
          <div class="empty-hint">
            <el-icon :size="28"><Key /></el-icon>
            <p>{{ mcp.hasActiveToken ? '如需重新查看明文，请重置 token。旧 token 会立即失效。' : '点击生成后会显示一次性 token 和客户端配置。' }}</p>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CopyDocument, Delete, Key, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMcpTokenAPI,
  generateMcpTokenAPI,
  revokeMcpTokenAPI,
  getMcpUsageAPI
} from '@/api/user'

const loadingMcp = ref(false)
const generatingMcp = ref(false)
const revokingMcp = ref(false)

const mcp = reactive({
  hasActiveToken: false,
  token: '',
  tokenPrefix: '',
  role: '',
  endpointUrl: '',
  claudeCommand: '',
  jsonConfig: '',
  quotas: []
})

const roleText = computed(() => {
  if (!mcp.hasActiveToken) return '生成后确认'
  return mcp.role === 'DEVELOPER' ? '开发者额度' : '普通只读'
})

const quotaPercent = (item) => {
  const limit = Number(item?.limit || 0)
  if (limit <= 0) return 0
  return Math.min(100, Math.round((Number(item.used || 0) / limit) * 100))
}

const applyMcpPayload = (payload = {}) => {
  mcp.hasActiveToken = Boolean(payload.hasActiveToken)
  mcp.token = payload.token || ''
  mcp.tokenPrefix = payload.tokenPrefix || ''
  mcp.role = payload.role || ''
  mcp.endpointUrl = payload.endpointUrl || ''
  mcp.claudeCommand = payload.claudeCommand || ''
  mcp.jsonConfig = payload.jsonConfig || ''
  mcp.quotas = Array.isArray(payload.quotas) ? payload.quotas : []
}

const mergeMcpUsage = async () => {
  try {
    const usage = await getMcpUsageAPI()
    if (Array.isArray(usage?.items)) {
      mcp.quotas = usage.items
    }
  } catch { /* token status still usable */ }
}

const loadMcpToken = async () => {
  loadingMcp.value = true
  try {
    const payload = await getMcpTokenAPI()
    applyMcpPayload(payload)
    await mergeMcpUsage()
  } catch (e) {
    ElMessage.error(e.message || 'MCP 状态加载失败')
  } finally { loadingMcp.value = false }
}

const generateMcpToken = async () => {
  if (mcp.hasActiveToken) {
    try {
      await ElMessageBox.confirm('重置后旧 token 会立即失效，确认继续吗？', '重置 MCP token', {
        type: 'warning',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  generatingMcp.value = true
  try {
    const payload = await generateMcpTokenAPI()
    applyMcpPayload(payload)
    await mergeMcpUsage()
    ElMessage.success('MCP token 已生成，请立即复制')
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally { generatingMcp.value = false }
}

const revokeMcpToken = async () => {
  try {
    await ElMessageBox.confirm('撤销后已配置的 MCP 客户端将无法继续访问题库，确认撤销吗？', '撤销 MCP token', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  revokingMcp.value = true
  try {
    await revokeMcpTokenAPI()
    applyMcpPayload({ endpointUrl: mcp.endpointUrl })
    ElMessage.success('MCP token 已撤销')
  } catch (e) {
    ElMessage.error(e.message || '撤销失败')
  } finally { revokingMcp.value = false }
}

const copyText = async (text) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

onMounted(loadMcpToken)
</script>

<style scoped>
.surface-card {
  background: #fff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.section-shell { padding: 24px; }

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.mcp-head { align-items: center; }

.section-kicker, .settings-label {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.section-title { margin: 0; font-size: 20px; line-height: 1.25; font-weight: 800; color: #191c1e; }
.section-desc { margin: 6px 0 0; color: #5a6678; font-size: 14px; line-height: 1.6; }

.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.mcp-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.85fr) minmax(0, 1.15fr);
  gap: 20px;
}

.settings-block {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.mcp-status {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mcp-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  font-size: 14px;
}

.muted-label {
  color: #64748b;
  font-size: 13px;
}

.mono-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #1f2937;
}

.wrap-text { overflow-wrap: anywhere; }
.mcp-config { min-width: 0; }

.quota-list {
  display: grid;
  gap: 12px;
  padding-top: 4px;
}

.quota-item {
  display: grid;
  gap: 6px;
}

.quota-item > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.quota-label {
  color: #334155;
  font-size: 13px;
}

.quota-meta {
  color: #64748b;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.token-once {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.copy-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.code-box {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #111827;
  color: #e5e7eb;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.empty-hint {
  min-height: 142px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #64748b;
  text-align: center;
}

.empty-hint p {
  margin: 0;
  max-width: 360px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .section-head {
    flex-direction: column;
    align-items: stretch;
  }

  .section-actions {
    flex-wrap: wrap;
  }

  .mcp-grid { grid-template-columns: 1fr; }
}
</style>
