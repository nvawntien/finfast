import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { ApiError } from '../../api/errors';
import { identityService, IdentityPending, RoleDenied, SessionProblem, useSession } from './SessionProvider';
import { IdentityBusyError } from './sessionStore';
import { validateIdentity, type FieldErrors, type Registration } from './validation';
import styles from './IdentityPage.module.css';

const labels = { displayName: 'Tên hiển thị', email: 'Email', password: 'Mật khẩu' };
export function IdentityPage({ register = false }: { register?: boolean }) {
  const { status, session, store, expired } = useSession();
  const location = useLocation();
  const [values, setValues] = useState<Registration>({ displayName: '', email: '', password: '' });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);
  const [success, setSuccess] = useState(false);
  const [visible, setVisible] = useState(false);
  const summary = useRef<HTMLDivElement>(null);
  const busy = useRef(false);
  const mounted = useRef(true);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => { document.title = `${register ? 'Tạo tài khoản' : 'Đăng nhập'} | FinFast`; }, [register]);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; controller.current?.abort(); }; }, []);
  useEffect(() => { if (message || Object.keys(errors).length) summary.current?.focus(); }, [message, errors]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy.current) return;
    const next = validateIdentity(values, register);
    setErrors(next); setMessage('');
    if (Object.keys(next).length) return;
    busy.current = true; setPending(true);
    controller.current = new AbortController();
    try {
      if (register) { await identityService.register(values, controller.current.signal); if (mounted.current) setSuccess(true); }
      else await store.login(values);
    } catch (error) {
      if (mounted.current) {
        if (error instanceof IdentityBusyError) {
          setMessage('Một yêu cầu xác thực khác vẫn đang xử lý. Vui lòng chờ yêu cầu đó hoàn tất.');
        } else if (error instanceof ApiError && error.kind === 'http' && error.status === 409 && register) {
          setErrors({ email: 'Email này đã được sử dụng. Hãy dùng email khác hoặc đăng nhập.' });
        } else if (error instanceof ApiError && error.kind === 'http' && error.status === 401 && !register) {
          setMessage('Email hoặc mật khẩu không đúng. Vui lòng thử lại.');
        } else if (error instanceof ApiError && error.kind === 'http' && error.status === 422) {
          setMessage('Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường.');
        } else {
          setMessage(register ? 'Chưa xác nhận được kết quả đăng ký. Bạn có thể thử đăng nhập trước khi gửi lại.' : 'Không thể xác nhận đăng nhập. Kiểm tra kết nối rồi thử lại.');
        }
      }
    } finally {
      busy.current = false;
      if (mounted.current) { setPending(false); setValues(current => ({ ...current, password: '' })); }
    }
  }
  if (status === 'authenticated') {
    if (session?.role !== 'CUSTOMER') return <RoleDenied />;
    const from: unknown = location.state?.from;
    const target = typeof from === 'string' && /^\/customer(?:\/(?:transfer|history|receipts\/[^/?#]+))?$/.test(from) ? from : '/customer';
    return <Navigate to={target} replace />;
  }
  if (status === 'error') return <SessionProblem />;
  if (status === 'signing-in' || status === 'signing-out') return <IdentityPending />;
  if (status === 'checking') return <p role="status">Đang kiểm tra phiên…</p>;
  return <div className={styles.grid}>
    <section className={styles.intro} aria-label="Giới thiệu FinFast">
      <p className={styles.eyebrow}>FINFAST · CUSTOMER WEB</p>
      <h2>Một khởi đầu rõ ràng.<br />Một trải nghiệm liền mạch.</h2>
      <p>Không gian trải nghiệm ngân hàng mô phỏng dành cho bạn.</p>
      <ul><li>Thông tin tài khoản trong một không gian riêng</li><li>Phiên truy cập được quản lý bởi máy chủ</li><li>Không sử dụng tiền thật</li></ul>
      <p className={styles.caption}>Trải nghiệm có trách nhiệm, từ bước đầu tiên.</p>
    </section>
    <section className={styles.card} aria-labelledby="identity-title">
      <p className={styles.eyebrow}>{register ? 'BẮT ĐẦU' : 'CHÀO MỪNG TRỞ LẠI'}</p>
      <h1 id="identity-title">{register ? 'Tạo tài khoản' : 'Đăng nhập'}</h1>
      <p className={styles.subtitle}>{register ? 'Tạo tài khoản Customer để bắt đầu trải nghiệm FinFast.' : 'Sử dụng email và mật khẩu của bạn để tiếp tục.'}</p>
      {(expired || location.state?.expired) && <p role="status" className={styles.notice}>Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.</p>}
      {success ? <div role="status" className={styles.success}><h2>Đăng ký thành công</h2><p>Tài khoản đã được tạo. Bạn cần đăng nhập riêng để tiếp tục.</p><Link to="/login">Đến trang đăng nhập</Link></div> : <form onSubmit={submit} noValidate aria-busy={pending}>
        {(message || Object.keys(errors).length > 0) && <div ref={summary} tabIndex={-1} role="alert" className={styles.errors}>
          <p>{message || 'Vui lòng kiểm tra thông tin sau:'}</p>
          {Object.entries(errors).length > 0 && <ul>{Object.entries(errors).map(([field, text]) => <li key={field}><a href={`#${field}`} onClick={event => { event.preventDefault(); document.getElementById(field)?.focus(); }}>{text}</a></li>)}</ul>}
        </div>}
        {(['displayName', 'email', 'password'] as const).filter(field => register || field !== 'displayName').map(field => <div className={styles.field} key={field}>
          <label htmlFor={field}>{labels[field]} <span className={styles.required}>(bắt buộc)</span></label>
          <input id={field} name={field} type={field === 'password' ? visible ? 'text' : 'password' : field === 'email' ? 'email' : 'text'}
            autoComplete={field === 'password' ? register ? 'new-password' : 'current-password' : field === 'displayName' ? 'name' : 'username'}
            autoCapitalize={field === 'displayName' ? 'words' : 'none'} spellCheck={false} required readOnly={pending}
            value={values[field]} onChange={event => setValues(current => ({ ...current, [field]: event.target.value }))}
            aria-invalid={!!errors[field]} aria-describedby={errors[field] ? `${field}-error` : undefined} />
          {errors[field] && <p id={`${field}-error`} className={styles.fieldError}>{errors[field]}</p>}
          {field === 'password' && <button className={styles.reveal} type="button" aria-pressed={visible} onClick={() => setVisible(current => !current)}>{visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}</button>}
        </div>)}
        <button className={styles.submit} disabled={pending} type="submit">{pending ? 'Đang xử lý…' : register ? 'Tạo tài khoản' : 'Đăng nhập'}</button>
        {pending && <p role="status">Đang gửi yêu cầu. Vui lòng chờ.</p>}
      </form>}
      <p className={styles.alternate}>{register ? 'Đã có tài khoản? ' : 'Chưa có tài khoản? '}<Link to={register ? '/login' : '/register'}>{register ? 'Đăng nhập' : 'Tạo tài khoản'}</Link></p>
    </section>
  </div>;
}
