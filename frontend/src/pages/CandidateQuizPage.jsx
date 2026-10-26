import React, { useState, useEffect } from 'react';
import { getCandidateState, submitAnswer } from '../services/api';
import { connectSessionWebSocket } from '../services/websocket';

export default function CandidateQuizPage({
  sessionInfo,
  onFinished,
  onNavigate,
}) {
  const { pin, candidateId, nickname } = sessionInfo;

  const [state, setState] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedLabel, setSelectedLabel] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [timerRemaining, setTimerRemaining] = useState(0);

  useEffect(() => {
    loadCandidateState();
  }, [pin, candidateId]);

  async function loadCandidateState() {
    try {
      setLoading(true);
      const data = await getCandidateState(pin, candidateId);
      setState(data);
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

  // 1. WAITING SCREEN FOR CANDIDATE
  if (state.status === 'WAITING') {
    return (
      <main className="flex-1 flex flex-col items-center justify-center p-6 text-center max-w-sm mx-auto">
        <div className="w-16 h-16 rounded-full bg-indigo-50 border border-indigo-200 flex items-center justify-center mx-auto mb-4">
          <div className="w-4 h-4 bg-indigo-600 rounded-full animate-ping" />
        </div>

        <h1 className="text-xl font-bold text-slate-900 mb-1">You're in!</h1>
        <p className="text-sm text-slate-600 mb-4">
          Playing as <span className="font-semibold text-slate-900">{nickname || state.nickname}</span>
        </p>

        <div className="p-4 bg-white border border-slate-200 rounded-xl w-full text-xs text-slate-500 shadow-xs">
          Waiting for the host to start the first question...
        </div>
      </main>
    );
  }

  // 2. ACTIVE OR REVEALED QUESTION SCREEN (Mobile Optimized)
  const currentQ = state.currentQuestion;
  const isRevealed = state.status === 'QUESTION_REVEALED';
  const allowReselect = Boolean(state.allowReselection);
  const hasSubmitted = state.hasSubmitted || !!selectedLabel;
  const isLocked = hasSubmitted && !allowReselect;
  const isTimeUp = timerRemaining <= 0;

  return (
    <main className="flex-1 max-w-md mx-auto w-full px-4 py-5 flex flex-col justify-between">
      {/* Top Header: Question # and Countdown */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Question {state.currentQuestionIndex + 1} of {state.totalQuestions}
          </span>
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
