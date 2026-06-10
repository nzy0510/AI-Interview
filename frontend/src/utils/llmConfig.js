export const llmProviderPresets = [
  {
    value: 'deepseek',
    label: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-v4-flash',
    description: '适合中文问答与代码场景，默认使用官方 OpenAI-compatible 地址。'
  },
  {
    value: 'moonshot',
    label: 'Kimi / Moonshot',
    baseUrl: 'https://api.moonshot.cn/v1',
    modelName: 'kimi-k2.6',
    description: '适合长上下文与中文资料整理，默认使用 Kimi 官方兼容接口。'
  },
  {
    value: 'zhipu',
    label: 'GLM / 智谱',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4/',
    modelName: 'glm-5.1',
    description: '适合通用中文对话与推理，默认使用智谱 OpenAI 兼容接口。'
  },
  {
    value: 'qwen',
    label: 'Qwen / 通义千问',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'qwen-plus',
    description: '适合阿里云百炼兼容接入，默认使用北京地域兼容地址。'
  },
  {
    value: 'custom',
    label: '自定义兼容源',
    baseUrl: 'https://api.openai.com/v1',
    modelName: 'gpt-4o-mini',
    description: '用于任意 OpenAI-compatible 服务，所有字段都可手工调整。'
  }
]

const providerPresetMap = llmProviderPresets.reduce((acc, item) => {
  acc[item.value] = item
  return acc
}, {})

const missingConfigPatterns = [
  'llm config',
  'llm provider',
  'provider config',
  'provider 未配置',
  '未配置大模型',
  '请先配置',
  '没有启用',
  '无可用模型配置',
  'active llm',
  'llm_config_required',
  'llm provider required'
]

export function getLlmProviderPreset(provider) {
  return providerPresetMap[provider] || providerPresetMap.custom
}

export function getLlmProviderLabel(provider) {
  return getLlmProviderPreset(provider).label
}

export function createLlmConfigDraft(provider = 'deepseek') {
  const preset = getLlmProviderPreset(provider)
  return {
    id: null,
    provider: preset.value,
    displayName: preset.label,
    baseUrl: preset.baseUrl,
    modelName: preset.modelName,
    apiKey: '',
    temperature: 0.7
  }
}

export function applyProviderPreset(draft, nextProvider, previousProvider = draft?.provider) {
  const nextPreset = getLlmProviderPreset(nextProvider)
  const previousPreset = getLlmProviderPreset(previousProvider)
  const nextDraft = { ...draft }

  if (!nextDraft.displayName || nextDraft.displayName === previousPreset.label) {
    nextDraft.displayName = nextPreset.label
  }
  if (!nextDraft.baseUrl || nextDraft.baseUrl === previousPreset.baseUrl) {
    nextDraft.baseUrl = nextPreset.baseUrl
  }
  if (!nextDraft.modelName || nextDraft.modelName === previousPreset.modelName) {
    nextDraft.modelName = nextPreset.modelName
  }

  nextDraft.provider = nextPreset.value
  return nextDraft
}

export function buildLlmConfigPayload(form) {
  const payload = {
    provider: String(form.provider || '').trim(),
    displayName: String(form.displayName || '').trim(),
    baseUrl: String(form.baseUrl || '').trim(),
    modelName: String(form.modelName || '').trim(),
    temperature: Number.isFinite(Number(form.temperature)) ? Number(form.temperature) : 0.7
  }

  const apiKey = String(form.apiKey || '').trim()
  if (apiKey) {
    payload.apiKey = apiKey
  }

  return payload
}

export function maskApiKeyHint(hint) {
  return hint ? String(hint) : '已保存密钥'
}

export function sanitizeLlmMessage(message) {
  return String(message || '')
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, 'Bearer ***')
    .replace(/sk-[A-Za-z0-9_-]{8,}/g, (match) => `${match.slice(0, 3)}***${match.slice(-4)}`)
    .replace(/([A-Za-z_]*api[_-]?key["']?\s*[:=]\s*["']?)([^"'\s,}]+)/gi, '$1***')
}

export function deriveLlmConfigStatus(configs = []) {
  const active = Array.isArray(configs) ? configs.find((item) => item?.enabled) : null
  return {
    resolved: true,
    hasAnyConfig: Array.isArray(configs) && configs.length > 0,
    hasActiveConfig: Boolean(active),
    activeProvider: active?.provider || '',
    activeModelName: active?.modelName || '',
    activeDisplayName: active?.displayName || ''
  }
}

export function createUnknownLlmConfigStatus() {
  return {
    resolved: false,
    hasAnyConfig: false,
    hasActiveConfig: false,
    activeProvider: '',
    activeModelName: '',
    activeDisplayName: ''
  }
}

export function normalizeLlmConfigStatus(status) {
  if (!status || typeof status !== 'object') {
    return createUnknownLlmConfigStatus()
  }

  return {
    resolved: true,
    hasAnyConfig: Boolean(status.hasAnyConfig ?? status.configured ?? status.hasConfig ?? status.hasActiveConfig),
    hasActiveConfig: Boolean(status.hasActiveConfig ?? status.active ?? status.configured),
    activeProvider: String(status.activeProvider || status.provider || ''),
    activeModelName: String(status.activeModelName || status.modelName || ''),
    activeDisplayName: String(status.activeDisplayName || status.displayName || '')
  }
}

export function isMissingLlmConfigError(error) {
  const message = String(error?.message || error?.response?.data?.msg || '').toLowerCase()
  return missingConfigPatterns.some((pattern) => message.includes(pattern))
}

export function buildLlmConfigRouteQuery(source = '') {
  return {
    reason: 'missing-config',
    source
  }
}

export function getLlmConfigSourceLabel(source) {
  const labels = {
    dashboard: '工作台',
    'interview-setup': '面试准备',
    interview: '文字面试',
    'video-interview': '视频面试',
    mentor: 'AI Mentor'
  }
  return labels[source] || '当前功能'
}
