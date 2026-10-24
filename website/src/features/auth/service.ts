import { createHttpClient } from '../../api/client';
import { ApiError } from '../../api/errors';
import { isRecord } from '../../api/contracts';
import type { Credentials, Registration } from './validation';

export type Session = { userId: string; role: 'CUSTOMER' | 'ADMIN'; displayName?: string };
export function decodeSession(data: unknown): Session {
  if (!isRecord(data) || typeof data.userId !== 'string'
    || (data.role !== 'CUSTOMER' && data.role !== 'ADMIN')
    || (data.displayName !== undefined && typeof data.displayName !== 'string')
    || Object.keys(data).some(key => !['userId', 'role', 'displayName'].includes(key))) throw new ApiError('protocol');
  return data as Session;
}

export function createIdentityService(client: ReturnType<typeof createHttpClient>) {
  return {
    async session() { return decodeSession((await client.request('/auth/session')).data); },
    async login({ email, password }: Credentials) {
      return decodeSession((await client.request('/auth/login', { method: 'POST', body: { email: email.trim(), password } })).data);
    },
    async register({ displayName, email, password }: Registration, signal?: AbortSignal) {
      const { data } = await client.request('/auth/register', { method: 'POST',
        body: { displayName: displayName.trim(), email: email.trim(), password }, signal });
      if (!isRecord(data) || typeof data.customerId !== 'string' || typeof data.accountNumber !== 'string'
        || data.status !== 'ACTIVE' || data.currency !== 'VND' || data.balanceVnd !== '0'
        || Object.keys(data).some(key => !['customerId', 'accountNumber', 'status', 'currency', 'balanceVnd'].includes(key))) {
        throw new ApiError('protocol', { requestMayHaveBeenSent: true });
      }
    },
    async logout() {
      const { data } = await client.request('/auth/logout', { method: 'POST' });
      if (!isRecord(data) || typeof data.message !== 'string' || Object.keys(data).length !== 1) throw new ApiError('protocol');
    },
  };
}
