'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Activity, ArrowRight, Lock, Mail, User, Building2, AlertCircle, Sparkles } from 'lucide-react';
import { signupUser } from '@/lib/auth';

export default function SignupPage() {
  const router = useRouter();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [workspaceName, setWorkspaceName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await signupUser(fullName, email, password, workspaceName);
      router.push('/workspaces');
    } catch (err: any) {
      setError(err.message || 'Failed to create workspace account');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#070b12] text-slate-100 flex flex-col justify-center py-12 sm:px-6 lg:px-8 selection:bg-indigo-500/30">
      {/* Background radial gradient glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[650px] h-[380px] bg-gradient-to-tr from-indigo-600/15 via-rose-600/10 to-transparent blur-3xl pointer-events-none rounded-full" />

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
          Create Your Incident Workspace
        </h2>
        <p className="mt-1 text-center text-xs text-slate-400">
          Solo developers, engineering squads, or enterprise organizations.{' '}
          <Link href="/login" className="font-medium text-indigo-400 hover:text-indigo-300 transition-colors">
            Already have an account? Sign in
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
                Full Name
              </label>
              <div className="relative rounded-lg shadow-sm">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <User className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Paras Sharma"
                  className="block w-full rounded-lg border border-slate-800 bg-slate-900/80 pl-10 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none transition-colors"
                />
              </div>
            </div>

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
                  placeholder="paras@company.com"
                  className="block w-full rounded-lg border border-slate-800 bg-slate-900/80 pl-10 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none transition-colors"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Workspace / Organization Name
              </label>
              <div className="relative rounded-lg shadow-sm">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <Building2 className="h-4 w-4 text-slate-500" />
                </div>
                <input
                  type="text"
                  required
                  value={workspaceName}
                  onChange={(e) => setWorkspaceName(e.target.value)}
                  placeholder="Acme Production (or Personal Project)"
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
                  minLength={6}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 6 characters"
                  className="block w-full rounded-lg border border-slate-800 bg-slate-900/80 pl-10 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none transition-colors"
                />
              </div>
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={loading}
                className="w-full flex justify-center items-center gap-2 py-2.5 px-4 rounded-lg text-xs font-medium text-white bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900 focus:ring-indigo-500 transition-all shadow-lg shadow-indigo-600/30 disabled:opacity-50"
              >
                {loading ? 'Creating Workspace...' : 'Launch Incident Workspace'}
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </form>

          <div className="mt-5 text-center">
            <span className="text-[11px] text-slate-500 flex items-center justify-center gap-1.5">
              <Sparkles className="w-3 h-3 text-indigo-400" />
              Includes AI Root Cause Correlation & Webhook Ingestion API Key
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
