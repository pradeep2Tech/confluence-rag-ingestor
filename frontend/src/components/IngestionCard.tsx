import { Play, RefreshCw } from 'lucide-react';
import type { IngestionResponse, IngestionStatus } from '../types/api';
import { LoadingSpinner } from './LoadingSpinner';

interface IngestionCardProps {
  status: IngestionStatus | null;
  lastResult: IngestionResponse | null;
  onStart: () => void;
  onRefresh: () => void;
  starting: boolean;
  refreshing: boolean;
}

function progressPercent(status: IngestionStatus | null): number {
  if (!status || status.totalPages === 0) return 0;
  return Math.round((status.ingestedCount / status.totalPages) * 100);
}

export function IngestionCard({
  status,
  lastResult,
  onStart,
  onRefresh,
  starting,
  refreshing,
}: IngestionCardProps) {
  const percent = progressPercent(status);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Ingestion</h2>
        <p className="mt-1 text-slate-400">
          Crawl Confluence, extract Markdown, chunk content, and ingest vectors into ChromaDB.
        </p>
      </div>

      <section className="card p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold">Pipeline Control</h3>
            <p className="mt-1 text-sm text-slate-400">
              Runs the full pipeline: manifest crawl → markdown → chunks → vectors.
            </p>
          </div>
          <div className="flex gap-2">
            <button type="button" className="btn-secondary" onClick={onRefresh} disabled={refreshing}>
              {refreshing ? <LoadingSpinner /> : <RefreshCw className="h-4 w-4" />}
              Refresh status
            </button>
            <button type="button" className="btn-primary" onClick={onStart} disabled={starting}>
              {starting ? <LoadingSpinner /> : <Play className="h-4 w-4" />}
              Start ingestion
            </button>
          </div>
        </div>

        {status && (
          <div className="mt-6 space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              {status.activeJob ? (
                <span className="badge-warning">Job running: {status.activeJob}</span>
              ) : (
                <span className="badge-neutral">Idle</span>
              )}
              <span className="text-sm text-slate-400">{status.message}</span>
            </div>

            <div>
              <div className="mb-2 flex justify-between text-sm">
                <span className="text-slate-400">Progress</span>
                <span className="font-medium text-slate-200">{percent}%</span>
              </div>
              <div className="h-2.5 overflow-hidden rounded-full bg-slate-800">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-brand-600 to-sky-500 transition-all duration-500"
                  style={{ width: `${percent}%` }}
                />
              </div>
            </div>

            <dl className="grid gap-3 sm:grid-cols-4">
              {[
                ['Total pages', status.totalPages],
                ['Ingested', status.ingestedCount],
                ['Pending', status.pendingCount],
                ['Failed', status.failedCount],
              ].map(([label, value]) => (
                <div key={label as string} className="rounded-xl bg-slate-950/50 px-4 py-3">
                  <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
                  <dd className="mt-1 text-2xl font-bold">{value as number}</dd>
                </div>
              ))}
            </dl>

            {(status.crawlProgress || status.batchProgress) && (
              <details className="rounded-xl border border-slate-800 bg-slate-950/40 p-4">
                <summary className="cursor-pointer text-sm font-medium text-slate-300">
                  Raw progress details
                </summary>
                <pre className="mt-3 overflow-x-auto text-xs text-slate-400">
                  {JSON.stringify(
                    { crawl: status.crawlProgress, batch: status.batchProgress },
                    null,
                    2,
                  )}
                </pre>
              </details>
            )}
          </div>
        )}
      </section>

      {lastResult && (
        <section className="card p-6">
          <h3 className="mb-3 text-lg font-semibold">Last Ingestion Result</h3>
          <div className="flex items-center gap-2">
            <span
              className={
                lastResult.status === 'ERROR'
                  ? 'badge-error'
                  : lastResult.status === 'ACCEPTED'
                    ? 'badge-warning'
                    : 'badge-success'
              }
            >
              {lastResult.status}
            </span>
            <span className="text-sm text-slate-300">{lastResult.message}</span>
          </div>
          {lastResult.errorDetail && (
            <p className="mt-2 text-sm text-rose-300">{lastResult.errorDetail}</p>
          )}
        </section>
      )}
    </div>
  );
}
