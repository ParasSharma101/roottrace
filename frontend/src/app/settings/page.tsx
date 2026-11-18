'use client';

import React, { useState } from 'react';
import { Navbar } from '@/components/Navbar';
import { TaskRecoveryPanel } from '@/components/TaskRecoveryPanel';
import { IngestionKeysPanel } from '@/components/IngestionKeysPanel';
import { TeamMembersPanel } from '@/components/TeamMembersPanel';
import { EmailVerificationPanel } from '@/components/EmailVerificationPanel';
import { AccountSecurityPanel } from '@/components/AccountSecurityPanel';
import { WorkspaceInvitationsPanel } from '@/components/WorkspaceInvitationsPanel';
import { EmailStatus, getAuthUser } from '@/lib/auth';
import { Building } from 'lucide-react';

export default function SettingsPage() {
  const [emailStatus, setEmailStatus] = useState<EmailStatus | null>(null);

  const currentUser = getAuthUser();
  const isAdmin = currentUser?.roles?.some(r => r.includes('ADMIN'));
  const isOperator = currentUser?.roles?.some(r => r === 'ROLE_ADMIN' || r === 'ROLE_SRE');

  return (
    <div className="min-h-screen bg-[#070b12] text-slate-100 flex flex-col selection:bg-indigo-500/30">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Building className="w-5 h-5 text-indigo-400" />
            <h1 className="text-2xl font-bold tracking-tight text-white">
              Workspace & Team Settings
            </h1>
          </div>
          <p className="text-xs text-slate-400">
            Manage ingestion keys, recover failed tasks, and review team permissions.
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left 2 Cols: Ingestion Key & Webhook setup */}
          <div className="lg:col-span-2 space-y-6">
            <EmailVerificationPanel onChange={setEmailStatus} />
            <AccountSecurityPanel />
            <IngestionKeysPanel isAdmin={!!isAdmin} />
            {isOperator && <TaskRecoveryPanel />}
          </div>

          {/* Right Col: Team Members & Invites */}
          <div className="space-y-6">
            <WorkspaceInvitationsPanel isAdmin={!!isAdmin} emailVerified={!!emailStatus?.emailVerified} deliveryConfigured={!!emailStatus?.deliveryConfigured} />

            <TeamMembersPanel isAdmin={!!isAdmin} emailVerified={!!emailStatus?.emailVerified} currentUserId={currentUser?.userId} />
          </div>
        </div>
      </main>
    </div>
  );
}
