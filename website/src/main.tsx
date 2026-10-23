import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AppRoutes } from './app/router';
import { prepareEnvironment } from './app/prepareEnvironment';
import './styles/tokens.css';
import './styles/global.css';

prepareEnvironment().then(mockEnabled => createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {mockEnabled && <aside role="status">Chế độ Mock API — dữ liệu giả lập, chưa kết nối backend.</aside>}
    <BrowserRouter><AppRoutes /></BrowserRouter>
  </StrictMode>,
)).catch(() => {
  // Fail closed; never fall back to real APIs when mock initialization fails.
  document.getElementById('root')!.textContent = 'Không thể khởi tạo môi trường API. Hãy kiểm tra cấu hình và tải lại trang.';
});
