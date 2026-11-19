'use client';

import { useState } from 'react';
import Link from 'next/link';
import { TaskRecoveryPanel } from '@/components/TaskRecoveryPanel';
import { PageHeader } from '@/components/workspace/PageHeader';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';
import { ArrowRight, Cable, Users } from 'lucide-react';
import { isDemoEnabled } from '@/lib/demo';
import { DemoTools } from '@/components/workspace/DemoTools';

export default function SettingsPage() {
  const user = useWorkspaceSession();
  const [operations,setOperations] = useState(false);
  const operator = user.roles.includes('ROLE_ADMIN') || user.roles.includes('ROLE_SRE');
  return <div className="ux-page"><PageHeader eyebrow="WORKSPACE ADMINISTRATION" title="Settings" description="Workspace details and tools for reviewing processing issues." />
    <section className="ux-card"><div className="ux-section-heading"><div><h2>Workspace details</h2><p>These details identify the workspace you are currently using.</p></div></div><dl className="ux-details-list"><div><dt>Workspace name</dt><dd>{user.workspaceName}</dd></div><div><dt>Workspace address</dt><dd>{user.workspaceSlug}</dd></div><div><dt>Your access</dt><dd>{user.roles.includes('ROLE_ADMIN') ? 'Administrator' : user.roles.includes('ROLE_SRE') ? 'SRE' : 'Viewer'}</dd></div></dl></section>
    <div className="ux-help-grid"><Link href="/integrations"><Cable size={20} aria-hidden="true" /><div><h3>Project connections</h3><p>Manage ingestion keys and confirm incoming data.</p></div><ArrowRight size={16} aria-hidden="true" /></Link><Link href="/team"><Users size={20} aria-hidden="true" /><div><h3>Members and invitations</h3><p>Invite people and review workspace access.</p></div><ArrowRight size={16} aria-hidden="true" /></Link></div>
    {operator ? <details className="ux-card ux-disclosure" onToggle={event => setOperations(event.currentTarget.open)}><summary><div><h2>Processing and recovery</h2><p>Advanced tools for administrators and SREs. Inspect failures before retrying work.</p></div></summary>{operations && <div className="legacy-panels ux-disclosure-content"><TaskRecoveryPanel /></div>}</details> : <section className="ux-card"><h2>Processing and recovery</h2><p className="ux-guide-note">An administrator or SRE can review processing failures and retry work.</p></section>}
    {isDemoEnabled && <DemoTools />}
    <p className="ux-subtle-note">Looking for email verification, password recovery, or your sessions? <Link href="/account">Open your Account settings.</Link></p>
  </div>;
}
