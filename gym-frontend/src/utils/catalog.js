export const catalogLabels = { exercises: 'exercise', workouts: 'workout', nutrition: 'nutrition plan' };
const text = (value, label, max, required = true) => {
  const result = String(value ?? '').trim();
  if ((required && !result) || result.length > max) throw new Error(`${label} must contain ${required ? '1' : '0'}–${max} characters.`);
  return result;
};
const lines = (value, label) => {
  const entries = String(value ?? '').trim() ? String(value).trim().split('\n').map(item => text(item, label, 255)) : [];
  if (entries.length > 31) throw new Error(`${label} allows at most 31 entries.`);
  return entries;
};
const number = (value, label, max, min = 0) => {
  const result = Number(value);
  if (String(value ?? '').trim() === '' || !Number.isFinite(result) || result < min || result > max) throw new Error(`${label} must be between ${min} and ${max}.`);
  if (!Number.isInteger(result)) throw new Error(`${label} must be a whole number.`);
  return result;
};
export const workoutPayload = (form, original = null, selectionsChanged = false) => {
  const status = form.status || 'DRAFT';
  if (!['DRAFT', 'PUBLISHED', 'ARCHIVED'].includes(status)) throw new Error('Choose a valid workout status.');
  const payload = { level: text(form.level, 'Level', 100), duration: text(form.duration, 'Duration', 100), schedule: lines(form.schedule, 'Schedule'), status };
  if (!original || selectionsChanged) {
    payload.exerciseIds = (form.exerciseIds || []).map(Number);
    if (payload.exerciseIds.length > 100 || new Set(payload.exerciseIds).size !== payload.exerciseIds.length || payload.exerciseIds.some(id => !Number.isSafeInteger(id) || id < 1)) throw new Error('Choose up to 100 distinct exercises.');
    if (!payload.exerciseIds.length && status !== 'DRAFT') throw new Error('An empty exercise selection is allowed only for a draft.');
  }
  return payload;
};
export const exercisePayload = form => {
  if (!['BEGINNER', 'INTERMEDIATE', 'ADVANCED'].includes(form.difficulty)) throw new Error('Choose a valid difficulty.');
  const recommendedSets = form.recommendedSets === '' || form.recommendedSets == null ? null : number(form.recommendedSets, 'Sets', 100, 1);
  return { name: text(form.name, 'Name', 100), category: text(form.category, 'Category', 100), difficulty: form.difficulty, recommendedSets, recommendedReps: text(form.recommendedReps, 'Reps', 100, false), description: text(form.description, 'Description', 4000, false), imageUrl: text(form.imageUrl, 'Image URL', 255, false) };
};
export const nutritionPayload = form => ({ category: text(form.category, 'Category', 100), calories: number(form.calories, 'Calories', 20000), protein: number(form.protein, 'Protein', 2000), carbs: number(form.carbs, 'Carbs', 2000), fats: number(form.fats, 'Fats', 2000), meals: lines(form.meals, 'Meals') });
export const approvedExerciseImage = exercise => exercise.imageApproved === true && typeof exercise.imageUrl === 'string' && (/^\/images\/[^\s\\?#]+$/.test(exercise.imageUrl) || /^https:\/\//i.test(exercise.imageUrl)) ? exercise.imageUrl : '';
const exerciseNotes = {
  'incline dumbbell press': 'Dumbbells · incline bench',
  'lat pulldown': 'Cable machine · overhead pull',
  'single arm row': 'Dumbbell · unilateral row',
  'barbell bench press': 'Barbell · flat bench',
};
export const exerciseVisualNote = name => {
  const key = name?.trim().toLowerCase();
  return Object.hasOwn(exerciseNotes, key) ? exerciseNotes[key] : name;
};
