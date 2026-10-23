import { NavLink } from 'react-router';
import { Icon } from './Icon';
import styles from './Navigation.module.css';

const items = [
  { to: '/customer', label: 'Tổng quan', icon: 'overview' },
  { to: '/customer/transfer', label: 'Chuyển tiền', icon: 'transfer' },
  { to: '/customer/history', label: 'Lịch sử', icon: 'history' },
] as const;

export function Navigation() {
  return <nav aria-label="Điều hướng khách hàng" className={styles.navigation}>
    <p className={styles.label}>KHÔNG GIAN KHÁCH HÀNG</p>
    <ul>{items.map(({ to, label, icon }) => <li key={to}>
      <NavLink to={to} end className={({ isActive }) => `${styles.link} ${isActive ? styles.active : ''}`}>
        <Icon name={icon} /><span>{label}</span>
      </NavLink>
    </li>)}</ul>
  </nav>;
}
