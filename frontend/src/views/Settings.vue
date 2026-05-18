<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">Preferences</p>
          <h1 class="page-title">偏好中心</h1>
          <p class="page-subtitle">管理你的账号信息、面试默认设置与密码安全。</p>
        </div>
      </div>
    </header>

    <el-main class="page-body">
      <!-- Account Section -->
      <section class="surface-card section-shell">
        <div class="section-head">
          <div>
            <p class="section-kicker">Account</p>
            <h2 class="section-title">账号信息</h2>
          </div>
          <div class="section-actions">
            <el-button type="primary" size="small" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
            <el-button type="danger" size="small" plain @click="handleLogout">退出登录</el-button>
          </div>
        </div>
        <div class="settings-grid">
          <div class="settings-block">
            <div class="avatar-section">
              <el-upload
                class="avatar-uploader"
                :action="avatarUploadUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                :before-upload="beforeAvatarUpload"
                :on-success="handleAvatarSuccess"
                :on-error="handleAvatarError"
                accept="image/png,image/jpeg,image/webp"
              >
                <el-avatar v-if="profile.avatar" :src="profile.avatar" :size="72" />
                <el-icon v-else class="avatar-uploader-icon" :size="72"><UserFilled /></el-icon>
              </el-upload>
              <p class="avatar-hint">点击上传头像，支持 PNG / JPG / WebP，最大 2MB</p>
            </div>
            <el-form label-position="top">
              <el-form-item label="用户名">
                <el-input :model-value="profile.username" disabled />
                <span class="field-hint">用户名为账户唯一标识，注册后不可修改</span>
              </el-form-item>
              <el-form-item label="展示昵称">
                <el-input v-model="profile.nickname" placeholder="你的展示名称" maxlength="20" />
              </el-form-item>
              <el-form-item label="联系邮箱">
                <el-input v-model="profile.email" placeholder="you@example.com" />
              </el-form-item>
            </el-form>
          </div>
          <div class="settings-block password-block">
            <h4 class="block-title">修改密码</h4>
            <el-form label-position="top">
              <el-form-item label="旧密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="输入旧密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="changingPwd" @click="changePassword">修改密码</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </section>

      <!-- Interview Preferences -->
      <section class="surface-card section-shell">
        <div class="section-head">
          <div>
            <p class="section-kicker">Interview</p>
            <h2 class="section-title">面试默认偏好</h2>
            <p class="section-desc">设置后，面试准备页会自动读取这些默认值。</p>
          </div>
          <el-button type="primary" size="small" :loading="savingPref" @click="savePreference">保存偏好</el-button>
        </div>
        <div class="settings-grid three-col">
          <div class="settings-block">
            <div class="settings-label">默认模式</div>
            <el-radio-group v-model="pref.defaultMode">
              <el-radio-button label="text">文字模式</el-radio-button>
              <el-radio-button label="video">视频模式</el-radio-button>
            </el-radio-group>
          </div>
          <div class="settings-block">
            <div class="settings-label">默认岗位</div>
            <el-select v-model="pref.defaultRole" placeholder="选择默认岗位" clearable style="width:100%">
              <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </div>
          <div class="settings-block">
            <div class="settings-label">难度倾向</div>
            <el-radio-group v-model="pref.difficultyLevel">
              <el-radio-button label="junior">应届/0-1年</el-radio-button>
              <el-radio-button label="mid">1-3年</el-radio-button>
              <el-radio-button label="senior">3-5年</el-radio-button>
              <el-radio-button label="principal">5年+</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </section>

      <!-- MCP Token -->
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
              <span class="muted-label">当前前缀</span>
              <span class="mono-text">{{ mcp.tokenPrefix || '生成后显示' }}</span>
            </div>
            <div class="mcp-row">
              <span class="muted-label">服务地址</span>
              <span class="mono-text wrap-text">{{ mcp.endpointUrl || '-' }}</span>
            </div>
            <div class="mcp-row">
              <span class="muted-label">今日额度</span>
              <span>{{ quotaText }}</span>
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

      <!-- Operations -->
      <section v-if="isDeveloper" class="surface-card section-shell">
        <div class="section-head operations-head">
          <div>
            <p class="section-kicker">Operations</p>
            <h2 class="section-title">运营入口</h2>
            <p class="section-desc">仅开发者账号可见，用于查看访问统计、用户反馈与额度保护。</p>
          </div>
          <el-button type="primary" :icon="DataAnalysis" @click="router.push('/admin/analytics')">
            打开统计
          </el-button>
        </div>
      </section>
    </el-main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CopyDocument, DataAnalysis, Delete, Key, Refresh, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCurrentUserAPI,
  updateProfileAPI,
  changePasswordAPI,
  getPreferenceAPI,
  updatePreferenceAPI,
  getMcpTokenAPI,
  generateMcpTokenAPI,
  revokeMcpTokenAPI,
  getMcpUsageAPI
} from '@/api/user'
import { logout } from '@/utils/auth'
import { interviewSetupDefaults } from '@/mock/setup'

const router = useRouter()
const savingProfile = ref(false)
const savingPref = ref(false)
const changingPwd = ref(false)
const isDeveloper = ref(false)
const loadingMcp = ref(false)
const generatingMcp = ref(false)
const revokingMcp = ref(false)

const roleOptions = interviewSetupDefaults.roleOptions

const profile = reactive({ username: '', nickname: '', email: '', avatar: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '' })

const pref = reactive({
  defaultMode: 'text',
  defaultRole: '',
  difficultyLevel: 'mid'
})

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

const quotaText = computed(() => {
  const total = (mcp.quotas || []).find(item => item.type === 'total')
  return total ? `${total.used || 0}/${total.limit}` : '-'
})

const loadData = async () => {
  try {
    const user = await getCurrentUserAPI()
    if (user) {
      profile.username = user.username || ''
      profile.nickname = user.nickname || ''
      profile.email = user.email || ''
      profile.avatar = user.avatar ? ((import.meta.env.VITE_API_BASE_URL || '') + user.avatar) : ''
      isDeveloper.value = Boolean(user.isDeveloper)
    }
  } catch { /* defaults ok */ }
  try {
    const p = await getPreferenceAPI()
    if (p) {
      pref.defaultMode = p.defaultMode || 'text'
      pref.defaultRole = roleOptions.includes(p.defaultRole) ? p.defaultRole : ''
      pref.difficultyLevel = p.difficultyLevel || 'mid'
    }
  } catch { /* defaults ok */ }
  await loadMcpToken()
}

const saveProfile = async () => {
  savingProfile.value = true
  try {
    await updateProfileAPI({ nickname: profile.nickname, email: profile.email })
    import('@/utils/auth').then(({ setNickname }) => setNickname(profile.nickname))
    ElMessage.success('资料已更新')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally { savingProfile.value = false }
}

const changePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  changingPwd.value = true
  try {
    await changePasswordAPI(passwordForm)
    ElMessage.success('密码已修改，下次登录请使用新密码')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  } finally { changingPwd.value = false }
}

const savePreference = async () => {
  savingPref.value = true
  try {
    await updatePreferenceAPI({ ...pref })
    ElMessage.success('偏好已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally { savingPref.value = false }
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

onMounted(loadData)

const avatarUploadUrl = `${import.meta.env.VITE_API_BASE_URL || ''}/api/user/avatar`
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }

const beforeAvatarUpload = (file) => {
  const isImage = ['image/png', 'image/jpeg', 'image/webp'].includes(file.type)
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('仅支持 PNG / JPG / WebP 格式！'); return false }
  if (!isLt2M) { ElMessage.error('头像文件不能超过 2MB！'); return false }
  return true
}

const handleAvatarSuccess = (response) => {
  // el-upload 使用原生 XHR，response 可能是未解析的 JSON 字符串
  let parsed
  try {
    parsed = typeof response === 'string' ? JSON.parse(response) : response
  } catch {
    parsed = response
  }
  if (parsed?.code === 200 && parsed?.data?.avatarUrl) {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
    profile.avatar = baseUrl + parsed.data.avatarUrl
    ElMessage.success('头像已更新')
  } else {
    ElMessage.error(parsed?.msg || '上传失败')
  }
}

const handleAvatarError = () => {
  ElMessage.error('头像上传失败，请重试')
}

const handleLogout = () => {
  logout()
  router.push('/login')
}
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: #f7f9fb;
  color: #191c1e;
}

.settings-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 32px;
  background: rgba(247, 249, 251, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(69, 70, 82, 0.08);
}

.brand-cluster { display: flex; align-items: center; gap: 16px; min-width: 0; }
.icon-button { flex: 0 0 auto; }
.header-copy { min-width: 0; }

.eyebrow, .section-kicker, .settings-label {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title { margin: 0; font-size: 24px; line-height: 1.2; font-weight: 800; color: #191c1e; }
.page-subtitle, .section-desc { margin: 6px 0 0; color: #5a6678; font-size: 14px; line-height: 1.6; }

.page-body {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-sizing: border-box;
}

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

.section-title { margin: 0; font-size: 20px; line-height: 1.25; font-weight: 800; color: #191c1e; }

.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.operations-head {
  align-items: center;
  margin-bottom: 0;
}

.mcp-head {
  align-items: center;
}

.settings-grid { display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 20px; }
.settings-grid.three-col { grid-template-columns: repeat(3, 1fr); }

.mcp-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.85fr) minmax(0, 1.15fr);
  gap: 20px;
}

.settings-block {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.block-title { margin: 0 0 12px; font-size: 16px; color: #191c1e; }

:deep(.el-input.is-disabled .el-input__wrapper) { background: #fff; }

.field-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
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

.wrap-text {
  overflow-wrap: anywhere;
}

.mcp-config {
  min-width: 0;
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
  .settings-header { flex-direction: column; align-items: stretch; }
  .settings-grid, .settings-grid.three-col, .mcp-grid { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .settings-header, .page-body { padding-left: 16px; padding-right: 16px; }
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.avatar-uploader :deep(.el-upload) {
  border: 2px dashed rgba(69, 70, 82, 0.15);
  border-radius: 50%;
  cursor: pointer;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.avatar-uploader :deep(.el-upload):hover {
  border-color: #3a388b;
}

.avatar-uploader-icon {
  color: #b0aea5;
}

.avatar-hint {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
</style>
