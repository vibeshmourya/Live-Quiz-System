import React, { useState, useEffect, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Tooltip,
} from 'chart.js';
import { Bar } from 'react-chartjs-2';
import {
  getHostState,
  startQuestion,
  revealAnswer,
  nextQuestion,
  endQuiz,
} from '../services/api';
import { connectSessionWebSocket } from '../services/websocket';

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip);

export default function HostLivePage({ pin, onEndQuiz, onNavigate }) {
  const [state, setState] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [timerRemaining, setTimerRemaining] = useState(0);
  const [copiedLink, setCopiedLink] = useState(false);

  const stateRef = useRef(null);
  stateRef.current = state;

  // Poll or initial fetch
  useEffect(() => {
    loadState();
  }, [pin]);

  async function loadState() {
    try {
      setLoading(true);
      const data = await getHostState(pin);
      setState(data);
    } catch (err) {
      setError(err.message || 'Failed to fetch session state');
    } finally {
      setLoading(false);
    }
  }

  // Real-time WebSocket connection
  useEffect(() => {
    const unsubscribe = connectSessionWebSocket(
      pin,
      // onEvent (session changes)
      (event) => {
        if (event.type === 'PARTICIPANT_JOINED') {
          setState((prev) =>
            prev ? { ...prev, participantCount: event.payload.participantCount } : prev
          );
        } else if (event.type === 'QUESTION_STARTED') {
          loadState();
        } else if (event.type === 'QUESTION_REVEALED') {
          setState((prev) =>
            prev
              ? {
                  ...prev,
                  status: 'QUESTION_REVEALED',
                  correctOptionLabel: event.payload.correctOptionLabel,
                  correctPercentage: event.payload.correctPercentage,
                  optionCounts: event.payload.counts,
                  submissionCount: event.payload.totalSubmissions,
                }
              : prev
          );
        } else if (event.type === 'SESSION_FINISHED') {
          setState((prev) =>
            prev
              ? {
                  ...prev,
                  status: 'FINISHED',
                  leaderboard: event.payload.leaderboard,
                }
              : prev
          );
        }
      },
      // onStats (live voting updates)
      (statsMsg) => {
        if (statsMsg.type === 'STATS_UPDATED') {
          setState((prev) =>
            prev
              ? {
                  ...prev,
                  optionCounts: statsMsg.payload.counts,
                  submissionCount: statsMsg.payload.totalSubmissions,
                  participantCount: statsMsg.payload.participantCount || prev.participantCount,
                }
              : prev
          );
        }
      }
    );

    return () => {
      unsubscribe();
    };
  }, [pin]);

  // Synchronized countdown timer
  useEffect(() => {
    if (!state || state.status !== 'QUESTION_ACTIVE' || !state.questionEndsAtEpochMs) {
      return;
    }

    function updateRemaining() {
      const now = Date.now();
      const diff = Math.max(0, Math.ceil((state.questionEndsAtEpochMs - now) / 1000));
      setTimerRemaining(diff);

      // Auto-reveal if timer expired
      if (diff === 0 && stateRef.current?.status === 'QUESTION_ACTIVE') {
        handleReveal();
      }
    }

    updateRemaining();
    const interval = setInterval(updateRemaining, 1000);
    return () => clearInterval(interval);
  }, [state?.questionEndsAtEpochMs, state?.status]);

  async function handleStartQuiz() {
    try {
      const updated = await startQuestion(pin, 0);
      setState(updated);
    } catch (err) {
      setError(err.message || 'Failed to start quiz');
    }
  }

  async function handleReveal() {
    try {
      const updated = await revealAnswer(pin);
      setState(updated);
    } catch (err) {
      setError(err.message || 'Failed to reveal answer');
    }
  }

  async function handleNextQuestion() {
    try {
      const updated = await nextQuestion(pin);
      setState(updated);
      if (updated.status === 'FINISHED') {
        onEndQuiz(updated);
      }
    } catch (err) {
      setError(err.message || 'Failed to move to next question');
    }
  }

  async function handleEndQuiz() {
    try {
      const updated = await endQuiz(pin);
      setState(updated);
      onEndQuiz(updated);
    } catch (err) {
      setError(err.message || 'Failed to end quiz');
    }
  }

  function getInitialBaseUrl() {
    try {
      const saved = localStorage.getItem('quizora_public_base_url');
      if (saved && /^https?:\/\//i.test(saved.trim())) {
        return saved.trim().replace(/\/+$/, '');
      }
    } catch (e) {
      // ignore
    }
    return window.location.origin;
  }

  function validateAndResolve(input, sessionPin) {
    if (!input || !input.trim()) {
      return { isValid: false, fullUrl: '', error: 'Join URL cannot be empty.' };
    }
    let raw = input.trim();
    if (!/^https?:\/\//i.test(raw)) {
      raw = 'https://' + raw;
    }
    try {
      const parsed = new URL(raw);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        return { isValid: false, fullUrl: '', error: 'Only HTTP and HTTPS URLs are supported.' };
      }
      if (!parsed.hostname || (!parsed.hostname.includes('.') && parsed.hostname !== 'localhost')) {
        return { isValid: false, fullUrl: '', error: 'Please enter a valid domain or public URL (e.g. ngrok).' };
      }
      const origin = parsed.origin;
      const fullUrl = `${origin}/join?pin=${sessionPin}`;
      return { isValid: true, fullUrl, origin, error: '' };
    } catch (e) {
      return { isValid: false, fullUrl: '', error: 'Invalid URL format.' };
    }
  }

  const [urlInput, setUrlInput] = useState(() => `${getInitialBaseUrl()}/join?pin=${pin}`);
  const [urlValidation, setUrlValidation] = useState(() => validateAndResolve(getInitialBaseUrl(), pin));

  useEffect(() => {
    const resolved = validateAndResolve(urlInput, pin);
    setUrlValidation(resolved);
  }, [pin]);

  function handleUrlChange(newVal) {
    setUrlInput(newVal);
    const resolved = validateAndResolve(newVal, pin);
    setUrlValidation(resolved);
    if (resolved.isValid) {
      try {
        localStorage.setItem('quizora_public_base_url', resolved.origin);
      } catch (e) {}
    }
  }

  function handleUrlBlur() {
    if (urlValidation.isValid) {
      setUrlInput(urlValidation.fullUrl);
    }
  }

  function handleResetUrl() {
    const defaultOrigin = window.location.origin;
    const defaultFull = `${defaultOrigin}/join?pin=${pin}`;
    setUrlInput(defaultFull);
    setUrlValidation({ isValid: true, fullUrl: defaultFull, origin: defaultOrigin, error: '' });
    try {
      localStorage.removeItem('quizora_public_base_url');
    } catch (e) {}
  }

  function copyJoinLink() {
    if (!urlValidation.isValid) return;
    navigator.clipboard.writeText(urlValidation.fullUrl);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  }

  if (loading && !state) {
    return (
      <main className="flex-1 flex items-center justify-center p-6">
        <p className="text-sm text-slate-500">Connecting to live session...</p>
      </main>
    );
  }

  if (error && !state) {
    return (
      <main className="flex-1 flex items-center justify-center p-6">
        <div className="max-w-md p-6 bg-red-50 border border-red-200 rounded-xl text-center">
          <p className="text-sm font-medium text-red-700">{error}</p>
          <button
            onClick={() => onNavigate('home')}
            className="mt-4 px-4 py-2 bg-white border border-red-200 text-xs font-semibold rounded-lg text-slate-700"
          >
            Back to Home
          </button>
        </div>
      </main>
    );
  }

  // 1. LOBBY STATE (WAITING)
  if (state.status === 'WAITING') {
    return (
      <main className="flex-1 max-w-4xl mx-auto px-4 py-8 flex flex-col justify-center">
        <div className="bg-white border border-slate-200 rounded-2xl p-8 shadow-xs text-center">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            {state.quizTitle}
          </span>
          <h1 className="text-xl font-bold text-slate-900 mt-1 mb-8">
            Live Quiz Lobby
          </h1>

          <div className="grid md:grid-cols-2 gap-8 items-center max-w-2xl mx-auto mb-8 text-left">
            {/* PIN & Instructions */}
            <div className="space-y-4">
              <div>
                <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">
                  Join with PIN
                </span>
                <div className="text-5xl font-mono font-extrabold tracking-wider text-indigo-600 mt-1">
                  {pin}
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-slate-500">Direct Join URL</span>
                  {urlValidation.origin && urlValidation.origin !== window.location.origin && (
                    <button
                      type="button"
                      onClick={handleResetUrl}
                      className="text-[11px] text-indigo-600 hover:text-indigo-800 underline cursor-pointer"
                      title="Reset URL back to local host"
                    >
                      Reset to localhost
                    </button>
                  )}
                </div>
                <div className="flex items-center gap-2 mt-1">
                  <input
                    id="host-direct-join-url-input"
                    type="text"
                    value={urlInput}
                    onChange={(e) => handleUrlChange(e.target.value)}
                    onBlur={handleUrlBlur}
                    placeholder="e.g. https://xyz.ngrok-free.app"
                    className={`flex-1 text-xs font-mono bg-white border px-3 py-2 rounded-md text-slate-800 focus:outline-none focus:ring-2 select-all ${
                      urlValidation.isValid
                        ? 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500'
                        : 'border-red-300 bg-red-50/40 text-red-800 focus:border-red-500 focus:ring-red-500'
                    }`}
                  />
                  <button
                    id="host-copy-url-btn"
                    onClick={copyJoinLink}
                    disabled={!urlValidation.isValid}
                    className="px-3 py-2 text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-md border border-slate-200 transition-colors shrink-0 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                  >
                    {copiedLink ? 'Copied!' : 'Copy'}
                  </button>
                </div>
                {urlValidation.isValid ? (
                  <p className="text-[11px] text-slate-400 mt-1">
                    Editable: Enter or paste your public ngrok URL to refresh the QR code for phones.
                  </p>
                ) : (
                  <p className="text-[11px] text-red-600 mt-1 font-medium">
                    {urlValidation.error}
                  </p>
                )}
              </div>

              <div className="pt-2">
                <span className="text-xs font-semibold text-slate-600">Connected Participants:</span>
                <div className="text-2xl font-bold text-slate-900 mt-0.5">
                  {state.participantCount} joined
                </div>
              </div>
            </div>

            {/* QR Code */}
            <div className="flex flex-col items-center justify-center p-6 bg-slate-50 rounded-xl border border-slate-200">
              {urlValidation.isValid && urlValidation.fullUrl ? (
                <QRCodeSVG value={urlValidation.fullUrl} size={180} level="M" />
              ) : (
                <div className="w-[180px] h-[180px] rounded-lg border-2 border-dashed border-slate-300 flex flex-col items-center justify-center p-3 text-center bg-slate-100/60">
                  <span className="text-xs font-semibold text-slate-500">QR Unavailable</span>
                  <span className="text-[11px] text-slate-400 mt-1">Enter a valid HTTP/HTTPS URL</span>
                </div>
              )}
              <span className="text-xs text-slate-500 mt-3 font-medium">
                Scan with phone to join
              </span>
            </div>
          </div>

          <div className="max-w-sm mx-auto">
            <button
              id="host-start-quiz-btn"
              onClick={handleStartQuiz}
              disabled={state.participantCount === 0}
              className="w-full py-3.5 px-6 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-xl text-base shadow-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {state.participantCount === 0 ? 'Waiting for Candidates to Join...' : 'Start Quiz Now'}
            </button>
          </div>
        </div>
      </main>
    );
  }

  // 2. ACTIVE OR REVEALED QUESTION STATE
  const currentQ = state.currentQuestion;
  const isRevealed = state.status === 'QUESTION_REVEALED';
  const isLastQuestion = state.currentQuestionIndex + 1 >= state.totalQuestions;

  // Chart Data preparation
  const counts = state.optionCounts || { A: 0, B: 0, C: 0, D: 0 };
  const chartLabels = ['A', 'B', 'C', 'D'];
  const chartDataValues = [counts['A'] || 0, counts['B'] || 0, counts['C'] || 0, counts['D'] || 0];

  // Bar colors
  const backgroundColors = chartLabels.map((lbl) => {
    if (isRevealed) {
      return lbl === state.correctOptionLabel ? '#10b981' : '#cbd5e1';
    }
    return '#6366f1';
  });

  const chartData = {
    labels: chartLabels,
    datasets: [
      {
        data: chartDataValues,
        backgroundColor: backgroundColors,
        borderRadius: 6,
        barThickness: 36,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 250 },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (context) => ` ${context.parsed.y} answers`,
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { font: { weight: 'bold', size: 13 }, color: '#475569' },
      },
      y: {
        beginAtZero: true,
        grid: { color: '#f1f5f9' },
        ticks: {
          stepSize: 1,
          precision: 0,
          color: '#94a3b8',
          font: { size: 11 },
        },
      },
    },
  };

  return (
    <main className="flex-1 max-w-4xl mx-auto px-4 py-6 w-full flex flex-col justify-between">
      {/* Top Status Bar: Question #, Timer, Connected Counts */}
      <div className="bg-white border border-slate-200 rounded-xl px-5 py-3.5 mb-5 flex items-center justify-between shadow-xs">
        <div className="flex items-center gap-3">
          <span className="text-xs font-bold uppercase tracking-wider text-indigo-700 bg-indigo-50 px-2.5 py-1 rounded">
            Question {state.currentQuestionIndex + 1} of {state.totalQuestions}
          </span>
          <span className="text-xs font-medium text-slate-500">
            {currentQ?.marks || 10} Marks
          </span>
        </div>

        {/* Live Timer */}
        <div className="flex items-center gap-2">
          <span className="text-xs uppercase tracking-wider font-semibold text-slate-500">
            Time Left:
          </span>
          <span
            className={`font-mono text-xl font-bold px-3 py-0.5 rounded-md ${
              timerRemaining <= 5 && !isRevealed
                ? 'bg-red-100 text-red-700 animate-pulse'
                : 'bg-slate-100 text-slate-900'
            }`}
          >
            {isRevealed ? '0s' : `${timerRemaining}s`}
          </span>
        </div>

        {/* Participant & Submission Counters */}
        <div className="flex items-center gap-4 text-xs font-medium text-slate-600">
          <div>
            <span>Submissions: </span>
            <span className="font-bold text-slate-900">
              {state.submissionCount} / {state.participantCount}
            </span>
          </div>
          <div className="w-px h-4 bg-slate-200" />
          <div>
            <span>Candidates: </span>
            <span className="font-bold text-slate-900">{state.participantCount}</span>
          </div>
        </div>
      </div>

      {/* Main Question & Live Analytics Layout */}
      <div className="grid md:grid-cols-12 gap-5 flex-1 mb-5">
        {/* Left Column: Question & Options (7 cols) */}
        <div className="md:col-span-7 bg-white border border-slate-200 rounded-xl p-6 shadow-xs flex flex-col justify-between">
          <div>
            <h2 className="text-xl font-bold text-slate-900 leading-snug mb-6">
              {currentQ?.questionText}
            </h2>

            <div className="space-y-3">
              {currentQ?.options?.map((opt) => {
                const isCorrect = isRevealed && opt.optionLabel === state.correctOptionLabel;
                return (
                  <div
                    key={opt.optionLabel}
                    className={`flex items-start gap-3 p-3.5 rounded-lg border transition-all ${
                      isCorrect
                        ? 'bg-emerald-50 border-emerald-500 ring-2 ring-emerald-500/20'
                        : isRevealed
                        ? 'bg-slate-50/60 border-slate-200 text-slate-500'
                        : 'bg-slate-50 border-slate-200 text-slate-800'
                    }`}
                  >
                    <span
                      className={`w-7 h-7 rounded flex items-center justify-center font-bold text-xs shrink-0 ${
                        isCorrect
                          ? 'bg-emerald-600 text-white'
                          : 'bg-white border border-slate-300 text-slate-700'
                      }`}
                    >
                      {opt.optionLabel}
                    </span>
                    <span className="text-sm font-medium pt-0.5 flex-1">
                      {opt.optionText}
                    </span>
                    {isCorrect && (
                      <span className="text-xs font-bold text-emerald-700 uppercase tracking-wider px-2 py-0.5 bg-emerald-100 rounded shrink-0">
                        Correct Answer
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Reveal stats notification */}
          {isRevealed && (
            <div className="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
              <span className="text-slate-500 font-medium">Correct Response Rate:</span>
              <span className="font-bold text-emerald-700 text-sm">
                {state.correctPercentage}% Correct
              </span>
            </div>
          )}
        </div>

        {/* Right Column: Live Bar Chart (5 cols) */}
        <div className="md:col-span-5 bg-white border border-slate-200 rounded-xl p-5 shadow-xs flex flex-col">
          <div className="flex items-center justify-between mb-4">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
              Live Response Distribution
            </span>
            <span className="text-xs font-semibold text-slate-500">
              {state.submissionCount} Votes
            </span>
          </div>

          <div className="flex-1 min-h-[220px]">
            <Bar data={chartData} options={chartOptions} />
          </div>

          <p className="text-[11px] text-slate-400 text-center mt-3">
            Real-time candidate submissions via WebSockets
          </p>
        </div>
      </div>

      {/* Host Action Controls */}
      <div className="bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between shadow-xs">
        <div>
          <button
            onClick={handleEndQuiz}
            className="text-xs font-medium text-red-600 hover:text-red-700 px-3 py-1.5 rounded border border-red-200 hover:bg-red-50 transition-colors"
          >
            End Quiz Early
          </button>
        </div>

        <div className="flex items-center gap-3">
          {!isRevealed ? (
            <button
              id="host-reveal-btn"
              onClick={handleReveal}
              className="px-5 py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-medium text-sm rounded-lg shadow-xs transition-colors"
            >
              Reveal Answer
            </button>
          ) : isLastQuestion ? (
            <button
              id="host-view-results-btn"
              onClick={handleEndQuiz}
              className="px-6 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm rounded-lg shadow-xs transition-colors"
            >
              Finish & View Final Leaderboard
            </button>
          ) : (
            <button
              id="host-next-question-btn"
              onClick={handleNextQuestion}
              className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm rounded-lg shadow-xs transition-colors"
            >
              Next Question →
            </button>
          )}
        </div>
      </div>
    </main>
  );
}
