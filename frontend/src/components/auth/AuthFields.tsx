'use client';

import { useState, type InputHTMLAttributes } from 'react';
import { Eye, EyeOff, LockKeyhole, type LucideIcon } from 'lucide-react';

type FieldProps = InputHTMLAttributes<HTMLInputElement> & { id: string; label: string; icon: LucideIcon; hint?: string };

export function AuthField({ id, label, icon: Icon, hint, ...input }: FieldProps) {
  return <div className="auth-field">
    <label htmlFor={id}>{label}</label>
    <div className="auth-input-wrap"><Icon size={18} aria-hidden="true" /><input id={id} aria-describedby={hint ? id + '-hint' : undefined} {...input} /></div>
    {hint && <p id={id + '-hint'} className="auth-field-hint">{hint}</p>}
  </div>;
}

export function AuthPasswordField({ id, label, hint, ...input }: Omit<FieldProps, 'icon' | 'type'>) {
  const [visible, setVisible] = useState(false);
  return <div className="auth-field">
    <label htmlFor={id}>{label}</label>
    <div className="auth-input-wrap auth-password-wrap">
      <LockKeyhole size={18} aria-hidden="true" />
      <input id={id} {...input} type={visible ? 'text' : 'password'} aria-describedby={hint ? id + '-hint' : undefined} />
      <button type="button" className="auth-password-toggle" onClick={() => setVisible(current => !current)} disabled={input.disabled}
        aria-label={visible ? 'Hide password' : 'Show password'} aria-pressed={visible} aria-controls={id}>
        {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
      </button>
    </div>
    {hint && <p id={id + '-hint'} className="auth-field-hint">{hint}</p>}
  </div>;
}
