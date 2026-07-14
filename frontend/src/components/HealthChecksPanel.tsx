import type { ComponentHealth } from '../types/api';

interface HealthCheckRowProps {
  label: string;
  health: ComponentHealth | null;
  loading: boolean;
}

function statusDotClass(health: ComponentHealth | null, loading: boolean): string {
  if (loading || health === null) {
    return 'bg-amber-400';
  }
  return health.status === 'UP' ? 'bg-emerald-400' : 'bg-rose-400';
}

function statusLabel(health: ComponentHealth | null, loading: boolean): string {
  if (loading || health === null) {
    return 'Checking…';
  }
  return health.status === 'UP' ? 'Connected' : 'Disconnected';
}

function HealthCheckRow({ label, health, loading }: HealthCheckRowProps) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-sm text-slate-300">{label}</span>
      <div className="flex items-center gap-2">
        <span
          className={`h-2.5 w-2.5 shrink-0 rounded-full ${statusDotClass(health, loading)}`}
          title={health?.message}
        />
        <span className="text-xs text-slate-400">{statusLabel(health, loading)}</span>
      </div>
    </div>
  );
}

interface HealthChecksPanelProps {
  health: {
    application: ComponentHealth | null;
    vectorStore: ComponentHealth | null;
    model: ComponentHealth | null;
  } | null;
  loading: boolean;
}

export function HealthChecksPanel({ health, loading }: HealthChecksPanelProps) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Health Checks</p>
      <div className="mt-3 space-y-2.5">
        <HealthCheckRow label="Application" health={health?.application ?? null} loading={loading} />
        <HealthCheckRow label="Vector Store" health={health?.vectorStore ?? null} loading={loading} />
        <HealthCheckRow label="Model" health={health?.model ?? null} loading={loading} />
      </div>
    </div>
  );
}
