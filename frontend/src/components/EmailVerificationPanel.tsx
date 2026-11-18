'use client';
import { useCallback, useEffect, useState } from 'react';
import { EmailStatus, getEmailStatus, requestEmailVerification } from '@/lib/auth';

export function EmailVerificationPanel({ onChange }: { onChange?: (status: EmailStatus) => void }) {
  const [status,setStatus]=useState<EmailStatus | null>(null),[busy,setBusy]=useState(false);
  const [error,setError]=useState<string | null>(null),[notice,setNotice]=useState<string | null>(null);
  const refresh=useCallback(async () => {
    setBusy(true); setError(null);
    try { const value=await getEmailStatus(); setStatus(value); onChange?.(value); }
    catch { setError('Unable to load email verification status. Sign in and refresh.'); }
    finally { setBusy(false); }
  },[onChange]);
  useEffect(() => { void refresh(); },[refresh]);
  const request=async () => {
    setBusy(true); setError(null); setNotice(null);
    try { await requestEmailVerification(); setNotice('Verification requested. Check your inbox; requests within one minute reuse the previous email.'); await refresh(); }
    catch { setError('Unable to request verification. Check email delivery configuration or try again later.'); }
    finally { setBusy(false); }
  };
  return <section className="rounded-2xl border border-slate-800 bg-[#0b101b] p-5 space-y-3 text-sm">
    <h2 className="font-semibold">Email verification</h2>
    {error && <p role="alert" className="text-rose-300">{error}</p>}
    {notice && <p role="status" className="text-emerald-300">{notice}</p>}
    {status && <>
      <p className="break-all text-slate-300">{status.email} · {status.emailVerified ? 'Verified' : 'Verification needed'}</p>
      {!status.emailVerified && <p className="text-xs text-slate-400">Verify this address before inviting teammates or accepting workspace invitations.</p>}
      {!status.deliveryConfigured && !status.emailVerified && <p className="text-xs text-amber-200">Email delivery is not configured. Contact the service operator.</p>}
      {!status.emailVerified && status.deliveryConfigured && <p className="text-xs text-slate-500">Email status: {status.deliveryStatus === 'SENT' ? 'Submitted to mail server' : status.deliveryStatus.toLowerCase().replaceAll('_',' ')}</p>}
    </>}
    <div className="flex gap-3 text-xs">
      {status && !status.emailVerified && <button type="button" disabled={busy || !status.deliveryConfigured} onClick={() => void request()} className="rounded bg-indigo-600 px-3 py-2 disabled:opacity-50">Send verification email</button>}
      <button type="button" disabled={busy} onClick={() => void refresh()} className="rounded bg-slate-800 px-3 py-2 disabled:opacity-50">{busy ? 'Checking...' : 'Refresh status'}</button>
    </div>
  </section>;
}
