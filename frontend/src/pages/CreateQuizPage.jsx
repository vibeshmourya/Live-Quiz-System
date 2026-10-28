import React, { useState, useEffect } from 'react';
import {
  fetchQuizzes,
  createQuiz,
  saveDraft,
  updateQuiz,
  createSession,
  getQuizPreview,
  deleteQuiz,
  duplicateQuiz,
} from '../services/api';
import ExamPreviewModal from '../components/ExamPreviewModal';

export default function CreateQuizPage({ onSessionCreated, onNavigate }) {
  const [existingQuizzes, setExistingQuizzes] = useState([]);
  const [loadingQuizzes, setLoadingQuizzes] = useState(true);
  const [activeTab, setActiveTab] = useState('new'); // 'new' or 'existing'

  // Quiz Editor State
  const [editingQuizId, setEditingQuizId] = useState(null);
  const [title, setTitle] = useState('');
  const [examStatus, setExamStatus] = useState('DRAFT');
  const [answerChangePolicy, setAnswerChangePolicy] = useState('SINGLE_SELECTION');
  const [requireFullscreen, setRequireFullscreen] = useState(false);
  const [monitorTabSwitch, setMonitorTabSwitch] = useState(false);
  const [restrictCopyPaste, setRestrictCopyPaste] = useState(false);
  const [warningThreshold, setWarningThreshold] = useState(3);
  const [resultsPublished, setResultsPublished] = useState(true);
  const [showCorrectAnswers, setShowCorrectAnswers] = useState(true);
  const [questions, setQuestions] = useState([
    {
      questionText: '',
      timeLimit: 20,
      marks: 10,
      options: [
        { optionLabel: 'A', optionText: '', isCorrect: true },
        { optionLabel: 'B', optionText: '', isCorrect: false },
        { optionLabel: 'C', optionText: '', isCorrect: false },
        { optionLabel: 'D', optionText: '', isCorrect: false },
      ],
    },
  ]);

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [previewQuiz, setPreviewQuiz] = useState(null);

  useEffect(() => {
    loadQuizzes();
  }, []);

  async function loadQuizzes() {
    try {
      setLoadingQuizzes(true);
      const data = await fetchQuizzes();
      setExistingQuizzes(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingQuizzes(false);
    }
  }

  // Question editing handlers
  function addQuestion() {
    setQuestions([
      ...questions,
      {
        questionText: '',
        timeLimit: 20,
        marks: 10,
        options: [
          { optionLabel: 'A', optionText: '', isCorrect: true },
          { optionLabel: 'B', optionText: '', isCorrect: false },
          { optionLabel: 'C', optionText: '', isCorrect: false },
          { optionLabel: 'D', optionText: '', isCorrect: false },
        ],
      },
    ]);
  }

  function duplicateQuestion(index) {
    const q = questions[index];
    const copy = {
      questionText: q.questionText ? `${q.questionText} (Copy)` : '',
      timeLimit: q.timeLimit,
      marks: q.marks,
      options: q.options.map((opt) => ({ ...opt })),
    };
    const updated = [...questions];
    updated.splice(index + 1, 0, copy);
    setQuestions(updated);
  }

  function removeQuestion(index) {
    if (questions.length <= 1) {
      setError('Quiz must have at least one question.');
      return;
    }
    const updated = questions.filter((_, i) => i !== index);
    setQuestions(updated);
  }

  function moveQuestion(index, direction) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= questions.length) return;
    const copy = [...questions];
    const [moved] = copy.splice(index, 1);
    copy.splice(targetIndex, 0, moved);
    setQuestions(copy);
  }

  function updateQuestionField(index, field, value) {
    const copy = [...questions];
    copy[index][field] = value;
    setQuestions(copy);
  }

  function updateOptionText(qIndex, optIndex, text) {
    const copy = [...questions];
    copy[qIndex].options[optIndex].optionText = text;
    setQuestions(copy);
  }

  function selectCorrectOption(qIndex, optIndex) {
    const copy = [...questions];
    copy[qIndex].options = copy[qIndex].options.map((opt, i) => ({
      ...opt,
      isCorrect: i === optIndex,
    }));
    setQuestions(copy);
  }

  function loadQuizIntoEditor(quiz) {
    setEditingQuizId(quiz.id);
    setTitle(quiz.title);
    setExamStatus(quiz.examStatus || 'DRAFT');
    setAnswerChangePolicy(quiz.answerChangePolicy || 'SINGLE_SELECTION');
    setRequireFullscreen(Boolean(quiz.requireFullscreen));
    setMonitorTabSwitch(Boolean(quiz.monitorTabSwitch));
    setRestrictCopyPaste(Boolean(quiz.restrictCopyPaste));
    setWarningThreshold(quiz.warningThreshold !== undefined && quiz.warningThreshold !== null ? quiz.warningThreshold : 3);
    setResultsPublished(quiz.resultsPublished !== undefined && quiz.resultsPublished !== null ? Boolean(quiz.resultsPublished) : true);
    setShowCorrectAnswers(quiz.showCorrectAnswers !== undefined && quiz.showCorrectAnswers !== null ? Boolean(quiz.showCorrectAnswers) : true);
    if (quiz.questions && quiz.questions.length > 0) {
      setQuestions(
        quiz.questions.map((q) => ({
          id: q.id,
          questionText: q.questionText || '',
          timeLimit: q.timeLimit || 20,
          marks: q.marks || 10,
          options: (q.options || []).map((o) => ({
            id: o.id,
            optionLabel: o.optionLabel,
            optionText: o.optionText || '',
            isCorrect: Boolean(o.isCorrect),
          })),
        }))
      );
    }
    setError('');
    setSuccessMessage('');
    setActiveTab('new');
  }

  function resetEditor() {
    setEditingQuizId(null);
    setTitle('');
    setExamStatus('DRAFT');
    setAnswerChangePolicy('SINGLE_SELECTION');
    setRequireFullscreen(false);
    setMonitorTabSwitch(false);
    setRestrictCopyPaste(false);
    setWarningThreshold(3);
    setResultsPublished(true);
    setShowCorrectAnswers(true);
    setQuestions([
      {
        questionText: '',
        timeLimit: 20,
        marks: 10,
        options: [
          { optionLabel: 'A', optionText: '', isCorrect: true },
          { optionLabel: 'B', optionText: '', isCorrect: false },
          { optionLabel: 'C', optionText: '', isCorrect: false },
          { optionLabel: 'D', optionText: '', isCorrect: false },
        ],
      },
    ]);
    setError('');
    setSuccessMessage('');
  }

  async function handleSaveDraft() {
    setError('');
    setSuccessMessage('');
    if (!title.trim()) {
      setError('Please provide an examination title to save a draft.');
      return;
    }

    try {
      setSaving(true);
      const payload = {
        id: editingQuizId,
        title: title.trim(),
        examStatus: 'DRAFT',
        answerChangePolicy,
        requireFullscreen,
        monitorTabSwitch,
        restrictCopyPaste,
        warningThreshold: parseInt(warningThreshold) || 3,
        resultsPublished,
        showCorrectAnswers,
        questions: questions.map((q, idx) => ({
          ...q,
          orderIndex: idx,
        })),
      };

      const saved = editingQuizId
        ? await updateQuiz(editingQuizId, payload)
        : await saveDraft(payload);

      setEditingQuizId(saved.id);
      setSuccessMessage('Draft saved persistently in database.');
      loadQuizzes();
    } catch (err) {
      setError(err.message || 'Failed to save draft');
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveAndStart() {
    setError('');
    setSuccessMessage('');
    if (!title.trim()) {
      setError('Please provide an examination title.');
      return;
    }

    for (let i = 0; i < questions.length; i++) {
      const q = questions[i];
      if (!q.questionText.trim()) {
        setError(`Question ${i + 1} text is empty.`);
        return;
      }
      for (let j = 0; j < 4; j++) {
        if (!q.options[j].optionText.trim()) {
          setError(`Option ${q.options[j].optionLabel} for Question ${i + 1} is empty.`);
          return;
        }
      }
      if (!q.options.some((o) => o.isCorrect)) {
        setError(`Question ${i + 1} has no correct option selected.`);
        return;
      }
    }

    try {
      setSaving(true);
      const payload = {
        id: editingQuizId,
        title: title.trim(),
        examStatus: 'SCHEDULED',
        answerChangePolicy,
        requireFullscreen,
        monitorTabSwitch,
        restrictCopyPaste,
        warningThreshold: parseInt(warningThreshold) || 3,
        resultsPublished,
        showCorrectAnswers,
        questions: questions.map((q, idx) => ({
          ...q,
          orderIndex: idx,
        })),
      };

      const saved = editingQuizId
        ? await updateQuiz(editingQuizId, payload)
        : await createQuiz(payload);

      // Start live examination session with frozen question set
      const session = await createSession(saved.id);
      onSessionCreated(session.pin);
    } catch (err) {
      setError(err.message || 'Failed to save and launch examination');
    } finally {
      setSaving(false);
    }
  }

  async function handleLaunchExisting(quizId) {
    try {
      setSaving(true);
      setError('');
      const session = await createSession(quizId);
      onSessionCreated(session.pin);
    } catch (err) {
      setError(err.message || 'Failed to launch live session');
    } finally {
      setSaving(false);
    }
  }

  async function handlePreview(quizId) {
    try {
      const data = await getQuizPreview(quizId);
      setPreviewQuiz(data);
    } catch (err) {
      setError(err.message || 'Failed to load examination preview');
    }
  }

  function handleCurrentPreview() {
    setPreviewQuiz({
      id: editingQuizId,
      title: title || 'Untitled Examination',
      examStatus: examStatus,
      totalMarks: questions.reduce((acc, q) => acc + (parseInt(q.marks) || 0), 0),
      questions: questions,
    });
  }

  async function handleDeleteQuiz(quizId, e) {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this examination?')) return;
    try {
      await deleteQuiz(quizId);
      loadQuizzes();
    } catch (err) {
      setError(err.message || 'Failed to delete examination');
    }
  }

  async function handleDuplicateQuiz(quizId, e) {
    if (e) e.stopPropagation();
    setError('');
    setSuccessMessage('');
    try {
      setSaving(true);
      const copy = await duplicateQuiz(quizId);
      setSuccessMessage(`Duplicated quiz successfully: "${copy.title}" created as draft.`);
      await loadQuizzes();
    } catch (err) {
      setError(err.message || 'Failed to duplicate examination');
    } finally {
      setSaving(false);
    }
  }

  const currentTotalMarks = questions.reduce((acc, q) => acc + (parseInt(q.marks) || 0), 0);

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">
            {editingQuizId ? 'Edit Examination' : 'Host a Quiz'}
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            {editingQuizId
              ? 'Update questions dynamically before the examination starts.'
              : 'Create a new examination or select an existing one to launch live.'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {editingQuizId && (
            <button
              onClick={resetEditor}
              className="text-xs font-medium text-indigo-600 hover:text-indigo-800 px-3 py-1.5 rounded border border-indigo-200 hover:bg-indigo-50"
            >
              + New Exam
            </button>
          )}
          <button
            onClick={() => onNavigate('home')}
            className="text-sm font-medium text-slate-600 hover:text-slate-900 px-3 py-1.5 rounded border border-slate-200 hover:bg-slate-50"
          >
            Cancel
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 mb-6">
        <button
          id="tab-new-quiz"
          onClick={() => setActiveTab('new')}
          className={`pb-3 px-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'new'
              ? 'border-indigo-600 text-indigo-600 font-semibold'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          {editingQuizId ? 'Edit Questions' : 'Create New Quiz'}
        </button>
        <button
          id="tab-saved-quizzes"
          onClick={() => setActiveTab('existing')}
          className={`pb-3 px-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'existing'
              ? 'border-indigo-600 text-indigo-600 font-semibold'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          Saved Quizzes ({existingQuizzes.length})
        </button>
      </div>

      {error && (
        <div className="mb-6 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-6 p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-sm text-emerald-700">
          {successMessage}
        </div>
      )}

      {activeTab === 'existing' ? (
        <div className="space-y-3">
          {loadingQuizzes ? (
            <p className="text-sm text-slate-500 py-6 text-center">Loading quizzes...</p>
          ) : existingQuizzes.length === 0 ? (
            <p className="text-sm text-slate-500 py-6 text-center">No saved quizzes found. Create one now.</p>
          ) : (
            existingQuizzes.map((quiz) => (
              <div
                key={quiz.id}
                className="bg-white border border-slate-200 rounded-lg p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-xs"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-semibold text-slate-900">{quiz.title}</h3>
                    <span
                      className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded ${
                        quiz.examStatus === 'DRAFT'
                          ? 'bg-amber-100 text-amber-800'
                          : quiz.examStatus === 'SCHEDULED'
                          ? 'bg-blue-100 text-blue-800'
                          : quiz.examStatus === 'LIVE'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      {quiz.examStatus || 'DRAFT'}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">
                    {quiz.questionCount || (quiz.questions ? quiz.questions.length : 0)} Questions
                    {' • '}
                    <span className="font-semibold text-slate-700">
                      {quiz.totalMarks || 0} Total Marks
                    </span>
                    {' • '}
                    <span className="text-indigo-600 font-medium">
                      {quiz.answerChangePolicy === 'ALLOW_RESELECTION' ? 'Allow Reselection' : 'Single Selection'}
                    </span>
                  </p>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => handlePreview(quiz.id)}
                    className="px-3 py-1.5 bg-slate-50 hover:bg-slate-100 text-slate-700 text-xs font-medium border border-slate-200 rounded-md transition-colors"
                  >
                    Preview
                  </button>
                  <button
                    type="button"
                    disabled={saving}
                    onClick={(e) => handleDuplicateQuiz(quiz.id, e)}
                    className="px-3 py-1.5 bg-slate-50 hover:bg-slate-100 text-slate-700 text-xs font-medium border border-slate-200 rounded-md transition-colors disabled:opacity-50"
                    title="Duplicate this examination into a new editable draft"
                  >
                    Duplicate
                  </button>
                  {quiz.examStatus !== 'LIVE' && quiz.examStatus !== 'COMPLETED' && (
                    <button
                      onClick={() => loadQuizIntoEditor(quiz)}
                      className="px-3 py-1.5 bg-white hover:bg-slate-50 text-indigo-600 text-xs font-medium border border-indigo-200 rounded-md transition-colors"
                    >
                      Edit Questions
                    </button>
                  )}
                  {quiz.examStatus !== 'COMPLETED' && (
                    <button
                      id={`launch-quiz-${quiz.id}`}
                      disabled={saving}
                      onClick={() => handleLaunchExisting(quiz.id)}
                      className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-medium rounded-md transition-colors disabled:opacity-50"
                    >
                      {saving ? 'Starting...' : 'Start Live Session'}
                    </button>
                  )}
                  {quiz.examStatus !== 'LIVE' && (
                    <button
                      onClick={(e) => handleDeleteQuiz(quiz.id, e)}
                      className="p-1.5 text-slate-400 hover:text-red-600 rounded"
                      title="Delete Examination"
                    >
                      ✕
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      ) : (
        <div className="space-y-6">
          {/* Quiz Title & Dynamic Metadata */}
          <div className="bg-white border border-slate-200 rounded-lg p-5 shadow-xs space-y-4">
            <div>
              <div className="flex items-center justify-between mb-1">
                <label htmlFor="quiz-title" className="block text-sm font-medium text-slate-700">
                  Examination Title
                </label>
                <span className="text-xs text-slate-500 font-mono">
                  {questions.length} Questions • {currentTotalMarks} Total Marks
                </span>
              </div>
              <input
                id="quiz-title"
                type="text"
                placeholder="e.g. Operating Systems & Cloud Computing"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>

            {/* Answer Change Policy Setting */}
            <div className="pt-3 border-t border-slate-100">
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-2">
                Answer Change Policy
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <label
                  className={`flex items-start gap-2.5 p-3 rounded-lg border text-left cursor-pointer transition-colors ${
                    answerChangePolicy === 'SINGLE_SELECTION'
                      ? 'border-indigo-600 bg-indigo-50/40 ring-1 ring-indigo-500/20'
                      : 'border-slate-200 hover:border-slate-300 bg-white'
                  }`}
                >
                  <input
                    type="radio"
                    name="answerChangePolicy"
                    value="SINGLE_SELECTION"
                    checked={answerChangePolicy === 'SINGLE_SELECTION'}
                    onChange={() => setAnswerChangePolicy('SINGLE_SELECTION')}
                    className="mt-0.5 text-indigo-600 focus:ring-indigo-500"
                  />
                  <div>
                    <span className="block text-xs font-semibold text-slate-900">Single Selection</span>
                    <span className="block text-[11px] text-slate-500 mt-0.5">
                      Answer is locked on submit. Students cannot re-select another option.
                    </span>
                  </div>
                </label>

                <label
                  className={`flex items-start gap-2.5 p-3 rounded-lg border text-left cursor-pointer transition-colors ${
                    answerChangePolicy === 'ALLOW_RESELECTION'
                      ? 'border-indigo-600 bg-indigo-50/40 ring-1 ring-indigo-500/20'
                      : 'border-slate-200 hover:border-slate-300 bg-white'
                  }`}
                >
                  <input
                    type="radio"
                    name="answerChangePolicy"
                    value="ALLOW_RESELECTION"
                    checked={answerChangePolicy === 'ALLOW_RESELECTION'}
                    onChange={() => setAnswerChangePolicy('ALLOW_RESELECTION')}
                    className="mt-0.5 text-indigo-600 focus:ring-indigo-500"
                  />
                  <div>
                    <span className="block text-xs font-semibold text-slate-900">Allow Reselection</span>
                    <span className="block text-[11px] text-slate-500 mt-0.5">
                      Students can change their answer while active until deadline.
                    </span>
                  </div>
                </label>
              </div>
            </div>

            {/* Examination Security & Integrity Settings */}
            <div className="pt-3 border-t border-slate-100">
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-2">
                Examination Security & Integrity Controls
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <label className="flex items-start gap-2.5 p-3 rounded-lg border border-slate-200 hover:border-slate-300 bg-white cursor-pointer transition-colors">
                  <input
                    type="checkbox"
                    checked={requireFullscreen}
                    onChange={(e) => setRequireFullscreen(e.target.checked)}
                    className="mt-0.5 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                  <div>
                    <span className="block text-xs font-semibold text-slate-900">Enforce Fullscreen</span>
                    <span className="block text-[11px] text-slate-500 mt-0.5">
                      Prompts and monitors student fullscreen status.
                    </span>
                  </div>
                </label>

                <label className="flex items-start gap-2.5 p-3 rounded-lg border border-slate-200 hover:border-slate-300 bg-white cursor-pointer transition-colors">
                  <input
                    type="checkbox"
                    checked={monitorTabSwitch}
                    onChange={(e) => setMonitorTabSwitch(e.target.checked)}
                    className="mt-0.5 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                  <div>
                    <span className="block text-xs font-semibold text-slate-900">Monitor Tab Switching</span>
                    <span className="block text-[11px] text-slate-500 mt-0.5">
                      Detects when student leaves exam window or tab.
                    </span>
                  </div>
                </label>

                <label className="flex items-start gap-2.5 p-3 rounded-lg border border-slate-200 hover:border-slate-300 bg-white cursor-pointer transition-colors">
                  <input
                    type="checkbox"
                    checked={restrictCopyPaste}
                    onChange={(e) => setRestrictCopyPaste(e.target.checked)}
                    className="mt-0.5 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                  <div>
                    <span className="block text-xs font-semibold text-slate-900">Restrict Copy / Paste</span>
                    <span className="block text-[11px] text-slate-500 mt-0.5">
                      Disables selection and blocks clipboard shortcuts.
                    </span>
                  </div>
                </label>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-3 pt-3 border-t border-slate-100 items-center">
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">
                    Warning Threshold (Incidents)
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="10"
                    value={warningThreshold}
                    onChange={(e) => setWarningThreshold(Math.max(1, parseInt(e.target.value) || 1))}
                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  />
                </div>

                <label className="flex items-center gap-2 text-xs text-slate-700 cursor-pointer pt-4">
                  <input
                    type="checkbox"
                    checked={resultsPublished}
                    onChange={(e) => setResultsPublished(e.target.checked)}
                    className="text-indigo-600 rounded focus:ring-indigo-500"
                  />
                  <span>Publish Results to Students</span>
                </label>

                <label className="flex items-center gap-2 text-xs text-slate-700 cursor-pointer pt-4">
                  <input
                    type="checkbox"
                    checked={showCorrectAnswers}
                    onChange={(e) => setShowCorrectAnswers(e.target.checked)}
                    className="text-indigo-600 rounded focus:ring-indigo-500"
                  />
                  <span>Reveal Correct Answers</span>
                </label>
              </div>
            </div>
          </div>

          {/* Question List */}
          <div className="space-y-5">
            {questions.map((q, qIndex) => (
              <div
                key={q.id || qIndex}
                className="bg-white border border-slate-200 rounded-lg p-5 shadow-xs"
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="font-bold text-sm text-slate-800">
                    Question {qIndex + 1}
                  </span>
                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => duplicateQuestion(qIndex)}
                      className="text-xs px-2 py-1 text-slate-600 border border-slate-200 rounded hover:bg-slate-50"
                      title="Duplicate Question"
                    >
                      Duplicate
                    </button>
                    <button
                      type="button"
                      disabled={qIndex === 0}
                      onClick={() => moveQuestion(qIndex, -1)}
                      className="text-xs px-2 py-1 text-slate-600 border border-slate-200 rounded hover:bg-slate-50 disabled:opacity-30"
                      title="Move Up"
                    >
                      ▲
                    </button>
                    <button
                      type="button"
                      disabled={qIndex === questions.length - 1}
                      onClick={() => moveQuestion(qIndex, 1)}
                      className="text-xs px-2 py-1 text-slate-600 border border-slate-200 rounded hover:bg-slate-50 disabled:opacity-30"
                      title="Move Down"
                    >
                      ▼
                    </button>
                    <button
                      type="button"
                      onClick={() => removeQuestion(qIndex)}
                      className="text-xs px-2 py-1 text-red-600 border border-red-200 rounded hover:bg-red-50 ml-1"
                      title="Delete Question"
                    >
                      Delete
                    </button>
                  </div>
                </div>

                <div className="mb-4">
                  <input
                    type="text"
                    placeholder="Enter question text..."
                    value={q.questionText}
                    onChange={(e) => updateQuestionField(qIndex, 'questionText', e.target.value)}
                    className="w-full px-3.5 py-2 border border-slate-300 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>

                {/* Timing and Marks */}
                <div className="grid grid-cols-2 gap-4 mb-4">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">
                      Time Limit (seconds)
                    </label>
                    <input
                      type="number"
                      min="5"
                      max="120"
                      value={q.timeLimit}
                      onChange={(e) => updateQuestionField(qIndex, 'timeLimit', parseInt(e.target.value) || 20)}
                      className="w-full px-3 py-1.5 border border-slate-300 rounded-md text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">
                      Marks / Points
                    </label>
                    <input
                      type="number"
                      min="1"
                      max="100"
                      value={q.marks}
                      onChange={(e) => updateQuestionField(qIndex, 'marks', parseInt(e.target.value) || 10)}
                      className="w-full px-3 py-1.5 border border-slate-300 rounded-md text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>
                </div>

                {/* Answer Options */}
                <div>
                  <div className="text-xs font-medium text-slate-600 mb-2">
                    Answer Options (Click radio button to mark correct answer):
                  </div>
                  <div className="space-y-2">
                    {q.options.map((opt, optIndex) => (
                      <div
                        key={opt.optionLabel}
                        className={`flex items-center gap-2.5 p-2 rounded-md border ${
                          opt.isCorrect
                            ? 'bg-emerald-50/60 border-emerald-300'
                            : 'bg-slate-50 border-slate-200'
                        }`}
                      >
                        <input
                          type="radio"
                          id={`correct-${qIndex}-${optIndex}`}
                          name={`correct-${qIndex}`}
                          checked={opt.isCorrect}
                          onChange={() => selectCorrectOption(qIndex, optIndex)}
                          className="w-4 h-4 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                        />
                        <label
                          htmlFor={`correct-${qIndex}-${optIndex}`}
                          className="font-bold text-xs text-slate-700 w-4 cursor-pointer"
                        >
                          {opt.optionLabel}
                        </label>
                        <input
                          type="text"
                          placeholder={`Option ${opt.optionLabel} text...`}
                          value={opt.optionText}
                          onChange={(e) => updateOptionText(qIndex, optIndex, e.target.value)}
                          className="flex-1 px-3 py-1.5 bg-white border border-slate-300 rounded text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                        {opt.isCorrect && (
                          <span className="text-[11px] font-semibold text-emerald-700 uppercase tracking-wider px-2 py-0.5 bg-emerald-100 rounded">
                            Correct
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Action Buttons */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 pt-2">
            <div className="flex items-center gap-2">
              <button
                id="add-question-btn"
                type="button"
                onClick={addQuestion}
                className="px-4 py-2 bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-300 rounded-lg transition-colors shadow-xs"
              >
                + Add Question
              </button>

              <button
                type="button"
                onClick={handleCurrentPreview}
                className="px-4 py-2 bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-300 rounded-lg transition-colors shadow-xs"
              >
                Preview Exam
              </button>
            </div>

            <div className="flex items-center gap-2.5">
              <button
                id="save-draft-btn"
                type="button"
                disabled={saving}
                onClick={handleSaveDraft}
                className="px-4 py-2 bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-300 rounded-lg transition-colors shadow-xs disabled:opacity-50"
              >
                {saving ? 'Saving...' : 'Save Draft'}
              </button>

              <button
                id="save-and-start-btn"
                type="button"
                disabled={saving}
                onClick={handleSaveAndStart}
                className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg transition-colors shadow-sm disabled:opacity-50"
              >
                {saving ? 'Creating Session...' : 'Save & Start Live Session'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confidential Examination Preview Modal */}
      {previewQuiz && (
        <ExamPreviewModal
          quiz={previewQuiz}
          onClose={() => setPreviewQuiz(null)}
        />
      )}
    </div>
  );
}
