import { get, post } from './http'
import type { AdminInvitationView } from './types'

export function createAdminInvitation(username: string): Promise<AdminInvitationView> {
  return post<AdminInvitationView>('/admin/invitations', { username })
}

export function fetchAdminInvitations(status?: string): Promise<AdminInvitationView[]> {
  return get<AdminInvitationView[]>(
    '/admin/invitations',
    status ? { status } : undefined,
  )
}

export function approveAdminInvitation(id: number): Promise<AdminInvitationView> {
  return post<AdminInvitationView>(`/admin/invitations/${id}/approve`)
}

export function revokeAdminInvitation(id: number): Promise<AdminInvitationView> {
  return post<AdminInvitationView>(`/admin/invitations/${id}/revoke`)
}

export function acceptAdminInvitation(token: string): Promise<void> {
  return post<void>('/user/admin-invitation/accept', { token })
}
