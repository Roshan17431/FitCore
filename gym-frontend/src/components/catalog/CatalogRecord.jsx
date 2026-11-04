import Badge from '../common/Badge';
import Button from '../common/Button';
import Card from '../common/Card';

export default function CatalogRecord({ kind, row, busy, onEdit, onArchive, onPublish }) {
  const name = row.name || row.level || row.category;
  const isArchived = row.archived || row.status === 'ARCHIVED';
  return <Card className="flex min-w-0 flex-col">
    <div className="flex items-start justify-between gap-3">
      <h2 className="min-w-0 break-words text-lg font-bold">{name}</h2>
      <Badge tone={isArchived ? 'amber' : 'emerald'}>{isArchived ? 'Archived' : row.status || 'Active'}</Badge>
    </div>
    {kind === 'exercises' ? <>
      <p className="mt-2 text-sm text-muted">{row.category} · {row.difficulty} · {row.recommendedSets ?? '—'} sets · {row.recommendedReps || '—'} reps</p>
      <p className="mt-3 whitespace-pre-wrap break-words text-sm">{row.description}</p>
    </> : kind === 'workouts' ? <>
      <p className="mt-2 text-sm text-muted">{row.duration}</p>
      <p className="mt-3 break-words text-sm">Exercises: {row.exercises?.join(', ') || 'No exercises selected'}</p>
      {row.unresolvedExercises?.length ? <p className="mt-2 break-words text-sm text-amber-800 dark:text-amber-200">Unresolved legacy names: {row.unresolvedExercises.join(', ')}</p> : null}
      <ul className="mt-2 space-y-1 text-sm text-muted">{row.schedule?.map((entry, i) => <li className="break-words" key={i}>{entry}</li>)}</ul>
    </> : <>
      <p className="mt-2 text-sm text-muted">Gym-provided estimates: {row.calories} kcal · {row.protein}g protein · {row.carbs}g carbs · {row.fats}g fats</p>
      <ul className="mt-3 space-y-1 text-sm">{row.meals?.map((meal, i) => <li className="break-words" key={i}>{meal}</li>)}</ul>
    </>}
    <div className="mt-auto flex flex-wrap gap-2 pt-5">
      <Button variant="outline" disabled={busy} onClick={onEdit} aria-label={`Edit ${name}`}>Edit</Button>
      {kind === 'workouts' && row.status === 'DRAFT' ? <Button disabled={busy} onClick={onPublish} aria-label={`Publish ${name}`}>Publish</Button> : null}
      {!isArchived ? <Button variant="ghost" disabled={busy} onClick={onArchive} aria-label={`Archive ${name}`}>Archive</Button> : null}
    </div>
  </Card>;
}
