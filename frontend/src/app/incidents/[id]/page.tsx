'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ArrowLeft, Bell, FileText, ListOrdered, MessageSquare, RefreshCw, Send, Sparkles } from 'lucide-react';
import { getIncident, getTimeline, getPostmortem, verifyHypothesis, rejectHypothesis, addIncidentNote, updateIncidentStatus } from '@/lib/api';
import type { IncidentDetail, IncidentStatus, Postmortem, TimelineEvent } from '@/types';
import { StatusBadge, SeverityBadge } from '@/components/Badges';
import { TimelineView } from '@/components/TimelineView';
import { HypothesisCard } from '@/components/HypothesisCard';
import { PostmortemViewer } from '@/components/PostmortemViewer';
import { useWorkspaceSession } from '@/components/workspace/WorkspaceSession';
import { PageHeader } from '@/components/workspace/PageHeader';

type Tab = 'TIMELINE' | 'HYPOTHESES' | 'ALERTS' | 'NOTES' | 'POSTMORTEM';
export default function IncidentInvestigationPage() {
  const params = useParams(), user = useWorkspaceSession();
  const incidentId = params.id as string;
  const canReview = user.roles.includes('ROLE_ADMIN') || user.roles.includes('ROLE_SRE');
  const [incident,setIncident] = useState<IncidentDetail | null>(null),[timeline,setTimeline] = useState<TimelineEvent[]>([]);
  const [report,setReport] = useState<Postmortem | null>(null),[reportError,setReportError] = useState(false),[reportLoading,setReportLoading] = useState(false);
  const [tab,setTab] = useState<Tab>('TIMELINE'),[loading,setLoading] = useState(true),[error,setError] = useState(false);
  const [actionError,setActionError] = useState<string | null>(null),[note,setNote] = useState(''),[busy,setBusy] = useState(false);
  const request = useRef(0);
  const reportRequest = useRef(0);
  const load = useCallback(async () => {
    const current = ++request.current; setLoading(true); setError(false);
    try {
      const [detail,events] = await Promise.all([getIncident(incidentId),getTimeline(incidentId)]);
      if(current === request.current) { setIncident(detail); setTimeline(events); }
    } catch { if(current === request.current) setError(true); }
    finally { if(current === request.current) setLoading(false); }
  },[incidentId]);
  useEffect(() => {
    setIncident(null); setTimeline([]); setReport(null); setReportError(false); setReportLoading(false); setActionError(null); setNote(''); setTab('TIMELINE');
    void load(); return () => { request.current++; reportRequest.current++; };
  },[load]);
  const loadReport = useCallback(async () => {
    const current = ++reportRequest.current;
    setReportLoading(true); setReportError(false);
    try { const nextReport = await getPostmortem(incidentId); if(current === reportRequest.current) setReport(nextReport); }
    catch { if(current === reportRequest.current) setReportError(true); }
    finally { if(current === reportRequest.current) setReportLoading(false); }
  },[incidentId]);
  useEffect(() => { if(tab === 'POSTMORTEM') void loadReport(); },[tab,loadReport]);
  const review = async (id: string,reason?: string) => {
    if(!canReview) return;
    if(reason !== undefined) await rejectHypothesis(incidentId,id,user.fullName,reason);
    else await verifyHypothesis(incidentId,id,user.fullName);
    setReport(null); await load();
  };
  const addNote = async (event: React.FormEvent) => {
    event.preventDefault(); if(!note.trim() || busy || !canReview) return;
    setBusy(true); setActionError(null);
    try { await addIncidentNote(incidentId,user.fullName,note.trim()); setNote(''); await load(); }
    catch { setActionError('Your note could not be saved. Check your access and try again.'); }
    finally { setBusy(false); }
  };
  const changeStatus = async (status: IncidentStatus) => {
    if(!canReview || busy) return; setBusy(true); setActionError(null);
    try { await updateIncidentStatus(incidentId,status); setReport(null); await load(); }
    catch { setActionError('The status change could not be confirmed. Refresh before trying again.'); }
    finally { setBusy(false); }
  };
  if(loading && !incident) return <div className="ux-card ux-empty" role="status">Loading incident…</div>;
  if(error || !incident) return <div className="ux-page"><Link href="/incidents" className="ux-link"><ArrowLeft size={14} />Back to incidents</Link><div className="ux-card ux-empty" role="alert"><h1>We couldn’t open this incident.</h1><p>It may be unavailable in this workspace, or the connection may have failed.</p><button className="ux-button secondary" onClick={() => void load()}>Try again</button></div></div>;
  const tabs = [
    { key:'TIMELINE',label:'Timeline',count:timeline.length,icon:ListOrdered },
    { key:'HYPOTHESES',label:'Hypotheses',count:incident.hypotheses.length,icon:Sparkles },
    { key:'ALERTS',label:'Alerts',count:incident.alerts.length,icon:Bell },
    { key:'NOTES',label:'Notes',count:incident.notes.length,icon:MessageSquare },
    { key:'POSTMORTEM',label:'Postmortem',count:null,icon:FileText },
  ];
  return <div className="ux-page legacy-panels">
    <Link href="/incidents" className="ux-link"><ArrowLeft size={14} aria-hidden="true" />Back to incidents</Link>
    <PageHeader eyebrow="INCIDENT INVESTIGATION" title={incident.title} description="Follow the timeline, review the evidence, and record your findings." actions={<button className="ux-button secondary" disabled={loading} onClick={() => void load()}><RefreshCw size={15} aria-hidden="true" />Refresh</button>} />
    <section className="ux-card ux-investigation-summary"><div><StatusBadge status={incident.status} /><SeverityBadge severity={incident.severityLevel} /><span className="ux-service-tag">{incident.serviceName}</span></div><label>Incident status<select value={incident.status} disabled={!canReview || busy} onChange={event => void changeStatus(event.target.value as IncidentStatus)}><option value="OPEN">Open</option><option value="INVESTIGATING">Investigating</option><option value="RESOLVED">Resolved</option></select></label><p>Started {new Date(incident.startTime).toLocaleString()}{incident.resolutionTime && ' · Resolved '+new Date(incident.resolutionTime).toLocaleString()}</p>{incident.summary && <p className="ux-incident-summary-text">{incident.summary}</p>}</section>
    {!canReview && <div className="ux-notice">You have viewer access. You can explore the evidence; an SRE or administrator can change status, review hypotheses, and add notes.</div>}
    {actionError && <div className="ux-notice warning" role="alert">{actionError}</div>}
    <div className="ux-investigation-tabs" role="tablist" aria-label="Investigation sections" onKeyDown={event => {
      if(!['ArrowRight','ArrowLeft','Home','End'].includes(event.key)) return;
      event.preventDefault();
      const index = tabs.findIndex(item => item.key === tab);
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length-1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
      setTab(tabs[next].key as Tab);
      event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
    }}>{tabs.map(item => <button key={item.key} role="tab" id={'tab-'+item.key} aria-selected={tab === item.key} tabIndex={tab === item.key ? 0 : -1} aria-controls={'panel-'+item.key} onClick={() => setTab(item.key as Tab)}><item.icon size={16} aria-hidden="true" />{item.label}{item.count !== null && <span>{item.count}</span>}</button>)}</div>
    <section role="tabpanel" id={'panel-'+tab} aria-labelledby={'tab-'+tab} className="ux-investigation-panel">
      {tab === 'TIMELINE' && <div className="ux-card"><div className="ux-section-heading"><div><h2>What changed, and when?</h2><p>Expand an event to inspect its supporting details.</p></div></div><TimelineView events={timeline} /></div>}
      {tab === 'HYPOTHESES' && <div className="space-y-4"><p className="ux-guide-note">Hypotheses are suggestions, not confirmed root causes. Review their cited evidence before verifying or rejecting them.</p>{incident.hypotheses.length ? incident.hypotheses.map(hypothesis => <HypothesisCard key={hypothesis.id} hypothesis={hypothesis} canReview={canReview} onVerify={id => review(id)} onReject={(id,reason) => review(id,reason)} />) : <div className="ux-card ux-empty"><Sparkles size={26} /><h2>No hypotheses yet</h2><p>Investigation may still be processing, or there may be insufficient evidence. Refresh to check again.</p></div>}</div>}
      {tab === 'ALERTS' && <div className="ux-card"><div className="ux-section-heading"><div><h2>Alerts behind this incident</h2><p>Source signals that triggered or joined this investigation.</p></div></div>{incident.alerts.length ? <ul className="ux-alert-list">{incident.alerts.map(alert => <li key={alert.id}><div><strong>{alert.alertType}</strong><span>{alert.source} · {new Date(alert.triggeredAt).toLocaleString()}</span></div><details><summary>View alert payload</summary><pre>{alert.rawPayload}</pre></details></li>)}</ul> : <p className="ux-guide-note">No alerts are linked to this incident.</p>}</div>}
      {tab === 'NOTES' && <div className="ux-card"><div className="ux-section-heading"><div><h2>Findings and activity</h2><p>Keep investigation notes and recorded actions together.</p></div></div>{canReview && <form onSubmit={addNote} className="ux-note-form"><label htmlFor="investigation-note">Add a finding</label><textarea id="investigation-note" value={note} onChange={event => setNote(event.target.value)} required disabled={busy} placeholder="What did you discover, change, or rule out?" /><div><span>Posting as {user.fullName}</span><button className="ux-button" disabled={busy || !note.trim()}><Send size={14} aria-hidden="true" />{busy ? 'Saving…' : 'Add note'}</button></div></form>}{incident.notes.length ? <ul className="ux-notes-list">{incident.notes.map(item => <li key={item.id}><div><strong>{item.author}</strong><time>{new Date(item.createdAt).toLocaleString()}</time></div><p>{item.note}</p></li>)}</ul> : <p className="ux-guide-note">No notes recorded yet.</p>}</div>}
      {tab === 'POSTMORTEM' && (reportLoading ? <div className="ux-card ux-empty" role="status">Loading the postmortem…</div> : reportError ? <div className="ux-card ux-empty" role="alert"><h2>Postmortem unavailable</h2><p>We couldn’t load the report. Try again.</p><button className="ux-button secondary" onClick={() => void loadReport()}>Retry</button></div> : report ? <PostmortemViewer postmortem={report} /> : null)}
    </section>
  </div>;
}
