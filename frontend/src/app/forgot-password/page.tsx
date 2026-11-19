'use client';

import Link from 'next/link';
import { useState } from 'react';
import { requestPasswordReset } from '@/lib/auth';
import { AuthShell } from '@/components/auth/AuthShell';
import { AuthField } from '@/components/auth/AuthFields';
import { Mail } from 'lucide-react';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null);
    try { await requestPasswordReset(email); setSent(true); }
    catch (error) { setError(error instanceof Error ? error.message : 'Recovery is unavailable. Try again later.'); }
    finally { setBusy(false); }
  };
  return <AuthShell mode="login">
    <header className="auth-form-heading"><h1>Forgot your password?</h1><p>Enter your account email and we’ll send you a link to choose a new password.</p></header>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {sent ? <p role="status" className="auth-field-hint">If this address belongs to an eligible account, a reset link will arrive by email. Check your inbox and spam folder. Requests are limited; wait before trying again.</p> :
      <form onSubmit={submit} className="auth-form" aria-busy={busy}>
        <AuthField id="recovery-email" name="email" label="Account email" icon={Mail} type="email" autoComplete="email" required maxLength={255} placeholder="you@company.com" value={email} onChange={event => setEmail(event.target.value)} disabled={busy} />
        <button disabled={busy} className="auth-submit">{busy ? 'Requesting…' : 'Send reset link'}</button>
      </form>}
    <p className="auth-switch"><Link href="/login">Return to sign in</Link></p>
  </AuthShell>;
}
