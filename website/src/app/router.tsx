import { CustomerDashboard } from '../features/account/CustomerDashboard';
import { HistoryPage, ReceiptPage } from '../features/history/HistoryPages';
import { TransferPage } from '../features/transfers/TransferPage';
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
        <Route path="customer" element={<CustomerDashboard />} />
        <Route path="customer/transfer" element={<TransferPage />} />
        <Route path="customer/history" element={<HistoryPage />} />
        <Route path="customer/receipts/:reference" element={<ReceiptPage />} />
      </Route>
      <Route path="*" element={<PlaceholderPage title="404 — Không tìm thấy trang" screen="KHÔNG TÌM THẤY" description="Đường dẫn không tồn tại. Bạn có thể trở về tổng quan để tiếp tục." />} />
    </Route>
  </Routes>;
}
