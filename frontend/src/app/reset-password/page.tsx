'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { resetPassword } from '@/lib/auth';

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
  return <section className="max-w-lg mx-auto py-12 space-y-5">
    <h1 className="text-2xl font-semibold">Choose a new password</h1>
    {error && <p role="alert" className="text-rose-300">{error}</p>}
    {done ? <p role="status" className="text-emerald-300">Your password was reset and all sessions were signed out. Sign in with your new password.</p> :
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm text-slate-400">Use at least 15 characters. Resetting signs out every session and confirms ownership of your email. It does not join any workspace.</p>
        <label htmlFor="new-password" className="block text-sm">New password</label>
        <input id="new-password" type="password" autoComplete="new-password" minLength={15} maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} className="w-full rounded border border-slate-700 bg-slate-900 p-3" />
        <label htmlFor="confirm-password" className="block text-sm">Confirm password</label>
        <input id="confirm-password" type="password" autoComplete="new-password" minLength={15} maxLength={72} required value={confirmation} onChange={event => setConfirmation(event.target.value)} className="w-full rounded border border-slate-700 bg-slate-900 p-3" />
        <button disabled={!token || busy} className="rounded bg-indigo-600 px-4 py-2 disabled:opacity-50">{busy ? 'Resetting...' : 'Reset password and sign out all sessions'}</button>
      </form>}
    <Link href="/login" className="block text-indigo-300">Sign in</Link>
    {!done && <Link href="/forgot-password" className="block text-indigo-300">Request another reset link</Link>}
  </section>;
}
