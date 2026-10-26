function resolveApiBase() {
  const envUrl = (import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '').trim();
  if (!envUrl) {
    return '/api';
  }
  const clean = envUrl.replace(/\/+$/, '');
  return clean.endsWith('/api') ? clean : `${clean}/api`;
}

const API_BASE = resolveApiBase();

export function getFacultyToken() {
  return localStorage.getItem('quizlive_faculty_token');
}

export function getFacultyUser() {
  try {
    const raw = localStorage.getItem('quizlive_faculty_user');
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

export function setFacultySession(token, user) {
  localStorage.setItem('quizlive_faculty_token', token);
  localStorage.setItem('quizlive_faculty_user', JSON.stringify(user));
}

export function clearFacultySession() {
  localStorage.removeItem('quizlive_faculty_token');
  localStorage.removeItem('quizlive_faculty_user');
}

function getHeaders(isJson = true) {
  const headers = {};
  if (isJson) {
    headers['Content-Type'] = 'application/json';
  }
  const token = getFacultyToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

async function parseErrorResponse(res, defaultMsg) {
  try {
    const contentType = res.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      const data = await res.json();
      return data.message || data.error || data.detail || defaultMsg;
    }
    const text = await res.text();
    if (res.status === 404) {
      return 'Backend service endpoint not found (HTTP 404). Please ensure the backend is deployed, running, and VITE_API_BASE_URL is configured.';
    }
    if (res.status === 502 || res.status === 503 || res.status === 504) {
      return `Backend server is temporarily unavailable (HTTP ${res.status}). Free-tier instances (e.g. Render) spin down when inactive and take ~30-50s to wake up. Please retry in a moment.`;
    }
    if (text && text.length < 200 && !text.includes('<!DOCTYPE') && !text.includes('<html')) {
      return text;
    }
    return `${defaultMsg} (HTTP ${res.status})`;
  } catch (e) {
    if (res.status === 404) {
      return 'Backend service endpoint not found (HTTP 404). Please ensure the backend is running and VITE_API_BASE_URL is configured.';
    }
    return `${defaultMsg} (HTTP ${res.status})`;
  }
}

function handleNetworkError(err, actionName) {
  if (err.name === 'TypeError' || (err.message && err.message.toLowerCase().includes('fetch'))) {
    return new Error(`Unable to connect to backend server for ${actionName}. Please verify the backend is online and CORS is configured.`);
  }
  return err;
}

export async function loginFaculty(identifier, password) {
  const isEmail = identifier && identifier.includes('@');
  const payload = isEmail ? { email: identifier.trim(), password } : { username: identifier.trim(), password };
  let res;
  try {
    res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  } catch (err) {
    throw handleNetworkError(err, 'faculty login');
  }

  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Invalid username or password');
    throw new Error(errMsg);
  }

  const data = await res.json();
  setFacultySession(data.token, {
    username: data.username,
    email: data.email,
    name: data.name,
    role: data.role,
  });
  return data;
}

export async function registerFaculty({ name, email, password }) {
  let res;
  try {
    res = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name.trim(), email: email.trim().toLowerCase(), password }),
    });
  } catch (err) {
    throw handleNetworkError(err, 'faculty registration');
  }

  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Registration failed');
    throw new Error(errMsg);
  }

  const data = await res.json();
  setFacultySession(data.token, {
    username: data.username,
    email: data.email,
    name: data.name,
    role: data.role,
  });
  return data;
}

export async function logoutFaculty() {
  const token = getFacultyToken();
  if (token) {
    try {
      await fetch(`${API_BASE}/auth/logout`, {
        method: 'POST',
        headers: getHeaders(false),
      });
    } catch (e) {
      // ignore
    }
  }
  clearFacultySession();
}

export async function checkFacultySession() {
  const token = getFacultyToken();
  if (!token) return null;
  try {
    const res = await fetch(`${API_BASE}/auth/me`, {
      headers: getHeaders(false),
    });
    if (!res.ok) {
      clearFacultySession();
      return null;
    }
    return res.json();
  } catch (e) {
    return null;
  }
}

export async function fetchQuizzes() {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes`, {
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'fetching quizzes');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to fetch quizzes');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function getQuizById(id) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${id}`, {
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'fetching quiz details');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to fetch quiz');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function getQuizPreview(id) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${id}/preview`, {
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'fetching examination preview');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to fetch examination preview');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function createQuiz(quiz) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(quiz),
    });
  } catch (err) {
    throw handleNetworkError(err, 'creating quiz');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to create quiz');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function saveDraft(quiz) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/draft`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(quiz),
    });
  } catch (err) {
    throw handleNetworkError(err, 'saving quiz draft');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to save draft');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function updateQuiz(id, quiz) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${id}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(quiz),
    });
  } catch (err) {
    throw handleNetworkError(err, 'updating quiz');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to update quiz');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function duplicateQuestion(quizId, questionId) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${quizId}/questions/${questionId}/duplicate`, {
      method: 'POST',
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'duplicating question');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to duplicate question');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function deleteQuestion(quizId, questionId) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${quizId}/questions/${questionId}`, {
      method: 'DELETE',
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'deleting question');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to delete question');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function reorderQuestions(quizId, questionIds) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${quizId}/questions/reorder`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ questionIds }),
    });
  } catch (err) {
    throw handleNetworkError(err, 'reordering questions');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to reorder questions');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function deleteQuiz(id) {
  let res;
  try {
    res = await fetch(`${API_BASE}/quizzes/${id}`, {
      method: 'DELETE',
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'deleting examination');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to delete examination');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function createSession(quizId) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/create`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ quizId }),
    });
  } catch (err) {
    throw handleNetworkError(err, 'creating live session');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to create live session');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function joinSession({ sessionPin, nickname, candidateId }) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionPin, nickname, candidateId }),
    });
  } catch (err) {
    throw handleNetworkError(err, 'joining session');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to join session');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function getHostState(pin) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${pin}/host`, {
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'fetching host state');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to fetch host state');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function getCandidateState(pin, candidateId) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${pin}/candidate?candidateId=${encodeURIComponent(candidateId || '')}`);
  } catch (err) {
    throw handleNetworkError(err, 'fetching candidate state');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to fetch candidate state');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function startQuestion(pin, questionIndex = 0) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${pin}/start-question`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ questionIndex }),
    });
  } catch (err) {
    throw handleNetworkError(err, 'starting question');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to start question');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function submitAnswer({ sessionPin, candidateId, questionId, optionLabel }) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${sessionPin}/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionPin, candidateId, questionId, optionLabel }),
    });
  } catch (err) {
    throw handleNetworkError(err, 'submitting answer');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to submit answer');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function revealAnswer(pin) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${pin}/reveal`, {
      method: 'POST',
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'revealing answer');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to reveal answer');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function nextQuestion(pin) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${pin}/next`, {
      method: 'POST',
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'advancing question');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to advance question');
    throw new Error(errMsg);
  }
  return res.json();
}

export async function endQuiz(pin) {
  let res;
  try {
    res = await fetch(`${API_BASE}/sessions/${pin}/end`, {
      method: 'POST',
      headers: getHeaders(false),
    });
  } catch (err) {
    throw handleNetworkError(err, 'ending quiz');
  }
  if (!res.ok) {
    const errMsg = await parseErrorResponse(res, 'Failed to end quiz');
    throw new Error(errMsg);
  }
  return res.json();
}
