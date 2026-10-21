import { Dumbbell } from 'lucide-react';

const exerciseNotes = {
  'incline dumbbell press': 'Dumbbells · incline bench',
  'lat pulldown': 'Cable machine · overhead pull',
  'single arm row': 'Dumbbell · unilateral row',
  'barbell bench press': 'Barbell · flat bench',
};
const ExerciseMedia = ({ name }) => (
  <div data-exercise-visual className="flex aspect-[16/9] w-full flex-col items-center justify-center gap-2 border-b border-line bg-surface-alt px-5 text-center">
    <Dumbbell aria-hidden="true" className="mb-1 h-9 w-9 text-brand" strokeWidth={1.5} />
    <p className="text-xs font-semibold uppercase tracking-widest text-muted">Visual guide unavailable</p>
    <p className="text-sm font-semibold text-ink">{exerciseNotes[name?.trim().toLowerCase()] || name}</p>
    <p className="max-w-64 text-xs text-muted">Ask your trainer for a technique demonstration.</p>
  </div>
);
export default ExerciseMedia;
