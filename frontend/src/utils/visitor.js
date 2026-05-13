const ANON_KEY = 'interwise_anonymous_id'

export const getAnonymousId = () => {
  let id = localStorage.getItem(ANON_KEY)
  if (!id) {
    const randomPart = crypto?.randomUUID?.() || `${Date.now()}_${Math.random().toString(16).slice(2)}`
    id = `anon_${randomPart.replaceAll('-', '').slice(0, 32)}`
    localStorage.setItem(ANON_KEY, id)
  }
  return id
}
