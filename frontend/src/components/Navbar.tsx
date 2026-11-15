'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { Activity, ShieldAlert, Zap, CheckCircle2, Github, UserCheck, ChevronDown, KeyRound, Shield } from 'lucide-react';
import { triggerScenarioA, triggerScenarioB } from '@/lib/api';
import { AuthUser, getAuthUser, loginDemoRole, ensureDefaultAuth } from '@/lib/auth';

export function Navbar({ onRefresh }: { onRefresh?: () => void }) {
  const [simulating, setSimulating] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [showRoleMenu, setShowRoleMenu] = useState(false);

  useEffect(() => {
    ensureDefaultAuth().then(setUser);

    const handleAuthChange = () => {
      setUser(getAuthUser());
    };

    window.addEventListener('auth-change', handleAuthChange);
    return () => window.removeEventListener('auth-change', handleAuthChange);
  }, []);

  const handleSimulate = async (scenario: 'A' | 'B') => {
    try {
      setSimulating(scenario);
      setMessage(`Triggering Scenario ${scenario}...`);
      if (scenario === 'A') {
        await triggerScenarioA();
      } else {
        await triggerScenarioB();
      }
      setMessage(`Scenario ${scenario} incident triggered! Correlation & AI pipeline active.`);
      setTimeout(() => {
        setMessage(null);
        if (onRefresh) onRefresh();
      }, 2500);
    } catch (err: any) {
      setMessage(`Failed: ${err.message}`);
      setTimeout(() => setMessage(null), 3000);
    } finally {
      setSimulating(null);
    }
  };

  const handleSwitchRole = async (role: 'SRE' | 'VIEWER' | 'ADMIN') => {
    try {
      const newUser = await loginDemoRole(role);
      setUser(newUser);
      setShowRoleMenu(false);
      setMessage(`Switched active identity to ${newUser.name}`);
      setTimeout(() => setMessage(null), 2500);
      if (onRefresh) onRefresh();
    } catch (err: any) {
      setMessage(`Failed to switch role: ${err.message}`);
    }
  };

  const currentRole = user?.roles?.some(r => r.includes('ADMIN'))
    ? 'ADMIN'
    : user?.roles?.some(r => r.includes('SRE'))
    ? 'SRE'
    : 'VIEWER';

  return (
    <header className="sticky top-0 z-50 bg-[#090d16]/90 backdrop-blur-md border-b border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-indigo-500 to-rose-500 flex items-center justify-center shadow-lg shadow-indigo-500/20 group-hover:scale-105 transition-transform">
              <Activity className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg tracking-tight text-white group-hover:text-indigo-400 transition-colors">
                  RootTrace
                </span>
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  AI SRE
                </span>
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Incident Investigator</p>
            </div>
          </Link>

          <nav className="hidden md:flex items-center gap-1 text-sm font-medium">
            <Link 
              href="/" 
              className="px-3 py-1.5 rounded-md text-slate-200 hover:text-white hover:bg-slate-800/60 transition-colors"
            >
              Command Center
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {message && (
            <div className="hidden lg:flex items-center gap-1.5 text-xs bg-indigo-950/80 text-indigo-200 border border-indigo-800/50 px-3 py-1.5 rounded-full animate-fade-in">
              <CheckCircle2 className="w-3.5 h-3.5 text-indigo-400" />
              <span>{message}</span>
            </div>
          )}

          {/* Role / Persona Switcher */}
          <div className="relative">
            <button
              onClick={() => setShowRoleMenu(!showRoleMenu)}
              className="flex items-center gap-2 px-2.5 py-1.5 rounded-md bg-slate-900/90 border border-slate-700/80 hover:border-slate-600 text-xs text-slate-200 transition-colors"
              title="Click to switch SRE/Viewer role"
            >
              <span className={`w-2 h-2 rounded-full ${currentRole === 'ADMIN' ? 'bg-amber-400' : currentRole === 'SRE' ? 'bg-emerald-400' : 'bg-blue-400'}`} />
              <span className="font-medium text-[11px] text-slate-300">
                {user ? user.name : 'Authenticating...'}
              </span>
              <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 border border-slate-700">
                {currentRole}
              </span>
              <ChevronDown className="w-3 h-3 text-slate-400" />
            </button>

            {showRoleMenu && (
              <div className="absolute right-0 mt-1 w-60 rounded-lg bg-slate-900 border border-slate-800 shadow-xl shadow-black/50 py-1.5 z-50 text-xs">
                <div className="px-3 py-1.5 border-b border-slate-800 text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                  Switch Active Persona
                </div>
                <button
                  onClick={() => handleSwitchRole('SRE')}
                  className={`w-full text-left px-3 py-2 flex items-center justify-between hover:bg-slate-800/60 transition-colors ${currentRole === 'SRE' ? 'text-emerald-400 font-semibold' : 'text-slate-300'}`}
                >
                  <div className="flex items-center gap-2">
                    <UserCheck className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Alice (On-Call SRE)</span>
                  </div>
                  <span className="text-[10px] bg-emerald-500/10 text-emerald-300 px-1.5 py-0.5 rounded border border-emerald-500/20">SRE</span>
                </button>
                <button
                  onClick={() => handleSwitchRole('ADMIN')}
                  className={`w-full text-left px-3 py-2 flex items-center justify-between hover:bg-slate-800/60 transition-colors ${currentRole === 'ADMIN' ? 'text-amber-400 font-semibold' : 'text-slate-300'}`}
                >
                  <div className="flex items-center gap-2">
                    <Shield className="w-3.5 h-3.5 text-amber-400" />
                    <span>Bob (Lead Admin)</span>
                  </div>
                  <span className="text-[10px] bg-amber-500/10 text-amber-300 px-1.5 py-0.5 rounded border border-amber-500/20">ADMIN</span>
                </button>
                <button
                  onClick={() => handleSwitchRole('VIEWER')}
                  className={`w-full text-left px-3 py-2 flex items-center justify-between hover:bg-slate-800/60 transition-colors ${currentRole === 'VIEWER' ? 'text-blue-400 font-semibold' : 'text-slate-300'}`}
                >
                  <div className="flex items-center gap-2">
                    <KeyRound className="w-3.5 h-3.5 text-blue-400" />
                    <span>Charlie (Dev / Observer)</span>
                  </div>
                  <span className="text-[10px] bg-blue-500/10 text-blue-300 px-1.5 py-0.5 rounded border border-blue-500/20">VIEWER</span>
                </button>
              </div>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => handleSimulate('A')}
              disabled={simulating !== null}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/30 text-xs font-medium transition-all disabled:opacity-50"
              title="Simulates DB lock contention alert + deployment + error logs"
            >
              <Zap className="w-3.5 h-3.5 text-rose-400" />
              {simulating === 'A' ? 'Injecting...' : 'Simulate Scenario A'}
            </button>

            <button
              onClick={() => handleSimulate('B')}
              disabled={simulating !== null}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border border-amber-500/30 text-xs font-medium transition-all disabled:opacity-50"
              title="Simulates Upstream dependency timeout"
            >
              <ShieldAlert className="w-3.5 h-3.5 text-amber-400" />
              {simulating === 'B' ? 'Injecting...' : 'Simulate Scenario B'}
            </button>
          </div>

          <a
            href="https://github.com/ParasSharma101/roottrace"
            target="_blank"
            rel="noopener noreferrer"
            className="p-2 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
            title="View GitHub Repository"
          >
            <Github className="w-4 h-4" />
          </a>
        </div>
      </div>
    </header>
  );
}
