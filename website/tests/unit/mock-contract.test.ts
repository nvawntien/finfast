import { readFileSync } from 'node:fs';
import { parse } from 'yaml';
import { afterAll, afterEach, beforeAll, expect, it } from 'vitest';
import { setupServer } from 'msw/node';
import { createMockApi } from '../../src/mocks/handlers';
import { mockCredentials } from '../../src/mocks/fixtures';
import { createHttpClient } from '../../src/api/client';
import { customerOperations } from '../../src/api/contracts';

type Schema = { $ref?: string; allOf?: Schema[]; type?: string; required?: string[];
  properties?: Record<string, Schema>; additionalProperties?: boolean; enum?: unknown[];
  items?: Schema; pattern?: string; nullable?: boolean; format?: string };
type ResponseSpec = { content: { 'application/json': { schema: Schema } } };
type OpenApi = { paths: Record<string, Record<string, { responses: Record<string, ResponseSpec> }>>;
  components: { schemas: Record<string, Schema> } };
const spec = parse(readFileSync(new URL('../../../docs/03-design/contracts/openapi-v1.yaml', import.meta.url), 'utf8')) as OpenApi;

function validate(value: unknown, schema: Schema): void {
  if (schema.$ref) return validate(value, spec.components.schemas[schema.$ref.split('/').at(-1)!]);
  if (schema.allOf) { for (const child of schema.allOf) validate(value, child); return; }
  if (value === null && schema.nullable) return;
  if (schema.enum) expect(schema.enum).toContain(value);
  if (schema.type === 'string') {
    expect(typeof value).toBe('string');
    if (schema.pattern) expect(value).toMatch(new RegExp(schema.pattern));
    if (schema.format === 'date-time') expect(Number.isNaN(Date.parse(String(value)))).toBe(false);
  }
  if (schema.type === 'array') {
    expect(Array.isArray(value)).toBe(true);
    for (const item of value as unknown[]) validate(item, schema.items!);
  }
  if (schema.type === 'object') {
    expect(value !== null && typeof value === 'object' && !Array.isArray(value)).toBe(true);
    const record = value as Record<string, unknown>;
    for (const key of schema.required ?? []) expect(record).toHaveProperty(key);
    for (const [key, child] of Object.entries(record)) {
      if (schema.additionalProperties === false) expect(schema.properties).toHaveProperty(key);
      if (schema.properties?.[key]) validate(child, schema.properties[key]);
    }
  }
}

const origin = 'https://mock-contract.test';
const mock = createMockApi(origin);
const server = setupServer(...mock.handlers);
const api = createHttpClient({ baseUrl: `${origin}/api/v1` });
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => { mock.reset(); server.resetHandlers(); });
afterAll(() => server.close());

it('Customer status inventory matches OpenAPI plus the approved BK-008 amendment', () => {
  for (const [operation, statuses] of Object.entries(customerOperations)) {
    const [method, path] = operation.split(' ');
    const documented = Object.keys(spec.paths[`/api/v1${path}`][method.toLowerCase()].responses).map(Number);
    // This branch predates the approved 2026-10-26 amendment in develop e3dc6ef.
    // Keep docs untouched; accept its exact additive statuses only for Account GET.
    // Set also permits running unchanged once the amended OpenAPI is integrated.
    const approved = operation === 'GET /accounts/me' ? [...new Set([...documented, 500, 503])] : documented;
    expect([...statuses].sort()).toEqual(approved.sort());
  }
});

it('all default Customer fixtures match the authoritative response schemas', async () => {
  const cases: [string, string, Parameters<typeof api.request>[1], string?][] = [
    ['GET', '/auth/csrf', {}],
    ['POST', '/auth/register', { method: 'POST', body: { ...mockCredentials, email: 'new@example.test', displayName: 'Mock' } }],
    ['POST', '/auth/login', { method: 'POST', body: mockCredentials }],
    ['GET', '/auth/session', {}],
    ['GET', '/accounts/me', {}],
    ['GET', '/accounts/me/history', {}],
    ['GET', '/transactions/MOCK-TX-001/receipt', {}, '/transactions/{reference}/receipt'],
    ['POST', '/transfers', { method: 'POST', idempotencyKey: 'same-key', body: { recipientAccountNumber: 'MOCK-ACCOUNT-002', amountVnd: '1' } }],
    ['POST', '/request-outcomes/resolve', { method: 'POST', body: { operationType: 'INTERNAL_TRANSFER', idempotencyKey: 'same-key' } }],
    ['POST', '/auth/logout', { method: 'POST' }],
  ];
  for (const [method, path, options, schemaPath] of cases) {
    const response = await api.request(path, options);
    validate(response.data, spec.paths[`/api/v1${schemaPath ?? path}`][method.toLowerCase()].responses[response.status].content['application/json'].schema);
  }
});

it('mock protects reads, rejects wrong credentials and hides unknown receipts', async () => {
  await expect(api.request('/accounts/me')).rejects.toMatchObject({ status: 401 });
  await expect(api.request('/auth/login', { method: 'POST', body: { email: 'wrong@example.test', password: 'wrong' } })).rejects.toMatchObject({ status: 401 });
  await api.request('/auth/login', { method: 'POST', body: mockCredentials });
  await expect(api.request('/transactions/private-reference/receipt')).rejects.toMatchObject({ status: 404 });
  await expect(api.request('/accounts/me/history?limit=101')).rejects.toMatchObject({ status: 422, financialOutcome: undefined });
});

it('mock enforces CSRF on financial commands', async () => {
  await api.request('/auth/login', { method: 'POST', body: mockCredentials });
  const response = await fetch(`${origin}/api/v1/transfers`, { method: 'POST', headers: { 'Idempotency-Key': 'original' } });
  expect(response.status).toBe(403);
  validate(await response.json(), spec.components.schemas.ErrorResponse);
});

it('unhandled API and other origins fail closed instead of reaching a backend', async () => {
  await expect(fetch(`${origin}/api/v1/admin/accounts`)).rejects.toThrow();
  await expect(fetch('https://backend.example.invalid/api/v1/auth/session')).rejects.toThrow();
});

it('mock reset isolates authentication state between tests', async () => {
  await api.request('/auth/login', { method: 'POST', body: mockCredentials });
  mock.reset();
  await expect(api.request('/auth/session')).rejects.toMatchObject({ status: 401 });
});
