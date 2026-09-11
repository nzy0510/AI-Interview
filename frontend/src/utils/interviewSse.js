export function parseInterviewSseData(rawData) {
  if (typeof rawData !== 'string' || !rawData.trim()) return null

  let payload
  try {
    payload = JSON.parse(rawData)
  } catch {
    return null
  }
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return null

  if (payload.error) return { kind: 'error', data: String(payload.error) }
  if (payload.phase) return { kind: 'phase', data: payload.phase }
  if (payload.done === true || payload.done === 'true') return { kind: 'done', data: true }
  if (payload.content !== undefined && payload.content !== null) {
    return { kind: 'content', data: payload.content }
  }
  return { kind: 'unknown', data: null }
}
