'use client';

import React, { useState } from 'react';
import { HypothesisDetail, HypothesisStatus } from '@/types';
import { Sparkles, CheckCircle2, XCircle, FileCode, Terminal, AlertCircle } from 'lucide-react';

interface HypothesisCardProps {
  hypothesis: HypothesisDetail;
  onVerify: (id: string) => Promise<void>;
  onReject: (id: string, reason: string) => Promise<void>;
}

export function HypothesisCard({ hypothesis, onVerify, onReject }: HypothesisCardProps) {
  const [loading, setLoading] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const confidencePercent = Math.round(hypothesis.confidenceScore * 100);

  let citations: any[] = [];
  try {
    citations = JSON.parse(hypothesis.evidenceCitations || '[]');
  } catch {
    citations = [];
  }

  const handleVerify = async () => {
    try {
      setLoading(true);
      await onVerify(hypothesis.id);
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async () => {
    if (!reason.trim()) return;
    try {
      setLoading(true);
      await onReject(hypothesis.id, reason);
      setRejecting(false);
    } finally {
      setLoading(false);
    }
  };

  const statusBorder = {
    PENDING: 'border-slate-800 bg-slate-900/60',
    VERIFIED: 'border-emerald-500/40 bg-emerald-950/15 ring-1 ring-emerald-500/30',
    REJECTED: 'border-rose-500/20 bg-rose-950/10 opacity-70',
  }[hypothesis.status];

  return (
    <div className={`rounded-xl border p-5 transition-all ${statusBorder}`}>
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Sparkles className="w-3.5 h-3.5" />
              AI Hypothesis
            </span>

            <span
              className={`text-xs px-2 py-0.5 rounded font-mono font-medium ${
                hypothesis.status === 'VERIFIED'
                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                  : hypothesis.status === 'REJECTED'
                  ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                  : 'bg-slate-800 text-slate-400'
              }`}
            >
              {hypothesis.status}
            </span>
          </div>

          <h3 className="text-base font-semibold text-slate-100 leading-snug">
            {hypothesis.description}
          </h3>
        </div>

        {/* Confidence Score Bar */}
        <div className="flex flex-col items-end shrink-0">
          <span className="text-xl font-bold font-mono text-indigo-400">{confidencePercent}%</span>
          <span className="text-[10px] text-slate-500 font-medium uppercase">Confidence</span>
          <div className="w-20 h-1.5 bg-slate-800 rounded-full mt-1.5 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                confidencePercent >= 80
                  ? 'bg-emerald-500'
                  : confidencePercent >= 50
                  ? 'bg-amber-500'
                  : 'bg-rose-500'
              }`}
              style={{ width: `${confidencePercent}%` }}
            />
          </div>
        </div>
      </div>

      {/* Grounded Evidence Citations */}
      {citations.length > 0 && (
        <div className="mt-4 pt-3 border-t border-slate-800/80">
          <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400 block mb-2">
            Grounded Evidence Citations ({citations.length})
          </span>
          <div className="flex flex-wrap gap-2">
            {citations.map((c, i) => (
              <div
                key={i}
                className="flex items-center gap-1.5 text-xs bg-slate-950/80 border border-slate-800 px-2.5 py-1 rounded text-slate-300"
              >
                {c.type === 'LOG' && <Terminal className="w-3.5 h-3.5 text-rose-400" />}
                {c.type === 'GITHUB_PR' && <FileCode className="w-3.5 h-3.5 text-purple-400" />}
                {c.type === 'METRIC' && <AlertCircle className="w-3.5 h-3.5 text-amber-400" />}
                <span className="font-mono text-slate-400 text-[11px]">[{c.type}]</span>
                <span className="font-mono text-indigo-300">{c.referenceId}</span>
                {c.detail && <span className="text-slate-400 text-[11px]">- {c.detail}</span>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Action Buttons for Human Verification */}
      <div className="mt-5 pt-3 border-t border-slate-800 flex items-center justify-between gap-3">
        <span className="text-xs text-slate-500">
          Generated at {new Date(hypothesis.createdAt).toLocaleTimeString()}
        </span>

        {hypothesis.status === 'PENDING' && (
          <div className="flex items-center gap-2">
            {rejecting ? (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder="Reason for rejecting..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="bg-slate-950 border border-slate-700 rounded px-2.5 py-1 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-rose-500"
                />
                <button
                  onClick={handleReject}
                  disabled={loading || !reason.trim()}
                  className="px-2.5 py-1 bg-rose-600 hover:bg-rose-500 text-white text-xs rounded font-medium disabled:opacity-50"
                >
                  Confirm Reject
                </button>
                <button
                  onClick={() => setRejecting(false)}
                  className="text-xs text-slate-400 hover:text-white"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <>
                <button
                  onClick={() => setRejecting(true)}
                  disabled={loading}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
                >
                  <XCircle className="w-3.5 h-3.5 text-rose-400" />
                  Reject
                </button>

                <button
                  onClick={handleVerify}
                  disabled={loading}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium shadow-sm transition-all"
                >
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  Verify Root Cause
                </button>
              </>
            )}
          </div>
        )}

        {hypothesis.status === 'VERIFIED' && (
          <div className="flex items-center gap-1.5 text-xs text-emerald-400 font-medium">
            <CheckCircle2 className="w-4 h-4" />
            Verified Root Cause by On-Call SRE
          </div>
        )}

        {hypothesis.status === 'REJECTED' && (
          <div className="flex items-center gap-1.5 text-xs text-rose-400">
            <XCircle className="w-4 h-4" />
            Marked as invalid by engineer
          </div>
        )}
      </div>
    </div>
  );
}
