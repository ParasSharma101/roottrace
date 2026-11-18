'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { EmailVerificationPanel } from '@/components/EmailVerificationPanel';
import { InvitationInbox } from '@/components/InvitationInbox';
import { EmailStatus, getAuthToken, listMyWorkspaces, selectWorkspace, WorkspaceMembership } from '@/lib/auth';

export default function WorkspacesPage() {
  const router = useRouter();
  const [workspaces, setWorkspaces] = useState<WorkspaceMembership[]>([]);
  const [loading, setLoading] = useState(true);
  const [selecting, setSelecting] = useState<string | null>(null);
  const [emailStatus, setEmailStatus] = useState<EmailStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAuthToken()) { router.replace('/login'); return; }
    listMyWorkspaces().then(setWorkspaces)
      .catch((err: Error) => setError(err.message)).finally(() => setLoading(false));
  }, [router]);

  const choose = async (id: string) => {
    setSelecting(id);
    setError(null);
    try {
      await selectWorkspace(id);
      // Drop data held by the previous workspace's mounted components.
      window.location.assign('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to select workspace');
      setSelecting(null);
    }
  };

  return <section className="max-w-xl mx-auto py-12 space-y-6">
    <div>
      <h1 className="text-2xl font-semibold">Choose a workspace</h1>
      <p className="mt-2 text-sm text-slate-400">Your permissions apply to the workspace you select.</p>
    </div>
    <EmailVerificationPanel onChange={setEmailStatus} />
    <InvitationInbox emailVerified={!!emailStatus?.emailVerified} onAccepted={() => { void listMyWorkspaces().then(setWorkspaces).catch(() => setError('Refresh workspaces to see your new membership.')); }} />
    {error && <p role="alert" className="text-rose-300">{error} <Link href="/login" className="underline">Sign in</Link></p>}
    {loading ? <p className="text-slate-400">Loading workspaces...</p> :
      workspaces.length === 0 ? <p className="text-slate-400">You have no workspace memberships. Contact your workspace administrator.</p> :
      <ul className="space-y-3">{workspaces.map(workspace => <li key={workspace.workspaceId}>
        <button disabled={selecting !== null} onClick={() => choose(workspace.workspaceId)}
          className="w-full text-left rounded-xl border border-slate-700 bg-slate-900 p-4 hover:border-indigo-400 disabled:opacity-50">
          <span className="block font-medium">{workspace.name}</span>
          <span className="text-xs text-slate-400">{workspace.slug} · {workspace.role.replace('ROLE_', '')}</span>
          {selecting === workspace.workspaceId && <span className="block text-sm text-indigo-300">Opening workspace...</span>}
        </button>
      </li>)}</ul>}
  </section>;
}
