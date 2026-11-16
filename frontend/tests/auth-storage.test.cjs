const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function session() {
  const values = new Map();
  const localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  };
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/lib/auth.ts'), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  vm.runInNewContext(compiled, {
    module, exports: module.exports, localStorage,
    window: { dispatchEvent() {} }, Event: class Event {},
    require(name) {
      if (name === './demo') return { isDemoEnabled: false };
      throw new Error('Unexpected dependency: ' + name);
    },
  });
  return { api: module.exports, localStorage };
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
