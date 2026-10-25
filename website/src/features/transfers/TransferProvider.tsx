import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { api } from '../../api';
import { useSession } from '../auth/SessionProvider';
import { createTransferService } from './service';
import { createTransferStore } from './transferStore';
import { createBrowserRecoveryVault } from './recoveryVault';

const Context = createContext<ReturnType<typeof createTransferStore> | null>(null);
/** Above routes/guards: preserve unresolved owner-scoped intents across navigation/revalidation. */
export function TransferProvider({ children }: { children: ReactNode }) {
  const { store: session } = useSession();
  const [store] = useState(() => createTransferStore(session, createTransferService(api), undefined,
    createBrowserRecoveryVault(import.meta.env.DEV && import.meta.env.MODE === 'mock' ? 'simulation' : 'live')));
  useEffect(() => {
    const disconnect = store.connect();
    // Only invalidate across tabs; never broadcast principal, payload, credentials or outcomes.
    const channel = typeof BroadcastChannel === 'undefined' ? undefined : new BroadcastChannel('finfast-session-invalidation-v1');
    // An auth completion can arrive while a pre-rotation session GET is pending.
    // Follow that deduplicated request with a fresh check rather than trusting its cookie era.
    if (channel) channel.onmessage = () => { void session.refresh().then(() => session.refresh()); };
    let identityMutation = false;
    const stopSession = session.subscribe(() => {
      const status = session.getSnapshot().status;
      if (status === 'signing-in' || status === 'signing-out') { identityMutation = true; channel?.postMessage('invalidate'); }
      else if (identityMutation) { identityMutation = false; channel?.postMessage('invalidate'); }
    });
    const focus = () => { void session.refresh(); };
    window.addEventListener('focus', focus);
    return () => { window.removeEventListener('focus', focus); stopSession(); channel?.close(); disconnect(); };
  }, [store]);
  return <Context.Provider value={store}>{children}</Context.Provider>;
}
export function useTransfer() {
  const store = useContext(Context);
  if (!store) throw new Error('TransferProvider is required');
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot);
  return { store, state };
}
