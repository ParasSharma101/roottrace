'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Activity, ShieldAlert, Zap, CheckCircle2, Github } from 'lucide-react';
import { triggerScenarioA, triggerScenarioB } from '@/lib/api';

export function Navbar({ onRefresh }: { onRefresh?: () => void }) {
  const [simulating, setSimulating] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

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
