import Card from '../common/Card';

const ChartCard = ({ children, subtitle, title }) => (
  <Card className="min-h-80">
    <div className="mb-4">
      <h2 className="text-base font-bold text-ink">{title}</h2>
      {subtitle ? <p className="mt-1 text-sm text-muted">{subtitle}</p> : null}
    </div>
    <div role="group" aria-label={title} className="h-64 min-w-0 text-muted">{children}</div>
  </Card>
);

export default ChartCard;
