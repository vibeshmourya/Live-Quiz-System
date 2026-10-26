const API_BASE = '/api';

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

export async function loginFaculty(identifier, password) {
  const isEmail = identifier && identifier.includes('@');
  const payload = isEmail ? { email: identifier.trim(), password } : { username: identifier.trim(), password };
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || 'Invalid username or password');
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
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: name.trim(), email: email.trim().toLowerCase(), password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || 'Registration failed');
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
  const res = await fetch(`${API_BASE}/quizzes`, {
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to fetch quizzes');
  return res.json();
}

export async function getQuizById(id) {
  const res = await fetch(`${API_BASE}/quizzes/${id}`, {
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to fetch quiz');
  return res.json();
}

export async function getQuizPreview(id) {
  const res = await fetch(`${API_BASE}/quizzes/${id}/preview`, {
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to fetch examination preview');
  return res.json();
}

export async function createQuiz(quiz) {
  const res = await fetch(`${API_BASE}/quizzes`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify(quiz),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to create quiz');
  }
  return res.json();
}

export async function saveDraft(quiz) {
  const res = await fetch(`${API_BASE}/quizzes/draft`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify(quiz),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to save draft');
  }
  return res.json();
}

export async function updateQuiz(id, quiz) {
  const res = await fetch(`${API_BASE}/quizzes/${id}`, {
    method: 'PUT',
    headers: getHeaders(),
    body: JSON.stringify(quiz),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to update quiz');
  }
  return res.json();
}

export async function duplicateQuestion(quizId, questionId) {
  const res = await fetch(`${API_BASE}/quizzes/${quizId}/questions/${questionId}/duplicate`, {
    method: 'POST',
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to duplicate question');
  return res.json();
}

export async function deleteQuestion(quizId, questionId) {
  const res = await fetch(`${API_BASE}/quizzes/${quizId}/questions/${questionId}`, {
    method: 'DELETE',
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to delete question');
  return res.json();
}

export async function reorderQuestions(quizId, questionIds) {
  const res = await fetch(`${API_BASE}/quizzes/${quizId}/questions/reorder`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify({ questionIds }),
  });
  if (!res.ok) throw new Error('Failed to reorder questions');
  return res.json();
}

export async function deleteQuiz(id) {
  const res = await fetch(`${API_BASE}/quizzes/${id}`, {
    method: 'DELETE',
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to delete examination');
  return res.json();
}

export async function createSession(quizId) {
  const res = await fetch(`${API_BASE}/sessions/create`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify({ quizId }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to create live session');
  }
  return res.json();
}

export async function joinSession({ sessionPin, nickname, candidateId }) {
  const res = await fetch(`${API_BASE}/sessions/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionPin, nickname, candidateId }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to join session');
  }
  return res.json();
}

export async function getHostState(pin) {
  const res = await fetch(`${API_BASE}/sessions/${pin}/host`, {
    headers: getHeaders(false),
  });
  if (!res.ok) throw new Error('Failed to fetch host state');
  return res.json();
}

export async function getCandidateState(pin, candidateId) {
  const res = await fetch(`${API_BASE}/sessions/${pin}/candidate?candidateId=${encodeURIComponent(candidateId || '')}`);
  if (!res.ok) throw new Error('Failed to fetch candidate state');
  return res.json();
}

export async function startQuestion(pin, questionIndex = 0) {
  const res = await fetch(`${API_BASE}/sessions/${pin}/start-question`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify({ questionIndex }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to start question');
  }
  return res.json();
}

export async function submitAnswer({ sessionPin, candidateId, questionId, optionLabel }) {
  const res = await fetch(`${API_BASE}/sessions/${sessionPin}/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionPin, candidateId, questionId, optionLabel }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to submit answer');
  }
  return res.json();
}

export async function revealAnswer(pin) {
  const res = await fetch(`${API_BASE}/sessions/${pin}/reveal`, {
    method: 'POST',
    headers: getHeaders(false),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to reveal answer');
  }
  return res.json();
}

export async function nextQuestion(pin) {
  const res = await fetch(`${API_BASE}/sessions/${pin}/next`, {
    method: 'POST',
    headers: getHeaders(false),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to advance question');
  }
  return res.json();
}

export async function endQuiz(pin) {
  const res = await fetch(`${API_BASE}/sessions/${pin}/end`, {
    method: 'POST',
    headers: getHeaders(false),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || 'Failed to end quiz');
  }
  return res.json();
}
