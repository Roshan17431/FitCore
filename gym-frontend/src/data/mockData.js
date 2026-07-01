export const users = [
  {
    id: 'u-1001',
    fullName: 'Alex Carter',
    email: 'alex.member@fitcore.test',
    role: 'MEMBER',
    fitnessGoal: 'Muscle Gain',
    status: 'Active',
    joinedAt: '2026-01-12',
    membership: 'Premium',
    attendanceRate: 86,
  },
  {
    id: 'u-1002',
    fullName: 'Maya Singh',
    email: 'maya.admin@fitcore.test',
    role: 'ADMIN',
    fitnessGoal: 'Operations',
    status: 'Active',
    joinedAt: '2025-08-19',
    membership: 'Elite',
    attendanceRate: 92,
  },
  {
    id: 'u-1003',
    fullName: 'Ryan Brooks',
    email: 'ryan.trainer@fitcore.test',
    role: 'TRAINER',
    fitnessGoal: 'Strength Coaching',
    status: 'Active',
    joinedAt: '2025-11-03',
    membership: 'Staff',
    attendanceRate: 95,
  },
  {
    id: 'u-1004',
    fullName: 'Priya Nair',
    email: 'priya.member@fitcore.test',
    role: 'MEMBER',
    fitnessGoal: 'Weight Loss',
    status: 'Active',
    joinedAt: '2026-03-05',
    membership: 'Basic',
    attendanceRate: 74,
  },
  {
    id: 'u-1005',
    fullName: 'Jordan Lee',
    email: 'jordan.member@fitcore.test',
    role: 'MEMBER',
    fitnessGoal: 'Maintenance',
    status: 'Paused',
    joinedAt: '2025-12-22',
    membership: 'Premium',
    attendanceRate: 61,
  },
];

export const membershipPlans = [
  {
    id: 1,
    name: 'Basic',
    price: 29,
    duration: '1 month',
    durationMonths: 1,
    recommended: false,
    features: ['Gym floor access', 'Locker room', 'Workout library', 'Monthly body check'],
  },
  {
    id: 2,
    name: 'Premium',
    price: 79,
    duration: '3 months',
    durationMonths: 3,
    recommended: true,
    features: ['Everything in Basic', 'Trainer consultation', 'Nutrition dashboard', 'Progress analytics'],
  },
  {
    id: 3,
    name: 'Elite',
    price: 149,
    duration: '6 months',
    durationMonths: 6,
    recommended: false,
    features: ['Everything in Premium', 'Personal trainer sessions', 'Priority booking', 'Recovery assessment'],
  },
];

export const activeMembership = {
  plan: 'Premium',
  startDate: '2026-05-01',
  expiryDate: '2026-08-01',
  remainingDays: 32,
  status: 'Active',
};

export const membershipHistory = [
  { id: 1, planName: 'Basic', purchaseDate: '2026-01-12', expiryDate: '2026-02-12', status: 'Expired' },
  { id: 2, planName: 'Premium', purchaseDate: '2026-02-13', expiryDate: '2026-05-13', status: 'Expired' },
  { id: 3, planName: 'Premium', purchaseDate: '2026-05-01', expiryDate: '2026-08-01', status: 'Active' },
];

export const dashboardCards = [
  { title: 'Membership Status', value: 'Premium Active', detail: '32 days remaining', trend: '+12%', tone: 'emerald' },
  { title: "Today's Workout", value: 'Push Strength', detail: '5 exercises planned', trend: '65%', tone: 'sky' },
  { title: 'Calories Consumed', value: '1,860 kcal', detail: '640 kcal remaining', trend: '74%', tone: 'amber' },
  { title: 'Attendance Summary', value: '18 visits', detail: 'This month', trend: '+4', tone: 'violet' },
  { title: 'Progress Summary', value: '-3.2 kg', detail: 'Since January', trend: 'On track', tone: 'rose' },
];

export const weightProgress = [
  { label: 'Jan', weight: 86.4, bodyFat: 24.5 },
  { label: 'Feb', weight: 84.9, bodyFat: 23.8 },
  { label: 'Mar', weight: 83.8, bodyFat: 22.9 },
  { label: 'Apr', weight: 82.4, bodyFat: 21.7 },
  { label: 'May', weight: 81.1, bodyFat: 20.8 },
  { label: 'Jun', weight: 79.8, bodyFat: 19.9 },
];

export const workoutCompletion = [
  { label: 'Mon', completed: 90, planned: 100 },
  { label: 'Tue', completed: 76, planned: 100 },
  { label: 'Wed', completed: 100, planned: 100 },
  { label: 'Thu', completed: 64, planned: 100 },
  { label: 'Fri', completed: 88, planned: 100 },
  { label: 'Sat', completed: 72, planned: 100 },
  { label: 'Sun', completed: 45, planned: 100 },
];

export const attendanceChart = [
  { label: 'Week 1', visits: 4 },
  { label: 'Week 2', visits: 5 },
  { label: 'Week 3', visits: 3 },
  { label: 'Week 4', visits: 6 },
];

export const upcomingSessions = [
  { id: 1, trainer: 'Ryan Brooks', focus: 'Strength Assessment', date: '2026-07-01', time: '07:30 AM' },
  { id: 2, trainer: 'Elena Torres', focus: 'Mobility Reset', date: '2026-07-03', time: '06:00 PM' },
  { id: 3, trainer: 'Noah Patel', focus: 'Core Stability', date: '2026-07-05', time: '09:00 AM' },
];

export const recentActivities = [
  { id: 1, title: 'Completed Push Strength', meta: '42 min, 5 exercises', time: 'Today' },
  { id: 2, title: 'Logged quinoa power bowl', meta: '560 kcal, 38g protein', time: 'Yesterday' },
  { id: 3, title: 'Checked in for evening session', meta: '6:18 PM to 7:29 PM', time: 'Jun 28' },
  { id: 4, title: 'Updated body measurements', meta: 'Waist down 1.2 cm', time: 'Jun 27' },
];

export const notifications = [
  { id: 1, title: 'Membership renewal window opens in 12 days', type: 'Membership' },
  { id: 2, title: 'Trainer slot available tomorrow morning', type: 'Session' },
  { id: 3, title: 'Protein target missed twice this week', type: 'Nutrition' },
];

export const workoutCategories = [
  {
    category: 'Chest',
    exercises: [
      {
        id: 1,
        name: 'Barbell Bench Press',
        image: 'https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=800&q=80',
        description: 'Primary compound press for chest, triceps, and anterior shoulders.',
        sets: 4,
        reps: '8-10',
        difficulty: 'Intermediate',
      },
      {
        id: 2,
        name: 'Incline Dumbbell Press',
        image: 'https://images.unsplash.com/photo-1598971639058-fab3c3109a00?auto=format&fit=crop&w=800&q=80',
        description: 'Upper chest builder with balanced shoulder control.',
        sets: 3,
        reps: '10-12',
        difficulty: 'Beginner',
      },
    ],
  },
  {
    category: 'Back',
    exercises: [
      {
        id: 3,
        name: 'Lat Pulldown',
        image: 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=800&q=80',
        description: 'Vertical pull pattern for lats and upper back strength.',
        sets: 4,
        reps: '10-12',
        difficulty: 'Beginner',
      },
      {
        id: 4,
        name: 'Single Arm Row',
        image: 'https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?auto=format&fit=crop&w=800&q=80',
        description: 'Unilateral row to improve back thickness and posture.',
        sets: 3,
        reps: '12 each',
        difficulty: 'Intermediate',
      },
    ],
  },
  {
    category: 'Shoulders',
    exercises: [
      {
        id: 5,
        name: 'Dumbbell Shoulder Press',
        image: 'https://images.unsplash.com/photo-1571019613914-85f342c6a11e?auto=format&fit=crop&w=800&q=80',
        description: 'Overhead press pattern for delts and trunk stability.',
        sets: 4,
        reps: '8-10',
        difficulty: 'Intermediate',
      },
      {
        id: 6,
        name: 'Lateral Raise',
        image: 'https://images.unsplash.com/photo-1599058917765-a780eda07a3e?auto=format&fit=crop&w=800&q=80',
        description: 'Isolation movement for medial deltoid development.',
        sets: 3,
        reps: '12-15',
        difficulty: 'Beginner',
      },
    ],
  },
  {
    category: 'Legs',
    exercises: [
      {
        id: 7,
        name: 'Back Squat',
        image: 'https://images.unsplash.com/photo-1593079831268-3381b0db4a77?auto=format&fit=crop&w=800&q=80',
        description: 'Full lower-body compound lift for strength and coordination.',
        sets: 4,
        reps: '6-8',
        difficulty: 'Advanced',
      },
      {
        id: 8,
        name: 'Romanian Deadlift',
        image: 'https://images.unsplash.com/photo-1605296867424-35fc25c9212a?auto=format&fit=crop&w=800&q=80',
        description: 'Posterior chain hinge pattern for hamstrings and glutes.',
        sets: 3,
        reps: '8-10',
        difficulty: 'Intermediate',
      },
    ],
  },
  {
    category: 'Arms',
    exercises: [
      {
        id: 9,
        name: 'Cable Triceps Pressdown',
        image: 'https://images.unsplash.com/photo-1584863231364-2edc166de576?auto=format&fit=crop&w=800&q=80',
        description: 'Elbow extension movement for triceps volume.',
        sets: 3,
        reps: '12-15',
        difficulty: 'Beginner',
      },
      {
        id: 10,
        name: 'EZ Bar Curl',
        image: 'https://images.unsplash.com/photo-1584466977773-e625c37cdd50?auto=format&fit=crop&w=800&q=80',
        description: 'Biceps curl variation with wrist-friendly grip.',
        sets: 3,
        reps: '10-12',
        difficulty: 'Beginner',
      },
    ],
  },
  {
    category: 'Core',
    exercises: [
      {
        id: 11,
        name: 'Cable Wood Chop',
        image: 'https://images.unsplash.com/photo-1594737625785-a6cbdabd333c?auto=format&fit=crop&w=800&q=80',
        description: 'Rotational core exercise for anti-rotation control.',
        sets: 3,
        reps: '12 each',
        difficulty: 'Intermediate',
      },
      {
        id: 12,
        name: 'Plank',
        image: 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=800&q=80',
        description: 'Core bracing drill for trunk endurance.',
        sets: 3,
        reps: '45 sec',
        difficulty: 'Beginner',
      },
    ],
  },
];

export const workoutPlans = [
  {
    level: 'Beginner',
    duration: '35 min/session',
    schedule: ['Mon: Full Body', 'Wed: Cardio + Core', 'Fri: Full Body'],
    exercises: ['Goblet Squat', 'Incline Press', 'Lat Pulldown', 'Plank'],
  },
  {
    level: 'Intermediate',
    duration: '50 min/session',
    schedule: ['Mon: Push', 'Tue: Pull', 'Thu: Legs', 'Sat: Conditioning'],
    exercises: ['Bench Press', 'Row', 'Back Squat', 'Rower Intervals'],
  },
  {
    level: 'Advanced',
    duration: '70 min/session',
    schedule: ['Mon: Upper Power', 'Tue: Lower Power', 'Thu: Push Hypertrophy', 'Fri: Pull Hypertrophy', 'Sat: Legs'],
    exercises: ['Deadlift', 'Weighted Pull-Up', 'Front Squat', 'Overhead Press'],
  },
];

export const dailyWorkout = [
  { id: 1, name: 'Bench Press', targetSets: 4, targetReps: 10, sets: 4, reps: 10, complete: true },
  { id: 2, name: 'Incline Dumbbell Press', targetSets: 3, targetReps: 12, sets: 3, reps: 10, complete: true },
  { id: 3, name: 'Cable Fly', targetSets: 3, targetReps: 15, sets: 2, reps: 12, complete: false },
  { id: 4, name: 'Triceps Pressdown', targetSets: 3, targetReps: 12, sets: 2, reps: 12, complete: false },
];

export const workoutHistory = [
  { id: 1, workoutDate: '2026-06-29', duration: '46 min', completedExercises: 5 },
  { id: 2, workoutDate: '2026-06-27', duration: '58 min', completedExercises: 7 },
  { id: 3, workoutDate: '2026-06-25', duration: '39 min', completedExercises: 4 },
  { id: 4, workoutDate: '2026-06-23', duration: '52 min', completedExercises: 6 },
];

export const nutritionCards = [
  { title: 'Daily Calories', value: '1,860', target: '2,500 kcal', tone: 'amber' },
  { title: 'Protein Intake', value: '142g', target: '180g', tone: 'emerald' },
  { title: 'Carbs Intake', value: '188g', target: '260g', tone: 'sky' },
  { title: 'Fat Intake', value: '54g', target: '72g', tone: 'rose' },
];

export const dietPlans = [
  {
    category: 'Weight Loss',
    meals: ['Greek yogurt bowl', 'Grilled chicken salad', 'Lentil soup', 'Cottage cheese snack'],
    calories: 1850,
    protein: 155,
    carbs: 170,
    fats: 55,
  },
  {
    category: 'Muscle Gain',
    meals: ['Oats and eggs', 'Rice chicken bowl', 'Whey smoothie', 'Salmon and potatoes'],
    calories: 2850,
    protein: 205,
    carbs: 330,
    fats: 80,
  },
  {
    category: 'Weight Gain',
    meals: ['Peanut butter toast', 'Beef burrito bowl', 'Trail mix', 'Pasta with turkey'],
    calories: 3200,
    protein: 190,
    carbs: 395,
    fats: 95,
  },
  {
    category: 'Maintenance',
    meals: ['Egg wrap', 'Paneer rice bowl', 'Fruit and nuts', 'Chicken stir fry'],
    calories: 2350,
    protein: 170,
    carbs: 255,
    fats: 72,
  },
];

export const foodSuggestions = [
  { id: 1, name: 'Chicken Breast', serving: '150g', calories: 248, protein: 46, carbs: 0, fat: 5 },
  { id: 2, name: 'Brown Rice', serving: '1 cup', calories: 216, protein: 5, carbs: 45, fat: 2 },
  { id: 3, name: 'Greek Yogurt', serving: '200g', calories: 146, protein: 20, carbs: 8, fat: 4 },
  { id: 4, name: 'Banana', serving: '1 medium', calories: 105, protein: 1, carbs: 27, fat: 0 },
];

export const calorieTracker = {
  dailyGoal: 2500,
  consumedCalories: 1860,
  remainingCalories: 640,
};

export const measurements = {
  chest: 102,
  waist: 84,
  arms: 36,
  legs: 59,
  bodyFat: 19.9,
};

export const trainers = [
  {
    id: 1,
    name: 'Ryan Brooks',
    photo: 'https://images.unsplash.com/photo-1594381898411-846e7d193883?auto=format&fit=crop&w=800&q=80',
    specialization: 'Strength and Hypertrophy',
    experience: '8 years',
    bio: 'Strength coach focused on progressive overload, technique audits, and sustainable muscle gain.',
    certifications: ['NASM CPT', 'Precision Nutrition L1', 'Kettlebell Coach'],
    availableSlots: ['2026-07-01 07:30', '2026-07-02 18:00', '2026-07-04 08:30'],
  },
  {
    id: 2,
    name: 'Elena Torres',
    photo: 'https://images.unsplash.com/photo-1609899537878-88d5ba429bdb?auto=format&fit=crop&w=800&q=80',
    specialization: 'Mobility and Conditioning',
    experience: '6 years',
    bio: 'Builds conditioning plans for busy members while improving joint range and movement quality.',
    certifications: ['ACE CPT', 'FRC Mobility Specialist'],
    availableSlots: ['2026-07-03 18:00', '2026-07-05 10:30', '2026-07-06 17:30'],
  },
  {
    id: 3,
    name: 'Noah Patel',
    photo: 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=800&q=80',
    specialization: 'Fat Loss and Core Training',
    experience: '5 years',
    bio: 'Combines habit coaching, metabolic conditioning, and core stability progressions.',
    certifications: ['ISSA CPT', 'TRX Suspension Training'],
    availableSlots: ['2026-07-05 09:00', '2026-07-07 19:00', '2026-07-08 07:00'],
  },
];

export const sessionHistory = [
  { id: 1, trainerName: 'Ryan Brooks', date: '2026-06-24', status: 'Completed' },
  { id: 2, trainerName: 'Elena Torres', date: '2026-06-17', status: 'Completed' },
  { id: 3, trainerName: 'Noah Patel', date: '2026-06-10', status: 'Cancelled' },
];

export const attendanceHistory = [
  { id: 1, date: '2026-06-29', checkInTime: '06:18 PM', checkOutTime: '07:29 PM' },
  { id: 2, date: '2026-06-27', checkInTime: '07:04 AM', checkOutTime: '08:08 AM' },
  { id: 3, date: '2026-06-25', checkInTime: '06:42 PM', checkOutTime: '07:36 PM' },
  { id: 4, date: '2026-06-23', checkInTime: '07:15 AM', checkOutTime: '08:02 AM' },
];

export const monthlyAttendance = [
  { label: 'Jan', visits: 12 },
  { label: 'Feb', visits: 15 },
  { label: 'Mar', visits: 17 },
  { label: 'Apr', visits: 14 },
  { label: 'May', visits: 19 },
  { label: 'Jun', visits: 18 },
];

export const weeklyAttendance = [
  { label: 'Mon', visits: 48 },
  { label: 'Tue', visits: 52 },
  { label: 'Wed', visits: 46 },
  { label: 'Thu', visits: 55 },
  { label: 'Fri', visits: 61 },
  { label: 'Sat', visits: 67 },
  { label: 'Sun', visits: 33 },
];

export const adminCards = [
  { title: 'Total Members', value: '1,284', detail: '+84 this month', tone: 'sky' },
  { title: 'Active Members', value: '1,036', detail: '80.6% active', tone: 'emerald' },
  { title: 'Trainers', value: '18', detail: '4 specializations', tone: 'violet' },
  { title: 'Revenue', value: '$94.2k', detail: '+18.4% month over month', tone: 'amber' },
];

export const membershipGrowth = [
  { label: 'Jan', members: 820 },
  { label: 'Feb', members: 902 },
  { label: 'Mar', members: 978 },
  { label: 'Apr', members: 1046 },
  { label: 'May', members: 1170 },
  { label: 'Jun', members: 1284 },
];

export const revenueAnalytics = [
  { label: 'Jan', revenue: 62000 },
  { label: 'Feb', revenue: 68500 },
  { label: 'Mar', revenue: 71600 },
  { label: 'Apr', revenue: 80500 },
  { label: 'May', revenue: 84600 },
  { label: 'Jun', revenue: 94200 },
];

export const adminAttendanceAnalytics = [
  { label: 'Morning', visits: 420 },
  { label: 'Afternoon', visits: 315 },
  { label: 'Evening', visits: 618 },
  { label: 'Weekend', visits: 284 },
];

