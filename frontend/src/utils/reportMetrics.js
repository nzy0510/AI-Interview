export function normalizeKnowledgePoints(raw) {
  let points = []
  try {
    points = typeof raw === 'string' ? JSON.parse(raw || '[]') : raw
  } catch {
    points = []
  }

  if (!Array.isArray(points)) return []

  return points
    .filter((item) => item && (item.concept || item.category))
    .map((item) => {
      const mastery = Number(item.mastery)
      const percent = Number.isFinite(mastery)
        ? Math.max(0, Math.min(100, Math.round(mastery * 100)))
        : 0

      return {
        concept: item.concept || '未命名知识点',
        category: item.category || '综合',
        percent,
      }
    })
    .sort((a, b) => b.percent - a.percent)
}
