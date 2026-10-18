import { del, get, post, put } from './http'
import type { Address, AddressPayload } from './types'

export function fetchAddresses(): Promise<Address[]> {
  return get<Address[]>('/addresses')
}

export function createAddress(payload: AddressPayload): Promise<Address> {
  return post<Address>('/addresses', payload)
}

export function updateAddress(id: number, payload: AddressPayload): Promise<Address> {
  return put<Address>(`/addresses/${id}`, payload)
}

export function removeAddress(id: number): Promise<void> {
  return del<void>(`/addresses/${id}`)
}

/** 单独的「设默认」接口：不用回传全部字段，也不会拿旧快照覆盖别的修改 */
export function setDefaultAddress(id: number): Promise<void> {
  return put<void>(`/addresses/${id}/default`)
}

