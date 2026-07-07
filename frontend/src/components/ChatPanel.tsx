import { ExternalLink, Send, Trash2 } from 'lucide-react';
import type { ChatMessage } from '../types/api';
import { LoadingSpinner } from './LoadingSpinner';

interface ChatPanelProps {
  messages: ChatMessage[];
  question: string;
  onQuestionChange: (value: string) => void;
  onSend: () => void;
  onClear: () => void;
  sending: boolean;
}

export function ChatPanel({
  messages,
  question,
  onQuestionChange,
  onSend,
  onClear,
  sending,
}: ChatPanelProps) {
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSend();
  };

  return (
    <div className="flex h-[calc(100vh-8rem)] flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Ask AI</h2>
          <p className="mt-1 text-slate-400">
            Ask questions about your ingested Confluence content with source citations.
          </p>
        </div>
        <button type="button" className="btn-ghost" onClick={onClear} disabled={messages.length === 0}>
          <Trash2 className="h-4 w-4" />
          Clear chat
        </button>
      </div>

      <div className="card flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex-1 space-y-4 overflow-y-auto p-6">
          {messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center text-center text-slate-500">
              <p className="text-lg font-medium text-slate-400">No messages yet</p>
              <p className="mt-1 max-w-md text-sm">
                Ingest content first, then ask a question like &quot;How do I configure SSL verification?&quot;
              </p>
            </div>
          ) : (
            messages.map((message) => (
              <div
                key={message.id}
                className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                    message.role === 'user'
                      ? 'bg-brand-600 text-white'
                      : 'border border-slate-800 bg-slate-950/70 text-slate-100'
                  }`}
                >
                  {message.loading ? (
                    <LoadingSpinner label="Thinking…" />
                  ) : (
                    <p className="whitespace-pre-wrap">{message.content}</p>
                  )}
                  {message.sources && message.sources.length > 0 && (
                    <div className="mt-4 space-y-2 border-t border-slate-800 pt-3">
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Sources</p>
                      {message.sources.map((source, index) => (
                        <div key={index} className="rounded-lg bg-slate-900/80 p-3 text-xs">
                          <div className="flex items-start justify-between gap-2">
                            <p className="font-medium text-slate-200">
                              {source.title ?? 'Untitled'}
                              {source.headingPath ? ` › ${source.headingPath}` : ''}
                            </p>
                            {source.webUrl && (
                              <a
                                href={source.webUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="shrink-0 text-brand-400 hover:text-brand-300"
                              >
                                <ExternalLink className="h-3.5 w-3.5" />
                              </a>
                            )}
                          </div>
                          {source.score != null && (
                            <p className="mt-1 text-slate-500">Score: {source.score.toFixed(3)}</p>
                          )}
                          {source.excerpt && (
                            <p className="mt-2 text-slate-400 line-clamp-3">{source.excerpt}</p>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        <form onSubmit={handleSubmit} className="border-t border-slate-800 p-4">
          <div className="flex gap-3">
            <input
              className="input flex-1"
              placeholder="Ask a question about your Confluence content…"
              value={question}
              onChange={(e) => onQuestionChange(e.target.value)}
              disabled={sending}
            />
            <button type="submit" className="btn-primary shrink-0" disabled={sending || !question.trim()}>
              {sending ? <LoadingSpinner /> : <Send className="h-4 w-4" />}
              Ask
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
