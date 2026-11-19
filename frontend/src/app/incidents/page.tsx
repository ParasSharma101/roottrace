'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Activity, RefreshCw, Search } from 'lucide-react';
import { getIncidents } from '@/lib/api';
import type { IncidentStatus, IncidentSummary, SeverityLevel } from '@/types';
import { IncidentList } from '@/components/workspace/IncidentList';
import { PageHeader } from '@/components/workspace/PageHeader';

export default function IncidentsPage() {
  const [incidents,setIncidents] = useState<IncidentSummary[]>([]),[loading,setLoading] = useState(true);
  const [status,setStatus] = useState<IncidentStatus | 'ALL'>('ALL'),[severity,setSeverity] = useState<SeverityLevel | 'ALL'>('ALL');
  const [service,setService] = useState(''),[query,setQuery] = useState(''),[error,setError] = useState(false);
  const request = useRef(0);
  const load = useCallback(async () => {
    const current = ++request.current;
    setLoading(true); setError(false);
    try {
      const data = await getIncidents(status === 'ALL' ? undefined : status,severity === 'ALL' ? undefined : severity,query || undefined);
      if(current === request.current) setIncidents(data);
    } catch { if(current === request.current) { setError(true); setIncidents([]); } }
    finally { if(current === request.current) setLoading(false); }
  },[status,severity,query]);
  useEffect(() => { void load(); return () => { request.current++; }; },[load]);
  const filtered = status !== 'ALL' || severity !== 'ALL' || !!query;
  const clear = () => { setStatus('ALL'); setSeverity('ALL'); setService(''); setQuery(''); };
  return <div className="ux-page">
    <PageHeader eyebrow="INVESTIGATE" title="Incidents" description="Find what happened, follow the evidence, and decide what to do next." actions={<button className="ux-button secondary" onClick={() => void load()} disabled={loading}><RefreshCw size={15} aria-hidden="true" />Refresh</button>} />
    <section className="ux-card ux-card-flush">
      <div className="ux-filterbar"><div className="ux-filter-field"><label htmlFor="incident-status">Status</label><select id="incident-status" value={status} onChange={event => setStatus(event.target.value as IncidentStatus | 'ALL')}><option value="ALL">All statuses</option><option value="OPEN">Open</option><option value="INVESTIGATING">Investigating</option><option value="RESOLVED">Resolved</option></select></div><div className="ux-filter-field"><label htmlFor="incident-severity">Severity</label><select id="incident-severity" value={severity} onChange={event => setSeverity(event.target.value as SeverityLevel | 'ALL')}><option value="ALL">All severities</option><option value="SEV1">SEV1 · Critical</option><option value="SEV2">SEV2 · High</option><option value="SEV3">SEV3 · Medium</option><option value="SEV4">SEV4 · Low</option></select></div>
        <form className="ux-search" onSubmit={event => { event.preventDefault(); setQuery(service.trim()); }}><label htmlFor="incident-service">Service name</label><div><Search size={16} aria-hidden="true" /><input id="incident-service" placeholder="Exact service name" value={service} onChange={event => setService(event.target.value)} /><button type="submit">Search</button></div></form>
      </div>
      {filtered && <div className="ux-filter-summary">Showing filtered results<button onClick={clear}>Clear filters</button></div>}
      {loading ? <div className="ux-empty" role="status">Loading incidents…</div> : error ? <div className="ux-empty" role="alert"><h2>We couldn’t load incidents.</h2><p>Check your connection and refresh to try again.</p></div> : incidents.length ? <><div className="ux-list-count">{incidents.length} {incidents.length === 1 ? 'incident' : 'incidents'}{filtered ? ' matching these filters' : ' in this workspace'}</div><IncidentList incidents={incidents} /></> : <div className="ux-empty"><Activity size={28} aria-hidden="true" /><h2>{filtered ? 'No matching incidents' : 'Your first investigation starts with an alert.'}</h2><p>{filtered ? 'Try another service name or clear the filters.' : 'Logs provide context. Alerts create incidents for your team to investigate.'}</p>{filtered ? <button onClick={clear} className="ux-button secondary">Clear filters</button> : <Link href="/integrations#send-data" className="ux-button">Connect alerts</Link>}</div>}
    </section>
  </div>;
}
