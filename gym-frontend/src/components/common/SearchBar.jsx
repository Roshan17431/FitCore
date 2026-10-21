import { Search } from 'lucide-react';

const SearchBar = ({ onChange, placeholder = 'Search', value }) => (
  <label className="relative block w-full sm:max-w-sm">
    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
    <input
      aria-label={placeholder}
      className="h-11 w-full rounded-lg border border-control-line bg-surface pl-9 pr-3 text-sm text-ink outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/20"
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      type="search"
      value={value}
    />
  </label>
);

export default SearchBar;

