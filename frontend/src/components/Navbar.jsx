import React from 'react';

export default function Navbar({ onNavigate, currentScreen, pin, facultyUser, onLogout }) {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="max-w-5xl mx-auto px-4 h-16 flex items-center justify-between">
        <button
          onClick={() => onNavigate('home')}
          className="flex items-center gap-2.5 text-left group focus:outline-none"
        >
          <div className="w-8 h-8 rounded bg-indigo-600 flex items-center justify-center text-white font-bold text-sm tracking-wider">
            QZ
          </div>
          <span className="font-semibold text-lg text-slate-900 group-hover:text-indigo-600 transition-colors">
            Quizora
          </span>
        </button>

        <div className="flex items-center gap-3">
          {pin && (
            <div className="flex items-center gap-2 text-xs font-medium text-slate-600 bg-slate-100 px-3 py-1.5 rounded-md">
              <span>PIN:</span>
              <span className="font-mono font-bold text-slate-900 text-sm tracking-wider">{pin}</span>
            </div>
          )}

          {facultyUser && (
            <div className="flex items-center gap-2 text-xs text-slate-500">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              <span className="hidden sm:inline font-medium text-slate-700">{facultyUser.name}</span>
              <button
                onClick={onLogout}
                className="text-xs text-rose-600 hover:text-rose-700 font-medium ml-1 cursor-pointer"
                title="Logout faculty session"
              >
                Logout
              </button>
            </div>
          )}

          {currentScreen !== 'home' && (
            <button
              onClick={() => onNavigate('home')}
              className="text-xs text-slate-500 hover:text-slate-800 font-medium px-3 py-1.5 rounded border border-slate-200 hover:border-slate-300 transition-colors"
            >
              {currentScreen === 'create' ? 'Dashboard' : 'Leave'}
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
