'use client';

import React, { useState } from 'react';
import { Postmortem } from '@/types';
import { Download, Copy, Check, FileText } from 'lucide-react';

export function PostmortemViewer({ postmortem }: { postmortem: Postmortem }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(postmortem.markdownContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const blob = new Blob([postmortem.markdownContent], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `postmortem-${postmortem.incidentId}.md`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-4">
      {/* Action Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 p-4 rounded-xl bg-slate-900 border border-slate-800">
        <div className="flex items-center gap-2">
          <FileText className="w-5 h-5 text-indigo-400" />
          <div>
            <h3 className="text-sm font-semibold text-white">Incident postmortem</h3>
            <p className="text-xs text-slate-400">
              Generated at {new Date(postmortem.generatedAt).toLocaleString()}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-400" /> Copied
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" /> Copy Markdown
              </>
            )}
          </button>

          <button
            onClick={handleDownload}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition-colors shadow-sm"
          >
            <Download className="w-3.5 h-3.5" /> Download .md
          </button>
        </div>
      </div>

      {/* Markdown Document Preview */}
      <div className="p-6 rounded-xl bg-slate-950 border border-slate-800 font-mono text-xs text-slate-300 leading-relaxed overflow-x-auto whitespace-pre-wrap max-h-[700px] overflow-y-auto">
        {postmortem.markdownContent}
      </div>
    </div>
  );
}
