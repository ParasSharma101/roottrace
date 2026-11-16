'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { getAuthToken, getAuthUser } from '@/lib/auth';
import { IncidentStatus, IncidentSummary, SeverityLevel } from '@/types';
import { getIncidents } from '@/lib/api';
import { StatusBadge, SeverityBadge } from '@/components/Badges';
import { 
  AlertOctagon, 
  Search, 
  RefreshCw, 
  Clock, 
  Sparkles, 
  Bell, 
  ArrowRight, 
  ShieldAlert, 
  CheckCircle2 
} from 'lucide-react';

export default function DashboardPage() {
  const router = useRouter();
  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [selectedStatus, setSelectedStatus] = useState<IncidentStatus | 'ALL'>('ALL');
  const [selectedSeverity, setSelectedSeverity] = useState<SeverityLevel | 'ALL'>('ALL');
  const [searchService, setSearchService] = useState<string>('');

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const statusParam = selectedStatus !== 'ALL' ? selectedStatus : undefined;
      const severityParam = selectedSeverity !== 'ALL' ? selectedSeverity : undefined;
      const serviceParam = searchService.trim() ? searchService.trim() : undefined;

      const data = await getIncidents(statusParam, severityParam, serviceParam);
      setIncidents(data);
    } catch (err: any) {
      setError(err.message || 'Failed to connect to RootTrace backend');
    } finally {
      setLoading(false);
    }
  }, [selectedStatus, selectedSeverity, searchService]);

  useEffect(() => {
    if (!getAuthToken()) { router.replace('/login'); return; }
    if (!getAuthUser()?.workspaceId) { router.replace('/workspaces'); return; }
    loadData();
  }, [loadData, router]);

  // Compute summary metrics
  const totalCount = incidents.length;
  const activeCount = incidents.filter((i) => i.status === 'INVESTIGATING' || i.status === 'OPEN').length;
  const resolvedCount = incidents.filter((i) => i.status === 'RESOLVED').length;
  const sev1Count = incidents.filter((i) => i.severityLevel === 'SEV1').length;

  return (
    <div className="space-y-8">
      {/* Top Banner / Hero */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <AlertOctagon className="w-6 h-6 text-indigo-400" />
            Incident Command Center
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Real-time correlation of alerts, deployments, Git diffs, and AI-grounded root cause hypotheses.
          </p>
        </div>

        <button
          onClick={loadData}
          disabled={loading}
          className="self-start md:self-auto flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-700 hover:bg-slate-800 text-xs font-medium text-slate-200 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-indigo-400' : ''}`} />
          Refresh Live State
        </button>
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-1">
          <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Total Incidents</span>
          <div className="text-2xl font-bold font-mono text-white">{totalCount}</div>
        </div>

        <div className="p-4 rounded-xl bg-amber-950/20 border border-amber-900/30 space-y-1">
          <span className="text-xs font-medium text-amber-400 uppercase tracking-wider flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
            Active / Investigating
          </span>
          <div className="text-2xl font-bold font-mono text-amber-300">{activeCount}</div>
        </div>

        <div className="p-4 rounded-xl bg-emerald-950/20 border border-emerald-900/30 space-y-1">
          <span className="text-xs font-medium text-emerald-400 uppercase tracking-wider flex items-center gap-1.5">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
            Resolved
          </span>
          <div className="text-2xl font-bold font-mono text-emerald-300">{resolvedCount}</div>
        </div>

        <div className="p-4 rounded-xl bg-rose-950/20 border border-rose-900/30 space-y-1">
          <span className="text-xs font-medium text-rose-400 uppercase tracking-wider flex items-center gap-1.5">
            <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
            Critical SEV1
          </span>
          <div className="text-2xl font-bold font-mono text-rose-300">{sev1Count}</div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 p-3 rounded-xl bg-slate-900/70 border border-slate-800">
        <div className="flex items-center gap-2 flex-wrap">
          {/* Status selector */}
          <select
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value as any)}
            className="bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
          >
            <option value="ALL">All Statuses</option>
            <option value="INVESTIGATING">INVESTIGATING</option>
            <option value="OPEN">OPEN</option>
            <option value="RESOLVED">RESOLVED</option>
          </select>

          {/* Severity selector */}
          <select
            value={selectedSeverity}
            onChange={(e) => setSelectedSeverity(e.target.value as any)}
            className="bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
          >
            <option value="ALL">All Severities</option>
            <option value="SEV1">SEV1 (Critical)</option>
            <option value="SEV2">SEV2 (High)</option>
            <option value="SEV3">SEV3 (Medium)</option>
            <option value="SEV4">SEV4 (Low)</option>
          </select>
        </div>

        {/* Search by Service Name */}
        <div className="relative min-w-[240px]">
          <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Filter by service name..."
            value={searchService}
            onChange={(e) => setSearchService(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-9 pr-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
          />
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-950/30 border border-rose-800 text-rose-300 text-sm">
          {error}
        </div>
      )}

      {/* Incident List */}
      <div className="space-y-3">
        {loading ? (
          <div className="text-center py-16 text-slate-500 text-sm flex items-center justify-center gap-2">
            <RefreshCw className="w-4 h-4 animate-spin text-indigo-400" />
            Loading incidents from RootTrace backend...
          </div>
        ) : incidents.length === 0 ? (
          <div className="text-center py-16 rounded-xl border border-dashed border-slate-800 space-y-3">
            <AlertOctagon className="w-8 h-8 text-slate-600 mx-auto" />
            <h3 className="text-sm font-semibold text-slate-300">No Incidents Found</h3>
            <p className="text-xs text-slate-500 max-w-sm mx-auto">
              No incidents match your current filter parameters, or no alerts have been ingested yet.
            </p>
            <p className="text-xs text-indigo-400 font-medium">
              Click &quot;Simulate Scenario A&quot; or &quot;Simulate Scenario B&quot; in the header bar above to inject live incidents!
            </p>
          </div>
        ) : (
          incidents.map((inc) => {
            const startDate = new Date(inc.startTime);
            const timeAgo = startDate.toLocaleTimeString([], { hour12: false });

            return (
              <Link
                key={inc.id}
                href={`/incidents/${inc.id}`}
                className="group block p-5 rounded-xl bg-slate-900/60 hover:bg-slate-900 border border-slate-800 hover:border-indigo-500/50 transition-all hover:shadow-lg hover:shadow-indigo-500/5"
              >
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <StatusBadge status={inc.status} />
                      <SeverityBadge severity={inc.severityLevel} />
                      <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                        {inc.serviceName}
                      </span>
                    </div>

                    <h2 className="text-base font-semibold text-slate-100 group-hover:text-indigo-400 transition-colors">
                      {inc.title}
                    </h2>

                    <div className="flex items-center gap-4 text-xs text-slate-400">
                      <span className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-500" />
                        Started at {timeAgo}
                      </span>
                      {inc.resolutionTime && (
                        <span className="text-emerald-400 font-medium">
                          Resolved in {Math.round((new Date(inc.resolutionTime).getTime() - startDate.getTime()) / 60000)}m
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-4 self-end md:self-center shrink-0">
                    <div className="flex items-center gap-3">
                      <div className="flex items-center gap-1 text-xs text-slate-400 bg-slate-950 px-2.5 py-1 rounded border border-slate-800">
                        <Bell className="w-3.5 h-3.5 text-rose-400" />
                        <span>{inc.alertCount} alerts</span>
                      </div>

                      <div className="flex items-center gap-1 text-xs text-slate-400 bg-slate-950 px-2.5 py-1 rounded border border-slate-800">
                        <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                        <span>{inc.hypothesisCount} hypotheses</span>
                      </div>
                    </div>

                    <div className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center text-slate-400 group-hover:bg-indigo-600 group-hover:text-white transition-all">
                      <ArrowRight className="w-4 h-4" />
                    </div>
                  </div>
                </div>
              </Link>
            );
          })
        )}
      </div>
    </div>
  );
}
