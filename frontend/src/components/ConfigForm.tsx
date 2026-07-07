import { Save, Wifi } from 'lucide-react';
import type { ConfigRequest, ConfigResponse } from '../types/api';
import { LoadingSpinner } from './LoadingSpinner';

interface ConfigFormProps {
  config: ConfigRequest;
  savedConfig: ConfigResponse | null;
  onChange: (patch: Partial<ConfigRequest>) => void;
  onSave: () => void;
  onTest: () => void;
  saving: boolean;
  testing: boolean;
}

export function ConfigForm({
  config,
  savedConfig,
  onChange,
  onSave,
  onTest,
  saving,
  testing,
}: ConfigFormProps) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Configuration</h2>
        <p className="mt-1 text-slate-400">
          Connect to Confluence, configure AI models, and choose your vector store.
        </p>
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="card p-6">
          <h3 className="mb-4 text-lg font-semibold">LLM & Embeddings</h3>
          <div className="space-y-4">
            <div>
              <label className="label" htmlFor="llmProvider">
                LLM Provider
              </label>
              <select
                id="llmProvider"
                className="input"
                value={config.llmProvider ?? 'ollama'}
                onChange={(e) => onChange({ llmProvider: e.target.value })}
              >
                <option value="ollama">Ollama (local)</option>
              </select>
            </div>
            <div>
              <label className="label" htmlFor="llmModel">
                Chat Model
              </label>
              <input
                id="llmModel"
                className="input"
                placeholder="llama3.2"
                value={config.llmModel ?? ''}
                onChange={(e) => onChange({ llmModel: e.target.value })}
              />
            </div>
            <div>
              <label className="label" htmlFor="ollamaBaseUrl">
                Ollama Base URL
              </label>
              <input
                id="ollamaBaseUrl"
                className="input"
                placeholder="http://localhost:11434"
                value={config.ollamaBaseUrl ?? ''}
                onChange={(e) => onChange({ ollamaBaseUrl: e.target.value })}
              />
            </div>
            <div>
              <label className="label" htmlFor="embeddingModel">
                Embedding Model
              </label>
              <input
                id="embeddingModel"
                className="input"
                placeholder="nomic-embed-text"
                value={config.embeddingModel ?? ''}
                onChange={(e) => onChange({ embeddingModel: e.target.value })}
              />
            </div>
          </div>
        </section>

        <section className="card p-6">
          <h3 className="mb-4 text-lg font-semibold">Confluence</h3>
          <div className="space-y-4">
            <div>
              <label className="label" htmlFor="confluenceBaseUrl">
                Base URL
              </label>
              <input
                id="confluenceBaseUrl"
                className="input"
                placeholder="https://confluence.example.com"
                value={config.confluenceBaseUrl ?? ''}
                onChange={(e) => onChange({ confluenceBaseUrl: e.target.value })}
              />
            </div>
            <div>
              <label className="label" htmlFor="confluenceTarget">
                Page URL, Page ID, or Space Key
              </label>
              <input
                id="confluenceTarget"
                className="input"
                placeholder="12345 or https://…/pages/12345/… or ENG"
                value={config.confluenceTarget ?? ''}
                onChange={(e) => onChange({ confluenceTarget: e.target.value })}
              />
              {savedConfig?.parentPageId && (
                <p className="mt-1.5 text-xs text-slate-500">
                  Resolved parent page ID: <span className="font-mono text-slate-400">{savedConfig.parentPageId}</span>
                </p>
              )}
            </div>
            <div>
              <label className="label" htmlFor="pat">
                Personal Access Token
              </label>
              <input
                id="pat"
                type="password"
                className="input"
                placeholder={savedConfig?.patConfigured ? savedConfig.maskedPat ?? '••••••••' : 'Enter PAT'}
                autoComplete="off"
                value={config.pat ?? ''}
                onChange={(e) => onChange({ pat: e.target.value })}
              />
              <p className="mt-1.5 text-xs text-slate-500">
                Token is sent to the backend only — never stored in browser localStorage.
              </p>
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-300">
              <input
                type="checkbox"
                className="rounded border-slate-600 bg-slate-900 text-brand-600 focus:ring-brand-500"
                checked={config.verifySsl ?? false}
                onChange={(e) => onChange({ verifySsl: e.target.checked })}
              />
              Verify SSL certificates
            </label>
          </div>
        </section>

        <section className="card p-6 xl:col-span-2">
          <h3 className="mb-4 text-lg font-semibold">Vector Store</h3>
          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <label className="label" htmlFor="vectorStore">
                Provider
              </label>
              <select
                id="vectorStore"
                className="input"
                value={config.vectorStore ?? 'chroma'}
                onChange={(e) => onChange({ vectorStore: e.target.value })}
              >
                <option value="chroma">ChromaDB</option>
              </select>
            </div>
            <div>
              <label className="label" htmlFor="chromaHost">
                Chroma Host
              </label>
              <input
                id="chromaHost"
                className="input"
                placeholder="http://localhost"
                value={config.chromaHost ?? ''}
                onChange={(e) => onChange({ chromaHost: e.target.value })}
              />
            </div>
            <div>
              <label className="label" htmlFor="chromaPort">
                Chroma Port
              </label>
              <input
                id="chromaPort"
                type="number"
                className="input"
                placeholder="8000"
                value={config.chromaPort ?? ''}
                onChange={(e) => onChange({ chromaPort: Number(e.target.value) || undefined })}
              />
            </div>
            <div className="md:col-span-3">
              <label className="label" htmlFor="chromaCollection">
                Collection Name
              </label>
              <input
                id="chromaCollection"
                className="input"
                placeholder="confluence-rag"
                value={config.chromaCollectionName ?? ''}
                onChange={(e) => onChange({ chromaCollectionName: e.target.value })}
              />
            </div>
          </div>
        </section>
      </div>

      <div className="flex flex-wrap gap-3">
        <button type="button" className="btn-primary" onClick={onSave} disabled={saving}>
          {saving ? <LoadingSpinner /> : <Save className="h-4 w-4" />}
          Save configuration
        </button>
        <button type="button" className="btn-secondary" onClick={onTest} disabled={testing}>
          {testing ? <LoadingSpinner /> : <Wifi className="h-4 w-4" />}
          Test Confluence connection
        </button>
      </div>
    </div>
  );
}
