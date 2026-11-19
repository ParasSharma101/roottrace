const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function load(name) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/lib', name + '.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  vm.runInNewContext(code, { module, exports: module.exports });
  return module.exports;
}
const navigation = load('navigation'), setup = load('setup');

test('incident detail stays under Incidents without falsely activating similarly named routes', () => {
  assert.equal(navigation.activeWorkspaceRoute('/incidents/incident-fixture'), '/incidents');
  assert.equal(navigation.pageTitle('/incidents/incident-fixture'), 'Incident investigation');
  assert.equal(navigation.activeWorkspaceRoute('/incidents-archive'), null);
  assert.equal(navigation.activeWorkspaceRoute('/account'), null);
});

test('navigation names match the user workflow and give personal settings a separate location', () => {
  assert.deepEqual(Array.from(navigation.workspaceNavigation, item => item.label), ['Overview','Incidents','Integrations','Team','Settings']);
  assert.equal(navigation.pageTitle('/account'), 'Your account');
  assert.equal(navigation.pageTitle('/workspaces'), 'Workspaces');
});

test('unknown readiness never claims that a project is connected', () => {
  assert.equal(setup.nextSetupAction(null).title, 'Check your connection');
});

test('creating a key alone still asks the user to send data', () => {
  const action = setup.nextSetupAction({ activeKeyCount:1, acceptedRequestCount:0, processedEventCount:0 });
  assert.equal(action.title, 'Send your first event');
  assert.equal(action.href, '/integrations#send-data');
});

test('HTTP acknowledgement is not presented as completed consumer processing', () => {
  assert.equal(setup.nextSetupAction({ activeKeyCount:1, acceptedRequestCount:1, processedEventCount:0 }).title, 'Confirm data processing');
  assert.equal(setup.nextSetupAction({ activeKeyCount:1, acceptedRequestCount:1, processedEventCount:1 }).href, '/incidents');
});

test('past processing does not hide that all ingestion keys have been revoked', () => {
  assert.equal(setup.nextSetupAction({ activeKeyCount:0, acceptedRequestCount:10, processedEventCount:10 }).title, 'Connect your project');
});
