import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
};

export const userApi = {
  getMe: () => api.get('/users/me'),
};

export const stationApi = {
  search: (keyword = '', page = 0, size = 20) =>
    api.get('/stations', { params: { keyword, page, size } }),
  getByCode: (code) => api.get(`/stations/${code}`),
  create: (data) => api.post('/admin/stations', data),
};

export const trainApi = {
  getAll: () => api.get('/trains'),
  getByNumber: (number) => api.get(`/trains/${number}`),
  create: (data) => api.post('/admin/trains', data),
  search: (from, to, date) =>
    api.get('/trains/search', { params: { from, to, date } }),
};

export const routeApi = {
  getByTrain: (trainId) => api.get(`/trains/${trainId}/routes`),
  create: (data) => api.post('/admin/routes', data),
};

export const scheduleApi = {
  getByTrain: (trainId) => api.get(`/trains/${trainId}/schedules`),
  create: (data) => api.post('/admin/schedules', data),
};

export const availabilityApi = {
  check: (trainRunId, coachType, fromStationId, toStationId) =>
    api.get('/availability', { params: { trainRunId, coachType, fromStationId, toStationId } }),
};

export const bookingApi = {
  create: (data) => api.post('/bookings', data),
  getByPnr: (pnr) => api.get(`/bookings/${pnr}`),
  getMy: (page = 0, size = 10) =>
    api.get('/bookings/my', { params: { page, size } }),
  cancel: (pnr, reason) =>
    api.post(`/bookings/${pnr}/cancel`, { cancellationReason: reason }),
};

export const pnrApi = {
  check: (pnr) => api.get(`/pnr/${pnr}`),
};

export const paymentApi = {
  initiate: (data) => api.post('/payments/initiate', data),
  getByBooking: (bookingId) => api.get(`/payments/booking/${bookingId}`),
  retry: (paymentId) => api.post(`/payments/${paymentId}/retry`),
};

export const adminApi = {
  generateTrainRuns: (data) => api.post('/admin/train-runs/generate', data),
  reindexSearch: () => api.post('/admin/search/reindex'),
  triggerJob: (jobName) => api.post(`/admin/scheduler/trigger/${jobName}`),
};

export default api;
