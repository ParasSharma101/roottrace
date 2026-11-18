'use client';

import { useEffect, useRef, useState } from 'react';
import { getTaskRedrives, getWorkspaceTask, listWorkspaceTasks, redriveWorkspaceTask,
  TaskKind, TaskRedrive, TaskStatus, WorkspaceTask } from '@/lib/api';

export function TaskRecoveryPanel() {
  const [status, setStatus] = useState<TaskStatus>('FAILED');
  const [kind, setKind] = useState<TaskKind | ''>('');
  const [tasks, setTasks] = useState<WorkspaceTask[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const [selected, setSelected] = useState<WorkspaceTask | null>(null);
  const [history, setHistory] = useState<TaskRedrive[]>([]);
  const [historyNext, setHistoryNext] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const listRequest = useRef(0), detailRequest = useRef(0);

  const load = async (before?: string) => {
    const request = ++listRequest.current;
    setLoading(true); setError(null);
    try {
      const page = await listWorkspaceTasks(status, kind || undefined, before);
      if (request !== listRequest.current) return;
      setTasks(current => before ? [...current, ...page.items] : page.items);
      setNext(page.nextCursor);
    } catch { if (request === listRequest.current) setError('Unable to load tasks. Check your workspace access and try again.'); }
    finally { if (request === listRequest.current) setLoading(false); }
  };
  useEffect(() => {
    setTasks([]); setNext(null); setSelected(null); setHistory([]); setReason(''); setNotice(null);
    void load();
    return () => { listRequest.current++; detailRequest.current++; };
    // Filters intentionally start a new query and discard previous-page requests.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, kind]);

  const inspect = async (id: string) => {
    const request = ++detailRequest.current;
    setBusy(true); setError(null); setReason('');
    try {
      const [task, page] = await Promise.all([getWorkspaceTask(id), getTaskRedrives(id)]);
      if (request !== detailRequest.current) return;
      setSelected(task); setHistory(page.items); setHistoryNext(page.nextCursor);
    } catch { if (request === detailRequest.current) setError('Unable to load this task. Refresh and check your workspace access.'); }
    finally { if (request === detailRequest.current) setBusy(false); }
  };
  const moreHistory = async () => {
    if (!selected || !historyNext) return;
    setBusy(true); setError(null);
    try {
      const page = await getTaskRedrives(selected.id, historyNext);
      setHistory(current => [...current, ...page.items]); setHistoryNext(page.nextCursor);
    } catch { setError('Unable to load more recovery history.'); }
    finally { setBusy(false); }
  };
  const retry = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selected || busy || selected.status !== 'FAILED' || !reason.trim()) return;
    setBusy(true); setError(null); setNotice(null);
    try {
      const queued = await redriveWorkspaceTask(selected, reason.trim());
      setSelected(queued); setReason('');
      setNotice('Retry queued. Refresh to check its progress.');
      const page = await getTaskRedrives(queued.id);
      setHistory(page.items); setHistoryNext(page.nextCursor);
      await load();
    } catch { setError('Retry could not be confirmed. Refresh this task and its history before trying again.'); }
    finally { setBusy(false); }
  };

  return <section className="p-6 rounded-2xl bg-[#0b101b] border border-slate-800 space-y-4">
    <div>
      <h2 className="text-sm font-semibold text-slate-200">Task recovery</h2>
      <p className="mt-2 text-xs text-slate-400">Review failed ingestion and investigation tasks after fixing the cause. Every retry records your account and reason.</p>
    </div>
    <div className="flex flex-wrap gap-2 text-xs">
      <label className="sr-only" htmlFor="task-status">Task status</label>
      <select id="task-status" value={status} disabled={busy || loading} onChange={e => setStatus(e.target.value as TaskStatus)} className="rounded bg-slate-900 border border-slate-700 p-2">
        <option value="FAILED">Failed</option><option value="PENDING">Pending</option><option value="RUNNING">Running</option><option value="SUCCEEDED">Succeeded</option>
      </select>
      <label className="sr-only" htmlFor="task-kind">Task kind</label>
      <select id="task-kind" value={kind} disabled={busy || loading} onChange={e => setKind(e.target.value as TaskKind | '')} className="rounded bg-slate-900 border border-slate-700 p-2">
        <option value="">All task types</option><option value="PUBLISH">Ingestion publication</option><option value="INVESTIGATE">Investigation</option>
      </select>
      <button type="button" disabled={busy || loading} onClick={() => { void load(); if (selected) void inspect(selected.id); }} className="rounded bg-slate-800 px-3 py-2 disabled:opacity-50">Refresh</button>
    </div>
    {error && <p role="alert" className="text-xs text-rose-300">{error}</p>}
    {notice && <p role="status" className="text-xs text-emerald-300">{notice}</p>}
    {loading && <p className="text-xs text-slate-400">Loading tasks...</p>}
    {!loading && tasks.length === 0 && !error && <p className="text-xs text-slate-400">No tasks match these filters.</p>}
    <ul className="divide-y divide-slate-800">{tasks.map(task => <li key={task.id} className="py-3 flex items-start justify-between gap-3 text-xs">
      <div className="min-w-0 space-y-1">
        <p>{task.kind === 'PUBLISH' ? 'Ingestion publication' : 'Incident investigation'} · {task.status.toLowerCase()}</p>
        <p className="font-mono text-slate-500 break-all">{task.id}</p>
        <p className="text-slate-400">{task.attempts}/5 attempts this cycle · {task.totalAttempts} lifetime · {task.redriveCount} recoveries</p>
        {task.lastErrorCode && <p className="text-amber-200 break-all">{task.lastErrorCode}</p>}
      </div>
      <button type="button" disabled={busy || loading} onClick={() => { setNotice(null); void inspect(task.id); }} className="shrink-0 rounded bg-slate-800 px-3 py-2 disabled:opacity-50">Inspect</button>
    </li>)}</ul>
    {next && <button type="button" disabled={busy || loading} onClick={() => void load(next)} className="rounded bg-slate-800 px-3 py-2 text-xs disabled:opacity-50">Load older tasks</button>}
    {selected && <div className="rounded-xl border border-slate-700 p-4 space-y-4 text-xs">
      <div className="flex justify-between gap-3">
        <div><h3 className="font-medium">Task details</h3><p className="mt-1 font-mono break-all text-slate-400">{selected.id}</p><p className="mt-1">Status: {selected.status.toLowerCase()}</p></div>
        <button type="button" disabled={busy} onClick={() => { detailRequest.current++; setSelected(null); setReason(''); }} className="self-start text-slate-400">Close</button>
      </div>
      {selected.incidentId && <a href={`/incidents/${selected.incidentId}`} className="inline-block text-indigo-300">Open incident</a>}
      {selected.status === 'FAILED' && <form onSubmit={retry} className="space-y-2">
        <p className="text-slate-400">Queue up to five more attempts. Completed evidence stays preserved.</p>
        <label htmlFor="task-reason" className="block">Reason for retry</label>
        <textarea id="task-reason" required maxLength={500} value={reason} disabled={busy} onChange={e => setReason(e.target.value)} placeholder="What was fixed? Omit secrets and log payloads." className="w-full rounded bg-slate-900 border border-slate-700 p-2" />
        <button disabled={busy || !reason.trim()} className="rounded bg-indigo-600 px-3 py-2 disabled:opacity-50">{busy ? 'Queuing...' : 'Confirm retry'}</button>
      </form>}
      <h3 className="font-medium">Recovery history</h3>
      {history.length === 0 ? <p className="text-slate-400">No recoveries recorded.</p> : <ol className="space-y-3">{history.map(entry => <li key={entry.id} className="border-l border-slate-700 pl-3 space-y-1">
        <p className="break-all">Recovery {entry.cycleNumber} · {entry.actorEmail}</p><p className="text-slate-500">{new Date(entry.createdAt).toLocaleString()}</p>
        <p className="text-slate-300 whitespace-pre-wrap break-words">{entry.reason}</p>
        <p className="text-slate-400">Previous cycle: {entry.previousAttempts} attempts{entry.previousErrorCode ? ` · ${entry.previousErrorCode}` : ''}</p>
      </li>)}</ol>}
      {historyNext && <button type="button" disabled={busy} onClick={() => void moreHistory()} className="rounded bg-slate-800 px-3 py-2 disabled:opacity-50">Load older recoveries</button>}
    </div>}
  </section>;
}
