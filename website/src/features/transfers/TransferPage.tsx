import { useEffect, useRef, useState, useSyncExternalStore, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api } from '../../api';
import { formatMoneyVnd } from '../../lib/money';
import { useSession } from '../auth/SessionProvider';
import { createAccountStore } from '../account/accountStore';
import { createAccountService, type AccountOverview } from '../account/service';
import { emptyTransferFields, reviewTransfer, type DraftErrors, type TransferDraft } from './draft';
import styles from './TransferPage.module.css';
import { useTransfer } from './TransferProvider';

export function TransferPage() {
  const { store: session } = useSession();
  const transfer = useTransfer();
  const [store] = useState(() => createAccountStore(session, createAccountService(api)));
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot);
  useEffect(() => store.connect(), [store]);
  useEffect(() => { document.title = 'Chuyển tiền nội bộ | FinFast'; }, []);
  useEffect(() => { if (transfer.state.phase === 'settled' && transfer.state.result.outcome === 'COMPLETED') void store.reload(); }, [transfer.state, store]);
  return <section className={styles.page}>
    <header><p className={styles.eyebrow}>CHUYỂN TIỀN NỘI BỘ</p><h1>Chuyển tiền nội bộ</h1><p>Nhập thông tin và kiểm tra lại trước khi chuyển tiền.</p></header>
    <aside className={styles.notice} aria-label="Lưu ý chuyển tiền"><strong>Chỉ chuyển tiền mô phỏng.</strong><p>Kiểm tra kỹ thông tin trước khi gửi. Nếu chưa rõ kết quả, giữ yêu cầu gốc và tra cứu; không tạo giao dịch thay thế.</p></aside>
    {transfer.state.phase !== 'idle' ? <TransferProgress onNew={() => { void transfer.store.newIntent().then(() => store.reload()); }} /> : <>
    {state.status === 'loading' && <p role="status" className={styles.card}>Đang tải tài khoản nguồn…</p>}
    {state.status === 'error' && <section className={styles.card}><p role="alert">{state.reason === 'forbidden' ? 'Bạn không có quyền đọc tài khoản nguồn.' : state.reason === 'server' ? 'Dịch vụ tài khoản đang gặp sự cố. Vui lòng thử lại sau.' : state.reason === 'network' ? 'Không thể kết nối để tải tài khoản nguồn.' : state.reason === 'timeout' ? 'Yêu cầu tải tài khoản nguồn đã hết thời gian chờ.' : 'Thông tin tài khoản nguồn chưa hợp lệ.'} Chưa thể kiểm tra thông tin chuyển tiền.</p><button type="button" onClick={() => void store.reload()}>Thử tải lại</button></section>}
    {state.status === 'ready' && (state.account.status === 'FROZEN'
      ? <section className={styles.card}><h2>Tài khoản đang bị phong tỏa</h2><p role="status">Không thể chuẩn bị chuyển tiền từ tài khoản FROZEN. Quyền xem số dư, lịch sử và biên nhận vẫn được giữ.</p><Link to="/customer">Xem tài khoản</Link> · <Link to="/customer/history">Xem lịch sử</Link></section>
      : <TransferForm key={`${session.getScope()}:${state.account.accountNumber}`} account={state.account} />)}</>}
  </section>;
}

function TransferProgress({ onNew }: { onNew: () => void }) {
  const { state, store } = useTransfer(); const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, [state]);
  if (state.phase === 'idle') return null;
  if (state.phase === 'loading') return <p role="status" className={styles.card}>Đang kiểm tra yêu cầu cần khôi phục…</p>;
  if (state.phase === 'blocked') return <section className={styles.card}><p role="alert">Không thể đọc dữ liệu khôi phục an toàn. Không tạo giao dịch thay thế hoặc xóa dữ liệu trình duyệt. Hãy kiểm tra quyền lưu trữ và liên hệ hỗ trợ nếu vẫn bị chặn.</p><button type="button" onClick={() => void store.reload()}>Kiểm tra lại dữ liệu khôi phục</button></section>;
  const result = state.phase === 'settled' ? state.result : undefined;
  const final = result?.outcome === 'COMPLETED' || result?.outcome === 'REJECTED';
  const title = !result ? state.phase === 'resolving' ? 'Đang tra cứu kết quả' : 'Đang gửi yêu cầu' : result.outcome === 'COMPLETED' ? 'Chuyển tiền đã hoàn tất' : result.outcome === 'REJECTED' ? 'Yêu cầu đã bị từ chối' : result.outcome === 'PROCESSING' ? 'Yêu cầu đang xử lý' : 'Chưa xác định kết quả';
  return <section className={styles.card} aria-label="Kết quả yêu cầu chuyển tiền"><h2 ref={heading} tabIndex={-1}>{title}</h2>
    <p role="status">{!result ? 'Đang chờ phản hồi từ máy chủ. Không gửi yêu cầu khác.' : `Trạng thái: ${result.outcome}`}</p>
    <p className={styles.money}>{formatMoneyVnd(state.intent.payload.amountVnd)}</p>
    <p>Tài khoản nhận: <span className={styles.exact}>{state.intent.payload.recipientAccountNumber}</span></p>
    {result?.outcome === 'COMPLETED' && <><p>Mã giao dịch: {result.reference}</p><Link to={`/customer/receipts/${encodeURIComponent(result.reference)}`}>Xem biên nhận</Link> · <Link to="/customer/history">Xem lịch sử</Link></>}
    {result?.outcome === 'REJECTED' && <><p role="alert">{result.message} ({result.code})</p><p>Máy chủ xác nhận từ chối cuối cùng, không ghi nhận chuyển tiền cho yêu cầu này. Nếu muốn thử một giao dịch mới, hãy chủ động tạo yêu cầu mới.</p></>}
    {result && !final && <>
      {result.outcome === 'UNKNOWN' && result.reason === 'conflict' && <p role="alert">Xung đột khóa yêu cầu (409). Kết quả của yêu cầu gốc không bị thay thế.</p>}
      <p>Chưa có xác nhận hoàn tất hoặc từ chối cuối cùng. Giữ yêu cầu và khóa gốc; không tạo giao dịch thay thế.</p>
      <p>Tra cứu chỉ kiểm tra kết quả của yêu cầu gốc, không gửi lại lệnh chuyển tiền. Không xóa dữ liệu trình duyệt khi chưa rõ kết quả.</p>
      {result.outcome === 'UNKNOWN' && result.reason === 'busy' && <p role="alert">Một tab khác đang xử lý yêu cầu. Chờ tab đó hoàn tất rồi tra cứu lại.</p>}
      {result.outcome === 'UNKNOWN' && result.reason === 'storage' && <p role="alert">Không thể xác minh lưu trữ khôi phục. Giữ trang mở và kiểm tra quyền lưu trữ; không tạo yêu cầu mới.</p>}
      {result.outcome === 'UNKNOWN' && result.reason === 'forbidden' && <p role="alert">Máy chủ chưa cho phép tra cứu trong phiên hiện tại. Kết quả giao dịch vẫn chưa xác định.</p>}
      <button type="button" onClick={() => void store.resolve()}>Tra cứu kết quả</button>
    </>}
    {final && <div className={styles.actions}><button type="button" onClick={onNew}>Tạo yêu cầu mới</button></div>}
  </section>;
}

function TransferForm({ account }: { account: AccountOverview }) {
  const { store: session } = useSession(); const transfer = useTransfer();
  const scope = useRef(session.getScope());
  const [submitError, setSubmitError] = useState('');
  const [fields, setFields] = useState(emptyTransferFields);
  const [errors, setErrors] = useState<DraftErrors>({});
  const [draft, setDraft] = useState<TransferDraft>();
  const summary = useRef<HTMLDivElement>(null); const confirmation = useRef<HTMLHeadingElement>(null);
  const recipient = useRef<HTMLInputElement>(null); const editing = useRef(false);
  useEffect(() => { if (Object.keys(errors).length) summary.current?.focus(); }, [errors]);
  useEffect(() => { if (draft) confirmation.current?.focus(); else if (editing.current) { recipient.current?.focus(); editing.current = false; } }, [draft]);
  function preview(event: FormEvent) {
    event.preventDefault();
    const result = reviewTransfer(fields, account);
    if (!result.ok) { setErrors(result.errors); return; }
    setErrors({}); setDraft(result.draft);
  }
  return <div className={styles.grid}>
    <aside className={styles.source} aria-label="Tài khoản nguồn"><h2>Tài khoản của bạn</h2><p>{account.accountNumber}</p><p>Số dư khả dụng</p><strong className={styles.balance}>{formatMoneyVnd(account.availableBalanceVnd)}</strong><p>Trạng thái ACTIVE · VND</p><p>Số dư theo lần tải gần nhất. Điều kiện chuyển tiền phải được máy chủ kiểm tra khi gửi.</p></aside>
    <section className={styles.card} aria-labelledby={draft ? 'transfer-review' : 'transfer-input'}>
      {draft ? <>
        <h2 id="transfer-review" ref={confirmation} tabIndex={-1}>Kiểm tra thông tin chuyển tiền</h2>
        <p>Đây là bản xem trước. Chưa có lệnh chuyển tiền nào được gửi hoặc xác nhận hoàn tất.</p>
        <dl className={styles.facts}><div><dt>Tài khoản nhận</dt><dd className={styles.exact}>{draft.recipientAccountNumber}</dd></div><div><dt>Số tiền</dt><dd className={styles.money}>{formatMoneyVnd(draft.amountVnd)}</dd></div>
          <div><dt>Ghi chú</dt><dd className={styles.exact}>{draft.note === undefined ? 'Không kèm ghi chú' : draft.note === '' ? 'Ghi chú rỗng' : draft.note}</dd></div></dl>
        <p>Chưa xác minh tài khoản nhận có tồn tại hoặc đủ điều kiện nhận tiền.</p>
        <div className={styles.actions}><button type="button" onClick={() => { editing.current = true; setDraft(undefined); }}>Chỉnh sửa thông tin</button><button type="button" onClick={() => {
          try { void transfer.store.submit(draft, scope.current).catch(() => setSubmitError('Chưa thể tạo yêu cầu gửi. Vui lòng kiểm tra lại thông tin.')); }
          catch { setSubmitError('Chưa thể tạo yêu cầu gửi. Vui lòng kiểm tra lại thông tin.'); }
        }}>Gửi chuyển tiền</button></div>
        {submitError && <p role="alert">{submitError}</p>}
      </> : <form onSubmit={preview} noValidate>
        <h2 id="transfer-input">Thông tin người nhận</h2>
        {Object.keys(errors).length > 0 && <div ref={summary} role="alert" tabIndex={-1} className={styles.errors}><p>Vui lòng kiểm tra thông tin sau:</p><ul>{Object.entries(errors).map(([field, message]) => <li key={field}>{field === 'account' ? message : <a href={`#transfer-${field}`} onClick={event => { event.preventDefault(); document.getElementById(`transfer-${field}`)?.focus(); }}>{message}</a>}</li>)}</ul></div>}
        <div className={styles.field}><label htmlFor="transfer-recipientAccountNumber">Số tài khoản nhận (bắt buộc)</label><input ref={recipient} id="transfer-recipientAccountNumber" name="recipientAccountNumber" type="text" autoComplete="off" autoCapitalize="none" spellCheck={false} required
          value={fields.recipientAccountNumber} onChange={event => setFields(current => ({ ...current, recipientAccountNumber: event.target.value }))} aria-invalid={!!errors.recipientAccountNumber} aria-describedby={`recipient-hint${errors.recipientAccountNumber ? ' recipient-error' : ''}`} />
          <p id="recipient-hint" className={styles.hint}>Nhập số tài khoản FinFast của người nhận.</p>{errors.recipientAccountNumber && <p id="recipient-error" className={styles.fieldError}>{errors.recipientAccountNumber}</p>}</div>
        <div className={styles.field}><label htmlFor="transfer-amountVnd">Số tiền VND (bắt buộc)</label><input id="transfer-amountVnd" name="amountVnd" type="text" inputMode="numeric" autoComplete="off" required
          value={fields.amountVnd} onChange={event => setFields(current => ({ ...current, amountVnd: event.target.value }))} aria-invalid={!!errors.amountVnd} aria-describedby={`amount-hint${errors.amountVnd ? ' amount-error' : ''}`} />
          <p id="amount-hint" className={styles.hint}>Số nguyên lớn hơn 0, chỉ nhập chữ số. Ví dụ: 200000.</p>{errors.amountVnd && <p id="amount-error" className={styles.fieldError}>{errors.amountVnd}</p>}</div>
        <label className={styles.check}><input type="checkbox" checked={fields.includeNote} onChange={event => setFields(current => ({ ...current, includeNote: event.target.checked }))} />Kèm ghi chú (không bắt buộc)</label>
        {fields.includeNote && <div className={styles.field}><label htmlFor="transfer-note">Ghi chú</label><textarea id="transfer-note" name="note" rows={4} value={fields.note} onChange={event => setFields(current => ({ ...current, note: event.target.value }))} aria-describedby="note-hint" /><p id="note-hint" className={styles.hint}>Nội dung được giữ nguyên, kể cả khoảng trắng. Bỏ chọn để không kèm ghi chú.</p></div>}
        <button type="submit" className={styles.primary}>Kiểm tra thông tin</button>
      </form>}
    </section>
  </div>;
}
