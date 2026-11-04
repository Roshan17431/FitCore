import { useState } from 'react';
import { approvedExerciseImage, exerciseVisualNote } from '../../utils/catalog';
import { Dumbbell } from 'lucide-react';

const ExerciseMedia = ({ name, image, imageApproved }) => {
  const [failedImage, setFailedImage] = useState('');
  const source = approvedExerciseImage({ imageUrl: image, imageApproved });
  if (source && failedImage !== source) return <img data-exercise-visual className="aspect-[16/9] w-full border-b border-line object-cover" src={source} alt={`${name} visual guide`} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedImage(source)} />;
  return (
  <div data-exercise-visual className="flex aspect-[16/9] w-full flex-col items-center justify-center gap-2 border-b border-line bg-surface-alt px-5 text-center">
    <Dumbbell aria-hidden="true" className="mb-1 h-9 w-9 text-brand" strokeWidth={1.5} />
    <p className="text-xs font-semibold uppercase tracking-widest text-muted">Visual guide unavailable</p>
    <p className="text-sm font-semibold text-ink">{exerciseVisualNote(name)}</p>
    <p className="max-w-64 text-xs text-muted">Ask your trainer for a technique demonstration.</p>
  </div>
);
};
export default ExerciseMedia;
