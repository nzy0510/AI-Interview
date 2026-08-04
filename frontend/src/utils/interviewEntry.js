function parseJsonSafely(value, fallback) {
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

function normalizeFocusAreas(value) {
  const parsed = parseJsonSafely(value, value)
  if (!Array.isArray(parsed)) return []
  return parsed.map((item) => String(item).trim()).filter(Boolean)
}

export function normalizeVisibleInterviewPositions(value) {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    const id = Number(item?.id)
    const name = typeof item?.name === 'string' ? item.name.trim() : ''
    if (!Number.isFinite(id) || id <= 0 || !name) return []
    return [{
      id,
      name,
      scope: item.scope || 'PUBLIC'
    }]
  })
}

export function resolveVisibleInterviewPosition(value, requestedId, requestedName) {
  const positions = normalizeVisibleInterviewPositions(value)
  if (!positions.length) return null

  const numericId = Number(requestedId)
  if (Number.isFinite(numericId) && numericId > 0) {
    const matchedById = positions.find((item) => item.id === numericId)
    if (matchedById) return matchedById
  }

  const normalizedName = typeof requestedName === 'string' ? requestedName.trim() : ''
  return positions.find((item) => item.name === normalizedName) || positions[0]
}

export function parseFocusAreas(queryFocus) {
  if (typeof queryFocus !== 'string' || !queryFocus.trim()) return []
  return queryFocus
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export async function loadTailoredResumeQuestions({
  isTailored,
  positionId,
  storageKey,
  apiBaseUrl,
  token,
  fetchImpl = fetch
}) {
  if (!isTailored) return undefined
  if (!positionId) {
    localStorage.removeItem(storageKey)
    return undefined
  }

  try {
    const resp = await fetchImpl(`${apiBaseUrl || ''}/api/resume/profile?positionId=${encodeURIComponent(positionId)}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!resp.ok) {
      localStorage.removeItem(storageKey)
      return undefined
    }
    const result = await resp.json()
    const analysis = result?.data?.analysis || result?.data
    if (result.code === 200 && analysis?.tailoredQuestions) {
      localStorage.setItem(storageKey, JSON.stringify(analysis))
      return analysis.tailoredQuestions
    }
    localStorage.removeItem(storageKey)
  } catch {}

  return undefined
}

export async function loadInterviewPreferenceFallback({ query, getPreference }) {
  if (query.role || query.focus || query.difficulty) return null

  try {
    const preference = await getPreference()
    if (!preference) return null

    return {
      position: preference.defaultRole || '',
      difficultyLevel: preference.difficultyLevel || '',
      focusAreas: normalizeFocusAreas(preference.focusAreas)
    }
  } catch {
    return null
  }
}
