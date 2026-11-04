import { useRef, useState } from 'react';
import Button from '../common/Button';
import Modal from '../common/Modal';
import { exercisePayload, workoutPayload, nutritionPayload, catalogLabels } from '../../utils/catalog';
import { formatApiError } from '../../services/api';
import { ExerciseFields, WorkoutFields, NutritionFields } from './CatalogFields';

const initialForm = (kind, row) => {
  if (kind === 'exercises') return { name: row?.name || '', category: row?.category || '', difficulty: row?.difficulty || 'BEGINNER', recommendedSets: row?.recommendedSets ?? '', recommendedReps: row?.recommendedReps || '', description: row?.description || '', imageUrl: row?.imageUrl || '' };
  if (kind === 'workouts') return { level: row?.level || '', duration: row?.duration || '', schedule: (row?.schedule || []).join('\n'), exerciseIds: row?.exerciseIds || [], status: row?.status || 'DRAFT' };
  return { category: row?.category || '', calories: row?.calories ?? '', protein: row?.protein ?? '', carbs: row?.carbs ?? '', fats: row?.fats ?? '', meals: (row?.meals || []).join('\n') };
};
export default function CatalogEditor({ kind, original, exercises, service, onSaved, onClose }) {
  const [form, setForm] = useState(() => initialForm(kind, original));
  const [selectionsChanged, setSelectionsChanged] = useState(false);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const change = field => event => setForm(current => ({ ...current, [field]: event.target.value }));
  const close = () => { if (!lock.current) onClose(); };
  const submit = async event => {
    event.preventDefault();
    if (lock.current) return;
    setError('');
    try {
      const payload = kind === 'exercises' ? exercisePayload(form) : kind === 'workouts' ? workoutPayload(form, original, selectionsChanged) : nutritionPayload(form);
      lock.current = true; setBusy(true);
      const result = original ? await service.update(original.id, payload) : await service.create(payload);
      onSaved(result);
    } catch (e) { setError(formatApiError(e)); }
    finally { lock.current = false; setBusy(false); }
  };
  return <Modal isOpen onClose={close} title={`${original ? 'Edit' : 'Create'} ${catalogLabels[kind]}`}><form className="space-y-4" onSubmit={submit}>
    {error ? <p role="alert" className="rounded-xl border border-rose-500/40 bg-surface-alt p-3 text-sm text-rose-800 dark:text-rose-200">{error}</p> : null}
    {kind === 'exercises' ? <ExerciseFields form={form} change={change} /> : kind === 'workouts' ? <WorkoutFields form={form} change={change} original={original} exercises={exercises} selectionsChanged={selectionsChanged} setSelectionsChanged={setSelectionsChanged} setForm={setForm} /> : <NutritionFields form={form} change={change} />}
    <div className="flex flex-wrap gap-3 border-t border-line pt-4"><Button type="submit" isLoading={busy}>Save {catalogLabels[kind]}</Button><Button variant="outline" disabled={busy} onClick={close}>Cancel</Button></div>
  </form></Modal>;
}
