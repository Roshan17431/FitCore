import { useState } from 'react';
import { cx } from '../../utils/formatters';

const isStockPhoto = (source = '') => /(?:^|\/\/|\.)(?:images\.)?(?:unsplash\.com|pexels\.com|pravatar\.cc)(?:\/|$)/i.test(source);
const Avatar = ({ name = 'Trainer', src, className }) => {
  const [failedSource, setFailedSource] = useState(null);
  const showPhoto = src && src !== failedSource && !isStockPhoto(src);
  const initials = name.trim().split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'T';
  return (
    <div className={cx('relative flex shrink-0 items-center justify-center overflow-hidden rounded-xl bg-brand-soft text-brand', className)}>
      {showPhoto ? <img alt={name} className="h-full w-full object-cover" decoding="async" loading="lazy" onError={() => setFailedSource(src)} src={src} /> : <span role="img" aria-label={`${name} — initials avatar`} className="flex h-full w-full items-center justify-center text-3xl font-semibold tracking-tight">{initials}</span>}
    </div>
  );
};
export default Avatar;
