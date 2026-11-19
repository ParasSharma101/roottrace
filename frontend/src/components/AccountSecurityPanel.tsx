'use client';

import { useState } from 'react';
import Link from 'next/link';
import { signOut } from '@/lib/auth';

export function AccountSecurityPanel() {
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const revoke = async () => {
    setBusy(true); setError(null);
    try { await signOut(true); window.location.assign('/login'); }
    catch { setError('Unable to confirm session revocation. Try again or sign in again.'); setBusy(false); }
  };
  return <section className="rounded-2xl border border-slate-800 bg-[#0b101b] p-5 space-y-3 text-sm">
    <h2 className="font-semibold">Account security</h2>
    <Link href="/forgot-password" className="block text-indigo-300">Reset password by email</Link>
    <p className="text-xs text-slate-400">Signing out all sessions includes this browser. Ingestion keys stay active and can be revoked separately in Integrations.</p>
    {error && <p role="alert" className="text-rose-300">{error}</p>}
    {confirm ? <div className="flex gap-4">
      <button disabled={busy} onClick={() => void revoke()} className="text-rose-300 disabled:opacity-50">{busy ? 'Signing out...' : 'Confirm sign out everywhere'}</button>
      <button disabled={busy} onClick={() => setConfirm(false)}>Cancel</button>
    </div> : <button onClick={() => setConfirm(true)} className="text-rose-300">Sign out all sessions</button>}
  </section>;
}
