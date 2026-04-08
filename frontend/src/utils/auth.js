/**
 * 从 JWT Token 中解码用户信息
 */
export function parseToken(token) {
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    const decoded = atob(payload)
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

/**
 * 获取当前登录用户的 ID
 */
export function getUserId() {
  const token = localStorage.getItem('token')
  const info = parseToken(token)
  return info?.id || null
}

/**
 * 获取当前登录用户名
 */
export function getUsername() {
  const token = localStorage.getItem('token')
  const info = parseToken(token)
  return info?.username || null
}

/**
 * 按用户隔离的 localStorage key
 */
export function userKey(key) {
  const uid = getUserId()
  return uid ? `${key}_${uid}` : key
}
