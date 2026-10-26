import React from 'react';

export default function HomePage({ onNavigate }) {
  return (
    <main className="flex-1 flex flex-col items-center justify-center p-6">
      <div className="w-full max-w-sm flex flex-col items-center text-center">
        {/* Brand */}
        <div className="w-14 h-14 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-extrabold text-2xl mb-4 shadow-sm">
          QZ
        </div>
        <h1 className="text-3xl font-bold text-slate-900 tracking-tight mb-8">
          Quizora
        </h1>

        {/* Primary Actions */}
        <div className="w-full space-y-3">
          <button
            id="home-create-quiz-btn"
            onClick={() => onNavigate('create')}
            className="w-full py-3.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-medium rounded-lg text-base transition-colors shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
          >
            Create Quiz
          </button>

          <button
            id="home-join-quiz-btn"
            onClick={() => onNavigate('join')}
            className="w-full py-3.5 px-4 bg-white hover:bg-slate-50 text-slate-800 font-medium rounded-lg text-base border border-slate-300 transition-colors shadow-xs focus:outline-none focus:ring-2 focus:ring-slate-400 focus:ring-offset-2"
          >
            Join Quiz
          </button>
        </div>
      </div>
    </main>
  );
}
