import { NavLink } from 'react-router-dom';
import { cx } from '../../utils/formatters';

const ModuleTabs = ({ tabs }) => (
  <div className="flex gap-2 overflow-x-auto rounded-lg border border-slate-200 bg-white p-1 dark:border-slate-800 dark:bg-slate-900">
    {tabs.map((tab) => (
      <NavLink
        className={({ isActive }) =>
          cx(
            'flex h-9 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-semibold transition',
            isActive
              ? 'bg-slate-950 text-white dark:bg-slate-800 dark:text-white'
              : 'text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white',
          )
        }
        key={tab.path}
        to={tab.path}
      >
        {tab.icon ? <tab.icon className="h-4 w-4" /> : null}
        <span>{tab.label}</span>
      </NavLink>
    ))}
  </div>
);

export default ModuleTabs;
