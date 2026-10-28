import React, { useEffect, useState } from 'react';
import confetti from 'canvas-confetti';
import {
  downloadExamPdf,
  downloadExamCsv,
  downloadIncidentPdf,
  downloadStudentPdf,
} from '../services/api';

export default function ResultsPage({ results, isHost, onNavigate, candidateInfo, pin }) {
  const [downloading, setDownloading] = useState(false);
  const [exportError, setExportError] = useState('');

  const resultsPublished = results?.resultsPublished !== false;
  const isCandidate = !isHost;

  useEffect(() => {
    // Only launch celebratory confetti if results are officially published
    if (resultsPublished) {
      try {
        confetti({
          particleCount: 80,
          spread: 60,
          origin: { y: 0.6 },
        });
      } catch (e) {
        // ignore
      }
    }
  }, [resultsPublished]);

  const leaderboard = results?.leaderboard || [];

  async function handleDownloadExamPdf() {
    if (!pin) return;
    try {
      setDownloading(true);
      setExportError('');
      await downloadExamPdf(pin);
    } catch (err) {
      setExportError(err.message || 'Failed to download exam PDF');
    } finally {
      setDownloading(false);
    }
  }

  async function handleDownloadExamCsv() {
    if (!pin) return;
    try {
      setDownloading(true);
      setExportError('');
      await downloadExamCsv(pin);
    } catch (err) {
      setExportError(err.message || 'Failed to download exam CSV');
    } finally {
      setDownloading(false);
    }
  }

  async function handleDownloadIncidentPdf() {
    if (!pin) return;
    try {
      setDownloading(true);
      setExportError('');
      await downloadIncidentPdf(pin);
    } catch (err) {
      setExportError(err.message || 'Failed to download incident summary');
    } finally {
      setDownloading(false);
    }
  }

  async function handleDownloadStudentPdf(candidateId) {
    if (!pin || !candidateId) return;
    try {
      setDownloading(true);
      setExportError('');
      await downloadStudentPdf(pin, candidateId);
    } catch (err) {
      setExportError(err.message || 'Failed to download student PDF');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <main className="flex-1 max-w-2xl mx-auto px-4 py-8 w-full flex flex-col items-center">
      <div className="w-full text-center mb-6">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Examination Completed
        </span>
        <h1 className="text-2xl font-bold text-slate-900 mt-1">
          {isHost ? 'Examination Results & Audit' : 'Final Results'}
        </h1>
      </div>

      {exportError && (
        <div className="w-full mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
          {exportError}
        </div>
      )}

      {/* Case 1: Student viewing unpublished results */}
      {isCandidate && !resultsPublished && (
        <div className="w-full bg-white border border-slate-200 rounded-xl p-8 mb-6 shadow-xs text-center">
          <div className="w-12 h-12 bg-amber-50 border border-amber-200 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-3 text-lg font-bold">
            🔒
          </div>
          <h2 className="text-base font-bold text-slate-900 mb-1">
            Submission Confirmed
          </h2>
          <p className="text-xs text-slate-600 max-w-md mx-auto leading-relaxed">
            Your examination attempt was successfully received and scored authoritatively by Quizora. Results and score details will be released once the examiner authorizes publication.
          </p>
          <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-center gap-2 text-xs text-slate-500">
            <span>Participant:</span>
            <span className="font-semibold text-slate-800">{candidateInfo?.nickname || results?.nickname}</span>
          </div>
        </div>
      )}

      {/* Case 2: Candidate Individual Performance Card (when published) */}
      {isCandidate && resultsPublished && (
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

      {/* Leaderboard Table (Shown if host, or if candidate with published results) */}
      {(isHost || resultsPublished) && (
        <div className="w-full bg-white border border-slate-200 rounded-xl p-5 shadow-xs mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-bold uppercase tracking-wider text-slate-700">
              Final Examination Roster
            </h2>
            {isHost && (
              <span className="text-xs font-semibold text-indigo-600">
                {leaderboard.length} candidates
              </span>
            )}
          </div>

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

                    <div className="flex items-center gap-3">
                      <span className="text-sm font-mono font-bold text-indigo-700">
                        {entry.score} pts
                      </span>
                      {isHost && entry.candidateId && (
                        <button
                          type="button"
                          disabled={downloading}
                          onClick={() => handleDownloadStudentPdf(entry.candidateId)}
                          className="text-[11px] font-medium text-slate-500 hover:text-indigo-600 hover:underline disabled:opacity-50"
                          title="Download individual candidate PDF report"
                        >
                          PDF Report
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Faculty-Only Academic Reports & Exports Section */}
      {isHost && (
        <div className="w-full bg-white border border-slate-200 rounded-xl p-5 shadow-xs mb-6">
          <div className="mb-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Faculty Examination Exports
            </h3>
            <p className="text-[11px] text-slate-500 mt-0.5">
              Generate authoritative academic reports and incident logs. Downloads are strictly restricted to faculty.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
            <button
              id="export-pdf-btn"
              type="button"
              disabled={downloading || !pin}
              onClick={handleDownloadExamPdf}
              className="py-2.5 px-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              <span>📄</span>
              <span>{downloading ? 'Exporting...' : 'Results (PDF)'}</span>
            </button>

            <button
              id="export-csv-btn"
              type="button"
              disabled={downloading || !pin}
              onClick={handleDownloadExamCsv}
              className="py-2.5 px-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              <span>📊</span>
              <span>{downloading ? 'Exporting...' : 'Results (CSV)'}</span>
            </button>

            <button
              id="export-incidents-btn"
              type="button"
              disabled={downloading || !pin}
              onClick={handleDownloadIncidentPdf}
              className="py-2.5 px-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              <span>🛡</span>
              <span>{downloading ? 'Exporting...' : 'Incidents (PDF)'}</span>
            </button>
          </div>
        </div>
      )}

      {/* Navigation Actions */}
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

