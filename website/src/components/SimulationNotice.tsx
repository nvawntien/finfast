import { Icon } from './Icon';
import styles from './SimulationNotice.module.css';

export function SimulationNotice() {
  return <aside aria-label="Thông báo mô phỏng" className={styles.notice}>
    <Icon name="info" />
    <p><strong>Chỉ sử dụng tiền mô phỏng.</strong> FinFast là hệ thống mô phỏng ngân hàng, không thực hiện giao dịch tiền thật.</p>
  </aside>;
}
