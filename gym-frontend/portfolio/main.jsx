import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import Card from '../src/components/common/Card';
import '../src/index.css';
import gyms from './fixtures.json';

export function GymPreview() {
  const [selected, setSelected] = useState(gyms[0].id);
  const gym = gyms.find(({ id }) => id === selected);
  const price = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(gym.price);

  return (
    <>
      <a className="skip-link" href="#workspace">Skip to gym workspace</a>
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-5 py-4 sm:px-8">
          <span className="text-xl font-bold tracking-tight">FitCore</span>
          <span className="text-sm font-medium text-muted">Portfolio preview</span>
        </div>
      </header>
      <main id="workspace" className="mx-auto max-w-5xl space-y-5 px-5 py-7 sm:px-8 sm:py-10">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold sm:text-4xl">{gym.name}</h1>
            <p className="mt-2 max-w-xl text-muted">Explore a read-only gym workspace with fictional people, plans and bookings.</p>
          </div>
          <label className="grid gap-1 text-sm font-semibold">
            Gym workspace
            <select className="rounded-lg border border-control-line bg-surface px-3 py-2" value={selected}
              onChange={(event) => setSelected(event.target.value)}>
              {gyms.map(({ id, name }) => <option key={id} value={id}>{name}</option>)}
            </select>
          </label>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Card>
            <h2 className="text-lg font-semibold">People</h2>
            <dl className="mt-3 divide-y divide-line">
              {[['Owner', gym.owner], ['Trainer', gym.trainer], ['Member', gym.member]].map(([role, name]) => (
                <div className="flex justify-between gap-4 py-2" key={role}>
                  <dt className="text-muted">{role}</dt>
                  <dd className="text-right font-medium">{name}</dd>
                </div>
              ))}
            </dl>
          </Card>
          <Card>
            <h2 className="text-lg font-semibold">Active membership</h2>
            <p className="mt-3 font-medium">{gym.plan}</p>
            <p className="mt-1 text-muted">{gym.member} · {price} for {gym.months} {gym.months === 1 ? 'month' : 'months'}</p>
          </Card>
          <Card>
            <h2 className="text-lg font-semibold">Upcoming booking</h2>
            <p className="mt-3 font-medium">{gym.booking}</p>
            <p className="mt-1 text-muted">{gym.member} with {gym.trainer}</p>
            <p className="mt-1 text-muted">Tomorrow · {gym.time}</p>
          </Card>
          <Card>
            <h2 className="text-lg font-semibold">Exercise catalog</h2>
            <p className="mt-3 font-medium">{gym.exercise}</p>
            <p className="mt-1 text-muted">{gym.category} · {gym.specialization}</p>
          </Card>
        </div>
      </main>
    </>
  );
}

createRoot(document.getElementById('root')).render(<GymPreview />);
