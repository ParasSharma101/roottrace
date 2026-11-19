'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { AuthUser, getAuthToken, getAuthUser, getCurrentProfile, setAuthSession, TOKEN_KEY } from '@/lib/auth';

const SessionContext = createContext<AuthUser | null>(null);
export function useWorkspaceSession(): AuthUser {
  const user = useContext(SessionContext);
  if (!user) throw new Error('Account session is not ready');
  return user;
}

export function WorkspaceSession({ children }: { children: ReactNode }) {
  const router = useRouter(), pathname = usePathname();
  const [user, setUser] = useState<AuthUser | null>(null), [error, setError] = useState(false);
  const [ready, setReady] = useState(false);
  const request = useRef(0);
  const refresh = useCallback(async () => {
    const current = ++request.current, token = getAuthToken();
    setError(false);
    if (!token) { setUser(null); setReady(false); router.replace('/login'); return; }
    try {
      const profile = await getCurrentProfile();
      if (current !== request.current || token !== getAuthToken()) return;
      if (!profile.workspaceId && pathname !== '/workspaces' && pathname !== '/account') {
        setReady(false); router.replace('/workspaces'); return;
      }
      setAuthSession(profile); setUser(profile); setReady(true);
    } catch {
      if (current !== request.current) return;
      if (!getAuthToken()) { setUser(null); router.replace('/login'); }
      else setError(true);
    }
  }, [pathname, router]);
  useEffect(() => { void refresh(); return () => { request.current++; }; }, [refresh]);
  useEffect(() => {
    const changed = () => {
      if (!getAuthToken()) { request.current++; setUser(null); setReady(false); router.replace('/login'); }
      else setUser(getAuthUser());
    };
    const crossTab = (event: StorageEvent) => {
      if (event.storageArea === localStorage && (event.key === TOKEN_KEY || event.key === null)) {
        window.location.assign(event.newValue ? '/workspaces' : '/login');
      }
    };
    window.addEventListener('auth-change', changed); window.addEventListener('storage', crossTab);
    return () => { window.removeEventListener('auth-change', changed); window.removeEventListener('storage', crossTab); };
  }, [router]);
  if (error) return <div className="product-session-state"><h1>We couldn’t confirm your session.</h1><p>Check your connection and try again.</p><button className="ux-button" onClick={() => void refresh()}>Try again</button><a className="ux-link" href="/login">Sign in again</a></div>;
  if (!ready || !user) return <div className="product-session-state" role="status">Opening your workspace…</div>;
  return <SessionContext.Provider value={user}><div key={user.workspaceId ?? 'account'}>{children}</div></SessionContext.Provider>;
}
