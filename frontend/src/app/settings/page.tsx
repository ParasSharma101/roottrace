'use client';

import React, { useState, useEffect } from 'react';
import { Navbar } from '@/components/Navbar';
import { getWorkspaceDetails, inviteWorkspaceMember, WorkspaceDetails } from '@/lib/api';
import { getAuthUser } from '@/lib/auth';
import { Key, Users, Copy, Check, UserPlus, Shield, UserCheck, Eye, Terminal, Sparkles, Building } from 'lucide-react';

export default function SettingsPage() {
  const [workspace, setWorkspace] = useState<WorkspaceDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [copiedKey, setCopiedKey] = useState(false);
  const [copiedCurl, setCopiedCurl] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('ROLE_SRE');
  const [inviting, setInviting] = useState(false);
  const [inviteMsg, setInviteMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const currentUser = getAuthUser();
  const isAdmin = currentUser?.roles?.some(r => r.includes('ADMIN'));

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

  const handleCopyKey = () => {
    if (!workspace?.apiKey) return;
    navigator.clipboard.writeText(workspace.apiKey);
    setCopiedKey(true);
    setTimeout(() => setCopiedKey(false), 2000);
  };

  const curlSnippet = workspace?.apiKey
    ? `curl -X POST http://localhost:8080/api/v1/webhooks/alerts/datadog \\\n  -H "Content-Type: application/json" \\\n  -H "X-RootTrace-Key: ${workspace.apiKey}" \\\n  -d '{"serviceName": "payment-api", "alertType": "HighLatency", "severity": "SEV1", "triggeredAt": "'$(date -u +%FT%TZ)'"}'`
    : '';

  const handleCopyCurl = () => {
    navigator.clipboard.writeText(curlSnippet);
    setCopiedCurl(true);
    setTimeout(() => setCopiedCurl(false), 2000);
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail) return;
    setInviting(true);
    setInviteMsg(null);

    try {
      await inviteWorkspaceMember(inviteEmail, inviteRole);
      setInviteMsg({ type: 'success', text: `Invitation created for ${inviteEmail}` });
      setInviteEmail('');
      await fetchWorkspace();
    } catch (err: any) {
      setInviteMsg({ type: 'error', text: err.message || 'Failed to send invite' });
    } finally {
      setInviting(false);
    }
  };

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
            Manage your telemetry ingestion keys, webhook endpoints, and team member permissions.
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
              {/* Ingestion Key Card */}
              <div className="p-6 rounded-2xl bg-[#0b101b] border border-slate-800 shadow-xl">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <Key className="w-4 h-4 text-emerald-400" />
                    <h2 className="text-sm font-semibold text-slate-200">
                      Telemetry Ingestion API Key
                    </h2>
                  </div>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                    Active
                  </span>
                </div>
                <p className="text-xs text-slate-400 mb-4">
                  Pass this secret header (<code className="text-indigo-300">X-RootTrace-Key</code>) in all incoming webhook and log stream payloads from Datadog, PagerDuty, or FluentBit.
                </p>

                <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800 p-2.5 rounded-xl font-mono text-xs text-slate-300">
                  <span className="flex-1 truncate select-all">{workspace?.apiKey || '••••••••••••••••'}</span>
                  <button
                    onClick={handleCopyKey}
                    disabled={!workspace?.apiKey}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 font-sans transition-colors"
                  >
                    {copiedKey ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-slate-400" />}
                    <span>{copiedKey ? 'Copied!' : 'Copy Key'}</span>
                  </button>
                </div>
              </div>

              {/* Quick Integration Curl Card */}
              <div className="p-6 rounded-2xl bg-[#0b101b] border border-slate-800 shadow-xl">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <Terminal className="w-4 h-4 text-indigo-400" />
                    <h2 className="text-sm font-semibold text-slate-200">
                      Webhook Ingestion Quickstart
                    </h2>
                  </div>
                  <button
                    onClick={handleCopyCurl}
                    disabled={!workspace?.apiKey}
                    className="flex items-center gap-1.5 text-xs text-indigo-400 hover:text-indigo-300 transition-colors"
                  >
                    {copiedCurl ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copiedCurl ? 'Copied' : 'Copy cURL'}</span>
                  </button>
                </div>
                <p className="text-xs text-slate-400 mb-3">
                  Test your real-time incident pipeline immediately from your terminal or CI/CD runner:
                </p>
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-900 font-mono text-[11px] text-slate-300 overflow-x-auto whitespace-pre">
                  {curlSnippet}
                </div>
              </div>
            </div>

            {/* Right Col: Team Members & Invites */}
            <div className="space-y-6">
              {/* Invite Member Card */}
              <div className="p-6 rounded-2xl bg-[#0b101b] border border-slate-800 shadow-xl">
                <div className="flex items-center gap-2 mb-3">
                  <UserPlus className="w-4 h-4 text-indigo-400" />
                  <h2 className="text-sm font-semibold text-slate-200">
                    Invite Teammate
                  </h2>
                </div>

                {!isAdmin ? (
                  <p className="text-xs text-slate-500 bg-slate-900/50 p-3 rounded-lg border border-slate-800">
                    Only workspace administrators can invite new members. Switch to an Admin persona or ask your team lead.
                  </p>
                ) : (
                  <form onSubmit={handleInvite} className="space-y-3">
                    {inviteMsg && (
                      <div className={`p-2.5 rounded-lg text-xs ${inviteMsg.type === 'success' ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/30' : 'bg-rose-500/10 text-rose-300 border border-rose-500/30'}`}>
                        {inviteMsg.text}
                      </div>
                    )}
                    <div>
                      <label className="block text-[11px] font-medium text-slate-400 mb-1">
                        Email Address
                      </label>
                      <input
                        type="email"
                        required
                        value={inviteEmail}
                        onChange={(e) => setInviteEmail(e.target.value)}
                        placeholder="colleague@company.com"
                        className="block w-full rounded-lg border border-slate-800 bg-slate-900/90 px-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-indigo-500 focus:outline-none"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-medium text-slate-400 mb-1">
                        Role Assignment
                      </label>
                      <select
                        value={inviteRole}
                        onChange={(e) => setInviteRole(e.target.value)}
                        className="block w-full rounded-lg border border-slate-800 bg-slate-900/90 px-3 py-2 text-xs text-slate-200 focus:border-indigo-500 focus:outline-none"
                      >
                        <option value="ROLE_SRE">On-Call SRE (Verify & Resolve)</option>
                        <option value="ROLE_VIEWER">Developer (Read-Only Observer)</option>
                        <option value="ROLE_ADMIN">Lead / Admin (Full Access)</option>
                      </select>
                    </div>
                    <button
                      type="submit"
                      disabled={inviting}
                      className="w-full py-2 px-3 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-medium text-white transition-colors disabled:opacity-50"
                    >
                      {inviting ? 'Sending Invite...' : 'Send Invitation'}
                    </button>
                  </form>
                )}
              </div>

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
