import { useCallback, useContext, useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Activity, Calculator, Plus, Ruler, Scale, TrendingUp } from 'lucide-react';
import ChartCard from '../components/charts/ChartCard';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import StateBlock from '../components/common/StateBlock';
import FormField from '../components/forms/FormField';
import DataTable from '../components/tables/DataTable';
import { AuthContext } from '../context/auth-context';
import { formatApiError, progressService } from '../services/api';
import { calculateBmi } from '../utils/calculations';

const tabs = [
  { path: '/progress/weight', label: 'Weight', icon: Scale },
  { path: '/progress/measurements', label: 'Measurements', icon: Ruler },
  { path: '/progress/bmi', label: 'BMI', icon: Calculator },
  { path: '/progress/analytics', label: 'Analytics', icon: TrendingUp },
];

const Progress = ({ section = 'weight' }) => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [weight, setWeight] = useState('');
  const [weightHistory, setWeightHistory] = useState([]);
  const [bmiInput, setBmiInput] = useState({ height: '', weight: '' });
  const [saving, setSaving] = useState(false);

  const loadProgress = useCallback(async () => {
    if (!user?.id) return;
    setError('');
    try {
      const progress = await progressService.getProgress(user.id);
      setData(progress);
      setWeightHistory(
        progress.weightProgress.map((item, index) => ({
          id: index + 1,
          date: item.label,
          weight: `${item.weight} kg`,
          bodyFat: item.bodyFat == null ? '--' : `${item.bodyFat}%`,
        })),
      );
      const latest = progress.weightProgress.at(-1);
      if (latest?.weight) {
        setBmiInput((current) => ({ ...current, weight: latest.weight }));
      }
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load progress.'));
    }
  }, [user?.id]);

  useEffect(() => {
    loadProgress();
  }, [loadProgress]);

  if (error) return <StateBlock message={error} onAction={loadProgress} />;
  if (!data) return <LoadingSpinner label="Loading progress" />;

  const bmi = calculateBmi(bmiInput.height, bmiInput.weight);
  const measurementCards = [
    { label: 'Chest', value: `${data.measurements.chest} cm` },
    { label: 'Waist', value: `${data.measurements.waist} cm` },
    { label: 'Arms', value: `${data.measurements.arms} cm` },
    { label: 'Legs', value: `${data.measurements.legs} cm` },
    { label: 'Body Fat %', value: `${data.measurements.bodyFat}%` },
  ];

  const addWeight = async (event) => {
    event.preventDefault();
    if (saving || Number(weight) <= 0 || !user?.id) return;
    setSaving(true);
    setError('');
    try {
      await progressService.addWeight(user.id, Number(weight));
      setWeight('');
      await loadProgress();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to add weight.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        description="Record weight, measurements, body composition, BMI, and training consistency trends."
        icon={Activity}
        kicker="Progress"
        title="Progress Tracking"
      />
      <ModuleTabs tabs={tabs} />

      {section === 'weight' ? (
        <div className="grid gap-4 xl:grid-cols-[0.75fr_1.25fr]">
          <Card>
            <h2 className="text-lg font-bold text-ink">Add Weight</h2>
            <form className="mt-4 space-y-4" onSubmit={addWeight}>
              <FormField
                label="Weight kg"
                required
                min="0.1"
                step="0.1"
                name="weight"
                onChange={(event) => setWeight(event.target.value)}
                placeholder="79.4"
                type="number"
                value={weight}
              />
              <Button icon={Plus} isLoading={saving} type="submit">Add Weight</Button>
            </form>
            <div className="mt-6 rounded-lg bg-surface-alt p-4">
              <p className="text-sm font-semibold text-muted">Latest Weight</p>
              <p className="mt-1 text-3xl font-bold text-ink">{data.weightProgress.at(-1)?.weight || '--'} kg</p>
            </div>
          </Card>

          <div className="space-y-4">
            <ChartCard subtitle="Monthly weight history" title="Progress Graph">
              <ResponsiveContainer height="100%" width="100%">
                <LineChart data={data.weightProgress}>
                  <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                  <XAxis dataKey="label" tickLine={false} />
                  <YAxis tickLine={false} width={34} />
                  <Tooltip />
                  <Line isAnimationActive={false} dataKey="weight" stroke="var(--chart-1)" strokeWidth={3} type="monotone" />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>
            <DataTable
              columns={[
                { key: 'date', header: 'Date' },
                { key: 'weight', header: 'Weight' },
                { key: 'bodyFat', header: 'Body Fat' },
              ]}
              data={weightHistory}
            />
          </div>
        </div>
      ) : null}

      {section === 'measurements' ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          {measurementCards.map((item) => (
            <Card key={item.label}>
              <p className="text-sm font-semibold text-muted">{item.label}</p>
              <p className="mt-2 text-3xl font-bold text-ink">{item.value}</p>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'bmi' ? (
        <div className="grid gap-4 lg:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <h2 className="text-lg font-bold text-ink">BMI Calculator</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <FormField
                label="Height cm"
                name="height"
                onChange={(event) => setBmiInput((current) => ({ ...current, height: event.target.value }))}
                type="number"
                value={bmiInput.height}
              />
              <FormField
                label="Weight kg"
                name="weight"
                onChange={(event) => setBmiInput((current) => ({ ...current, weight: event.target.value }))}
                type="number"
                value={bmiInput.weight}
              />
            </div>
          </Card>
          <Card>
            <p className="text-sm font-semibold text-muted">BMI Value</p>
            <p className="mt-2 text-6xl font-bold text-ink">{bmi.value || '--'}</p>
            <div className="mt-4">
              <Badge tone={bmi.category === 'Normal' ? 'emerald' : 'amber'}>{bmi.category}</Badge>
            </div>
          </Card>
        </div>
      ) : null}

      {section === 'analytics' ? (
        <div className="grid gap-4 xl:grid-cols-3">
          <ChartCard title="Weight Progress">
            <ResponsiveContainer height="100%" width="100%">
              <LineChart data={data.weightProgress}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Line isAnimationActive={false} dataKey="weight" stroke="var(--chart-1)" strokeWidth={3} type="monotone" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard title="Body Fat Progress">
            <ResponsiveContainer height="100%" width="100%">
              <LineChart data={data.weightProgress}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Line isAnimationActive={false} dataKey="bodyFat" stroke="#f43f5e" strokeWidth={3} type="monotone" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard title="Workout Consistency">
            <ResponsiveContainer height="100%" width="100%">
              <BarChart data={data.workoutCompletion}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.12} />
                <XAxis dataKey="label" tickLine={false} />
                <YAxis tickLine={false} width={34} />
                <Tooltip />
                <Bar isAnimationActive={false} dataKey="completed" fill="var(--chart-2)" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>
      ) : null}
    </div>
  );
};

export default Progress;
