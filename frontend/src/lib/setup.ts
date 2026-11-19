import type { WorkspaceSetupStatus } from '@/lib/api';

export function nextSetupAction(status: WorkspaceSetupStatus | null) {
  if (!status) return { title: 'Check your connection', description: 'Load connection details to see the next step.', href: '/integrations', label: 'Open connection guide' };
  if (!status.activeKeyCount) return { title: 'Connect your project', description: 'Create a key and send your first log to this workspace.', href: '/integrations', label: 'Connect a project' };
  if (!status.acceptedRequestCount && !status.processedEventCount) return { title: 'Send your first event', description: 'Your key is ready. Use the connection guide to send a log.', href: '/integrations#send-data', label: 'Send a log' };
  if (!status.processedEventCount) return { title: 'Confirm data processing', description: 'A request was accepted. Processing has not been confirmed yet.', href: '/integrations#connection-status', label: 'Check processing' };
  return { title: 'Your data has arrived', description: 'Review incoming incidents, or connect another service.', href: '/incidents', label: 'View incidents' };
}
