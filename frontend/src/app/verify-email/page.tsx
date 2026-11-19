'use client';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { verifyEmailAddress } from '@/lib/auth';
import { AuthShell } from '@/components/auth/AuthShell';

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
  return <AuthShell mode="login">
    <header className="auth-form-heading"><h1>{done ? 'Email verified.' : 'Confirm your email.'}</h1><p>{done ? 'You can now accept invitations and invite teammates when your role allows it.' : 'Sign in to the account that requested this link, then confirm your address.'}</p></header>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {done ? <p role="status" className="auth-field-hint">Your email is verified. Choose a workspace to continue.</p> : <button type="button" disabled={!token || busy} onClick={() => void confirm()} className="auth-submit">{busy ? 'Verifying…' : 'Confirm email address'}</button>}
    {!done && <p className="auth-switch"><Link href="/login" target="_blank" rel="noopener noreferrer">Sign in in another tab, then return here or reopen your email link</Link></p>}
    <p className="auth-switch"><Link href="/workspaces">Continue to workspaces</Link></p>
  </AuthShell>;
}
