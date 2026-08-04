const ORCHESTRATION_MODES = new Set(['AGENT', 'RULE', 'RULE_FALLBACK'])
const FALLBACK_CATEGORY_LABELS = {
  TIMEOUT: '规划超时',
  PROVIDER: '模型服务异常',
  TOOL: '工具调用失败',
  OUTPUT: '规划输出异常',
  MODEL: '模型规划失败',
  SYSTEM: '编排异常'
}
const FALLBACK_CATEGORIES = new Set(Object.keys(FALLBACK_CATEGORY_LABELS))
const ORCHESTRATION_ACTIONS = new Set([
  'DEEPEN',
  'REMEDIATE',
  'SWITCH_TOPIC',
  'PROBE_RESUME',
  'MOVE_TO_HR',
  'CONTINUE_PHASE'
])

const MODE_PRESENTATION = {
  AGENT: { label: 'Agent', type: 'success' },
  RULE: { label: '稳定规则', type: 'info' },
  RULE_FALLBACK: { label: '自动回退', type: 'warning' }
}

const ACTION_LABELS = {
  DEEPEN: '继续深挖',
  REMEDIATE: '基础补救',
  SWITCH_TOPIC: '切换主题',
  PROBE_RESUME: '项目追问',
  MOVE_TO_HR: '进入 HR 阶段',
  CONTINUE_PHASE: '继续当前阶段'
}

const TOOL_LABELS = {
  searchPositionKnowledge: '岗位知识',
  getCurrentResumeEvidence: '简历证据',
  getPositionLearningCoverage: '学习覆盖'
}

export function normalizeOrchestrationEvent(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return null
  if (payload.type !== 'orchestration') return null
  if (!ORCHESTRATION_MODES.has(payload.mode) || !ORCHESTRATION_ACTIONS.has(payload.action)) return null

  const tools = Array.isArray(payload.tools)
    ? [...new Set(payload.tools
      .filter((tool) => typeof tool === 'string')
      .map((tool) => tool.trim())
      .filter(Boolean))]
    : []

  const fallbackCategory = payload.mode === 'RULE_FALLBACK'
    && FALLBACK_CATEGORIES.has(payload.fallbackCategory)
    ? payload.fallbackCategory
    : null

  return {
    mode: payload.mode,
    action: payload.action,
    summary: typeof payload.summary === 'string' ? payload.summary.trim().slice(0, 180) : '',
    tools: tools.slice(0, 3),
    ...(fallbackCategory ? { fallbackCategory } : {})
  }
}

export function parseInterviewSseData(rawData) {
  if (typeof rawData !== 'string' || !rawData.trim()) return null

  let payload
  try {
    payload = JSON.parse(rawData)
  } catch {
    return null
  }
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return null

  if (payload.type === 'orchestration') {
    const orchestration = normalizeOrchestrationEvent(payload)
    return orchestration
      ? { kind: 'orchestration', data: orchestration }
      : { kind: 'unknown', data: null }
  }
  if (payload.error) return { kind: 'error', data: String(payload.error) }
  if (payload.phase) return { kind: 'phase', data: payload.phase }
  if (payload.done === true || payload.done === 'true') return { kind: 'done', data: true }
  if (payload.content !== undefined && payload.content !== null) {
    return { kind: 'content', data: payload.content }
  }
  return { kind: 'unknown', data: null }
}

export function getOrchestrationModePresentation(mode) {
  return MODE_PRESENTATION[mode] || { label: '策略', type: 'info' }
}

export function getOrchestrationActionLabel(action) {
  return ACTION_LABELS[action] || '继续面试'
}

export function getOrchestrationFallbackLabel(mode, fallbackCategory) {
  if (mode !== 'RULE_FALLBACK' || !FALLBACK_CATEGORIES.has(fallbackCategory)) return ''
  return FALLBACK_CATEGORY_LABELS[fallbackCategory]
}

export function getFriendlyToolLabels(tools) {
  if (!Array.isArray(tools)) return []
  return [...new Set(tools
    .filter((tool) => typeof tool === 'string' && tool.trim())
    .map((tool) => TOOL_LABELS[tool] || '只读工具'))]
}
