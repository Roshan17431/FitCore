import { NavLink } from 'react-router-dom';
import { cx } from '../../utils/formatters';

const ModuleTabs = ({ tabs }) => (
  <nav aria-label="Section navigation" className="flex gap-2 overflow-x-auto rounded-lg border border-line bg-surface p-1">
    {tabs.map((tab) => (
      <NavLink
        className={({ isActive }) =>
          cx(
            'flex min-h-11 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-semibold transition',
            isActive
              ? 'bg-brand-soft text-brand'
              : 'text-muted hover:bg-surface-alt hover:text-ink',
          )
        }
        key={tab.path}
        to={tab.path}
      >
        {tab.icon ? <tab.icon className="h-4 w-4" /> : null}
        <span>{tab.label}</span>
      </NavLink>
    ))}
  </nav>
);

export default ModuleTabs;
