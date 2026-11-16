'use client';

import { useEffect, useState } from 'react';
import { createIngestionKey, IngestionKey, listIngestionKeys, revokeIngestionKey } from '@/lib/api';

export function IngestionKeysPanel({ isAdmin }: { isAdmin: boolean }) {
  const [keys, setKeys] = useState<IngestionKey[]>([]);
  const [name, setName] = useState('');
  const [secret, setSecret] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [confirmRevoke, setConfirmRevoke] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!isAdmin) { setLoading(false); return; }
    listIngestionKeys().then(setKeys).catch(() => setError('Unable to load ingestion keys.'))
      .finally(() => setLoading(false));
  }, [isAdmin]);

  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    if (secret || loading || busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await createIngestionKey(name.trim());
      setSecret(result.secret);
      setKeys(current => [result.key, ...current]);
      setName('');
      setCopied(false);
    } catch { setError('Unable to create a key. Check your name and administrator access.'); }
    finally { setBusy(false); }
  };

  const revoke = async (id: string) => {
    setBusy(true);
    setError(null);
    try {
      await revokeIngestionKey(id);
      setKeys(await listIngestionKeys());
      setConfirmRevoke(null);
    } catch { setError('Unable to revoke this key. Refresh and try again.'); }
    finally { setBusy(false); }
  };

  const copy = async () => {
    if (!secret) return;
    try { await navigator.clipboard.writeText(secret); setCopied(true); }
    catch { setError('Clipboard unavailable. Select and copy the key shown below.'); }
  };

  return <section className="p-6 rounded-2xl bg-[#0b101b] border border-slate-800 shadow-xl space-y-4">
    <div>
      <h2 className="text-sm font-semibold text-slate-200">Ingestion keys</h2>
      <p className="mt-2 text-xs text-slate-400">Use separate named credentials for each integration. New secrets are shown once.</p>
      <p className="mt-2 text-xs text-amber-200">Webhook ingestion with these keys is not available in this build.</p>
    </div>
    {!isAdmin ? <p className="text-sm text-slate-400">A workspace administrator can manage ingestion keys.</p> : <>
      {error && <p role="alert" className="text-sm text-rose-300">{error}</p>}
      {secret && <div className="rounded-xl border border-amber-500/40 p-4 space-y-3">
        <p className="text-sm text-amber-200">Save this key securely now. It cannot be retrieved after you leave this page.</p>
        <code className="block break-all select-all text-xs text-slate-200">{secret}</code>
        <div className="flex gap-3 text-xs">
          <button type="button" onClick={copy} className="rounded bg-slate-800 px-3 py-2">{copied ? 'Copied' : 'Copy key'}</button>
          <button type="button" onClick={() => setSecret(null)} className="rounded bg-indigo-600 px-3 py-2">I’ve saved this key</button>
        </div>
      </div>}
      <form onSubmit={create} className="flex gap-2">
        <label className="sr-only" htmlFor="key-name">Integration name</label>
        <input id="key-name" required maxLength={128} value={name} onChange={event => setName(event.target.value)}
          placeholder="Integration name" className="min-w-0 flex-1 rounded border border-slate-700 bg-slate-900 px-3 py-2 text-sm" />
        <button disabled={loading || busy || !!secret || !name.trim()} className="rounded bg-indigo-600 px-3 py-2 text-sm disabled:opacity-50">Create key</button>
      </form>
      {loading ? <p className="text-sm text-slate-400">Loading keys...</p> :
        keys.length === 0 && !error ? <p className="text-sm text-slate-400">No ingestion keys created.</p> :
        <ul className="divide-y divide-slate-800">{keys.map(key => <li key={key.id} className="py-3 space-y-2">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-sm">{key.name}</p>
              <p className="text-xs font-mono text-slate-400">{key.prefix}… · {key.revokedAt ? 'Revoked' : 'Not revoked'}</p>
            </div>
            {!key.revokedAt && <button disabled={busy || !!secret} onClick={() => setConfirmRevoke(key.id)}
              className="text-xs text-rose-300 disabled:opacity-50">Revoke</button>}
          </div>
          {confirmRevoke === key.id && <div className="text-xs text-rose-200 space-y-2">
            <p>Revoke this key? Integrations using it will lose access.</p>
            <button disabled={busy} onClick={() => revoke(key.id)} className="mr-4 underline">Confirm revoke</button>
            <button disabled={busy} onClick={() => setConfirmRevoke(null)}>Cancel</button>
          </div>}
        </li>)}</ul>}
    </>}
  </section>;
}
