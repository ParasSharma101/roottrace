'use client';

import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { AlertCircle, ArrowRight, Building2, Loader2, Mail, ShieldCheck, UserRound } from 'lucide-react';
import { AuthShell } from '@/components/auth/AuthShell';
import { AuthField, AuthPasswordField } from '@/components/auth/AuthFields';
import { signupUser } from '@/lib/auth';

export default function SignupPage() {
  const router = useRouter();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [workspaceName, setWorkspaceName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (loading) return;
    setError(null); setLoading(true);
    try { await signupUser(fullName, email, password, workspaceName); router.push('/'); }
    catch (error) { setError(error instanceof Error ? error.message : 'Unable to create your account. Please try again.'); }
    finally { setLoading(false); }
  };

  return <AuthShell mode="signup">
    <header className="auth-form-heading">
      <h1>Your next incident,<br />a little clearer.</h1>
      <p>Create your account and a workspace<br className="hidden sm:block" /> for your team or personal project.</p>
    </header>
    {error && <div id="signup-error" role="alert" className="auth-error"><AlertCircle size={16} aria-hidden="true" /><span>{error}</span></div>}
    <form className="auth-form auth-form-signup" onSubmit={submit} aria-busy={loading} aria-describedby={error ? 'signup-error' : undefined}>
      <AuthField id="signup-name" name="fullName" label="Full name" icon={UserRound} autoComplete="name" placeholder="Your full name" required maxLength={255} value={fullName} onChange={event => setFullName(event.target.value)} disabled={loading} />
      <AuthField id="signup-email" name="email" label="Email address" icon={Mail} type="email" autoComplete="username" placeholder="you@company.com" required maxLength={255} value={email} onChange={event => setEmail(event.target.value)} disabled={loading} />
      <AuthField id="signup-workspace" name="workspaceName" label="Workspace name" icon={Building2} autoComplete="organization" placeholder="Your team or project" required maxLength={255} value={workspaceName} onChange={event => setWorkspaceName(event.target.value)} disabled={loading} />
      <AuthPasswordField id="signup-password" name="password" label="Password" autoComplete="new-password" placeholder="Create a strong password" hint="Use at least 15 characters. A memorable passphrase works well." required minLength={15} maxLength={72} value={password} onChange={event => setPassword(event.target.value)} disabled={loading} />
      <button type="submit" className="auth-submit" disabled={loading}>
        {loading ? <><Loader2 size={16} className="auth-spinner" aria-hidden="true" />Creating your account...</> : <>Create account<ArrowRight size={16} aria-hidden="true" /></>}
      </button>
    </form>
    <p className="auth-next-step"><ShieldCheck size={14} aria-hidden="true" />Verify your email to invite teammates and join other workspaces.</p>
    <p className="auth-switch">Already have an account?<Link href="/login">Sign in</Link></p>
  </AuthShell>;
}
