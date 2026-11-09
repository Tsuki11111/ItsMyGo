import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearAuth,
  getStoredUser,
  getToken,
  setStoredUser,
  setToken,
} from './http'

// jsdom 环境的 localStorage 在 Node 25 下会被实验性实现遮蔽，用内存实现替代。
const memory = new Map<string, string>()
Object.defineProperty(window, 'localStorage', {
  configurable: true,
  value: {
    getItem: (key: string) => memory.get(key) ?? null,
    setItem: (key: string, value: string) => void memory.set(key, value),
    removeItem: (key: string) => void memory.delete(key),
    clear: () => memory.clear(),
  },
})

describe('auth storage', () => {
  beforeEach(() => memory.clear())

  it('reads back the token it wrote', () => {
    expect(getToken()).toBeNull()
    setToken('jwt-token')
    expect(getToken()).toBe('jwt-token')
  })

  it('reads back the stored user', () => {
    setStoredUser({
      userId: 1,
      username: 'testuser',
      nickname: '测试员小张',
      role: 'CUSTOMER',
    })
    expect(getStoredUser()).toMatchObject({ userId: 1, username: 'testuser' })
  })

  it('treats corrupted user JSON as logged out and removes it', () => {
    window.localStorage.setItem('its-my-go:user', '{broken')
    expect(getStoredUser()).toBeNull()
    expect(window.localStorage.getItem('its-my-go:user')).toBeNull()
  })

  it('clears both token and user', () => {
    setToken('jwt-token')
    setStoredUser({ userId: 1, username: 'u', nickname: 'n', role: 'CUSTOMER' })
    clearAuth()
    expect(getToken()).toBeNull()
    expect(getStoredUser()).toBeNull()
  })
})
