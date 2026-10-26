import React, { useEffect } from 'react';
import confetti from 'canvas-confetti';

export default function ResultsPage({ results, isHost, onNavigate, candidateInfo }) {
  useEffect(() => {
    // Launch celebratory confetti when viewing final results
    try {
      confetti({
        particleCount: 80,
        spread: 60,
        origin: { y: 0.6 },
      });
    } catch (e) {
      // ignore
    }
  }, []);

  const leaderboard = results?.leaderboard || [];

  return (
    <main className="flex-1 max-w-2xl mx-auto px-4 py-8 w-full flex flex-col items-center">
      <div className="w-full text-center mb-6">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Quiz Completed
        </span>
        <h1 className="text-2xl font-bold text-slate-900 mt-1">Final Results</h1>
      </div>

      {/* Candidate Individual Performance Card (if candidate) */}
      {!isHost && (
        <div className="w-full bg-white border border-slate-200 rounded-xl p-6 mb-6 shadow-xs text-center">
          <span className="text-xs uppercase font-bold tracking-wider text-slate-500">
            {candidateInfo?.nickname || results?.nickname || 'Your Performance'}
          </span>
          <div className="text-4xl font-extrabold text-indigo-600 my-2">
            {results?.myScore ?? 0} pts
          </div>

          <div className="grid grid-cols-3 gap-2 mt-4 pt-4 border-t border-slate-100 text-xs">
            <div>
              <span className="text-slate-400 block">Rank</span>
              <span className="font-bold text-slate-800 text-sm">
                #{results?.myRank || '-'}
              </span>
            </div>
            <div>
              <span className="text-slate-400 block">Correct</span>
              <span className="font-bold text-emerald-600 text-sm">
                {results?.totalCorrect ?? 0}
              </span>
            </div>
            <div>
              <span className="text-slate-400 block">Incorrect</span>
              <span className="font-bold text-red-500 text-sm">
                {results?.totalIncorrect ?? 0}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Leaderboard Table */}
      <div className="w-full bg-white border border-slate-200 rounded-xl p-5 shadow-xs mb-6">
        <h2 className="text-sm font-bold uppercase tracking-wider text-slate-700 mb-4">
          Final Leaderboard
        </h2>

        {leaderboard.length === 0 ? (
          <p className="text-xs text-slate-500 text-center py-4">No participants recorded.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {leaderboard.map((entry) => {
              const isMe = candidateInfo && entry.candidateId === candidateInfo.candidateId;
              return (
                <div
                  key={entry.candidateId || entry.rank}
                  className={`flex items-center justify-between py-3 px-2 rounded-lg ${
                    isMe ? 'bg-indigo-50/80 font-bold' : ''
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <span
                      className={`w-6 text-center font-mono text-xs font-bold ${
                        entry.rank === 1
                          ? 'text-amber-500'
                          : entry.rank === 2
                          ? 'text-slate-400'
                          : entry.rank === 3
                          ? 'text-amber-700'
                          : 'text-slate-500'
                      }`}
                    >
                      {entry.rank === 1 ? '🥇' : entry.rank === 2 ? '🥈' : entry.rank === 3 ? '🥉' : `#${entry.rank}`}
                    </span>
                    <span className="text-sm text-slate-900">
                      {entry.nickname} {isMe && '(You)'}
                    </span>
                  </div>

                  <span className="text-sm font-mono font-bold text-indigo-700">
                    {entry.score} pts
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="w-full flex gap-3 justify-center">
        {isHost ? (
          <>
            <button
              onClick={() => onNavigate('create')}
              className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg transition-colors shadow-xs"
            >
              Start Another Quiz
            </button>
            <button
              onClick={() => onNavigate('home')}
              className="px-5 py-2.5 bg-white hover:bg-slate-50 border border-slate-300 text-slate-700 text-xs font-semibold rounded-lg transition-colors shadow-xs"
            >
              Back to Home
            </button>
          </>
        ) : (
          <button
            onClick={() => onNavigate('home')}
            className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg transition-colors shadow-xs"
          >
            Back to Home
          </button>
        )}
      </div>
    </main>
  );
}
