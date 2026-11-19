'use client';

import { useState } from 'react';
import Link from 'next/link';
import { triggerScenarioA, triggerScenarioB } from '@/lib/api';
import { useWorkspaceSession } from './WorkspaceSession';

export function DemoTools() {
  const user = useWorkspaceSession();
  const [busy,setBusy] = useState(false),[notice,setNotice] = useState<string | null>(null);
  const demoWorkspace = user.workspaceId === '00000000-0000-0000-0000-000000000001';
  const run = async (scenario: 'A' | 'B') => {
    setBusy(true); setNotice(null);
    try { if(scenario === 'A') await triggerScenarioA(); else await triggerScenarioB(); setNotice('Demo scenario submitted. Refresh Incidents to inspect the investigation.'); }
    catch { setNotice('The demo scenario could not be submitted. Check that local demo mode is enabled.'); }
    finally { setBusy(false); }
  };
  return <details className="ux-card ux-disclosure"><summary><div><h2>Local demo tools</h2><p>Development-only scenarios for the isolated demo workspace.</p></div></summary><div className="ux-disclosure-content">{!demoWorkspace ? <p className="ux-guide-note">Demo scenarios are separate from your project. Use a local demo account from <Link href="/login" className="ux-link">Sign in</Link> to explore them.</p> : <><div className="ux-header-actions"><button className="ux-button secondary" disabled={busy} onClick={() => void run('A')}>Generate scenario A</button><button className="ux-button secondary" disabled={busy} onClick={() => void run('B')}>Generate scenario B</button></div>{notice && <p role="status" className="ux-guide-note">{notice}</p>}</>}</div></details>;
}
