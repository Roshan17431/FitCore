import axios from 'axios';

const DEFAULT_API_URL = 'http://localhost:8080/api';
const configuredApiUrl = import.meta.env.VITE_API_URL || DEFAULT_API_URL;
const apiBaseURL = configuredApiUrl.replace(/\/+$/, '');

const getRequestUrl = (config = {}) => {
  const baseURL = config.baseURL || apiBaseURL;
  const url = config.url || '';

  if (/^https?:\/\//i.test(url)) {
    return url;
  }

  return `${baseURL}${url.startsWith('/') ? '' : '/'}${url}`;
};

export const formatApiError = (error, fallbackMessage = 'Request failed.') => {
  const requestUrl = getRequestUrl(error.config);

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
    const reason = error.code || error.message || 'No response received';
    return `Connection failure (${reason}) while calling ${requestUrl}. Confirm the backend is running and CORS allows this frontend origin.`;
  }

  return error.message || fallbackMessage;
};

const api = axios.create({
  baseURL: apiBaseURL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const isAuthEndpoint = config.url?.startsWith('/auth/');
    if (token && !isAuthEndpoint) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint = error.config?.url?.startsWith('/auth/');
    if (error.response && error.response.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
