import { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CalendarPlus, History, IdCard, UserRoundCheck, UsersRound } from 'lucide-react';
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
import { formatDate } from '../utils/formatters';

const tabs = [
  { path: '/trainers/list', label: 'Trainers', icon: UsersRound },
  { path: '/trainers/profiles', label: 'Profiles', icon: IdCard },
  { path: '/trainers/booking', label: 'Booking', icon: CalendarPlus },
  { path: '/trainers/history', label: 'History', icon: History },
];

const historyColumns = [
  { key: 'trainerName', header: 'Trainer Name' },
  { key: 'date', header: 'Date', render: (row) => formatDate(row.date) },
  { key: 'status', header: 'Status', badge: true },
];

const Trainers = ({ section = 'list' }) => {
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [booking, setBooking] = useState({ trainerId: '', date: new Date().toISOString().slice(0, 10), time: '' });
  const [submitting, setSubmitting] = useState(false);

  const loadTrainers = useCallback(async () => {
    if (!user?.id) return;
    setError('');
    try {
      const [trainers, sessions] = await Promise.all([
        trainerService.getTrainers(),
        trainerService.getSessions(user.id),
      ]);
      setData({ trainers, ...sessions });
      setBooking((current) => ({ ...current, trainerId: current.trainerId || String(trainers[0]?.id || '') }));
    } catch (requestError) {
      setError(formatApiError(requestError, 'Unable to load trainers.'));
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
    if (submitting || !user?.id || !booking.trainerId || !booking.date || !booking.time) return;
    const [hours, minutes] = booking.time.split(':').map(Number);
    const endHour = String((hours + 1) % 24).padStart(2, '0');
    const endTime = `${endHour}:${String(minutes || 0).padStart(2, '0')}`;
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
    } finally {
      setSubmitting(false);
    }
  };

  if (error) return <StateBlock message={error} onAction={loadTrainers} />;
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

      {section === 'list' ? (
        <div className="grid gap-4 lg:grid-cols-3">
          {data.trainers.map((trainer) => (
            <Card className="overflow-hidden p-0" key={trainer.id}>
              <img alt={trainer.name} className="h-60 w-full object-cover" src={trainer.photo} />
              <div className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-bold text-slate-950 dark:text-white">{trainer.name}</h2>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{trainer.specialization}</p>
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
                <img alt={trainer.name} className="h-20 w-20 rounded-lg object-cover" src={trainer.photo} />
                <div className="min-w-0">
                  <h2 className="text-xl font-bold text-slate-950 dark:text-white">{trainer.name}</h2>
                  <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{trainer.specialization}</p>
                  <Badge className="mt-2" tone="sky">{trainer.experience}</Badge>
                </div>
              </div>
              <p className="mt-5 text-sm leading-6 text-slate-600 dark:text-slate-300">{trainer.bio}</p>
              <div className="mt-5">
                <p className="text-sm font-bold uppercase text-slate-500 dark:text-slate-400">Certifications</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {trainer.certifications.map((certification) => (
                    <Badge key={certification}>{certification}</Badge>
                  ))}
                </div>
              </div>
              <div className="mt-5">
                <p className="text-sm font-bold uppercase text-slate-500 dark:text-slate-400">Available Slots</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {trainer.availableSlots.map((slot) => (
                    <Badge key={slot} tone="emerald">{slot}</Badge>
                  ))}
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'booking' && selectedTrainer ? (
        <div className="grid gap-4 lg:grid-cols-[0.8fr_1.2fr]">
          <Card>
            <h2 className="text-lg font-bold text-slate-950 dark:text-white">Session Booking</h2>
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
                min={new Date().toLocaleDateString('en-CA')}
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
              <img alt={selectedTrainer.name} className="h-32 w-32 rounded-lg object-cover" src={selectedTrainer.photo} />
              <div>
                <h2 className="text-2xl font-bold text-slate-950 dark:text-white">{selectedTrainer.name}</h2>
                <p className="mt-1 text-slate-500 dark:text-slate-400">{selectedTrainer.specialization}</p>
                <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">{selectedTrainer.bio}</p>
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
