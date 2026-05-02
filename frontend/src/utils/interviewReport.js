function formatWpm(wpm) {
  return wpm || '—'
}

function parseStructuredField(value, fallback) {
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

function parseAbility(value) {
  const parsed = parseStructuredField(value, {})
  return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
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
  return {
    ability: parseAbility(response?.abilityJson),
    recommendations: parseRecommendations(response?.recommendations),
    emotion: parseEmotion(response?.emotionJson)
  }
}
