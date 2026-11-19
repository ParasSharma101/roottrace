import Link from 'next/link';
import { ArrowUpRight, Clock3 } from 'lucide-react';
import type { IncidentSummary } from '@/types';
import { SeverityBadge, StatusBadge } from '@/components/Badges';

export function IncidentList({ incidents }: { incidents: IncidentSummary[] }) {
  return <ul className="ux-incident-list">{incidents.map(incident => <li key={incident.id}><Link href={'/incidents/'+incident.id} className="ux-incident-row"><span className={'ux-severity-mark '+incident.severityLevel.toLowerCase()} aria-hidden="true" /><div className="ux-incident-main"><div className="ux-incident-meta"><span>{incident.serviceName}</span><SeverityBadge severity={incident.severityLevel} /><StatusBadge status={incident.status} /></div><h3>{incident.title}</h3><p><Clock3 size={13} aria-hidden="true" />{new Date(incident.startTime).toLocaleString()}<span>·</span>{incident.alertCount} {incident.alertCount === 1 ? 'alert' : 'alerts'}<span>·</span>{incident.hypothesisCount} {incident.hypothesisCount === 1 ? 'hypothesis' : 'hypotheses'}</p></div><ArrowUpRight size={18} aria-hidden="true" /></Link></li>)}</ul>;
}
