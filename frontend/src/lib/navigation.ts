export const workspaceNavigation = [
  { href: '/', label: 'Overview' },
  { href: '/incidents', label: 'Incidents' },
  { href: '/integrations', label: 'Integrations' },
  { href: '/team', label: 'Team' },
  { href: '/settings', label: 'Settings' },
] as const;

export function activeWorkspaceRoute(path: string): string | null {
  if (path === '/') return '/';
  return workspaceNavigation.find(item => item.href !== '/' && (path === item.href || path.startsWith(item.href + '/')))?.href ?? null;
}

export function pageTitle(path: string): string {
  if (path === '/account') return 'Your account';
  if (path === '/workspaces') return 'Workspaces';
  if (path.startsWith('/incidents/')) return 'Incident investigation';
  return workspaceNavigation.find(item => item.href === activeWorkspaceRoute(path))?.label ?? 'RootTrace';
}
