import { CheckCircle2 } from 'lucide-react';

const Toast = ({ message }) => {
  if (!message) return null;

  return (
    <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 flex max-w-[calc(100vw-2rem)] sm:max-w-sm items-center gap-3 rounded-lg border border-emerald-200 bg-surface px-4 py-3 text-sm font-semibold text-ink shadow-lg dark:border-emerald-500/30">
      <CheckCircle2 className="h-5 w-5 text-emerald-500" />
      <span>{message}</span>
    </div>
  );
};

export default Toast;

