'use client';

export interface AuthUser {
  email: string;
  name: string;
  roles: string[];
  token: string;
  isBreakGlass?: boolean;
}

const TOKEN_KEY = 'roottrace_auth_token';
const USER_KEY = 'roottrace_auth_user';

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getAuthUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const userJson = localStorage.getItem(USER_KEY);
  if (!userJson) return null;
  try {
    return JSON.parse(userJson);
  } catch {
    return null;
  }
}

export function setAuthSession(user: AuthUser): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, user.token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  window.dispatchEvent(new Event('auth-change'));
}

export function clearAuthSession(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  window.dispatchEvent(new Event('auth-change'));
}

export async function loginDemoRole(role: 'SRE' | 'VIEWER' | 'ADMIN'): Promise<AuthUser> {
  const API_BASE =
    process.env.NEXT_PUBLIC_API_URL ||
    (typeof window !== 'undefined' ? '/api/v1' : 'http://localhost:8080/api/v1');

  const res = await fetch(`${API_BASE}/auth/demo-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  });

  if (!res.ok) {
    throw new Error('Failed to login with demo role');
  }

  const data = await res.json();
  const authUser: AuthUser = {
    email: data.email,
    name: data.name,
    roles: data.roles,
    token: data.token,
    isBreakGlass: false,
  };

  setAuthSession(authUser);
  return authUser;
}

export async function ensureDefaultAuth(): Promise<AuthUser> {
  const existing = getAuthUser();
  if (existing && getAuthToken()) {
    return existing;
  }
  return loginDemoRole('SRE');
}
