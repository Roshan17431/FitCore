import { AlertCircle, RefreshCw } from 'lucide-react';
import Button from './Button';
import Card from './Card';

const StateBlock = ({ actionLabel = 'Retry', message, onAction, title = 'Unable to load data' }) => (
  <Card className="flex min-h-56 flex-col items-center justify-center text-center">
    <AlertCircle className="h-8 w-8 text-amber-500" />
    <h2 className="mt-3 text-lg font-bold text-ink">{title}</h2>
    <p className="mt-2 max-w-xl text-sm text-muted">{message}</p>
    {onAction ? (
      <Button className="mt-5" icon={RefreshCw} onClick={onAction} variant="outline">
        {actionLabel}
      </Button>
    ) : null}
  </Card>
);

export default StateBlock;
