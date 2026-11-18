'use client';

import { useEffect, useRef, useState } from 'react';
import { getQueueHealth, QueueHealth } from '@/lib/api';

export function QueueHealthPanel() {
  const [summary,setSummary]=useState<QueueHealth | null>(null);
  const [loading,setLoading]=useState(false), [error,setError]=useState(false);
  const request=useRef(0);
  const refresh=async () => {
    const current=++request.current;
    setLoading(true); setError(false);
    try { const value=await getQueueHealth(); if(current===request.current) setSummary(value); }
    catch { if(current===request.current) setError(true); }
    finally { if(current===request.current) setLoading(false); }
  };
  useEffect(() => { void refresh(); return () => { request.current++; }; },[]);
  return <div className="rounded-xl border border-slate-800 p-4 space-y-3 text-xs">
    <div className="flex items-center justify-between gap-3"><h3 className="font-medium">Queue health</h3>
      <button type="button" disabled={loading} onClick={() => void refresh()} className="rounded bg-slate-800 px-3 py-2 disabled:opacity-50">{loading?'Checking...':'Refresh health'}</button></div>
    {error && <p role="alert" className="text-rose-300">Queue health could not be refreshed. Any figures below are from the previous check.</p>}
    {summary && <>
      <p className="text-slate-500">Last checked: {new Date(summary.observedAt).toLocaleString()}</p>
      <div className="grid gap-3 sm:grid-cols-2">{summary.queues.map(queue => <div key={queue.kind} className="space-y-1">
        <p className="text-slate-200">{queue.kind==='PUBLISH'?'Ingestion publication':'Investigation'}</p>
        <p className="text-slate-400">{queue.pending} pending · {queue.running} running · {queue.failed} failed</p>
        <p className="text-slate-400">{queue.ready} ready · oldest ready wait {queue.oldestReadyAgeSeconds}s</p>
      </div>)}</div>
      {summary.signals.length===0 ? <p className="text-emerald-300">No failed tasks, overdue ready work, or expired leases found in this check.</p> :
        <ul role="status" className="space-y-2 text-amber-200">{summary.signals.map(signal => <li key={`${signal.kind}:${signal.code}`}>
          {signal.kind==='PUBLISH'?'Ingestion':'Investigation'}: {signal.count} {signal.code==='FAILED_TASKS'?'failed tasks need review.':
            signal.code==='OVERDUE_PENDING'?`ready tasks have waited at least ${summary.pendingOverdueSeconds}s. Check workers and dependencies.`:
            'worker leases expired. Check worker availability and recovery.'}
        </li>)}</ul>}
      <p className="text-slate-500">These checks cover this workspace’s task queue. Broker delivery, DLQs, and late evidence need separate checks.</p>
    </>}
  </div>;
}
