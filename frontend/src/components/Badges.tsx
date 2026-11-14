import React from 'react';
import { IncidentStatus, SeverityLevel, TimelineEventType } from '@/types';
import { 
  Rocket, 
  GitPullRequest, 
  AlertTriangle, 
  FileTerminal, 
  Sparkles, 
  UserCheck 
} from 'lucide-react';

export function StatusBadge({ status }: { status: IncidentStatus }) {
  const styles = {
    OPEN: 'bg-red-500/10 text-red-400 border-red-500/30',
    INVESTIGATING: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
    RESOLVED: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
  }[status] || 'bg-slate-500/10 text-slate-400 border-slate-500/30';

  const pulse = status === 'INVESTIGATING' || status === 'OPEN';

  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${styles}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${
        status === 'OPEN' ? 'bg-red-400' : status === 'INVESTIGATING' ? 'bg-amber-400' : 'bg-emerald-400'
      } ${pulse ? 'animate-pulse' : ''}`} />
      {status}
    </span>
  );
}

export function SeverityBadge({ severity }: { severity: SeverityLevel }) {
  const styles = {
    SEV1: 'bg-rose-500/20 text-rose-300 border-rose-500/40 font-bold',
    SEV2: 'bg-orange-500/20 text-orange-300 border-orange-500/40 font-semibold',
    SEV3: 'bg-yellow-500/20 text-yellow-300 border-yellow-500/40',
    SEV4: 'bg-slate-500/20 text-slate-300 border-slate-500/40',
  }[severity] || 'bg-slate-500/20 text-slate-300 border-slate-500/40';

  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs border ${styles}`}>
      {severity}
    </span>
  );
}

export function EventTypeIcon({ type }: { type: TimelineEventType }) {
  switch (type) {
    case 'DEPLOYMENT':
      return <Rocket className="w-4 h-4 text-sky-400" />;
    case 'GITHUB_PR':
      return <GitPullRequest className="w-4 h-4 text-purple-400" />;
    case 'ALERT':
      return <AlertTriangle className="w-4 h-4 text-rose-400" />;
    case 'LOG_ERROR':
      return <FileTerminal className="w-4 h-4 text-red-400" />;
    case 'AI_HYPOTHESIS':
      return <Sparkles className="w-4 h-4 text-amber-400" />;
    case 'HUMAN_ACTION':
      return <UserCheck className="w-4 h-4 text-emerald-400" />;
    default:
      return <AlertTriangle className="w-4 h-4 text-slate-400" />;
  }
}
