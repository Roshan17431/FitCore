import { useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CalendarPlus, History, IdCard, UserRoundCheck, UsersRound } from 'lucide-react';
import Avatar from '../components/common/Avatar';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import StateBlock from '../components/common/StateBlock';
import Toast from '../components/common/Toast';
import FormField from '../components/forms/FormField';
import DataTable from '../components/tables/DataTable';
import { AuthContext } from '../context/auth-context';
import { formatApiError, trainerService } from '../services/api';

const tabs = [
  { path: '/trainers/list', label: 'Trainers', icon: UsersRound },
  { path: '/trainers/profiles', label: 'Profiles', icon: IdCard },
  { path: '/trainers/booking', label: 'Booking', icon: CalendarPlus },
  { path: '/trainers/history', label: 'History', icon: History },
];

const historyColumns = [
  { key: 'trainerName', header: 'Trainer Name' },
  { key: 'date', header: 'Date (UTC)', render: (row) => new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${row.date}T00:00:00Z`)) },
  { key: 'time', header: 'Time (UTC)' },
  { key: 'status', header: 'Status', badge: true },
];

const Trainers = ({ section = 'list' }) => {
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [booking, setBooking] = useState(() => ({ trainerId: '', date: new Date().toISOString().slice(0, 10), time: '' }));
  const [submitting, setSubmitting] = useState(false);
  const submissionLock = useRef(false);

  const loadTrainers = useCallback(async ({ preserveError = false } = {}) => {
    if (!user?.id) return;
    if (!preserveError) setError('');
    try {
      const [trainers, sessions] = await Promise.all([
        trainerService.getTrainers(),
        trainerService.getSessions(user.id),
      ]);
      setData({ trainers, ...sessions });
      setBooking((current) => ({ ...current, trainerId: current.trainerId || String(trainers[0]?.id || '') }));
    } catch (requestError) {
      setError((current) => preserveError && current ? current : formatApiError(requestError, 'Unable to load trainers.'));
    }
  }, [user?.id]);

  useEffect(() => {
    loadTrainers();
  }, [loadTrainers]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const selectedTrainer = useMemo(() => {
    if (!data) return null;
    return data.trainers.find((trainer) => String(trainer.id) === booking.trainerId) || data.trainers[0];
  }, [booking.trainerId, data]);

  const submitBooking = async (event) => {
    event.preventDefault();
    if (submissionLock.current || !user?.id || !booking.trainerId || !booking.date || !booking.time) return;
    setToast('');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(booking.date) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(booking.time)) {
      setError('Choose a valid UTC date and time.');
      return;
    }
    const [hours, minutes] = booking.time.split(':').map(Number);
    if (hours >= 23) {
      setError('Sessions must start and finish on the same day in UTC. Choose a time before 23:00.');
      return;
    }
    const start = Date.parse(`${booking.date}T${booking.time}:00Z`);
    if (!Number.isFinite(start) || new Date(start).toISOString().slice(0, 10) !== booking.date || start <= Date.now()) {
      setError('Choose a session start in the future (UTC).');
      return;
    }
    const endHour = String(hours + 1).padStart(2, '0');
    const endTime = `${endHour}:${String(minutes || 0).padStart(2, '0')}`;
    submissionLock.current = true;
    setSubmitting(true);
    setError('');
    try {
      await trainerService.bookSession(user.id, booking.trainerId, {
        member: user,
        trainer: { id: Number(booking.trainerId), user: selectedTrainer.user, specialization: selectedTrainer.specialization },
        sessionDate: booking.date,
        startTime: booking.time,
        endTime,
      });
      setToast(`Session booked with ${selectedTrainer?.name || 'trainer'}`);
      await loadTrainers();
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to book trainer session.'));
      if (requestError.response?.status === 409) await loadTrainers({ preserveError: true });
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  };

  if (error && !data) return <StateBlock message={error} onAction={() => loadTrainers()} />;
  if (!data) return <LoadingSpinner label="Loading trainers" />;

  return (
    <div className="space-y-6">
      <PageHeader
        description="Explore trainer specialties, profiles, certifications, available slots, bookings, and session history."
        icon={UsersRound}
        kicker="Trainers"
        title="Trainer Module"
      />
      <ModuleTabs tabs={tabs} />

      {error ? <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-300" role="alert">{error}</div> : null}

      {section === 'list' ? (
        <div className="grid gap-4 lg:grid-cols-3">
          {data.trainers.map((trainer) => (
            <Card className="overflow-hidden p-0" key={trainer.id}>
              <Avatar name={trainer.name} className="h-48 w-full rounded-none" src={trainer.photo} />
              <div className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-bold text-ink">{trainer.name}</h2>
                    <p className="mt-1 text-sm text-muted">{trainer.specialization}</p>
                  </div>
                  <Badge tone="emerald">{trainer.experience}</Badge>
                </div>
                <Button className="mt-5 w-full" icon={CalendarPlus} onClick={() => {
                  setBooking((current) => ({ ...current, trainerId: String(trainer.id) }));
                  navigate('/trainers/booking');
                }} variant="outline">
                  Book Session
                </Button>
              </div>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'profiles' ? (
        <div className="grid gap-4 lg:grid-cols-2">
          {data.trainers.map((trainer) => (
            <Card key={trainer.id}>
              <div className="flex gap-4">
                <Avatar name={trainer.name} className="h-20 w-20" src={trainer.photo} />
                <div className="min-w-0">
                  <h2 className="text-xl font-bold text-ink">{trainer.name}</h2>
                  <p className="mt-1 text-sm text-muted">{trainer.specialization}</p>
                  <Badge className="mt-2" tone="sky">{trainer.experience}</Badge>
                </div>
              </div>
              <p className="mt-5 text-sm leading-6 text-muted">{trainer.bio}</p>
              <div className="mt-5">
                <p className="text-sm font-bold uppercase text-muted">Certifications</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {trainer.certifications.map((certification) => (
                    <Badge key={certification}>{certification}</Badge>
                  ))}
                </div>
              </div>
              <div className="mt-5">
                <p className="text-sm font-bold uppercase text-muted">Available Slots (UTC)</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {trainer.availableSlots.map((slot) => (
                    <Badge key={slot} tone="emerald">{slot}</Badge>
                  ))}
                  {!trainer.availableSlots.length ? <p className="text-sm text-muted">No daily time restrictions published.</p> : null}
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'booking' && selectedTrainer ? (
        <div className="grid gap-4 lg:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <h2 className="text-lg font-bold text-ink">Session Booking</h2>
            <p className="mt-2 text-sm text-muted">All dates and times use UTC. Sessions last one hour and must finish on the same day.</p>
            <form
              className="mt-4 space-y-4"
              onSubmit={submitBooking}
            >
              <FormField
                label="Select Trainer"
                name="trainerId"
                onChange={(event) => setBooking((current) => ({ ...current, trainerId: event.target.value }))}
                options={data.trainers.map((trainer) => ({ label: trainer.name, value: String(trainer.id) }))}
                value={booking.trainerId}
              />
              <FormField
                label="Select Date"
                required
                min={new Date().toISOString().slice(0, 10)}
                name="date"
                onChange={(event) => setBooking((current) => ({ ...current, date: event.target.value }))}
                type="date"
                value={booking.date}
              />
              <FormField
                label="Select Time"
                required
                max="22:59"
                name="time"
                onChange={(event) => setBooking((current) => ({ ...current, time: event.target.value }))}
                type="time"
                value={booking.time}
              />
              <Button icon={UserRoundCheck} isLoading={submitting} type="submit">Book Session</Button>
            </form>
          </Card>

          <Card>
            <div className="flex flex-col gap-4 sm:flex-row">
              <Avatar name={selectedTrainer.name} className="h-32 w-32" src={selectedTrainer.photo} />
              <div>
                <h2 className="text-2xl font-bold text-ink">{selectedTrainer.name}</h2>
                <p className="mt-1 text-muted">{selectedTrainer.specialization}</p>
                <p className="mt-4 text-sm leading-6 text-muted">{selectedTrainer.bio}</p>
              </div>
            </div>
            <div className="mt-5">
              <p className="text-sm font-bold text-ink">Published availability (UTC)</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {selectedTrainer.availableSlots.map((slot) => <Badge key={slot} tone="emerald">{slot}</Badge>)}
                {!selectedTrainer.availableSlots.length ? <p className="text-sm text-muted">No daily time restrictions published. Availability is confirmed when you book.</p> : null}
              </div>
            </div>
          </Card>
        </div>
      ) : null}

      {section === 'booking' && !selectedTrainer ? (
        <StateBlock title="No trainers available" message="Trainer profiles will appear after they are created by an administrator." />
      ) : null}

      {section === 'history' ? <DataTable columns={historyColumns} data={data.sessionHistory} /> : null}

      <Toast message={toast} />
    </div>
  );
};

export default Trainers;
