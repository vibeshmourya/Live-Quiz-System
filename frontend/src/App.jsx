import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import HomePage from './pages/HomePage';
import CreateQuizPage from './pages/CreateQuizPage';
import JoinQuizPage from './pages/JoinQuizPage';
import HostLivePage from './pages/HostLivePage';
import CandidateQuizPage from './pages/CandidateQuizPage';
import ResultsPage from './pages/ResultsPage';
import FacultyLoginModal from './components/FacultyLoginModal';
import { getFacultyUser, logoutFaculty, checkFacultySession } from './services/api';

export default function App() {
  const [screen, setScreen] = useState('home');
  const [activePin, setActivePin] = useState('');
  const [candidateInfo, setCandidateInfo] = useState(null);
  const [finalResults, setFinalResults] = useState(null);
  const [isHost, setIsHost] = useState(false);
  const [facultyUser, setFacultyUser] = useState(getFacultyUser());
  const [showLoginModal, setShowLoginModal] = useState(false);

  // Validate faculty session on mount and listen for authorization invalidation
  useEffect(() => {
    async function verifyAuth() {
      if (facultyUser) {
        const verified = await checkFacultySession();
        if (verified) {
          setFacultyUser(verified);
        } else {
          setFacultyUser(null);
        }
      }
    }
    verifyAuth();

    function handleUnauthorized() {
      setFacultyUser(null);
    }
    window.addEventListener('quizora:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('quizora:unauthorized', handleUnauthorized);
  }, []);

  // Parse URL on initial load for deep join links (e.g. /join?pin=123456) or recover active session
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const pinFromUrl = params.get('pin');
    const path = window.location.pathname;

    if (pinFromUrl || path === '/join') {
      setActivePin(pinFromUrl || '');
      setScreen('join');
      return;
    }

    try {
      const savedCandidate = sessionStorage.getItem('quizora_candidate_session');
      if (savedCandidate) {
        const parsed = JSON.parse(savedCandidate);
        if (parsed && parsed.pin && parsed.candidateId) {
          setActivePin(parsed.pin);
          setCandidateInfo(parsed);
          setIsHost(false);
          setScreen('candidate_quiz');
          return;
        }
      }

      const savedHostPin = sessionStorage.getItem('quizora_host_pin');
      if (savedHostPin && facultyUser) {
        setActivePin(savedHostPin);
        setIsHost(true);
        setScreen('host_live');
      }
    } catch (e) {
      // ignore
    }
  }, []);

  function handleNavigate(targetScreen) {
    if (targetScreen === 'home') {
      setActivePin('');
      setCandidateInfo(null);
      setFinalResults(null);
      setIsHost(false);
      try {
        sessionStorage.removeItem('quizora_candidate_session');
        sessionStorage.removeItem('quizora_host_pin');
      } catch (e) {}
      window.history.replaceState({}, '', '/');
      setScreen('home');
      return;
    }

    if (targetScreen === 'create') {
      if (!facultyUser) {
        setShowLoginModal(true);
        return;
      }
    }

    setScreen(targetScreen);
  }

  function handleFacultyLoginSuccess(user) {
    setFacultyUser(user);
    setShowLoginModal(false);
    setScreen('create');
  }

  async function handleFacultyLogout() {
    await logoutFaculty();
    setFacultyUser(null);
    try {
      sessionStorage.removeItem('quizora_host_pin');
    } catch (e) {}
    handleNavigate('home');
  }

  function handleSessionCreated(pin) {
    setActivePin(pin);
    setIsHost(true);
    try {
      sessionStorage.setItem('quizora_host_pin', pin);
    } catch (e) {}
    setScreen('host_live');
  }

  function handleCandidateJoined(info) {
    setActivePin(info.pin);
    setCandidateInfo(info);
    setIsHost(false);
    try {
      sessionStorage.setItem('quizora_candidate_session', JSON.stringify(info));
    } catch (e) {}
    setScreen('candidate_quiz');
  }

  function handleQuizFinished(results) {
    try {
      sessionStorage.removeItem('quizora_candidate_session');
      sessionStorage.removeItem('quizora_host_pin');
    } catch (e) {}
    setFinalResults(results);
    setScreen('results');
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans text-slate-900">
      <Navbar
        onNavigate={handleNavigate}
        currentScreen={screen}
        pin={activePin}
        facultyUser={facultyUser}
        onLogout={handleFacultyLogout}
      />

      {screen === 'home' && (
        <HomePage onNavigate={handleNavigate} />
      )}

      {screen === 'create' && (
        <CreateQuizPage
          onSessionCreated={handleSessionCreated}
          onNavigate={handleNavigate}
        />
      )}

      {screen === 'join' && (
        <JoinQuizPage
          initialPin={activePin}
          onJoined={handleCandidateJoined}
          onNavigate={handleNavigate}
        />
      )}

      {screen === 'host_live' && (
        <HostLivePage
          pin={activePin}
          onEndQuiz={handleQuizFinished}
          onNavigate={handleNavigate}
        />
      )}

      {screen === 'candidate_quiz' && candidateInfo && (
        <CandidateQuizPage
          sessionInfo={candidateInfo}
          onFinished={handleQuizFinished}
          onNavigate={handleNavigate}
        />
      )}

      {screen === 'results' && (
        <ResultsPage
          results={finalResults}
          isHost={isHost}
          candidateInfo={candidateInfo}
          pin={activePin}
          onNavigate={handleNavigate}
        />
      )}

      {showLoginModal && (
        <FacultyLoginModal
          onSuccess={handleFacultyLoginSuccess}
          onCancel={() => setShowLoginModal(false)}
        />
      )}
    </div>
  );
}
