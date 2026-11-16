'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { 
  Activity, 
  ShieldAlert, 
  Zap, 
  CheckCircle2, 
  Github, 
  UserCheck, 
  ChevronDown, 
  KeyRound, 
  Shield, 
  Settings, 
  LogOut, 
  Sparkles,
  Layers
} from 'lucide-react';
import { triggerScenarioA, triggerScenarioB } from '@/lib/api';
import { AuthUser, getAuthUser, loginDemoRole, clearAuthSession, ensureDefaultAuth } from '@/lib/auth';

export function Navbar({ onRefresh }: { onRefresh?: () => void }) {
  const router = useRouter();
  const [simulating, setSimulating] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showSandboxMenu, setShowSandboxMenu] = useState(false);

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
      setShowSandboxMenu(false);
      setMessage(`Triggering Scenario ${scenario}...`);
      if (scenario === 'A') {
        await triggerScenarioA();
      } else {
        await triggerScenarioB();
      }
      setMessage(`Scenario ${scenario} incident triggered! Correlation & AI active.`);
      setTimeout(() => {
        setMessage(null);
        if (onRefresh) onRefresh();
      }, 2500);
    } catch (err: any) {
      setMessage(`Simulation failed: ${err.message}`);
      setTimeout(() => setMessage(null), 3000);
    } finally {
      setSimulating(null);
    }
  };

  const handleSwitchRole = async (role: 'SRE' | 'VIEWER' | 'ADMIN') => {
    try {
      const newUser = await loginDemoRole(role);
      setUser(newUser);
      setShowUserMenu(false);
      setMessage(`Active persona: ${newUser.fullName || newUser.name}`);
      setTimeout(() => setMessage(null), 2500);
      if (onRefresh) onRefresh();
    } catch (err: any) {
      setMessage(`Switch failed: ${err.message}`);
    }
  };

  const handleSignOut = () => {
    clearAuthSession();
    setUser(null);
    setShowUserMenu(false);
    router.push('/login');
  };

  const currentRole = user?.roles?.some(r => r.includes('ADMIN'))
    ? 'ADMIN'
    : user?.roles?.some(r => r.includes('SRE'))
    ? 'SRE'
    : 'VIEWER';

  return (
    <header className="sticky top-0 z-50 bg-[#070b12]/95 backdrop-blur-md border-b border-slate-800/90">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Left: Branding & Core Navigation */}
        <div className="flex items-center gap-8">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-rose-500 flex items-center justify-center shadow-md shadow-indigo-500/20 group-hover:scale-105 transition-transform">
              <Activity className="w-4 h-4 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-base tracking-tight text-white group-hover:text-indigo-400 transition-colors">
                  RootTrace
                </span>
                <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  SaaS SRE
                </span>
              </div>
              <p className="text-[10px] text-slate-400 font-medium">Incident Investigator</p>
            </div>
          </Link>

          <nav className="hidden md:flex items-center gap-1 text-xs font-medium">
            <Link 
              href="/" 
              className="px-3 py-1.5 rounded-md text-slate-300 hover:text-white hover:bg-slate-800/60 transition-colors flex items-center gap-1.5"
            >
              <Layers className="w-3.5 h-3.5 text-slate-400" />
              <span>Command Center</span>
            </Link>
            <Link 
              href="/settings" 
              className="px-3 py-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors flex items-center gap-1.5"
            >
              <Settings className="w-3.5 h-3.5 text-slate-400" />
              <span>Workspace & Keys</span>
            </Link>
          </nav>
        </div>

        {/* Right: Sandbox, Notifications, Persona & Profile */}
        <div className="flex items-center gap-3">
          {message && (
            <div className="hidden lg:flex items-center gap-1.5 text-xs bg-indigo-950/80 text-indigo-200 border border-indigo-800/50 px-3 py-1.5 rounded-full animate-fade-in">
              <CheckCircle2 className="w-3.5 h-3.5 text-indigo-400" />
              <span>{message}</span>
            </div>
          )}

          {/* Clean Interactive Sandbox Dropdown (Replaces prominent raw scenario buttons) */}
          <div className="relative">
            <button
              onClick={() => setShowSandboxMenu(!showSandboxMenu)}
              disabled={simulating !== null}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600/10 hover:bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 text-xs font-medium transition-colors"
              title="Inject test incidents for live AI correlation testing"
            >
              <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
              <span>{simulating ? 'Injecting Outage...' : 'Test Sandbox'}</span>
              <ChevronDown className="w-3 h-3 text-indigo-400" />
            </button>

            {showSandboxMenu && (
              <div className="absolute right-0 mt-1.5 w-64 rounded-xl bg-[#0b101b] border border-slate-800 shadow-2xl py-1.5 z-50 text-xs animate-fade-in">
                <div className="px-3 py-1.5 border-b border-slate-800/80 text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                  Inject Outage Scenario
                </div>
                <button
                  onClick={() => handleSimulate('A')}
                  className="w-full text-left px-3 py-2.5 hover:bg-slate-900 flex items-start gap-2.5 transition-colors group"
                >
                  <Zap className="w-4 h-4 text-rose-400 shrink-0 mt-0.5 group-hover:scale-110 transition-transform" />
                  <div>
                    <div className="font-medium text-slate-200">Scenario A: Lock Contention</div>
                    <div className="text-[10px] text-slate-400">Database connection pool exhaustion alert + Git PR correlation.</div>
                  </div>
                </button>
                <button
                  onClick={() => handleSimulate('B')}
                  className="w-full text-left px-3 py-2.5 hover:bg-slate-900 flex items-start gap-2.5 transition-colors group border-t border-slate-800/50"
                >
                  <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0 mt-0.5 group-hover:scale-110 transition-transform" />
                  <div>
                    <div className="font-medium text-slate-200">Scenario B: Upstream Timeout</div>
                    <div className="text-[10px] text-slate-400">Payment gateway 504 Gateway Timeout alert + HTTP logs.</div>
                  </div>
                </button>
              </div>
            )}
          </div>

          {/* User Profile & Persona Switcher */}
          <div className="relative">
            <button
              onClick={() => setShowUserMenu(!showUserMenu)}
              className="flex items-center gap-2 pl-2 pr-2.5 py-1.5 rounded-lg bg-slate-900/90 border border-slate-800 hover:border-slate-700 text-xs text-slate-200 transition-colors"
            >
              <div className="w-5 h-5 rounded-full bg-gradient-to-tr from-indigo-500 to-indigo-700 flex items-center justify-center text-[10px] font-bold text-white uppercase">
                {user?.fullName?.charAt(0) || user?.email?.charAt(0) || 'U'}
              </div>
              <span className="font-medium text-[11px] text-slate-200 max-w-[120px] truncate">
                {user ? user.fullName || user.email.split('@')[0] : 'Sign In'}
              </span>
              <span className={`text-[9px] font-mono px-1.5 py-0.5 rounded border ${
                currentRole === 'ADMIN'
                  ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                  : currentRole === 'SRE'
                  ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                  : 'bg-blue-500/10 text-blue-400 border-blue-500/20'
              }`}>
                {currentRole}
              </span>
              <ChevronDown className="w-3 h-3 text-slate-400" />
            </button>

            {showUserMenu && (
              <div className="absolute right-0 mt-1.5 w-64 rounded-xl bg-[#0b101b] border border-slate-800 shadow-2xl py-2 z-50 text-xs animate-fade-in">
                {/* User Header */}
                <div className="px-3.5 py-2 border-b border-slate-800/80">
                  <div className="font-semibold text-slate-200 truncate">{user?.fullName}</div>
                  <div className="text-[11px] text-slate-400 truncate">{user?.email}</div>
                  <div className="text-[10px] text-indigo-400 font-mono mt-1">
                    Workspace: {user?.workspaceName || 'Personal'}
                  </div>
                </div>

                {/* Switch Persona Section */}
                <div className="px-3.5 py-1.5 text-[10px] font-semibold text-slate-400 uppercase tracking-wider mt-1">
                  Switch Active Persona
                </div>
                <button
                  onClick={() => handleSwitchRole('SRE')}
                  className={`w-full text-left px-3.5 py-1.5 flex items-center justify-between hover:bg-slate-900 transition-colors ${currentRole === 'SRE' ? 'text-emerald-400 font-semibold' : 'text-slate-300'}`}
                >
                  <div className="flex items-center gap-2">
                    <UserCheck className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Alice (On-Call SRE)</span>
                  </div>
                  <span className="text-[9px] bg-emerald-500/10 text-emerald-300 px-1.5 py-0.2 rounded border border-emerald-500/20">SRE</span>
                </button>
                <button
                  onClick={() => handleSwitchRole('ADMIN')}
                  className={`w-full text-left px-3.5 py-1.5 flex items-center justify-between hover:bg-slate-900 transition-colors ${currentRole === 'ADMIN' ? 'text-amber-400 font-semibold' : 'text-slate-300'}`}
                >
                  <div className="flex items-center gap-2">
                    <Shield className="w-3.5 h-3.5 text-amber-400" />
                    <span>Bob (Lead Admin)</span>
                  </div>
                  <span className="text-[9px] bg-amber-500/10 text-amber-300 px-1.5 py-0.2 rounded border border-amber-500/20">ADMIN</span>
                </button>
                <button
                  onClick={() => handleSwitchRole('VIEWER')}
                  className={`w-full text-left px-3.5 py-1.5 flex items-center justify-between hover:bg-slate-900 transition-colors ${currentRole === 'VIEWER' ? 'text-blue-400 font-semibold' : 'text-slate-300'}`}
                >
                  <div className="flex items-center gap-2">
                    <KeyRound className="w-3.5 h-3.5 text-blue-400" />
                    <span>Charlie (Observer)</span>
                  </div>
                  <span className="text-[9px] bg-blue-500/10 text-blue-300 px-1.5 py-0.2 rounded border border-blue-500/20">VIEWER</span>
                </button>

                {/* Actions */}
                <div className="mt-1 pt-1 border-t border-slate-800/80">
                  <Link
                    href="/settings"
                    onClick={() => setShowUserMenu(false)}
                    className="flex items-center gap-2 px-3.5 py-2 text-slate-300 hover:bg-slate-900 hover:text-white transition-colors"
                  >
                    <Settings className="w-3.5 h-3.5 text-slate-400" />
                    <span>Workspace Settings</span>
                  </Link>
                  <button
                    onClick={handleSignOut}
                    className="w-full flex items-center gap-2 px-3.5 py-2 text-rose-400 hover:bg-rose-950/20 transition-colors"
                  >
                    <LogOut className="w-3.5 h-3.5 text-rose-400" />
                    <span>Sign Out</span>
                  </button>
                </div>
              </div>
            )}
          </div>

          <a
            href="https://github.com/ParasSharma101/roottrace"
            target="_blank"
            rel="noopener noreferrer"
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
            title="View GitHub Repository"
          >
            <Github className="w-4 h-4" />
          </a>
        </div>
      </div>
    </header>
  );
}
