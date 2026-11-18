'use client';
import { useCallback, useEffect, useState } from 'react';
import { acceptWorkspaceInvitation, listMyInvitations, WorkspaceInvitation } from '@/lib/api';

export function InvitationInbox({ emailVerified,onAccepted }: { emailVerified: boolean; onAccepted: () => void }) {
  const [items,setItems]=useState<WorkspaceInvitation[]>([]),[next,setNext]=useState<string | null>(null),[busy,setBusy]=useState(false);
  const [error,setError]=useState<string | null>(null),[notice,setNotice]=useState<string | null>(null);
  const load=useCallback(async (before?: string) => {
    if(!emailVerified) return; setBusy(true); setError(null);
    try { const page=await listMyInvitations(before); setItems(current => before ? [...current,...page.items] : page.items); setNext(page.nextCursor); }
    catch { setError('Unable to load invitations. Check your verified email and refresh.'); }
    finally { setBusy(false); }
  },[emailVerified]);
  useEffect(() => { if(emailVerified) void load(); else setItems([]); },[emailVerified,load]);
  const accept=async (item: WorkspaceInvitation) => {
    setBusy(true); setError(null); setNotice(null);
    try { const accepted=await acceptWorkspaceInvitation(item.id); setNotice(`Joined ${accepted.workspaceName}. Choose the workspace below to open it.`); await load(); onAccepted(); }
    catch { setError('This invitation may be expired, revoked, or unavailable to this account. Refresh before trying again.'); }
    finally { setBusy(false); }
  };
  return <section className="rounded-2xl border border-slate-800 p-5 space-y-3">
    <h2 className="font-semibold">Workspace invitations</h2>
    {!emailVerified ? <p className="text-sm text-slate-400">Verify your email to review invitations sent to that address.</p> : <>
      {error && <p role="alert" className="text-sm text-rose-300">{error}</p>}{notice && <p role="status" className="text-sm text-emerald-300">{notice}</p>}
      <button type="button" disabled={busy} onClick={() => void load()} className="text-xs text-indigo-300">Refresh invitations</button>
      {items.length===0 && !error && <p className="text-sm text-slate-400">No pending invitations.</p>}
      <ul className="space-y-3">{items.map(item => <li key={item.id} className="rounded-xl border border-slate-700 p-3 space-y-2">
        <p className="break-words font-medium">{item.workspaceName}</p><p className="text-xs text-slate-400">Offered role: {item.role.replace('ROLE_','')} · expires {new Date(item.expiresAt).toLocaleString()}</p>
        <button type="button" disabled={busy} onClick={() => void accept(item)} className="rounded bg-indigo-600 px-3 py-2 text-xs disabled:opacity-50">Accept and join as {item.role.replace('ROLE_','')}</button>
      </li>)}</ul>
      {next && <button disabled={busy} onClick={() => void load(next)} className="text-xs text-indigo-300">Load older invitations</button>}
    </>}
  </section>;
}
