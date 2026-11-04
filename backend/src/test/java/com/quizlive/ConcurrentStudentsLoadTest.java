package com.quizlive;

import com.quizlive.dto.JoinRequest;
import com.quizlive.dto.OptionStatsDto;
import com.quizlive.dto.SubmitAnswerRequest;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.LiveSessionService;
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
public class ConcurrentStudentsLoadTest {

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

    @Test
    @DisplayName("Should successfully handle 100 simultaneous student joins and submissions under concurrent load")
    void testConcurrentSubmissions100Students() throws Exception {
        final int NUM_STUDENTS = 100;

        // 1. Setup isolated test quiz
        Quiz quiz = new Quiz("High Concurrency Final Exam");
        quiz.setFacultyUsername("prof_benchmark");
        quiz.setExamStatus(ExamStatus.SCHEDULED);
        quiz.setAnswerChangePolicy(AnswerChangePolicy.SINGLE_SELECTION);

        Question q = new Question();
        q.setQuestionText("What is the time complexity of binary search?");
        q.setTimeLimit(60);
        q.setMarks(10);
        q.setOrderIndex(0);
        q.setQuiz(quiz);

        AnswerOption optA = new AnswerOption("A", "O(log N)", true);
        optA.setQuestion(q);
        AnswerOption optB = new AnswerOption("B", "O(N)", false);
        optB.setQuestion(q);
        AnswerOption optC = new AnswerOption("C", "O(1)", false);
        optC.setQuestion(q);
        AnswerOption optD = new AnswerOption("D", "O(N^2)", false);
        optD.setQuestion(q);

        q.setOptions(new ArrayList<>(List.of(optA, optB, optC, optD)));
        quiz.setQuestions(new ArrayList<>(List.of(q)));
        quiz = quizRepository.save(quiz);

        // 2. Create Live Session
        LiveSession session = liveSessionService.createLiveSession(quiz.getId());
        String pin = session.getPin();

        // 3. Concurrent joining of 100 students
        ExecutorService joinExecutor = Executors.newFixedThreadPool(25);
        CountDownLatch startJoinLatch = new CountDownLatch(1);
        CountDownLatch finishJoinLatch = new CountDownLatch(NUM_STUDENTS);

        List<Participant> participants = new CopyOnWriteArrayList<>();
        AtomicInteger joinFailures = new AtomicInteger(0);

        long joinStartTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_STUDENTS; i++) {
            final int studentIndex = i;
            joinExecutor.submit(() -> {
                try {
                    startJoinLatch.await();
                    String candidateId = "concurrent-cand-" + studentIndex;
                    String nickname = "Student " + (studentIndex + 1);
                    Participant p = liveSessionService.joinSession(new JoinRequest(pin, nickname, candidateId));
                    liveSessionService.acknowledgeRules(pin, candidateId);
                    participants.add(p);
                } catch (Exception e) {
                    joinFailures.incrementAndGet();
                } finally {
                    finishJoinLatch.countDown();
                }
            });
        }

        startJoinLatch.countDown();
        boolean joinedInTime = finishJoinLatch.await(30, TimeUnit.SECONDS);
        long joinDuration = System.currentTimeMillis() - joinStartTime;
        joinExecutor.shutdown();

        assertTrue(joinedInTime, "All 100 students should complete join within 30 seconds");
        assertEquals(0, joinFailures.get(), "Zero join failures expected");
        assertEquals(NUM_STUDENTS, participants.size(), "All 100 participants should be registered");

        // 4. Start Question 0
        liveSessionService.startQuestion(pin, 0);

        // 5. Simultaneous 100 students answer submission
        ExecutorService submitExecutor = Executors.newFixedThreadPool(25);
        CountDownLatch startSubmitLatch = new CountDownLatch(1);
        CountDownLatch finishSubmitLatch = new CountDownLatch(NUM_STUDENTS);

        AtomicInteger submitSuccess = new AtomicInteger(0);
        AtomicInteger submitFailures = new AtomicInteger(0);

        String[] optionChoices = new String[]{"A", "B", "C", "D"};
        long submitStartTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_STUDENTS; i++) {
            final Participant participant = participants.get(i);
            final String chosenOption = optionChoices[i % 4];

            submitExecutor.submit(() -> {
                try {
                    startSubmitLatch.await();
                    SubmitAnswerRequest req = new SubmitAnswerRequest();
                    req.setSessionPin(pin);
                    req.setCandidateId(participant.getCandidateId());
                    req.setQuestionId(q.getId());
                    req.setOptionLabel(chosenOption);

                    liveSessionService.submitAnswer(req);
                    submitSuccess.incrementAndGet();
                } catch (Exception e) {
                    submitFailures.incrementAndGet();
                } finally {
                    finishSubmitLatch.countDown();
                }
            });
        }

        startSubmitLatch.countDown();
        boolean submittedInTime = finishSubmitLatch.await(30, TimeUnit.SECONDS);
        long submitDuration = System.currentTimeMillis() - submitStartTime;
        submitExecutor.shutdown();

        assertTrue(submittedInTime, "All 100 student submissions should complete within 30 seconds");
        assertEquals(NUM_STUDENTS, submitSuccess.get(), "All 100 submissions must succeed");
        assertEquals(0, submitFailures.get(), "Zero submission failures expected under 100 concurrent students");

        // 6. Verify aggregated stats
        OptionStatsDto stats = liveSessionService.getOptionStats(session.getId(), q.getId());
        assertEquals(NUM_STUDENTS, stats.getTotalSubmissions(), "Total submissions must equal 100");
        // Each of the 4 options was chosen by exactly 25 students (100 / 4 = 25)
        assertEquals(25L, stats.getCounts().get("A"));
        assertEquals(25L, stats.getCounts().get("B"));
        assertEquals(25L, stats.getCounts().get("C"));
        assertEquals(25L, stats.getCounts().get("D"));

        System.out.printf("Load Test Results for 100 Students:%n" +
                        "- 100 Joins Duration: %d ms (avg %.2f ms/student)%n" +
                        "- 100 Submissions Duration: %d ms (avg %.2f ms/submission)%n" +
                        "- Total Submissions Recorded: %d%n" +
                        "- Failures: %d%n",
                joinDuration, (double) joinDuration / NUM_STUDENTS,
                submitDuration, (double) submitDuration / NUM_STUDENTS,
                stats.getTotalSubmissions(), submitFailures.get());
    }

    @Test
    @DisplayName("Should prevent duplicate submissions under rapid concurrent double-click race condition")
    void testDuplicateSubmissionPrevention() throws Exception {
        Quiz quiz = new Quiz("Duplicate Submission Prevention Test");
        quiz.setFacultyUsername("prof_benchmark");
        quiz.setExamStatus(ExamStatus.SCHEDULED);
        quiz.setAnswerChangePolicy(AnswerChangePolicy.SINGLE_SELECTION);

        Question q = new Question();
        q.setQuestionText("Is HTTP stateless?");
        q.setTimeLimit(30);
        q.setMarks(5);
        q.setOrderIndex(0);
        q.setQuiz(quiz);

        AnswerOption optA = new AnswerOption("A", "Yes", true);
        optA.setQuestion(q);
        q.setOptions(new ArrayList<>(List.of(optA)));
        quiz.setQuestions(new ArrayList<>(List.of(q)));
        quiz = quizRepository.save(quiz);

        LiveSession session = liveSessionService.createLiveSession(quiz.getId());
        String pin = session.getPin();

        Participant participant = liveSessionService.joinSession(new JoinRequest(pin, "Double Clicker", "cand-double-01"));
        liveSessionService.acknowledgeRules(pin, participant.getCandidateId());
        liveSessionService.startQuestion(pin, 0);

        // Simulate 5 simultaneous rapid tap submissions from the same student
        int RAPID_CLICKS = 5;
        ExecutorService clickExecutor = Executors.newFixedThreadPool(RAPID_CLICKS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(RAPID_CLICKS);

        for (int i = 0; i < RAPID_CLICKS; i++) {
            clickExecutor.submit(() -> {
                try {
                    startLatch.await();
                    SubmitAnswerRequest req = new SubmitAnswerRequest();
                    req.setSessionPin(pin);
                    req.setCandidateId(participant.getCandidateId());
                    req.setQuestionId(q.getId());
                    req.setOptionLabel("A");
                    liveSessionService.submitAnswer(req);
                } catch (Exception ignored) {
                    // Subsequent calls after lock are expected to be rejected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        clickExecutor.shutdown();

        // Verify only 1 submission row was persisted for this candidate and question
        long count = submissionRepository.countByLiveSessionIdAndQuestionId(session.getId(), q.getId());
        assertEquals(1, count, "Exactly 1 submission record must exist regardless of concurrent taps");
    }
}
