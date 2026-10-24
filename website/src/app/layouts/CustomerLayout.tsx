import { LogoutButton, useSession } from '../../features/auth/SessionProvider';
import { Link, Outlet, useLocation } from 'react-router';
import { useEffect, useRef } from 'react';
import { Navigation } from '../../components/Navigation';
import { SimulationNotice } from '../../components/SimulationNotice';
import styles from './CustomerLayout.module.css';

export function CustomerLayout() {
  const { status, session } = useSession();
  const customer = status === 'authenticated' && session?.role === 'CUSTOMER';
  const { pathname } = useLocation();
  const main = useRef<HTMLElement>(null);
  const previousPath = useRef(pathname);

  useEffect(() => {
    if (previousPath.current !== pathname) {
      main.current?.focus();
      window.scrollTo(0, 0);
      previousPath.current = pathname;
    }
  }, [pathname]);

  return <div className={styles.shell}>
    <a className={styles.skip} href="#main-content">Bỏ qua điều hướng</a>
    <header className={styles.header}>
      <Link to="/customer" className={styles.brand} aria-label="FinFast — Tổng quan"><span className={styles.mark} aria-hidden="true">F</span>FinFast<span className={styles.brandDot} aria-hidden="true">.</span></Link>
      <nav aria-label="Truy cập" className={styles.access}>{status === 'authenticated' || status === 'signing-out' ? <LogoutButton /> : <><Link to="/login">Đăng nhập</Link><Link to="/register">Đăng ký</Link>{status === 'signing-in' && <LogoutButton />}</>}</nav>
    </header>
    <div className={`${styles.body} ${customer ? '' : styles.publicBody}`}>
      {customer && <div className={styles.sidebar}><Navigation /><p className={styles.sidebarNote}>Một không gian để trải nghiệm ngân hàng mô phỏng.</p></div>}
      <main id="main-content" ref={main} tabIndex={-1} className={styles.main}>
        <SimulationNotice />
        <Outlet />
        <footer className={styles.footer}>FinFast · Customer Web <span>Môi trường mô phỏng · Không sử dụng tiền thật</span></footer>
      </main>
    </div>
  </div>;
}
