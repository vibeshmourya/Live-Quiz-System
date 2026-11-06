package com.quizlive;

import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
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
    private AuthService authService;

    @Autowired
    private ReportExportService reportExportService;

    private LiveSession session;
    private Participant participant;
    private String facultyAuthToken;

    @BeforeEach
    void setUp() {
        LoginResponse loginResp = authService.login(new LoginRequest("faculty", "password123"));
        facultyAuthToken = "Bearer " + loginResp.getToken();

        Quiz quiz = new Quiz("Automata & Formal Languages");
        quiz.setFacultyUsername("faculty");
        quiz.setExamStatus(ExamStatus.COMPLETED);

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
        // Valid PDF magic header
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
        // Check CSV escaping for quotes/commas in nickname: Alec "The Coder" O'Neill, Jr.
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
}
