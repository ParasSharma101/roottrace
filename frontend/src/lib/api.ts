import {
  IncidentDetail,
  IncidentNoteDetail,
  IncidentStatus,
  IncidentSummary,
  HypothesisDetail,
  Postmortem,
  SeverityLevel,
  TimelineEvent,
} from '@/types';
import { getAuthToken, clearAuthSession } from './auth';

const API_BASE =
  process.env.NEXT_PUBLIC_API_URL ||
  (typeof window !== 'undefined' ? '/api/v1' : 'http://localhost:8080/api/v1');

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options?.headers as Record<string, string>) || {}),
  };

  if (token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    ...options,
    headers,
    cache: 'no-store',
  });

  if (!res.ok) {
    const errorBody = await res.text();
    throw new Error(`API Error [${res.status}]: ${errorBody}`);
  }

  return res.json();
}

export async function getIncidents(
  status?: IncidentStatus,
  severity?: SeverityLevel,
  serviceName?: string
): Promise<IncidentSummary[]> {
  const params = new URLSearchParams();
  if (status) params.append('status', status);
  if (severity) params.append('severity', severity);
  if (serviceName) params.append('serviceName', serviceName);

  const queryString = params.toString();
  const url = `${API_BASE}/incidents${queryString ? `?${queryString}` : ''}`;
  return fetchJson<IncidentSummary[]>(url);
}

export async function getIncident(id: string): Promise<IncidentDetail> {
  return fetchJson<IncidentDetail>(`${API_BASE}/incidents/${id}`);
}

export async function getTimeline(id: string): Promise<TimelineEvent[]> {
  return fetchJson<TimelineEvent[]>(`${API_BASE}/incidents/${id}/timeline`);
}

export async function verifyHypothesis(
  incidentId: string,
  hypothesisId: string,
  author: string = 'On-Call Engineer'
): Promise<HypothesisDetail> {
  return fetchJson<HypothesisDetail>(
    `${API_BASE}/incidents/${incidentId}/hypotheses/${hypothesisId}/verify`,
    {
      method: 'POST',
      body: JSON.stringify({ author }),
    }
  );
}

export async function rejectHypothesis(
  incidentId: string,
  hypothesisId: string,
  author: string = 'On-Call Engineer',
  reason: string = 'Rejected by engineer'
): Promise<HypothesisDetail> {
  return fetchJson<HypothesisDetail>(
    `${API_BASE}/incidents/${incidentId}/hypotheses/${hypothesisId}/reject`,
    {
      method: 'POST',
      body: JSON.stringify({ author, reason }),
    }
  );
}

export async function addIncidentNote(
  incidentId: string,
  author: string,
  note: string,
  hypothesisId?: string
): Promise<IncidentNoteDetail> {
  return fetchJson<IncidentNoteDetail>(`${API_BASE}/incidents/${incidentId}/notes`, {
    method: 'POST',
    body: JSON.stringify({ author, note, hypothesisId }),
  });
}

export async function updateIncidentStatus(
  incidentId: string,
  status: IncidentStatus
): Promise<IncidentDetail> {
  return fetchJson<IncidentDetail>(`${API_BASE}/incidents/${incidentId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export async function getPostmortem(incidentId: string): Promise<Postmortem> {
  return fetchJson<Postmortem>(`${API_BASE}/incidents/${incidentId}/postmortem`);
}

// =========================================================================
// Simulation Triggers for Live Demo
// =========================================================================

export async function triggerScenarioA(): Promise<any> {
  return fetchJson(`${API_BASE}/simulation/scenario-a`, {
    method: 'POST',
  });
}

export async function triggerScenarioB(): Promise<any> {
  return fetchJson(`${API_BASE}/simulation/scenario-b`, {
    method: 'POST',
  });
}

// =========================================================================
// SaaS Workspace & Team Management APIs
// =========================================================================

export interface TeamMember {
  membershipId: string;
  version: number;
  userId: string;
  fullName: string;
  email: string;
  role: string;
  joinedAt: string;
}

export interface WorkspaceDetails {
  id: string;
  name: string;
  slug: string;
  apiKey: string | null;
  members: TeamMember[];
}

export async function getWorkspaceDetails(): Promise<WorkspaceDetails> {
  return fetchJson<WorkspaceDetails>(`${API_BASE}/workspace`);
}

export interface WorkspaceSetupStatus {
  observedAt: string; activeKeyCount: number; acceptedRequestCount: number; processedEventCount: number;
  lastRequestAt: string | null; lastProcessedAt: string | null;
}
export async function getWorkspaceSetupStatus(): Promise<WorkspaceSetupStatus> {
  return fetchJson(`${API_BASE}/workspace/setup-status`);
}

export interface MembershipAuditEntry {
  id: string; membershipId: string; actorEmail: string; targetEmail: string;
  action: 'ROLE_CHANGED' | 'MEMBER_REMOVED' | 'MEMBER_LEFT'; previousRole: string; newRole: string | null;
  membershipVersion: number; reason: string; revokedInvitations: number; createdAt: string;
}

async function membershipMutation(member: TeamMember, action: 'role' | 'remove', reason: string, role?: string): Promise<Response> {
  const token = getAuthToken();
  if (!token) throw new Error('Sign in again to manage membership.');
  const response = await fetch(`${API_BASE}/workspace/members/${encodeURIComponent(member.membershipId)}/${action}`, {
    method: action === 'role' ? 'PATCH' : 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, cache: 'no-store',
    body: JSON.stringify({ expectedVersion: member.version, reason, ...(action === 'role' ? { role } : {}) }),
  });
  if (response.status === 401) { clearAuthSession(); throw new Error('Your session is no longer active. Sign in again.'); }
  if (response.status === 409) throw new Error('Membership changed, or this action would remove the last administrator. Refresh the team before trying again.');
  if (response.status === 404) throw new Error('This membership is no longer available. Refresh the team.');
  if (response.status === 403) throw new Error('You need current administrator access and a verified email to manage other members.');
  if (!response.ok) throw new Error('Unable to complete the membership change. Check your access and try again.');
  return response;
}

export async function changeMemberRole(member: TeamMember, role: string, reason: string): Promise<TeamMember> {
  return (await membershipMutation(member, 'role', reason, role)).json();
}
export async function removeMember(member: TeamMember, reason: string): Promise<void> {
  await membershipMutation(member, 'remove', reason);
}
export async function listMembershipAudit(before?: string): Promise<CursorPage<MembershipAuditEntry>> {
  return fetchJson(`${API_BASE}/workspace/members/audit${before ? `?before=${encodeURIComponent(before)}` : ''}`);
}

export async function inviteWorkspaceMember(email: string, role: string): Promise<WorkspaceInvitation> {
  return fetchJson(`${API_BASE}/workspace/invites`, {
    method: 'POST',
    body: JSON.stringify({ email, role }),
  });
}

export interface IngestionKey {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  revokedAt: string | null;
}

export async function listIngestionKeys(): Promise<IngestionKey[]> {
  return fetchJson(`${API_BASE}/workspace/ingestion-keys`);
}

export async function createIngestionKey(name: string): Promise<{ key: IngestionKey; secret: string }> {
  return fetchJson(`${API_BASE}/workspace/ingestion-keys`, { method: 'POST', body: JSON.stringify({ name }) });
}

export async function revokeIngestionKey(id: string): Promise<void> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE}/workspace/ingestion-keys/${encodeURIComponent(id)}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` }, cache: 'no-store',
  });
  if (!response.ok) throw new Error('Unable to revoke this key');
}


export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type TaskKind = 'PUBLISH' | 'INVESTIGATE';
export interface WorkspaceTask {
  id: string; kind: TaskKind; status: TaskStatus; submissionId: string | null; incidentId: string | null;
  attempts: number; totalAttempts: number; redriveCount: number; availableAt: string; leaseUntil: string | null;
  lastErrorCode: string | null; createdAt: string; completedAt: string | null;
}
export interface TaskRedrive {
  id: string; taskId: string; actorUserId: string | null; actorEmail: string; reason: string; cycleNumber: number;
  previousAttempts: number; totalAttemptsBefore: number; previousErrorCode: string | null;
  previousCompletedAt: string | null; createdAt: string;
}
export interface CursorPage<T> { items: T[]; nextCursor: string | null; }
export async function listWorkspaceTasks(status: TaskStatus, kind?: TaskKind, before?: string): Promise<CursorPage<WorkspaceTask>> {
  const params = new URLSearchParams({ status });
  if (kind) params.set('kind', kind);
  if (before) params.set('before', before);
  return fetchJson(`${API_BASE}/workspace/tasks?${params}`);
}
export async function getWorkspaceTask(id: string): Promise<WorkspaceTask> {
  return fetchJson(`${API_BASE}/workspace/tasks/${encodeURIComponent(id)}`);
}
export async function getTaskRedrives(id: string, before?: string): Promise<CursorPage<TaskRedrive>> {
  const suffix = before ? `?before=${encodeURIComponent(before)}` : '';
  return fetchJson(`${API_BASE}/workspace/tasks/${encodeURIComponent(id)}/redrives${suffix}`);
}
export async function redriveWorkspaceTask(task: WorkspaceTask, reason: string): Promise<WorkspaceTask> {
  return fetchJson(`${API_BASE}/workspace/tasks/${encodeURIComponent(task.id)}/redrive`, {
    method: 'POST', body: JSON.stringify({ reason, expectedRedriveCount: task.redriveCount }),
  });
}


export interface QueueHealth {
  observedAt: string; pendingOverdueSeconds: number;
  queues: Array<{ kind: TaskKind; pending: number; running: number; succeeded: number; failed: number; ready: number;
    overduePending: number; expiredLeases: number; oldestReadyAgeSeconds: number }>;
  signals: Array<{ kind: TaskKind; code: 'FAILED_TASKS' | 'OVERDUE_PENDING' | 'EXPIRED_LEASES'; count: number }>;
}
export async function getQueueHealth(): Promise<QueueHealth> {
  return fetchJson(`${API_BASE}/workspace/queue-health`);
}


export interface WorkspaceInvitation {
  id: string; workspaceId: string; workspaceName: string; email: string; role: string; status: string;
  createdAt: string; expiresAt: string; invitedByEmail: string | null; acceptedAt: string | null; revokedAt: string | null; deliveryStatus: string;
}
export async function listWorkspaceInvites(before?: string): Promise<CursorPage<WorkspaceInvitation>> {
  return fetchJson(`${API_BASE}/workspace/invites${before ? `?before=${encodeURIComponent(before)}` : ''}`);
}
export async function revokeWorkspaceInvite(id: string): Promise<void> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE}/workspace/invites/${encodeURIComponent(id)}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' });
  if (!response.ok) throw new Error('Invitation could not be revoked');
}
export async function listMyInvitations(before?: string): Promise<CursorPage<WorkspaceInvitation>> {
  return fetchJson(`${API_BASE}/auth/invitations${before ? `?before=${encodeURIComponent(before)}` : ''}`);
}
export async function acceptWorkspaceInvitation(id: string): Promise<{ workspaceId: string; workspaceName: string; role: string }> {
  return fetchJson(`${API_BASE}/auth/invitations/${encodeURIComponent(id)}/accept`, { method: 'POST' });
}
