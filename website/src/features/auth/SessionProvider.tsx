import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { api } from '../../api';
import { createIdentityService } from './service';
import { createSessionStore } from './sessionStore';

export const identityService = createIdentityService(api);
const Context = createContext<ReturnType<typeof createSessionStore> | null>(null);
export function SessionProvider({ children }: { children: ReactNode }) {
  const [store] = useState(() => createSessionStore(identityService));
  const { pathname } = useLocation();
  useEffect(() => {
    void store.refresh();
    const refresh = () => { if (document.visibilityState === 'visible') void store.refresh(); };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => { window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh); };
  }, [store]);
  useEffect(() => { if (pathname.startsWith('/customer')) void store.refresh(); }, [pathname, store]);
  return <Context.Provider value={store}>{children}</Context.Provider>;
}
export function useSession() {
  const store = useContext(Context);
  if (!store) throw new Error('SessionProvider is required.');
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot);
  return { ...state, store };
}
export function LogoutButton() {
  const { store, status } = useSession();
  return <button type="button" disabled={status === 'signing-out'} onClick={() => void store.logout()}>{status === 'signing-out' ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>;
}
export function IdentityPending() {
  const { status, waitingForLogin } = useSession();
  return <section><h1>{status === 'signing-in' ? 'Đang đăng nhập' : 'Đang đăng xuất'}</h1>
    <p role="status">{status === 'signing-in'
      ? 'Yêu cầu đăng nhập trước vẫn đang xử lý. Chưa thể đăng ký hoặc gửi yêu cầu đăng nhập khác. Bạn có thể chọn Đăng xuất để kết thúc phiên sau khi yêu cầu này hoàn tất.'
      : waitingForLogin ? 'Đang chờ yêu cầu đăng nhập hoàn tất để đăng xuất. Phiên đó sẽ không được mở trong giao diện.' : 'Đang xác nhận đăng xuất với máy chủ. Vui lòng chờ.'}</p></section>;
}
export function SessionProblem() {
  const { store, logoutUncertain } = useSession();
  return <section><h1>Không thể xác minh phiên</h1><p role="alert">{logoutUncertain ? 'Chưa xác nhận được đăng xuất. Phiên trên máy chủ có thể vẫn còn hiệu lực.' : 'Không thể kết nối hoặc phản hồi không hợp lệ. Vui lòng thử lại.'}</p>
    <button type="button" onClick={() => void store.refresh()}>Kiểm tra lại phiên</button> <LogoutButton /></section>;
}
export function CustomerGuard() {
  const { status, session, expired } = useSession();
  const location = useLocation();
  if (status === 'signing-in' || status === 'signing-out') return <IdentityPending />;
  if (status === 'checking') return <p role="status">Đang kiểm tra phiên…</p>;
  if (status === 'error') return <SessionProblem />;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname, expired }} />;
  if (session?.role !== 'CUSTOMER') return <RoleDenied />;
  return <Outlet />;
}
export function RoleDenied() {
  return <section><h1>Không có quyền truy cập Customer Web</h1><p>Tài khoản này không có vai trò CUSTOMER. Giao diện quản trị chưa được triển khai tại đây. Hãy đăng xuất để dùng tài khoản Customer khác.</p></section>;
}
