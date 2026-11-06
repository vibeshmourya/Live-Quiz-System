package com.quizlive;

import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
import com.quizlive.dto.RegisterRequest;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.AuthService;
import com.quizlive.service.ReportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ExportReportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private ExamIncidentRepository incidentRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private ReportExportService reportExportService;

    private LiveSession session;
    private Participant participant;
    private String facultyAuthToken;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        LoginResponse loginResp = authService.login(new LoginRequest("faculty", "password123"));
        facultyAuthToken = "Bearer " + loginResp.getToken();

        quiz = new Quiz("Automata & Formal Languages");
        quiz.setFacultyUsername("faculty");
        quiz.setExamStatus(ExamStatus.COMPLETED);
        quiz.setResultsPublished(true);

        List<Question> questions = new ArrayList<>();
        Question q1 = new Question();
        q1.setQuestionText("Is DFA equivalent to NFA in language recognition power?");
        q1.setTimeLimit(30);
        q1.setMarks(5);
        q1.setOrderIndex(0);
        q1.setQuiz(quiz);

        List<AnswerOption> options = new ArrayList<>();
        AnswerOption optA = new AnswerOption("A", "Yes, both recognize exactly regular languages", true);
        optA.setQuestion(q1);

        AnswerOption optB = new AnswerOption("B", "No, NFA is strictly more powerful", false);
        optB.setQuestion(q1);

        options.add(optA);
        options.add(optB);
        q1.setOptions(options);
        questions.add(q1);
        quiz.setQuestions(questions);

        quiz = quizRepository.save(quiz);

        session = new LiveSession();
        session.setPin("652074");
        session.setQuiz(quiz);
        session.setStatus(SessionStatus.FINISHED);
        session = liveSessionRepository.save(session);

        participant = new Participant();
        participant.setLiveSession(session);
        participant.setSessionPin("652074");
        participant.setCandidateId("cand-alec-99");
        participant.setNickname("Alec \"The Coder\" O'Neill, Jr.");
        participant.setScore(95);
        participant.setRulesAcknowledged(true);
        participant = participantRepository.save(participant);

        Participant participant2 = new Participant();
        participant2.setLiveSession(session);
        participant2.setSessionPin("652074");
        participant2.setCandidateId("cand-bob-102");
        participant2.setNickname("Bob Smith");
        participant2.setScore(80);
        participant2.setRulesAcknowledged(true);
        participantRepository.save(participant2);

        ExamIncident incident = new ExamIncident(session, participant, IncidentType.FULLSCREEN_EXIT, "Exited full screen to check notification");
        incident.setReviewStatus(ReviewStatus.FLAGGED);
        incidentRepository.save(incident);
    }

    @Test
    @DisplayName("GET /api/reports/exam/{pin}/pdf should return valid PDF binary with correct headers")
    void testExamPdfDownload() throws Exception {
        byte[] pdfBytes = mockMvc.perform(get("/api/reports/exam/652074/pdf")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
        String header = new String(pdfBytes, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    @DisplayName("GET /api/reports/session/{pin}/pdf alias should return valid PDF binary")
    void testSessionPdfDownloadAlias() throws Exception {
        mockMvc.perform(get("/api/reports/session/652074/pdf")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @DisplayName("GET /api/reports/exam/{pin}/csv should return properly escaped CSV content")
    void testExamCsvDownloadAndEscaping() throws Exception {
        String csvContent = mockMvc.perform(get("/api/reports/exam/652074/csv")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotNull(csvContent);
        assertTrue(csvContent.contains("Rank,Candidate ID,Nickname"));
        assertTrue(csvContent.contains("\"Alec \"\"The Coder\"\" O'Neill, Jr.\""));
    }

    @Test
    @DisplayName("GET /api/reports/exam/{pin}/incidents/pdf should generate incident audit PDF")
    void testIncidentPdfDownload() throws Exception {
        byte[] pdfBytes = mockMvc.perform(get("/api/reports/exam/652074/incidents/pdf")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertNotNull(pdfBytes);
        assertEquals("%PDF", new String(pdfBytes, 0, 4));
    }

    @Test
    @DisplayName("Faculty should be able to download individual student result PDF")
    void testStudentPdfDownload() throws Exception {
        byte[] pdfBytes = mockMvc.perform(get("/api/reports/exam/652074/student/cand-alec-99/pdf")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertNotNull(pdfBytes);
        assertEquals("%PDF", new String(pdfBytes, 0, 4));
    }

    @Test
    @DisplayName("Student with published results should be able to download own score report PDF via X-Candidate-Id header")
    void testStudentDownloadsOwnPdfWithHeader() throws Exception {
        byte[] pdfBytes = mockMvc.perform(get("/api/reports/exam/652074/student/cand-alec-99/pdf")
                        .header("X-Candidate-Id", "cand-alec-99"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertNotNull(pdfBytes);
        assertEquals("%PDF", new String(pdfBytes, 0, 4));
    }

    @Test
    @DisplayName("Student with published results should be able to download own score report PDF via query param")
    void testStudentDownloadsOwnPdfWithQueryParam() throws Exception {
        byte[] pdfBytes = mockMvc.perform(get("/api/reports/exam/652074/student/cand-alec-99/pdf?requesterCandidateId=cand-alec-99"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertNotNull(pdfBytes);
        assertEquals("%PDF", new String(pdfBytes, 0, 4));
    }

    @Test
    @DisplayName("Student should be blocked with 403 when trying to download another student's report")
    void testStudentBlockedFromOtherStudentPdf() throws Exception {
        mockMvc.perform(get("/api/reports/exam/652074/student/cand-alec-99/pdf")
                        .header("X-Candidate-Id", "cand-bob-102"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Students may only access their own examination report")));
    }

    @Test
    @DisplayName("Student should be blocked with 403 when results are not published")
    void testStudentBlockedWhenResultsUnpublished() throws Exception {
        quiz.setResultsPublished(false);
        quizRepository.save(quiz);

        mockMvc.perform(get("/api/reports/exam/652074/student/cand-alec-99/pdf")
                        .header("X-Candidate-Id", "cand-alec-99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Examination results have not been published by the examiner")));
    }

    @Test
    @DisplayName("Student without authentication or candidate ID should be rejected with 401")
    void testUnauthenticatedStudentRejected() throws Exception {
        mockMvc.perform(get("/api/reports/exam/652074/student/cand-alec-99/pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    @DisplayName("Complete exam PDF without faculty token should be rejected with 401")
    void testCompleteExamPdfWithoutFacultyToken() throws Exception {
        mockMvc.perform(get("/api/reports/exam/652074/pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication required: Please sign in as faculty")));
    }

    @Test
    @DisplayName("Complete exam CSV without faculty token should be rejected with 401")
    void testCompleteExamCsvWithoutFacultyToken() throws Exception {
        mockMvc.perform(get("/api/reports/exam/652074/csv"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication required: Please sign in as faculty")));
    }

    @Test
    @DisplayName("Incident audit PDF without faculty token should be rejected with 401")
    void testIncidentPdfWithoutFacultyToken() throws Exception {
        mockMvc.perform(get("/api/reports/exam/652074/incidents/pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication required: Please sign in as faculty")));
    }

    @Test
    @DisplayName("Unauthorized faculty should be blocked with 403 from accessing another faculty's private examination")
    void testUnauthorizedFacultyBlockedFromPrivateQuiz() throws Exception {
        // Create a private quiz owned by prof_private
        Quiz privateQuiz = new Quiz("Advanced Quantum Computing");
        privateQuiz.setFacultyUsername("prof_private");
        privateQuiz.setExamStatus(ExamStatus.COMPLETED);
        privateQuiz = quizRepository.save(privateQuiz);

        LiveSession privateSession = new LiveSession();
        privateSession.setPin("882190");
        privateSession.setQuiz(privateQuiz);
        privateSession.setStatus(SessionStatus.FINISHED);
        privateSession = liveSessionRepository.save(privateSession);

        // Register other faculty user
        LoginResponse otherFacultyResp = authService.register(new RegisterRequest("Dr. Smith", "dr_smith@quizora.edu", "pass12345"));
        String otherFacultyToken = "Bearer " + otherFacultyResp.getToken();

        // other faculty tries to access private session report
        mockMvc.perform(get("/api/reports/exam/882190/pdf")
                        .header("Authorization", otherFacultyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Access denied: You are not authorized to access reports for this examination.")));
    }

    @Test
    @DisplayName("ADMIN faculty can access any examination report")
    void testAdminCanAccessAnyExamReport() throws Exception {
        // Create private quiz
        Quiz privateQuiz = new Quiz("Cryptography");
        privateQuiz.setFacultyUsername("prof_exclusive");
        privateQuiz.setExamStatus(ExamStatus.COMPLETED);
        privateQuiz = quizRepository.save(privateQuiz);

        LiveSession privateSession = new LiveSession();
        privateSession.setPin("334120");
        privateSession.setQuiz(privateQuiz);
        privateSession.setStatus(SessionStatus.FINISHED);
        privateSession = liveSessionRepository.save(privateSession);

        // Register admin faculty
        LoginResponse adminResp = authService.register(new RegisterRequest("Admin User", "dean@quizora.edu", "adminPass123"));
        Faculty adminFaculty = facultyRepository.findByEmail("dean@quizora.edu").orElseThrow();
        adminFaculty.setRole("ADMIN");
        facultyRepository.save(adminFaculty);

        String adminToken = "Bearer " + adminResp.getToken();

        mockMvc.perform(get("/api/reports/exam/334120/pdf")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @DisplayName("Any registered faculty can access default system-seeded quiz ('faculty' owner)")
    void testAnyFacultyCanAccessDefaultQuiz() throws Exception {
        // Session 652074 is owned by "faculty" (default template)
        LoginResponse otherFacultyResp = authService.register(new RegisterRequest("Prof. Jones", "jones@quizora.edu", "jonesPass123"));
        String otherFacultyToken = "Bearer " + otherFacultyResp.getToken();

        mockMvc.perform(get("/api/reports/exam/652074/pdf")
                        .header("Authorization", otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @DisplayName("Request with non-existent candidate ID returns 404")
    void testNonExistentCandidateReturns404() throws Exception {
        mockMvc.perform(get("/api/reports/exam/652074/student/non-existent-candidate/pdf")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Participant not found with ID")));
    }

    @Test
    @DisplayName("Request with non-existent session PIN returns 404")
    void testNonExistentSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/reports/exam/999999/student/cand-alec-99/pdf")
                        .header("Authorization", facultyAuthToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Session not found with PIN")));
    }
}
