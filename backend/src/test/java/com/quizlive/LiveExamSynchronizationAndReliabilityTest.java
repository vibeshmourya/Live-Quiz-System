package com.quizlive;

import com.quizlive.dto.*;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.LiveSessionService;
import com.quizlive.controller.LiveSessionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LiveExamSynchronizationAndReliabilityTest {

    @Autowired
    private LiveSessionService liveSessionService;

    @Autowired
    private LiveSessionController liveSessionController;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private Quiz quiz;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        quiz = new Quiz("Distributed Systems Midterm Exam");
        quiz.setFacultyUsername("prof_distributed");
        quiz.setExamStatus(ExamStatus.SCHEDULED);
        quiz.setAnswerChangePolicy(AnswerChangePolicy.SINGLE_SELECTION);
        quiz.setRequireFullscreen(true);
        quiz.setShowCorrectAnswers(true);

        List<Question> questions = new ArrayList<>();

        Question q1 = new Question();
        q1.setQuestionText("What is Paxos consensus?");
        q1.setTimeLimit(30);
        q1.setMarks(5);
        q1.setOrderIndex(0);
        q1.setQuiz(quiz);
        AnswerOption q1A = new AnswerOption("A", "A consensus algorithm for state machine replication", true);
        q1A.setQuestion(q1);
        AnswerOption q1B = new AnswerOption("B", "A distributed file format", false);
        q1B.setQuestion(q1);
        q1.setOptions(new ArrayList<>(List.of(q1A, q1B)));
        questions.add(q1);

        Question q2 = new Question();
        q2.setQuestionText("What does CAP theorem stand for?");
        q2.setTimeLimit(30);
        q2.setMarks(5);
        q2.setOrderIndex(1);
        q2.setQuiz(quiz);
        AnswerOption q2A = new AnswerOption("A", "Consistency, Availability, Partition tolerance", true);
        q2A.setQuestion(q2);
        AnswerOption q2B = new AnswerOption("B", "Cache, Authorization, Protocol", false);
        q2B.setQuestion(q2);
        q2.setOptions(new ArrayList<>(List.of(q2A, q2B)));
        questions.add(q2);

        Question q3 = new Question();
        q3.setQuestionText("What is vector clock used for?");
        q3.setTimeLimit(30);
        q3.setMarks(5);
        q3.setOrderIndex(2);
        q3.setQuiz(quiz);
        AnswerOption q3A = new AnswerOption("A", "Capturing causal relationships between events", true);
        q3A.setQuestion(q3);
        AnswerOption q3B = new AnswerOption("B", "Measuring network latency", false);
        q3B.setQuestion(q3);
        q3.setOptions(new ArrayList<>(List.of(q3A, q3B)));
        questions.add(q3);

        quiz.setQuestions(questions);
        quiz = quizRepository.save(quiz);

        session = liveSessionService.createLiveSession(quiz.getId());
    }

    @Test
    @DisplayName("1. Faculty Navigation & Idempotence: Duplicate Next requests must not advance multiple questions")
    void testDuplicateNextRequestsIdempotence() {
        String pin = session.getPin();

        // Start Question 0
        SessionStateResponse startState = liveSessionService.startQuestion(pin, 0);
        assertEquals(0, startState.getCurrentQuestionIndex());
        assertEquals(SessionStatus.QUESTION_ACTIVE, startState.getStatus());

        // Faculty clicks Next with expectedCurrentIndex = 0
        SessionStateResponse nextState1 = liveSessionService.advanceOrEnd(pin, 0);
        assertEquals(1, nextState1.getCurrentQuestionIndex(), "First advance should transition to question 1");

        // Duplicate click with expectedCurrentIndex = 0 arrives (e.g. rapid double click or network retry)
        SessionStateResponse nextState2 = liveSessionService.advanceOrEnd(pin, 0);
        assertEquals(1, nextState2.getCurrentQuestionIndex(), "Duplicate click with stale expectedIndex 0 must NOT advance to question 2");

        // Verify authoritative database state is still Question 1
        LiveSession updatedSession = liveSessionRepository.findByPin(pin).orElseThrow();
        assertEquals(1, updatedSession.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("2. Backward & Forward Navigation: Faculty can navigate to previous question without losing session state")
    void testPreviousQuestionNavigation() {
        String pin = session.getPin();

        liveSessionService.startQuestion(pin, 0);
        liveSessionService.advanceOrEnd(pin, 0); // Question 1
        liveSessionService.advanceOrEnd(pin, 1); // Question 2

        LiveSession s = liveSessionRepository.findByPin(pin).orElseThrow();
        assertEquals(2, s.getCurrentQuestionIndex());

        // Faculty navigates back to Question 1
        SessionStateResponse prev1 = liveSessionService.previousQuestion(pin, 2);
        assertEquals(1, prev1.getCurrentQuestionIndex(), "Previous navigation should transition to question 1");

        // Faculty navigates back to Question 0
        SessionStateResponse prev2 = liveSessionService.previousQuestion(pin, 1);
        assertEquals(0, prev2.getCurrentQuestionIndex(), "Previous navigation should transition to question 0");

        // At Question 0, navigating previous should stay at 0
        SessionStateResponse prev3 = liveSessionService.previousQuestion(pin, 0);
        assertEquals(0, prev3.getCurrentQuestionIndex(), "Cannot navigate before question 0");
    }

    @Test
    @DisplayName("3. Candidate Reconnection: Reconnecting client retrieves the authoritative server state")
    void testCandidateReconnectionStateRecovery() {
        String pin = session.getPin();

        // Student joins and acknowledges rules
        Participant p = liveSessionService.joinSession(new JoinRequest(pin, "Alice", "cand-alice-101"));
        liveSessionService.acknowledgeRules(pin, "cand-alice-101");

        // Faculty starts question 1
        liveSessionService.startQuestion(pin, 1);

        // Student simulates browser reload / reconnection: calls getCandidateSessionState
        CandidateStateResponse restored = liveSessionService.getCandidateSessionState(pin, "cand-alice-101");
        assertNotNull(restored);
        assertEquals(1, restored.getCurrentQuestionIndex(), "Candidate should receive active question index 1");
        assertEquals("What does CAP theorem stand for?", restored.getCurrentQuestion().getQuestionText());
        assertEquals(SessionStatus.QUESTION_ACTIVE, restored.getStatus());
        assertTrue(restored.isRulesAcknowledged());
        assertFalse(restored.isHasSubmitted());
    }

    @Test
    @DisplayName("4. Answer Persistence & Locking: Submitted answer is retained across navigation and re-selection policy is enforced")
    void testAnswerPersistenceAndLocking() {
        String pin = session.getPin();

        Participant p = liveSessionService.joinSession(new JoinRequest(pin, "Bob", "cand-bob-202"));
        liveSessionService.acknowledgeRules(pin, "cand-bob-202");

        // Question 0
        liveSessionService.startQuestion(pin, 0);
        Long q0Id = quiz.getQuestions().get(0).getId();

        SubmitAnswerRequest submitReq = new SubmitAnswerRequest();
        submitReq.setSessionPin(pin);
        submitReq.setCandidateId("cand-bob-202");
        submitReq.setQuestionId(q0Id);
        submitReq.setOptionLabel("A");

        Submission sub = liveSessionService.submitAnswer(submitReq);
        assertNotNull(sub);
        assertEquals("A", sub.getSelectedLabel());

        // Check candidate state on question 0
        CandidateStateResponse stateQ0 = liveSessionService.getCandidateSessionState(pin, "cand-bob-202");
        assertTrue(stateQ0.isHasSubmitted());
        assertEquals("A", stateQ0.getMySelectedLabel());

        // Attempt second submission when policy is SINGLE_SELECTION
        SubmitAnswerRequest secondSubmit = new SubmitAnswerRequest();
        secondSubmit.setSessionPin(pin);
        secondSubmit.setCandidateId("cand-bob-202");
        secondSubmit.setQuestionId(q0Id);
        secondSubmit.setOptionLabel("B");

        assertThrows(IllegalStateException.class, () -> {
            liveSessionService.submitAnswer(secondSubmit);
        }, "Locked answer policy should prevent reselection");

        // Advance to Question 1 then return to Question 0
        liveSessionService.advanceOrEnd(pin, 0);
        liveSessionService.previousQuestion(pin, 1);

        // Check that Question 0 answer is still recorded in database
        CandidateStateResponse recoveredQ0 = liveSessionService.getCandidateSessionState(pin, "cand-bob-202");
        assertTrue(recoveredQ0.isHasSubmitted());
        assertEquals("A", recoveredQ0.getMySelectedLabel());
    }

    @Test
    @DisplayName("5. Multi-Student Synchronization & Concurrent Load: 80 students join, advance, and submit simultaneously")
    void testMultiStudent80ConcurrentSynchronization() throws Exception {
        final int NUM_STUDENTS = 80;
        String pin = session.getPin();

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch joinLatch = new CountDownLatch(NUM_STUDENTS);
        List<Participant> participants = new CopyOnWriteArrayList<>();
        AtomicInteger joinErrors = new AtomicInteger(0);

        // Step A: 80 Students join simultaneously
        for (int i = 0; i < NUM_STUDENTS; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    String cid = "sync-student-" + idx;
                    Participant p = liveSessionService.joinSession(new JoinRequest(pin, "Student " + idx, cid));
                    liveSessionService.acknowledgeRules(pin, cid);
                    participants.add(p);
                } catch (Exception e) {
                    joinErrors.incrementAndGet();
                } finally {
                    joinLatch.countDown();
                }
            });
        }

        assertTrue(joinLatch.await(15, TimeUnit.SECONDS), "80 students should join within 15 seconds");
        assertEquals(0, joinErrors.get(), "Zero join errors expected");
        assertEquals(NUM_STUDENTS, participants.size());

        // Step B: Faculty starts Question 0
        liveSessionService.startQuestion(pin, 0);

        // Verify all 80 students see Question 0 in state
        for (Participant p : participants) {
            CandidateStateResponse st = liveSessionService.getCandidateSessionState(pin, p.getCandidateId());
            assertEquals(0, st.getCurrentQuestionIndex(), "Student must receive Question 0");
            assertEquals(SessionStatus.QUESTION_ACTIVE, st.getStatus());
        }

        // Step C: 80 Students submit answers simultaneously
        Long q0Id = quiz.getQuestions().get(0).getId();
        CountDownLatch submitLatch = new CountDownLatch(NUM_STUDENTS);
        AtomicInteger submitErrors = new AtomicInteger(0);

        for (int i = 0; i < NUM_STUDENTS; i++) {
            final int idx = i;
            final String cid = "sync-student-" + idx;
            final String label = (idx % 2 == 0) ? "A" : "B";
            pool.submit(() -> {
                try {
                    SubmitAnswerRequest req = new SubmitAnswerRequest();
                    req.setSessionPin(pin);
                    req.setCandidateId(cid);
                    req.setQuestionId(q0Id);
                    req.setOptionLabel(label);
                    liveSessionService.submitAnswer(req);
                } catch (Exception e) {
                    submitErrors.incrementAndGet();
                } finally {
                    submitLatch.countDown();
                }
            });
        }

        assertTrue(submitLatch.await(15, TimeUnit.SECONDS), "80 submissions should finish within 15 seconds");
        assertEquals(0, submitErrors.get(), "Zero submission errors expected");

        // Verify option distribution
        OptionStatsDto stats = liveSessionService.getOptionStats(session.getId(), q0Id);
        assertEquals(80, stats.getTotalSubmissions());
        assertEquals(40L, stats.getCounts().get("A"));
        assertEquals(40L, stats.getCounts().get("B"));

        // Step D: Faculty advances to Question 1
        SessionStateResponse q1Host = liveSessionService.advanceOrEnd(pin, 0);
        assertEquals(1, q1Host.getCurrentQuestionIndex());

        // Verify all 80 students see Question 1 in authoritative state
        for (Participant p : participants) {
            CandidateStateResponse st = liveSessionService.getCandidateSessionState(pin, p.getCandidateId());
            assertEquals(1, st.getCurrentQuestionIndex(), "All students must be synchronized to Question 1");
            assertFalse(st.isHasSubmitted(), "Question 1 should be fresh and unsubmitted");
        }

        pool.shutdown();
    }

    @Test
    @DisplayName("7. Answer Validation: Correct answer is evaluated properly and revealed with correct label (not null)")
    void testCorrectAnswerEvaluationAndRevealIntegrity() {
        // Create a dedicated quiz where Question 4 has option C as correct
        Quiz testQuiz = new Quiz("Live Examination Verification Quiz");
        testQuiz.setFacultyUsername("prof_eval");
        testQuiz.setExamStatus(ExamStatus.SCHEDULED);
        testQuiz.setShowCorrectAnswers(true);

        Question q = new Question();
        q.setQuestionText("Which OSI model layer handles end-to-end communication and flow control?");
        q.setTimeLimit(30);
        q.setMarks(10);
        q.setOrderIndex(0);
        q.setQuiz(testQuiz);

        AnswerOption optA = new AnswerOption("A", "Network Layer", false);
        optA.setQuestion(q);
        AnswerOption optB = new AnswerOption("B", "Data Link Layer", false);
        optB.setQuestion(q);
        AnswerOption optC = new AnswerOption("C", "Transport Layer", true);
        optC.setQuestion(q);
        AnswerOption optD = new AnswerOption("D", "Session Layer", false);
        optD.setQuestion(q);
        q.setOptions(new ArrayList<>(List.of(optA, optB, optC, optD)));

        testQuiz.setQuestions(new ArrayList<>(List.of(q)));
        testQuiz = quizRepository.save(testQuiz);

        LiveSession s = liveSessionService.createLiveSession(testQuiz.getId());
        String pin = s.getPin();

        // Register 3 candidates
        Participant pCorrect = liveSessionService.joinSession(new JoinRequest(pin, "Alice", "cand-alice"));
        liveSessionService.acknowledgeRules(pin, "cand-alice");
        Participant pWrong = liveSessionService.joinSession(new JoinRequest(pin, "Bob", "cand-bob"));
        liveSessionService.acknowledgeRules(pin, "cand-bob");
        Participant pUnanswered = liveSessionService.joinSession(new JoinRequest(pin, "Charlie", "cand-charlie"));
        liveSessionService.acknowledgeRules(pin, "cand-charlie");

        // Faculty starts Question 0
        liveSessionService.startQuestion(pin, 0);

        // Alice submits C (correct)
        SubmitAnswerRequest reqAlice = new SubmitAnswerRequest();
        reqAlice.setSessionPin(pin);
        reqAlice.setCandidateId(pCorrect.getCandidateId());
        reqAlice.setQuestionId(q.getId());
        reqAlice.setOptionLabel("C");
        liveSessionService.submitAnswer(reqAlice);

        // Bob submits B (incorrect)
        SubmitAnswerRequest reqBob = new SubmitAnswerRequest();
        reqBob.setSessionPin(pin);
        reqBob.setCandidateId(pWrong.getCandidateId());
        reqBob.setQuestionId(q.getId());
        reqBob.setOptionLabel("B");
        liveSessionService.submitAnswer(reqBob);

        // Charlie submits nothing

        // Faculty reveals answer
        SessionStateResponse hostState = liveSessionService.revealAnswer(pin);
        assertEquals(SessionStatus.QUESTION_REVEALED, hostState.getStatus());
        assertEquals("C", hostState.getCorrectOptionLabel(), "Host must see C as the correct option");

        // Verify Alice's state (Correct)
        CandidateStateResponse aliceState = liveSessionService.getCandidateSessionState(pin, pCorrect.getCandidateId());
        assertEquals("C", aliceState.getRevealedCorrectLabel(), "Alice must receive correct label C");
        assertEquals("C", aliceState.getCorrectOptionLabel(), "Alice must receive correctOptionLabel alias C");
        assertNotNull(aliceState.getRevealedCorrectLabel(), "revealedCorrectLabel must NEVER be null");
        assertNotEquals("null", aliceState.getRevealedCorrectLabel(), "revealedCorrectLabel must not be string 'null'");
        assertTrue(aliceState.isHasSubmitted(), "Alice must be recorded as submitted");
        assertEquals("C", aliceState.getMySelectedLabel(), "Alice selected C");
        assertEquals(Boolean.TRUE, aliceState.getWasCorrect(), "Alice wasCorrect must evaluate to true");

        // Verify Bob's state (Incorrect)
        CandidateStateResponse bobState = liveSessionService.getCandidateSessionState(pin, pWrong.getCandidateId());
        assertEquals("C", bobState.getRevealedCorrectLabel(), "Bob must receive correct label C");
        assertEquals("C", bobState.getCorrectOptionLabel(), "Bob must receive correctOptionLabel alias C");
        assertTrue(bobState.isHasSubmitted());
        assertEquals("B", bobState.getMySelectedLabel());
        assertEquals(Boolean.FALSE, bobState.getWasCorrect(), "Bob wasCorrect must evaluate to false");

        // Verify Charlie's state (Unanswered)
        CandidateStateResponse charlieState = liveSessionService.getCandidateSessionState(pin, pUnanswered.getCandidateId());
        assertEquals("C", charlieState.getRevealedCorrectLabel(), "Charlie must receive correct label C");
        assertFalse(charlieState.isHasSubmitted());
        assertNull(charlieState.getMySelectedLabel());
        assertEquals(Boolean.FALSE, charlieState.getWasCorrect(), "Charlie wasCorrect must evaluate to false");
    }

    @Test
    @DisplayName("8. Answer Reveal Resilience: When showCorrectAnswers is false or null, explicit faculty reveal must still deliver correct label")
    void testRevealAnswerWhenQuizShowCorrectAnswersIsFalseOrNull() {
        Quiz testQuiz = new Quiz("Exam with showCorrectAnswers disabled");
        testQuiz.setFacultyUsername("prof_strict");
        testQuiz.setExamStatus(ExamStatus.SCHEDULED);
        testQuiz.setShowCorrectAnswers(false); // explicitly false

        Question q = new Question();
        q.setQuestionText("What is the time complexity of binary search?");
        q.setTimeLimit(20);
        q.setMarks(5);
        q.setOrderIndex(0);
        q.setQuiz(testQuiz);

        AnswerOption optA = new AnswerOption("A", "O(n)", false);
        optA.setQuestion(q);
        AnswerOption optB = new AnswerOption("B", "O(log n)", true);
        optB.setQuestion(q);
        q.setOptions(new ArrayList<>(List.of(optA, optB)));

        testQuiz.setQuestions(new ArrayList<>(List.of(q)));
        testQuiz = quizRepository.save(testQuiz);

        LiveSession s = liveSessionService.createLiveSession(testQuiz.getId());
        String pin = s.getPin();

        Participant cand = liveSessionService.joinSession(new JoinRequest(pin, "David", "cand-david"));
        liveSessionService.acknowledgeRules(pin, "cand-david");
        liveSessionService.startQuestion(pin, 0);

        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setSessionPin(pin);
        req.setCandidateId(cand.getCandidateId());
        req.setQuestionId(q.getId());
        req.setOptionLabel("B");
        liveSessionService.submitAnswer(req);

        // Faculty explicitly triggers revealAnswer
        SessionStateResponse host = liveSessionService.revealAnswer(pin);
        assertEquals("B", host.getCorrectOptionLabel());

        // Even though quiz.showCorrectAnswers was false, faculty explicitly triggered revealAnswer,
        // so candidate must receive "B" and wasCorrect=true (not null!)
        CandidateStateResponse candState = liveSessionService.getCandidateSessionState(pin, cand.getCandidateId());
        assertEquals("B", candState.getRevealedCorrectLabel(), "Candidate must receive 'B', never null");
        assertEquals("B", candState.getCorrectOptionLabel(), "Candidate must receive correctOptionLabel 'B'");
        assertEquals(Boolean.TRUE, candState.getWasCorrect(), "Candidate wasCorrect must evaluate to true");

        // When quiz is ended and showCorrectAnswers is false, finished state must respect quiz setting and not expose correct answer
        liveSessionService.endQuiz(pin);
        CandidateStateResponse finishedState = liveSessionService.getCandidateSessionState(pin, cand.getCandidateId());
        assertEquals(SessionStatus.FINISHED, finishedState.getStatus());
        assertNull(finishedState.getRevealedCorrectLabel(), "When showCorrectAnswers is false, finished state must not expose revealedCorrectLabel");
    }

    @Test
    @DisplayName("9. Multi-Candidate Distribution: 5 candidates submit answers to Question 4 and option counts are exact")
    void testMultiCandidateOptionDistributionAndEvaluation() {
        Quiz testQuiz = new Quiz("Five Candidate Examination");
        testQuiz.setFacultyUsername("prof_multi");
        testQuiz.setExamStatus(ExamStatus.SCHEDULED);

        Question q = new Question();
        q.setQuestionText("Which design pattern ensures a class has only one instance?");
        q.setTimeLimit(25);
        q.setMarks(10);
        q.setOrderIndex(0);
        q.setQuiz(testQuiz);

        AnswerOption optA = new AnswerOption("A", "Factory", false);
        optA.setQuestion(q);
        AnswerOption optB = new AnswerOption("B", "Observer", false);
        optB.setQuestion(q);
        AnswerOption optC = new AnswerOption("C", "Singleton", true);
        optC.setQuestion(q);
        AnswerOption optD = new AnswerOption("D", "Adapter", false);
        optD.setQuestion(q);
        q.setOptions(new ArrayList<>(List.of(optA, optB, optC, optD)));

        testQuiz.setQuestions(new ArrayList<>(List.of(q)));
        testQuiz = quizRepository.save(testQuiz);

        LiveSession s = liveSessionService.createLiveSession(testQuiz.getId());
        String pin = s.getPin();

        // 5 candidates
        List<Participant> participants = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String cid = "c-" + i;
            participants.add(liveSessionService.joinSession(new JoinRequest(pin, "Student " + i, cid)));
            liveSessionService.acknowledgeRules(pin, cid);
        }

        liveSessionService.startQuestion(pin, 0);

        // 3 vote for C (correct), 1 votes for A, 1 votes for B
        String[] votes = {"C", "C", "C", "A", "B"};
        for (int i = 0; i < 5; i++) {
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setSessionPin(pin);
            req.setCandidateId(participants.get(i).getCandidateId());
            req.setQuestionId(q.getId());
            req.setOptionLabel(votes[i]);
            liveSessionService.submitAnswer(req);
        }

        // Check stats
        OptionStatsDto stats = liveSessionService.getOptionStats(s.getId(), q.getId());
        assertEquals(5, stats.getTotalSubmissions());
        assertEquals(3L, stats.getCounts().get("C"));
        assertEquals(1L, stats.getCounts().get("A"));
        assertEquals(1L, stats.getCounts().get("B"));
        assertEquals(0L, stats.getCounts().get("D"));

        // Faculty reveals answer
        SessionStateResponse host = liveSessionService.revealAnswer(pin);
        assertEquals("C", host.getCorrectOptionLabel());
        assertEquals(60.0, host.getCorrectPercentage(), 0.01);

        // Verify all 3 candidates who answered C have wasCorrect = true
        for (int i = 0; i < 3; i++) {
            CandidateStateResponse resp = liveSessionService.getCandidateSessionState(pin, participants.get(i).getCandidateId());
            assertEquals("C", resp.getRevealedCorrectLabel());
            assertEquals(Boolean.TRUE, resp.getWasCorrect(), "Candidate " + (i + 1) + " must evaluate to correct");
        }

        // Verify candidates 4 and 5 who answered A and B have wasCorrect = false
        CandidateStateResponse respA = liveSessionService.getCandidateSessionState(pin, participants.get(3).getCandidateId());
        assertEquals("C", respA.getRevealedCorrectLabel());
        assertEquals(Boolean.FALSE, respA.getWasCorrect());

        CandidateStateResponse respB = liveSessionService.getCandidateSessionState(pin, participants.get(4).getCandidateId());
        assertEquals("C", respB.getRevealedCorrectLabel());
        assertEquals(Boolean.FALSE, respB.getWasCorrect());
    }

    @Test
    @DisplayName("10. Reproduction Question 3 Scenario: Unique Constraint prevents duplicate entries, 5 candidates, option A is authoritative correct, no candidate receives N/A or null")
    void testExactReproductionQuestion3WithUniqueConstraint() {
        Quiz testQuiz = new Quiz("Relational Database Examination");
        testQuiz.setFacultyUsername("prof_db");
        testQuiz.setExamStatus(ExamStatus.SCHEDULED);

        // Create 4 questions so this is Question 3 of 4 (index 2)
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Question q = new Question();
            q.setTimeLimit(20);
            q.setMarks(10);
            q.setOrderIndex(i);
            q.setQuiz(testQuiz);
            if (i == 2) {
                // Exact question from prompt
                q.setQuestionText("In relational databases like MySQL, which key constraint prevents duplicate entries across defined columns?");
                AnswerOption optA = new AnswerOption("A", "UNIQUE Constraint", true);
                optA.setQuestion(q);
                AnswerOption optB = new AnswerOption("B", "CHECK Constraint", false);
                optB.setQuestion(q);
                AnswerOption optC = new AnswerOption("C", "FOREIGN KEY Constraint", false);
                optC.setQuestion(q);
                AnswerOption optD = new AnswerOption("D", "DEFAULT Clause", false);
                optD.setQuestion(q);
                q.setOptions(new ArrayList<>(List.of(optA, optB, optC, optD)));
            } else if (i == 3) {
                // Exact Question 4 of 4: Option C is correct
                q.setQuestionText("Which HTTP status code represents successful resource creation?");
                AnswerOption optA = new AnswerOption("A", "200 OK", false);
                optA.setQuestion(q);
                AnswerOption optB = new AnswerOption("B", "202 Accepted", false);
                optB.setQuestion(q);
                AnswerOption optC = new AnswerOption("C", "201 Created", true);
                optC.setQuestion(q);
                AnswerOption optD = new AnswerOption("D", "204 No Content", false);
                optD.setQuestion(q);
                q.setOptions(new ArrayList<>(List.of(optA, optB, optC, optD)));
            } else {
                q.setQuestionText("Placeholder Question " + (i + 1));
                AnswerOption optA = new AnswerOption("A", "Choice A", true);
                optA.setQuestion(q);
                AnswerOption optB = new AnswerOption("B", "Choice B", false);
                optB.setQuestion(q);
                q.setOptions(new ArrayList<>(List.of(optA, optB)));
            }
            questions.add(q);
        }
        testQuiz.setQuestions(questions);
        testQuiz = quizRepository.save(testQuiz);

        LiveSession session = liveSessionService.createLiveSession(testQuiz.getId());
        String pin = session.getPin();

        // 6 candidates join and acknowledge rules
        List<Participant> candidates = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            String cid = "cand-q3-" + i;
            candidates.add(liveSessionService.joinSession(new JoinRequest(pin, "Student " + i, cid)));
            liveSessionService.acknowledgeRules(pin, cid);
        }

        // Navigate to Question 3 (index 2)
        liveSessionService.startQuestion(pin, 2);

        // Candidate 1 selects Option A (Correct)
        SubmitAnswerRequest req1 = new SubmitAnswerRequest();
        req1.setSessionPin(pin);
        req1.setCandidateId(candidates.get(0).getCandidateId());
        req1.setQuestionId(questions.get(2).getId());
        req1.setOptionLabel("A");
        liveSessionService.submitAnswer(req1);

        // Candidate 2 selects Option B (Incorrect)
        SubmitAnswerRequest req2 = new SubmitAnswerRequest();
        req2.setSessionPin(pin);
        req2.setCandidateId(candidates.get(1).getCandidateId());
        req2.setQuestionId(questions.get(2).getId());
        req2.setOptionLabel("B");
        liveSessionService.submitAnswer(req2);

        // Candidate 3 selects Option C (Incorrect)
        SubmitAnswerRequest req3 = new SubmitAnswerRequest();
        req3.setSessionPin(pin);
        req3.setCandidateId(candidates.get(2).getCandidateId());
        req3.setQuestionId(questions.get(2).getId());
        req3.setOptionLabel("C");
        liveSessionService.submitAnswer(req3);

        // Candidate 4 selects Option D (Incorrect)
        SubmitAnswerRequest req4 = new SubmitAnswerRequest();
        req4.setSessionPin(pin);
        req4.setCandidateId(candidates.get(3).getCandidateId());
        req4.setQuestionId(questions.get(2).getId());
        req4.setOptionLabel("D");
        liveSessionService.submitAnswer(req4);

        // Candidate 5 does not answer (unanswered / time up)

        // Candidate 6 selects Option A (Correct), will simulate reload during reveal
        SubmitAnswerRequest req6 = new SubmitAnswerRequest();
        req6.setSessionPin(pin);
        req6.setCandidateId(candidates.get(5).getCandidateId());
        req6.setQuestionId(questions.get(2).getId());
        req6.setOptionLabel("A");
        liveSessionService.submitAnswer(req6);

        // Verify that BEFORE faculty reveals answer, correct answer is NEVER exposed to candidate
        CandidateStateResponse cand1Active = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertNull(cand1Active.getRevealedCorrectLabel(), "Correct label must NOT be exposed while question is active");
        assertNull(cand1Active.getCorrectOptionLabel(), "Correct option label must NOT be exposed while question is active");
        assertNull(cand1Active.getWasCorrect(), "wasCorrect must be null while question is active");
        assertEquals("A", cand1Active.getMySelectedLabel(), "Candidate's own selection is preserved");
        assertTrue(cand1Active.isHasSubmitted(), "Candidate hasSubmitted is true");

        // Faculty reveals answer
        SessionStateResponse host = liveSessionService.revealAnswer(pin);
        assertEquals("A", host.getCorrectOptionLabel(), "Host must authoritative know correct option is A");
        assertEquals(40.0, host.getCorrectPercentage(), 0.01, "2 of 5 submissions correct = 40%");

        // Verify Candidate 1: Selected A -> Must be evaluated as Correct, verdict true, revealed label "A"
        CandidateStateResponse cand1State = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertEquals("A", cand1State.getRevealedCorrectLabel(), "Revealed label must be 'A', never null or N/A");
        assertEquals("A", cand1State.getCorrectOptionLabel(), "Correct option label alias must be 'A'");
        assertEquals("A", cand1State.getMySelectedLabel(), "Selected label must be 'A'");
        assertEquals(Boolean.TRUE, cand1State.getWasCorrect(), "Candidate 1 must be marked correct");
        assertTrue(cand1State.isHasSubmitted(), "Candidate 1 has submitted");
        assertEquals(40, cand1State.getTotalMarks(), "Candidate state total marks must be 40 (4 questions * 10 marks)");

        // Verify Candidate 2: Selected B -> Must be evaluated as Incorrect, verdict false, revealed label "A"
        CandidateStateResponse cand2State = liveSessionService.getCandidateSessionState(pin, candidates.get(1).getCandidateId());
        assertEquals("A", cand2State.getRevealedCorrectLabel());
        assertEquals("B", cand2State.getMySelectedLabel());
        assertEquals(Boolean.FALSE, cand2State.getWasCorrect(), "Candidate 2 selected B, so wasCorrect must be false");

        // Verify Candidate 3: Selected C -> Must be evaluated as Incorrect, verdict false, revealed label "A"
        CandidateStateResponse cand3State = liveSessionService.getCandidateSessionState(pin, candidates.get(2).getCandidateId());
        assertEquals("A", cand3State.getRevealedCorrectLabel());
        assertEquals("C", cand3State.getMySelectedLabel());
        assertEquals(Boolean.FALSE, cand3State.getWasCorrect(), "Candidate 3 selected C, so wasCorrect must be false");

        // Verify Candidate 4: Selected D -> Must be evaluated as Incorrect, verdict false, revealed label "A"
        CandidateStateResponse cand4State = liveSessionService.getCandidateSessionState(pin, candidates.get(3).getCandidateId());
        assertEquals("A", cand4State.getRevealedCorrectLabel());
        assertEquals("D", cand4State.getMySelectedLabel());
        assertEquals(Boolean.FALSE, cand4State.getWasCorrect(), "Candidate 4 selected D, so wasCorrect must be false");

        // Verify Candidate 5: Unanswered -> verdict false, hasSubmitted false, revealed label "A"
        CandidateStateResponse cand5State = liveSessionService.getCandidateSessionState(pin, candidates.get(4).getCandidateId());
        assertEquals("A", cand5State.getRevealedCorrectLabel());
        assertNull(cand5State.getMySelectedLabel(), "Unanswered candidate has no selected label");
        assertFalse(cand5State.isHasSubmitted(), "Unanswered candidate hasSubmitted must be false");
        assertEquals(Boolean.FALSE, cand5State.getWasCorrect(), "Unanswered candidate wasCorrect must be false");

        // Verify Candidate 6: Simulates page reload / reconnection after reveal -> restores state with wasCorrect=true and label "A"
        CandidateStateResponse cand6Reloaded = liveSessionService.getCandidateSessionState(pin, candidates.get(5).getCandidateId().trim());
        assertEquals("A", cand6Reloaded.getRevealedCorrectLabel(), "Candidate 6 reloaded must restore 'A'");
        assertEquals("A", cand6Reloaded.getMySelectedLabel());
        assertEquals(Boolean.TRUE, cand6Reloaded.getWasCorrect(), "Candidate 6 reloaded must evaluate to true");

        // Advance to Question 4 (index 3)
        liveSessionService.advanceOrEnd(pin);
        CandidateStateResponse q4Active = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertEquals(3, q4Active.getCurrentQuestionIndex(), "Session must be on Question 4 (index 3)");
        assertNull(q4Active.getRevealedCorrectLabel(), "Question 4 correct label must NOT be exposed while active");

        // Candidate 1 selects Option C (Correct)
        SubmitAnswerRequest q4Req1 = new SubmitAnswerRequest();
        q4Req1.setSessionPin(pin);
        q4Req1.setCandidateId(candidates.get(0).getCandidateId());
        q4Req1.setQuestionId(questions.get(3).getId());
        q4Req1.setOptionLabel("C");
        liveSessionService.submitAnswer(q4Req1);

        // Candidate 2 selects Option A (Incorrect)
        SubmitAnswerRequest q4Req2 = new SubmitAnswerRequest();
        q4Req2.setSessionPin(pin);
        q4Req2.setCandidateId(candidates.get(1).getCandidateId());
        q4Req2.setQuestionId(questions.get(3).getId());
        q4Req2.setOptionLabel("A");
        liveSessionService.submitAnswer(q4Req2);

        // Faculty reveals Question 4
        SessionStateResponse q4HostReveal = liveSessionService.revealAnswer(pin);
        assertEquals("C", q4HostReveal.getCorrectOptionLabel(), "Host must authoritative know correct option is C");

        // Verify Candidate 1: Selected C -> evaluated as Correct, verdict true, revealed label "C"
        CandidateStateResponse q4Cand1 = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertEquals("C", q4Cand1.getRevealedCorrectLabel(), "Candidate 1 revealed label must be 'C'");
        assertEquals("C", q4Cand1.getMySelectedLabel(), "Candidate 1 selected label must be 'C'");
        assertEquals(Boolean.TRUE, q4Cand1.getWasCorrect(), "Candidate 1 selected correct C -> wasCorrect must be true");

        // Verify Candidate 2: Selected A -> evaluated as Incorrect, verdict false, revealed label "C"
        CandidateStateResponse q4Cand2 = liveSessionService.getCandidateSessionState(pin, candidates.get(1).getCandidateId());
        assertEquals("C", q4Cand2.getRevealedCorrectLabel(), "Candidate 2 revealed label must be 'C'");
        assertEquals("A", q4Cand2.getMySelectedLabel(), "Candidate 2 selected label must be 'A'");
        assertEquals(Boolean.FALSE, q4Cand2.getWasCorrect(), "Candidate 2 selected wrong A -> wasCorrect must be false");

        // Verify Candidate 4 (unanswered): verdict false, revealed label "C"
        CandidateStateResponse q4Cand4 = liveSessionService.getCandidateSessionState(pin, candidates.get(3).getCandidateId());
        assertEquals("C", q4Cand4.getRevealedCorrectLabel(), "Unanswered candidate revealed label must be 'C'");
        assertEquals(Boolean.FALSE, q4Cand4.getWasCorrect(), "Unanswered candidate wasCorrect must be false");

        // Verify Candidate 1 reload / recovery after reveal: restores state with wasCorrect=true and label "C"
        CandidateStateResponse q4Cand1Reloaded = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId().trim());
        assertEquals("C", q4Cand1Reloaded.getRevealedCorrectLabel(), "Candidate 1 reloaded must restore 'C'");
        assertEquals("C", q4Cand1Reloaded.getMySelectedLabel());
        assertEquals(Boolean.TRUE, q4Cand1Reloaded.getWasCorrect(), "Candidate 1 reloaded must evaluate to true");

        // Finish examination
        liveSessionService.advanceOrEnd(pin);

        // Verify Marks-Only Leaderboard format
        List<LeaderboardEntryDto> leaderboard = liveSessionService.getLeaderboard(session.getId());
        assertFalse(leaderboard.isEmpty(), "Leaderboard must not be empty");
        for (LeaderboardEntryDto entry : leaderboard) {
            assertTrue(entry.getTotalMarks() > 0, "Leaderboard entry must include totalMarks");
            assertNotNull(entry.getNickname(), "Leaderboard entry must include nickname");
            assertTrue(entry.getScore() >= 0, "Leaderboard entry must include marks obtained (score)");
        }
    }

    @Test
    @DisplayName("11. Final Reproduction: Question 1 of 4 WebSocket full-duplex communication over single TCP connection, Option B correct, 5 candidates, transaction synchronization, reload/recovery, no indefinite pending")
    void testExactQuestion1WebSocketScenarioWithTransactionSyncAndRecovery() {
        Quiz q1Quiz = new Quiz("Networking and Real-Time Systems Examination");
        q1Quiz.setFacultyUsername("prof_networks");
        q1Quiz.setExamStatus(ExamStatus.SCHEDULED);
        q1Quiz.setShowCorrectAnswers(true);
        q1Quiz.setAnswerChangePolicy(AnswerChangePolicy.SINGLE_SELECTION);

        // Create 4 questions: Question 1 is the exact prompt question
        List<Question> questions = new ArrayList<>();

        // Question 1 of 4: Which protocol provides full-duplex communication over a single TCP connection in real time?
        Question q1 = new Question();
        q1.setQuestionText("Which protocol provides full-duplex communication over a single TCP connection in real time?");
        q1.setTimeLimit(30);
        q1.setMarks(10);
        q1.setOrderIndex(0);
        q1.setQuiz(q1Quiz);

        AnswerOption optA = new AnswerOption("A", "HTTP/1.1 Long Polling", false);
        optA.setQuestion(q1);
        AnswerOption optB = new AnswerOption("B", "WebSocket", true); // Authoritative correct option
        optB.setQuestion(q1);
        AnswerOption optC = new AnswerOption("C", "Server-Sent Events (SSE)", false);
        optC.setQuestion(q1);
        AnswerOption optD = new AnswerOption("D", "DNS Lookup", false);
        optD.setQuestion(q1);

        q1.setOptions(new ArrayList<>(List.of(optA, optB, optC, optD)));
        questions.add(q1);

        // Questions 2, 3, 4 placeholders
        for (int i = 1; i < 4; i++) {
            Question q = new Question();
            q.setQuestionText("Subsequent Question " + (i + 1));
            q.setTimeLimit(30);
            q.setMarks(10);
            q.setOrderIndex(i);
            q.setQuiz(q1Quiz);
            AnswerOption oA = new AnswerOption("A", "Option A", true);
            oA.setQuestion(q);
            AnswerOption oB = new AnswerOption("B", "Option B", false);
            oB.setQuestion(q);
            q.setOptions(new ArrayList<>(List.of(oA, oB)));
            questions.add(q);
        }

        q1Quiz.setQuestions(questions);
        q1Quiz = quizRepository.save(q1Quiz);

        LiveSession liveSession = liveSessionService.createLiveSession(q1Quiz.getId());
        String pin = liveSession.getPin();

        // 5 Candidates join and acknowledge rules
        List<Participant> candidates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String cid = "cand-q1-" + i;
            Participant p = liveSessionService.joinSession(new JoinRequest(pin, "Student " + i, cid));
            liveSessionService.acknowledgeRules(pin, cid);
            candidates.add(p);
        }

        // Faculty starts Question 1 (index 0)
        SessionStateResponse hostStart = liveSessionService.startQuestion(pin, 0);
        assertEquals(0, hostStart.getCurrentQuestionIndex());
        assertEquals(1L, hostStart.getStateVersion(), "Active Question 0 stateVersion must be 1");

        // Verify Candidate 1 before submission: stateVersion=1, no verdict exposed
        CandidateStateResponse cand1Before = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertEquals(1L, cand1Before.getStateVersion());
        assertNull(cand1Before.getRevealedCorrectLabel());
        assertNull(cand1Before.getCorrectOptionLabel());
        assertNull(cand1Before.getWasCorrect());
        assertFalse(cand1Before.isHasSubmitted());
        assertNull(cand1Before.getMySelectedLabel());

        // 1. Candidate 1 selects B (Correct)
        SubmitAnswerRequest req1 = new SubmitAnswerRequest();
        req1.setSessionPin(pin);
        req1.setCandidateId(candidates.get(0).getCandidateId());
        req1.setQuestionId(q1.getId());
        req1.setOptionLabel("B");
        liveSessionService.submitAnswer(req1);

        // 2. Candidate 2 selects A (Incorrect)
        SubmitAnswerRequest req2 = new SubmitAnswerRequest();
        req2.setSessionPin(pin);
        req2.setCandidateId(candidates.get(1).getCandidateId());
        req2.setQuestionId(q1.getId());
        req2.setOptionLabel("A");
        liveSessionService.submitAnswer(req2);

        // 3. Candidate 3 selects C (Incorrect)
        SubmitAnswerRequest req3 = new SubmitAnswerRequest();
        req3.setSessionPin(pin);
        req3.setCandidateId(candidates.get(2).getCandidateId());
        req3.setQuestionId(q1.getId());
        req3.setOptionLabel("C");
        liveSessionService.submitAnswer(req3);

        // 4. Candidate 4 selects D (Incorrect)
        SubmitAnswerRequest req4 = new SubmitAnswerRequest();
        req4.setSessionPin(pin);
        req4.setCandidateId(candidates.get(3).getCandidateId());
        req4.setQuestionId(q1.getId());
        req4.setOptionLabel("D");
        liveSessionService.submitAnswer(req4);

        // 5. Candidate 5 does NOT answer (unanswered)

        // Verify that while active, submitted candidates have their selection saved but no verdict is leaked
        CandidateStateResponse cand1Active = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertTrue(cand1Active.isHasSubmitted());
        assertEquals("B", cand1Active.getMySelectedLabel());
        assertNull(cand1Active.getRevealedCorrectLabel());
        assertNull(cand1Active.getCorrectOptionLabel());
        assertNull(cand1Active.getWasCorrect());

        // Faculty reveals the answer
        SessionStateResponse hostReveal = liveSessionService.revealAnswer(pin);
        assertEquals(SessionStatus.QUESTION_REVEALED, hostReveal.getStatus());
        assertEquals("B", hostReveal.getCorrectOptionLabel(), "Host must authoritatively identify option B as correct");
        assertEquals(5L, hostReveal.getStateVersion(), "Revealed Question 0 stateVersion must be 5");
        assertEquals(4, hostReveal.getSubmissionCount(), "4 candidates submitted");
        assertEquals(25.0, hostReveal.getCorrectPercentage(), 0.01, "1 of 4 submissions correct = 25%");

        // 1. Candidate 1: Selected B -> Correct, wasCorrect = true, revealedCorrectLabel = "B"
        CandidateStateResponse c1 = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertEquals(5L, c1.getStateVersion(), "Candidate 1 stateVersion must be 5");
        assertEquals("B", c1.getRevealedCorrectLabel(), "Candidate 1 revealed label must be 'B'");
        assertEquals("B", c1.getCorrectOptionLabel(), "Candidate 1 correct option label must be 'B'");
        assertEquals("B", c1.getMySelectedLabel(), "Candidate 1 selected label must be 'B'");
        assertEquals("B", c1.getSelectedOptionLabel(), "Candidate 1 selected option label must be 'B'");
        assertEquals(q1.getId(), c1.getQuestionId(), "Candidate 1 questionId must match q1 ID");
        assertEquals(0, c1.getQuestionIndex(), "Candidate 1 questionIndex must be 0");
        assertEquals(SessionStatus.QUESTION_REVEALED, c1.getStatus(), "Candidate 1 status must be QUESTION_REVEALED");
        assertTrue(c1.isHasSubmitted(), "Candidate 1 hasSubmitted must be true");
        assertEquals(Boolean.TRUE, c1.getWasCorrect(), "Candidate 1 selected B -> wasCorrect must be true");

        // Verify Authoritative REST endpoint contract for Candidate 1
        ResponseEntity<?> restC1 = liveSessionController.getCandidateState(pin, candidates.get(0).getCandidateId());
        assertTrue(restC1.getStatusCode().is2xxSuccessful());
        assertTrue(restC1.getBody() instanceof CandidateStateResponse);
        CandidateStateResponse c1RestBody = (CandidateStateResponse) restC1.getBody();
        assertEquals(q1.getId(), c1RestBody.getQuestionId());
        assertEquals(0, c1RestBody.getQuestionIndex());
        assertEquals("B", c1RestBody.getSelectedOptionLabel());
        assertEquals("B", c1RestBody.getCorrectOptionLabel());
        assertEquals("B", c1RestBody.getRevealedCorrectLabel());
        assertTrue(c1RestBody.isHasSubmitted());
        assertEquals(Boolean.TRUE, c1RestBody.getWasCorrect());
        assertEquals(SessionStatus.QUESTION_REVEALED, c1RestBody.getStatus());
        assertEquals(5L, c1RestBody.getStateVersion());

        // 2. Candidate 2: Selected A -> Incorrect, wasCorrect = false, revealedCorrectLabel = "B"
        CandidateStateResponse c2 = liveSessionService.getCandidateSessionState(pin, candidates.get(1).getCandidateId());
        assertEquals(5L, c2.getStateVersion());
        assertEquals("B", c2.getRevealedCorrectLabel());
        assertEquals("B", c2.getCorrectOptionLabel());
        assertEquals("A", c2.getMySelectedLabel());
        assertEquals("A", c2.getSelectedOptionLabel());
        assertEquals(q1.getId(), c2.getQuestionId());
        assertEquals(0, c2.getQuestionIndex());
        assertEquals(SessionStatus.QUESTION_REVEALED, c2.getStatus());
        assertTrue(c2.isHasSubmitted());
        assertEquals(Boolean.FALSE, c2.getWasCorrect(), "Candidate 2 selected A -> wasCorrect must be false");

        // 3. Candidate 3: Selected C -> Incorrect, wasCorrect = false, revealedCorrectLabel = "B"
        CandidateStateResponse c3 = liveSessionService.getCandidateSessionState(pin, candidates.get(2).getCandidateId());
        assertEquals(5L, c3.getStateVersion());
        assertEquals("B", c3.getRevealedCorrectLabel());
        assertEquals("B", c3.getCorrectOptionLabel());
        assertEquals("C", c3.getMySelectedLabel());
        assertEquals("C", c3.getSelectedOptionLabel());
        assertEquals(q1.getId(), c3.getQuestionId());
        assertEquals(0, c3.getQuestionIndex());
        assertEquals(SessionStatus.QUESTION_REVEALED, c3.getStatus());
        assertTrue(c3.isHasSubmitted());
        assertEquals(Boolean.FALSE, c3.getWasCorrect(), "Candidate 3 selected C -> wasCorrect must be false");

        // 4. Candidate 4: Selected D -> Incorrect, wasCorrect = false, revealedCorrectLabel = "B"
        CandidateStateResponse c4 = liveSessionService.getCandidateSessionState(pin, candidates.get(3).getCandidateId());
        assertEquals(5L, c4.getStateVersion());
        assertEquals("B", c4.getRevealedCorrectLabel());
        assertEquals("B", c4.getCorrectOptionLabel());
        assertEquals("D", c4.getMySelectedLabel());
        assertEquals("D", c4.getSelectedOptionLabel());
        assertEquals(q1.getId(), c4.getQuestionId());
        assertEquals(0, c4.getQuestionIndex());
        assertEquals(SessionStatus.QUESTION_REVEALED, c4.getStatus());
        assertTrue(c4.isHasSubmitted());
        assertEquals(Boolean.FALSE, c4.getWasCorrect(), "Candidate 4 selected D -> wasCorrect must be false");

        // 5. Candidate 5: Unanswered -> Unanswered, wasCorrect = false, hasSubmitted = false, revealedCorrectLabel = "B"
        CandidateStateResponse c5 = liveSessionService.getCandidateSessionState(pin, candidates.get(4).getCandidateId());
        assertEquals(5L, c5.getStateVersion());
        assertEquals("B", c5.getRevealedCorrectLabel(), "Unanswered candidate revealed label must be 'B'");
        assertEquals("B", c5.getCorrectOptionLabel());
        assertNull(c5.getMySelectedLabel(), "Unanswered candidate has no selected label");
        assertNull(c5.getSelectedOptionLabel(), "Unanswered candidate selectedOptionLabel must be null");
        assertEquals(q1.getId(), c5.getQuestionId());
        assertEquals(0, c5.getQuestionIndex());
        assertEquals(SessionStatus.QUESTION_REVEALED, c5.getStatus());
        assertFalse(c5.isHasSubmitted(), "Unanswered candidate hasSubmitted must be false");
        assertEquals(Boolean.FALSE, c5.getWasCorrect(), "Unanswered candidate wasCorrect must be false, not null or stuck pending");

        // Verify Authoritative REST endpoint contract for Unanswered Candidate 5
        ResponseEntity<?> restC5 = liveSessionController.getCandidateState(pin, candidates.get(4).getCandidateId());
        assertTrue(restC5.getStatusCode().is2xxSuccessful());
        CandidateStateResponse c5RestBody = (CandidateStateResponse) restC5.getBody();
        assertEquals(q1.getId(), c5RestBody.getQuestionId());
        assertEquals(0, c5RestBody.getQuestionIndex());
        assertNull(c5RestBody.getSelectedOptionLabel());
        assertEquals("B", c5RestBody.getCorrectOptionLabel());
        assertFalse(c5RestBody.isHasSubmitted());
        assertEquals(SessionStatus.QUESTION_REVEALED, c5RestBody.getStatus());

        // Test Candidate Reload & Reconnect after reveal
        CandidateStateResponse c1Reload = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId().trim());
        assertEquals(5L, c1Reload.getStateVersion());
        assertEquals("B", c1Reload.getRevealedCorrectLabel(), "Reloaded Candidate 1 must receive 'B'");
        assertEquals(Boolean.TRUE, c1Reload.getWasCorrect(), "Reloaded Candidate 1 wasCorrect must be true");

        CandidateStateResponse c2Reload = liveSessionService.getCandidateSessionState(pin, candidates.get(1).getCandidateId().trim());
        assertEquals(5L, c2Reload.getStateVersion());
        assertEquals("B", c2Reload.getRevealedCorrectLabel(), "Reloaded Candidate 2 must receive 'B'");
        assertEquals(Boolean.FALSE, c2Reload.getWasCorrect(), "Reloaded Candidate 2 wasCorrect must be false");

        // Test Question Progression: Advance to Question 2 (index 1)
        SessionStateResponse hostNext = liveSessionService.advanceOrEnd(pin);
        assertEquals(1, hostNext.getCurrentQuestionIndex(), "Host must advance to Question 2 (index 1)");
        assertEquals(SessionStatus.QUESTION_ACTIVE, hostNext.getStatus());
        assertEquals(11L, hostNext.getStateVersion(), "Question 1 active stateVersion must be 11");

        // Candidate 1 on Question 2: Clean slate, no leaked answers or verdicts from Question 1
        CandidateStateResponse c1Q2 = liveSessionService.getCandidateSessionState(pin, candidates.get(0).getCandidateId());
        assertEquals(1, c1Q2.getCurrentQuestionIndex(), "Candidate 1 must be on Question 2 (index 1)");
        assertEquals(11L, c1Q2.getStateVersion());
        assertEquals(SessionStatus.QUESTION_ACTIVE, c1Q2.getStatus());
        assertNull(c1Q2.getRevealedCorrectLabel(), "Question 2 correct label must NOT be leaked");
        assertNull(c1Q2.getCorrectOptionLabel(), "Question 2 correctOptionLabel must NOT be leaked");
        assertNull(c1Q2.getWasCorrect(), "Question 2 wasCorrect must be null while active");
        assertFalse(c1Q2.isHasSubmitted(), "Question 2 hasSubmitted must be false");
        assertNull(c1Q2.getMySelectedLabel(), "Question 2 mySelectedLabel must be null");
    }
}
