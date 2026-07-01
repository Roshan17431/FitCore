import { useContext, useEffect, useMemo, useState } from 'react';
import { AlertCircle, Apple, Flame, Utensils } from 'lucide-react';
import { AuthContext } from '../context/auth-context';
import api, { formatApiError } from '../services/api';
import './ModulePage.css';

const Nutrition = () => {
  const { user } = useContext(AuthContext);
  const [goal, setGoal] = useState(null);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const loadNutrition = async () => {
      if (!user?.id) {
        setError('Unable to load nutrition because the user session is missing.');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');

      try {
        const [goalResult, logsResponse] = await Promise.all([
          api.get(`/nutrition/goal/${user.id}`).catch((err) => {
            if (err.response?.status === 404) {
              return { data: null };
            }
            throw err;
          }),
          api.get(`/nutrition/logs/${user.id}`),
        ]);

        setGoal(goalResult.data);
        setLogs(Array.isArray(logsResponse.data) ? logsResponse.data : []);
      } catch (err) {
        console.error('Failed to load nutrition:', err);
        setError(formatApiError(err, 'Nutrition data is unavailable right now.'));
        setGoal(null);
        setLogs([]);
      } finally {
        setLoading(false);
      }
    };

    loadNutrition();
  }, [user?.id]);

  const totals = useMemo(() => {
    return logs.reduce(
      (current, log) => ({
        calories: current.calories + (log.calories || 0),
        protein: current.protein + (log.protein || 0),
        carbs: current.carbs + (log.carbs || 0),
        fat: current.fat + (log.fat || 0),
      }),
      { calories: 0, protein: 0, carbs: 0, fat: 0 },
    );
  }, [logs]);

  if (loading) {
    return (
      <section className="module-page animate-fade-in">
        <div className="glass-card module-loading">
          <div className="spinner"></div>
          <p className="text-muted">Loading nutrition...</p>
        </div>
      </section>
    );
  }

  return (
    <section className="module-page animate-fade-in">
      <div className="module-header">
        <div className="module-title">
          <div className="module-title-icon">
            <Apple size={24} />
          </div>
          <div>
            <h2>Nutrition</h2>
            <p className="text-muted">Daily goals and food logs</p>
          </div>
        </div>
      </div>

      {error && (
        <div className="glass-card module-error">
          <AlertCircle size={28} />
          <h3>Nutrition data could not be loaded</h3>
          <p>{error}</p>
        </div>
      )}

      {!error && (
        <>
          <div className="module-stats">
            <div className="glass-card module-stat">
              <span>Calories today</span>
              <strong>{totals.calories}</strong>
            </div>
            <div className="glass-card module-stat">
              <span>Protein</span>
              <strong>{totals.protein}g</strong>
            </div>
            <div className="glass-card module-stat">
              <span>Carbs</span>
              <strong>{totals.carbs}g</strong>
            </div>
            <div className="glass-card module-stat">
              <span>Fat</span>
              <strong>{totals.fat}g</strong>
            </div>
          </div>

          <div className="glass-card module-panel">
            <div className="module-card-header">
              <h3>Daily Goal</h3>
              <span className="module-badge">
                <Flame size={14} />
                Target
              </span>
            </div>

            {goal ? (
              <div className="module-grid">
                <div className="glass-card module-card">
                  <h3>{goal.targetCalories} calories</h3>
                  <div className="module-meta">
                    <span className="module-badge">{goal.targetProtein}g protein</span>
                    <span className="module-badge">{goal.targetCarbs}g carbs</span>
                    <span className="module-badge">{goal.targetFat}g fat</span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="module-empty">
                <Apple size={42} color="var(--border-color)" />
                <h3>No nutrition goal yet.</h3>
                <p className="text-muted">Your target calories and macros will appear here once they are configured.</p>
              </div>
            )}
          </div>

          <div className="glass-card module-panel">
            <div className="module-card-header">
              <h3>Food Logs</h3>
              <span className="module-badge">{logs.length} entries</span>
            </div>

            {logs.length === 0 ? (
              <div className="module-empty">
                <Utensils size={42} color="var(--border-color)" />
                <h3>No food logged today.</h3>
                <p className="text-muted">Food entries will appear here after meals are logged.</p>
              </div>
            ) : (
              <div className="module-list">
                {logs.map((log) => (
                  <article className="glass-card module-card" key={log.id}>
                    <div className="module-card-header">
                      <h3>{log.mealName}</h3>
                      <span className="module-badge">{log.calories || 0} kcal</span>
                    </div>
                    <p className="text-muted">{log.mealType || 'Meal'}</p>
                    <div className="module-meta">
                      <span className="module-badge">{log.protein || 0}g protein</span>
                      <span className="module-badge">{log.carbs || 0}g carbs</span>
                      <span className="module-badge">{log.fat || 0}g fat</span>
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

export default Nutrition;
