'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Activity, ArrowRight, Lock, Mail, AlertCircle, Shield, UserCheck, KeyRound } from 'lucide-react';
import { loginWithCredentials, loginDemoRole } from '@/lib/auth';
import { isDemoEnabled } from '@/lib/demo';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await loginWithCredentials(email, password);
      router.push('/workspaces');
    } catch (err: any) {
      setError(err.message || 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  };

  const handleDemoLogin = async (role: 'SRE' | 'ADMIN' | 'VIEWER') => {
    setError(null);
    setLoading(true);
    try {
      await loginDemoRole(role);
      router.push('/');
    } catch (err: any) {
      setError('Demo login failed: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#070b12] text-slate-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8 selection:bg-indigo-500/30">
      {/* Background radial gradient glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[350px] bg-gradient-to-tr from-indigo-600/15 via-rose-600/10 to-transparent blur-3xl pointer-events-none rounded-full" />

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10">
        <Link href="/" className="flex items-center justify-center gap-3 group mb-2">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-rose-500 flex items-center justify-center shadow-lg shadow-indigo-500/25 group-hover:scale-105 transition-transform">
            <Activity className="w-6 h-6 text-white" />
          </div>
          <div className="flex items-center gap-2">
            <span className="font-bold text-2xl tracking-tight text-white group-hover:text-indigo-400 transition-colors">
              RootTrace
            </span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
              AI SRE
            </span>
          </div>
        </Link>
        <h2 className="mt-3 text-center text-xl font-bold tracking-tight text-slate-200">
          Sign In to Incident Workspace
        </h2>
        <p className="mt-1 text-center text-xs text-slate-400">
          Or{' '}
          <Link href="/signup" className="font-medium text-indigo-400 hover:text-indigo-300 transition-colors">
            create a new workspace for your project
          </Link>
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md relative z-10">
        <div className="bg-[#0b101b]/90 backdrop-blur-xl py-8 px-6 shadow-2xl shadow-black/80 rounded-2xl border border-slate-800 sm:px-10">
          {error && (
            <div className="mb-5 flex items-center gap-2.5 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs animate-fade-in">
              <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
              <span>{error}</span>
            </div>
          )}

          <form className="space-y-4" onSubmit={handleSubmit}>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Work Email
              </label>
              <div className="relative rounded-lg shadow-sm">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <Mail className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="alice.sre@company.com"
                  className="block w-full rounded-lg border border-slate-800 bg-slate-900/80 pl-10 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none transition-colors"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Password
              </label>
              <div className="relative rounded-lg shadow-sm">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <Lock className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="block w-full rounded-lg border border-slate-800 bg-slate-900/80 pl-10 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none transition-colors"
                />
              </div>
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={loading}
                className="w-full flex justify-center items-center gap-2 py-2.5 px-4 rounded-lg text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900 focus:ring-indigo-500 transition-all shadow-lg shadow-indigo-600/30 disabled:opacity-50"
              >
                {loading ? 'Authenticating...' : 'Sign In to Workspace'}
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </form>

          {/* Quick 1-Click Evaluation Personas */}
          {isDemoEnabled && (
          <div className="mt-6 pt-6 border-t border-slate-800/80">
            <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider text-center mb-3">
              Fast Evaluation • 1-Click Demo Profiles
            </p>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => handleDemoLogin('SRE')}
                disabled={loading}
                className="flex flex-col items-center justify-center p-2 rounded-lg bg-slate-900/90 border border-slate-800 hover:border-emerald-500/50 hover:bg-emerald-950/20 text-slate-300 transition-all group"
              >
                <UserCheck className="w-4 h-4 text-emerald-400 mb-1 group-hover:scale-110 transition-transform" />
                <span className="text-[11px] font-medium text-slate-200">Alice (SRE)</span>
                <span className="text-[9px] text-emerald-400 font-mono">Responder</span>
              </button>

              <button
                type="button"
                onClick={() => handleDemoLogin('ADMIN')}
                disabled={loading}
                className="flex flex-col items-center justify-center p-2 rounded-lg bg-slate-900/90 border border-slate-800 hover:border-amber-500/50 hover:bg-amber-950/20 text-slate-300 transition-all group"
              >
                <Shield className="w-4 h-4 text-amber-400 mb-1 group-hover:scale-110 transition-transform" />
                <span className="text-[11px] font-medium text-slate-200">Bob (Lead)</span>
                <span className="text-[9px] text-amber-400 font-mono">Admin</span>
              </button>

              <button
                type="button"
                onClick={() => handleDemoLogin('VIEWER')}
                disabled={loading}
                className="flex flex-col items-center justify-center p-2 rounded-lg bg-slate-900/90 border border-slate-800 hover:border-blue-500/50 hover:bg-blue-950/20 text-slate-300 transition-all group"
              >
                <KeyRound className="w-4 h-4 text-blue-400 mb-1 group-hover:scale-110 transition-transform" />
                <span className="text-[11px] font-medium text-slate-200">Charlie</span>
                <span className="text-[9px] text-blue-400 font-mono">Observer</span>
              </button>
            </div>
          </div>
          )}
        </div>
      </div>
    </div>
  );
}
