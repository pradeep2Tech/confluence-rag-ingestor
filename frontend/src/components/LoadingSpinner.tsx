import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  label?: string;
  className?: string;
}

export function LoadingSpinner({ label, className = '' }: LoadingSpinnerProps) {
  return (
    <div className={`flex items-center gap-2 text-sm text-slate-400 ${className}`}>
      <Loader2 className="h-4 w-4 animate-spin text-brand-500" />
      {label && <span>{label}</span>}
    </div>
  );
}
