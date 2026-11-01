import { workoutKey } from '../utils/tenant-session';
import { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Dumbbell, History, Library, ListChecks, Timer } from 'lucide-react';
import ExerciseMedia from '../components/common/ExerciseMedia';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import SearchBar from '../components/common/SearchBar';
import StateBlock from '../components/common/StateBlock';
import DataTable from '../components/tables/DataTable';
import { AuthContext } from '../context/auth-context';
import { formatApiError, workoutService } from '../services/api';
import { getCompletionPercentage } from '../utils/calculations';
import { formatDate } from '../utils/formatters';

const tabs = [
  { path: '/workouts/library', label: 'Library', icon: Library },
  { path: '/workouts/plans', label: 'Plans', icon: Dumbbell },
  { path: '/workouts/tracker', label: 'Daily Tracker', icon: ListChecks },
  { path: '/workouts/history', label: 'History', icon: History },
];

const historyColumns = [
  { key: 'workoutDate', header: 'Workout Date', render: (row) => formatDate(row.workoutDate) },
  { key: 'duration', header: 'Duration' },
  { key: 'completedExercises', header: 'Completed Exercises' },
];

const Workouts = ({ section = 'library' }) => {
  const { user } = useContext(AuthContext);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const selectionStorageKey = workoutKey(user);
  const selectedPlanId = searchParams.get('plan') || localStorage.getItem(selectionStorageKey);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [tracker, setTracker] = useState([]);
  const [saving, setSaving] = useState(false);

  const loadWorkouts = useCallback(async () => {
    if (!user?.id) return;
    setError('');
    try {
      const [library, plans, history] = await Promise.all([
        workoutService.getLibrary(),
        workoutService.getPlans(),
        workoutService.getHistory(user.id),
      ]);
      localStorage.removeItem(`workoutPlan:${user.id}`);
      if(selectedPlanId && !plans.some(plan => String(plan.id) === selectedPlanId)) localStorage.removeItem(selectionStorageKey);
      const selectedPlan = plans.find((plan) => String(plan.id) === selectedPlanId);
      const exercises = library.flatMap((group) => group.exercises.map((exercise) => ({ ...exercise, category: group.category })));
      const dailyExercises = selectedPlan
        ? exercises.filter((exercise) => selectedPlan.exercises.includes(exercise.name))
        : exercises.slice(0, 5);
      setData({ library, plans, history, selectedPlan });
      setTracker(dailyExercises.map((exercise) => ({
        id: exercise.id, name: exercise.name, category: exercise.category, difficulty: exercise.difficulty,
        targetSets: exercise.sets || 0,
        targetReps: exercise.reps || '0', sets: exercise.sets || 0,
        reps: 0, complete: false,
      })));
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load workouts.'));
    }
  }, [user?.id, selectedPlanId, selectionStorageKey]);

  useEffect(() => {
    loadWorkouts();
  }, [loadWorkouts]);

  const filteredLibrary = useMemo(() => {
    if (!data) return [];
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) return data.library;

    return data.library
      .map((group) => ({
        ...group,
        exercises: group.exercises.filter((exercise) =>
          [group.category, exercise.name, exercise.difficulty].some((value) =>
            value.toLowerCase().includes(normalizedQuery),
          ),
        ),
      }))
      .filter((group) => group.exercises.length);
  }, [data, query]);

  const completion = getCompletionPercentage(tracker);

  const updateTracker = (id, field, value) => {
    setTracker((current) =>
      current.map((item) => (item.id === id ? { ...item, [field]: value } : item)),
    );
  };

  const saveTracker = async () => {
    const completed = tracker.filter((exercise) => exercise.complete);
    if (saving || !completed.length || !user?.id) return;
    setSaving(true);
    setError('');
    try {
      await workoutService.logWorkout(user.id, {
        userId: user.id,
        durationMinutes: completed.length * 12,
        notes: 'Daily workout tracker session',
        exercises: completed.map((exercise) => ({
          exercise: { id: exercise.id, name: exercise.name, category: exercise.category, difficulty: exercise.difficulty.toUpperCase() },
          setsCompleted: Number(exercise.sets) || 0,
          repsCompleted: String(exercise.reps || exercise.targetReps || ''),
        })),
      });
      await loadWorkouts();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to save workout tracker.'));
    } finally {
      setSaving(false);
    }
  };

  if (error) return <StateBlock message={error} onAction={loadWorkouts} />;
  if (!data) return <LoadingSpinner label="Loading workouts" />;

  return (
    <div className="space-y-6">
      <PageHeader
        actions={section === 'library' ? <SearchBar onChange={setQuery} placeholder="Search exercises" value={query} /> : null}
        description="Browse exercises, assign plans, complete daily workout sets, and review workout history."
        icon={Dumbbell}
        kicker="Workout"
        title="Workout Management"
      />
      <ModuleTabs tabs={tabs} />

      {section === 'library' ? (
        <div className="space-y-6">
          {filteredLibrary.map((group) => (
            <section className="space-y-3" key={group.category}>
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold text-ink">{group.category}</h2>
                <Badge>{group.exercises.length} exercises</Badge>
              </div>
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {group.exercises.map((exercise) => (
                  <Card className="overflow-hidden p-0" key={exercise.id}>
                    <ExerciseMedia name={exercise.name} />
                    <div className="p-4">
                      <div className="flex items-start justify-between gap-3">
                        <h3 className="text-base font-bold text-ink">{exercise.name}</h3>
                        <Badge tone={exercise.difficulty === 'Advanced' ? 'rose' : exercise.difficulty === 'Intermediate' ? 'amber' : 'emerald'}>
                          {exercise.difficulty}
                        </Badge>
                      </div>
                      <p className="mt-2 text-sm text-muted">{exercise.description}</p>
                      <div className="mt-4 grid grid-cols-2 gap-3">
                        <div className="rounded-lg bg-surface-alt p-3">
                          <p className="text-xs font-semibold uppercase text-muted">Sets</p>
                          <p className="font-bold text-ink">{exercise.sets}</p>
                        </div>
                        <div className="rounded-lg bg-surface-alt p-3">
                          <p className="text-xs font-semibold uppercase text-muted">Reps</p>
                          <p className="font-bold text-ink">{exercise.reps}</p>
                        </div>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            </section>
          ))}
        </div>
      ) : null}

      {section === 'plans' ? (
        <div className="grid gap-4 lg:grid-cols-3">
          {data.plans.map((plan) => (
            <Card className="flex min-h-80 flex-col" key={plan.level}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-xl font-bold text-ink">{plan.level}</h2>
                  <p className="mt-1 text-sm text-muted">{plan.duration}</p>
                </div>
                <Timer className="h-5 w-5 text-emerald-500" />
              </div>
              <div className="mt-6">
                <p className="text-sm font-bold uppercase text-muted">Weekly Schedule</p>
                <ul className="mt-3 space-y-2 text-sm text-muted">
                  {plan.schedule.map((item) => (
                    <li className="rounded-lg bg-surface-alt px-3 py-2" key={item}>
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="mt-6">
                <p className="text-sm font-bold uppercase text-muted">Exercises</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {plan.exercises.map((exercise) => (
                    <Badge key={exercise} tone="sky">{exercise}</Badge>
                  ))}
                </div>
              </div>
              <Button className="mt-auto" variant="outline" onClick={() => {
                localStorage.setItem(workoutKey(user), String(plan.id));
                navigate(`/workouts/tracker?plan=${plan.id}`);
              }}>Assign Plan</Button>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'plans' && !data.plans.length ? <StateBlock title="No workout plans available" message="Workout plans will appear here when they are added to the library." /> : null}

      {section === 'tracker' ? (
        <div className="grid gap-4 xl:grid-cols-[0.75fr_1.25fr]">
          <Card>
            <p className="text-sm font-semibold text-muted">{data.selectedPlan ? `${data.selectedPlan.level} Plan` : 'Completion Percentage'}</p>
            <p className="mt-2 text-5xl font-bold text-ink">{completion}%</p>
            <div className="mt-5 h-3 rounded-full bg-surface-alt">
              <div className="h-3 rounded-full bg-emerald-500 transition-all" style={{ width: `${completion}%` }} />
            </div>
            <p className="mt-4 text-sm text-muted">Track sets, reps, and completed exercises for today's plan.</p>
          </Card>

          <div className="space-y-3">
            {tracker.length ? tracker.map((exercise) => (
              <Card className="grid gap-4 sm:grid-cols-[1fr_auto]" key={exercise.id}>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-base font-bold text-ink">{exercise.name}</h2>
                    {exercise.complete ? <Badge tone="emerald">Complete</Badge> : <Badge tone="amber">Pending</Badge>}
                  </div>
                  <p className="mt-1 text-sm text-muted">
                    Target: {exercise.targetSets} sets x {exercise.targetReps} reps
                  </p>
                  <div className="mt-4 grid max-w-sm grid-cols-2 gap-3">
                    <label className="text-sm font-semibold text-muted">
                      Sets
                      <input
                        className="mt-1 h-10 w-full rounded-lg border border-line bg-surface px-3"
                        min="0"
                        onChange={(event) => updateTracker(exercise.id, 'sets', Number(event.target.value))}
                        type="number"
                        value={exercise.sets}
                      />
                    </label>
                    <label className="text-sm font-semibold text-muted">
                      Reps
                      <input
                        className="mt-1 h-10 w-full rounded-lg border border-line bg-surface px-3"
                        min="0"
                        onChange={(event) => updateTracker(exercise.id, 'reps', Number(event.target.value))}
                        type="number"
                        value={exercise.reps}
                      />
                    </label>
                  </div>
                </div>
                <Button
                  icon={CheckCircle2}
                  onClick={() => updateTracker(exercise.id, 'complete', !exercise.complete)}
                  variant={exercise.complete ? 'outline' : 'primary'}
                >
                  {exercise.complete ? 'Mark Pending' : 'Mark Complete'}
                </Button>
              </Card>
            )) : <StateBlock title="No exercises available" message="Workout exercises will appear here after they are added in the backend." />}
            <div className="flex justify-end">
              <Button disabled={!tracker.some((exercise) => exercise.complete)} isLoading={saving} onClick={saveTracker}>
                Save Workout
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {section === 'history' ? <DataTable columns={historyColumns} data={data.history} /> : null}
    </div>
  );
};

export default Workouts;
