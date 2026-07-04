const TOKEN_KEY = 'scheduler_jwt';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, { ...options, headers });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      // ignore parse errors
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  const text = await response.text();
  // Some error responses may be plain text (e.g. "Username already exists").
  // Try parsing JSON, but fall back to returning raw text when parsing fails.
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (parseErr) {
    // Normalize plain-text responses into a JSON object for consistent handling
    return { message: text };
  }
}

export const api = {
  register: (username, password) =>
    request('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  login: (username, password) =>
    request('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  getProjects: () => request('/api/v1/projects'),

  getDefaultProject: () => request('/api/v1/projects/default'),

  createProject: (name) =>
    request('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify({ name }),
    }),

  getQueues: () => request('/api/v1/queues'),

  createQueue: (payload) =>
    request('/api/v1/queues', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  pauseQueue: (id) => request(`/api/v1/queues/${id}/pause`, { method: 'POST' }),

  resumeQueue: (id) => request(`/api/v1/queues/${id}/resume`, { method: 'POST' }),

  getQueueHealth: () => request('/api/v1/dashboard/queues/health'),

  getJobs: (params) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') query.set(k, v);
    });
    return request(`/api/v1/jobs?${query}`);
  },

  createJob: (queueId, payload) =>
    request(`/api/v1/jobs/queue/${queueId}`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getDlq: (page = 0, size = 20) =>
    request(`/api/v1/jobs/dlq?page=${page}&size=${size}`),

  retryDlq: (dlqEntryId) =>
    request(`/api/v1/jobs/dlq/${dlqEntryId}/retry`, { method: 'POST' }),

  getMetrics: () => request('/api/v1/jobs/metrics'),

  getThroughput: (hours = 24) =>
    request(`/api/v1/dashboard/throughput?hours=${hours}`),

  getWorkerStatuses: () => request('/api/v1/workers/status'),
};
