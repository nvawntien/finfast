export type Credentials = { email: string; password: string };
export type Registration = Credentials & { displayName: string };
export type FieldErrors = Partial<Record<keyof Registration, string>>;
/** TD-007: byte capacity, not JS string length. Never normalize or truncate passwords. */
export function exceedsPasswordByteLimit(password: string): boolean {
  return new TextEncoder().encode(password).byteLength > 72;
}

export function validateIdentity(values: Registration, register: boolean): FieldErrors {
  const errors: FieldErrors = {};
  if (register && !values.displayName.trim()) errors.displayName = 'Nhập tên hiển thị.';
  if (!values.email.trim()) errors.email = 'Nhập email.';
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) errors.email = 'Nhập địa chỉ email hợp lệ.';
  if (!values.password) errors.password = 'Nhập mật khẩu.';
  else if (exceedsPasswordByteLimit(values.password)) errors.password = 'Mật khẩu không được vượt quá 72 byte UTF-8; ký tự có dấu có thể dùng nhiều byte.';
  return errors;
}
