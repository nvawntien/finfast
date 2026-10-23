export type ApiErrorKind = 'http' | 'protocol' | 'network' | 'timeout' | 'aborted';

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly code?: string;
  readonly serverMessage?: string;
  readonly financialOutcome?: 'REJECTED' | 'UNKNOWN';
  /** True means the main request MAY have reached the server, not that it committed. */
  readonly requestMayHaveBeenSent: boolean;

  constructor(kind: ApiErrorKind, options: {
    status?: number; code?: string; serverMessage?: string;
    financialOutcome?: 'REJECTED' | 'UNKNOWN'; requestMayHaveBeenSent?: boolean;
  } = {}) {
    super(`API ${kind} error${options.status ? ` (${options.status})` : ''}`);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = options.status;
    this.code = options.code;
    this.serverMessage = options.serverMessage;
    this.financialOutcome = options.financialOutcome;
    this.requestMayHaveBeenSent = options.requestMayHaveBeenSent ?? false;
  }
}
