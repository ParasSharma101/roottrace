import Link from 'next/link';
import type { ReactNode } from 'react';
import { ArrowUpRight, GitBranch, Layers3, Radio, ScanLine, ShieldCheck } from 'lucide-react';

export function AuthShell({ mode, children }: { mode: 'login' | 'signup'; children: ReactNode }) {
  const signup = mode === 'signup';
  return <div className="auth-screen"><div className="auth-shell">
    <section className="auth-form-side" aria-label={signup ? 'Create your RootTrace account' : 'Sign in to RootTrace'}>
      <Link href="/" className="auth-brand" aria-label="RootTrace home">
        <span className="auth-brand-mark"><GitBranch size={23} strokeWidth={1.8} aria-hidden="true" /></span>
        <span>RootTrace<span className="auth-brand-dot">.</span></span>
      </Link>
      <div className={'auth-form-content' + (signup ? ' auth-form-content-signup' : '')}>{children}</div>
      <p className="auth-form-footnote"><span aria-hidden="true" />A little more clarity. A lot less guesswork.</p>
    </section>
    <aside className="auth-story" aria-label="About RootTrace">
      <div className="auth-story-glow" aria-hidden="true" />
      <div className="auth-story-heading">
        <p className="auth-eyebrow"><span /> INCIDENT INVESTIGATION, CONNECTED</p>
        <h2>{signup ? <>Bring clarity to<br />every <span>incident.</span></> : <>Less firefighting.<br />More <span>understanding.</span></>}</h2>
        <p className="auth-story-description">Bring alerts, logs, and deployment context together. Give your next investigation a clearer starting point.</p>
      </div>
      <div className="auth-investigation" aria-label="Illustration of an incident investigation">
        <div className="auth-investigation-header"><span><ScanLine size={15} aria-hidden="true" /> THE INVESTIGATION</span><span className="auth-example-label">Illustrative workflow</span></div>
        <div className="auth-signal">
          <span className="auth-signal-icon"><Radio size={19} aria-hidden="true" /></span>
          <div><span className="auth-signal-label">START WITH A SIGNAL</span><p>Something changed. Find out why.</p></div>
          <ArrowUpRight size={18} className="auth-signal-arrow" aria-hidden="true" />
        </div>
        <ol className="auth-trace-steps">
          <li><span className="auth-step-marker">01</span><div><h3>Connect the evidence</h3><p>Alerts, logs, and deployment context.</p></div></li>
          <li><span className="auth-step-marker">02</span><div><h3>Follow the trace</h3><p>Correlate events into grounded hypotheses.</p></div></li>
          <li><span className="auth-step-marker auth-step-marker-final"><GitBranch size={16} aria-hidden="true" /></span><div><h3>Make the call</h3><p>Review the evidence. Own the response.</p></div></li>
        </ol>
        <div className="auth-investigation-footer"><span className="auth-evidence-dots" aria-hidden="true"><i /><i /><i /></span>From scattered signals to a shared picture.</div>
      </div>
      <div className="auth-story-footer"><p>Built for the people behind the uptime.</p><div><span><Layers3 size={15} aria-hidden="true" /> Shared context</span><span><ShieldCheck size={15} aria-hidden="true" /> Human review</span></div></div>
    </aside>
  </div></div>;
}
