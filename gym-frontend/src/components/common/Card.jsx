import { cx } from '../../utils/formatters';

const Card = ({ as: Component = 'section', children, className }) => (
  <Component
    className={cx(
      'rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition duration-200 dark:border-slate-800 dark:bg-slate-900/90 sm:p-5',
      className,
    )}
  >
    {children}
  </Component>
);

export default Card;

