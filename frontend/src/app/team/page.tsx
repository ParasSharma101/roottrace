'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getEmailStatus, EmailStatus } from '@/lib/auth';
import { TeamMembersPanel } from '@/components/TeamMembersPanel';
import { WorkspaceInvitationsPanel } from '@/components/WorkspaceInvitationsPanel';
import { PageHeader } from '@/components/workspace/PageHeader';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';

export default function TeamPage() {
  const user = useWorkspaceSession();
  const [status,setStatus] = useState<EmailStatus | null>(null);
  useEffect(() => { getEmailStatus().then(setStatus).catch(() => setStatus(null)); },[]);
  return <div className="ux-page"><PageHeader eyebrow="WORK TOGETHER" title="Team" description="Invite teammates and manage who can access this workspace." />
    {!user.emailVerified && <div className="ux-notice">Verify your email before inviting teammates or changing their access. <Link href="/account" className="ux-link">Open Account settings</Link></div>}
    <div className="ux-team-grid legacy-panels"><TeamMembersPanel isAdmin={user.roles.includes('ROLE_ADMIN')} emailVerified={!!status?.emailVerified} currentUserId={user.userId} /><WorkspaceInvitationsPanel isAdmin={user.roles.includes('ROLE_ADMIN')} emailVerified={!!status?.emailVerified} deliveryConfigured={!!status?.deliveryConfigured} /></div>
  </div>;
}
