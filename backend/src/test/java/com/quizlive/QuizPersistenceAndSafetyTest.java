package com.quizlive;

import com.quizlive.dto.AnswerOptionDto;
import com.quizlive.dto.QuestionDto;
import com.quizlive.dto.QuizDto;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.QuizService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class QuizPersistenceAndSafetyTest {

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Test
    @DisplayName("Should create and save a new quiz draft with proper relationships")
    void testCreateQuizAndSaveDraft() {
        QuizDto dto = new QuizDto();
        dto.setTitle("Database Systems Midterm");
        dto.setExamStatus(ExamStatus.DRAFT);
        dto.setAnswerChangePolicy(AnswerChangePolicy.ALLOW_RESELECTION);

        QuestionDto qDto = new QuestionDto();
        qDto.setQuestionText("What is the primary key in a relational table?");
        qDto.setTimeLimit(30);
        qDto.setMarks(5);
        qDto.setOrderIndex(0);

        List<AnswerOptionDto> options = new ArrayList<>();
        options.add(new AnswerOptionDto(null, "A", "Unique identifier for each record", true));
        options.add(new AnswerOptionDto(null, "B", "Foreign reference", false));
        qDto.setOptions(options);

        dto.setQuestions(List.of(qDto));

        QuizDto saved = quizService.saveOrUpdateDraft(dto, "prof_test");
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Database Systems Midterm", saved.getTitle());
        assertEquals(1, saved.getQuestions().size());

        QuestionDto savedQ = saved.getQuestions().get(0);
        assertNotNull(savedQ.getId());
        assertEquals(2, savedQ.getOptions().size());
        assertNotNull(savedQ.getOptions().get(0).getId());
        assertNotNull(savedQ.getOptions().get(1).getId());
    }

    @Test
    @DisplayName("Should edit an existing quiz without recreating IDs or breaking option references")
    void testEditQuizPreservingExistingQuestionAndOptionIds() {
        // Step 1: Create initial quiz
        QuizDto initial = new QuizDto();
        initial.setTitle("Algorithms Quiz 1");
        initial.setExamStatus(ExamStatus.SCHEDULED);

        QuestionDto q1 = new QuestionDto();
        q1.setQuestionText("What is the average time complexity of QuickSort?");
        q1.setTimeLimit(25);
        q1.setMarks(10);
        q1.setOrderIndex(0);

        List<AnswerOptionDto> options = new ArrayList<>();
        options.add(new AnswerOptionDto(null, "A", "O(N log N)", true));
        options.add(new AnswerOptionDto(null, "B", "O(N^2)", false));
        q1.setOptions(options);
        initial.setQuestions(List.of(q1));

        QuizDto savedInitial = quizService.saveOrUpdateDraft(initial, "prof_test");
        Long quizId = savedInitial.getId();
        Long questionId = savedInitial.getQuestions().get(0).getId();
        Long optAId = savedInitial.getQuestions().get(0).getOptions().get(0).getId();
        Long optBId = savedInitial.getQuestions().get(0).getOptions().get(1).getId();

        // Step 2: Edit the quiz by adding option C and updating question text
        QuizDto updateDto = new QuizDto();
        updateDto.setId(quizId);
        updateDto.setTitle("Algorithms Quiz 1 — Revised");
        updateDto.setExamStatus(ExamStatus.SCHEDULED);

        QuestionDto updateQ = new QuestionDto();
        updateQ.setId(questionId); // Provide existing ID
        updateQ.setQuestionText("What is the average runtime complexity of QuickSort?");
        updateQ.setTimeLimit(30);
        updateQ.setMarks(10);
        updateQ.setOrderIndex(0);

        List<AnswerOptionDto> updatedOptions = new ArrayList<>();
        // Keep Option A with existing ID
        updatedOptions.add(new AnswerOptionDto(optAId, "A", "O(N log N) - Average", true));
        // Keep Option B with existing ID
        updatedOptions.add(new AnswerOptionDto(optBId, "B", "O(N^2) - Worst case", false));
        // Add new Option C without ID
        updatedOptions.add(new AnswerOptionDto(null, "C", "O(1)", false));
        updateQ.setOptions(updatedOptions);
        updateDto.setQuestions(List.of(updateQ));

        QuizDto savedUpdate = quizService.saveOrUpdateDraft(updateDto, "prof_test");

        assertEquals(quizId, savedUpdate.getId());
        assertEquals("Algorithms Quiz 1 — Revised", savedUpdate.getTitle());
        assertEquals(1, savedUpdate.getQuestions().size());

        QuestionDto resultQ = savedUpdate.getQuestions().get(0);
        assertEquals(questionId, resultQ.getId(), "Existing question ID must be preserved");
        assertEquals("What is the average runtime complexity of QuickSort?", resultQ.getQuestionText());

        assertEquals(3, resultQ.getOptions().size());
        assertEquals(optAId, resultQ.getOptions().get(0).getId(), "Option A ID must be preserved");
        assertEquals(optBId, resultQ.getOptions().get(1).getId(), "Option B ID must be preserved");
        assertNotNull(resultQ.getOptions().get(2).getId(), "New Option C must receive a new ID");
    }

    @Test
    @DisplayName("Should prevent deletion of an answer option if referenced by historical student submission")
    void testHistoricalSubmissionPreservedOnOptionEdit() {
        // Create quiz
        Quiz quiz = new Quiz("Operating Systems Quiz");
        quiz.setFacultyUsername("prof_vibesh");
        quiz.setExamStatus(ExamStatus.SCHEDULED);

        Question q = new Question();
        q.setQuestionText("What is a mutex?");
        q.setTimeLimit(20);
        q.setMarks(5);
        q.setOrderIndex(0);
        q.setQuiz(quiz);

        AnswerOption optA = new AnswerOption("A", "Mutual exclusion lock", true);
        optA.setQuestion(q);
        AnswerOption optB = new AnswerOption("B", "Deadlock detector", false);
        optB.setQuestion(q);

        q.setOptions(new ArrayList<>(List.of(optA, optB)));
        quiz.setQuestions(new ArrayList<>(List.of(q)));
        quiz = quizRepository.save(quiz);

        // Simulate student session and submission
        LiveSession session = new LiveSession("112233", quiz);
        session.setStatus(SessionStatus.FINISHED);
        session = liveSessionRepository.save(session);

        Participant participant = new Participant(session, "112233", "cand-1", "Student Alice");
        participant = participantRepository.save(participant);

        Submission sub = new Submission();
        sub.setLiveSession(session);
        sub.setParticipant(participant);
        sub.setQuestion(q);
        sub.setSelectedOption(optA);
        sub.setSelectedLabel("A");
        sub.setIsCorrect(true);
        sub.setMarksAwarded(5);
        sub.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(sub);

        // Faculty attempts to update quiz draft and omits Option A from the payload
        QuizDto editDto = new QuizDto();
        editDto.setId(quiz.getId());
        editDto.setTitle("Operating Systems Quiz (Updated)");
        editDto.setExamStatus(ExamStatus.SCHEDULED);

        QuestionDto editQ = new QuestionDto();
        editQ.setId(q.getId());
        editQ.setQuestionText("What is a mutex lock?");
        editQ.setTimeLimit(20);
        editQ.setMarks(5);
        editQ.setOrderIndex(0);

        // Only sending Option B (Option A omitted)
        editQ.setOptions(List.of(new AnswerOptionDto(optB.getId(), "B", "Deadlock detector", false)));
        editDto.setQuestions(List.of(editQ));

        // When saved: Option A must NOT be destructively deleted because it has a historical submission
        QuizDto result = quizService.saveOrUpdateDraft(editDto, "prof_vibesh");
        assertNotNull(result);

        // Verification: Submission still references optA without foreign key constraint failure
        Submission persistedSub = submissionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(optA.getId(), persistedSub.getSelectedOption().getId(), "Historical submission must remain intact");
    }

    @Test
    @DisplayName("Should prevent quiz deletion when historical student submissions exist")
    void testPreventQuizDeletionWithHistoricalSubmissions() {
        Quiz quiz = new Quiz("Web Tech Examination");
        quiz.setFacultyUsername("prof_vibesh");
        quiz.setExamStatus(ExamStatus.COMPLETED);

        Question q = new Question();
        q.setQuestionText("What is CSS?");
        q.setTimeLimit(20);
        q.setMarks(5);
        q.setOrderIndex(0);
        q.setQuiz(quiz);

        AnswerOption opt = new AnswerOption("A", "Cascading Style Sheets", true);
        opt.setQuestion(q);
        q.setOptions(new ArrayList<>(List.of(opt)));
        quiz.setQuestions(new ArrayList<>(List.of(q)));
        quiz = quizRepository.save(quiz);

        LiveSession session = new LiveSession("445566", quiz);
        session.setStatus(SessionStatus.FINISHED);
        session = liveSessionRepository.save(session);

        Participant participant = new Participant(session, "445566", "cand-2", "Student Bob");
        participant = participantRepository.save(participant);

        Submission sub = new Submission();
        sub.setLiveSession(session);
        sub.setParticipant(participant);
        sub.setQuestion(q);
        sub.setSelectedOption(opt);
        sub.setSelectedLabel("A");
        sub.setIsCorrect(true);
        sub.setMarksAwarded(5);
        submissionRepository.save(sub);

        final Long quizId = quiz.getId();

        // Attempting to delete this quiz must be blocked with an informative exception
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            quizService.deleteQuiz(quizId);
        });

        assertTrue(ex.getMessage().contains("Cannot delete this examination because it contains historical examination records"),
                "Expected explanation regarding academic integrity and historical submissions");

        // Verify quiz is NOT deleted
        assertTrue(quizRepository.existsById(quiz.getId()), "Quiz must remain in database");
    }

    @Test
    @DisplayName("Should safely delete quiz when only empty/abandoned live sessions exist (no student attempts)")
    void testSafeQuizDeletionWithEmptySession() {
        Quiz quiz = new Quiz("Test Quiz Without Students");
        quiz.setFacultyUsername("prof_vibesh");
        quiz.setExamStatus(ExamStatus.SCHEDULED);

        Question q = new Question();
        q.setQuestionText("Dummy question?");
        q.setTimeLimit(20);
        q.setMarks(5);
        q.setOrderIndex(0);
        q.setQuiz(quiz);

        AnswerOption opt = new AnswerOption("A", "Dummy", true);
        opt.setQuestion(q);
        q.setOptions(new ArrayList<>(List.of(opt)));
        quiz.setQuestions(new ArrayList<>(List.of(q)));
        quiz = quizRepository.save(quiz);

        // Host created a live session but no students ever joined and no submissions occurred
        LiveSession emptySession = new LiveSession("778899", quiz);
        emptySession.setStatus(SessionStatus.WAITING);
        liveSessionRepository.save(emptySession);

        final Long emptyQuizId = quiz.getId();

        // Now deleting the quiz should cleanly remove the empty session shell and the quiz
        assertDoesNotThrow(() -> {
            quizService.deleteQuiz(emptyQuizId);
        });

        assertFalse(quizRepository.existsById(emptyQuizId), "Quiz should be deleted");
        assertFalse(liveSessionRepository.findByPin("778899").isPresent(), "Empty session should be cleaned up");
    }
}
