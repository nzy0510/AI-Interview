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

/**
 * 获取缓存的昵称（用于跨页面同步）
 */
export function getNickname() {
  return localStorage.getItem('cached_nickname') || null
}

/**
 * 缓存昵称（Settings 保存后调用）
 */
export function setNickname(nickname) {
  if (nickname) {
    localStorage.setItem('cached_nickname', nickname)
  } else {
    localStorage.removeItem('cached_nickname')
  }
}

/**
 * 清除所有登录态（退出登录时调用）
 */
export function logout() {
  const userId = getUserId()
  localStorage.removeItem('token')
  localStorage.removeItem('cached_nickname')
  if (userId) {
    Object.keys(localStorage).forEach(key => {
      if (key.endsWith(`_${userId}`)) localStorage.removeItem(key)
    })
  }
}
