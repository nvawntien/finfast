import { Fragment, useEffect, useState, useSyncExternalStore } from 'react';
import { Link } from 'react-router';
import { api } from '../../api';
import { Icon } from '../../components/Icon';
import { formatMoneyVnd, type MoneyVnd } from '../../lib/money';
import { useSession } from '../auth/SessionProvider';
import { createAccountService } from './service';
import { createAccountStore, type AccountState } from './accountStore';
import styles from './CustomerDashboard.module.css';

const errorMessages: Record<Extract<AccountState, { status: 'error' }>['reason'], string> = {
  empty: 'Chưa nhận được thông tin tài khoản hợp lệ. Hãy thử tải lại; không thể xác định số dư từ phản hồi rỗng.',
  protocol: 'Thông tin tài khoản trả về không hợp lệ. Chưa thể hiển thị số dư. Vui lòng thử lại sau.',
  forbidden: 'Bạn không có quyền xem tài khoản này. Không có dữ liệu tài khoản nào được hiển thị.',
  network: 'Không thể kết nối để tải tài khoản. Kiểm tra kết nối rồi thử lại.',
  timeout: 'Yêu cầu tải tài khoản đã hết thời gian chờ. Bạn có thể thử lại.',
};
function MoneyText({ value }: { value: MoneyVnd }) {
  const groups = formatMoneyVnd(value).split('.');
  return <>{groups.map((group, index) => <Fragment key={index}><span className={styles.moneyGroup}>{group}{index < groups.length - 1 ? '.' : ''}</span>{index < groups.length - 1 && <wbr />}</Fragment>)}</>;
}
export function CustomerDashboard() {
  const { store: sessionStore, session } = useSession();
  const [store] = useState(() => createAccountStore(sessionStore, createAccountService(api)));
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot);
  useEffect(() => store.connect(), [store]);
  useEffect(() => { document.title = 'Tổng quan tài khoản | FinFast'; }, []);
  const account = state.status === 'ready' ? state.account : undefined;
  return <>
    <section className={styles.heading} aria-labelledby="dashboard-title">
      <div><p className={styles.eyebrow}>CUSTOMER WEB / TỔNG QUAN</p><h1 id="dashboard-title">Tổng quan tài khoản</h1>
        <p className={styles.subtitle}>{session?.displayName ? `Xin chào, ${session.displayName}.` : 'Chào mừng bạn trở lại.'} Xem thông tin tài khoản VND của bạn.</p></div>
      <button className={styles.refresh} type="button" disabled={state.status === 'loading'} onClick={() => void store.reload()}>
        <Icon name="history" />{state.status === 'loading' ? 'Đang tải…' : state.status === 'error' ? 'Thử lại' : 'Làm mới'}
      </button>
    </section>
    <div className={styles.content}>
      {state.status === 'loading' && <section className={styles.loading}><p role="status">Đang tải thông tin tài khoản…</p><div className={styles.skeleton} aria-hidden="true" /><div className={styles.skeletonShort} aria-hidden="true" /></section>}
      {state.status === 'error' && <section className={styles.error} role="alert"><Icon name="info" /><div><h2>{state.reason === 'empty' ? 'Chưa có dữ liệu tài khoản hợp lệ' : 'Không thể hiển thị tài khoản'}</h2><p>{errorMessages[state.reason]}</p></div></section>}
      {account && <>
        {account.status === 'FROZEN' && <aside className={styles.frozen} aria-label="Tài khoản bị phong tỏa"><Icon name="info" /><p><strong>Tài khoản đang bị phong tỏa.</strong> Bạn vẫn có thể xem số dư và các thông tin được phép. Tài khoản FROZEN không đủ điều kiện chuyển tiền; quyền truy cập được kiểm tra tại máy chủ.</p></aside>}
        <div className={styles.accountGrid}>
          <section className={styles.balance} aria-labelledby="available-title">
            <p className={styles.eyebrow}>TÀI KHOẢN KHÁCH HÀNG</p><h2 id="available-title">Số dư khả dụng</h2>
            <p className={styles.amount} data-testid="available-balance"><MoneyText value={account.availableBalanceVnd} /></p>
            <p className={styles.balanceNote}>{account.balanceVnd === '0' ? 'Tài khoản hiện có số dư 0 VND.' : 'Số dư theo phản hồi mới nhất từ dịch vụ tài khoản.'}</p>
            <div className={styles.balanceTotal}><span>Số dư tài khoản</span><strong data-testid="account-balance"><MoneyText value={account.balanceVnd} /></strong></div>
          </section>
          <section className={styles.details} aria-labelledby="account-details-title"><h2 id="account-details-title">Thông tin tài khoản</h2>
            <dl><div><dt>Số tài khoản</dt><dd data-testid="account-number" className={styles.number}>{account.accountNumber}</dd></div>
              <div><dt>Loại tài khoản</dt><dd>Khách hàng (CUSTOMER)</dd></div>
              <div><dt>Trạng thái</dt><dd><span className={account.status === 'ACTIVE' ? styles.active : styles.frozenBadge}>{account.status === 'ACTIVE' ? 'Đang hoạt động · ACTIVE' : 'Bị phong tỏa · FROZEN'}</span></dd></div>
              <div><dt>Đơn vị tiền tệ</dt><dd>{account.currency}</dd></div></dl>
          </section>
        </div>
        <p className={styles.readNotice}>Số dư khả dụng bằng số dư tài khoản trong FinFast V1. Đây là tiền mô phỏng, không phải tiền thật.</p>
        <section className={styles.next} aria-labelledby="next-title"><h2 id="next-title">Tiếp tục khám phá</h2><div className={styles.links}>
          {account.status === 'ACTIVE' && <Link to="/customer/transfer"><Icon name="transfer" /><span><strong>Mở trang chuyển tiền</strong><small>Chức năng đang được xây dựng</small></span></Link>}
          <Link to="/customer/history"><Icon name="history" /><span><strong>Mở trang lịch sử</strong><small>Xem giao dịch đã ghi nhận và biên nhận</small></span></Link>
        </div></section>
      </>}
    </div>
  </>;
}
