import { post } from './http'
import type { AuthResult } from './types'

export function login(username: string, password: string): Promise<AuthResult> {
  return post<AuthResult>('/auth/login', { username, password })
}

export function register(username: string, password: string, nickname?: string): Promise<AuthResult> {
  return post<AuthResult>('/auth/register', { username, password, nickname })
}
