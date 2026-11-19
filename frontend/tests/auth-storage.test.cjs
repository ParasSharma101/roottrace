const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function session(fetchResult) {
  const values = new Map();
  const localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  };
  const module = { exports: {} };
  const calls = [];
  const source = fs.readFileSync(path.join(__dirname, '../src/lib/auth.ts'), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  vm.runInNewContext(compiled, {
    module, exports: module.exports, localStorage, TextEncoder,
    process: { env: { NEXT_PUBLIC_API_URL: '/api/v1' } },
    fetch: async (url, options) => {
      calls.push({ url, options });
      if (fetchResult instanceof Error) throw fetchResult;
      return fetchResult ?? { ok: true, status: 204 };
    },
    window: { dispatchEvent() {} }, Event: class Event {},
    require(name) {
      if (name === './demo') return { isDemoEnabled: false };
      throw new Error('Unexpected dependency: ' + name);
    },
  });
  return { api: module.exports, localStorage, calls };
}

test('reading an old profile removes its saved ingestion credential', () => {
  const { api, localStorage } = session();
  localStorage.setItem('roottrace_auth_user', JSON.stringify({ email: 'owner@example.com', apiKey: 'old-fixture-secret' }));
  const user = api.getAuthUser();
  assert.equal(user.email, 'owner@example.com');
  assert.equal(Object.hasOwn(user, 'apiKey'), false);
  assert.equal(localStorage.getItem('roottrace_auth_user').includes('old-fixture-secret'), false);
});

test('saving a session preserves login while discarding legacy key fields', () => {
  const { api, localStorage } = session();
  api.setAuthSession({ email: 'owner@example.com', token: 'session-fixture-token', roles: [], apiKey: 'do-not-store' });
  assert.equal(api.getAuthToken(), 'session-fixture-token');
  const stored = JSON.parse(localStorage.getItem('roottrace_auth_user'));
  assert.equal(stored.email, 'owner@example.com');
  assert.equal(Object.hasOwn(stored, 'apiKey'), false);
});

test('sign out revokes the server session before removing local credentials', async () => {
  const { api, calls } = session();
  api.setAuthSession({ token: 'session-fixture-token', email: 'owner@example.com', roles: [] });
  await api.signOut();
  assert.equal(calls[0].url, '/api/v1/auth/logout');
  assert.equal(calls[0].options.headers.Authorization, 'Bearer session-fixture-token');
  assert.equal(api.getAuthToken(), null);
});

test('failed server sign out keeps credentials for a retry', async () => {
  const { api } = session(new Error('offline'));
  api.setAuthSession({ token: 'session-fixture-token', email: 'owner@example.com', roles: [] });
  await assert.rejects(api.signOut(), /offline/);
  assert.equal(api.getAuthToken(), 'session-fixture-token');
});

test('sign out everywhere uses the account-wide endpoint', async () => {
  const { api, calls } = session();
  api.setAuthSession({ token: 'session-fixture-token', email: 'owner@example.com', roles: [] });
  await api.signOut(true);
  assert.equal(calls[0].url, '/api/v1/auth/logout-all');
  assert.equal(api.getAuthToken(), null);
});

test('reset submits proof in a body without storing it or sending a session', async () => {
  const { api, calls, localStorage } = session({ ok: true, status: 200 });
  api.setAuthSession({ token: 'old-session', email: 'owner@example.com', roles: [] });
  await api.resetPassword('rt_rst_fixture-secret', 'replacement-password-123');
  assert.equal(calls[0].url, '/api/v1/auth/password/reset');
  assert.equal(calls[0].options.headers.Authorization, undefined);
  assert.equal(JSON.parse(calls[0].options.body).token, 'rt_rst_fixture-secret');
  assert.equal(localStorage.getItem('roottrace_auth_user'), null);
  assert.equal(api.getAuthToken(), null);
});

test('new password validation rejects short and truncated multibyte input', () => {
  const { api } = session();
  assert.throws(() => api.validateNewPassword('too-short'), /15 characters/);
  assert.throws(() => api.validateNewPassword('é'.repeat(37)), /72 bytes/);
  assert.doesNotThrow(() => api.validateNewPassword('a'.repeat(72)));
});

test('retrying logout for an inactive session clears local state', async () => {
  const { api } = session({ ok: false, status: 401 });
  api.setAuthSession({ token: 'inactive-session', email: 'owner@example.com', roles: [] });
  await api.signOut();
  assert.equal(api.getAuthToken(), null);
});

test('an inactive session cannot report successful account-wide revocation', async () => {
  const { api } = session({ ok: false, status: 401 });
  api.setAuthSession({ token: 'inactive-session', email: 'owner@example.com', roles: [] });
  await assert.rejects(api.signOut(true), /Sign in again/);
  assert.equal(api.getAuthToken(), null);
});

test('workspace entry refreshes cached permissions from the live account profile', async () => {
  const { api } = session({ ok: true, status: 200, json: async () => ({ userId: 'user-fixture', email: 'owner@example.com', fullName: 'Owner', roles: ['ROLE_VIEWER'], emailVerified: true, workspaceId: 'workspace-fixture' }) });
  api.setAuthSession({ token: 'existing-session', email: 'owner@example.com', roles: ['ROLE_ADMIN'] });
  const profile = await api.getCurrentProfile();
  api.setAuthSession(profile);
  assert.equal(profile.token, 'existing-session');
  assert.deepEqual(Array.from(api.getAuthUser().roles), ['ROLE_VIEWER']);
  assert.equal(api.getAuthUser().emailVerified, true);
});

test('expired profile requests clear cached workspace identity', async () => {
  const { api } = session({ ok: false, status: 401 });
  api.setAuthSession({ token: 'expired-session', email: 'owner@example.com', roles: ['ROLE_ADMIN'] });
  await assert.rejects(api.getCurrentProfile(), /Sign in again/);
  assert.equal(api.getAuthToken(), null);
  assert.equal(api.getAuthUser(), null);
});
