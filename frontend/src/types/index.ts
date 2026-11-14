export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED';
export type SeverityLevel = 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
export type HypothesisStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type TimelineEventType = 
  | 'DEPLOYMENT' 
  | 'GITHUB_PR' 
  | 'ALERT' 
  | 'LOG_ERROR' 
  | 'AI_HYPOTHESIS' 
  | 'HUMAN_ACTION';

export interface IncidentSummary {
  id: string;
  title: string;
  status: IncidentStatus;
  severityLevel: SeverityLevel;
  serviceName: string;
  startTime: string;
  resolutionTime: string | null;
  alertCount: number;
  hypothesisCount: number;
}

export interface AlertDetail {
  id: string;
  source: string;
  alertType: string;
  serviceName: string;
  rawPayload: string;
  triggeredAt: string;
}

export interface HypothesisDetail {
  id: string;
  incidentId: string;
  description: string;
  confidenceScore: number;
  status: HypothesisStatus;
  evidenceCitations: string;
  createdAt: string;
  updatedAt: string;
}

export interface IncidentNoteDetail {
  id: string;
  incidentId: string;
  hypothesisId: string | null;
  author: string;
  note: string;
  createdAt: string;
}

export interface IncidentDetail {
  id: string;
  title: string;
  summary: string | null;
  status: IncidentStatus;
  severityLevel: SeverityLevel;
  serviceName: string;
  startTime: string;
  resolutionTime: string | null;
  createdAt: string;
  updatedAt: string;
  alerts: AlertDetail[];
  hypotheses: HypothesisDetail[];
  notes: IncidentNoteDetail[];
}

export interface TimelineEvent {
  id: string;
  timestamp: string;
  eventType: TimelineEventType;
  title: string;
  description: string;
  metadata: Record<string, any>;
  severity: string;
}

export interface Postmortem {
  incidentId: string;
  title: string;
  markdownContent: string;
  generatedAt: string;
}
