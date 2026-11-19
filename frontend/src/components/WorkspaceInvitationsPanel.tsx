'use client';
import { useCallback, useEffect, useState } from 'react';
import { inviteWorkspaceMember, listWorkspaceInvites, revokeWorkspaceInvite, WorkspaceInvitation } from '@/lib/api';

export function WorkspaceInvitationsPanel({ isAdmin, emailVerified, deliveryConfigured }: { isAdmin: boolean; emailVerified: boolean; deliveryConfigured: boolean }) {
  const [items,setItems]=useState<WorkspaceInvitation[]>([]),[next,setNext]=useState<string | null>(null);
  const [email,setEmail]=useState(''),[role,setRole]=useState('ROLE_SRE'),[busy,setBusy]=useState(false);
  const [error,setError]=useState<string | null>(null),[notice,setNotice]=useState<string | null>(null),[confirm,setConfirm]=useState<string | null>(null);
  const load=useCallback(async (before?: string) => {
    if(!isAdmin) return; setBusy(true); setError(null);
    try { const page=await listWorkspaceInvites(before); setItems(current => before ? [...current,...page.items] : page.items); setNext(page.nextCursor); }
    catch { setError('Unable to load invitations. Check administrator access and refresh.'); }
    finally { setBusy(false); }
  },[isAdmin]);
  useEffect(() => { if(isAdmin) void load(); },[isAdmin,load]);
  const invite=async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setNotice(null);
    try { await inviteWorkspaceMember(email,role); setEmail(''); setNotice('Invitation created and email queued. Membership starts only after acceptance.'); await load(); }
    catch { setError('Invitation could not be created. Verify your email, check delivery setup, and look for an existing member or pending invitation.'); }
    finally { setBusy(false); }
  };
  const revoke=async (id: string) => {
    setBusy(true); setError(null);
    try { await revokeWorkspaceInvite(id); setConfirm(null); await load(); }
    catch { setError('This invitation may have changed. Refresh before trying again.'); }
    finally { setBusy(false); }
  };
  return <section className="p-5 rounded-2xl border border-slate-800 bg-[#0b101b] space-y-3 text-xs">
    <h2 className="text-sm font-semibold">Workspace invitations</h2>
    {!isAdmin ? <p className="text-slate-400">Ask a workspace administrator to invite teammates.</p> : <>
      {error && <p role="alert" className="text-rose-300">{error}</p>}{notice && <p role="status" className="text-emerald-300">{notice}</p>}
      {(!emailVerified || !deliveryConfigured) && <p className="text-amber-200">Verify your email and configure email delivery before creating invitations.</p>}
      <form onSubmit={invite} className="space-y-2">
        <label htmlFor="invite-email" className="block">Teammate email</label>
        <input id="invite-email" type="email" required maxLength={255} value={email} onChange={e => setEmail(e.target.value)} className="w-full rounded border border-slate-700 bg-slate-900 p-2" />
        <label htmlFor="invite-role" className="block">Role to offer</label>
        <select id="invite-role" value={role} onChange={e => setRole(e.target.value)} className="w-full rounded border border-slate-700 bg-slate-900 p-2">
          <option value="ROLE_VIEWER">Viewer</option><option value="ROLE_SRE">SRE</option><option value="ROLE_ADMIN">Administrator</option>
        </select>
        <button disabled={busy || !emailVerified || !deliveryConfigured} className="rounded bg-indigo-600 px-3 py-2 disabled:opacity-50">Create invitation</button>
      </form>
      <button type="button" disabled={busy} onClick={() => void load()} className="text-indigo-300 disabled:opacity-50">Refresh invitations</button>
      {items.length===0 && !error && <p className="text-slate-500">No invitations created.</p>}
      <ul className="divide-y divide-slate-800">{items.map(item => <li key={item.id} className="py-3 space-y-2">
        <p className="break-all">{item.email} · {item.role.replace('ROLE_','')}</p>
        <p className="text-slate-400">{item.status.toLowerCase()} · expires {new Date(item.expiresAt).toLocaleString()}</p>
        <p className="text-slate-500">Email: {item.deliveryStatus==='SENT' ? 'submitted to mail server' : item.deliveryStatus.toLowerCase().replaceAll('_',' ')}</p>
        {(item.status==='PENDING' || item.status==='EXPIRED') && (confirm===item.id ? <div className="flex gap-3">
          <button disabled={busy} onClick={() => void revoke(item.id)} className="text-rose-300">Confirm revoke</button><button disabled={busy} onClick={() => setConfirm(null)}>Cancel</button>
        </div> : <button disabled={busy} onClick={() => setConfirm(item.id)} className="text-rose-300">Revoke invitation</button>)}
      </li>)}</ul>
      {next && <button disabled={busy} onClick={() => void load(next)} className="text-indigo-300">Load older invitations</button>}
    </>}
  </section>;
}
