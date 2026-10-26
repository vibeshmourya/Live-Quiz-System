import React from 'react';

export default function ExamPreviewModal({ quiz, onClose }) {
  if (!quiz) return null;

  const totalMarks = quiz.totalMarks || (quiz.questions || []).reduce((acc, q) => acc + (q.marks || 0), 0);
  const questions = quiz.questions || [];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <div className="bg-white border border-slate-200 rounded-2xl max-w-2xl w-full max-h-[85vh] flex flex-col shadow-xl">
        {/* Header */}
        <div className="p-5 border-b border-slate-200 flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-indigo-50 text-indigo-700">
                Confidential Examination Preview
              </span>
              <span className={`text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded ${
                quiz.examStatus === 'DRAFT'
                  ? 'bg-amber-100 text-amber-800'
                  : quiz.examStatus === 'SCHEDULED'
                  ? 'bg-blue-100 text-blue-800'
                  : quiz.examStatus === 'LIVE'
                  ? 'bg-emerald-100 text-emerald-800'
                  : 'bg-slate-100 text-slate-700'
              }`}>
                {quiz.examStatus || 'DRAFT'}
              </span>
            </div>
            <h2 className="text-xl font-bold text-slate-900">{quiz.title}</h2>
            <div className="flex items-center gap-4 text-xs text-slate-500 mt-1">
              <span>{questions.length} Questions</span>
              <span>•</span>
              <span className="font-semibold text-slate-700">{totalMarks} Total Marks</span>
              <span>•</span>
              <span className="font-medium text-indigo-700">
                {quiz.answerChangePolicy === 'ALLOW_RESELECTION' ? 'Allow Reselection' : 'Single Selection'}
              </span>
              {quiz.facultyUsername && (
                <>
                  <span>•</span>
                  <span>Faculty: {quiz.facultyUsername}</span>
                </>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-2xl leading-none px-2"
          >
            ×
          </button>
        </div>

        {/* Content list */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {questions.length === 0 ? (
            <p className="text-xs text-slate-400 text-center py-6">No questions added yet.</p>
          ) : (
            questions.map((q, idx) => (
              <div key={q.id || idx} className="border border-slate-200 rounded-xl p-4 bg-slate-50/50">
                <div className="flex items-center justify-between text-xs text-slate-500 mb-2">
                  <span className="font-bold text-slate-700">Question {idx + 1}</span>
                  <div className="flex gap-2 font-mono">
                    <span>{q.marks || 10} pts</span>
                    <span>•</span>
                    <span>{q.timeLimit || 20}s</span>
                  </div>
                </div>
                <p className="text-sm font-semibold text-slate-900 mb-3">{q.questionText}</p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {q.options?.map((opt) => (
                    <div
                      key={opt.optionLabel}
                      className={`text-xs p-2.5 rounded-lg border flex items-center gap-2 ${
                        opt.isCorrect
                          ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-medium'
                          : 'bg-white border-slate-200 text-slate-700'
                      }`}
                    >
                      <span
                        className={`w-5 h-5 rounded flex items-center justify-center font-bold text-[11px] ${
                          opt.isCorrect ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {opt.optionLabel}
                      </span>
                      <span className="flex-1">{opt.optionText}</span>
                      {opt.isCorrect && (
                        <span className="text-[10px] font-bold uppercase text-emerald-700 bg-emerald-100 px-1.5 py-0.5 rounded">
                          Answer
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-200 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-900 text-white text-xs font-semibold rounded-lg transition-colors"
          >
            Close Preview
          </button>
        </div>
      </div>
    </div>
  );
}
