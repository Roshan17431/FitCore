import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Apple, Dumbbell, ListChecks, Plus } from 'lucide-react';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import Modal from '../components/common/Modal';
import PageHeader from '../components/common/PageHeader';
import StateBlock from '../components/common/StateBlock';
import FormField from '../components/forms/FormField';
import CatalogRecord from '../components/catalog/CatalogRecord';
import CatalogEditor from '../components/catalog/CatalogEditor';
import { catalogLabels } from '../utils/catalog';
import { catalogService, formatApiError } from '../services/api';

const sections = [{ key: 'exercises', label: 'Exercises', icon: Dumbbell }, { key: 'workouts', label: 'Workouts', icon: ListChecks }, { key: 'nutrition', label: 'Nutrition', icon: Apple }];
const nameOf = row => row.name || row.level || row.category;
const archived = row => row.archived || row.status === 'ARCHIVED';
export default function Catalogs() {
  const [data, setData] = useState(null), [tab, setTab] = useState('exercises');
  const [query, setQuery] = useState(''), [category, setCategory] = useState(''), [visibility, setVisibility] = useState('all');
  const [editor, setEditor] = useState(null), [confirmation, setConfirmation] = useState(null);
  const [error, setError] = useState(''), [actionError, setActionError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const load = useCallback(async () => {
    setError('');
    try {
      const [exercises, workouts, nutrition] = await Promise.all(sections.map(section => catalogService[section.key].list()));
      setData({ exercises, workouts, nutrition });
    } catch (e) { setError(formatApiError(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);
  const upsert = (kind, row) => setData(current => ({ ...current, [kind]: current[kind].some(item => item.id === row.id) ? current[kind].map(item => item.id === row.id ? row : item) : [...current[kind], row] }));
  const action = async (kind, row, operation) => {
    if (lock.current) return;
    lock.current = true; setBusy(true); setActionError(''); setError(''); setNotice('');
    try { const result = await catalogService[kind][operation](row.id); upsert(kind, result); setConfirmation(null); setNotice(`${nameOf(row)} ${operation === 'publish' ? 'published' : 'archived'}.`); }
    catch (e) { if (operation === 'archive') setActionError(formatApiError(e)); else setError(formatApiError(e)); }
    finally { lock.current = false; setBusy(false); }
  };
  const rows = useMemo(() => (data?.[tab] || []).filter(row => {
    const matchesQuery = [row.name, row.level, row.category, row.description].some(value => String(value || '').toLowerCase().includes(query.trim().toLowerCase()));
    const matchesStatus = visibility === 'all' || (visibility === 'archived' ? archived(row) : visibility === 'active' ? !archived(row) : row.status === visibility);
    return matchesQuery && matchesStatus && (!category || row.category === category);
  }), [data, tab, query, category, visibility]);
  if (!data) return error ? <StateBlock message={error} onAction={load} /> : <LoadingSpinner label="Loading gym catalogs" />;
  const categories = [...new Set(data[tab].map(row => row.category).filter(Boolean))].sort();
  return <div className="space-y-6"><PageHeader title="Gym Catalogs" kicker="Gym management" description="Build the exercise, workout and nutrition libraries for your gym." actions={<Button icon={Plus} onClick={() => { setNotice(''); setEditor({ kind: tab, original: null }); }}>Create {catalogLabels[tab]}</Button>} />
    <div aria-label="Catalog sections" className="flex gap-2 overflow-x-auto rounded-2xl border border-line bg-surface p-2">{sections.map(section => <Button key={section.key} aria-pressed={tab === section.key} variant={tab === section.key ? 'primary' : 'ghost'} icon={section.icon} onClick={() => { setTab(section.key); setQuery(''); setCategory(''); setVisibility('all'); setError(''); }}>{section.label}</Button>)}</div>
    {error ? <p role="alert" className="rounded-xl border border-line bg-surface p-4 text-sm">{error}</p> : null}{notice ? <p role="status" className="rounded-xl bg-brand-soft p-4 text-sm text-brand">{notice}</p> : null}
    <Card className="grid items-end gap-4 sm:grid-cols-2 xl:grid-cols-3"><FormField label="Search catalog" type="search" placeholder="Search by name or category" value={query} onChange={e => setQuery(e.target.value)} />{tab !== 'workouts' ? <FormField label="Category filter" options={[{ value: '', label: 'All categories' }, ...categories]} value={category} onChange={e => setCategory(e.target.value)} /> : null}<FormField label="Status filter" options={[{ value: 'all', label: 'All records' }, ...(tab === 'workouts' ? [{ value: 'DRAFT', label: 'Draft' }, { value: 'PUBLISHED', label: 'Published' }] : [{ value: 'active', label: 'Active' }]), { value: 'archived', label: 'Archived' }]} value={visibility} onChange={e => setVisibility(e.target.value)} /></Card>
    <p className="text-sm text-muted">{tab === 'workouts' ? 'New workouts start as drafts. Publish a plan with active local exercises to show it in the workout library.' : tab === 'nutrition' ? 'Nutrition figures are gym-provided estimates. Archived plans stay in your management records.' : 'Archived exercises stay in history. Published workout references must be removed before an exercise can be archived.'}</p>
    {rows.length ? <div className="grid gap-4 xl:grid-cols-2">{rows.map(row => <CatalogRecord key={row.id} kind={tab} row={row} busy={busy} onEdit={() => setEditor({ kind: tab, original: row })} onPublish={() => action(tab, row, 'publish')} onArchive={() => { setActionError(''); setConfirmation({ kind: tab, row }); }} />)}</div> : <StateBlock title={data[tab].length ? 'No matching catalog records' : `No ${tab === 'nutrition' ? 'nutrition plans' : tab} yet`} message={data[tab].length ? 'Try another search or filter.' : `Create your first ${catalogLabels[tab]} to build this gym’s library.`} />}
    {editor ? <CatalogEditor key={`${editor.kind}-${editor.original?.id || 'new'}`} {...editor} exercises={data.exercises} service={catalogService[editor.kind]} onClose={() => setEditor(null)} onSaved={row => { upsert(editor.kind, row); setNotice(`${nameOf(row)} saved.`); setEditor(null); }} /> : null}
    <Modal isOpen={Boolean(confirmation)} onClose={() => { if (!busy) setConfirmation(null); }} title={`Archive ${confirmation ? catalogLabels[confirmation.kind] : 'record'}?`}><p className="break-words text-sm">{confirmation ? nameOf(confirmation.row) : ''} will be hidden from the active library. Saved history and management records remain available.</p>{actionError ? <p role="alert" className="mt-4 text-sm text-rose-800 dark:text-rose-200">{actionError}</p> : null}<div className="mt-5 flex flex-wrap gap-3"><Button variant="danger" isLoading={busy} onClick={() => action(confirmation.kind, confirmation.row, 'archive')}>Confirm archive</Button><Button variant="outline" disabled={busy} onClick={() => setConfirmation(null)}>Cancel</Button></div></Modal>
  </div>;
}
