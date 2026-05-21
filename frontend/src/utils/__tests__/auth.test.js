import { beforeEach, describe, expect, it } from 'vitest'
import { getToken, withAuthHeaders } from '../auth'

describe('auth utils', () => {
  const createStorage = () => {
    const store = new Map()
    return {
      getItem: (key) => store.get(key) ?? null,
      setItem: (key, value) => { store.set(key, String(value)) },
      removeItem: (key) => { store.delete(key) },
      clear: () => { store.clear() }
    }
  }

  beforeEach(() => {
    globalThis.localStorage = createStorage()
    localStorage.clear()
  })

  it('returns an empty string when token is missing', () => {
    expect(getToken()).toBe('')
    expect(withAuthHeaders({ 'X-Test': '1' })).toEqual({ 'X-Test': '1' })
  })

  it('adds the bearer token without dropping existing headers', () => {
    localStorage.setItem('token', 'jwt-token')

    expect(getToken()).toBe('jwt-token')
    expect(withAuthHeaders({ 'Content-Type': 'application/json' })).toEqual({
      'Content-Type': 'application/json',
      Authorization: 'Bearer jwt-token'
    })
  })
})
