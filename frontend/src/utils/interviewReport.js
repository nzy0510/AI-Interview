function formatWpm(wpm) {
  return wpm || '—'
}

const INTERVIEW_CONTROL_MARKER_PATTERN = /\[(SWITCH_TO_HR|AUTO_FINISH|TERMINATE)\]/g

const ABILITY_ALIASES = {
  techDepth: ['techDepth', 'technicalDepth', 'tech', 'technical', 'projectDepth'],
  breadth: ['breadth', 'knowledgeBreadth', 'coverage', 'knowledgeCoverage'],
  problemSolving: ['problemSolving', 'problem', 'solution', 'algorithm', 'scenarioReasoning'],
  expression: ['expression', 'communication', 'communicationAbility', 'clarity'],
  logic: ['logic', 'logicalThinking', 'structure'],
  adaptability: ['adaptability', 'resilience', 'pressure', 'stressResistance']
}

export function stripInterviewControlMarkers(text) {
  if (text == null) return ''
  return String(text).replace(INTERVIEW_CONTROL_MARKER_PATTERN, '').trim()
}

export function detectInterviewControlMarkers(text) {
  const source = String(text || '')
  return {
    switchToHr: source.includes('[SWITCH_TO_HR]'),
    autoFinish: source.includes('[AUTO_FINISH]'),
    terminate: source.includes('[TERMINATE]')
  }
}

export function parseStructuredField(value, fallback) {
  if (value == null || value === '') return fallback
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return fallback
    }
  }
  return value
}

export function normalizeAbility(value) {
  const parsed = parseStructuredField(value, {})
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}

  const normalized = {}
  Object.entries(ABILITY_ALIASES).forEach(([targetKey, aliases]) => {
    for (const alias of aliases) {
      if (parsed[alias]) {
        normalized[targetKey] = parsed[alias]
        return
      }
    }
  })
  return { ...parsed, ...normalized }
}

function parseRecommendations(value) {
  const parsed = parseStructuredField(value, [])
  return Array.isArray(parsed) ? parsed : []
}

function parseEmotion(value) {
  const parsed = parseStructuredField(value, null)
  return parsed && typeof parsed === 'object' ? parsed : null
}

function normalizeConfidence(confidence) {
  const numericConfidence = Number(confidence)
  return Number.isFinite(numericConfidence) ? numericConfidence : null
}

function buildConfidenceMetric(confidence, label) {
  const normalizedConfidence = normalizeConfidence(confidence)
  if (normalizedConfidence === null) return null
  return {
    icon: '✨',
    value: (normalizedConfidence * 100).toFixed(0),
    unit: '%',
    label
  }
}

function buildDominantEmotionMetric(dominantEmotion, emotionLabel) {
  if (!dominantEmotion) return null
  return {
    icon: '🎭',
    value: emotionLabel(dominantEmotion),
    label: '主导情绪',
    highlight: true
  }
}

function appendEmotionMetrics(metrics, emotionSource, emotionLabel, confidenceLabel) {
  if (!emotionSource) return metrics

  const confidenceMetric = buildConfidenceMetric(emotionSource.avgConfidence, confidenceLabel)
  const dominantEmotionMetric = buildDominantEmotionMetric(emotionSource.dominantEmotion, emotionLabel)

  if (confidenceMetric) metrics.push(confidenceMetric)
  if (dominantEmotionMetric) metrics.push(dominantEmotionMetric)
  return metrics
}

export function buildTextInterviewReportMetrics({
  wpm,
  voiceRounds,
  totalUserRounds,
  emotion,
  emotionLabel
}) {
  const metrics = [
    { icon: '🎤', value: formatWpm(wpm), unit: 'WPM', label: '平均语速' },
    { icon: '🗣️', value: voiceRounds, label: '语音互动轮次' },
    { icon: '⌨️', value: totalUserRounds, label: '总发信轮次' }
  ]

  return appendEmotionMetrics(metrics, emotion, emotionLabel, '自信指数')
}

export function buildVideoInterviewReportMetrics({
  wpm,
  totalRounds,
  emotionSummary,
  emotionLabel
}) {
  const metrics = [
    { icon: '🎤', value: formatWpm(wpm), unit: 'WPM', label: '平均语速' },
    { icon: '🗣️', value: totalRounds, label: '交流轮次' }
  ]

  return appendEmotionMetrics(metrics, emotionSummary, emotionLabel, '表现自信指数')
}

export function parseInterviewFinishPayload(response) {
  const source = response?.record || response || {}
  return {
    ability: normalizeAbility(source.abilityJson),
    recommendations: parseRecommendations(source.recommendations),
    emotion: parseEmotion(source.emotionJson)
  }
}
