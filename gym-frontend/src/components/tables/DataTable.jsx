import Badge from '../common/Badge';
import Card from '../common/Card';

const resolveTone = (value = '') => {
  const normalized = String(value).toLowerCase();
  if (['active', 'completed', 'scheduled'].includes(normalized)) return 'emerald';
  if (['paused', 'pending'].includes(normalized)) return 'amber';
  if (['expired', 'cancelled', 'inactive'].includes(normalized)) return 'rose';
  return 'slate';
};

const DataTable = ({ columns, data, emptyText = 'No records found', label = 'Records' }) => (
  <Card className="overflow-hidden p-0">
    <div className="max-w-full overflow-x-auto" role="region" aria-label={`${label} table; scroll horizontally for more columns`} tabIndex={0}>
      <table className="min-w-full divide-y divide-line text-left text-sm">
        <caption className="sr-only">{label}</caption>
        <thead className="bg-surface-alt text-xs font-bold uppercase text-muted">
          <tr>
            {columns.map((column) => (
              <th scope="col" className="whitespace-nowrap px-4 py-3" key={column.key}>
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {data.length ? (
            data.map((row) => (
              <tr className="hover:bg-surface-alt dark:hover:bg-surface-alt/60" key={row.id || JSON.stringify(row)}>
                {columns.map((column) => {
                  const value = column.render ? column.render(row) : row[column.key];
                  return (
                    <td className="whitespace-nowrap px-4 py-3 text-ink" key={column.key}>
                      {column.badge ? <Badge tone={resolveTone(value)}>{value}</Badge> : value}
                    </td>
                  );
                })}
              </tr>
            ))
          ) : (
            <tr>
              <td className="px-4 py-8 text-center text-muted" colSpan={columns.length}>
                {emptyText}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  </Card>
);

export default DataTable;

