'use client';

import React, { useState } from 'react';
import { TimelineEvent, TimelineEventType } from '@/types';
import { EventTypeIcon } from './Badges';
import { ChevronDown, ChevronRight, Clock, Copy, Check } from 'lucide-react';

export function TimelineView({ events }: { events: TimelineEvent[] }) {
  const [filter, setFilter] = useState<string>('ALL');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const filterTabs = [
    { label: 'All Events', value: 'ALL' },
    { label: 'Alerts', value: 'ALERT' },
    { label: 'Releases & PRs', value: 'RELEASES' },
    { label: 'Error Logs', value: 'LOG_ERROR' },
    { label: 'AI Hypotheses', value: 'AI_HYPOTHESIS' },
    { label: 'Human Actions', value: 'HUMAN_ACTION' },
  ];

  const filteredEvents = events.filter((ev) => {
    if (filter === 'ALL') return true;
    if (filter === 'RELEASES') return ev.eventType === 'DEPLOYMENT' || ev.eventType === 'GITHUB_PR';
    return ev.eventType === filter;
  });

  const toggleExpand = (id: string) => {
    setExpandedId((prev) => (prev === id ? null : id));
  };

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const getBorderColor = (type: TimelineEventType) => {
    switch (type) {
      case 'DEPLOYMENT': return 'border-l-sky-500 bg-sky-950/10';
      case 'GITHUB_PR': return 'border-l-purple-500 bg-purple-950/10';
      case 'ALERT': return 'border-l-rose-500 bg-rose-950/15';
      case 'LOG_ERROR': return 'border-l-red-500 bg-red-950/15';
      case 'AI_HYPOTHESIS': return 'border-l-amber-500 bg-amber-950/15';
      case 'HUMAN_ACTION': return 'border-l-emerald-500 bg-emerald-950/10';
      default: return 'border-l-slate-600 bg-slate-900/40';
    }
  };

  return (
    <div className="space-y-4">
      {/* Filter Chips */}
      <div className="flex flex-wrap items-center gap-2 pb-2 border-b border-slate-800">
        {filterTabs.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setFilter(tab.value)}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
              filter === tab.value
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-slate-800/60 text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            {tab.label}
          </button>
        ))}
        <span className="ml-auto text-xs text-slate-500">
          Showing {filteredEvents.length} of {events.length} events
        </span>
      </div>

      {/* Timeline track */}
      {filteredEvents.length === 0 ? (
        <div className="text-center py-12 text-slate-500 text-sm">
          No events match the selected filter.
        </div>
      ) : (
        <div className="relative pl-6 space-y-4 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-slate-800">
          {filteredEvents.map((ev) => {
            const isExpanded = expandedId === ev.id;
            const hasMetadata = ev.metadata && Object.keys(ev.metadata).length > 0;
            const date = new Date(ev.timestamp);
            const timeStr = date.toLocaleTimeString([], { hour12: false });
            const dateStr = date.toLocaleDateString([], { month: 'short', day: 'numeric' });

            return (
              <div key={ev.id} className="relative group">
                {/* Node icon marker */}
                <div className="absolute -left-[27px] top-3.5 w-6 h-6 rounded-full bg-slate-900 border border-slate-700 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <EventTypeIcon type={ev.eventType} />
                </div>

                {/* Event Card */}
                <div
                  className={`rounded-lg border border-slate-800 border-l-4 ${getBorderColor(
                    ev.eventType
                  )} p-4 transition-all`}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs font-mono font-medium px-2 py-0.5 rounded bg-slate-800/80 text-slate-300">
                          {ev.eventType}
                        </span>
                        <h4 className="text-sm font-semibold text-slate-200">{ev.title}</h4>
                      </div>

                      <p className="text-xs text-slate-400 whitespace-pre-wrap leading-relaxed">
                        {ev.description}
                      </p>
                    </div>

                    <div className="flex flex-col items-end gap-1 shrink-0">
                      <div className="flex items-center gap-1 text-[11px] font-mono text-slate-400">
                        <Clock className="w-3 h-3" />
                        <span>{timeStr}</span>
                      </div>
                      <span className="text-[10px] text-slate-500">{dateStr}</span>

                      {hasMetadata && (
                        <button
                          onClick={() => toggleExpand(ev.id)}
                          className="mt-1 flex items-center gap-1 text-[11px] text-indigo-400 hover:text-indigo-300 font-medium"
                        >
                          {isExpanded ? (
                            <>
                              <ChevronDown className="w-3.5 h-3.5" /> Less
                            </>
                          ) : (
                            <>
                              <ChevronRight className="w-3.5 h-3.5" /> Details
                            </>
                          )}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Expanded Metadata / Stack Trace / Diff */}
                  {isExpanded && hasMetadata && (
                    <div className="mt-3 pt-3 border-t border-slate-800/80">
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="text-[11px] font-mono uppercase tracking-wider text-slate-500">
                          Payload & Metadata
                        </span>
                        <button
                          onClick={() => handleCopy(ev.id, JSON.stringify(ev.metadata, null, 2))}
                          className="flex items-center gap-1 text-[10px] text-slate-400 hover:text-white"
                        >
                          {copiedId === ev.id ? (
                            <>
                              <Check className="w-3 h-3 text-emerald-400" /> Copied
                            </>
                          ) : (
                            <>
                              <Copy className="w-3 h-3" /> Copy JSON
                            </>
                          )}
                        </button>
                      </div>
                      <pre className="text-[11px] font-mono bg-slate-950 p-3 rounded border border-slate-800/80 overflow-x-auto text-slate-300 max-h-48">
                        {JSON.stringify(ev.metadata, null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
