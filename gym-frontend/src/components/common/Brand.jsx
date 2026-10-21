import { Link } from 'react-router-dom';
import mark from '../../assets/fitcore-mark.svg';
const Brand = ({ to = '/dashboard', inverse = false }) => (
  <Link to={to} aria-label="FitCore home" className={`inline-flex items-center gap-3 rounded-lg ${inverse ? 'text-white' : 'text-ink'}`}>
    <img alt="" src={mark} width="40" height="40" />
    <span><span className="block text-xl font-bold tracking-tight">FitCore<span className={inverse ? 'text-emerald-200' : 'text-brand'}>.</span></span><span className={`block text-[10px] font-semibold uppercase tracking-[.18em] ${inverse ? 'text-emerald-200' : 'text-muted'}`}>Your fitness workspace</span></span>
  </Link>
);
export default Brand;
