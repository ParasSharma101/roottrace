import type { ReactNode } from 'react';

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description: string; actions?: ReactNode }) {
  return <header className="ux-page-header"><div>{eyebrow && <p className="ux-eyebrow">{eyebrow}</p>}<h1>{title}</h1><p className="ux-page-description">{description}</p></div>{actions && <div className="ux-header-actions">{actions}</div>}</header>;
}
