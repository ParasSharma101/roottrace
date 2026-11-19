'use client';

import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { AlertCircle, ArrowRight, Loader2, Mail } from 'lucide-react';
import { AuthShell } from '@/components/auth/AuthShell';
import { AuthField, AuthPasswordField } from '@/components/auth/AuthFields';
import { loginWithCredentials, loginDemoRole } from '@/lib/auth';
import { isDemoEnabled } from '@/lib/demo';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (loading) return;
    setError(null); setLoading(true);
    try { await loginWithCredentials(email, password); router.push('/workspaces'); }
    catch (error) { setError(error instanceof Error ? error.message : 'Unable to sign in. Please try again.'); }
    finally { setLoading(false); }
  };
  const demo = async (role: 'SRE' | 'ADMIN' | 'VIEWER') => {
    if (loading) return;
    setError(null); setLoading(true);
    try { await loginDemoRole(role); router.push('/'); }
    catch { setError('Demo sign-in is unavailable. Please try again.'); }
    finally { setLoading(false); }
  };

  return <AuthShell mode="login">
    <header className="auth-form-heading">
      <h1>Welcome back.</h1>
      <p>Sign in to your workspace and pick up<br className="hidden sm:block" /> where your investigation left off.</p>
    </header>
    {error && <div id="login-error" role="alert" className="auth-error"><AlertCircle size={16} aria-hidden="true" /><span>{error}</span></div>}
    <form className="auth-form" onSubmit={submit} aria-busy={loading} aria-describedby={error ? 'login-error' : undefined}>
      <AuthField id="login-email" name="email" label="Email address" icon={Mail} type="email" autoComplete="username" placeholder="you@company.com" required maxLength={255} value={email} onChange={event => setEmail(event.target.value)} disabled={loading} />
      <AuthPasswordField id="login-password" name="password" label="Password" autoComplete="current-password" placeholder="Enter your password" required value={password} onChange={event => setPassword(event.target.value)} disabled={loading} />
      <Link href="/forgot-password" className="auth-forgot">Forgot password?</Link>
      <button type="submit" className="auth-submit" disabled={loading}>
        {loading ? <><Loader2 size={16} className="auth-spinner" aria-hidden="true" />Signing in...</> : <>Sign in<ArrowRight size={16} aria-hidden="true" /></>}
      </button>
    </form>
    <p className="auth-switch">New to RootTrace?<Link href="/signup">Create an account</Link></p>
    {isDemoEnabled && <div className="auth-demo"><p>Explore the local demo</p><div>
      <button type="button" disabled={loading} onClick={() => void demo('SRE')}>SRE</button>
      <button type="button" disabled={loading} onClick={() => void demo('ADMIN')}>Administrator</button>
      <button type="button" disabled={loading} onClick={() => void demo('VIEWER')}>Viewer</button>
    </div></div>}
  </AuthShell>;
}
