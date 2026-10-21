import { cx } from '../../utils/formatters';
const Card = ({ as: Component = 'section', children, className = '', ...props }) => (
  <Component className={cx('min-w-0 rounded-2xl border border-line', !/\bbg-/.test(className) && 'bg-surface', !/\b(?:p|px|py)-/.test(className) && 'p-4 sm:p-5', !/\bshadow-/.test(className) && 'shadow-panel', className)} {...props}>{children}</Component>
);
export default Card;
