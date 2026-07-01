import { Loader2 } from 'lucide-react';

const LoadingSpinner = ({ label = 'Loading' }) => (
  <div className="flex min-h-64 flex-col items-center justify-center gap-3 text-slate-500 dark:text-slate-400">
    <Loader2 className="h-8 w-8 animate-spin text-emerald-500" />
    <span className="text-sm font-medium">{label}</span>
  </div>
);

export default LoadingSpinner;

