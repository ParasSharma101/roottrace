'use client';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { verifyEmailAddress } from '@/lib/auth';

export default function VerifyEmailPage() {
  const loaded=useRef(false);
  const [token,setToken]=useState<string | null>(null),[done,setDone]=useState(false),[busy,setBusy]=useState(false);
  const [error,setError]=useState<string | null>(null);
  useEffect(() => {
    if(loaded.current) return; loaded.current=true;
    const value=new URLSearchParams(window.location.hash.slice(1)).get('token');
    window.history.replaceState(null,'',window.location.pathname);
    setToken(value);
    if(!value) setError('Open the verification link from your email, or request a new link from your account.');
  },[]);
  const confirm=async () => {
    if(!token || busy) return; setBusy(true); setError(null);
    try { await verifyEmailAddress(token); setToken(null); setDone(true); }
    catch(error) { setError(error instanceof Error ? error.message : 'Verification could not be completed'); }
    finally { setBusy(false); }
  };
  return <section className="max-w-lg mx-auto py-12 space-y-5">
    <h1 className="text-2xl font-semibold">Verify your email</h1>
    {error && <p role="alert" className="text-rose-300">{error}</p>}
    {done ? <p role="status" className="text-emerald-300">Your email is verified. Continue to review your workspace invitations.</p> : <>
      <p className="text-sm text-slate-400">Sign in to the account that requested this link, then confirm your address. Verification does not join a workspace.</p>
      <button type="button" disabled={!token || busy} onClick={() => void confirm()} className="rounded bg-indigo-600 px-4 py-2 disabled:opacity-50">{busy ? 'Verifying...' : 'Confirm email address'}</button>
    </>}
    {!done && <Link href="/login" target="_blank" rel="noopener noreferrer" className="block text-indigo-300">Sign in in another tab, then return here or reopen the email link</Link>}
    <Link href="/workspaces" className="block text-indigo-300">Continue to workspaces</Link>
  </section>;
}
