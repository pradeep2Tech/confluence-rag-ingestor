export interface ConfigResponse {
  llmProvider: string;
  llmModel: string;
  ollamaBaseUrl: string;
  embeddingModel: string;
  confluenceBaseUrl: string;
  confluenceTarget: string;
  parentPageId: string;
  spaceKey: string | null;
  vectorStore: string;
  chromaHost: string;
  chromaPort: number;
  chromaCollectionName: string;
  verifySsl: boolean;
  maskedPat: string | null;
  patConfigured: boolean;
}

export interface ConfigRequest {
  llmProvider?: string;
  llmModel?: string;
  ollamaBaseUrl?: string;
  embeddingModel?: string;
  confluenceBaseUrl?: string;
  confluenceTarget?: string;
  vectorStore?: string;
  chromaHost?: string;
  chromaPort?: number;
  chromaCollectionName?: string;
  verifySsl?: boolean;
  pat?: string;
}

export interface ConfluenceTestResponse {
  connected: boolean;
  message: string;
  displayName: string | null;
  parentPageId: string | null;
  pageTitle: string | null;
  spaceKey: string | null;
}

export interface IngestionResponse {
  status: 'ACCEPTED' | 'ALREADY_RUNNING' | 'ERROR' | 'SUCCESS';
  mode: string;
  parentPageId: string;
  manifestPath: string;
  totalPages: number;
  ingestedCount: number;
  pendingCount: number;
  failedCount: number;
  message: string;
  errorDetail: string | null;
}

export interface IngestionStatus {
  parentPageId: string;
  manifestPath: string;
  manifestExists: boolean;
  activeJob: string | null;
  totalPages: number;
  ingestedCount: number;
  pendingCount: number;
  failedCount: number;
  message: string;
  crawlProgress: Record<string, unknown> | null;
  batchProgress: Record<string, unknown> | null;
}

export interface ChatSource {
  title: string | null;
  webUrl: string | null;
  headingPath: string | null;
  score: number | null;
  excerpt: string | null;
}

export interface ChatResponse {
  status: 'SUCCESS' | 'ERROR';
  question: string;
  parentPageId: string | null;
  answer: string | null;
  sources: ChatSource[];
  message: string;
  errorDetail: string | null;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: ChatSource[];
  loading?: boolean;
}
