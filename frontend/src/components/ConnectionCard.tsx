import { Plug, Server } from 'lucide-react';
import type { ConfluenceTestResponse } from '../types/api';

interface ConnectionCardProps {
  result: ConfluenceTestResponse | null;
}

export function ConnectionCard({ result }: ConnectionCardProps) {
  return (
    <section className="card p-6">
      <div className="mb-4 flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-sky-500/15 text-sky-300">
          <Plug className="h-5 w-5" />
        </div>
        <div>
          <h3 className="text-lg font-semibold">Connection Status</h3>
          <p className="text-sm text-slate-400">Latest Confluence connectivity test</p>
        </div>
      </div>

      {!result ? (
        <p className="text-sm text-slate-500">Run a connection test from Configuration to verify your credentials.</p>
      ) : (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <span className={result.connected ? 'badge-success' : 'badge-error'}>
              {result.connected ? 'Connected' : 'Failed'}
            </span>
            <span className="text-sm text-slate-300">{result.message}</span>
          </div>
          {result.connected && (
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              {result.displayName && (
                <div className="rounded-lg bg-slate-950/50 px-3 py-2">
                  <dt className="text-slate-500">User</dt>
                  <dd className="font-medium">{result.displayName}</dd>
                </div>
              )}
              {result.parentPageId && (
                <div className="rounded-lg bg-slate-950/50 px-3 py-2">
                  <dt className="text-slate-500">Parent Page ID</dt>
                  <dd className="font-mono font-medium">{result.parentPageId}</dd>
                </div>
              )}
              {result.pageTitle && (
                <div className="rounded-lg bg-slate-950/50 px-3 py-2 sm:col-span-2">
                  <dt className="text-slate-500">Page Title</dt>
                  <dd className="font-medium">{result.pageTitle}</dd>
                </div>
              )}
            </dl>
          )}
        </div>
      )}

      <div className="mt-4 flex items-center gap-2 text-xs text-slate-500">
        <Server className="h-3.5 w-3.5" />
        PAT is held server-side in memory and masked in API responses.
      </div>
    </section>
  );
}
