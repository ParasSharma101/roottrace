'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowRight, RefreshCw } from 'lucide-react';
import { getWorkspaceSetupStatus, WorkspaceSetupStatus } from '@/lib/api';
import { IngestionKeysPanel } from '@/components/IngestionKeysPanel';
import { ConnectionGuide } from '@/components/workspace/ConnectionGuide';
import { PageHeader } from '@/components/workspace/PageHeader';
import { SetupChecklist } from '@/components/workspace/SetupChecklist';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';

export default function IntegrationsPage() {
  const user = useWorkspaceSession(), isAdmin = user.roles.includes('ROLE_ADMIN');
  const [status,setStatus] = useState<WorkspaceSetupStatus | null>(null),[loading,setLoading] = useState(true),[error,setError] = useState(false);
  const refresh = useCallback(async () => {
    setLoading(true); setError(false);
    try { setStatus(await getWorkspaceSetupStatus()); }
    catch { setStatus(null); setError(true); }
    finally { setLoading(false); }
  },[]);
  useEffect(() => { void refresh(); },[refresh]);
  return <div className="ux-page">
    <PageHeader eyebrow="CONNECT YOUR PROJECT" title="Integrations" description="Create a key, send an event, and confirm that RootTrace received it." />
    <section className="ux-card"><SetupChecklist status={status} /></section>
    <div className="ux-section-intro"><span className="ux-step-tag">01</span><div><h2>Create a key for your service</h2><p>{isAdmin ? 'Use a separate named key for each service or integration.' : 'Ask a workspace administrator to create a key, then use the examples below.'}</p></div></div>
    <div className="legacy-panels"><IngestionKeysPanel isAdmin={isAdmin} onChanged={refresh} /></div>
    <ConnectionGuide />
    <section className="ux-card" id="connection-status"><div className="ux-section-heading"><div><p className="ux-eyebrow">STEP 3</p><h2>Confirm your connection</h2><p>These checks describe recorded ingestion, not continuous service health.</p></div><button className="ux-button secondary" disabled={loading} onClick={() => void refresh()}><RefreshCw size={15} aria-hidden="true" />{loading ? 'Checking…' : 'Check again'}</button></div>
      {error ? <div className="ux-notice warning" role="alert">Connection details are unavailable. Try again before assuming that data has arrived.</div> : <div className="ux-connection-status"><div><span>Requests accepted</span><strong>{loading || !status ? '—' : status.acceptedRequestCount.toLocaleString()}</strong><p>{status?.lastRequestAt ? 'Last request: '+new Date(status.lastRequestAt).toLocaleString() : 'No request recorded yet.'}</p></div><div><span>Events processed</span><strong>{loading || !status ? '—' : status.processedEventCount.toLocaleString()}</strong><p>{status?.lastProcessedAt ? 'Last processed: '+new Date(status.lastProcessedAt).toLocaleString() : 'Processing has not been confirmed yet.'}</p></div></div>}
      {status?.processedEventCount ? <div className="ux-section-heading ux-section-bottom"><p>Data has been processed. Alerts create incidents; logs provide investigation context.</p><Link href="/incidents" className="ux-button">View incidents<ArrowRight size={15} aria-hidden="true" /></Link></div> : <p className="ux-guide-note">Sent a request but still waiting? Check the response, then refresh. An administrator or SRE can inspect queued work in <Link className="ux-link" href="/settings">Settings</Link>.</p>}
    </section>
    <p className="ux-subtle-note">Currently supported: log and alert webhooks. Provider-specific connectors and deployment ingestion are not configured through this guide.</p>
  </div>;
}
