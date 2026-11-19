'use client';
import { useCallback, useEffect, useState } from 'react';
import { EmailStatus, getEmailStatus, requestEmailVerification } from '@/lib/auth';

export function EmailVerificationPanel({ onChange }: { onChange?: (status: EmailStatus) => void }) {
  const [status,setStatus]=useState<EmailStatus | null>(null),[busy,setBusy]=useState(false);
  const [error,setError]=useState<string | null>(null),[notice,setNotice]=useState<string | null>(null);
  const [localMailHelp,setLocalMailHelp]=useState(false);
  const refresh=useCallback(async () => {
    setBusy(true); setError(null);
    try { const value=await getEmailStatus(); setStatus(value); onChange?.(value); }
    catch { setError('Unable to load email verification status. Sign in and refresh.'); }
    finally { setBusy(false); }
  },[onChange]);
  useEffect(() => { void refresh(); },[refresh]);
  useEffect(() => {
    setLocalMailHelp(process.env.NODE_ENV === 'development' && ['localhost','127.0.0.1','[::1]'].includes(window.location.hostname));
  },[]);
  useEffect(() => {
    if(status?.emailVerified || !['PENDING','RUNNING'].includes(status?.deliveryStatus ?? '')) return;
    let checks=0;
    const timer=window.setInterval(() => {
      if(++checks>10) { window.clearInterval(timer); return; }
      void refresh();
    },3000);
    return () => window.clearInterval(timer);
  },[status?.emailVerified,status?.deliveryStatus,refresh]);
  const request=async () => {
    setBusy(true); setError(null); setNotice(null);
    try { await requestEmailVerification(); setNotice('Verification request accepted. Check the delivery status below; requests within one minute keep the existing link.'); await refresh(); }
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
      {!status.emailVerified && status.deliveryConfigured && localMailHelp && <p className="text-xs text-sky-200">
        Using the local Mailpit setup? <a href="http://localhost:8025" target="_blank" rel="noopener noreferrer" className="underline">Open the test inbox</a>. Captured test emails do not arrive in Gmail or your personal inbox.
      </p>}
    </>}
    <div className="flex gap-3 text-xs">
      {status && !status.emailVerified && <button type="button" disabled={busy || !status.deliveryConfigured} onClick={() => void request()} className="rounded bg-indigo-600 px-3 py-2 disabled:opacity-50">Send verification email</button>}
      <button type="button" disabled={busy} onClick={() => void refresh()} className="rounded bg-slate-800 px-3 py-2 disabled:opacity-50">{busy ? 'Checking...' : 'Refresh status'}</button>
    </div>
  </section>;
}
