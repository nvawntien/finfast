/** Customer subset of docs/03-design/contracts/openapi-v1.yaml. No Admin API. */
export const customerOperations = {
  'GET /auth/csrf': [200],
  'POST /auth/register': [201, 409, 422, 503],
  'POST /auth/login': [200, 401, 503],
  'POST /auth/logout': [200, 401],
  'GET /auth/session': [200, 401],
  // Approved BK-008 amendment 2026-10-26 (develop e3dc6ef), scoped to this GET.
  'GET /accounts/me': [200, 401, 403, 500, 503],
  'POST /transfers': [200, 202, 401, 403, 409, 422, 503],
  'POST /request-outcomes/resolve': [200, 202, 401, 403, 404, 409, 503],
  'GET /accounts/me/history': [200, 401, 403, 422],
  'GET /transactions/{reference}/receipt': [200, 401, 404],
} as const;

export function operationFor(method: string, pathname: string) {
  const path = /^\/transactions\/[^/]+\/receipt$/.test(pathname)
    ? '/transactions/{reference}/receipt' : pathname;
  const operation = `${method} ${path}`;
  if (!(operation in customerOperations)) throw new TypeError('Unsupported Customer API operation.');
  return operation as keyof typeof customerOperations;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
