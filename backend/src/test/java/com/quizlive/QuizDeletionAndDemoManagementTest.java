package com.quizlive;

import com.quizlive.dto.AnswerOptionDto;
import com.quizlive.dto.JoinRequest;
import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
import com.quizlive.dto.QuestionDto;
import com.quizlive.dto.QuizDto;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.AuthService;
import com.quizlive.service.LiveSessionService;
import com.quizlive.service.QuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class QuizDeletionAndDemoManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExamIncidentRepository examIncidentRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private LiveSessionService liveSessionService;

    private String facultyToken;
    private String otherFacultyToken;

    @BeforeEach
    void setUp() {
        // Authenticate default faculty
        LoginResponse login = authService.login(new LoginRequest("faculty@quizora.edu", "password123"));
        facultyToken = "Bearer " + login.getToken();

        // Register and authenticate a second faculty member for authorization tests
        try {
            com.quizlive.dto.RegisterRequest reg = new com.quizlive.dto.RegisterRequest();
            reg.setName("Dr. Robert Smith");
            reg.setEmail("robert.smith@quizora.edu");
            reg.setPassword("securePassword456");
            authService.register(reg);
        } catch (Exception ignored) {
            // Already registered in seed
        }
        LoginResponse otherLogin = authService.login(new LoginRequest("robert.smith@quizora.edu", "securePassword456"));
        otherFacultyToken = "Bearer " + otherLogin.getToken();
    }

    @Test
    @DisplayName("Test 1: Delete an empty draft quiz permanently")
    void testDeleteEmptyDraftQuiz() throws Exception {
        Quiz draft = new Quiz("Empty Draft Quiz");
        draft.setFacultyUsername("faculty");
        draft.setExamStatus(ExamStatus.DRAFT);
        draft = quizRepository.save(draft);
        Long draftId = draft.getId();

        mockMvc.perform(delete("/api/quizzes/" + draftId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.deletedId", is(draftId.intValue())));

        assertFalse(quizRepository.existsById(draftId), "Empty draft quiz must be permanently deleted from database");
    }

    @Test
    @DisplayName("Test 2: Delete a quiz containing multiple questions permanently")
    void testDeleteQuizWithMultipleQuestions() throws Exception {
        Quiz quiz = new Quiz("Algorithms Multi-Question Quiz");
        quiz.setFacultyUsername("faculty");
        quiz.setExamStatus(ExamStatus.SCHEDULED);

        Question q1 = new Question();
        q1.setQuestionText("What is binary search complexity?");
        q1.setTimeLimit(20);
        q1.setMarks(5);
        q1.setOrderIndex(0);
        q1.addOption(new AnswerOption("A", "O(log N)", true));
        q1.addOption(new AnswerOption("B", "O(N)", false));
        quiz.addQuestion(q1);

        Question q2 = new Question();
        q2.setQuestionText("What is bubble sort worst case?");
        q2.setTimeLimit(20);
        q2.setMarks(5);
        q2.setOrderIndex(1);
        q2.addOption(new AnswerOption("A", "O(N^2)", true));
        q2.addOption(new AnswerOption("B", "O(1)", false));
        quiz.addQuestion(q2);

        quiz = quizRepository.save(quiz);
        Long quizId = quiz.getId();

        mockMvc.perform(delete("/api/quizzes/" + quizId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertFalse(quizRepository.existsById(quizId), "Quiz and its cascaded questions must be permanently deleted");
    }

    @Test
    @DisplayName("Test 3: Delete a demo quiz")
    void testDeleteDemoQuiz() throws Exception {
        Quiz demo = new Quiz("Introductory Demo Quiz");
        demo.setFacultyUsername("faculty");
        demo.setExamStatus(ExamStatus.SCHEDULED);
        demo.setIsDemo(true);
        demo = quizRepository.save(demo);
        Long demoId = demo.getId();

        mockMvc.perform(delete("/api/quizzes/" + demoId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertFalse(quizRepository.existsById(demoId), "Demo quiz must be deleted cleanly");
    }

    @Test
    @DisplayName("Test 4: Delete a duplicate quiz without affecting the original")
    void testDeleteDuplicateQuiz() throws Exception {
        // Original quiz
        Quiz original = new Quiz("Original Master Template");
        original.setFacultyUsername("faculty");
        original.setExamStatus(ExamStatus.SCHEDULED);
        Question q = new Question();
        q.setQuestionText("Template Question 1");
        q.setTimeLimit(30);
        q.setMarks(10);
        q.setOrderIndex(0);
        q.addOption(new AnswerOption("A", "Yes", true));
        original.addQuestion(q);
        original = quizRepository.save(original);
        Long origId = original.getId();

        // Duplicate the quiz
        QuizDto copyDto = quizService.duplicateQuiz(origId, "faculty");
        Long copyId = copyDto.getId();
        assertNotNull(copyId);
        assertNotEquals(origId, copyId);

        // Delete the copy
        mockMvc.perform(delete("/api/quizzes/" + copyId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertFalse(quizRepository.existsById(copyId), "Duplicated quiz must be deleted");
        assertTrue(quizRepository.existsById(origId), "Original template quiz must remain completely intact");
    }

    @Test
    @DisplayName("Test 6: Delete an already deleted quiz returns 404")
    void testDeleteAlreadyDeletedQuiz() throws Exception {
        Quiz quiz = new Quiz("Temporary Flash Quiz");
        quiz.setFacultyUsername("faculty");
        quiz.setExamStatus(ExamStatus.DRAFT);
        quiz = quizRepository.save(quiz);
        Long quizId = quiz.getId();

        // First deletion
        mockMvc.perform(delete("/api/quizzes/" + quizId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk());

        // Second deletion must return 404 NOT_FOUND
        mockMvc.perform(delete("/api/quizzes/" + quizId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }

    @Test
    @DisplayName("Test 7: Attempt deletion as a student (or unauthenticated) returns 401")
    void testAttemptDeletionAsStudent() throws Exception {
        Quiz quiz = new Quiz("Protected Midterm Exam");
        quiz.setFacultyUsername("faculty");
        quiz = quizRepository.save(quiz);

        // Request with no token
        mockMvc.perform(delete("/api/quizzes/" + quiz.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", containsString("token required")));

        // Request with invalid student token
        mockMvc.perform(delete("/api/quizzes/" + quiz.getId())
                        .header("Authorization", "Bearer invalid-student-token-12345"))
                .andExpect(status().isUnauthorized());

        assertTrue(quizRepository.existsById(quiz.getId()), "Quiz must remain untouched");
    }

    @Test
    @DisplayName("Test 8: Attempt deletion of another faculty member's quiz returns 403 Forbidden")
    void testAttemptDeletionOfAnotherFacultyQuiz() throws Exception {
        // Quiz owned by Dr. Robert Smith
        Quiz robertsQuiz = new Quiz("Dr. Robert's Advanced Network Security");
        robertsQuiz.setFacultyUsername("robertsmith");
        robertsQuiz.setExamStatus(ExamStatus.SCHEDULED);
        robertsQuiz = quizRepository.save(robertsQuiz);
        Long robertsQuizId = robertsQuiz.getId();

        // Default faculty attempts to delete Robert's quiz
        mockMvc.perform(delete("/api/quizzes/" + robertsQuizId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("Unauthorized")));

        assertTrue(quizRepository.existsById(robertsQuizId), "Dr. Robert's quiz must remain untouched in MySQL");

        // Dr. Robert can delete his own quiz
        mockMvc.perform(delete("/api/quizzes/" + robertsQuizId)
                        .header("Authorization", otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Test 9: Attempt deletion while examination is LIVE returns 409 Conflict")
    void testAttemptDeletionWhileExamLive() throws Exception {
        Quiz liveQuiz = new Quiz("Live Concurrency Exam");
        liveQuiz.setFacultyUsername("faculty");
        liveQuiz.setExamStatus(ExamStatus.LIVE);
        liveQuiz = quizRepository.save(liveQuiz);
        Long liveQuizId = liveQuiz.getId();

        mockMvc.perform(delete("/api/quizzes/" + liveQuizId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("LIVE")));

        assertTrue(quizRepository.existsById(liveQuizId), "Live quiz must remain untouched");
    }

    @Test
    @DisplayName("Test 10, 11 & 12: Delete quiz with completed examinations archives quiz, reopens results, and generates PDF reports")
    void testDeleteCompletedQuizArchivesAndPreservesReports() throws Exception {
        // Setup quiz with questions
        Quiz completedQuiz = new Quiz("Final Distributed Databases Examination");
        completedQuiz.setFacultyUsername("faculty");
        completedQuiz.setExamStatus(ExamStatus.COMPLETED);

        Question q1 = new Question();
        q1.setQuestionText("Which property guarantees transactions survive system crashes?");
        q1.setTimeLimit(30);
        q1.setMarks(10);
        q1.setOrderIndex(0);
        AnswerOption optA = new AnswerOption("A", "Durability", true);
        AnswerOption optB = new AnswerOption("B", "Atomicity", false);
        q1.addOption(optA);
        q1.addOption(optB);
        completedQuiz.addQuestion(q1);
        completedQuiz = quizRepository.save(completedQuiz);
        Long quizId = completedQuiz.getId();

        // Create completed live session
        String pin = "987654";
        LiveSession session = new LiveSession(pin, completedQuiz);
        session.setStatus(SessionStatus.FINISHED);
        session.setCompletedAt(LocalDateTime.now());
        session = liveSessionRepository.save(session);

        // Student participant
        Participant student = new Participant(session, pin, "cand-archived-999", "Sarah Connor");
        student.setScore(10);
        student = participantRepository.save(student);

        // Student submission
        Submission sub = new Submission();
        sub.setLiveSession(session);
        sub.setParticipant(student);
        sub.setQuestion(q1);
        sub.setSelectedOption(optA);
        sub.setSelectedLabel("A");
        sub.setIsCorrect(true);
        sub.setMarksAwarded(10);
        sub.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(sub);

        // 1. Faculty deletes this completed quiz from the dashboard
        mockMvc.perform(delete("/api/quizzes/" + quizId)
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.deletedId", is(quizId.intValue())));

        // Verification 1: Quiz is soft-deleted / archived
        Quiz archivedQuiz = quizRepository.findById(quizId).orElseThrow();
        assertTrue(archivedQuiz.getDeleted(), "Quiz must be marked as deleted / archived");
        assertNotNull(archivedQuiz.getDeletedAt(), "Deleted timestamp must be recorded");

        // Verification 2: Quiz no longer appears in visible active quiz dashboard list
        mockMvc.perform(get("/api/quizzes")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(quizId.intValue()))));

        // Test 11: Reopen completed examination after deleting/archiving its parent quiz
        mockMvc.perform(get("/api/sessions/" + pin + "/results")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pin", is(pin)))
                .andExpect(jsonPath("$.quizTitle", is("Final Distributed Databases Examination")))
                .andExpect(jsonPath("$.leaderboard", hasSize(1)))
                .andExpect(jsonPath("$.leaderboard[0].nickname", is("Sarah Connor")))
                .andExpect(jsonPath("$.leaderboard[0].score", is(10)));

        // Test 12: Download historical faculty complete examination report PDF
        mockMvc.perform(get("/api/reports/exam/" + pin + "/pdf")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("Quizora_Exam_Results_" + pin + ".pdf")));

        // Test 12: Download historical student report PDF
        mockMvc.perform(get("/api/reports/exam/" + pin + "/student/cand-archived-999/pdf")
                        .header("X-Candidate-Id", "cand-archived-999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("Quizora_Student_Report_" + pin + "_cand-archived-999.pdf")));
    }
}

