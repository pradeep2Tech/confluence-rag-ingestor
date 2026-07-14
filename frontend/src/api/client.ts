import type {
  ChatResponse,
  ConfigRequest,
  ConfigResponse,
  ConfluenceTestResponse,
  HealthResponse,
  IngestionResponse,
  IngestionStatus,
  UiIngestRequest,
} from '../types/api';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers ?? {}),
    },
    ...options,
  });

  if (!response.ok) {
    let detail = response.statusText;
    try {
      const body = await response.json();
      detail = body.detail ?? body.message ?? body.title ?? detail;
    } catch {
      // ignore parse errors
    }
    throw new Error(detail || `Request failed (${response.status})`);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  getConfig: () => request<ConfigResponse>('/api/config'),
  saveConfig: (body: ConfigRequest) =>
    request<ConfigResponse>('/api/config', { method: 'POST', body: JSON.stringify(body) }),
  testConfluence: (body: ConfigRequest) =>
    request<ConfluenceTestResponse>('/api/confluence/test', {
      method: 'POST',
      body: JSON.stringify({
        baseUrl: body.confluenceBaseUrl,
        confluenceTarget: body.confluenceTarget,
        verifySsl: body.verifySsl,
        pat: body.pat,
      }),
    }),
  startIngestion: (options: UiIngestRequest) =>
    request<IngestionResponse>('/api/ingest', {
      method: 'POST',
      body: JSON.stringify(options),
    }),
  getIngestionStatus: (parentPageId?: string) => {
    const query = parentPageId ? `?parentPageId=${encodeURIComponent(parentPageId)}` : '';
    return request<IngestionStatus>(`/api/ingest/status${query}`);
  },
  chat: (question: string, parentPageId?: string) =>
    request<ChatResponse>('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ question, parentPageId }),
    }),
  health: () => request<HealthResponse>('/api/health'),
};
