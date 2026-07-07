import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

type AlertVariant = 'success' | 'error' | 'info';

interface StatusAlertProps {
  variant: AlertVariant;
  title: string;
  message?: string;
  onDismiss?: () => void;
}

const styles: Record<AlertVariant, { container: string; icon: typeof CheckCircle2 }> = {
  success: {
    container: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-100',
    icon: CheckCircle2,
  },
  error: {
    container: 'border-rose-500/30 bg-rose-500/10 text-rose-100',
    icon: AlertCircle,
  },
  info: {
    container: 'border-sky-500/30 bg-sky-500/10 text-sky-100',
    icon: Info,
  },
};

export function StatusAlert({ variant, title, message, onDismiss }: StatusAlertProps) {
  const Icon = styles[variant].icon;
  return (
    <div className={`flex gap-3 rounded-xl border p-4 ${styles[variant].container}`}>
      <Icon className="mt-0.5 h-5 w-5 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        {message && <p className="mt-1 text-sm opacity-90">{message}</p>}
      </div>
      {onDismiss && (
        <button type="button" onClick={onDismiss} className="shrink-0 opacity-70 hover:opacity-100">
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
