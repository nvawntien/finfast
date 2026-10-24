import { IdentityPage } from '../features/auth/IdentityPage';
import { CustomerGuard } from '../features/auth/SessionProvider';
import { Navigate, Route, Routes } from 'react-router';
import { CustomerLayout } from './layouts/CustomerLayout';
import { PlaceholderPage } from '../components/PlaceholderPage';

export function AppRoutes() {
  return <Routes>
    <Route element={<CustomerLayout />}>
      <Route index element={<Navigate to="/customer" replace />} />
      <Route path="register" element={<IdentityPage key="register" register />} />
      <Route path="login" element={<IdentityPage key="login" />} />
      <Route element={<CustomerGuard />}>
        <Route path="customer" element={<PlaceholderPage title="Tổng quan tài khoản" screen="TỔNG QUAN" description="Không gian tài khoản và giao dịch mô phỏng của bạn." overview />} />
        <Route path="customer/transfer" element={<PlaceholderPage title="Chuyển tiền nội bộ" screen="CHUYỂN TIỀN" description="Chức năng chuyển tiền chưa khả dụng trong giai đoạn này." />} />
        <Route path="customer/history" element={<PlaceholderPage title="Lịch sử giao dịch" screen="LỊCH SỬ" description="Dữ liệu lịch sử chưa được kết nối. Không có giao dịch nào được hiển thị." />} />
        <Route path="customer/receipts/:reference" element={<PlaceholderPage title="Biên nhận giao dịch" screen="BIÊN NHẬN" description="Đường dẫn này chưa tra cứu hoặc xác nhận bất kỳ giao dịch nào." />} />
      </Route>
      <Route path="*" element={<PlaceholderPage title="404 — Không tìm thấy trang" screen="KHÔNG TÌM THẤY" description="Đường dẫn không tồn tại. Bạn có thể trở về tổng quan để tiếp tục." />} />
    </Route>
  </Routes>;
}
