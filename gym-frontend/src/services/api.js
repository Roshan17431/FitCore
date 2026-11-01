import axios from 'axios';

const normalizeBackendBaseURL = (value = '') => {
  const trimmedUrl = value.trim().replace(/\/+$/, '');
  return trimmedUrl.endsWith('/api') ? trimmedUrl.slice(0, -4) : trimmedUrl;
};
const backendBaseURL = normalizeBackendBaseURL(import.meta.env.VITE_API_BASE_URL);
const apiBaseURL = backendBaseURL ? `${backendBaseURL}/api` : '';
const apiConfigError = backendBaseURL
  ? ''
  : 'Missing VITE_API_BASE_URL. Set it to the backend origin, for example: https://fitcore-posv.onrender.com';

export const formatApiError = (error, fallbackMessage = 'Request failed.') => {
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return 'The server took too long to respond. It may be starting up. Please try again shortly.';
  }

  if (error.response) {
    const { status, data } = error.response;
    const validationMessages = data?.validationErrors
      ? Object.entries(data.validationErrors).map(([field, message]) => `${field}: ${message}`)
      : [];
    const backendMessage = validationMessages.length > 0
      ? validationMessages.join(', ')
      : data?.message || data?.error || fallbackMessage;

    return `HTTP ${status}: ${backendMessage}`;
  }

  if (error.request) {
    return 'Unable to reach FitCore. Please try again shortly.';
  }

  return error.message || fallbackMessage;
};

const api = axios.create({
  baseURL: apiBaseURL,
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
});

const publicAuth = new Set(['/auth/login', '/auth/register', '/auth/register-owner', '/auth/forgot-password']);
const currentToken = () => localStorage.getItem('token') || sessionStorage.getItem('token');
api.interceptors.request.use(config => {
  if (apiConfigError) return Promise.reject(new Error(apiConfigError));
  if (!publicAuth.has(config.url)) {
    config.fitcoreToken = currentToken();
    if (config.fitcoreToken) config.headers.Authorization = `Bearer ${config.fitcoreToken}`;
  }
  return config;
});
api.interceptors.response.use(response => {
  if (response.config.fitcoreToken && response.config.fitcoreToken !== currentToken()) return Promise.reject(new axios.CanceledError('Session changed'));
  return response;
}, error => {
  const config = error.config || {};
  const unavailable = error.response?.status === 403 && error.response?.data?.message === 'GYM_UNAVAILABLE';
  if (config.fitcoreToken && config.fitcoreToken === currentToken() && (error.response?.status === 401 || unavailable)) {
    for (const storage of [localStorage, sessionStorage]) { storage.removeItem('token'); storage.removeItem('user'); }
    window.dispatchEvent(new CustomEvent('fitcore:session-ended', { detail: unavailable ? 'Your gym is suspended. Contact your gym owner.' : 'Your session expired. Please sign in again.' }));
  }
  return Promise.reject(error);
});

const unwrap = (response) => response.data;

const labelFromEnum = (value = '') =>
  String(value)
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');

const toDateInputValue = (date) => new Date(date).toISOString().slice(0, 10);

const groupExercises = (exercises = []) => {
  const grouped = exercises.reduce((groups, exercise) => {
    const category = exercise.category || 'General';
    if (!groups[category]) {
      groups[category] = { category, exercises: [] };
    }
    groups[category].exercises.push({
      id: exercise.id,
      name: exercise.name,
      image: exercise.imageUrl,
      description: exercise.description,
      sets: exercise.recommendedSets,
      reps: exercise.recommendedReps,
      difficulty: labelFromEnum(exercise.difficulty),
    });
    return groups;
  }, {});

  return Object.values(grouped);
};

const mapMembershipPlan = (plan) => ({
  ...plan,
  price: Number(plan.price),
  duration: `${plan.durationMonths} ${plan.durationMonths === 1 ? 'month' : 'months'}`,
});

const mapMembership = (membership) => {
  if (!membership) return null;
  const endDate = membership.endDate || membership.expiryDate;
  const plan = typeof membership.plan === 'object' && membership.plan ? membership.plan : null;
  const planName = plan?.name || membership.planName || (typeof membership.plan === 'string' ? membership.plan : 'Membership');

  return {
    ...membership,
    plan: planName,
    planName,
    planId: plan?.id ?? membership.planId,
    planPrice: Number(plan?.price ?? membership.planPrice ?? 0),
    durationMonths: plan?.durationMonths ?? membership.durationMonths,
    features: plan?.features || membership.features || [],
    startDate: membership.startDate,
    endDate,
    expiryDate: endDate,
    remainingDays: membership.remainingDays ?? 0,
    status: labelFromEnum(membership.effectiveStatus || membership.status),
  };
};

const mapWorkoutLog = (log) => ({
  id: log.id,
  workoutDate: log.date,
  duration: `${log.durationMinutes || 0} min`,
  completedExercises: log.exercises?.length || 0,
  notes: log.notes,
});

const mapFoodLog = (log) => ({
  id: log.id,
  name: log.mealName,
  mealName: log.mealName,
  mealType: log.mealType,
  calories: log.calories || 0,
  protein: log.protein || 0,
  carbs: log.carbs || 0,
  fat: log.fat || 0,
  serving: labelFromEnum(log.mealType),
  logDate: log.logDate,
  logTime: log.logTime,
});

const mapAttendanceLog = (log) => ({
  id: log.id,
  date: log.checkInDate,
  checkInTime: log.checkInTime ? new Date(log.checkInTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--',
  checkOutTime: log.checkOutTime ? new Date(log.checkOutTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--',
  checkedIn: Boolean(log.checkInTime && !log.checkOutTime),
  raw: log,
});

const buildAttendanceStats = (logs = []) => {
  const today = new Date();
  const week = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (6 - index));
    const key = toDateInputValue(date);
    return {
      label: date.toLocaleDateString('en-US', { weekday: 'short' }),
      visits: logs.filter((log) => log.checkInDate === key).length,
    };
  });

  const months = Array.from({ length: 6 }, (_, index) => {
    const date = new Date(today.getFullYear(), today.getMonth() - (5 - index), 1);
    const month = date.getMonth();
    const year = date.getFullYear();
    return {
      label: date.toLocaleDateString('en-US', { month: 'short' }),
      visits: logs.filter((log) => {
        const checkIn = new Date(log.checkInDate);
        return checkIn.getMonth() === month && checkIn.getFullYear() === year;
      }).length,
    };
  });

  return { weeklyAttendance: week, monthlyAttendance: months };
};

const mapTrainer = (trainer) => ({
  id: trainer.id,
  user: trainer.user,
  enabled: trainer.user?.enabled !== false,
  name: trainer.user?.fullName || 'Trainer',
  email: trainer.user?.email,
  specialization: trainer.specialization,
  experience: `${trainer.experienceYears || 0} years`,
  experienceYears: trainer.experienceYears || 0,
  bio: trainer.bio,
  photo: trainer.photoUrl,
  certifications: trainer.certifications || [],
  availableSlots: trainer.availableSlots || [],
});

const mapSession = (session) => ({
  id: session.id,
  trainerName: session.trainer?.user?.fullName || session.trainer?.name || 'Trainer',
  date: session.sessionDate,
  status: labelFromEnum(session.status),
  focus: session.trainer?.specialization || 'Training Session',
  trainer: session.trainer?.user?.fullName || 'Trainer',
  time: session.startTime?.slice(0, 5) || '--',
});

const buildWorkoutCompletion = (logs = []) => {
  const today = new Date();
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (6 - index));
    const key = toDateInputValue(date);
    return {
      label: date.toLocaleDateString('en-US', { weekday: 'short' }),
      completed: logs.some((log) => log.date === key) ? 100 : 0,
    };
  });
};

export const authService = {
  login: (email, password) => api.post('/auth/login', { email, password }).then(unwrap),
  register: ({ fullName, email, password, fitnessGoal, invitationToken }) =>
    api.post('/auth/register', { fullName, email, password, fitnessGoal, invitationToken }).then(unwrap),
  registerOwner: ({ fullName, email, password, gymName, slug }) => api.post('/auth/register-owner', { fullName, email, password, gymName, slug }).then(unwrap),
  me: () => api.get('/auth/me').then(unwrap),
  resetPassword: (email) => api.post('/auth/forgot-password', { email }).then(unwrap),
};

export const dashboardService = {
  getOverview: (userId) => api.get(`/dashboard/${userId}`).then(unwrap),
};

export const notificationService = {
  getNotifications: (userId) => api.get(`/notifications/${userId}`).then(unwrap),
};

export const membershipService = {
  getPlans: () => api.get('/memberships/plans').then(unwrap).then((plans) => plans.map(mapMembershipPlan)),
  getActive: (userId) =>
    api.get(`/memberships/user/${userId}/active`)
      .then((response) => response.status === 204 ? null : mapMembership(response.data))
      .catch((error) => {
        if (error.response?.status === 204) return null;
        throw error;
      }),
  getHistory: (userId) =>
    api.get(`/memberships/user/${userId}`).then(unwrap).then((memberships) =>
      memberships.map((membership) => ({
        ...mapMembership(membership),
        purchaseDate: membership.startDate,
      })),
    ),
  subscribe: (userId, planId) => api.post(`/memberships/subscribe/${userId}/${planId}`).then(unwrap),
  renew: (userId, membershipId, expectedEndDate) =>
    api.post(`/memberships/renew/${userId}/${membershipId}`, { expectedEndDate }).then(unwrap),
  upgrade: (userId, planId, membershipId, expectedEndDate) =>
    api.post(`/memberships/upgrade/${userId}/${planId}`, { membershipId, expectedEndDate }).then(unwrap),
};

export const workoutService = {
  getLibrary: () => api.get('/workouts/exercises').then(unwrap).then(groupExercises),
  getPlans: () => api.get('/workouts/plans').then(unwrap),
  getDailyTracker: () =>
    api.get('/workouts/exercises').then(unwrap).then((exercises) =>
      exercises.slice(0, 5).map((exercise) => ({
        id: exercise.id,
        name: exercise.name,
        targetSets: exercise.recommendedSets || 0,
        targetReps: exercise.recommendedReps || '0',
        sets: 0,
        reps: 0,
        complete: false,
      })),
    ),
  getHistory: (userId) => api.get(`/workouts/logs/${userId}`).then(unwrap).then((logs) => logs.map(mapWorkoutLog)),
  logWorkout: (userId, payload) => api.post(`/workouts/logs/${userId}`, payload).then(unwrap),
};

export const nutritionService = {
  getDashboard: (userId) => api.get(`/nutrition/dashboard/${userId}`).then(unwrap),
  getDietPlans: () => api.get('/nutrition/plans').then(unwrap),
  getFoodLogs: (userId, date = toDateInputValue(new Date())) =>
    api.get(`/nutrition/logs/${userId}`, { params: { date } }).then(unwrap).then((logs) => logs.map(mapFoodLog)),
  logFood: (userId, food) =>
    api.post(`/nutrition/log/${userId}`, {
      userId,
      mealName: food.name || food.mealName,
      mealType: food.mealType || 'SNACK',
      calories: Number(food.calories) || 0,
      protein: Number(food.protein) || 0,
      carbs: Number(food.carbs) || 0,
      fat: Number(food.fat) || 0,
      logDate: food.logDate || toDateInputValue(new Date()),
    }).then(unwrap).then(mapFoodLog),
};

export const progressService = {
  getProgress: async (userId) => {
    const [progressResponse, workoutResponse] = await Promise.all([
      api.get(`/tracking/progress/${userId}`),
      api.get(`/workouts/logs/${userId}`),
    ]);
    const progress = progressResponse.data || [];
    const workouts = workoutResponse.data || [];
    const sortedProgress = [...progress].sort((a, b) => new Date(a.logDate) - new Date(b.logDate));
    const latest = sortedProgress.at(-1);

    return {
      weightProgress: sortedProgress.map((item) => ({
        id: item.id,
        label: new Date(item.logDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        date: item.logDate,
        weight: item.weight,
        bodyFat: item.bodyFatPercentage,
      })),
      measurements: {
        chest: latest?.chest || 0,
        waist: latest?.waist || 0,
        arms: latest?.arms || 0,
        legs: latest?.legs || 0,
        bodyFat: latest?.bodyFatPercentage || 0,
      },
      workoutCompletion: buildWorkoutCompletion(workouts),
    };
  },
  addWeight: (userId, weight) => api.post(`/tracking/progress/${userId}`, { userId, weight }).then(unwrap),
};

export const trainerService = {
  getTrainers: () => api.get('/trainers').then(unwrap).then((trainers) => trainers.map(mapTrainer)),
  getSessions: (userId) =>
    api.get(`/trainers/sessions/${userId}`).then(unwrap).then((sessions) => {
      const mapped = sessions.map(mapSession);
      return {
        upcomingSessions: mapped.filter((session) => session.status === 'Scheduled'),
        sessionHistory: mapped,
      };
    }),
  bookSession: (memberId, trainerId, payload) => {
    // Session metadata is never an ownership selector in a business request.
    const person = (user) => user && ({ id: user.id, fullName: user.fullName, email: user.email, role: user.role });
    const body = {
      member: person(payload.member),
      trainer: { id: Number(trainerId), user: person(payload.trainer?.user), specialization: payload.trainer?.specialization },
      sessionDate: payload.sessionDate,
      startTime: payload.startTime,
      endTime: payload.endTime,
    };
    return api.post(`/trainers/book/${memberId}/${trainerId}`, body).then(unwrap);
  },
};

export const attendanceService = {
  getAttendance: async (userId) => {
    const logs = await api.get(`/tracking/attendance/${userId}`).then(unwrap);
    const mapped = logs.map(mapAttendanceLog);
    return {
      attendanceHistory: mapped,
      currentSession: mapped.find((log) => log.checkedIn) || null,
      ...buildAttendanceStats(logs),
    };
  },
  checkIn: (userId) => api.post(`/tracking/attendance/check-in/${userId}`).then(unwrap).then(mapAttendanceLog),
  checkOut: (attendanceId) => api.put(`/tracking/attendance/check-out/${attendanceId}`).then(unwrap).then(mapAttendanceLog),
};

export const adminService = {
  getDashboard: () => api.get('/admin/dashboard').then(unwrap).then((data) => ({
    ...data,
    trainers: data.trainers.map(mapTrainer),
    membershipPlans: data.membershipPlans.map(mapMembershipPlan),
    attendanceHistory: data.attendanceHistory.map(mapAttendanceLog),
  })),
  updateUser: (userId, payload) => api.put(`/admin/users/${userId}`, payload).then(unwrap),
  deleteUser: (userId) => api.delete(`/admin/users/${userId}`).then(unwrap),
  createTrainer: (payload) => api.post('/admin/trainers', payload).then(unwrap).then(mapTrainer),
  updateTrainer: (trainerId, payload) => api.put(`/admin/trainers/${trainerId}`, payload).then(unwrap).then(mapTrainer),
  deleteTrainer: (trainerId) => api.delete(`/admin/trainers/${trainerId}`).then(unwrap),
  createMembershipPlan: (payload) => api.post('/admin/membership-plans', payload).then(unwrap).then(mapMembershipPlan),
  updateMembershipPlan: (planId, payload) => api.put(`/admin/membership-plans/${planId}`, payload).then(unwrap).then(mapMembershipPlan),
  deleteMembershipPlan: (planId) => api.delete(`/admin/membership-plans/${planId}`).then(unwrap),
};

export default api;

export const gymService = {
  get: () => api.get('/gym').then(unwrap),
  update: ({ name }) => api.patch('/gym', { name }).then(unwrap),
  invitations: () => api.get('/gym/invitations').then(unwrap),
  invite: ({ email, role }) => api.post('/gym/invitations', { email, role }).then(unwrap),
  revoke: (id) => api.delete(`/gym/invitations/${encodeURIComponent(id)}`).then(unwrap),
};
