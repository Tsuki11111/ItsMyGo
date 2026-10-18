import { get, postForm, put } from './http'
import type { UserProfile } from './types'

export function fetchProfile(): Promise<UserProfile> {
  return get<UserProfile>('/user/profile')
}

export interface UpdateProfilePayload {
  nickname: string
  phone: string
}

export function updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
  return put<UserProfile>('/user/profile', payload)
}

/** 后端会校验旧密码；成功后前端应主动登出、让用户用新密码重新登录 */
export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return put<void>('/user/password', { oldPassword, newPassword })
}

export function uploadAvatar(file: File): Promise<UserProfile> {
  const form = new FormData()
  form.append('file', file)
  return postForm<UserProfile>('/user/avatar', form)
}
