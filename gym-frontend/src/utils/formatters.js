export const formatCurrency = (amount) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(amount);

export const formatDate = (date) =>
  new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(date));

export const percent = (value, total) => {
  if (!total) return 0;
  return Math.round((value / total) * 100);
};

export const cx = (...classes) => classes.filter(Boolean).join(' ');

