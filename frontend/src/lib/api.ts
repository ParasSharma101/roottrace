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

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers || {}),
    },
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
  const service = 'payment-service';
  const alertPayload = {
    source: 'datadog',
    alertType: 'ConnectionPoolExhausted',
    serviceName: service,
    severity: 'SEV1',
    description: 'Database connection pool exhausted. 99% acquisition timeout rate.',
    timestamp: new Date().toISOString(),
    metrics: { p99_latency_ms: 12400, pool_saturation: 1.0 },
  };

  return fetchJson(`${API_BASE}/webhooks/alerts/datadog`, {
    method: 'POST',
    body: JSON.stringify(alertPayload),
  });
}

export async function triggerScenarioB(): Promise<any> {
  const service = 'shipping-service';
  const alertPayload = {
    source: 'datadog',
    alertType: 'UpstreamDependencyTimeout',
    serviceName: service,
    severity: 'SEV2',
    description: 'Downstream HTTP call to FedEx API timing out after 10000ms',
    timestamp: new Date().toISOString(),
    metrics: { error_rate: 0.42, timeout_count: 85 },
  };

  return fetchJson(`${API_BASE}/webhooks/alerts/datadog`, {
    method: 'POST',
    body: JSON.stringify(alertPayload),
  });
}
