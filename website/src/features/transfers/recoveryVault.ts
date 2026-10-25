import { isRecord } from '../../api/contracts';
import { parsePositiveMoneyVnd } from '../../lib/money';
import type { TransferIntent } from './transferStore';

export class RecoveryBusy extends Error {}
export interface RecoveryVault {
  lock<T>(owner: string, work: () => Promise<T>): Promise<T>;
  read(owner: string): Promise<TransferIntent | undefined>;
  write(intent: TransferIntent): Promise<void>;
  remove(owner: string): Promise<void>;
  subscribe(listener: () => void): () => void;
}
const encode = (text: string) => new TextEncoder().encode(text);
async function digest(text: string) {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', encode(text))), byte => byte.toString(16).padStart(2, '0')).join('');
}
/** Browser integrity format, NOT TD-003's server fingerprint (trusted source ID is not exposed). */
export function recoveryCanonical(intent: TransferIntent) {
  return JSON.stringify([intent.owner, 'INTERNAL_TRANSFER', intent.key, intent.payload.recipientAccountNumber,
    intent.payload.amountVnd, Object.hasOwn(intent.payload, 'note') ? ['present', intent.payload.note] : ['absent']]);
}
export const recoveryFingerprint = (intent: TransferIntent) => digest(`finfast-browser-recovery:v1\0${recoveryCanonical(intent)}`);
function validate(value: unknown, owner: string): TransferIntent {
  if (!isRecord(value) || value.owner !== owner || typeof value.key !== 'string' || !value.key || value.key.length > 200 || /[\r\n]/.test(value.key)
    || !isRecord(value.payload) || typeof value.payload.recipientAccountNumber !== 'string' || !value.payload.recipientAccountNumber.trim()
    || Object.keys(value).some(key => !['owner', 'key', 'payload'].includes(key))
    || Object.keys(value.payload).some(key => !['recipientAccountNumber', 'amountVnd', 'note'].includes(key))
    || Object.hasOwn(value.payload, 'note') && typeof value.payload.note !== 'string') throw new Error('Invalid recovery record');
  return Object.freeze({ owner, key: value.key, payload: Object.freeze({ recipientAccountNumber: value.payload.recipientAccountNumber,
    amountVnd: parsePositiveMoneyVnd(value.payload.amountVnd), ...(Object.hasOwn(value.payload, 'note') ? { note: value.payload.note as string } : {}) }) });
}

/** Fail closed if durable storage, Web Crypto or cross-tab Web Locks are unavailable.
 * AES-GCM protects stored payload integrity/confidentiality at rest; same-origin XSS can
 * still use the nonextractable key. This is not authorization or protection from XSS.
 */
export function createBrowserRecoveryVault(mode: string): RecoveryVault {
  const namespace = `finfast-transfer-recovery-v1:${mode}`;
  const requireCapabilities = () => {
    if (!navigator.locks || !crypto.subtle || typeof BroadcastChannel === 'undefined') throw new Error('Recovery unavailable');
  };
  let database: Promise<IDBDatabase> | undefined;
  const db = () => database ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(namespace, 1);
    request.onupgradeneeded = () => request.result.createObjectStore('records');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error('Recovery storage blocked'));
  });
  async function get(key: string): Promise<unknown> {
    const connection = await db();
    return new Promise((resolve, reject) => {
      const transaction = connection.transaction('records', 'readonly');
      const request = transaction.objectStore('records').get(key);
      transaction.oncomplete = () => resolve(request.result);
      transaction.onabort = () => reject(transaction.error);
      transaction.onerror = () => reject(transaction.error);
    });
  }
  async function put(entries: [string, unknown][], remove?: string) {
    const connection = await db();
    return new Promise<void>((resolve, reject) => {
      const transaction = connection.transaction('records', 'readwrite', { durability: 'strict' });
      const records = transaction.objectStore('records');
      for (const [key, value] of entries) records.put(value, key);
      if (remove) records.delete(remove);
      transaction.oncomplete = () => resolve();
      transaction.onabort = () => reject(transaction.error);
      transaction.onerror = () => reject(transaction.error);
    });
  }
  const scope = (owner: string) => digest(`${namespace}\0${owner}`);
  const broadcast = () => { const channel = new BroadcastChannel(namespace); channel.postMessage('changed'); channel.close(); };
  return {
    async lock(owner, work) {
      requireCapabilities();
      return navigator.locks.request(`${namespace}:${await scope(owner)}`, { ifAvailable: true }, async lock => {
        if (!lock) throw new RecoveryBusy();
        return work();
      });
    },
    async read(owner) {
      requireCapabilities(); // No unsafe lease/localStorage fallback or unprotected submission.
      const id = await scope(owner); const record = await get(id);
      if (record === undefined) return undefined;
      if (!isRecord(record) || record.version !== 1 || !(record.iv instanceof Uint8Array) || !(record.ciphertext instanceof ArrayBuffer)
        || !(record.key instanceof CryptoKey)) throw new Error('Unreadable recovery record');
      const bytes = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: record.iv as Uint8Array<ArrayBuffer>, additionalData: encode(id) }, record.key, record.ciphertext);
      const value: unknown = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
      if (!isRecord(value) || typeof value.fingerprint !== 'string') throw new Error('Invalid recovery integrity');
      const intent = validate(value.intent, owner);
      if (value.fingerprint !== await recoveryFingerprint(intent)) throw new Error('Recovery fingerprint mismatch');
      return intent;
    },
    async write(intent) {
      const id = await scope(intent.owner);
      const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const plaintext = JSON.stringify({ intent, fingerprint: await recoveryFingerprint(intent) });
      const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: encode(id) }, key, encode(plaintext));
      // One atomic transaction commits key+ciphertext before any transfer POST.
      await put([[id, { version: 1, key, iv, ciphertext }]]); broadcast();
    },
    async remove(owner) { await put([], await scope(owner)); broadcast(); },
    subscribe(listener) {
      if (typeof BroadcastChannel === 'undefined') return () => undefined;
      const channel = new BroadcastChannel(namespace); channel.onmessage = () => listener();
      return () => channel.close();
    },
  };
}
