'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { changeMemberRole, getWorkspaceDetails, listMembershipAudit, MembershipAuditEntry, removeMember, TeamMember, WorkspaceDetails } from '@/lib/api';
import { clearAuthSession, getAuthUser, selectWorkspace } from '@/lib/auth';

export function TeamMembersPanel({ isAdmin, emailVerified, currentUserId }: { isAdmin: boolean; emailVerified: boolean; currentUserId?: string }) {
  const [workspace, setWorkspace] = useState<WorkspaceDetails | null>(null);
  const [entries, setEntries] = useState<MembershipAuditEntry[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [edit, setEdit] = useState<{ member: TeamMember; action: 'role' | 'remove' } | null>(null);
  const [role, setRole] = useState('ROLE_VIEWER');
  const [reason, setReason] = useState('');
  const refresh = useCallback(async () => {
    setBusy(true); setError(null);
    try {
      const data = await getWorkspaceDetails();
      const user = getAuthUser();
      const currentRole = user?.roles.includes('ROLE_ADMIN') ? 'ROLE_ADMIN' : user?.roles.includes('ROLE_SRE') ? 'ROLE_SRE' : 'ROLE_VIEWER';
      const ownRole = data.members.find(member => member.userId === user?.userId)?.role;
      if (ownRole && ownRole !== currentRole) {
        await selectWorkspace(data.id); window.location.assign('/settings'); return;
      }
      setWorkspace(data);
    }
    catch { setWorkspace(null); setEntries([]); setEdit(null); setError('Unable to load the team. Your membership may have changed. Sign in again or refresh.'); }
    finally { setBusy(false); }
  }, []);
  const history = useCallback(async (before?: string) => {
    if (!isAdmin) return;
    try {
      const page = await listMembershipAudit(before);
      setEntries(current => before ? [...current, ...page.items] : page.items); setNext(page.nextCursor);
    } catch { setEntries([]); setNext(null); setError('Unable to load membership history. Check administrator access and refresh.'); }
  }, [isAdmin]);
  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { if (isAdmin) void history(); else { setEntries([]); setNext(null); } }, [isAdmin, history]);
  const begin = (member: TeamMember, action: 'role' | 'remove') => {
    setEdit({ member, action }); setRole(member.role); setReason(''); setError(null); setNotice(null);
  };
  const apply = async (event: React.FormEvent) => {
    event.preventDefault(); if (!edit || !workspace || busy) return;
    setBusy(true); setError(null);
    try {
      if (edit.action === 'role') await changeMemberRole(edit.member, role, reason.trim());
      else await removeMember(edit.member, reason.trim());
      if (edit.member.userId === currentUserId) {
        if (edit.action === 'remove') { clearAuthSession(); window.location.assign('/login'); return; }
        await selectWorkspace(workspace.id); window.location.assign('/settings'); return;
      }
      setEdit(null); setNotice(edit.action === 'role' ? 'Member role updated.' : 'Member removed and pending invitations revoked.');
      await refresh(); await history();
    } catch (error) { setError(error instanceof Error ? error.message : 'Unable to change membership.'); }
    finally { setBusy(false); }
  };
  const adminCount = workspace?.members.filter(member => member.role === 'ROLE_ADMIN').length ?? 0;
  return <section className="rounded-2xl border border-slate-800 bg-[#0b101b] p-5 space-y-4 text-xs">
    <h2 className="text-sm font-semibold">Workspace members ({workspace?.members.length ?? 0})</h2>
    {error && <p role="alert" className="text-rose-300">{error} <Link href="/login" className="underline">Sign in</Link></p>}
    {notice && <p role="status" className="text-emerald-300">{notice}</p>}
    {isAdmin && !emailVerified && <p className="text-amber-200">Verify your email before changing roles or removing teammates.</p>}
    <button disabled={busy} type="button" onClick={() => { setEdit(null); void refresh(); void history(); }} className="text-indigo-300 disabled:opacity-50">Refresh team and history</button>
    <ul className="divide-y divide-slate-800">{workspace?.members.map(member => {
      const self = member.userId === currentUserId;
      const lastAdmin = member.role === 'ROLE_ADMIN' && adminCount <= 1;
      return <li key={member.membershipId} className="py-3 space-y-2">
        <p className="font-medium break-words">{member.fullName}{self ? ' (you)' : ''}</p>
        <p className="break-all text-slate-400">{member.email}</p>
        <p>{member.role.replace('ROLE_', '')}</p>
        {lastAdmin && <p className="text-amber-200">Promote another administrator before leaving or changing this role.</p>}
        <div className="flex flex-wrap gap-4">
          {isAdmin && emailVerified && <button type="button" disabled={busy || lastAdmin} onClick={() => begin(member, 'role')} className="text-indigo-300 disabled:opacity-40">Change role</button>}
          {(self || (isAdmin && emailVerified)) && <button type="button" disabled={busy || lastAdmin} onClick={() => begin(member, 'remove')} className="text-rose-300 disabled:opacity-40">{self ? 'Leave workspace' : 'Remove member'}</button>}
        </div>
      </li>;
    })}</ul>
    {edit && <form onSubmit={apply} className="rounded-xl border border-slate-700 p-3 space-y-3">
      <h3 className="font-semibold break-all">{edit.action === 'role' ? 'Change role for' : 'Remove'} {edit.member.email}</h3>
      {edit.action === 'role' ? <>
        <label htmlFor="member-role" className="block">New role</label>
        <select id="member-role" value={role} onChange={event => setRole(event.target.value)} disabled={busy} className="w-full rounded border border-slate-700 bg-slate-900 p-2">
          <option value="ROLE_VIEWER">Viewer</option><option value="ROLE_SRE">SRE</option><option value="ROLE_ADMIN">Administrator</option>
        </select>
      </> : <p className="text-slate-400">Access to this workspace will end and pending invitations to this address will be revoked. Workspace data and ingestion keys remain. {edit.member.userId === currentUserId && 'You will need to sign in again to choose another workspace.'}</p>}
      <label htmlFor="membership-reason" className="block">Reason (recorded in workspace history)</label>
      <textarea id="membership-reason" required maxLength={500} value={reason} onChange={event => setReason(event.target.value)} disabled={busy} className="w-full rounded border border-slate-700 bg-slate-900 p-2" />
      <div className="flex gap-4">
        <button disabled={busy || !reason.trim() || (edit.action === 'role' && role === edit.member.role)} className="rounded bg-indigo-600 px-3 py-2 disabled:opacity-40">{busy ? 'Saving...' : edit.action === 'role' ? 'Confirm role change' : 'Confirm removal'}</button>
        <button type="button" disabled={busy} onClick={() => setEdit(null)}>Cancel</button>
      </div>
    </form>}
    {isAdmin && <div className="border-t border-slate-800 pt-4 space-y-3">
      <h3 className="font-semibold">Membership history</h3>
      {entries.length === 0 && <p className="text-slate-500">No membership changes recorded.</p>}
      <ul className="space-y-3">{entries.map(entry => <li key={entry.id} className="space-y-1 break-words">
        <p>{entry.targetEmail} - {entry.action === 'ROLE_CHANGED' ? `${entry.previousRole.replace('ROLE_', '')} to ${entry.newRole?.replace('ROLE_', '')}` : entry.action === 'MEMBER_LEFT' ? 'left workspace' : 'removed'}</p>
        <p className="text-slate-400">By {entry.actorEmail} - {new Date(entry.createdAt).toLocaleString()}</p>
        <p className="whitespace-pre-wrap">{entry.reason}</p>
        {entry.revokedInvitations > 0 && <p className="text-slate-500">Pending invitations revoked: {entry.revokedInvitations}</p>}
      </li>)}</ul>
      {next && <button type="button" disabled={busy} onClick={() => void history(next)} className="text-indigo-300">Load older changes</button>}
    </div>}
  </section>;
}
