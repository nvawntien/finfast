import { createHttpClient } from './client';

/** Same-origin integration only; no guessed backend host or permissive CORS proxy. */
export const api = createHttpClient({ baseUrl: `${window.location.origin}/api/v1` });
export { ApiError } from './errors';
