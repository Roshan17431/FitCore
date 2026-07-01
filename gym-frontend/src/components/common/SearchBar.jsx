import { Search } from 'lucide-react';

const SearchBar = ({ onChange, placeholder = 'Search', value }) => (
  <label className="relative block w-full sm:max-w-sm">
    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
    <input
      className="h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-3 text-sm text-slate-900 outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/20 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      type="search"
      value={value}
    />
  </label>
);

export default SearchBar;

