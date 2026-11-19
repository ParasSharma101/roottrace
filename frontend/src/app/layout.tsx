import type { Metadata } from 'next';
import './globals.css';
import './auth.css';
import { AppFrame } from '@/components/AppFrame';

export const metadata: Metadata = {
  title: 'RootTrace — AI Production Incident Investigator',
  description: 'Event-driven observability system correlating alerts, logs, releases, and PR diffs into grounded root-cause hypotheses with Gemini AI.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="min-h-screen bg-[#090d16] text-slate-100 antialiased">
        <AppFrame>{children}</AppFrame>
      </body>
    </html>
  );
}
