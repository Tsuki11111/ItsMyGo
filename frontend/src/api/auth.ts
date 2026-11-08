import { post, get } from './http'
import type { AuthResult, PermissionView } from './types'

export function login(username: string, password: string): Promise<AuthResult> {
  return post<AuthResult>('/auth/login', { username, password })
}

export function register(username: string, password: string, nickname?: string): Promise<AuthResult> {
  return post<AuthResult>('/auth/register', { username, password, nickname })
}

/** 当前管理员的子角色与权限码（非管理员返回空） */
export function fetchPermissions(): Promise<PermissionView> {
  return get<PermissionView>('/auth/permissions', undefined, 10000)
}
