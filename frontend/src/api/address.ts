import { get, post } from './http'
import type { Address, AddressPayload } from './types'

export function fetchAddresses(): Promise<Address[]> {
  return get<Address[]>('/addresses')
}

export function createAddress(payload: AddressPayload): Promise<Address> {
  return post<Address>('/addresses', payload)
}
