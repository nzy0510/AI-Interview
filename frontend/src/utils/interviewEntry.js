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

export function parseFocusAreas(queryFocus) {
  if (typeof queryFocus !== 'string' || !queryFocus.trim()) return []
  return queryFocus
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export async function loadTailoredResumeQuestions({
  isTailored,
  storageKey,
  apiBaseUrl,
  token,
  fetchImpl = fetch
}) {
  if (!isTailored) return undefined

  try {
    const cached = localStorage.getItem(storageKey)
    if (cached) {
      const parsed = JSON.parse(cached)
      if (parsed && parsed.tailoredQuestions) {
        return parsed.tailoredQuestions
      }
    }
  } catch {}

  try {
    const resp = await fetchImpl(`${apiBaseUrl || ''}/api/resume/profile`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!resp.ok) return undefined
    const result = await resp.json()
    if (result.code === 200 && result.data && result.data.tailoredQuestions) {
      return result.data.tailoredQuestions
    }
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
