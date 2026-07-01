import {
  activeMembership,
  adminAttendanceAnalytics,
  adminCards,
  attendanceChart,
  attendanceHistory,
  calorieTracker,
  dashboardCards,
  dietPlans,
  foodSuggestions,
  membershipGrowth,
  membershipHistory,
  membershipPlans,
  measurements,
  monthlyAttendance,
  notifications,
  nutritionCards,
  recentActivities,
  revenueAnalytics,
  sessionHistory,
  trainers,
  upcomingSessions,
  users,
  weeklyAttendance,
  weightProgress,
  workoutCategories,
  workoutCompletion,
  workoutHistory,
  workoutPlans,
  dailyWorkout,
} from '../data/mockData';

const REGISTERED_USERS_KEY = 'fitcore_registered_users';
const VALID_DEMO_PASSWORDS = ['Fitcore@123', 'fitcore123', 'password123', 'Secure@123'];

const wait = (payload, ms = 220) =>
  new Promise((resolve) => {
    window.setTimeout(() => {
      resolve(structuredClone(payload));
    }, ms);
  });

const getRegisteredUsers = () => {
  try {
    return JSON.parse(localStorage.getItem(REGISTERED_USERS_KEY) || '[]');
  } catch {
    return [];
  }
};

const saveRegisteredUsers = (registeredUsers) => {
  localStorage.setItem(REGISTERED_USERS_KEY, JSON.stringify(registeredUsers));
};

const sanitizeUser = (user) => {
  const { password: _password, ...safeUser } = user;
  return safeUser;
};

const createToken = (user) => `mock-token-${user.id}-${Date.now()}`;

export const mockAuthService = {
  async login(email, password) {
    const normalizedEmail = email.trim().toLowerCase();
    const registeredUsers = getRegisteredUsers();
    const user = [...registeredUsers, ...users].find(
      (entry) => entry.email.toLowerCase() === normalizedEmail,
    );

    const passwordMatches =
      user?.password === password || (!user?.password && VALID_DEMO_PASSWORDS.includes(password));

    if (!user || !passwordMatches) {
      throw new Error('Invalid email or password.');
    }

    return wait({
      token: createToken(user),
      user: sanitizeUser(user),
    });
  },

  async register(userData) {
    const normalizedEmail = userData.email.trim().toLowerCase();
    const registeredUsers = getRegisteredUsers();
    const exists = [...registeredUsers, ...users].some(
      (entry) => entry.email.toLowerCase() === normalizedEmail,
    );

    if (exists) {
      throw new Error('An account with this email already exists.');
    }

    const user = {
      id: `u-${Date.now()}`,
      fullName: userData.fullName,
      email: normalizedEmail,
      password: userData.password,
      role: 'MEMBER',
      fitnessGoal: userData.fitnessGoal,
      status: 'Active',
      joinedAt: new Date().toISOString().slice(0, 10),
      membership: 'Basic',
      attendanceRate: 0,
    };

    saveRegisteredUsers([user, ...registeredUsers]);

    return wait({
      token: createToken(user),
      user: sanitizeUser(user),
    });
  },

  async resetPassword(email) {
    if (!email) {
      throw new Error('Email is required.');
    }

    return wait({ ok: true });
  },
};

export const dashboardService = {
  getOverview: () =>
    wait({
      dashboardCards,
      weightProgress,
      workoutCompletion,
      attendanceChart,
      upcomingSessions,
      recentActivities,
      notifications,
    }),
};

export const membershipService = {
  getPlans: () => wait(membershipPlans),
  getActive: () => wait(activeMembership),
  getHistory: () => wait(membershipHistory),
};

export const workoutService = {
  getLibrary: () => wait(workoutCategories),
  getPlans: () => wait(workoutPlans),
  getDailyTracker: () => wait(dailyWorkout),
  getHistory: () => wait(workoutHistory),
};

export const nutritionService = {
  getDashboard: () => wait({ nutritionCards, calorieTracker }),
  getDietPlans: () => wait(dietPlans),
  getFoodSuggestions: () => wait(foodSuggestions),
};

export const progressService = {
  getProgress: () => wait({ weightProgress, measurements, workoutCompletion }),
};

export const trainerService = {
  getTrainers: () => wait(trainers),
  getSessions: () => wait({ upcomingSessions, sessionHistory }),
};

export const attendanceService = {
  getAttendance: () =>
    wait({
      attendanceHistory,
      weeklyAttendance,
      monthlyAttendance,
    }),
};

export const adminService = {
  getDashboard: () =>
    wait({
      adminCards,
      membershipGrowth,
      revenueAnalytics,
      adminAttendanceAnalytics,
      users,
      trainers,
      membershipPlans,
      attendanceHistory,
    }),
};
