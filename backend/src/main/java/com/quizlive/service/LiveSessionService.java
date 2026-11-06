package com.quizlive.service;

import com.quizlive.dto.*;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

            liveSessionRepository.save(session);

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

            messagingTemplate.convertAndSend("/topic/session/" + pin,
                    new WsMessage("QUESTION_STARTED", questionPayload));

            // Deliver authoritative host session state to host topic
            SessionStateResponse hostState = getHostSessionState(pin);
            messagingTemplate.convertAndSend("/topic/session/" + pin + "/host",
                    new WsMessage("HOST_STATE_CHANGED", hostState));

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

        AnswerOption selectedOption = null;
        Long requestedOptionId = request.getSelectedOptionId() != null ? request.getSelectedOptionId() : request.getOptionId();
        if (requestedOptionId != null) {
            selectedOption = currentQuestion.getOptions().stream()
                    .filter(o -> requestedOptionId.equals(o.getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (selectedOption == null && request.getOptionLabel() != null && !request.getOptionLabel().trim().isEmpty()) {
            String labelCandidate = request.getOptionLabel().trim().toUpperCase();
            selectedOption = currentQuestion.getOptions().stream()
                    .filter(o -> o.getOptionLabel() != null && o.getOptionLabel().trim().equalsIgnoreCase(labelCandidate))
                    .findFirst()
                    .orElse(null);
        }
        if (selectedOption == null) {
            throw new IllegalArgumentException("Invalid option specified for current question: ID=" + requestedOptionId + ", label=" + request.getOptionLabel());
        }

        String label = selectedOption.getOptionLabel().trim().toUpperCase();
        boolean isCorrect = Boolean.TRUE.equals(selectedOption.getIsCorrect());
        int questionMarks = currentQuestion.getMarks() != null ? currentQuestion.getMarks() : 1;
        int marks = isCorrect ? questionMarks : 0;

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

        // Publish participant-specific verification event to candidate
        Map<String, Object> verifyPayload = new HashMap<>();
        verifyPayload.put("eventType", "ANSWER_VERIFIED");
        verifyPayload.put("roomCode", session.getPin());
        verifyPayload.put("sessionPin", session.getPin());
        verifyPayload.put("participantId", participant.getId());
        verifyPayload.put("candidateId", participant.getCandidateId());
        verifyPayload.put("questionId", currentQuestion.getId());
        verifyPayload.put("submissionId", submission.getId());
        verifyPayload.put("selectedOptionId", selectedOption.getId());
        verifyPayload.put("submittedLabel", label);
        verifyPayload.put("isCorrect", isCorrect);
        verifyPayload.put("verified", true);
        verifyPayload.put("status", "VERIFIED");
        verifyPayload.put("marksAwarded", marks);
        verifyPayload.put("message", isCorrect ? "Answer verified successfully" : "Incorrect answer");

        messagingTemplate.convertAndSend("/topic/session/" + session.getPin() + "/candidate/" + participant.getCandidateId(),
                new WsMessage("ANSWER_VERIFIED", verifyPayload));

        return submission;
    }

    @Transactional
    public AnswerVerificationResponse submitAndVerifyAnswer(SubmitAnswerRequest request) {
        Submission submission = submitAnswer(request);
        AnswerVerificationResponse resp = new AnswerVerificationResponse();
        resp.setSuccess(true);
        resp.setVerified(true);
        resp.setQuestionId(submission.getQuestion().getId());
        resp.setSelectedOptionId(submission.getSelectedOption() != null ? submission.getSelectedOption().getId() : null);
        resp.setSubmittedLabel(submission.getSelectedLabel());
        resp.setIsCorrect(submission.getIsCorrect());
        resp.setMarksAwarded(submission.getMarksAwarded());
        resp.setStatus("VERIFIED");
        resp.setMessage(Boolean.TRUE.equals(submission.getIsCorrect()) ? "Answer verified successfully" : "Incorrect answer");
        resp.setSubmittedAt(submission.getSubmittedAt());
        return resp;
    }

    @Transactional
    public SessionStateResponse revealAnswer(String pin) {
        String lockKey = ("session-nav:" + pin).intern();
        synchronized (lockKey) {
            LiveSession session = liveSessionRepository.findByPin(pin)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + pin));

            session.setStatus(SessionStatus.QUESTION_REVEALED);
            liveSessionRepository.save(session);

            Question currentQuestion = session.getQuiz().getQuestions().get(session.getCurrentQuestionIndex());
            String correctLabel = findAuthoritativeCorrectOptionLabel(currentQuestion);

            long totalSubmissions = submissionRepository.countByLiveSessionIdAndQuestionId(session.getId(), currentQuestion.getId());
            long correctSubmissions = submissionRepository.countByLiveSessionIdAndQuestionIdAndIsCorrectTrue(session.getId(), currentQuestion.getId());
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
            revealPayload.put("stateVersion", ((long) session.getCurrentQuestionIndex() * 10L) + 5L);

            log.info("[REVEAL_ANSWER] PIN={}, QuestionId={}, CorrectOption={}, TotalSubmissions={}, CorrectSubmissions={}",
                    pin, currentQuestion.getId(), correctLabel, totalSubmissions, correctSubmissions);

            messagingTemplate.convertAndSend("/topic/session/" + pin,
                    new WsMessage("QUESTION_REVEALED", revealPayload));

            SessionStateResponse hostState = getHostSessionState(pin);
            messagingTemplate.convertAndSend("/topic/session/" + pin + "/host",
                    new WsMessage("HOST_STATE_CHANGED", hostState));

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
            liveSessionRepository.save(session);

            Quiz quiz = session.getQuiz();
            if (quiz != null) {
                quiz.setExamStatus(ExamStatus.COMPLETED);
                quiz.setUpdatedAt(LocalDateTime.now());
                quizRepository.save(quiz);
            }

            boolean resultsPublished = Boolean.TRUE.equals(session.getQuiz().getResultsPublished());
            List<LeaderboardEntryDto> leaderboard = getLeaderboard(session.getId());

            Map<String, Object> finishPayload = new HashMap<>();
            finishPayload.put("questionIndex", session.getCurrentQuestionIndex());
            finishPayload.put("resultsPublished", resultsPublished);
            finishPayload.put("leaderboard", resultsPublished ? leaderboard : Collections.emptyList());
            finishPayload.put("stateVersion", Long.MAX_VALUE);

            messagingTemplate.convertAndSend("/topic/session/" + pin,
                    new WsMessage("SESSION_FINISHED", finishPayload));

            SessionStateResponse hostState = getHostSessionState(pin);
            messagingTemplate.convertAndSend("/topic/session/" + pin + "/host",
                    new WsMessage("HOST_STATE_CHANGED", hostState));

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
            resp.setCurrentQuestion(toCandidateQuestionDto(currentQuestion));

            if (participant != null) {
                Optional<Submission> sub = submissionRepository.findByLiveSessionIdAndParticipantIdAndQuestionId(
                        session.getId(), participant.getId(), currentQuestion.getId());
                if (sub.isPresent()) {
                    resp.setHasSubmitted(true);
                    resp.setMySelectedLabel(sub.get().getSelectedLabel());
                    if (sub.get().getSelectedOption() != null) {
                        resp.setMySelectedOptionId(sub.get().getSelectedOption().getId());
                    }
                    resp.setWasCorrect(sub.get().getIsCorrect());
                } else {
                    resp.setHasSubmitted(false);
                    if (isRevealedOrFinished) {
                        resp.setWasCorrect(false);
                    }
                }
            }

            if (isRevealedOrFinished) {
                String correctLabel = findAuthoritativeCorrectOptionLabel(currentQuestion);
                resp.setRevealedCorrectLabel(correctLabel);
                resp.setCorrectOptionLabel(correctLabel);
            }
        }

        log.info("[CANDIDATE_STATE] PIN={}, CandidateId={}, Status={}, QuestionIndex={}, Selected={}, RevealedCorrect={}, WasCorrect={}",
                pin, safeCandidateId, session.getStatus(), session.getCurrentQuestionIndex(),
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

    public List<LeaderboardEntryDto> getLeaderboard(Long sessionId) {
        List<Participant> participants = participantRepository.findByLiveSessionIdOrderByScoreDesc(sessionId);
        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rank = 1;
        for (Participant p : participants) {
            entries.add(new LeaderboardEntryDto(rank++, p.getNickname(), p.getScore(), p.getCandidateId()));
        }
        return entries;
    }

    private String findAuthoritativeCorrectOptionLabel(Question question) {
        if (question == null || question.getOptions() == null) {
            return null;
        }
        for (AnswerOption opt : question.getOptions()) {
            if (Boolean.TRUE.equals(opt.getIsCorrect())) {
                String label = opt.getOptionLabel();
                if (label != null && !label.trim().isEmpty()) {
                    return label.trim().toUpperCase();
                }
            }
        }
        log.warn("[DATA INTEGRITY WARNING] Question ID {} ('{}') has no option marked with isCorrect=true!",
                question.getId(), question.getQuestionText());
        return null;
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
}
