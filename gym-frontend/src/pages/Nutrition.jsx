import { useEffect, useMemo, useState } from 'react';
import { Apple, Beef, Flame, Plus, Salad, Search, Target } from 'lucide-react';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import Card from '../components/common/Card';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ModuleTabs from '../components/common/ModuleTabs';
import PageHeader from '../components/common/PageHeader';
import SearchBar from '../components/common/SearchBar';
import StatCard from '../components/common/StatCard';
import FormField from '../components/forms/FormField';
import { nutritionService } from '../services/mockApi';
import { percent } from '../utils/formatters';

const tabs = [
  { path: '/nutrition/dashboard', label: 'Dashboard', icon: Apple },
  { path: '/nutrition/diet-plans', label: 'Diet Plans', icon: Salad },
  { path: '/nutrition/food-logger', label: 'Food Logger', icon: Search },
  { path: '/nutrition/calorie-tracker', label: 'Calories', icon: Target },
];

const nutritionIcons = [Flame, Beef, Apple, Salad];

const Nutrition = ({ section = 'dashboard' }) => {
  const [data, setData] = useState(null);
  const [query, setQuery] = useState('');
  const [foodLogs, setFoodLogs] = useState([
    { id: 1, name: 'Oats and eggs', calories: 520, protein: 32, carbs: 54, fat: 18 },
    { id: 2, name: 'Chicken rice bowl', calories: 680, protein: 52, carbs: 76, fat: 14 },
  ]);
  const [form, setForm] = useState({ name: '', calories: '', protein: '', carbs: '', fat: '' });

  useEffect(() => {
    Promise.all([
      nutritionService.getDashboard(),
      nutritionService.getDietPlans(),
      nutritionService.getFoodSuggestions(),
    ]).then(([dashboard, dietPlans, suggestions]) => setData({ ...dashboard, dietPlans, suggestions }));
  }, []);

  const filteredFoods = useMemo(() => {
    if (!data) return [];
    const normalizedQuery = query.trim().toLowerCase();
    return data.suggestions.filter((food) =>
      [food.name, food.serving].some((value) => value.toLowerCase().includes(normalizedQuery)),
    );
  }, [data, query]);

  const addFood = (food) => {
    setFoodLogs((current) => [{ ...food, id: Date.now() }, ...current]);
  };

  const submitFood = (event) => {
    event.preventDefault();
    if (!form.name || !form.calories) return;
    addFood({
      name: form.name,
      calories: Number(form.calories),
      protein: Number(form.protein) || 0,
      carbs: Number(form.carbs) || 0,
      fat: Number(form.fat) || 0,
      serving: 'Custom',
    });
    setForm({ name: '', calories: '', protein: '', carbs: '', fat: '' });
  };

  if (!data) return <LoadingSpinner label="Loading nutrition" />;

  const consumedCalories = foodLogs.reduce((total, food) => total + Number(food.calories), 0);
  const calorieGoal = data.calorieTracker.dailyGoal;
  const remainingCalories = Math.max(calorieGoal - consumedCalories, 0);

  return (
    <div className="space-y-6">
      <PageHeader
        actions={section === 'food-logger' ? <SearchBar onChange={setQuery} placeholder="Search food" value={query} /> : null}
        description="Plan meals, log food, inspect nutrition facts, and track daily calorie progress."
        icon={Apple}
        kicker="Nutrition"
        title="Nutrition Management"
      />
      <ModuleTabs tabs={tabs} />

      {section === 'dashboard' ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {data.nutritionCards.map((card, index) => (
            <StatCard
              detail={`Goal: ${card.target}`}
              icon={nutritionIcons[index]}
              key={card.title}
              title={card.title}
              tone={card.tone}
              value={card.value}
            />
          ))}
        </div>
      ) : null}

      {section === 'diet-plans' ? (
        <div className="grid gap-4 lg:grid-cols-2">
          {data.dietPlans.map((plan) => (
            <Card key={plan.category}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="text-xl font-bold text-slate-950 dark:text-white">{plan.category}</h2>
                  <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{plan.calories} kcal daily target</p>
                </div>
                <Badge tone="emerald">{plan.protein}g protein</Badge>
              </div>
              <div className="mt-5 grid grid-cols-3 gap-3">
                <div className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/70">
                  <p className="text-xs font-semibold uppercase text-slate-500 dark:text-slate-400">Carbs</p>
                  <p className="font-bold">{plan.carbs}g</p>
                </div>
                <div className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/70">
                  <p className="text-xs font-semibold uppercase text-slate-500 dark:text-slate-400">Fats</p>
                  <p className="font-bold">{plan.fats}g</p>
                </div>
                <div className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/70">
                  <p className="text-xs font-semibold uppercase text-slate-500 dark:text-slate-400">Meals</p>
                  <p className="font-bold">{plan.meals.length}</p>
                </div>
              </div>
              <div className="mt-5 flex flex-wrap gap-2">
                {plan.meals.map((meal) => (
                  <Badge key={meal} tone="sky">{meal}</Badge>
                ))}
              </div>
            </Card>
          ))}
        </div>
      ) : null}

      {section === 'food-logger' ? (
        <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
          <Card>
            <h2 className="text-lg font-bold text-slate-950 dark:text-white">Add Food</h2>
            <form className="mt-4 grid gap-4 sm:grid-cols-2" onSubmit={submitFood}>
              <FormField
                className="sm:col-span-2"
                label="Food name"
                name="name"
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Grilled salmon"
                value={form.name}
              />
              {['calories', 'protein', 'carbs', 'fat'].map((field) => (
                <FormField
                  key={field}
                  label={field[0].toUpperCase() + field.slice(1)}
                  name={field}
                  onChange={(event) => setForm((current) => ({ ...current, [field]: event.target.value }))}
                  placeholder="0"
                  type="number"
                  value={form[field]}
                />
              ))}
              <Button className="sm:col-span-2" icon={Plus} type="submit">
                Add Food
              </Button>
            </form>
          </Card>

          <div className="space-y-4">
            <Card>
              <h2 className="text-lg font-bold text-slate-950 dark:text-white">Nutrition Facts</h2>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {filteredFoods.map((food) => (
                  <div className="rounded-lg border border-slate-200 p-3 dark:border-slate-800" key={food.id}>
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-bold text-slate-950 dark:text-white">{food.name}</p>
                        <p className="text-sm text-slate-500 dark:text-slate-400">{food.serving}</p>
                      </div>
                      <Button onClick={() => addFood(food)} size="sm" variant="outline">Log</Button>
                    </div>
                    <div className="mt-3 grid grid-cols-4 gap-2 text-center text-xs font-semibold">
                      <span>{food.calories} kcal</span>
                      <span>{food.protein}g P</span>
                      <span>{food.carbs}g C</span>
                      <span>{food.fat}g F</span>
                    </div>
                  </div>
                ))}
              </div>
            </Card>

            <Card>
              <h2 className="text-lg font-bold text-slate-950 dark:text-white">Food Log</h2>
              <div className="mt-4 space-y-3">
                {foodLogs.map((food) => (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-slate-50 p-3 dark:bg-slate-800/70" key={food.id}>
                    <div>
                      <p className="font-semibold text-slate-900 dark:text-white">{food.name}</p>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {food.protein}g protein, {food.carbs}g carbs, {food.fat}g fat
                      </p>
                    </div>
                    <Badge tone="amber">{food.calories} kcal</Badge>
                  </div>
                ))}
              </div>
            </Card>
          </div>
        </div>
      ) : null}

      {section === 'calorie-tracker' ? (
        <Card>
          <div className="grid gap-6 lg:grid-cols-[0.7fr_1.3fr]">
            <div>
              <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Daily Goal</p>
              <p className="mt-2 text-5xl font-bold text-slate-950 dark:text-white">{calorieGoal}</p>
              <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Consumed Calories: {consumedCalories}</p>
              <p className="text-sm text-slate-500 dark:text-slate-400">Remaining Calories: {remainingCalories}</p>
            </div>
            <div>
              <div className="mb-2 flex justify-between text-sm font-semibold text-slate-600 dark:text-slate-300">
                <span>Calorie progress</span>
                <span>{percent(consumedCalories, calorieGoal)}%</span>
              </div>
              <div className="h-4 rounded-full bg-slate-100 dark:bg-slate-800">
                <div
                  className="h-4 rounded-full bg-emerald-500 transition-all"
                  style={{ width: `${Math.min(percent(consumedCalories, calorieGoal), 100)}%` }}
                />
              </div>
              <div className="mt-6 grid gap-3 sm:grid-cols-3">
                <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                  <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Consumed</p>
                  <p className="text-xl font-bold">{consumedCalories}</p>
                </div>
                <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                  <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Remaining</p>
                  <p className="text-xl font-bold">{remainingCalories}</p>
                </div>
                <div className="rounded-lg bg-slate-50 p-4 dark:bg-slate-800/70">
                  <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">Logged Foods</p>
                  <p className="text-xl font-bold">{foodLogs.length}</p>
                </div>
              </div>
            </div>
          </div>
        </Card>
      ) : null}
    </div>
  );
};

export default Nutrition;
