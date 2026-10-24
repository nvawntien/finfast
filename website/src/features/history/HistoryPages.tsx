import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link, useParams } from 'react-router';
import { api } from '../../api';
import { formatMoneyVnd } from '../../lib/money';
import { useSession } from '../auth/SessionProvider';
import { createHistoryService, type HistoryPage as HistoryData } from './service';
import { createReadStore, type ReadState } from './readStore';
import styles from './HistoryPages.module.css';

const service = createHistoryService(api);
const typeLabel = { INTERNAL_TRANSFER: 'Chuyển tiền nội bộ', DEMO_ALLOCATION: 'Cấp tiền mô phỏng' };
const errors = {
  'not-found': 'Không tìm thấy biên nhận hoặc bạn không có quyền xem biên nhận này.',
  forbidden: 'Bạn không có quyền xem lịch sử này.', cursor: 'Không thể tiếp tục trang này. Hãy tải lại từ trang đầu.',
  protocol: 'Phản hồi không hợp lệ. Không có dữ liệu giao dịch nào được hiển thị.',
  network: 'Không thể kết nối. Kiểm tra kết nối và thử lại.', timeout: 'Yêu cầu đã hết thời gian chờ. Bạn có thể thử lại.',
};
function Feedback<T>({ state, retry }: { state: ReadState<T>; retry: () => void }) {
  if (state.status === 'loading') return <p role="status" className={styles.panel}>Đang tải dữ liệu giao dịch…</p>;
  if (state.status === 'error') return <section className={styles.panel}><p role="alert">{errors[state.reason]}</p><button type="button" onClick={retry}>Thử lại</button></section>;
  return null;
}
function Timestamp({ value }: { value: string }) { return <time dateTime={value}>{new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })} (UTC+7)</time>; }
export function HistoryPage() {
  const { store: session } = useSession();
  const [store] = useState(() => createReadStore<HistoryData, string | undefined>(session, service.history, undefined));
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot);
  const [cursors, setCursors] = useState<(string | undefined)[]>([undefined]);
  const heading = useRef<HTMLHeadingElement>(null); const navigating = useRef(false);
  useEffect(() => store.connect(), [store]);
  useEffect(() => { document.title = 'Lịch sử giao dịch | FinFast'; }, []);
  useEffect(() => { if (state.status !== 'loading' && navigating.current) { heading.current?.focus(); navigating.current = false; } }, [state.status]);
  function navigate(next: (string | undefined)[]) { navigating.current = true; setCursors(next); void store.load(next[next.length - 1]); }
  return <section className={styles.page}>
    <header className={styles.heading}><div><p className={styles.eyebrow}>GIAO DỊCH ĐÃ GHI NHẬN</p><h1 ref={heading} tabIndex={-1}>Lịch sử giao dịch</h1><p>Các giao dịch đã cam kết của tài khoản bạn, theo thứ tự từ mới đến cũ.</p></div>
      <button type="button" disabled={state.status === 'loading'} onClick={() => navigate([undefined])}>Tải lại từ đầu</button></header>
    <Feedback state={state} retry={() => { navigating.current = true; void store.retry(); }} />
    {state.status === 'ready' && <>
      {state.data.items.length === 0 ? <div className={styles.panel}><h2>Chưa có giao dịch</h2><p>Không có giao dịch đã ghi nhận trong trang lịch sử này.</p></div> :
        <ol className={styles.list} aria-label="Giao dịch đã cam kết">{state.data.items.map(item => <li key={item.reference} className={styles.panel}>
          <div className={styles.row}><h2>{typeLabel[item.type]}</h2><strong className={styles.money}>{item.direction === 'INCOMING' ? '+' : '−'}{formatMoneyVnd(item.amountVnd)}</strong></div>
          <p>{item.direction === 'INCOMING' ? 'Tiền vào · Từ' : 'Tiền ra · Đến'} {item.counterpartyLabel}</p>
          <p><Timestamp value={item.committedAt} /></p>
          <Link to={`/customer/receipts/${encodeURIComponent(item.reference)}`}>Xem biên nhận <span className={styles.reference}>{item.reference}</span></Link>
        </li>)}</ol>}
    </>}
    <nav className={styles.paging} aria-label="Phân trang lịch sử">
      <button type="button" disabled={state.status === 'loading' || cursors.length === 1} onClick={() => navigate(cursors.slice(0, -1))}>Trang trước</button>
      <span>Trang {cursors.length}</span>
      <button type="button" disabled={state.status !== 'ready' || state.data.nextCursor === null} onClick={() => { if (state.status === 'ready' && state.data.nextCursor !== null) navigate([...cursors, state.data.nextCursor]); }}>Trang tiếp</button>
    </nav>
  </section>;
}
export function ReceiptPage() {
  const { reference = '' } = useParams();
  return <ReceiptContent key={reference} reference={reference} />;
}
function ReceiptContent({ reference }: { reference: string }) {
  const { store: session } = useSession();
  const [store] = useState(() => createReadStore(session, service.receipt, reference));
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot);
  useEffect(() => store.connect(), [store]);
  useEffect(() => { document.title = 'Biên nhận giao dịch | FinFast'; }, []);
  return <section className={styles.page}>
    <header className={styles.heading}><div><p className={styles.eyebrow}>CHI TIẾT GIAO DỊCH</p><h1>Biên nhận giao dịch</h1><Link to="/customer/history">Về lịch sử giao dịch</Link></div></header>
    <Feedback state={state} retry={() => void store.retry()} />
    {state.status === 'ready' && <article className={styles.panel} aria-label="Biên nhận đã cam kết">
      <p className={styles.completed}>Đã hoàn tất · COMPLETED</p><h2>{typeLabel[state.data.type]}</h2>
      <p className={styles.amount}>{formatMoneyVnd(state.data.amountVnd)}</p>
      <dl className={styles.facts}><div><dt>Mã giao dịch</dt><dd>{state.data.reference}</dd></div>
        <div><dt>Nguồn</dt><dd>{state.data.sourceLabel}</dd></div><div><dt>Đích</dt><dd>{state.data.destinationLabel}</dd></div>
        <div><dt>Đơn vị tiền tệ</dt><dd>{state.data.currency}</dd></div><div><dt>Thời gian ghi nhận</dt><dd><Timestamp value={state.data.committedAt} /></dd></div></dl>
      <p>Biên nhận từ dữ liệu giao dịch đã cam kết. Chỉ sử dụng tiền mô phỏng.</p>
    </article>}
  </section>;
}
