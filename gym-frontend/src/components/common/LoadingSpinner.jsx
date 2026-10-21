import { Loader2 } from 'lucide-react';

const LoadingSpinner = ({ label = 'Loading' }) => (
  <div role="status" aria-live="polite" className="flex min-h-64 flex-col items-center justify-center gap-3 text-muted">
    <Loader2 className="h-8 w-8 animate-spin text-emerald-500" />
    <span className="text-sm font-medium">{label}</span>
  </div>
);

export default LoadingSpinner;

