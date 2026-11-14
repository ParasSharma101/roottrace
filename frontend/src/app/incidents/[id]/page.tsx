'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { 
  IncidentDetail, 
  TimelineEvent, 
  Postmortem, 
  IncidentStatus 
} from '@/types';
import { 
  getIncident, 
  getTimeline, 
  getPostmortem, 
  verifyHypothesis, 
  rejectHypothesis, 
  addIncidentNote, 
  updateIncidentStatus 
} from '@/lib/api';
import { StatusBadge, SeverityBadge } from '@/components/Badges';
import { TimelineView } from '@/components/TimelineView';
import { HypothesisCard } from '@/components/HypothesisCard';
import { PostmortemViewer } from '@/components/PostmortemViewer';
import { 
  ArrowLeft, 
  Clock, 
  Sparkles, 
  ListOrdered, 
  Bell, 
  MessageSquare, 
  FileText, 
  RefreshCw, 
  Send 
} from 'lucide-react';

export default function IncidentInvestigationPage() {
  const params = useParams();
  const incidentId = params?.id as string;

  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [postmortem, setPostmortem] = useState<Postmortem | null>(null);
  const [activeTab, setActiveTab] = useState<'TIMELINE' | 'HYPOTHESES' | 'ALERTS' | 'NOTES' | 'POSTMORTEM'>('TIMELINE');

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // New Note State
  const [authorName, setAuthorName] = useState('On-Call SRE');
  const [newNote, setNewNote] = useState('');
  const [submittingNote, setSubmittingNote] = useState(false);

  const loadAll = useCallback(async () => {
    if (!incidentId) return;
    try {
      setLoading(true);
      setError(null);

      const [incData, tlData] = await Promise.all([
        getIncident(incidentId),
        getTimeline(incidentId),
      ]);

      setIncident(incData);
      setTimeline(tlData);
    } catch (err: any) {
      setError(err.message || 'Failed to load incident investigation details');
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  // Load postmortem when its tab is clicked
  useEffect(() => {
    if (activeTab === 'POSTMORTEM' && incidentId && !postmortem) {
      getPostmortem(incidentId)
        .then(setPostmortem)
        .catch((err) => console.error('Failed to load postmortem', err));
    }
  }, [activeTab, incidentId, postmortem]);

  const handleVerifyHypothesis = async (hypId: string) => {
    await verifyHypothesis(incidentId, hypId, authorName);
    await loadAll();
    // Refresh postmortem if cached
    setPostmortem(null);
  };

  const handleRejectHypothesis = async (hypId: string, reason: string) => {
    await rejectHypothesis(incidentId, hypId, authorName, reason);
    await loadAll();
    setPostmortem(null);
  };

  const handleAddNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newNote.trim()) return;

    try {
      setSubmittingNote(true);
      await addIncidentNote(incidentId, authorName, newNote.trim());
      setNewNote('');
      await loadAll();
    } finally {
      setSubmittingNote(false);
    }
  };

  const handleStatusChange = async (newStatus: IncidentStatus) => {
    try {
      const updated = await updateIncidentStatus(incidentId, newStatus);
      setIncident(updated);
      await loadAll();
      setPostmortem(null);
    } catch (err: any) {
      alert('Failed to update status: ' + err.message);
    }
  };

  if (loading && !incident) {
    return (
      <div className="text-center py-24 text-slate-400 text-sm flex items-center justify-center gap-2">
        <RefreshCw className="w-5 h-5 animate-spin text-indigo-400" />
        Loading incident investigation room...
      </div>
    );
  }

  if (error || !incident) {
    return (
      <div className="space-y-4">
        <Link href="/" className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-white">
          <ArrowLeft className="w-4 h-4" /> Back to Command Center
        </Link>
        <div className="p-6 rounded-xl bg-rose-950/20 border border-rose-800 text-rose-300 text-sm">
          {error || 'Incident not found'}
        </div>
      </div>
    );
  }

  const startDate = new Date(incident.startTime);

  return (
    <div className="space-y-6">
      {/* Top Bar with Back Link and Live Refresh */}
      <div className="flex items-center justify-between">
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-400 hover:text-white transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Command Center
        </Link>

        <button
          onClick={loadAll}
          className="flex items-center gap-1 text-xs text-slate-400 hover:text-white bg-slate-900 border border-slate-800 px-2.5 py-1 rounded"
        >
          <RefreshCw className="w-3.5 h-3.5 text-indigo-400" /> Refresh State
        </button>
      </div>

      {/* Incident Header Card */}
      <div className="p-6 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-4 shadow-xl">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="space-y-2">
            <div className="flex items-center gap-2 flex-wrap">
              <StatusBadge status={incident.status} />
              <SeverityBadge severity={incident.severityLevel} />
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-200 border border-slate-700">
                {incident.serviceName}
              </span>
            </div>

            <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
              {incident.title}
            </h1>
          </div>

          {/* Incident Status Transitions */}
          <div className="flex items-center gap-3 shrink-0">
            <span className="text-xs text-slate-400 font-medium">Status Transition:</span>
            <select
              value={incident.status}
              onChange={(e) => handleStatusChange(e.target.value as IncidentStatus)}
              className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs font-medium text-slate-200 focus:outline-none focus:border-indigo-500"
            >
              <option value="INVESTIGATING">INVESTIGATING</option>
              <option value="OPEN">OPEN</option>
              <option value="RESOLVED">RESOLVED</option>
            </select>
          </div>
        </div>

        <div className="flex items-center gap-6 pt-3 border-t border-slate-800/80 text-xs text-slate-400 flex-wrap">
          <span className="flex items-center gap-1.5 font-mono">
            <Clock className="w-3.5 h-3.5 text-slate-500" />
            Started: {startDate.toUTCString()}
          </span>

          {incident.resolutionTime ? (
            <span className="text-emerald-400 font-medium">
              Resolved: {new Date(incident.resolutionTime).toUTCString()}
            </span>
          ) : (
            <span className="text-amber-400 font-medium flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
              Incident In Progress
            </span>
          )}

          <span className="text-slate-500 font-mono">ID: {incident.id}</span>
        </div>
      </div>

      {/* Workspace Tabs */}
      <div className="flex items-center gap-2 border-b border-slate-800 overflow-x-auto pb-1">
        <button
          onClick={() => setActiveTab('TIMELINE')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-t-lg text-xs font-semibold transition-all border-b-2 ${
            activeTab === 'TIMELINE'
              ? 'text-indigo-400 border-indigo-500 bg-slate-900/60'
              : 'text-slate-400 border-transparent hover:text-slate-200'
          }`}
        >
          <ListOrdered className="w-4 h-4" />
          Correlated Timeline ({timeline.length})
        </button>

        <button
          onClick={() => setActiveTab('HYPOTHESES')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-t-lg text-xs font-semibold transition-all border-b-2 ${
            activeTab === 'HYPOTHESES'
              ? 'text-indigo-400 border-indigo-500 bg-slate-900/60'
              : 'text-slate-400 border-transparent hover:text-slate-200'
          }`}
        >
          <Sparkles className="w-4 h-4 text-amber-400" />
          AI Root Causes ({incident.hypotheses.length})
        </button>

        <button
          onClick={() => setActiveTab('ALERTS')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-t-lg text-xs font-semibold transition-all border-b-2 ${
            activeTab === 'ALERTS'
              ? 'text-indigo-400 border-indigo-500 bg-slate-900/60'
              : 'text-slate-400 border-transparent hover:text-slate-200'
          }`}
        >
          <Bell className="w-4 h-4 text-rose-400" />
          Alerts ({incident.alerts.length})
        </button>

        <button
          onClick={() => setActiveTab('NOTES')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-t-lg text-xs font-semibold transition-all border-b-2 ${
            activeTab === 'NOTES'
              ? 'text-indigo-400 border-indigo-500 bg-slate-900/60'
              : 'text-slate-400 border-transparent hover:text-slate-200'
          }`}
        >
          <MessageSquare className="w-4 h-4 text-emerald-400" />
          SRE Collaboration & Audit ({incident.notes.length})
        </button>

        <button
          onClick={() => setActiveTab('POSTMORTEM')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-t-lg text-xs font-semibold transition-all border-b-2 ${
            activeTab === 'POSTMORTEM'
              ? 'text-indigo-400 border-indigo-500 bg-slate-900/60'
              : 'text-slate-400 border-transparent hover:text-slate-200'
          }`}
        >
          <FileText className="w-4 h-4 text-sky-400" />
          Automated Postmortem
        </button>
      </div>

      {/* Tab Panels */}
      <div>
        {/* Tab 1: Timeline */}
        {activeTab === 'TIMELINE' && (
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800">
            <TimelineView events={timeline} />
          </div>
        )}

        {/* Tab 2: AI Hypotheses */}
        {activeTab === 'HYPOTHESES' && (
          <div className="space-y-4">
            {incident.hypotheses.length === 0 ? (
              <div className="p-12 text-center rounded-2xl border border-dashed border-slate-800 space-y-2">
                <Sparkles className="w-8 h-8 text-amber-400/60 mx-auto" />
                <h3 className="text-sm font-semibold text-slate-300">No Hypotheses Generated Yet</h3>
                <p className="text-xs text-slate-500">
                  AI Orchestrator is analyzing the correlated timeline bundle.
                </p>
              </div>
            ) : (
              incident.hypotheses.map((h) => (
                <HypothesisCard
                  key={h.id}
                  hypothesis={h}
                  onVerify={handleVerifyHypothesis}
                  onReject={handleRejectHypothesis}
                />
              ))
            )}
          </div>
        )}

        {/* Tab 3: Alerts */}
        {activeTab === 'ALERTS' && (
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800 space-y-4">
            <h3 className="text-sm font-semibold text-white">Ingested Alerts ({incident.alerts.length})</h3>
            <div className="space-y-3">
              {incident.alerts.map((a) => (
                <div key={a.id} className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Bell className="w-4 h-4 text-rose-400" />
                      <span className="text-sm font-semibold text-slate-200">{a.alertType}</span>
                      <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-400">
                        {a.source}
                      </span>
                    </div>
                    <span className="text-xs font-mono text-slate-400">
                      {new Date(a.triggeredAt).toUTCString()}
                    </span>
                  </div>
                  <pre className="text-[11px] font-mono bg-slate-900 p-2.5 rounded border border-slate-800 text-slate-300 overflow-x-auto">
                    {a.rawPayload}
                  </pre>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Tab 4: Collaboration Notes */}
        {activeTab === 'NOTES' && (
          <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800 space-y-6">
            <form onSubmit={handleAddNote} className="space-y-3 p-4 rounded-xl bg-slate-950 border border-slate-800">
              <h4 className="text-xs font-mono uppercase tracking-wider text-slate-400">
                Add Engineer Investigation Note
              </h4>
              <div className="flex items-center gap-3">
                <input
                  type="text"
                  placeholder="Your Name / Handle"
                  value={authorName}
                  onChange={(e) => setAuthorName(e.target.value)}
                  className="w-48 bg-slate-900 border border-slate-800 rounded px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
                />
                <input
                  type="text"
                  placeholder="Record findings, mitigation steps, or updates..."
                  value={newNote}
                  onChange={(e) => setNewNote(e.target.value)}
                  className="flex-1 bg-slate-900 border border-slate-800 rounded px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
                />
                <button
                  type="submit"
                  disabled={submittingNote || !newNote.trim()}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium disabled:opacity-50 transition-colors"
                >
                  <Send className="w-3.5 h-3.5" />
                  Post
                </button>
              </div>
            </form>

            <div className="space-y-3">
              <h4 className="text-xs font-mono uppercase tracking-wider text-slate-400">
                Audit & Collaboration Log ({incident.notes.length})
              </h4>
              {incident.notes.length === 0 ? (
                <div className="text-center py-8 text-slate-500 text-xs">
                  No notes recorded yet.
                </div>
              ) : (
                incident.notes.map((n) => (
                  <div key={n.id} className="p-3.5 rounded-lg bg-slate-950 border border-slate-800 space-y-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-indigo-400">{n.author}</span>
                      <span className="text-[11px] font-mono text-slate-500">
                        {new Date(n.createdAt).toLocaleTimeString()}
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 leading-relaxed">{n.note}</p>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* Tab 5: Automated Postmortem */}
        {activeTab === 'POSTMORTEM' && (
          <div>
            {!postmortem ? (
              <div className="p-12 text-center rounded-2xl border border-slate-800 text-slate-400 text-xs flex items-center justify-center gap-2">
                <RefreshCw className="w-4 h-4 animate-spin text-indigo-400" />
                Synthesizing postmortem from verified hypotheses and timeline...
              </div>
            ) : (
              <PostmortemViewer postmortem={postmortem} />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
