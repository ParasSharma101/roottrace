'use client';

import Link from 'next/link';
import { useState } from 'react';
import { requestPasswordReset } from '@/lib/auth';

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
  return <section className="max-w-lg mx-auto py-12 space-y-5">
    <h1 className="text-2xl font-semibold">Reset your password</h1>
    {error && <p role="alert" className="text-rose-300">{error}</p>}
    {sent ? <p role="status" className="text-slate-300">If this address belongs to an eligible account, a reset link will arrive by email. Check your spam folder too. Requests are limited; wait before trying again.</p> :
      <form onSubmit={submit} className="space-y-4">
        <label htmlFor="recovery-email" className="block text-sm">Account email</label>
        <input id="recovery-email" type="email" autoComplete="email" required maxLength={255} value={email} onChange={event => setEmail(event.target.value)} className="w-full rounded border border-slate-700 bg-slate-900 p-3" />
        <button disabled={busy} className="rounded bg-indigo-600 px-4 py-2 disabled:opacity-50">{busy ? 'Requesting...' : 'Send reset link'}</button>
      </form>}
    <Link href="/login" className="block text-indigo-300">Return to sign in</Link>
  </section>;
}
