'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Activity, ArrowUpRight, Building2, Cable, ChevronDown, ChevronsUpDown, GitBranch, LayoutDashboard, LogOut, Settings2, UserRound, Users } from 'lucide-react';
import { activeWorkspaceRoute, pageTitle, workspaceNavigation } from '@/lib/navigation';
import { signOut } from '@/lib/auth';
import { useWorkspaceSession } from './WorkspaceSession';

const icons = [LayoutDashboard, Activity, Cable, Users, Settings2];
export function WorkspaceShell({ children }: { children: ReactNode }) {
  const user = useWorkspaceSession(), pathname = usePathname();
  const [menu, setMenu] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(false);
  const menuRoot = useRef<HTMLDivElement>(null);
  const role = user.roles.includes('ROLE_ADMIN') ? 'Administrator' : user.roles.includes('ROLE_SRE') ? 'SRE' : 'Viewer';
  useEffect(() => { setMenu(false); }, [pathname]);
  useEffect(() => {
    if (!menu) return;
    const close = (event: PointerEvent) => { if (!menuRoot.current?.contains(event.target as Node)) setMenu(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenu(false); };
    document.addEventListener('pointerdown', close); document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', escape); };
  }, [menu]);
  const logout = async () => {
    setBusy(true); setError(false);
    try { await signOut(); window.location.assign('/login'); }
    catch { setError(true); setBusy(false); }
  };
  return <div className="product-shell">
    <a href="#workspace-content" className="product-skip">Skip to content</a>
    <aside className="product-sidebar">
      <Link href={user.workspaceId ? '/' : '/workspaces'} className="product-brand"><span><GitBranch size={22} aria-hidden="true" /></span>RootTrace<span className="product-brand-dot">.</span></Link>
      <Link href="/workspaces" className="product-workspace-switch"><Building2 size={19} aria-hidden="true" /><span><strong>{user.workspaceName || 'Choose a workspace'}</strong><small>{user.workspaceId ? role : 'Select where you want to work'}</small></span><ChevronsUpDown size={15} aria-hidden="true" /></Link>
      <p className="product-nav-label">WORKSPACE</p>
      <nav className="product-navigation" aria-label="Workspace navigation">{workspaceNavigation.map((item, index) => {
        const Icon = icons[index], active = activeWorkspaceRoute(pathname) === item.href;
        return <Link key={item.href} href={user.workspaceId ? item.href : '/workspaces'} className={active ? 'product-nav-item active' : 'product-nav-item'} aria-current={active ? 'page' : undefined}><Icon size={18} aria-hidden="true" />{item.label}</Link>;
      })}</nav>
      <div className="product-sidebar-bottom">
        {user.workspaceId && <Link className="product-start-link" href="/integrations"><span>Get your project connected</span><ArrowUpRight size={15} aria-hidden="true" /></Link>}
        <div className="product-profile" ref={menuRoot}>
          <button className="product-profile-trigger" onClick={() => setMenu(value => !value)} aria-label="Your account" aria-expanded={menu} aria-controls="account-menu"><span className="product-avatar">{(user.fullName || user.email).slice(0,1).toUpperCase()}</span><span><strong>{user.fullName || 'Your account'}</strong><small>Your account</small></span><ChevronDown size={15} aria-hidden="true" /></button>
          {menu && <div id="account-menu" className="product-account-menu"><p className="product-menu-email">{user.email}</p><Link href="/account"><UserRound size={16} aria-hidden="true" />Account settings</Link><Link href="/workspaces"><Building2 size={16} aria-hidden="true" />Switch workspace</Link><button onClick={() => void logout()} disabled={busy}><LogOut size={16} aria-hidden="true" />{busy ? 'Signing out…' : 'Sign out'}</button>{error && <p role="alert">Sign-out could not be confirmed. Try again.</p>}</div>}
        </div>
      </div>
    </aside>
    <div className="product-workspace">
      <header className="product-topbar"><div><span>{user.workspaceName || 'Your account'}</span><span aria-hidden="true">/</span><strong>{pageTitle(pathname)}</strong></div>{user.workspaceId && <Link href="/integrations" className="product-topbar-help">Connection guide<ArrowUpRight size={14} aria-hidden="true" /></Link>}</header>
      <main id="workspace-content" className="product-main" tabIndex={-1}>{children}</main>
      <footer className="product-footer">RootTrace <span>Trace the cause. Own the response.</span></footer>
    </div>
  </div>;
}
