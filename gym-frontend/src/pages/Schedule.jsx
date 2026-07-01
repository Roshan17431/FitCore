import { useContext, useEffect, useState } from 'react';
import { AlertCircle, Calendar, Clock, UserRound } from 'lucide-react';
import { AuthContext } from '../context/auth-context';
import api, { formatApiError } from '../services/api';
import './ModulePage.css';

const Schedule = () => {
  const { user } = useContext(AuthContext);
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const loadSchedule = async () => {
      if (!user?.id) {
        setError('Unable to load schedule because the user session is missing.');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');

      try {
        const response = await api.get(`/trainers/sessions/${user.id}`);
        setSessions(Array.isArray(response.data) ? response.data : []);
      } catch (err) {
        console.error('Failed to load schedule:', err);
        setError(formatApiError(err, 'Schedule is unavailable right now.'));
        setSessions([]);
      } finally {
        setLoading(false);
      }
    };

    loadSchedule();
  }, [user?.id]);

  if (loading) {
    return (
      <section className="module-page animate-fade-in">
        <div className="glass-card module-loading">
          <div className="spinner"></div>
          <p className="text-muted">Loading schedule...</p>
        </div>
      </section>
    );
  }

  return (
    <section className="module-page animate-fade-in">
      <div className="module-header">
        <div className="module-title">
          <div className="module-title-icon">
            <Calendar size={24} />
          </div>
          <div>
            <h2>Schedule</h2>
            <p className="text-muted">Upcoming trainer sessions</p>
          </div>
        </div>
      </div>

      {error && (
        <div className="glass-card module-error">
          <AlertCircle size={28} />
          <h3>Schedule could not be loaded</h3>
          <p>{error}</p>
        </div>
      )}

      {!error && (
        <div className="glass-card module-panel">
          <div className="module-card-header">
            <h3>Booked Sessions</h3>
            <span className="module-badge">{sessions.length} sessions</span>
          </div>

          {sessions.length === 0 ? (
            <div className="module-empty">
              <Calendar size={42} color="var(--border-color)" />
              <h3>No sessions scheduled.</h3>
              <p className="text-muted">Trainer bookings will appear here after they are scheduled.</p>
            </div>
          ) : (
            <div className="module-list">
              {sessions.map((session) => (
                <article className="glass-card module-card" key={session.id}>
                  <div className="module-card-header">
                    <h3>{session.sessionDate || 'Scheduled session'}</h3>
                    <span className="module-badge">{session.status || 'Booked'}</span>
                  </div>
                  <p className="text-muted">
                    {session.trainer?.fullName || session.trainer?.name || 'Trainer details unavailable'}
                  </p>
                  <div className="module-meta">
                    <span className="module-badge">
                      <Clock size={14} />
                      {session.startTime || '--:--'} - {session.endTime || '--:--'}
                    </span>
                    <span className="module-badge">
                      <UserRound size={14} />
                      Personal training
                    </span>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
};

export default Schedule;
