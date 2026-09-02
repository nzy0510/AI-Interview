const validPositionId = (value) => {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

export function resolveMentorPosition(positions, requestedPositionId, history, fallbackToFirst = true) {
  const visiblePositions = Array.isArray(positions)
    ? positions.filter(position => validPositionId(position?.id) && String(position?.name || '').trim())
    : []
  if (!visiblePositions.length) return null

  const requestedId = validPositionId(requestedPositionId)
  const requested = requestedId
    ? visiblePositions.find(position => Number(position.id) === requestedId)
    : null
  if (requested) return requested

  const visibleIds = new Set(visiblePositions.map(position => Number(position.id)))
  const recentPositionId = (Array.isArray(history) ? history : [])
    .map(record => validPositionId(record?.positionId))
    .find(positionId => positionId && visibleIds.has(positionId))
  if (recentPositionId) {
    return visiblePositions.find(position => Number(position.id) === recentPositionId)
  }

  return fallbackToFirst ? visiblePositions[0] : null
}
