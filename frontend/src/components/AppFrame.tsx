'use client';

import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { WorkspaceSession } from '@/components/workspace/WorkspaceSession';
import { WorkspaceShell } from '@/components/workspace/WorkspaceShell';

export function AppFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (['/login','/signup','/forgot-password','/reset-password','/verify-email'].includes(pathname)) return <>{children}</>;
  return <WorkspaceSession><WorkspaceShell>{children}</WorkspaceShell></WorkspaceSession>;
}
