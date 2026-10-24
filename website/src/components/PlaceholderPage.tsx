import { useEffect } from 'react';
import { Link } from 'react-router';
import { Icon } from './Icon';
import styles from './PlaceholderPage.module.css';

type Props = { title: string; description: string; screen: string; overview?: boolean };

export function PlaceholderPage({ title, description, screen, overview = false }: Props) {
  useEffect(() => { document.title = `${title} | FinFast`; }, [title]);
  return <>
    <section className={styles.heading} aria-labelledby="page-title">
      <p className={styles.eyebrow}>CUSTOMER WEB <span aria-hidden="true">/</span> {screen}</p>
      <h1 id="page-title">{title}</h1><p className={styles.description}>{description}</p>
    </section>
    <section className={styles.placeholder} aria-labelledby="placeholder-title">
      <div className={styles.symbol}><Icon name="document" /></div>
      <div><p className={styles.badge}>Đang xây dựng</p><h2 id="placeholder-title">Màn hình chưa được triển khai</h2>
        <p>Đây là placeholder của giai đoạn nền tảng. Chưa có dữ liệu tài khoản hoặc thao tác giao dịch.</p>
        {!overview && <Link className={styles.back} to="/customer">Về tổng quan <span aria-hidden="true">→</span></Link>}
      </div>
    </section>
    {overview && <section className={styles.explore} aria-labelledby="explore-title">
      <h2 id="explore-title">Khám phá giao diện</h2>
      <div className={styles.cards}>
        <Link to="/customer/transfer" className={styles.card}><Icon name="transfer" /><h2>Chuyển tiền</h2><p>Không gian chuyển tiền nội bộ.</p><span>Xem màn hình <span aria-hidden="true">↗</span></span></Link>
        <Link to="/customer/history" className={styles.card}><Icon name="history" /><h2>Lịch sử</h2><p>Không gian tra cứu giao dịch.</p><span>Xem màn hình <span aria-hidden="true">↗</span></span></Link>
      </div>
    </section>}
  </>;
}
