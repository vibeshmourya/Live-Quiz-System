package com.quizlive.service;

import com.quizlive.dto.*;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final QuizRepository quizRepository;
    private final ParticipantRepository participantRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamIncidentRepository examIncidentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final Logger log = LoggerFactory.getLogger(LiveSessionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            QuizRepository quizRepository,
            ParticipantRepository participantRepository,
            SubmissionRepository submissionRepository,
            ExamIncidentRepository examIncidentRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.quizRepository = quizRepository;
        this.participantRepository = participantRepository;
        this.submissionRepository = submissionRepository;
        this.examIncidentRepository = examIncidentRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private String generateUniquePin() {
        for (int i = 0; i < 50; i++) {
            int code = 100000 + RANDOM.nextInt(900000);
            String pin = String.valueOf(code);
            if (!liveSessionRepository.existsByPin(pin)) {
                return pin;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Transactional
    public LiveSession createLiveSession(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + quizId));

        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("Cannot start live session for a quiz with no questions");
        }

        quiz.setExamStatus(ExamStatus.LIVE);
        quiz.setUpdatedAt(LocalDateTime.now());
        quizRepository.save(quiz);

        String pin = generateUniquePin();
        LiveSession session = new LiveSession(pin, quiz);
        return liveSessionRepository.save(session);
    }

    @Transactional
    public Participant joinSession(JoinRequest request) {
        LiveSession session = liveSessionRepository.findByPin(request.getSessionPin())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Session PIN: " + request.getSessionPin()));

        if (session.getStatus() == SessionStatus.FINISHED) {
            throw new IllegalStateException("This quiz session has already finished");
        }

        String candidateId = request.getCandidateId();
        if (candidateId == null || candidateId.trim().isEmpty()) {
            candidateId = UUID.randomUUID().toString();
        }

        String nickname = request.getNickname() != null && !request.getNickname().trim().isEmpty()
                ? request.getNickname().trim()
                : "Player " + candidateId.substring(0, 4);

        Optional<Participant> existing = participantRepository.findBySessionPinAndCandidateId(session.getPin(), candidateId);
        Participant participant;
        if (existing.isPresent()) {
            participant = existing.get();
            participant.setNickname(nickname);
            participant.setConnected(true);
        } else {
            participant = new Participant(session, session.getPin(), candidateId, nickname);
        }

        participant = participantRepository.save(participant);

        long participantCount = participantRepository.countBySessionPin(session.getPin());
        List<ParticipantDto> participantList = getSessionParticipants(session.getPin());

        // Broadcast to both host and candidates topic
        Map<String, Object> joinPayload = new HashMap<>();
        joinPayload.put("participantCount", participantCount);
        joinPayload.put("nickname", participant.getNickname());
        joinPayload.put("candidateId", participant.getCandidateId());
        joinPayload.put("participants", participantList);

        messagingTemplate.convertAndSend("/topic/session/" + session.getPin(),
                new WsMessage("PARTICIPANT_JOINED", joinPayload));

        return participant;
    }

    @Transactional(readOnly = true)
    public List<ParticipantDto> getSessionParticipants(String pin) {
        List<Participant> participants = participantRepository.findBySessionPinOrderByScoreDesc(pin);
        List<ParticipantDto> list = new ArrayList<>();
        for (Participant p : participants) {
            list.add(new ParticipantDto(
                    p.getId(),
                    p.getCandidateId(),
                    p.getNickname(),
                    p.getScore(),
                    p.getConnected(),
                    p.getRulesAcknowledged(),
                    p.getJoinedAt()
            ));
        }
        return list;
    }

    @Transactional
    public Participant acknowledgeRules(String pin, String candidateId) {
        Participant participant = participantRepository.findBySessionPinAndCandidateId(pin, candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not recognized for session: " + pin));

        participant.setRulesAcknowledged(true);
        participant = participantRepository.save(participant);

        List<ParticipantDto> participantList = getSessionParticipants(pin);
        Map<String, Object> ackPayload = new HashMap<>();
        ackPayload.put("candidateId", candidateId);
        ackPayload.put("nickname", participant.getNickname());
        ackPayload.put("rulesAcknowledged", true);
        ackPayload.put("participants", participantList);

        messagingTemplate.convertAndSend("/topic/session/" + pin,
                new WsMessage("RULES_ACKNOWLEDGED", ackPayload));

        return participant;
    }

    @Transactional
    public SessionStateResponse startQuestion(String pin, int questionIndex) {
        String lockKey = ("session-nav:" + pin).intern();
        synchronized (lockKey) {
            LiveSession session = liveSessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

            Quiz quiz = session.getQuiz();
            List<Question> questions = quiz.getQuestions();
            if (questionIndex < 0 || questionIndex >= questions.size()) {
                throw new IllegalArgumentException("Invalid question index: " + questionIndex);
            }

            Question currentQuestion = questions.get(questionIndex);
            long now = System.currentTimeMillis();
            long durationMs = currentQuestion.getTimeLimit() * 1000L;

            session.setStatus(SessionStatus.QUESTION_ACTIVE);
            session.setCurrentQuestionIndex(questionIndex);
            session.setQuestionStartedAtEpochMs(now);
            session.setQuestionEndsAtEpochMs(now + durationMs);

            liveSessionRepository.saveAndFlush(session);

            // Broadcast QUESTION_STARTED event to candidates
            CandidateQuestionDto candidateQ = toCandidateQuestionDto(currentQuestion);
            Map<String, Object> questionPayload = new HashMap<>();
            questionPayload.put("questionIndex", questionIndex);
            questionPayload.put("totalQuestions", questions.size());
            questionPayload.put("question", candidateQ);
            questionPayload.put("startedAt", now);
            questionPayload.put("endsAt", now + durationMs);
            questionPayload.put("timeLimit", currentQuestion.getTimeLimit());
            questionPayload.put("allowReselection", session.getQuiz().getAnswerChangePolicy() == AnswerChangePolicy.ALLOW_RESELECTION);
            questionPayload.put("requireFullscreen", session.getQuiz().getRequireFullscreen());
            questionPayload.put("monitorTabSwitch", session.getQuiz().getMonitorTabSwitch());
            questionPayload.put("restrictCopyPaste", session.getQuiz().getRestrictCopyPaste());
            questionPayload.put("warningThreshold", session.getQuiz().getWarningThreshold());
            questionPayload.put("stateVersion", ((long) questionIndex * 10L) + 1L);

            // Deliver authoritative host session state to host topic
            SessionStateResponse hostState = getHostSessionState(pin);

            sendAfterCommit(() -> {
                messagingTemplate.convertAndSend("/topic/session/" + pin,
                        new WsMessage("QUESTION_STARTED", questionPayload));
                messagingTemplate.convertAndSend("/topic/session/" + pin + "/host",
                        new WsMessage("HOST_STATE_CHANGED", hostState));
            });

            return hostState;
        }
    }

    @Transactional
    public Submission submitAnswer(SubmitAnswerRequest request) {
        LiveSession session = liveSessionRepository.findByPin(request.getSessionPin())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + request.getSessionPin()));

        if (session.getStatus() != SessionStatus.QUESTION_ACTIVE) {
            throw new IllegalStateException("Submissions are only accepted while the question is active");
        }

        long now = System.currentTimeMillis();
        if (session.getQuestionEndsAtEpochMs() != null && now > session.getQuestionEndsAtEpochMs() + 2500) {
            throw new IllegalStateException("Time has expired for this question");
        }

        Participant participant = participantRepository.findBySessionPinAndCandidateId(session.getPin(), request.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Participant not recognized for this session"));

        if (Boolean.FALSE.equals(participant.getRulesAcknowledged())) {
            throw new IllegalStateException("Mandatory examination rules must be acknowledged before submitting answers");
        }

        Question currentQuestion = session.getQuiz().getQuestions().get(session.getCurrentQuestionIndex());
        if (!currentQuestion.getId().equals(request.getQuestionId())) {
            throw new IllegalArgumentException("Submitted question does not match current active question");
        }

        boolean alreadySubmitted = submissionRepository.existsByLiveSessionIdAndParticipantIdAndQuestionId(
                session.getId(), participant.getId(), currentQuestion.getId());

        AnswerChangePolicy policy = session.getQuiz().getAnswerChangePolicy();
        if (alreadySubmitted && policy != AnswerChangePolicy.ALLOW_RESELECTION) {
            throw new IllegalStateException("Answer is locked. Reselection is not permitted for this examination.");
        }

        String label = request.getOptionLabel().trim().toUpperCase();
        AnswerOption selectedOption = currentQuestion.getOptions().stream()
                .filter(o -> o.getOptionLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid option label: " + label));

        boolean isCorrect = Boolean.TRUE.equals(selectedOption.getIsCorrect());
        int marks = isCorrect ? currentQuestion.getMarks() : 0;

        Submission submission;
        String lockKey = (session.getId() + ":" + participant.getId() + ":" + currentQuestion.getId()).intern();
        synchronized (lockKey) {
            Optional<Submission> existingSub = submissionRepository.findByLiveSessionIdAndParticipantIdAndQuestionId(
                    session.getId(), participant.getId(), currentQuestion.getId());

            if (existingSub.isPresent()) {
                if (policy != AnswerChangePolicy.ALLOW_RESELECTION) {
                    return existingSub.get();
                }
                submission = existingSub.get();
                int previousMarks = submission.getMarksAwarded() != null ? submission.getMarksAwarded() : 0;
                participant.setScore(Math.max(0, participant.getScore() - previousMarks + marks));
                participantRepository.save(participant);

                submission.setSelectedOption(selectedOption);
                submission.setSelectedLabel(label);
                submission.setIsCorrect(isCorrect);
                submission.setMarksAwarded(marks);
                submission.setResponseTimeMillis(now - (session.getQuestionStartedAtEpochMs() != null ? session.getQuestionStartedAtEpochMs() : now));
                submission.setSubmittedAt(LocalDateTime.now());
                submission = submissionRepository.save(submission);
            } else {
                submission = new Submission();
                submission.setLiveSession(session);
                submission.setParticipant(participant);
                submission.setQuestion(currentQuestion);
                submission.setSelectedOption(selectedOption);
                submission.setSelectedLabel(label);
                submission.setIsCorrect(isCorrect);
                submission.setMarksAwarded(marks);
                submission.setResponseTimeMillis(now - (session.getQuestionStartedAtEpochMs() != null ? session.getQuestionStartedAtEpochMs() : now));
                submission.setSubmittedAt(LocalDateTime.now());
                submission = submissionRepository.save(submission);

                if (isCorrect) {
                    participant.setScore(participant.getScore() + marks);
                    participantRepository.save(participant);
                }
            }
        }

        // Broadcast stats update to host instantly
        OptionStatsDto stats = getOptionStats(session.getId(), currentQuestion.getId());
        Map<String, Object> statsPayload = new HashMap<>();
        statsPayload.put("counts", stats.getCounts());
        statsPayload.put("totalSubmissions", stats.getTotalSubmissions());
        statsPayload.put("participantCount", participantRepository.countBySessionPin(session.getPin()));

        messagingTemplate.convertAndSend("/topic/session/" + session.getPin() + "/stats",
                new WsMessage("STATS_UPDATED", statsPayload));

        return submission;
    }

    @Transactional
    public SessionStateResponse revealAnswer(String pin) {
        String lockKey = ("session-nav:" + pin).intern();
        synchronized (lockKey) {
            LiveSession session = liveSessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

            session.setStatus(SessionStatus.QUESTION_REVEALED);
            liveSessionRepository.saveAndFlush(session);

            Question currentQuestion = session.getQuiz().getQuestions().get(session.getCurrentQuestionIndex());
            String correctLabel = findAuthoritativeCorrectOptionLabel(currentQuestion);

            List<Submission> submissions = submissionRepository.findByLiveSessionIdAndQuestionId(session.getId(), currentQuestion.getId());
            List<Participant> participants = participantRepository.findBySessionPinOrderByScoreDesc(session.getPin());

            Map<String, Map<String, Object>> candidateVerdicts = new HashMap<>();
            for (Participant p : participants) {
                if (p.getCandidateId() != null) {
                    Map<String, Object> v = new HashMap<>();
                    v.put("candidateId", p.getCandidateId());
                    v.put("hasSubmitted", false);
                    v.put("selectedOptionLabel", null);
                    v.put("wasCorrect", false);
                    candidateVerdicts.put(p.getCandidateId(), v);
                }
            }

            for (Submission sub : submissions) {
                if (sub.getParticipant() != null && sub.getParticipant().getCandidateId() != null) {
                    String cid = sub.getParticipant().getCandidateId();
                    Map<String, Object> v = candidateVerdicts.computeIfAbsent(cid, k -> new HashMap<>());
                    v.put("candidateId", cid);
                    v.put("hasSubmitted", true);
                    v.put("selectedOptionLabel", sub.getSelectedLabel());
                    v.put("wasCorrect", Boolean.TRUE.equals(sub.getIsCorrect()));
                }
            }

            long totalSubmissions = submissions.size();
            long correctSubmissions = submissions.stream().filter(s -> Boolean.TRUE.equals(s.getIsCorrect())).count();
            double correctPercent = totalSubmissions > 0 ? ((double) correctSubmissions / totalSubmissions) * 100.0 : 0.0;

            OptionStatsDto stats = getOptionStats(session.getId(), currentQuestion.getId());

            Map<String, Object> revealPayload = new HashMap<>();
            revealPayload.put("questionIndex", session.getCurrentQuestionIndex());
            revealPayload.put("questionId", currentQuestion.getId());
            revealPayload.put("correctOptionLabel", correctLabel);
            revealPayload.put("revealedCorrectLabel", correctLabel);
            revealPayload.put("correctAnswer", correctLabel);
            revealPayload.put("showCorrectAnswers", true);
            revealPayload.put("correctPercentage", Math.round(correctPercent * 10.0) / 10.0);
            revealPayload.put("counts", stats.getCounts());
            revealPayload.put("totalSubmissions", totalSubmissions);
            revealPayload.put("candidateVerdicts", candidateVerdicts);
            revealPayload.put("stateVersion", ((long) session.getCurrentQuestionIndex() * 10L) + 5L);

            log.info("[REVEAL_ANSWER] PIN={}, QuestionId={}, CorrectOption={}, TotalSubmissions={}, CorrectSubmissions={}, CandidateVerdicts={}, Destination={}",
                    pin, currentQuestion.getId(), correctLabel, totalSubmissions, correctSubmissions, candidateVerdicts.size(), "/topic/session/" + pin);

            SessionStateResponse hostState = getHostSessionState(pin);

            sendAfterCommit(() -> {
                messagingTemplate.convertAndSend("/topic/session/" + pin,
                        new WsMessage("QUESTION_REVEALED", revealPayload));
                messagingTemplate.convertAndSend("/topic/session/" + pin + "/host",
                        new WsMessage("HOST_STATE_CHANGED", hostState));
            });

            return hostState;
        }
    }

    @Transactional
    public SessionStateResponse advanceOrEnd(String pin) {
        return advanceOrEnd(pin, null);
    }

    @Transactional
    public SessionStateResponse advanceOrEnd(String pin, Integer expectedCurrentIndex) {
        String lockKey = ("session-nav:" + pin).intern();
        synchronized (lockKey) {
            LiveSession session = liveSessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

            if (session.getStatus() == SessionStatus.FINISHED) {
                return getHostSessionState(pin);
            }

            // Guard against duplicate / race-condition requests skipping questions
            if (expectedCurrentIndex != null && !session.getCurrentQuestionIndex().equals(expectedCurrentIndex)) {
                return getHostSessionState(pin);
            }

            int nextIndex = session.getCurrentQuestionIndex() + 1;
            if (nextIndex < session.getQuiz().getQuestions().size()) {
                return startQuestion(pin, nextIndex);
            } else {
                return endQuiz(pin);
            }
        }
    }

    @Transactional
    public SessionStateResponse previousQuestion(String pin) {
        return previousQuestion(pin, null);
    }

    @Transactional
    public SessionStateResponse previousQuestion(String pin, Integer expectedCurrentIndex) {
        String lockKey = ("session-nav:" + pin).intern();
        synchronized (lockKey) {
            LiveSession session = liveSessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

            if (session.getStatus() == SessionStatus.FINISHED) {
                return getHostSessionState(pin);
            }

            // Guard against stale navigation
            if (expectedCurrentIndex != null && !session.getCurrentQuestionIndex().equals(expectedCurrentIndex)) {
                return getHostSessionState(pin);
            }

            int prevIndex = session.getCurrentQuestionIndex() - 1;
            if (prevIndex >= 0) {
                return startQuestion(pin, prevIndex);
            } else {
                return getHostSessionState(pin);
            }
        }
    }

    @Transactional
    public SessionStateResponse endQuiz(String pin) {
        String lockKey = ("session-nav:" + pin).intern();
        synchronized (lockKey) {
            LiveSession session = liveSessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

            session.setStatus(SessionStatus.FINISHED);
            liveSessionRepository.saveAndFlush(session);

            Quiz quiz = session.getQuiz();
            if (quiz != null) {
                quiz.setExamStatus(ExamStatus.COMPLETED);
                quiz.setUpdatedAt(LocalDateTime.now());
                quizRepository.saveAndFlush(quiz);
            }

            boolean resultsPublished = Boolean.TRUE.equals(session.getQuiz().getResultsPublished());
            List<LeaderboardEntryDto> leaderboard = getLeaderboard(session.getId());

            Map<String, Object> finishPayload = new HashMap<>();
            finishPayload.put("questionIndex", session.getCurrentQuestionIndex());
            finishPayload.put("resultsPublished", resultsPublished);
            finishPayload.put("leaderboard", resultsPublished ? leaderboard : Collections.emptyList());
            finishPayload.put("stateVersion", Long.MAX_VALUE);

            SessionStateResponse hostState = getHostSessionState(pin);

            sendAfterCommit(() -> {
                messagingTemplate.convertAndSend("/topic/session/" + pin,
                        new WsMessage("SESSION_FINISHED", finishPayload));
                messagingTemplate.convertAndSend("/topic/session/" + pin + "/host",
                        new WsMessage("HOST_STATE_CHANGED", hostState));
            });

            return hostState;
        }
    }

    @Transactional(readOnly = true)
    public SessionStateResponse getHostSessionState(String pin) {
        LiveSession session = liveSessionRepository.findByPin(pin)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

        SessionStateResponse resp = new SessionStateResponse();
        resp.setPin(session.getPin());
        resp.setQuizId(session.getQuiz().getId());
        resp.setQuizTitle(session.getQuiz().getTitle());
        resp.setStatus(session.getStatus());
        resp.setCurrentQuestionIndex(session.getCurrentQuestionIndex());
        resp.setTotalQuestions(session.getQuiz().getQuestions().size());
        resp.setTotalMarks(session.getQuiz().calculateTotalMarks());
        resp.setRequireFullscreen(session.getQuiz().getRequireFullscreen());
        resp.setMonitorTabSwitch(session.getQuiz().getMonitorTabSwitch());
        resp.setRestrictCopyPaste(session.getQuiz().getRestrictCopyPaste());
        resp.setTotalIncidents(examIncidentRepository.countByLiveSessionId(session.getId()));

        long participantCount = participantRepository.countBySessionPin(session.getPin());
        resp.setParticipantCount(participantCount);

        if (session.getStatus() != SessionStatus.WAITING && session.getCurrentQuestionIndex() < session.getQuiz().getQuestions().size()) {
            Question currentQuestion = session.getQuiz().getQuestions().get(session.getCurrentQuestionIndex());
            resp.setCurrentQuestion(toQuestionDto(currentQuestion));

            long submissionCount = submissionRepository.countByLiveSessionIdAndQuestionId(session.getId(), currentQuestion.getId());
            resp.setSubmissionCount(submissionCount);

            OptionStatsDto stats = getOptionStats(session.getId(), currentQuestion.getId());
            resp.setOptionCounts(stats.getCounts());

            if (session.getStatus() == SessionStatus.QUESTION_REVEALED || session.getStatus() == SessionStatus.FINISHED) {
                String correctLabel = findAuthoritativeCorrectOptionLabel(currentQuestion);
                resp.setCorrectOptionLabel(correctLabel);

                long correctSubmissions = submissionRepository.countByLiveSessionIdAndQuestionIdAndIsCorrectTrue(session.getId(), currentQuestion.getId());
                double percent = submissionCount > 0 ? ((double) correctSubmissions / submissionCount) * 100.0 : 0.0;
                resp.setCorrectPercentage(Math.round(percent * 10.0) / 10.0);
            }
        }

        resp.setQuestionStartedAtEpochMs(session.getQuestionStartedAtEpochMs());
        resp.setQuestionEndsAtEpochMs(session.getQuestionEndsAtEpochMs());

        long now = System.currentTimeMillis();
        long remaining = 0;
        if (session.getQuestionEndsAtEpochMs() != null && session.getQuestionEndsAtEpochMs() > now) {
            remaining = (session.getQuestionEndsAtEpochMs() - now) / 1000L;
        }
        resp.setRemainingSeconds(remaining);

        if (session.getStatus() == SessionStatus.FINISHED) {
            resp.setLeaderboard(getLeaderboard(session.getId()));
        }

        long stateVersion = 0L;
        if (session.getStatus() == SessionStatus.QUESTION_ACTIVE) {
            stateVersion = ((long) session.getCurrentQuestionIndex() * 10L) + 1L;
        } else if (session.getStatus() == SessionStatus.QUESTION_REVEALED) {
            stateVersion = ((long) session.getCurrentQuestionIndex() * 10L) + 5L;
        } else if (session.getStatus() == SessionStatus.FINISHED) {
            stateVersion = Long.MAX_VALUE;
        }
        resp.setStateVersion(stateVersion);

        return resp;
    }

    @Transactional(readOnly = true)
    public CandidateStateResponse getCandidateSessionState(String pin, String candidateId) {
        LiveSession session = liveSessionRepository.findByPin(pin)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

        String safeCandidateId = candidateId != null ? candidateId.trim() : null;
        Participant participant = safeCandidateId != null && !safeCandidateId.isEmpty()
                ? participantRepository.findBySessionPinAndCandidateId(pin, safeCandidateId).orElse(null)
                : null;

        CandidateStateResponse resp = new CandidateStateResponse();
        resp.setPin(session.getPin());
        resp.setQuizTitle(session.getQuiz().getTitle());
        resp.setStatus(session.getStatus());
        resp.setCurrentQuestionIndex(session.getCurrentQuestionIndex());
        resp.setTotalQuestions(session.getQuiz().getQuestions().size());
        resp.setAllowReselection(session.getQuiz().getAnswerChangePolicy() == AnswerChangePolicy.ALLOW_RESELECTION);
        resp.setRequireFullscreen(session.getQuiz().getRequireFullscreen());
        resp.setMonitorTabSwitch(session.getQuiz().getMonitorTabSwitch());
        resp.setRestrictCopyPaste(session.getQuiz().getRestrictCopyPaste());
        resp.setWarningThreshold(session.getQuiz().getWarningThreshold());
        resp.setResultsPublished(session.getQuiz().getResultsPublished());
        resp.setShowCorrectAnswers(session.getQuiz().getShowCorrectAnswers());
        resp.setTotalMarks(session.getQuiz().calculateTotalMarks());

        if (participant != null) {
            resp.setNickname(participant.getNickname());
            resp.setMyScore(participant.getScore());
            resp.setMyIncidentCount(examIncidentRepository.countByLiveSessionIdAndParticipantId(session.getId(), participant.getId()));
            resp.setRulesAcknowledged(Boolean.TRUE.equals(participant.getRulesAcknowledged()));
        }

        boolean showAnswersSetting = Boolean.TRUE.equals(session.getQuiz().getShowCorrectAnswers());
        boolean isRevealedOrFinished = session.getStatus() == SessionStatus.QUESTION_REVEALED
                || (session.getStatus() == SessionStatus.FINISHED && showAnswersSetting);
        if (session.getStatus() != SessionStatus.WAITING && session.getCurrentQuestionIndex() < session.getQuiz().getQuestions().size()) {
            Question currentQuestion = session.getQuiz().getQuestions().get(session.getCurrentQuestionIndex());
            resp.setQuestionId(currentQuestion.getId());
            resp.setQuestionIndex(session.getCurrentQuestionIndex());
            resp.setCurrentQuestion(toCandidateQuestionDto(currentQuestion));

            if (participant != null) {
                Optional<Submission> sub = submissionRepository.findByLiveSessionIdAndParticipantIdAndQuestionId(
                        session.getId(), participant.getId(), currentQuestion.getId());
                if (sub.isPresent()) {
                    resp.setHasSubmitted(true);
                    resp.setMySelectedLabel(sub.get().getSelectedLabel());
                    resp.setSelectedOptionLabel(sub.get().getSelectedLabel());
                    if (isRevealedOrFinished) {
                        resp.setWasCorrect(sub.get().getIsCorrect());
                    }
                } else {
                    resp.setHasSubmitted(false);
                    resp.setMySelectedLabel(null);
                    resp.setSelectedOptionLabel(null);
                    if (isRevealedOrFinished) {
                        resp.setWasCorrect(false);
                    }
                }
            } else {
                resp.setHasSubmitted(false);
                resp.setMySelectedLabel(null);
                resp.setSelectedOptionLabel(null);
                if (isRevealedOrFinished) {
                    resp.setWasCorrect(false);
                }
            }

            if (isRevealedOrFinished) {
                String correctLabel = findAuthoritativeCorrectOptionLabel(currentQuestion);
                resp.setRevealedCorrectLabel(correctLabel);
                resp.setCorrectOptionLabel(correctLabel);
            }
        }

        log.info("[CANDIDATE_STATE] PIN={}, CandidateId={}, Status={}, QuestionIndex={}, QuestionId={}, Selected={}, RevealedCorrect={}, WasCorrect={}",
                pin, safeCandidateId, session.getStatus(), session.getCurrentQuestionIndex(),
                (session.getStatus() != SessionStatus.WAITING && session.getCurrentQuestionIndex() < session.getQuiz().getQuestions().size() ? session.getQuiz().getQuestions().get(session.getCurrentQuestionIndex()).getId() : "N/A"),
                resp.getMySelectedLabel(), resp.getRevealedCorrectLabel(), resp.getWasCorrect());

        resp.setQuestionStartedAtEpochMs(session.getQuestionStartedAtEpochMs());
        resp.setQuestionEndsAtEpochMs(session.getQuestionEndsAtEpochMs());

        long now = System.currentTimeMillis();
        long remaining = 0;
        if (session.getQuestionEndsAtEpochMs() != null && session.getQuestionEndsAtEpochMs() > now) {
            remaining = (session.getQuestionEndsAtEpochMs() - now) / 1000L;
        }
        resp.setRemainingSeconds(remaining);

        if (session.getStatus() == SessionStatus.FINISHED) {
            boolean isPublished = Boolean.TRUE.equals(session.getQuiz().getResultsPublished());
            if (isPublished) {
                List<LeaderboardEntryDto> leaderboard = getLeaderboard(session.getId());
                resp.setLeaderboard(leaderboard);

                if (participant != null) {
                    for (LeaderboardEntryDto entry : leaderboard) {
                        if (entry.getCandidateId().equals(candidateId)) {
                            resp.setMyRank(entry.getRank());
                            break;
                        }
                    }
                    long correct = submissionRepository.countByLiveSessionIdAndParticipantIdAndIsCorrectTrue(session.getId(), participant.getId());
                    long incorrect = submissionRepository.countByLiveSessionIdAndParticipantIdAndIsCorrectFalse(session.getId(), participant.getId());
                    resp.setTotalCorrect(correct);
                    resp.setTotalIncorrect(incorrect);
                }
            } else {
                resp.setLeaderboard(Collections.emptyList());
                if (participant != null) {
                    resp.setMyRank(null);
                    resp.setTotalCorrect(null);
                    resp.setTotalIncorrect(null);
                }
            }
        }

        long stateVersion = 0L;
        if (session.getStatus() == SessionStatus.QUESTION_ACTIVE) {
            stateVersion = ((long) session.getCurrentQuestionIndex() * 10L) + 1L;
        } else if (session.getStatus() == SessionStatus.QUESTION_REVEALED) {
            stateVersion = ((long) session.getCurrentQuestionIndex() * 10L) + 5L;
        } else if (session.getStatus() == SessionStatus.FINISHED) {
            stateVersion = Long.MAX_VALUE;
        }
        resp.setStateVersion(stateVersion);

        return resp;
    }

    public OptionStatsDto getOptionStats(Long sessionId, Long questionId) {
        List<Object[]> rows = submissionRepository.countBySelectedLabelForQuestion(sessionId, questionId);
        Map<String, Long> counts = new HashMap<>();
        counts.put("A", 0L);
        counts.put("B", 0L);
        counts.put("C", 0L);
        counts.put("D", 0L);

        long total = 0;
        for (Object[] row : rows) {
            String label = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            counts.put(label.toUpperCase(), count);
            total += count;
        }

        return new OptionStatsDto(counts, total);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboard(Long sessionId) {
        LiveSession session = liveSessionRepository.findById(sessionId).orElse(null);
        int totalMarks = (session != null && session.getQuiz() != null) ? session.getQuiz().calculateTotalMarks() : 0;
        List<Participant> participants = participantRepository.findByLiveSessionIdOrderByScoreDesc(sessionId);
        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rank = 1;
        for (Participant p : participants) {
            entries.add(new LeaderboardEntryDto(rank++, p.getNickname(), p.getScore(), p.getCandidateId(), totalMarks));
        }
        return entries;
    }

    private String findAuthoritativeCorrectOptionLabel(Question question) {
        if (question == null) {
            throw new IllegalStateException("Question cannot be null during answer evaluation");
        }
        if (question.getOptions() == null || question.getOptions().isEmpty()) {
            throw new IllegalStateException("Data integrity error: Question ID " + question.getId() + " has no options defined in the database");
        }
        String correctLabel = null;
        int correctCount = 0;
        for (AnswerOption opt : question.getOptions()) {
            if (Boolean.TRUE.equals(opt.getIsCorrect())) {
                String label = opt.getOptionLabel();
                if (label != null && !label.trim().isEmpty()) {
                    if (correctLabel == null) {
                        correctLabel = label.trim().toUpperCase();
                    }
                    correctCount++;
                }
            }
        }
        if (correctCount == 0) {
            log.error("[DATA INTEGRITY ERROR] Question ID {} ('{}') has NO option marked with isCorrect=true in database!",
                    question.getId(), question.getQuestionText());
            throw new IllegalStateException("Data integrity error: Question ID " + question.getId() + " has no correct option configured in database");
        }
        if (correctCount > 1) {
            log.warn("[DATA INTEGRITY WARNING] Question ID {} ('{}') has MULTIPLE options ({}) marked isCorrect=true! Using first matching option: {}",
                    question.getId(), question.getQuestionText(), correctCount, correctLabel);
        }
        return correctLabel;
    }

    private QuestionDto toQuestionDto(Question q) {
        QuestionDto dto = new QuestionDto();
        dto.setId(q.getId());
        dto.setQuestionText(q.getQuestionText());
        dto.setTimeLimit(q.getTimeLimit());
        dto.setMarks(q.getMarks());
        dto.setOrderIndex(q.getOrderIndex());

        List<AnswerOptionDto> optDtos = new ArrayList<>();
        for (AnswerOption opt : q.getOptions()) {
            optDtos.add(new AnswerOptionDto(opt.getId(), opt.getOptionLabel(), opt.getOptionText(), opt.getIsCorrect()));
        }
        dto.setOptions(optDtos);
        return dto;
    }

    private CandidateQuestionDto toCandidateQuestionDto(Question q) {
        CandidateQuestionDto dto = new CandidateQuestionDto();
        dto.setId(q.getId());
        dto.setQuestionText(q.getQuestionText());
        dto.setTimeLimit(q.getTimeLimit());
        dto.setMarks(q.getMarks());
        dto.setOrderIndex(q.getOrderIndex());

        List<CandidateOptionDto> optDtos = new ArrayList<>();
        for (AnswerOption opt : q.getOptions()) {
            // NEVER reveal isCorrect here
            optDtos.add(new CandidateOptionDto(opt.getId(), opt.getOptionLabel(), opt.getOptionText()));
        }
        dto.setOptions(optDtos);
        return dto;
    }

    private void sendAfterCommit(Runnable sendAction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAction.run();
                }
            });
        } else {
            sendAction.run();
        }
    }
}
