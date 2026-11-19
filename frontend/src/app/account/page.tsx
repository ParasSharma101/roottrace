'use client';

import { EmailVerificationPanel } from '@/components/EmailVerificationPanel';
import { AccountSecurityPanel } from '@/components/AccountSecurityPanel';
import { PageHeader } from '@/components/workspace/PageHeader';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';

export default function AccountPage() {
  const user = useWorkspaceSession();
  return <div className="ux-page"><PageHeader eyebrow="PERSONAL SETTINGS" title="Your account" description="Your email and sign-in security, across all your workspaces." />
    <section className="ux-card ux-account-summary"><span className="product-avatar">{(user.fullName || user.email).slice(0,1).toUpperCase()}</span><div><h2>{user.fullName}</h2><p>{user.email}</p></div></section>
    <div className="ux-two-columns legacy-panels"><EmailVerificationPanel /><AccountSecurityPanel /></div>
  </div>;
}
