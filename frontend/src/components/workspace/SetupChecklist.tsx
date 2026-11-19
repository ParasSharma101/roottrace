import { Check, KeyRound, Radio, Workflow } from 'lucide-react';
import type { WorkspaceSetupStatus } from '@/lib/api';

export function SetupChecklist({ status }: { status: WorkspaceSetupStatus | null }) {
  const steps = [
    { title: 'Create an ingestion key', text: 'Give your service access to this workspace.', done: !!status?.activeKeyCount, icon: KeyRound },
    { title: 'Send your first event', text: 'Connect your logs or alerts using the guide.', done: !!status?.acceptedRequestCount, icon: Radio },
    { title: 'Confirm data arrived', text: 'Check that RootTrace processed the event.', done: !!status?.processedEventCount, icon: Workflow },
  ];
  return <ol className="ux-setup-steps">{steps.map((step,index) => <li key={step.title} className={step.done ? 'complete' : ''}><span className="ux-step-number">{step.done ? <Check size={17} aria-hidden="true" /> : index+1}</span><div><h3>{step.title}</h3><p>{step.text}</p><small>{!status ? 'Not checked' : step.done ? 'Complete' : 'Not observed yet'}</small></div></li>)}</ol>;
}
