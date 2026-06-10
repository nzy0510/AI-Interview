<template>
  <div class="llm-page">
    <header class="llm-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">LLM Providers</p>
          <h1 class="page-title">大模型配置</h1>
          <p class="page-subtitle">为当前账号配置可切换的 OpenAI-compatible Provider，保存后一次只启用一个。</p>
        </div>
      </div>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadConfigs({ silent: false })">
        刷新列表
      </el-button>
    </header>

    <el-main class="page-body">
      <el-alert
        v-if="reasonMessage"
        type="warning"
        :closable="false"
        show-icon
        class="page-alert"
      >
        <template #title>{{ reasonMessage }}</template>
      </el-alert>

      <section class="surface-card section-shell overview-shell">
        <div class="overview-card">
          <span class="overview-label">配置总数</span>
          <strong>{{ configs.length }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">当前状态</span>
          <strong>{{ status.hasActiveConfig ? '已启用配置' : '待启用配置' }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">当前激活</span>
          <strong>{{ status.activeDisplayName || status.activeProvider || '暂无' }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">当前模型</span>
          <strong>{{ status.activeModelName || '未设置' }}</strong>
        </div>
      </section>

      <div class="page-grid">
        <section class="surface-card section-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">Provider List</p>
              <h2 class="section-title">已保存配置</h2>
              <p class="section-desc">列表只展示脱敏摘要，API Key 不会在页面回显。</p>
            </div>
            <el-button type="primary" plain @click="resetForm()">新增配置</el-button>
          </div>

          <el-empty v-if="!loading && !configs.length" description="当前还没有可用的大模型配置">
            <el-button type="primary" @click="resetForm()">创建第一个配置</el-button>
          </el-empty>

          <div v-else class="config-list">
            <article
              v-for="item in configs"
              :key="item.id"
              class="config-card"
              :class="{ 'is-active': isLlmConfigActive(item), 'is-editing': form.id === item.id }"
            >
              <div class="config-card__head">
                <div>
                  <div class="config-title-row">
                    <h3>{{ item.displayName || getLlmProviderLabel(item.provider) }}</h3>
                    <el-tag v-if="isLlmConfigActive(item)" type="success" size="small" effect="dark">当前启用</el-tag>
                  </div>
                  <p class="config-subtitle">{{ getLlmProviderLabel(item.provider) }}</p>
                </div>
                <el-tag size="small" effect="plain">{{ item.modelName || '未设置模型' }}</el-tag>
              </div>

              <div class="config-meta">
                <div class="meta-row">
                  <span>Base URL</span>
                  <strong>{{ item.baseUrl || '未设置' }}</strong>
                </div>
                <div class="meta-row">
                  <span>API Key</span>
                  <strong>{{ maskApiKeyHint(item.apiKeyHint) }}</strong>
                </div>
                <div class="meta-row">
                  <span>温度</span>
                  <strong>{{ formatTemperature(item.temperature) }}</strong>
                </div>
                <div class="meta-row">
                  <span>最近测试</span>
                  <strong>{{ getTestSummary(item) }}</strong>
                </div>
              </div>

              <p v-if="item.lastTestMessage" class="test-message">{{ sanitizeLlmMessage(item.lastTestMessage) }}</p>

              <div class="config-actions">
                <el-button size="small" @click="startEdit(item)">编辑</el-button>
                <el-button
                  size="small"
                  :loading="actionConfigId === item.id && actionType === 'test'"
                  @click="testSavedConfig(item)"
                >
                  测试连接
                </el-button>
                <el-button
                  v-if="!isLlmConfigActive(item)"
                  type="success"
                  size="small"
                  :loading="actionConfigId === item.id && actionType === 'activate'"
                  @click="activateConfig(item)"
                >
                  设为启用
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  plain
                  :loading="actionConfigId === item.id && actionType === 'delete'"
                  @click="removeConfig(item)"
                >
                  删除
                </el-button>
              </div>
            </article>
          </div>
        </section>

        <section class="surface-card section-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">Editor</p>
              <h2 class="section-title">{{ formMode === 'edit' ? '编辑 Provider' : '新增 Provider' }}</h2>
              <p class="section-desc">Provider 预设可作为起点，你仍然可以手工调整 Base URL、模型和显示名称。</p>
            </div>
            <el-button v-if="formMode === 'edit'" plain @click="resetForm(form.provider)">取消编辑</el-button>
          </div>

          <div class="preset-summary">
            <div>
              <div class="preset-label">当前预设</div>
              <strong>{{ currentPreset.label }}</strong>
            </div>
            <p>{{ currentPreset.description }}</p>
          </div>

          <el-form label-position="top" class="provider-form">
            <el-form-item label="Provider 预设">
              <el-select v-model="form.provider" style="width: 100%" @change="handleProviderChange">
                <el-option
                  v-for="item in presets"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <div class="form-grid">
              <el-form-item label="显示名称" class="form-grid__item">
                <el-input v-model="form.displayName" maxlength="64" placeholder="例如：DeepSeek 主账号" />
              </el-form-item>
              <el-form-item label="模型名称" class="form-grid__item">
                <el-input v-model="form.modelName" maxlength="120" placeholder="例如：deepseek-v4-flash" />
              </el-form-item>
            </div>

            <el-form-item label="Base URL">
              <el-input v-model="form.baseUrl" maxlength="240" placeholder="https://api.example.com/v1" />
            </el-form-item>

            <div class="form-grid">
              <el-form-item label="API Key" class="form-grid__item">
                <el-input
                  v-model="form.apiKey"
                  type="password"
                  show-password
                  maxlength="240"
                  autocomplete="new-password"
                  :placeholder="formMode === 'edit' ? '留空表示继续使用已保存密钥' : '输入当前 Provider 的 API Key'"
                />
              </el-form-item>
              <el-form-item label="Temperature" class="form-grid__item">
                <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" style="width: 100%" />
              </el-form-item>
            </div>

            <div class="form-hints">
              <span>API Key 只用于本次提交，不会在列表中回显完整内容。</span>
              <span>编辑现有配置时，如不更换密钥可保持为空。</span>
            </div>

            <div class="form-actions">
              <el-button
                type="primary"
                :loading="submitting"
                @click="saveConfig"
              >
                {{ formMode === 'edit' ? '保存修改' : '保存配置' }}
              </el-button>
              <el-button
                :loading="testingCurrent"
                @click="testCurrentForm"
              >
                {{ formMode === 'edit' && !form.apiKey ? '测试已保存配置' : '测试当前输入' }}
              </el-button>
              <el-button plain @click="applyCurrentPreset">重新套用预设</el-button>
            </div>
          </el-form>
        </section>
      </div>
    </el-main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateLlmConfigAPI,
  createLlmConfigAPI,
  deleteLlmConfigAPI,
  getLlmConfigsAPI,
  testLlmConfigAPI,
  updateLlmConfigAPI
} from '@/api/llm'
import {
  applyProviderPreset,
  buildLlmConfigPayload,
  createLlmConfigDraft,
  deriveLlmConfigStatus,
  getLlmConfigSourceLabel,
  getLlmProviderLabel,
  getLlmProviderPreset,
  isLlmConfigActive,
  isLlmTestSuccess,
  llmProviderPresets,
  maskApiKeyHint,
  sanitizeLlmMessage
} from '@/utils/llmConfig'

const route = useRoute()
const router = useRouter()

const presets = llmProviderPresets
const loading = ref(false)
const submitting = ref(false)
const testingCurrent = ref(false)
const actionConfigId = ref(null)
const actionType = ref('')
const configs = ref([])
const status = ref(deriveLlmConfigStatus([]))
const formMode = ref('create')
const lastSelectedProvider = ref('deepseek')

const form = reactive(createLlmConfigDraft())

const currentPreset = computed(() => getLlmProviderPreset(form.provider))
const sourceLabel = computed(() => getLlmConfigSourceLabel(String(route.query.source || '')))
const reasonMessage = computed(() => {
  if (route.query.reason !== 'missing-config') return ''
  return `${sourceLabel.value} 当前不可用，请先新增并启用一个 Provider，再返回继续操作。`
})

const formatTemperature = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed.toFixed(1) : '0.7'
}

const getTestSummary = (item) => {
  const testPassed = isLlmTestSuccess(item?.lastTestStatus)
  if (!item?.lastTestTime) return testPassed ? '最近一次测试通过' : '暂无测试记录'
  const timeText = new Date(item.lastTestTime).toLocaleString('zh-CN')
  return `${testPassed ? '通过' : '失败'} · ${timeText}`
}

const handleProviderChange = (provider) => {
  const nextDraft = applyProviderPreset({ ...form }, provider, lastSelectedProvider.value)
  Object.assign(form, nextDraft)
  lastSelectedProvider.value = provider
}

const applyCurrentPreset = () => {
  const presetDraft = createLlmConfigDraft(form.provider)
  Object.assign(form, {
    ...form,
    displayName: presetDraft.displayName,
    baseUrl: presetDraft.baseUrl,
    modelName: presetDraft.modelName
  })
}

const resetForm = (provider = 'deepseek') => {
  formMode.value = 'create'
  Object.assign(form, createLlmConfigDraft(provider))
  lastSelectedProvider.value = form.provider
}

const startEdit = (item) => {
  formMode.value = 'edit'
  Object.assign(form, {
    id: item.id,
    provider: item.provider || 'custom',
    displayName: item.displayName || getLlmProviderLabel(item.provider),
    baseUrl: item.baseUrl || '',
    modelName: item.modelName || '',
    apiKey: '',
    temperature: Number.isFinite(Number(item.temperature)) ? Number(item.temperature) : 0.7
  })
  lastSelectedProvider.value = form.provider
}

const validateForm = ({ requireApiKey = false } = {}) => {
  if (!String(form.displayName || '').trim()) {
    ElMessage.warning('请先填写显示名称')
    return false
  }
  if (!String(form.baseUrl || '').trim()) {
    ElMessage.warning('请先填写 Base URL')
    return false
  }
  if (!String(form.modelName || '').trim()) {
    ElMessage.warning('请先填写模型名称')
    return false
  }
  if (requireApiKey && !String(form.apiKey || '').trim()) {
    ElMessage.warning('请先填写 API Key')
    return false
  }
  return true
}

const loadConfigs = async ({ silent = true } = {}) => {
  loading.value = true
  try {
    const data = await getLlmConfigsAPI({ silent })
    configs.value = Array.isArray(data) ? data : []
    status.value = deriveLlmConfigStatus(configs.value)
  } catch (error) {
    configs.value = []
    status.value = deriveLlmConfigStatus([])
    if (!silent) {
      ElMessage.error(sanitizeLlmMessage(error?.message || '配置列表加载失败'))
    }
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  const requireApiKey = formMode.value !== 'edit'
  if (!validateForm({ requireApiKey })) return

  const payload = buildLlmConfigPayload(form)
  if (requireApiKey && !payload.apiKey) {
    ElMessage.warning('新增配置时必须填写 API Key')
    return
  }

  submitting.value = true
  try {
    if (formMode.value === 'edit' && form.id) {
      await updateLlmConfigAPI(form.id, payload)
      ElMessage.success('配置已更新')
    } else {
      await createLlmConfigAPI(payload)
      ElMessage.success('配置已保存')
    }
    await loadConfigs({ silent: true })
    resetForm(form.provider)
  } finally {
    submitting.value = false
  }
}

const buildTestPayload = () => {
  if (formMode.value === 'edit' && form.id && !String(form.apiKey || '').trim()) {
    return { configId: form.id }
  }
  if (!validateForm({ requireApiKey: true })) return null
  return buildLlmConfigPayload(form)
}

const showTestMessage = (result) => {
  const message = sanitizeLlmMessage(result?.message || result?.detail || result?.statusMessage || '连接测试通过')
  if (result?.success === false || result?.status === 'failed') {
    ElMessage.error(message)
    return
  }
  ElMessage.success(message)
}

const testCurrentForm = async () => {
  const payload = buildTestPayload()
  if (!payload) return

  testingCurrent.value = true
  try {
    const result = await testLlmConfigAPI(payload)
    showTestMessage(result)
    await loadConfigs({ silent: true })
  } finally {
    testingCurrent.value = false
  }
}

const testSavedConfig = async (item) => {
  actionConfigId.value = item.id
  actionType.value = 'test'
  try {
    const result = await testLlmConfigAPI({ configId: item.id })
    showTestMessage(result)
    await loadConfigs({ silent: true })
  } finally {
    actionConfigId.value = null
    actionType.value = ''
  }
}

const activateConfig = async (item) => {
  actionConfigId.value = item.id
  actionType.value = 'activate'
  try {
    await activateLlmConfigAPI(item.id)
    ElMessage.success('已切换为当前启用配置')
    await loadConfigs({ silent: true })
  } finally {
    actionConfigId.value = null
    actionType.value = ''
  }
}

const removeConfig = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确认删除配置「${item.displayName || getLlmProviderLabel(item.provider)}」吗？`,
      '删除配置',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  actionConfigId.value = item.id
  actionType.value = 'delete'
  try {
    await deleteLlmConfigAPI(item.id)
    ElMessage.success('配置已删除')
    if (form.id === item.id) {
      resetForm()
    }
    await loadConfigs({ silent: true })
  } finally {
    actionConfigId.value = null
    actionType.value = ''
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.llm-page {
  min-height: 100vh;
  background: #f7f9fb;
  color: #191c1e;
}

.llm-header {
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

.eyebrow,
.section-kicker,
.preset-label,
.overview-label {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title { margin: 0; font-size: 24px; line-height: 1.2; font-weight: 800; color: #191c1e; }
.page-subtitle,
.section-desc,
.preset-summary p,
.form-hints,
.config-subtitle,
.test-message {
  margin: 6px 0 0;
  color: #5a6678;
  font-size: 14px;
  line-height: 1.6;
}

.page-body {
  max-width: 1320px;
  margin: 0 auto;
  padding: 28px 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-sizing: border-box;
}

.page-alert {
  border-radius: 14px;
}

.surface-card {
  background: #fff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.section-shell { padding: 24px; }

.overview-shell {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.overview-card {
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(58, 56, 139, 0.05), rgba(255, 255, 255, 0.95));
  border: 1px solid rgba(58, 56, 139, 0.08);
}

.overview-card strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
  line-height: 1.3;
}

.page-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1.05fr);
  gap: 24px;
  align-items: start;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.25;
  font-weight: 800;
  color: #191c1e;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.config-card {
  padding: 18px;
  border-radius: 16px;
  border: 1px solid rgba(69, 70, 82, 0.08);
  background: #fcfcfd;
}

.config-card.is-active {
  border-color: rgba(35, 147, 90, 0.28);
  box-shadow: inset 0 0 0 1px rgba(35, 147, 90, 0.08);
}

.config-card.is-editing {
  border-color: rgba(58, 56, 139, 0.22);
}

.config-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.config-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.config-title-row h3 {
  margin: 0;
  font-size: 17px;
  line-height: 1.35;
}

.config-subtitle {
  margin-top: 4px;
}

.config-meta {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.meta-row span {
  color: #5a6678;
  flex: 0 0 76px;
}

.meta-row strong {
  flex: 1;
  text-align: right;
  word-break: break-all;
}

.test-message {
  margin-top: 12px;
}

.config-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.preset-summary {
  margin-bottom: 20px;
  padding: 16px 18px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(58, 56, 139, 0.05), rgba(255, 255, 255, 0.98));
  border: 1px solid rgba(58, 56, 139, 0.08);
}

.preset-summary strong {
  font-size: 16px;
}

.provider-form {
  display: flex;
  flex-direction: column;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.form-grid__item {
  min-width: 0;
}

.form-hints {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}

@media (max-width: 1100px) {
  .overview-shell,
  .page-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .llm-header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .section-shell {
    padding: 18px;
  }

  .form-grid,
  .config-card__head,
  .meta-row {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .meta-row strong {
    text-align: left;
  }
}
</style>
