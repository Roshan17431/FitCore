export const calculateBmi = (heightCm, weightKg) => {
  const heightM = Number(heightCm) / 100;
  const weight = Number(weightKg);

  if (!heightM || !weight) {
    return { value: 0, category: 'Enter height and weight' };
  }

  const value = Number((weight / (heightM * heightM)).toFixed(1));

  if (value < 18.5) return { value, category: 'Underweight' };
  if (value < 25) return { value, category: 'Normal' };
  if (value < 30) return { value, category: 'Overweight' };
  return { value, category: 'Obese' };
};

export const getCompletionPercentage = (items) => {
  if (!items.length) return 0;
  const complete = items.filter((item) => item.complete).length;
  return Math.round((complete / items.length) * 100);
};

