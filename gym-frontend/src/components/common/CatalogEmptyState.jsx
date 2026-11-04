import { useContext } from 'react';
import { Link } from 'react-router-dom';
import Card from './Card';
import { AuthContext } from '../../context/auth-context';
import { canManage } from '../../utils/tenant-session';

export default function CatalogEmptyState({ title, membership = false }) {
  const { user } = useContext(AuthContext);
  return <Card className="flex min-h-56 flex-col items-center justify-center text-center"><h2 className="text-lg font-bold">{title}</h2><p className="mt-2 max-w-xl text-sm text-muted">{canManage(user) ? membership ? 'Create the first membership plan for your gym so members can choose their access package.' : 'Build your gym’s catalog to make exercises and published plans available here.' : 'Your gym has not added these library items yet. Ask your gym team about upcoming options.'}</p>{canManage(user) ? <Link className="mt-5 inline-flex min-h-11 items-center justify-center rounded-xl border border-control-line px-4 text-sm font-semibold text-brand" to={membership ? '/admin/memberships' : '/gym/catalogs'}>{membership ? 'Manage membership plans' : 'Manage gym catalogs'}</Link> : null}</Card>;
}
