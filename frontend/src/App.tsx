import { useCallback, useEffect, useState } from 'react';
import { api } from './api/client';
import { ChatPanel } from './components/ChatPanel';
import { ConfigForm } from './components/ConfigForm';
import { ConnectionCard } from './components/ConnectionCard';
import { IngestionCard } from './components/IngestionCard';
import { Layout, type NavSection } from './components/Layout';
import { StatusAlert } from './components/StatusAlert';
import type {
  ChatMessage,
  ComponentHealth,
  ConfigRequest,
  ConfigResponse,
  ConfluenceTestResponse,
  HealthResponse,
  IngestionResponse,
  IngestionStatus,
  UiIngestRequest,
} from './types/api';

function configFromResponse(response: ConfigResponse): ConfigRequest {
  return {
    llmProvider: response.llmProvider,
    llmModel: response.llmModel,
    ollamaBaseUrl: response.ollamaBaseUrl,
    embeddingModel: response.embeddingModel,
    confluenceBaseUrl: response.confluenceBaseUrl,
    confluenceTarget: response.confluenceTarget,
    vectorStore: response.vectorStore,
    chromaHost: response.chromaHost,
    chromaPort: response.chromaPort,
    chromaCollectionName: response.chromaCollectionName,
    verifySsl: response.verifySsl,
    pat: '',
  };
}

export default function App() {
  const [section, setSection] = useState<NavSection>('config');
  const [config, setConfig] = useState<ConfigRequest>({});
  const [savedConfig, setSavedConfig] = useState<ConfigResponse | null>(null);
  const [connectionResult, setConnectionResult] = useState<ConfluenceTestResponse | null>(null);
  const [ingestionStatus, setIngestionStatus] = useState<IngestionStatus | null>(null);
  const [lastIngestion, setLastIngestion] = useState<IngestionResponse | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthLoading, setHealthLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [starting, setStarting] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [sending, setSending] = useState(false);

  const loadConfig = useCallback(async () => {
    try {
      const response = await api.getConfig();
      setSavedConfig(response);
      setConfig(configFromResponse(response));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load configuration');
    }
  }, []);

  const refreshStatus = useCallback(async () => {
    setRefreshing(true);
    try {
      const status = await api.getIngestionStatus(savedConfig?.parentPageId);
      setIngestionStatus(status);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load ingestion status');
    } finally {
      setRefreshing(false);
    }
  }, [savedConfig?.parentPageId]);

  const refreshHealth = useCallback(async () => {
    setHealthLoading(true);
    try {
      const response = await api.health();
      setHealth(response);
    } catch {
      const offline: ComponentHealth = { status: 'DOWN', message: 'Backend unreachable' };
      setHealth({
        application: offline,
        vectorStore: { status: 'DOWN', message: 'Unknown — backend offline' },
        model: { status: 'DOWN', message: 'Unknown — backend offline' },
      });
    } finally {
      setHealthLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfig();
    refreshHealth();
    const interval = setInterval(refreshHealth, 30000);
    return () => clearInterval(interval);
  }, [loadConfig, refreshHealth]);

  useEffect(() => {
    if (section !== 'ingest' || !savedConfig?.parentPageId) return;
    refreshStatus();
    const interval = setInterval(refreshStatus, 5000);
    return () => clearInterval(interval);
  }, [section, savedConfig?.parentPageId, refreshStatus]);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const response = await api.saveConfig(config);
      setSavedConfig(response);
      setConfig(configFromResponse(response));
      setSuccess('Configuration saved successfully.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save configuration');
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    setTesting(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.testConfluence(config);
      setConnectionResult(result);
      if (!result.connected) {
        setError(result.message);
      } else {
        setSuccess(result.message);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Connection test failed');
    } finally {
      setTesting(false);
    }
  };

  const handleStartIngestion = async (options: UiIngestRequest) => {
    setStarting(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.startIngestion(options);
      setLastIngestion(result);
      setSuccess(result.message);
      await refreshStatus();
      setSection('ingest');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start ingestion');
    } finally {
      setStarting(false);
    }
  };

  const handleSendChat = async () => {
    const trimmed = question.trim();
    if (!trimmed) return;

    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: trimmed,
    };
    const loadingMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      loading: true,
    };

    setMessages((prev) => [...prev, userMessage, loadingMessage]);
    setQuestion('');
    setSending(true);
    setError(null);

    try {
      const response = await api.chat(trimmed, savedConfig?.parentPageId);
      setMessages((prev) =>
        prev.map((message) =>
          message.id === loadingMessage.id
            ? {
                ...message,
                loading: false,
                content: response.answer ?? response.message,
                sources: response.sources,
              }
            : message,
        ),
      );
      if (response.status === 'ERROR') {
        setError(response.errorDetail ?? response.message);
      }
    } catch (err) {
      setMessages((prev) => prev.filter((message) => message.id !== loadingMessage.id));
      setError(err instanceof Error ? err.message : 'Chat request failed');
    } finally {
      setSending(false);
    }
  };

  return (
    <Layout active={section} onNavigate={setSection} health={health} healthLoading={healthLoading}>
      <div className="mb-6 space-y-3">
        {error && (
          <StatusAlert variant="error" title="Error" message={error} onDismiss={() => setError(null)} />
        )}
        {success && (
          <StatusAlert variant="success" title="Success" message={success} onDismiss={() => setSuccess(null)} />
        )}
      </div>

      {section === 'config' && (
        <div className="space-y-6">
          <ConfigForm
            config={config}
            savedConfig={savedConfig}
            onChange={(patch) => setConfig((prev) => ({ ...prev, ...patch }))}
            onSave={handleSave}
            onTest={handleTest}
            saving={saving}
            testing={testing}
          />
          <ConnectionCard result={connectionResult} />
        </div>
      )}

      {section === 'ingest' && (
        <IngestionCard
          status={ingestionStatus}
          lastResult={lastIngestion}
          onStart={handleStartIngestion}
          onRefresh={refreshStatus}
          starting={starting}
          refreshing={refreshing}
        />
      )}

      {section === 'chat' && (
        <ChatPanel
          messages={messages}
          question={question}
          onQuestionChange={setQuestion}
          onSend={handleSendChat}
          onClear={() => setMessages([])}
          sending={sending}
        />
      )}
    </Layout>
  );
}
