import React, { useState, useEffect } from 'react';
import { joinSession } from '../services/api';

export default function JoinQuizPage({ initialPin, onJoined, onNavigate }) {
  const [pin, setPin] = useState(initialPin || '');
  const [nickname, setNickname] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Check URL query parameters for pin
  useEffect(() => {
    if (!pin) {
      const params = new URLSearchParams(window.location.search);
      const urlPin = params.get('pin');
      if (urlPin) {
        setPin(urlPin);
      }
    }
  }, [pin]);

  function getOrCreateCandidateId() {
    let cid = localStorage.getItem('quizlive_candidate_id');
    if (!cid) {
      cid = 'c_' + Math.random().toString(36).substring(2, 10) + Date.now().toString(36);
      localStorage.setItem('quizlive_candidate_id', cid);
    }
    return cid;
  }

  async function handleJoin(e) {
    e.preventDefault();
    setError('');

    const cleanPin = pin.trim();
    const cleanNick = nickname.trim();

    if (!cleanPin) {
      setError('Please enter a 6-digit session PIN.');
      return;
    }
    if (!cleanNick) {
      setError('Please enter your display name.');
      return;
    }

    try {
      setLoading(true);
      const candidateId = getOrCreateCandidateId();
      const res = await joinSession({
        sessionPin: cleanPin,
        nickname: cleanNick,
        candidateId,
      });

      onJoined({
        pin: cleanPin,
        nickname: cleanNick,
        candidateId: res.candidateId || candidateId,
      });
    } catch (err) {
      setError(err.message || 'Failed to join session. Please check the PIN.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="flex-1 flex flex-col items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="w-12 h-12 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-extrabold text-xl mx-auto mb-3 shadow-sm">
            QZ
          </div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Join Live Quiz</h1>
          <p className="text-sm text-slate-500 mt-1">Enter the 6-digit session PIN and your name.</p>
        </div>

        {error && (
          <div className="mb-5 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
            {error}
          </div>
        )}

        <form onSubmit={handleJoin} className="bg-white border border-slate-200 rounded-xl p-6 shadow-xs space-y-4">
          <div>
            <label htmlFor="join-pin" className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1.5">
              Session PIN
            </label>
            <input
              id="join-pin"
              type="text"
              maxLength={8}
              placeholder="e.g. 849201"
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\s+/g, ''))}
              className="w-full px-3.5 py-3 border border-slate-300 rounded-lg text-center font-mono text-xl tracking-widest font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              autoFocus
            />
          </div>

          <div>
            <label htmlFor="join-nickname" className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1.5">
              Display Name
            </label>
            <input
              id="join-nickname"
              type="text"
              maxLength={30}
              placeholder="e.g. Alex"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>

          <button
            id="join-submit-btn"
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-medium rounded-lg text-sm transition-colors shadow-sm disabled:opacity-50 mt-2"
          >
            {loading ? 'Joining Session...' : 'Enter Quiz'}
          </button>
        </form>

        <div className="mt-6 text-center">
          <button
            onClick={() => onNavigate('home')}
            className="text-xs text-slate-500 hover:text-slate-800 transition-colors"
          >
            ← Back to Home
          </button>
        </div>
      </div>
    </main>
  );
}
