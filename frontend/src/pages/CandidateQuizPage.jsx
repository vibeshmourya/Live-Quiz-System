import React, { useState, useEffect, useRef } from 'react';
import { getCandidateState, submitAnswer, reportIncident, getSessionParticipants, acknowledgeRules } from '../services/api';
import { connectSessionWebSocket } from '../services/websocket';

export default function CandidateQuizPage({
  sessionInfo,
  onFinished,
  onNavigate,
}) {
  const { pin, candidateId, nickname } = sessionInfo;

  const [state, setState] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedLabel, setSelectedLabel] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [timerRemaining, setTimerRemaining] = useState(0);

  // Mandatory Rules state
  const [rulesAgreed, setRulesAgreed] = useState(false);
  const [isAcknowledging, setIsAcknowledging] = useState(false);
  const [rulesError, setRulesError] = useState('');

  // Secure examination state
  const isFullscreenSupported = typeof document !== 'undefined' && Boolean(
    document.fullscreenEnabled || 
    document.webkitFullscreenEnabled || 
    document.mozFullScreenEnabled || 
    document.msFullscreenEnabled
  );
  const [isFullscreen, setIsFullscreen] = useState(
    typeof document !== 'undefined' ? Boolean(document.fullscreenElement) : false
  );
  const [incidentCount, setIncidentCount] = useState(0);
  const [activeWarning, setActiveWarning] = useState(null);
  const lastIncidentTimeRef = useRef({});

  useEffect(() => {
    loadCandidateState();
    loadParticipants();
  }, [pin, candidateId]);

  async function loadParticipants() {
    try {
      const data = await getSessionParticipants(pin);
      if (Array.isArray(data)) {
        setParticipants(data);
      }
    } catch (e) {
      // Non-critical background fetch
    }
  }

  async function loadCandidateState() {
    try {
      setLoading(true);
      const data = await getCandidateState(pin, candidateId);
      setState(data);
      if (data.myIncidentCount !== undefined) {
        setIncidentCount(data.myIncidentCount);
      }
      if (data.hasSubmitted) {
        setSelectedLabel(data.mySelectedLabel);
      }
      if (data.status === 'FINISHED') {
        onFinished(data);
      }
    } catch (err) {
      setError(err.message || 'Failed to load session');
    } finally {
      setLoading(false);
    }
  }

  async function handleAcknowledgeRules() {
    if (!rulesAgreed || isAcknowledging) return;
    setIsAcknowledging(true);
    setRulesError('');
    try {
      await acknowledgeRules(pin, candidateId);
      setState((prev) => (prev ? { ...prev, rulesAcknowledged: true } : prev));
      loadParticipants();
    } catch (err) {
      setRulesError(err.message || 'Failed to acknowledge examination rules');
    } finally {
      setIsAcknowledging(false);
    }
  }

  // Record incident with local throttling and warning notification
  async function triggerIncident(eventType, details) {
    const now = Date.now();
    if (lastIncidentTimeRef.current[eventType] && now - lastIncidentTimeRef.current[eventType] < 3000) {
      return;
    }
    lastIncidentTimeRef.current[eventType] = now;

    setIncidentCount((prev) => {
      const nextCount = prev + 1;
      const threshold = state?.warningThreshold || 3;
      let warningText = `Security Notice: ${eventType.replace(/_/g, ' ').toLowerCase()} detected. Incident #${nextCount} logged.`;
      if (nextCount >= threshold) {
        warningText += ` Warning threshold (${threshold}) reached. Attempt flagged for faculty review.`;
      }
      setActiveWarning(warningText);
      setTimeout(() => setActiveWarning(null), 6000);
      return nextCount;
    });

    try {
      await reportIncident(pin, {
        candidateId,
        eventType,
        incidentDetails: details || `Detected in browser examination environment`,
      });
    } catch (e) {
      // ignore network errors
    }
  }

  // Request fullscreen explicitly upon user interaction
  async function enterFullscreenMode() {
    try {
      if (document.documentElement.requestFullscreen) {
        await document.documentElement.requestFullscreen();
        setIsFullscreen(true);
      }
    } catch (err) {
      console.warn('Fullscreen request declined or unsupported:', err);
    }
  }

  // Security Event Listeners (Fullscreen, Visibility/Tab switch, Clipboard restrictions)
  useEffect(() => {
    if (!state || state.status === 'WAITING' || state.status === 'FINISHED') {
      return;
    }

    function handleFullscreenChange() {
      const currentlyFullscreen = Boolean(document.fullscreenElement);
      setIsFullscreen(currentlyFullscreen);
      if (state.requireFullscreen && !currentlyFullscreen) {
        triggerIncident('FULLSCREEN_EXIT', 'Student exited browser full-screen mode');
      }
    }

    function handleVisibilityChange() {
      if (state.monitorTabSwitch && document.hidden) {
        triggerIncident('TAB_SWITCH', 'Student switched browser tab or minimized window');
      }
    }

    function handleCopy(e) {
      if (state.restrictCopyPaste) {
        e.preventDefault();
        triggerIncident('COPY_PASTE_ATTEMPT', 'Copy attempt intercepted');
      }
    }

    function handlePaste(e) {
      if (state.restrictCopyPaste) {
        e.preventDefault();
        triggerIncident('COPY_PASTE_ATTEMPT', 'Paste attempt intercepted');
      }
    }

    function handleCut(e) {
      if (state.restrictCopyPaste) {
        e.preventDefault();
        triggerIncident('COPY_PASTE_ATTEMPT', 'Cut attempt intercepted');
      }
    }

    function handleContextMenu(e) {
      if (state.restrictCopyPaste) {
        e.preventDefault();
      }
    }

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    document.addEventListener('copy', handleCopy);
    document.addEventListener('paste', handlePaste);
    document.addEventListener('cut', handleCut);
    document.addEventListener('contextmenu', handleContextMenu);

    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      document.removeEventListener('copy', handleCopy);
      document.removeEventListener('paste', handlePaste);
      document.removeEventListener('cut', handleCut);
      document.removeEventListener('contextmenu', handleContextMenu);
    };
  }, [state?.status, state?.requireFullscreen, state?.monitorTabSwitch, state?.restrictCopyPaste, state?.warningThreshold]);

  // WebSocket real-time updates
  useEffect(() => {
    const unsubscribe = connectSessionWebSocket(pin, (event) => {
      if (event.type === 'QUESTION_STARTED') {
        setSelectedLabel(null);
        setState((prev) =>
          prev
            ? {
                ...prev,
                status: 'QUESTION_ACTIVE',
                currentQuestionIndex: event.payload.questionIndex,
                totalQuestions: event.payload.totalQuestions,
                currentQuestion: event.payload.question,
                questionStartedAtEpochMs: event.payload.startedAt,
                questionEndsAtEpochMs: event.payload.endsAt,
                allowReselection: Boolean(event.payload.allowReselection),
                requireFullscreen: event.payload.requireFullscreen !== undefined ? Boolean(event.payload.requireFullscreen) : prev.requireFullscreen,
                monitorTabSwitch: event.payload.monitorTabSwitch !== undefined ? Boolean(event.payload.monitorTabSwitch) : prev.monitorTabSwitch,
                restrictCopyPaste: event.payload.restrictCopyPaste !== undefined ? Boolean(event.payload.restrictCopyPaste) : prev.restrictCopyPaste,
                hasSubmitted: false,
                mySelectedLabel: null,
                revealedCorrectLabel: null,
                wasCorrect: null,
              }
            : prev
        );
        loadCandidateState();
      } else if (event.type === 'QUESTION_REVEALED') {
        setState((prev) =>
          prev
            ? {
                ...prev,
                status: 'QUESTION_REVEALED',
                revealedCorrectLabel: event.payload.correctOptionLabel,
                wasCorrect: selectedLabel === event.payload.correctOptionLabel,
              }
            : prev
        );
      } else if (event.type === 'PARTICIPANT_JOINED') {
        if (event.payload.participants && Array.isArray(event.payload.participants)) {
          setParticipants(event.payload.participants);
        } else {
          loadParticipants();
        }
      } else if (event.type === 'RULES_ACKNOWLEDGED') {
        setParticipants((prev) =>
          prev.map((p) =>
            p.candidateId === event.payload.candidateId
              ? { ...p, rulesAcknowledged: true }
              : p
          )
        );
      } else if (event.type === 'SESSION_FINISHED') {
        loadCandidateState();
      }
    });

    return () => {
      unsubscribe();
    };
  }, [pin, selectedLabel]);

  // Synchronized countdown timer
  useEffect(() => {
    if (!state || state.status !== 'QUESTION_ACTIVE' || !state.questionEndsAtEpochMs) {
      return;
    }

    function updateRemaining() {
      const now = Date.now();
      const diff = Math.max(0, Math.ceil((state.questionEndsAtEpochMs - now) / 1000));
      setTimerRemaining(diff);
    }

    updateRemaining();
    const interval = setInterval(updateRemaining, 1000);
    return () => clearInterval(interval);
  }, [state?.questionEndsAtEpochMs, state?.status]);

  async function handleOptionSelect(label) {
    if (state?.status !== 'QUESTION_ACTIVE' || timerRemaining <= 0) {
      return;
    }

    const allowReselect = Boolean(state?.allowReselection);
    const hasAlreadySubmitted = Boolean(state?.hasSubmitted || selectedLabel);

    if (hasAlreadySubmitted && !allowReselect) {
      return;
    }

    if (label === selectedLabel) {
      return;
    }

    const prevLabel = selectedLabel;
    setSelectedLabel(label);
    setIsSubmitting(true);
    setError('');

    try {
      await submitAnswer({
        sessionPin: pin,
        candidateId,
        questionId: state.currentQuestion.id,
        optionLabel: label,
      });

      setState((prev) => ({
        ...prev,
        hasSubmitted: true,
        mySelectedLabel: label,
      }));
    } catch (err) {
      setError(err.message || 'Submission error');
      setSelectedLabel(prevLabel);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (loading && !state) {
    return (
      <main className="flex-1 flex items-center justify-center p-6">
        <p className="text-sm text-slate-500">Connecting to quiz session...</p>
      </main>
    );
  }

  if (error && !state) {
    return (
      <main className="flex-1 flex items-center justify-center p-6">
        <div className="max-w-xs p-6 bg-red-50 border border-red-200 rounded-xl text-center">
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

  // 1. MANDATORY EXAMINATION RULES ACKNOWLEDGMENT DIALOG
  if (state && !state.rulesAcknowledged) {
    return (
      <main className="flex-1 flex flex-col items-center justify-center p-4 max-w-lg mx-auto w-full min-h-[100dvh] pb-[calc(1.5rem+env(safe-area-inset-bottom,0px))]">
        <div className="bg-white border border-slate-200 rounded-2xl p-6 sm:p-7 shadow-sm w-full">
          <div className="flex items-center gap-2.5 mb-3">
            <span className="w-8 h-8 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-700 flex items-center justify-center font-bold text-sm">
              🛡️
            </span>
            <div>
              <span className="text-[11px] font-bold text-indigo-600 uppercase tracking-widest block">
                Quizora Examination Protocol
              </span>
              <h1 className="text-base sm:text-lg font-bold text-slate-900 leading-tight">
                Mandatory Examination Rules
              </h1>
            </div>
          </div>

          <p className="text-xs text-slate-500 mb-4 pb-3 border-b border-slate-100">
            Candidate: <strong className="text-slate-800">{nickname || state.nickname}</strong> • Quiz: <strong className="text-slate-800">{state.quizTitle}</strong> (PIN: <span className="font-mono">{pin}</span>)
          </p>

          <div className="space-y-3 max-h-[48vh] overflow-y-auto pr-1 text-xs text-slate-700">
            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <strong className="block text-slate-900 font-semibold mb-1">⏱ Authoritative Server Timers</strong>
              <span>
                Each question runs against an authoritative server deadline. At 0 seconds, answers are locked immediately and late submissions are rejected.
              </span>
            </div>

            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <strong className="block text-slate-900 font-semibold mb-1">📝 Answer Selection Policy</strong>
              <span>
                {state.allowReselection
                  ? 'Answer Reselection is ENABLED: You may change your chosen option before the countdown timer expires.'
                  : 'Single Submission LOCKED: Your selection is permanently recorded upon first tap and cannot be changed.'}
              </span>
            </div>

            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <strong className="block text-slate-900 font-semibold mb-1">🔒 Confidentiality & Navigation</strong>
              <span>
                Questions proceed sequentially under faculty control; previous questions cannot be revisited. Correct answers remain strictly encrypted on the server until authorized faculty reveal.
              </span>
            </div>

            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <strong className="block text-slate-900 font-semibold mb-1">👁 Examination Integrity & Monitoring Signals</strong>
              <ul className="list-disc list-inside space-y-1 text-slate-600 mt-1">
                {state.requireFullscreen && <li>Full-screen mode is required on desktop devices.</li>}
                {state.monitorTabSwitch && <li>Tab switching or window minimization is logged as a security incident.</li>}
                {state.restrictCopyPaste && <li>Copying, cutting, or pasting question content is restricted and recorded.</li>}
                <li>Warning threshold: {state.warningThreshold} incidents will flag your submission for faculty review.</li>
              </ul>
            </div>

            <div className="p-3 bg-amber-50/60 rounded-lg border border-amber-200 text-amber-900">
              <strong className="block font-semibold mb-1">📱 Mobile & Privacy Notice</strong>
              <span>
                Monitoring is strictly confined to this browser session window during active examination questions. Quizora does not access your microphone, camera, or personal device storage.
              </span>
            </div>
          </div>

          {rulesError && (
            <div className="mt-3 p-2 bg-red-50 border border-red-200 rounded text-xs text-red-700">
              {rulesError}
            </div>
          )}

          <div className="mt-5 pt-4 border-t border-slate-100 space-y-4">
            <label className="flex items-start gap-3 cursor-pointer select-none">
              <input
                id="rules-agree-checkbox"
                type="checkbox"
                checked={rulesAgreed}
                onChange={(e) => setRulesAgreed(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 cursor-pointer"
              />
              <span className="text-xs text-slate-700 leading-normal">
                I have read, understood, and agree to follow all Quizora examination rules and code of conduct.
              </span>
            </label>

            <button
              id="rules-acknowledge-btn"
              type="button"
              disabled={!rulesAgreed || isAcknowledging}
              onClick={handleAcknowledgeRules}
              className="w-full py-3 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs sm:text-sm rounded-xl shadow-xs transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {isAcknowledging ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                  <span>Entering Waiting Room...</span>
                </>
              ) : (
                'Acknowledge Rules & Join Examination'
              )}
            </button>
          </div>
        </div>
      </main>
    );
  }

  // 2. DYNAMIC PARTICIPANT LOBBY / WAITING SCREEN FOR CANDIDATE
  if (state.status === 'WAITING') {
    return (
      <main className="flex-1 flex flex-col items-center justify-center p-4 text-center max-w-md mx-auto w-full min-h-[100dvh] pb-[calc(1.5rem+env(safe-area-inset-bottom,0px))]">
        <div className="bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 shadow-xs w-full text-center">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 mb-5">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            Connected • Live Lobby
          </div>

          <div className="w-16 h-16 rounded-full bg-indigo-50 border border-indigo-200 flex items-center justify-center mx-auto mb-4">
            <span className="text-2xl">🎓</span>
          </div>

          <h1 className="text-lg font-bold text-slate-900 mb-1">
            {state.quizTitle}
          </h1>
          <p className="text-xs text-slate-500 mb-6">
            Session PIN: <span className="font-mono font-bold text-slate-800">{pin}</span> • Playing as <strong className="text-indigo-600">{nickname || state.nickname}</strong>
          </p>

          {/* Dynamic Candidate Roster in Waiting Room */}
          <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 text-left mb-6">
            <div className="flex items-center justify-between mb-3 pb-2 border-b border-slate-200/60">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Connected Candidates ({participants.length})
              </span>
              <span className="text-[11px] text-slate-500 font-mono">
                Waiting for Host...
              </span>
            </div>

            {participants.length === 0 ? (
              <p className="text-xs text-slate-400 text-center py-4">
                Connecting candidates to lobby...
              </p>
            ) : (
              <div className="flex flex-wrap gap-1.5 max-h-36 overflow-y-auto pr-1">
                {participants.map((p, idx) => {
                  const isMe = p.candidateId === candidateId;
                  return (
                    <div
                      key={p.candidateId || idx}
                      className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium border ${
                        isMe
                          ? 'bg-indigo-50 border-indigo-200 text-indigo-800 font-semibold'
                          : 'bg-white border-slate-200 text-slate-700'
                      }`}
                    >
                      <span className={`w-1.5 h-1.5 rounded-full ${isMe ? 'bg-indigo-600' : 'bg-emerald-500'}`}></span>
                      <span>{p.nickname}</span>
                      {isMe && <span className="text-[10px] text-indigo-500">(You)</span>}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 font-medium">
            Please keep this window active. The examination will automatically begin when your faculty starts Question 1.
          </div>
        </div>
      </main>
    );
  }

  // 3. ACTIVE OR REVEALED QUESTION SCREEN (Mobile & Desktop Optimized)
  const currentQ = state.currentQuestion;
  const isRevealed = state.status === 'QUESTION_REVEALED';
  const allowReselect = Boolean(state.allowReselection);
  const hasSubmitted = state.hasSubmitted || !!selectedLabel;
  const isLocked = hasSubmitted && !allowReselect;
  const isTimeUp = timerRemaining <= 0;

  return (
    <main
      className={`flex-1 max-w-md mx-auto w-full px-4 py-5 flex flex-col justify-between min-h-[100dvh] pb-[calc(1.5rem+env(safe-area-inset-bottom,0px))] overflow-x-hidden ${
        state.restrictCopyPaste ? 'select-none' : ''
      }`}
    >
      {/* Explicit Fullscreen Required Overlay (For Desktop Browsers Supporting Fullscreen) */}
      {state.requireFullscreen && !isFullscreen && isFullscreenSupported && (
        <div className="fixed inset-0 z-50 bg-slate-900/80 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 text-center shadow-xl border border-slate-200">
            <div className="w-12 h-12 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-3 text-xl font-bold">
              ⛶
            </div>
            <h3 className="text-base font-bold text-slate-900 mb-1">
              Full-Screen Mode Required
            </h3>
            <p className="text-xs text-slate-600 mb-5 leading-relaxed">
              This examination requires full-screen mode to ensure testing integrity. Please activate full-screen to proceed.
            </p>
            <button
              onClick={enterFullscreenMode}
              className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors cursor-pointer"
            >
              Enter Full-Screen Mode
            </button>
          </div>
        </div>
      )}

      {/* Mobile Notice When Fullscreen API is Unsupported (e.g. iOS Safari) */}
      {state.requireFullscreen && !isFullscreenSupported && (
        <div className="mb-3 p-2 bg-indigo-50 border border-indigo-200 rounded-lg text-[11px] text-indigo-700 text-center font-medium">
          📱 Mobile View: Keep this browser tab open. Do not switch tabs or minimize the window during testing.
        </div>
      )}

      {/* Active Incident Warning Notification */}
      {activeWarning && (
        <div className="mb-4 p-3 bg-amber-50 border border-amber-300 rounded-lg flex items-start gap-2.5 text-xs text-amber-900 shadow-xs animate-shake">
          <span className="font-bold text-amber-600 shrink-0">⚠</span>
          <div className="flex-1">
            <span className="font-semibold block">{activeWarning}</span>
            <span className="text-[11px] text-amber-700 mt-0.5 block">
              Repeated disruptions will be reviewed by the examination faculty.
            </span>
          </div>
        </div>
      )}

      {/* Top Header: Question #, Incident Counter (if any) and Countdown */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Question {state.currentQuestionIndex + 1} of {state.totalQuestions}
            </span>
            {incidentCount > 0 && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">
                {incidentCount} {incidentCount === 1 ? 'incident' : 'incidents'}
              </span>
            )}
          </div>
          <div
            className={`font-mono text-base font-bold px-3 py-1 rounded-md ${
              timerRemaining <= 5 && !isRevealed
                ? 'bg-red-100 text-red-700 animate-pulse'
                : 'bg-slate-100 text-slate-800'
            }`}
          >
            {isRevealed ? 'Revealed' : `${timerRemaining}s`}
          </div>
        </div>

        {/* Question Text */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 mb-5 shadow-xs">
          <h2 className="text-base sm:text-lg font-bold text-slate-900 leading-snug">
            {currentQ?.questionText}
          </h2>
        </div>

        {error && (
          <div className="mb-4 p-2.5 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
            {error}
          </div>
        )}

        {/* Four Large, Easy-to-Tap Answer Buttons */}
        <div className="space-y-3">
          {currentQ?.options?.map((opt) => {
            const isSelected = selectedLabel === opt.optionLabel;
            const isCorrect = isRevealed && opt.optionLabel === state.revealedCorrectLabel;
            const isWrongSelection = isRevealed && isSelected && !isCorrect;

            let buttonStyle = 'bg-white border-slate-200 text-slate-800 active:scale-[0.99]';

            if (isRevealed) {
              if (isCorrect) {
                buttonStyle = 'bg-emerald-500 text-white border-emerald-600 shadow-sm';
              } else if (isWrongSelection) {
                buttonStyle = 'bg-red-500 text-white border-red-600';
              } else {
                buttonStyle = 'bg-slate-50 text-slate-400 border-slate-200 opacity-60';
              }
            } else if (isSelected) {
              buttonStyle = 'bg-indigo-600 text-white border-indigo-700 shadow-sm';
            } else if (isLocked || isTimeUp) {
              buttonStyle = 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed opacity-60';
            } else {
              buttonStyle = 'bg-white hover:bg-slate-50 border-slate-300 text-slate-900 shadow-xs';
            }

            return (
              <button
                key={opt.optionLabel}
                id={`candidate-opt-${opt.optionLabel}`}
                type="button"
                disabled={isLocked || isTimeUp || isRevealed || isSubmitting}
                onClick={() => handleOptionSelect(opt.optionLabel)}
                className={`w-full min-h-[58px] p-4 rounded-xl border flex items-center gap-3 text-left transition-all ${buttonStyle}`}
              >
                <span
                  className={`w-8 h-8 rounded-lg flex items-center justify-center font-bold text-sm shrink-0 ${
                    isSelected || (isRevealed && isCorrect)
                      ? 'bg-white/20 text-white'
                      : 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {opt.optionLabel}
                </span>
                <span className="text-sm font-semibold flex-1 leading-snug">
                  {opt.optionText}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Submission Status & Feedback */}
      <div className="mt-6">
        {isRevealed ? (
          <div
            className={`p-4 rounded-xl text-center text-sm font-semibold border ${
              selectedLabel === state.revealedCorrectLabel
                ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                : selectedLabel
                ? `bg-red-50 border-red-200 text-red-800`
                : 'bg-slate-100 border-slate-200 text-slate-700'
            }`}
          >
            {selectedLabel === state.revealedCorrectLabel
              ? '✓ Correct! Well done.'
              : selectedLabel
              ? `✕ Incorrect. The correct answer was ${state.revealedCorrectLabel}.`
              : `Time up! The correct answer was ${state.revealedCorrectLabel}.`}
          </div>
        ) : hasSubmitted ? (
          <div className="p-4 bg-indigo-50 border border-indigo-200 rounded-xl text-center text-xs font-medium text-indigo-900 shadow-xs">
            {allowReselect ? (
              <span>✓ Submitted ({selectedLabel}). Reselection allowed: tap another option to change before time expires.</span>
            ) : (
              <span>✓ Answer locked ({selectedLabel}). Waiting for timer to expire...</span>
            )}
          </div>
        ) : isTimeUp ? (
          <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-center text-xs font-medium text-amber-800">
            Time has expired for this question.
          </div>
        ) : (
          <p className="text-center text-xs text-slate-400">
            {allowReselect ? 'Tap an option to submit (you can change it before deadline)' : 'Tap an option to lock in your answer'}
          </p>
        )}
      </div>
    </main>
  );
}
