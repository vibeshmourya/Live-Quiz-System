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
  getSessionIncidents,
  reviewIncident,
  getSessionParticipants,
} from '../services/api';
import { connectSessionWebSocket } from '../services/websocket';

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip);

export default function HostLivePage({ pin, onEndQuiz, onNavigate }) {
  const [state, setState] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionMessage, setActionMessage] = useState('');
  const [timerRemaining, setTimerRemaining] = useState(0);
  const [copiedLink, setCopiedLink] = useState(false);
  const [incidents, setIncidents] = useState([]);
  const [showIncidentsModal, setShowIncidentsModal] = useState(false);
  const [loadingIncidents, setLoadingIncidents] = useState(false);
  const [participants, setParticipants] = useState([]);
  const [isProcessingAction, setIsProcessingAction] = useState(false);

  const stateRef = useRef(null);
  stateRef.current = state;

  // Poll or initial fetch
  useEffect(() => {
    loadState();
    loadParticipants();
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

  async function loadParticipants() {
    try {
      const list = await getSessionParticipants(pin);
      setParticipants(list);
    } catch (e) {
      // ignore
    }
  }

  async function loadIncidents() {
    try {
      setLoadingIncidents(true);
      const list = await getSessionIncidents(pin);
      setIncidents(list);
    } catch (e) {
      console.error('Failed to load incidents:', e);
    } finally {
      setLoadingIncidents(false);
    }
  }

  async function handleReviewIncident(incidentId, reviewStatus) {
    try {
      await reviewIncident(pin, incidentId, {
        reviewStatus,
        reviewNotes: `Marked ${reviewStatus} by faculty during examination`,
      });
      await loadIncidents();
    } catch (err) {
      alert(err.message || 'Failed to update incident status');
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
          if (event.payload.participants) {
            setParticipants(event.payload.participants);
          } else {
            loadParticipants();
          }
        } else if (event.type === 'RULES_ACKNOWLEDGED') {
          if (event.payload.participants) {
            setParticipants(event.payload.participants);
          } else {
            loadParticipants();
          }
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
    if (isProcessingAction) return;
    setIsProcessingAction(true);
    setActionMessage('Starting examination question 1...');
    setError('');
    try {
      const updated = await startQuestion(pin, 0);
      setState(updated);
      setActionMessage('');
    } catch (err) {
      setError(err.message || 'Failed to start quiz');
    } finally {
      setIsProcessingAction(false);
    }
  }

  async function handleReveal() {
    if (isProcessingAction) return;
    setIsProcessingAction(true);
    setActionMessage('Authorizing answer reveal and calculating response rates...');
    setError('');
    try {
      const updated = await revealAnswer(pin);
      setState(updated);
      setActionMessage('');
    } catch (err) {
      setError(err.message || 'Failed to reveal answer');
    } finally {
      setIsProcessingAction(false);
    }
  }

  async function handleNextQuestion() {
    if (isProcessingAction) return;
    setIsProcessingAction(true);
    setActionMessage('Advancing to next examination question...');
    setError('');
    try {
      const updated = await nextQuestion(pin);
      setState(updated);
      setActionMessage('');
      if (updated.status === 'FINISHED') {
        onEndQuiz(updated);
      }
    } catch (err) {
      setError(err.message || 'Failed to move to next question');
    } finally {
      setIsProcessingAction(false);
    }
  }

  async function handleEndQuiz() {
    if (isProcessingAction) return;
    if (!window.confirm('Are you sure you want to conclude this examination?')) {
      return;
    }
    setIsProcessingAction(true);
    setActionMessage('Concluding examination and publishing final score audit...');
    setError('');
    try {
      const updated = await endQuiz(pin);
      setState(updated);
      setActionMessage('');
      onEndQuiz(updated);
    } catch (err) {
      setError(err.message || 'Failed to end quiz');
    } finally {
      setIsProcessingAction(false);
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
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-600">Connected Participants:</span>
                  <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                    Live Roster
                  </span>
                </div>
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

          {/* Dynamic Participant Roster */}
          <div className="max-w-2xl mx-auto mb-8 text-left bg-slate-50/80 rounded-xl p-4 border border-slate-200">
            <div className="flex items-center justify-between mb-3 pb-2 border-b border-slate-200/60">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Candidate Waiting Room ({participants.length})
              </span>
              <span className="text-[11px] text-slate-500">
                {participants.filter(p => p.rulesAcknowledged).length} / {participants.length} acknowledged rules
              </span>
            </div>

            {participants.length === 0 ? (
              <div className="py-6 text-center text-slate-400 text-xs">
                No candidates have joined yet. Share PIN <strong className="text-indigo-600 font-mono">{pin}</strong> or scan the QR code to enter.
              </div>
            ) : (
              <div className="flex flex-wrap gap-2 max-h-44 overflow-y-auto pr-1">
                {participants.map((p, idx) => (
                  <div
                    key={p.candidateId || idx}
                    className="flex items-center gap-2 bg-white px-3 py-1.5 rounded-lg border border-slate-200 shadow-2xs text-xs font-medium text-slate-800"
                  >
                    <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                    <span>{p.nickname}</span>
                    {p.rulesAcknowledged ? (
                      <span className="text-[10px] bg-indigo-50 text-indigo-700 font-semibold px-1.5 py-0.5 rounded border border-indigo-100" title="Examination rules accepted">
                        ✓ Ready
                      </span>
                    ) : (
                      <span className="text-[10px] bg-amber-50 text-amber-700 font-semibold px-1.5 py-0.5 rounded border border-amber-100" title="Awaiting rules acknowledgment">
                        Rules pending
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {actionMessage && (
            <div className="max-w-md mx-auto mb-4 p-2.5 bg-indigo-50 border border-indigo-200 rounded-lg text-xs text-indigo-700 font-medium flex items-center justify-center gap-2">
              <span className="inline-block w-3.5 h-3.5 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin"></span>
              <span>{actionMessage}</span>
            </div>
          )}

          <div className="max-w-sm mx-auto">
            <button
              id="host-start-quiz-btn"
              onClick={handleStartQuiz}
              disabled={state.participantCount === 0 || isProcessingAction}
              className="w-full py-3.5 px-6 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-xl text-base shadow-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {isProcessingAction ? (
                <>
                  <span className="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                  <span>Starting Examination...</span>
                </>
              ) : state.participantCount === 0 ? (
                'Waiting for Candidates to Join...'
              ) : (
                'Start Quiz Now'
              )}
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

      {/* Host Action Controls & In-flight Feedback */}
      {actionMessage && (
        <div className="mb-3 p-3 bg-indigo-50 border border-indigo-200 rounded-xl text-xs text-indigo-700 font-medium flex items-center justify-between shadow-2xs">
          <div className="flex items-center gap-2">
            <span className="inline-block w-4 h-4 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin shrink-0"></span>
            <span>{actionMessage}</span>
          </div>
          <span className="text-[11px] text-indigo-500 font-mono">Synchronizing state...</span>
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between shadow-xs">
        <div className="flex items-center gap-2">
          <button
            onClick={handleEndQuiz}
            disabled={isProcessingAction}
            className="text-xs font-medium text-red-600 hover:text-red-700 px-3 py-1.5 rounded border border-red-200 hover:bg-red-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            End Quiz Early
          </button>

          <button
            type="button"
            onClick={() => {
              loadIncidents();
              setShowIncidentsModal(true);
            }}
            className="text-xs font-medium text-amber-700 bg-amber-50 hover:bg-amber-100 px-3 py-1.5 rounded border border-amber-200 transition-colors flex items-center gap-1.5"
          >
            <span>🛡</span>
            <span>Security Incidents ({state.totalIncidents !== undefined ? state.totalIncidents : incidents.length})</span>
          </button>
        </div>

        <div className="flex items-center gap-3">
          {!isRevealed ? (
            <button
              id="host-reveal-btn"
              onClick={handleReveal}
              disabled={isProcessingAction}
              className="px-5 py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-medium text-sm rounded-lg shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {isProcessingAction && actionMessage.includes('reveal') && (
                <span className="inline-block w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              )}
              <span>Reveal Answer</span>
            </button>
          ) : isLastQuestion ? (
            <button
              id="host-view-results-btn"
              onClick={handleEndQuiz}
              disabled={isProcessingAction}
              className="px-6 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm rounded-lg shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {isProcessingAction && (
                <span className="inline-block w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              )}
              <span>Finish & View Final Leaderboard</span>
            </button>
          ) : (
            <button
              id="host-next-question-btn"
              onClick={handleNextQuestion}
              disabled={isProcessingAction}
              className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm rounded-lg shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {isProcessingAction && actionMessage.includes('Advancing') && (
                <span className="inline-block w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              )}
              <span>Next Question →</span>
            </button>
          )}
        </div>
      </div>

      {/* Incident Review Modal for Faculty */}
      {showIncidentsModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-2xl w-full p-6 shadow-xl border border-slate-200 max-h-[85vh] flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  Examination Incident Audit Log
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Logged interruptions, fullscreen exits, and clipboard activities during Session PIN: {pin}
                </p>
              </div>
              <button
                onClick={() => setShowIncidentsModal(false)}
                className="text-slate-400 hover:text-slate-600 text-lg font-bold p-1"
              >
                ✕
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              {loadingIncidents ? (
                <p className="text-xs text-slate-500 text-center py-6">Loading incident audit logs...</p>
              ) : incidents.length === 0 ? (
                <div className="text-center py-8 text-slate-500 text-xs">
                  <span className="text-2xl block mb-1">✓</span>
                  No security incidents or disruptions recorded for this examination session.
                </div>
              ) : (
                incidents.map((inc) => (
                  <div
                    key={inc.id}
                    className="p-3 bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-between gap-3 text-xs"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-slate-900">{inc.candidateNickname}</span>
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-200 text-slate-700">
                          {inc.eventType}
                        </span>
                        <span
                          className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                            inc.reviewStatus === 'FLAGGED'
                              ? 'bg-red-100 text-red-800'
                              : inc.reviewStatus === 'DISMISSED'
                              ? 'bg-slate-200 text-slate-600'
                              : inc.reviewStatus === 'REVIEWED'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-amber-100 text-amber-800'
                          }`}
                        >
                          {inc.reviewStatus}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-600 mt-1">
                        {inc.incidentDetails || 'Disruption recorded'}
                      </p>
                      <span className="text-[10px] text-slate-400">
                        {new Date(inc.serverTimestamp).toLocaleTimeString()}
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {inc.reviewStatus !== 'FLAGGED' && (
                        <button
                          type="button"
                          onClick={() => handleReviewIncident(inc.id, 'FLAGGED')}
                          className="px-2 py-1 bg-red-50 hover:bg-red-100 text-red-700 rounded border border-red-200 text-[11px] font-medium"
                        >
                          Flag
                        </button>
                      )}
                      {inc.reviewStatus !== 'DISMISSED' && (
                        <button
                          type="button"
                          onClick={() => handleReviewIncident(inc.id, 'DISMISSED')}
                          className="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded border border-slate-300 text-[11px] font-medium"
                        >
                          Dismiss
                        </button>
                      )}
                      {inc.reviewStatus !== 'REVIEWED' && (
                        <button
                          type="button"
                          onClick={() => handleReviewIncident(inc.id, 'REVIEWED')}
                          className="px-2 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded border border-emerald-200 text-[11px] font-medium"
                        >
                          Mark Reviewed
                        </button>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="pt-3 border-t border-slate-100 flex justify-end">
              <button
                onClick={() => setShowIncidentsModal(false)}
                className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold rounded-lg transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
