import { useId } from 'react';
import { cx } from '../../utils/formatters';

const FormField = ({ className, error, icon: Icon, label, name, onChange, options, type = 'text', value, id, ...props }) => {
  const generatedId = useId();
  const inputId = id || generatedId;
  const errorId = `${inputId}-error`;
  const common = {
    ...props, id: inputId, name, onChange, value,
    'aria-invalid': error ? true : props['aria-invalid'],
    'aria-describedby': [props['aria-describedby'], error ? errorId : null].filter(Boolean).join(' ') || undefined,
    className: cx('min-h-11 w-full min-w-0 rounded-xl border border-control-line bg-surface px-3 py-2 text-sm text-ink transition focus:border-focus focus:outline-2 focus:outline-focus', Icon && 'pl-10', error && 'border-rose-600'),
  };
  return (
    <div className={cx('min-w-0', className)}>
      <label htmlFor={inputId} className="mb-2 block text-sm font-semibold text-ink">{label}</label>
      <div className="relative">
        {Icon ? <Icon aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" /> : null}
        {options ? <select {...common}>{options.map((option) => { const v = typeof option === 'string' ? option : option.value; return <option key={v} value={v}>{typeof option === 'string' ? option : option.label}</option>; })}</select> : <input {...common} type={type} />}
      </div>
      {error ? <p id={errorId} className="mt-1.5 text-sm text-rose-700 dark:text-rose-300">{error}</p> : null}
    </div>
  );
};
export default FormField;
