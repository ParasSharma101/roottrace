'use client';

import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { Navbar } from '@/components/Navbar';

export function AppFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (pathname === '/login' || pathname === '/signup') return <>{children}</>;
  return <><Navbar /><main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">{children}</main></>;
}
