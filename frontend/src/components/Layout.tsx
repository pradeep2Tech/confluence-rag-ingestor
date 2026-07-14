import { Database, MessageSquare, Settings2, Sparkles } from 'lucide-react';
import { HealthChecksPanel } from './HealthChecksPanel';
import type { ComponentHealth } from '../types/api';

export type NavSection = 'config' | 'ingest' | 'chat';

interface LayoutProps {
  active: NavSection;
  onNavigate: (section: NavSection) => void;
  children: React.ReactNode;
  health: {
    application: ComponentHealth | null;
    vectorStore: ComponentHealth | null;
    model: ComponentHealth | null;
  } | null;
  healthLoading: boolean;
}

const navItems: { id: NavSection; label: string; icon: typeof Settings2 }[] = [
  { id: 'config', label: 'Configuration', icon: Settings2 },
  { id: 'ingest', label: 'Ingestion', icon: Database },
  { id: 'chat', label: 'Ask AI', icon: MessageSquare },
];

export function Layout({ active, onNavigate, children, health, healthLoading }: LayoutProps) {
  return (
    <div className="min-h-screen lg:flex">
      <aside className="border-b border-slate-800 bg-slate-900/80 lg:fixed lg:inset-y-0 lg:w-72 lg:border-b-0 lg:border-r">
        <div className="flex h-full flex-col p-6">
          <div className="mb-8 flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-600 shadow-lg shadow-brand-600/30">
              <Sparkles className="h-6 w-6 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-tight">Confluence RAG</h1>
              <p className="text-xs text-slate-400">Ingestor Dashboard</p>
            </div>
          </div>

          <nav className="space-y-1">
            {navItems.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                type="button"
                onClick={() => onNavigate(id)}
                className={`flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left text-sm font-medium transition ${
                  active === id
                    ? 'bg-brand-600 text-white shadow-lg shadow-brand-600/20'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                <Icon className="h-5 w-5" />
                {label}
              </button>
            ))}
          </nav>

          <div className="mt-auto pt-8">
            <HealthChecksPanel health={health} loading={healthLoading} />
          </div>
        </div>
      </aside>

      <main className="flex-1 lg:ml-72">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">{children}</div>
      </main>
    </div>
  );
}
