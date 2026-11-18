'use client';

import React, { useState, useEffect } from 'react';
import { Navbar } from '@/components/Navbar';
import { TaskRecoveryPanel } from '@/components/TaskRecoveryPanel';
import { IngestionKeysPanel } from '@/components/IngestionKeysPanel';
import { getWorkspaceDetails, WorkspaceDetails } from '@/lib/api';
import { EmailVerificationPanel } from '@/components/EmailVerificationPanel';
import { WorkspaceInvitationsPanel } from '@/components/WorkspaceInvitationsPanel';
import { EmailStatus, getAuthUser } from '@/lib/auth';
import { Key, Users, Copy, Check, UserPlus, Shield, UserCheck, Eye, Terminal, Sparkles, Building } from 'lucide-react';

export default function SettingsPage() {
  const [workspace, setWorkspace] = useState<WorkspaceDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [emailStatus, setEmailStatus] = useState<EmailStatus | null>(null);

  const currentUser = getAuthUser();
  const isAdmin = currentUser?.roles?.some(r => r.includes('ADMIN'));
  const isOperator = currentUser?.roles?.some(r => r === 'ROLE_ADMIN' || r === 'ROLE_SRE');

  const fetchWorkspace = async () => {
    try {
      setLoading(true);
      const data = await getWorkspaceDetails();
      setWorkspace(data);
    } catch (err: any) {
      console.error('Failed to load workspace:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWorkspace();
  }, []);



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

        {loading ? (
          <div className="flex items-center justify-center py-20 text-xs text-slate-500">
            Loading workspace details...
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Left 2 Cols: Ingestion Key & Webhook setup */}
            <div className="lg:col-span-2 space-y-6">
              <EmailVerificationPanel onChange={setEmailStatus} />
              <IngestionKeysPanel isAdmin={!!isAdmin} />
              {isOperator && <TaskRecoveryPanel />}
            </div>

            {/* Right Col: Team Members & Invites */}
            <div className="space-y-6">
              <WorkspaceInvitationsPanel isAdmin={!!isAdmin} emailVerified={!!emailStatus?.emailVerified} deliveryConfigured={!!emailStatus?.deliveryConfigured} />

              {/* Members List */}
              <div className="p-6 rounded-2xl bg-[#0b101b] border border-slate-800 shadow-xl">
                <div className="flex items-center gap-2 mb-4">
                  <Users className="w-4 h-4 text-slate-400" />
                  <h2 className="text-sm font-semibold text-slate-200">
                    Workspace Members ({workspace?.members?.length || 0})
                  </h2>
                </div>

                <div className="space-y-3">
                  {workspace?.members?.map((member) => (
                    <div key={member.userId} className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/60 border border-slate-800/80 text-xs">
                      <div className="min-w-0 pr-2">
                        <div className="font-medium text-slate-200 truncate">{member.fullName}</div>
                        <div className="text-[11px] text-slate-500 truncate">{member.email}</div>
                      </div>
                      <span className={`shrink-0 text-[10px] font-mono px-2 py-0.5 rounded border ${
                        member.role.includes('ADMIN')
                          ? 'bg-amber-500/10 text-amber-300 border-amber-500/20'
                          : member.role.includes('SRE')
                          ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20'
                          : 'bg-blue-500/10 text-blue-300 border-blue-500/20'
                      }`}>
                        {member.role.replace('ROLE_', '')}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
