const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function client(response) {
  const module = { exports: {} }, calls = [];
  let cleared = false;
  const source = fs.readFileSync(path.join(__dirname, '../src/lib/api.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  vm.runInNewContext(code, {
    module, exports: module.exports, process: { env: { NEXT_PUBLIC_API_URL: '/api/v1' } },
    fetch: async (url, options) => { calls.push({ url, options }); return response; },
    require(name) {
      if (name === './auth') return { getAuthToken: () => 'session-fixture', clearAuthSession: () => { cleared = true; } };
      throw new Error('Unexpected dependency: ' + name);
    },
  });
  return { api: module.exports, calls, cleared: () => cleared };
}

const member = { membershipId: 'membership-fixture', userId: 'user-fixture', role: 'ROLE_VIEWER', version: 4 };

test('role edits send the reviewed membership identity and version', async () => {
  const { api, calls } = client({ ok: true, status: 200, json: async () => ({ ...member, role: 'ROLE_SRE', version: 5 }) });
  const changed = await api.changeMemberRole(member, 'ROLE_SRE', 'On-call rotation');
  assert.equal(changed.version, 5);
  assert.equal(calls[0].url, '/api/v1/workspace/members/membership-fixture/role');
  assert.equal(calls[0].options.headers.Authorization, 'Bearer session-fixture');
  assert.deepEqual(JSON.parse(calls[0].options.body), { role: 'ROLE_SRE', expectedVersion: 4, reason: 'On-call rotation' });
});

test('successful removal accepts an empty 204 response', async () => {
  const { api } = client({ ok: true, status: 204, json: () => { throw new Error('204 must not be decoded as JSON'); } });
  await api.removeMember(member, 'Access no longer required');
});

test('conflicts preserve the reviewed version and require refresh rather than retrying blindly', async () => {
  const { api, calls } = client({ ok: false, status: 409 });
  await assert.rejects(api.removeMember(member, 'Reviewed removal'), /last administrator.*Refresh/);
  assert.equal(calls.length, 1);
  assert.equal(JSON.parse(calls[0].options.body).expectedVersion, 4);
});

test('revoked membership sessions clear stale local credentials', async () => {
  const { api, cleared } = client({ ok: false, status: 401 });
  await assert.rejects(api.changeMemberRole(member, 'ROLE_ADMIN', 'Reviewed promotion'), /Sign in again/);
  assert.equal(cleared(), true);
});
