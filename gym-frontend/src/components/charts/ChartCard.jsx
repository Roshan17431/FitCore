import Card from '../common/Card';

const ChartCard = ({ children, subtitle, title }) => (
  <Card className="min-h-80">
    <div className="mb-4">
      <h2 className="text-base font-bold text-slate-950 dark:text-white">{title}</h2>
      {subtitle ? <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p> : null}
    </div>
    <div className="h-64">{children}</div>
  </Card>
);

export default ChartCard;
