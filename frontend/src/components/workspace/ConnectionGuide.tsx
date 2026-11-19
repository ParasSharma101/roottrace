'use client';

import { useEffect, useState } from 'react';
import { Check, Copy, Terminal } from 'lucide-react';

export function ConnectionGuide() {
  const [language,setLanguage] = useState<'powershell' | 'curl'>('powershell');
  const [kind,setKind] = useState<'log' | 'alert'>('log');
  const [base,setBase] = useState('https://your-roottrace-host/api/v1');
  const [copied,setCopied] = useState(false),[error,setError] = useState(false);
  useEffect(() => {
    const configured = process.env.NEXT_PUBLIC_API_URL || '/api/v1';
    setBase((configured.startsWith('http') ? configured : window.location.origin + configured).replace(/\/$/,''));
  },[]);
  useEffect(() => { setCopied(false); setError(false); },[kind,language]);
  const endpoint = base + (kind === 'log' ? '/webhooks/logs' : '/webhooks/alerts/app');
  const powershellBody = kind === 'log'
    ? '  serviceName = "your-service"\n  timestamp = [DateTime]::UtcNow.ToString("o")\n  logLevel = "INFO"\n  message = "RootTrace connection check"'
    : '  serviceName = "your-service"\n  alertType = "ConnectionCheck"\n  severity = "SEV4"\n  triggeredAt = [DateTime]::UtcNow.ToString("o")\n  details = @{ message = "Manual connection check" }';
  const json = kind === 'log'
    ? '{"serviceName":"your-service","timestamp":"<CURRENT_ISO_TIME>","logLevel":"INFO","message":"RootTrace connection check"}'
    : '{"serviceName":"your-service","alertType":"ConnectionCheck","severity":"SEV4","triggeredAt":"<CURRENT_ISO_TIME>","details":{"message":"Manual connection check"}}';
  const example = language === 'powershell'
    ? '$env:ROOTTRACE_KEY = "<paste-your-key-here>"\n\n$headers = @{\n  "Content-Type" = "application/json"\n  "X-RootTrace-Key" = $env:ROOTTRACE_KEY\n}\n$body = @{\n'+powershellBody+'\n} | ConvertTo-Json\n\nInvoke-RestMethod -Method Post `\n  -Uri "'+endpoint+'" `\n  -Headers $headers -Body $body'
    : '# Replace <CURRENT_ISO_TIME> with the current UTC time.\nexport ROOTTRACE_KEY="<paste-your-key-here>"\n\ncurl --request POST "'+endpoint+'" \\\n  --header "Content-Type: application/json" \\\n  --header "X-RootTrace-Key: $ROOTTRACE_KEY" \\\n  --data \''+json+'\'';
  const copy = async () => {
    try { await navigator.clipboard.writeText(example); setCopied(true); setError(false); }
    catch { setError(true); }
  };
  return <section className="ux-card" id="send-data">
    <div className="ux-section-heading"><div><p className="ux-eyebrow">STEP 2</p><h2>Send data from your project</h2><p>Start with a log to check the connection. Send an alert when you want to create an incident.</p></div><Terminal size={22} aria-hidden="true" /></div>
    <div className="ux-example-controls"><div className="ux-segmented" aria-label="Event example"><button aria-pressed={kind === 'log'} onClick={() => setKind('log')}>Log · check connection</button><button aria-pressed={kind === 'alert'} onClick={() => setKind('alert')}>Alert · create an incident</button></div><label className="ux-language-picker">Example language<select value={language} onChange={event => setLanguage(event.target.value as 'powershell' | 'curl')}><option value="powershell">PowerShell</option><option value="curl">cURL</option></select></label></div>
    <p className="ux-guide-note">Replace the key placeholder with your saved key and <code>your-service</code> with your service name. Keep the key in server-side configuration.</p>
    <div className="ux-code-wrap"><button className="ux-code-copy" onClick={() => void copy()}><span>{copied ? 'Copied' : 'Copy example'}</span>{copied ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}</button><pre className="ux-code"><code>{example}</code></pre></div>
    {error && <p className="ux-notice warning" role="alert">Clipboard unavailable. Select and copy the example above.</p>}
    <div className="ux-notice">A <strong>202 Accepted</strong> response means your request was queued. Use the connection status below to confirm processing.{kind === 'alert' && ' Running the alert example creates a real test incident in this workspace.'}</div>
  </section>;
}
