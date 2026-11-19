'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Activity, ArrowRight, CheckCircle2, PlugZap, RefreshCw, ShieldAlert } from 'lucide-react';
import { getIncidents, getWorkspaceSetupStatus, WorkspaceSetupStatus } from '@/lib/api';
import { nextSetupAction } from '@/lib/setup';
import type { IncidentSummary } from '@/types';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';
import { PageHeader } from '@/components/workspace/PageHeader';
import { SetupChecklist } from '@/components/workspace/SetupChecklist';
import { IncidentList } from '@/components/workspace/IncidentList';

export default function OverviewPage() {
  const user = useWorkspaceSession();
  const [incidents,setIncidents] = useState<IncidentSummary[]>([]);
  const [setup,setSetup] = useState<WorkspaceSetupStatus | null>(null);
  const [loading,setLoading] = useState(true),[error,setError] = useState<string | null>(null),[incidentError,setIncidentError] = useState(false);
  const load = useCallback(async () => {
    setLoading(true); setError(null); setIncidentError(false);
    const [incidentResult,setupResult] = await Promise.allSettled([getIncidents(),getWorkspaceSetupStatus()]);
    if (incidentResult.status === 'fulfilled') setIncidents(incidentResult.value);
    else { setIncidents([]); setIncidentError(true); setError('Incident details could not be loaded. Refresh to try again.'); }
    if (setupResult.status === 'fulfilled') setSetup(setupResult.value);
    else { setSetup(null); setError(current => current || 'Connection details could not be loaded. Refresh to try again.'); }
    setLoading(false);
  },[]);
  useEffect(() => { void load(); },[load]);
  const active = incidents.filter(incident => incident.status !== 'RESOLVED').sort((a,b) => a.severityLevel.localeCompare(b.severityLevel) || Date.parse(b.startTime)-Date.parse(a.startTime));
  const next = nextSetupAction(setup);
  return <div className="ux-page">
    <PageHeader eyebrow={user.workspaceName || 'YOUR WORKSPACE'} title="Workspace overview" description="Connect your project, confirm incoming data, and follow active incidents." actions={<button className="ux-button secondary" disabled={loading} onClick={() => void load()}><RefreshCw size={15} className={loading ? 'animate-spin' : ''} aria-hidden="true" />Refresh</button>} />
    {error && <div className="ux-notice warning" role="alert">{error}</div>}
    <section className="ux-next-action"><div className="ux-next-action-icon"><PlugZap size={25} aria-hidden="true" /></div><div><p className="ux-eyebrow">YOUR NEXT STEP</p><h2>{loading ? 'Checking your workspace…' : next.title}</h2><p>{loading ? 'Loading connection and incident details.' : next.description}</p></div>{!loading && <Link href={next.href} className="ux-button">{next.label}<ArrowRight size={16} aria-hidden="true" /></Link>}</section>
    <div className="ux-metrics"><div><span>Active incidents</span><strong>{loading || incidentError ? '—' : active.length}</strong><small>Open or under investigation</small></div><div><span>Critical incidents</span><strong>{loading || incidentError ? '—' : active.filter(incident => incident.severityLevel === 'SEV1').length}</strong><small>Unresolved SEV1 incidents</small></div><div><span>Processed events</span><strong>{loading || !setup ? '—' : setup.processedEventCount.toLocaleString()}</strong><small>Processing confirmed by RootTrace</small></div></div>
    {(!setup?.processedEventCount || !setup?.activeKeyCount) && <section className="ux-card"><div className="ux-section-heading"><div><h2>Get your project connected</h2><p>Three steps from an empty workspace to your first signal.</p></div><Link href="/integrations" className="ux-link">Open guide<ArrowRight size={14} aria-hidden="true" /></Link></div><SetupChecklist status={setup} /></section>}
    <section className="ux-card ux-card-flush"><div className="ux-section-heading"><div><h2>Needs your attention</h2><p>Active incidents, with critical issues first.</p></div><Link href="/incidents" className="ux-link">All incidents<ArrowRight size={14} aria-hidden="true" /></Link></div>
      {loading ? <div className="ux-empty" role="status">Loading incidents…</div> : incidentError ? <div className="ux-empty"><ShieldAlert size={26} aria-hidden="true" /><h3>Incident status is unavailable</h3><p>Refresh before drawing conclusions about your services.</p></div> : active.length ? <IncidentList incidents={active.slice(0,4)} /> : <div className="ux-empty"><CheckCircle2 size={28} aria-hidden="true" /><h3>No active incidents</h3><p>{setup?.processedEventCount ? 'There are no open incidents in this workspace.' : 'Once you send an alert, its investigation will appear here.'}</p><Link href="/integrations#send-data" className="ux-link">Learn how alerts create incidents<ArrowRight size={14} aria-hidden="true" /></Link></div>}
    </section>
    <div className="ux-help-grid"><Link href="/integrations"><PlugZap size={20} aria-hidden="true" /><div><h3>Connect another service</h3><p>Keys, webhook examples, and delivery confirmation.</p></div><ArrowRight size={16} aria-hidden="true" /></Link><Link href="/incidents"><Activity size={20} aria-hidden="true" /><div><h3>Follow an investigation</h3><p>Review the timeline, evidence, and hypotheses.</p></div><ArrowRight size={16} aria-hidden="true" /></Link></div>
    {!user.emailVerified && <p className="ux-subtle-note">Want to invite teammates? <Link href="/account">Verify your email in Account settings.</Link></p>}
  </div>;
}
