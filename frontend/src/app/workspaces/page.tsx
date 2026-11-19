'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowRight, Building2, Check } from 'lucide-react';
import { listMyWorkspaces, selectWorkspace, WorkspaceMembership } from '@/lib/auth';
import { InvitationInbox } from '@/components/InvitationInbox';
import { PageHeader } from '@/components/workspace/PageHeader';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';

export default function WorkspacesPage() {
  const user = useWorkspaceSession();
  const [workspaces,setWorkspaces] = useState<WorkspaceMembership[]>([]),[loading,setLoading] = useState(true);
  const [selecting,setSelecting] = useState<string | null>(null),[error,setError] = useState<string | null>(null);
  const [inbox,setInbox] = useState(false);
  const refresh = useCallback(async () => {
    setLoading(true); setError(null);
    try { setWorkspaces(await listMyWorkspaces()); }
    catch { setError('Unable to load your workspaces. Try again.'); }
    finally { setLoading(false); }
  },[]);
  useEffect(() => { void refresh(); },[refresh]);
  const choose = async (id: string) => {
    setSelecting(id); setError(null);
    try { await selectWorkspace(id); window.location.assign('/'); }
    catch { setError('Unable to open this workspace. Your access may have changed.'); setSelecting(null); }
  };
  return <div className="ux-page ux-narrow"><PageHeader eyebrow="YOUR WORKSPACES" title="Where would you like to work?" description="Choose a workspace to open its projects and incidents. You can switch at any time." />
    {error && <div className="ux-notice warning" role="alert">{error}<button className="ux-link" onClick={() => void refresh()}>Try again</button></div>}
    {loading ? <div className="ux-card ux-empty" role="status">Loading your workspaces…</div> : workspaces.length ? <ul className="ux-workspace-list">{workspaces.map(workspace => <li key={workspace.workspaceId}><button onClick={() => void choose(workspace.workspaceId)} disabled={selecting !== null}><span className="ux-workspace-icon"><Building2 size={22} aria-hidden="true" /></span><span className="ux-workspace-copy"><strong>{workspace.name}</strong><small>{workspace.role.replace('ROLE_','')}{workspace.workspaceId === user.workspaceId && <span><Check size={12} aria-hidden="true" />Current workspace</span>}</small></span>{selecting === workspace.workspaceId ? <span>Opening…</span> : <ArrowRight size={18} aria-hidden="true" />}</button></li>)}</ul> : !error && <section className="ux-card ux-empty"><Building2 size={28} aria-hidden="true" /><h2>No workspaces yet</h2><p>Ask an administrator for an invitation to your team’s workspace.</p></section>}
    <details className="ux-card ux-disclosure" onToggle={event => setInbox(event.currentTarget.open)}><summary><div><h2>Invited to another workspace?</h2><p>Review invitations sent to your verified email address.</p></div></summary>{inbox && <div className="legacy-panels ux-disclosure-content"><InvitationInbox emailVerified={!!user.emailVerified} onAccepted={() => void refresh()} /></div>}</details>
    {!user.emailVerified && <p className="ux-subtle-note">You can open your own workspace now. <Link href="/account">Verify your email</Link> to accept invitations from other teams.</p>}
  </div>;
}
