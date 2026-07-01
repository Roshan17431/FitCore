import Badge from '../common/Badge';
import Card from '../common/Card';

const resolveTone = (value = '') => {
  const normalized = String(value).toLowerCase();
  if (['active', 'completed', 'scheduled'].includes(normalized)) return 'emerald';
  if (['paused', 'pending'].includes(normalized)) return 'amber';
  if (['expired', 'cancelled', 'inactive'].includes(normalized)) return 'rose';
  return 'slate';
};

const DataTable = ({ columns, data, emptyText = 'No records found' }) => (
  <Card className="overflow-hidden p-0">
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-left text-sm dark:divide-slate-800">
        <thead className="bg-slate-50 text-xs font-bold uppercase text-slate-500 dark:bg-slate-900 dark:text-slate-400">
          <tr>
            {columns.map((column) => (
              <th className="whitespace-nowrap px-4 py-3" key={column.key}>
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
          {data.length ? (
            data.map((row) => (
              <tr className="hover:bg-slate-50 dark:hover:bg-slate-800/60" key={row.id || JSON.stringify(row)}>
                {columns.map((column) => {
                  const value = column.render ? column.render(row) : row[column.key];
                  return (
                    <td className="whitespace-nowrap px-4 py-3 text-slate-700 dark:text-slate-300" key={column.key}>
                      {column.badge ? <Badge tone={resolveTone(value)}>{value}</Badge> : value}
                    </td>
                  );
                })}
              </tr>
            ))
          ) : (
            <tr>
              <td className="px-4 py-8 text-center text-slate-500 dark:text-slate-400" colSpan={columns.length}>
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

