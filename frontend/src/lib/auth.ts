'use client';

import { isDemoEnabled } from './demo';

export interface AuthUser {
  userId?: string;
  email: string;
  fullName: string;
  name?: string;
  roles: string[];
  token: string;
  workspaceId?: string | null;
  workspaceName?: string | null;
  workspaceSlug?: string | null;
  isBreakGlass?: boolean;
}

export const TOKEN_KEY = 'roottrace_auth_token';
const USER_KEY = 'roottrace_auth_user';

export interface WorkspaceMembership {
  workspaceId: string;
  name: string;
  slug: string;
  role: string;
}

async function accountRequest(path: string, options?: RequestInit): Promise<Response> {
  const token = getAuthToken();
  if (!token) throw new Error('Sign in to continue');
  const response = await fetch(`${getApiBase()}/auth/${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!response.ok) {
    if (response.status === 401) clearAuthSession();
    throw new Error(response.status === 401 ? 'Your session expired. Sign in again.' : 'Workspace access unavailable');
  }
  return response;
}

export async function listMyWorkspaces(): Promise<WorkspaceMembership[]> {
  return (await accountRequest('workspaces')).json();
}

export async function selectWorkspace(workspaceId: string): Promise<void> {
  const response = await accountRequest('select-workspace', {
    method: 'POST', body: JSON.stringify({ workspaceId }),
  });
  setAuthSession(await response.json());
}

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getAuthUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const userJson = localStorage.getItem(USER_KEY);
  if (!userJson) return null;
  try {
    const user = JSON.parse(userJson) as AuthUser & { apiKey?: unknown };
    if ('apiKey' in user) {
      delete user.apiKey;
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    }
    return user;
  } catch {
    return null;
  }
}

export function setAuthSession(user: AuthUser): void {
  if (typeof window === 'undefined') return;
  const safeUser = { ...user } as AuthUser & { apiKey?: unknown };
  delete safeUser.apiKey;
  localStorage.setItem(TOKEN_KEY, user.token);
  localStorage.setItem(USER_KEY, JSON.stringify(safeUser));
  window.dispatchEvent(new Event('auth-change'));
}

export function clearAuthSession(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  window.dispatchEvent(new Event('auth-change'));
}

const getApiBase = () =>
  process.env.NEXT_PUBLIC_API_URL ||
  (typeof window !== 'undefined' ? '/api/v1' : 'http://localhost:8080/api/v1');

export async function loginWithCredentials(email: string, password: string): Promise<AuthUser> {
  const res = await fetch(`${getApiBase()}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error(errorData.message || 'Invalid email or password');
  }

  const data = await res.json();
  const user: AuthUser = {
    userId: data.userId,
    email: data.email,
    fullName: data.fullName,
    name: data.fullName || data.email.split('@')[0],
    roles: data.roles,
    token: data.token,
    workspaceId: data.workspaceId,
    workspaceName: data.workspaceName,
    workspaceSlug: data.workspaceSlug,
    isBreakGlass: false,
  };

  setAuthSession(user);
  return user;
}

export async function signupUser(fullName: string, email: string, password: string, workspaceName: string): Promise<AuthUser> {
  const res = await fetch(`${getApiBase()}/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fullName, email, password, workspaceName }),
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error(errorData.message || 'Failed to create account');
  }

  const data = await res.json();
  const user: AuthUser = {
    userId: data.userId,
    email: data.email,
    fullName: data.fullName,
    name: data.fullName,
    roles: data.roles,
    token: data.token,
    workspaceId: data.workspaceId,
    workspaceName: data.workspaceName,
    workspaceSlug: data.workspaceSlug,
    isBreakGlass: false,
  };

  setAuthSession(user);
  return user;
}

export async function loginDemoRole(role: 'SRE' | 'VIEWER' | 'ADMIN'): Promise<AuthUser> {
  if (!isDemoEnabled) throw new Error('Demo login is disabled');
  const res = await fetch(`${getApiBase()}/auth/demo-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  });

  if (!res.ok) {
    throw new Error('Failed to login with demo role');
  }

  const data = await res.json();
  const authUser: AuthUser = {
    userId: data.userId,
    email: data.email,
    fullName: data.fullName,
    name: data.fullName || data.email.split('@')[0],
    roles: data.roles,
    token: data.token,
    workspaceId: data.workspaceId,
    workspaceName: data.workspaceName,
    workspaceSlug: data.workspaceSlug,
    isBreakGlass: false,
  };

  setAuthSession(authUser);
  return authUser;
}
