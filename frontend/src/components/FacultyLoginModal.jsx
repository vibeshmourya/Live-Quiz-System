import React, { useState } from 'react';
import { loginFaculty, registerFaculty } from '../services/api';

export default function FacultyLoginModal({ onSuccess, onCancel }) {
  const [tab, setTab] = useState('login'); // 'login' | 'register'

  // Login state
  const [username, setUsername] = useState('faculty');
  const [password, setPassword] = useState('');

  // Register state
  const [regName, setRegName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regConfirmPassword, setRegConfirmPassword] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  async function handleLogin(e) {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!username.trim() || !password) {
      setError('Please enter both email/username and password.');
      return;
    }

    try {
      setLoading(true);
      const res = await loginFaculty(username.trim(), password);
      onSuccess(res);
    } catch (err) {
      setError(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setLoading(false);
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!regName.trim()) {
      setError('Please enter your full name.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!regEmail.trim() || !emailRegex.test(regEmail.trim())) {
      setError('Please enter a valid email address (e.g. faculty@university.edu).');
      return;
    }
    if (!regPassword || regPassword.length < 6) {
      setError('Password must be at least 6 characters long.');
      return;
    }
    if (regPassword !== regConfirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    try {
      setLoading(true);
      const res = await registerFaculty({
        name: regName.trim(),
        email: regEmail.trim(),
        password: regPassword,
      });
      setSuccessMsg('Faculty registered successfully! Logging you in...');
      setTimeout(() => {
        onSuccess(res);
      }, 500);
    } catch (err) {
      setError(err.message || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="bg-white border border-slate-200 rounded-xl p-6 max-w-sm w-full shadow-lg">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded bg-indigo-600 flex items-center justify-center text-white font-bold text-xs">
              FA
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900 leading-tight">Faculty Portal</h2>
              <p className="text-xs text-slate-500">Authorized examination access</p>
            </div>
          </div>
          <button
            onClick={onCancel}
            className="text-slate-400 hover:text-slate-600 text-lg leading-none p-1 cursor-pointer"
          >
            ×
          </button>
        </div>

        {/* Tab switch */}
        <div className="flex border-b border-slate-200 mb-4">
          <button
            type="button"
            onClick={() => { setTab('login'); setError(''); setSuccessMsg(''); }}
            className={`flex-1 pb-2 text-xs font-semibold text-center border-b-2 transition-colors cursor-pointer ${
              tab === 'login'
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setTab('register'); setError(''); setSuccessMsg(''); }}
            className={`flex-1 pb-2 text-xs font-semibold text-center border-b-2 transition-colors cursor-pointer ${
              tab === 'register'
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Register
          </button>
        </div>

        {error && (
          <div className="mb-4 p-2.5 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
            {error}
          </div>
        )}

        {successMsg && (
          <div className="mb-4 p-2.5 bg-emerald-50 border border-emerald-200 rounded-lg text-xs text-emerald-700">
            {successMsg}
          </div>
        )}

        {tab === 'login' ? (
          <form onSubmit={handleLogin} className="space-y-3.5">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Email or Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="faculty@quizora.edu or faculty"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                autoFocus
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <p className="text-[11px] text-slate-400 mt-1">Default: password123</p>
            </div>

            <div className="pt-2 flex items-center gap-2">
              <button
                type="button"
                onClick={onCancel}
                className="flex-1 py-2 px-3 border border-slate-300 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading}
                className="flex-1 py-2 px-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-semibold shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
              >
                {loading ? 'Authenticating...' : 'Sign In'}
              </button>
            </div>
          </form>
        ) : (
          <form onSubmit={handleRegister} className="space-y-3">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Full Name
              </label>
              <input
                type="text"
                value={regName}
                onChange={(e) => setRegName(e.target.value)}
                placeholder="Prof. Jane Doe"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                autoFocus
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Faculty Email
              </label>
              <input
                type="email"
                value={regEmail}
                onChange={(e) => setRegEmail(e.target.value)}
                placeholder="jane.doe@university.edu"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Password
              </label>
              <input
                type="password"
                value={regPassword}
                onChange={(e) => setRegPassword(e.target.value)}
                placeholder="Min. 6 characters"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1">
                Confirm Password
              </label>
              <input
                type="password"
                value={regConfirmPassword}
                onChange={(e) => setRegConfirmPassword(e.target.value)}
                placeholder="Repeat password"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div className="pt-2 flex items-center gap-2">
              <button
                type="button"
                onClick={onCancel}
                className="flex-1 py-2 px-3 border border-slate-300 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading}
                className="flex-1 py-2 px-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-semibold shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
              >
                {loading ? 'Registering...' : 'Register Faculty'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
