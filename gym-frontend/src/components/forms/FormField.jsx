import { cx } from '../../utils/formatters';

const FormField = ({
  className,
  error,
  icon: Icon,
  label,
  name,
  onChange,
  options,
  type = 'text',
  value,
  ...props
}) => (
  <label className={cx('block', className)}>
    <span className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{label}</span>
    <span className="relative block">
      {Icon ? (
        <Icon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
      ) : null}
      {options ? (
        <select
          className={cx(
            'h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/20 dark:border-slate-800 dark:bg-slate-900 dark:text-white',
            Icon && 'pl-9',
          )}
          name={name}
          onChange={onChange}
          value={value}
          {...props}
        >
          {options.map((option) => {
            const optionValue = typeof option === 'string' ? option : option.value;
            const optionLabel = typeof option === 'string' ? option : option.label;

            return (
            <option key={optionValue} value={optionValue}>
              {optionLabel}
            </option>
            );
          })}
        </select>
      ) : (
        <input
          className={cx(
            'h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/20 dark:border-slate-800 dark:bg-slate-900 dark:text-white',
            Icon && 'pl-9',
          )}
          name={name}
          onChange={onChange}
          type={type}
          value={value}
          {...props}
        />
      )}
    </span>
    {error ? <span className="mt-1 block text-sm text-rose-600 dark:text-rose-400">{error}</span> : null}
  </label>
);

export default FormField;
