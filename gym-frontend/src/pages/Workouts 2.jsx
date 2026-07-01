import { useContext, useEffect, useState } from 'react';
import { Activity, AlertCircle, Clock, Dumbbell, ListChecks } from 'lucide-react';
import { AuthContext } from '../context/auth-context';
import api, { formatApiError } from '../services/api';
import './ModulePage.css';

const Workouts = () => {
  const { user } = useContext(AuthContext);
  const [logs, setLogs] = useState([]);
  const [exercises, setExercises] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const loadWorkouts = async () => {
      if (!user?.id) {
        setError('Unable to load workouts because the user session is missing.');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');

      try {
        const [logsResponse, exercisesResponse] = await Promise.all([
          api.get(`/workouts/logs/${user.id}`),
          api.get('/workouts/exercises'),
        ]);

        setLogs(Array.isArray(logsResponse.data) ? logsResponse.data : []);
        setExercises(Array.isArray(exercisesResponse.data) ? exercisesResponse.data : []);
      } catch (err) {
        console.error('Failed to load workouts:', err);
        setError(formatApiError(err, 'Workouts are unavailable right now.'));
        setLogs([]);
        setExercises([]);
      } finally {
        setLoading(false);
      }
    };

    loadWorkouts();
  }, [user?.id]);

  if (loading) {
    return (
      <section className="module-page animate-fade-in">
        <div className="glass-card module-loading">
          <div className="spinner"></div>
          <p className="text-muted">Loading workouts...</p>
        </div>
      </section>
    );
  }

  return (
    <section className="module-page animate-fade-in">
      <div className="module-header">
        <div className="module-title">
          <div className="module-title-icon">
            <Dumbbell size={24} />
          </div>
          <div>
            <h2>Workouts</h2>
            <p className="text-muted">Exercise library and recent workout logs</p>
          </div>
        </div>
      </div>

      {error && (
        <div className="glass-card module-error">
          <AlertCircle size={28} />
          <h3>Workout data could not be loaded</h3>
          <p>{error}</p>
        </div>
      )}

      {!error && (
        <>
          <div className="module-stats">
            <div className="glass-card module-stat">
              <span>Logged workouts</span>
              <strong>{logs.length}</strong>
            </div>
            <div className="glass-card module-stat">
              <span>Available exercises</span>
              <strong>{exercises.length}</strong>
            </div>
          </div>

          <div className="glass-card module-panel">
            <div className="module-card-header">
              <h3>Recent Logs</h3>
              <span className="module-badge">
                <Clock size={14} />
                Latest
              </span>
            </div>

            {logs.length === 0 ? (
              <div className="module-empty">
                <Activity size={42} color="var(--border-color)" />
                <h3>No workouts yet.</h3>
                <p className="text-muted">Completed workouts will appear here after they are logged.</p>
              </div>
            ) : (
              <div className="module-list">
                {logs.map((log) => (
                  <article className="glass-card module-card" key={log.id}>
                    <div className="module-card-header">
                      <h3>{log.date || 'Workout log'}</h3>
                      <span className="module-badge">{log.durationMinutes || 0} min</span>
                    </div>
                    <p className="text-muted">{log.notes || 'No notes recorded.'}</p>
                    <div className="module-meta">
                      <span className="module-badge">
                        <ListChecks size={14} />
                        {log.exercises?.length || 0} exercises
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>

          <div className="glass-card module-panel">
            <div className="module-card-header">
              <h3>Exercise Library</h3>
              <span className="module-badge">{exercises.length} items</span>
            </div>

            {exercises.length === 0 ? (
              <div className="module-empty">
                <Dumbbell size={42} color="var(--border-color)" />
                <h3>No exercises available.</h3>
                <p className="text-muted">Exercise templates will appear here when they are added in the backend.</p>
              </div>
            ) : (
              <div className="module-grid">
                {exercises.map((exercise) => (
                  <article className="glass-card module-card" key={exercise.id}>
                    <h3>{exercise.name}</h3>
                    <p className="text-muted">{exercise.description || 'No description available.'}</p>
                    <div className="module-meta">
                      <span className="module-badge">{exercise.category}</span>
                      <span className="module-badge">{exercise.difficulty}</span>
                      <span className="module-badge">{exercise.recommendedSets || 0} sets</span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </section>
  );
};

export default Workouts;
