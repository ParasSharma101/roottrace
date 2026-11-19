'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { resetPassword } from '@/lib/auth';
import { AuthShell } from '@/components/auth/AuthShell';
import { AuthPasswordField } from '@/components/auth/AuthFields';

export default function ResetPasswordPage() {
  const loaded = useRef(false);
  const [token, setToken] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (loaded.current) return; loaded.current = true;
    const value = new URLSearchParams(window.location.hash.slice(1)).get('token');
    window.history.replaceState(null, '', window.location.pathname);
    setToken(value);
    if (!value) setError('Open the reset link from your email, or request another link.');
  }, []);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError(null);
    if (!token || busy) return;
    if (password !== confirmation) { setError('The passwords do not match.'); return; }
    setBusy(true);
    try { await resetPassword(token, password); setToken(null); setPassword(''); setConfirmation(''); setDone(true); }
    catch (error) { setError(error instanceof Error ? error.message : 'Unable to reset password.'); }
    finally { setBusy(false); }
  };
  return <AuthShell mode="login">
    <header className="auth-form-heading"><h1>Choose a new password.</h1><p>Resetting signs out every session. You’ll sign in again with your new password.</p></header>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {done ? <p role="status" className="auth-field-hint">Your password was reset and all sessions were signed out. Sign in with your new password.</p> :
      <form onSubmit={submit} className="auth-form" aria-busy={busy}>
        <AuthPasswordField id="new-password" label="New password" name="password" autoComplete="new-password" minLength={15} maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} disabled={busy} hint="Use at least 15 characters." />
        <AuthPasswordField id="confirm-password" label="Confirm password" name="confirmation" autoComplete="new-password" minLength={15} maxLength={72} required value={confirmation} onChange={event => setConfirmation(event.target.value)} disabled={busy} />
        <button disabled={!token || busy} className="auth-submit">{busy ? 'Resetting…' : 'Reset password'}</button>
      </form>}
    <p className="auth-switch"><Link href="/login">Sign in</Link></p>
    {!done && <p className="auth-switch"><Link href="/forgot-password">Request another reset link</Link></p>}
  </AuthShell>;
}
