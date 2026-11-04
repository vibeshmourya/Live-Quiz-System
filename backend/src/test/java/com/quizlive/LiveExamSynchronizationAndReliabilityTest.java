package com.quizlive;

import com.quizlive.dto.*;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.LiveSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
