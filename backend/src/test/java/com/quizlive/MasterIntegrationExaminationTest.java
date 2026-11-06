package com.quizlive;

import com.quizlive.dto.JoinRequest;
import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.AuthService;
import com.quizlive.service.LiveSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MasterIntegrationExaminationTest {

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
    private AuthService authService;

    @Autowired
    private LiveSessionService liveSessionService;

    private String facultyToken;
    private Quiz quiz;
    private LiveSession session1;

    @BeforeEach
    void setUp() {
        LoginResponse login = authService.login(new LoginRequest("faculty", "password123"));
        facultyToken = "Bearer " + login.getToken();

        quiz = new Quiz("Distributed Systems Engineering");
        quiz.setFacultyUsername("faculty");
        quiz.setExamStatus(ExamStatus.DRAFT);
        quiz.setAnswerChangePolicy(AnswerChangePolicy.ALLOW_RESELECTION);
        quiz.setResultsPublished(true);

        List<Question> questions = new ArrayList<>();
        Question q1 = new Question();
        q1.setQuestionText("Which consensus algorithm uses leader election and replicated logs?");
        q1.setTimeLimit(30);
        q1.setMarks(10);
        q1.setOrderIndex(0);
        q1.setQuiz(quiz);

        List<AnswerOption> options = new ArrayList<>();
        AnswerOption optA = new AnswerOption("A", "Two-Phase Commit", false);
        optA.setQuestion(q1);

        AnswerOption optB = new AnswerOption("B", "Raft Consensus", true);
        optB.setQuestion(q1);

        options.add(optA);
        options.add(optB);
        q1.setOptions(options);
        questions.add(q1);
        quiz.setQuestions(questions);

        quiz = quizRepository.save(quiz);
        session1 = liveSessionService.createLiveSession(quiz.getId());
    }

    @Test
    @DisplayName("Test A: Student answer submission hides correctness while persisting internal score")
    void testStudentAnswerSubmissionHidesCorrectness() throws Exception {
        String pin = session1.getPin();
        Participant student = liveSessionService.joinSession(new JoinRequest(pin, "David", "cand-david-101"));
        liveSessionService.acknowledgeRules(pin, student.getCandidateId());
        liveSessionService.startQuestion(pin, 0);

        Question q1 = quiz.getQuestions().get(0);
        AnswerOption optB = q1.getOptions().stream().filter(o -> "B".equals(o.getOptionLabel())).findFirst().orElseThrow();

        // Student submits correct answer B over HTTP
        mockMvc.perform(post("/api/sessions/" + pin + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"candidateId\":\"%s\",\"questionId\":%d,\"selectedOptionId\":%d,\"optionLabel\":\"B\"}",
                                student.getCandidateId(), q1.getId(), optB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.submitted", is(true)))
                .andExpect(jsonPath("$.message", containsString("Answer submitted successfully")))
                // Ensure isCorrect, correctOptionId, correctAnswer are NOT exposed to student:
                .andExpect(jsonPath("$.isCorrect").doesNotExist())
                .andExpect(jsonPath("$.correctOptionId").doesNotExist())
                .andExpect(jsonPath("$.correctAnswer").doesNotExist());

        // Verify that backend verified answer internally in database:
        Submission sub = submissionRepository.findByLiveSessionIdAndParticipantIdAndQuestionId(
                session1.getId(), student.getId(), q1.getId()).orElseThrow();
        assertEquals(Boolean.TRUE, sub.getIsCorrect(), "Internal DB submission must record isCorrect=true");
        assertEquals(10, sub.getMarksAwarded(), "Internal DB submission must record 10 marks awarded");

        // Verify that candidate state does NOT expose correctness or live score during examination:
        mockMvc.perform(get("/api/sessions/" + pin + "/candidate")
                        .param("candidateId", student.getCandidateId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSubmitted", is(true)))
                .andExpect(jsonPath("$.mySelectedLabel", is("B")))
                .andExpect(jsonPath("$.wasCorrect").doesNotExist())
                .andExpect(jsonPath("$.revealedCorrectLabel").doesNotExist());
    }

    @Test
    @DisplayName("Test B: Answer privacy across public quiz endpoints")
    void testAnswerPrivacyPublicQuizEndpoints() throws Exception {
        // Non-faculty viewing public quiz must NOT see isCorrect
        mockMvc.perform(get("/api/quizzes/" + quiz.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].options[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[1].isCorrect").doesNotExist());
    }

    @Test
    @DisplayName("Test C: Ending examination stores completed state in MySQL and preserves all records")
    void testExaminationCompletionPersistence() throws Exception {
        String pin = session1.getPin();
        Participant p1 = liveSessionService.joinSession(new JoinRequest(pin, "Emma", "cand-emma-201"));
        liveSessionService.acknowledgeRules(pin, p1.getCandidateId());
        liveSessionService.startQuestion(pin, 0);

        Question q1 = quiz.getQuestions().get(0);
        AnswerOption optB = q1.getOptions().stream().filter(o -> "B".equals(o.getOptionLabel())).findFirst().orElseThrow();

        mockMvc.perform(post("/api/sessions/" + pin + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"candidateId\":\"%s\",\"questionId\":%d,\"selectedOptionId\":%d,\"optionLabel\":\"B\"}",
                                p1.getCandidateId(), q1.getId(), optB.getId())))
                .andExpect(status().isOk());

        // Faculty ends quiz
        mockMvc.perform(post("/api/sessions/" + pin + "/end")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FINISHED")));

        // Verify MySQL state
        LiveSession updatedSession = liveSessionRepository.findByPin(pin).orElseThrow();
        assertEquals(SessionStatus.FINISHED, updatedSession.getStatus());
        assertNotNull(updatedSession.getCompletedAt(), "completedAt timestamp must be recorded");

        // Verify participant score and submission preserved
        Participant preservedParticipant = participantRepository.findBySessionPinAndCandidateId(pin, p1.getCandidateId()).orElseThrow();
        assertEquals(10, preservedParticipant.getScore());
        assertTrue(submissionRepository.existsByLiveSessionIdAndParticipantIdAndQuestionId(updatedSession.getId(), preservedParticipant.getId(), q1.getId()));
    }

    @Test
    @DisplayName("Test D: Reopening examination history loads saved results and leaderboard")
    void testReopeningExaminationHistory() throws Exception {
        String pin = session1.getPin();
        Participant p = liveSessionService.joinSession(new JoinRequest(pin, "George", "cand-george-301"));
        liveSessionService.acknowledgeRules(pin, p.getCandidateId());
        liveSessionService.startQuestion(pin, 0);

        Question q1 = quiz.getQuestions().get(0);
        AnswerOption optB = q1.getOptions().stream().filter(o -> "B".equals(o.getOptionLabel())).findFirst().orElseThrow();

        mockMvc.perform(post("/api/sessions/" + pin + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"candidateId\":\"%s\",\"questionId\":%d,\"selectedOptionId\":%d,\"optionLabel\":\"B\"}",
                                p.getCandidateId(), q1.getId(), optB.getId())))
                .andExpect(status().isOk());

        liveSessionService.endQuiz(pin);

        // Faculty requests quiz examination history
        mockMvc.perform(get("/api/sessions/quiz/" + quiz.getId() + "/history")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].pin", is(pin)))
                .andExpect(jsonPath("$[0].totalCandidates", is(1)))
                .andExpect(jsonPath("$[0].totalMarks", is(10)));

        // Faculty requests saved results for results page
        mockMvc.perform(get("/api/sessions/" + pin + "/results")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pin", is(pin)))
                .andExpect(jsonPath("$.leaderboard", hasSize(1)))
                .andExpect(jsonPath("$.leaderboard[0].nickname", is("George")))
                .andExpect(jsonPath("$.leaderboard[0].score", is(10)));
    }

    @Test
    @DisplayName("Test E: Complete and individual PDF download reports generate valid binaries")
    void testPdfReportDownloads() throws Exception {
        String pin = session1.getPin();
        Participant p = liveSessionService.joinSession(new JoinRequest(pin, "Hannah", "cand-hannah-401"));
        liveSessionService.acknowledgeRules(pin, p.getCandidateId());
        liveSessionService.startQuestion(pin, 0);

        Question q1 = quiz.getQuestions().get(0);
        AnswerOption optB = q1.getOptions().stream().filter(o -> "B".equals(o.getOptionLabel())).findFirst().orElseThrow();

        mockMvc.perform(post("/api/sessions/" + pin + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"candidateId\":\"%s\",\"questionId\":%d,\"selectedOptionId\":%d,\"optionLabel\":\"B\"}",
                                p.getCandidateId(), q1.getId(), optB.getId())))
                .andExpect(status().isOk());

        liveSessionService.endQuiz(pin);

        // Complete exam PDF
        byte[] completePdf = mockMvc.perform(get("/api/reports/exam/" + pin + "/pdf")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(completePdf, 0, 4));

        // Individual student PDF
        byte[] studentPdf = mockMvc.perform(get("/api/reports/exam/" + pin + "/student/" + p.getCandidateId() + "/pdf")
                        .header("X-Candidate-Id", p.getCandidateId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(studentPdf, 0, 4));
    }

    @Test
    @DisplayName("Test F: Multiple examination sessions from the same quiz remain independent")
    void testMultipleSessionsIndependence() throws Exception {
        String pin1 = session1.getPin();
        Participant s1_p1 = liveSessionService.joinSession(new JoinRequest(pin1, "Student1_S1", "cand-s1-1"));
        liveSessionService.acknowledgeRules(pin1, s1_p1.getCandidateId());
        liveSessionService.startQuestion(pin1, 0);
        Question q1 = quiz.getQuestions().get(0);
        AnswerOption optB = q1.getOptions().stream().filter(o -> "B".equals(o.getOptionLabel())).findFirst().orElseThrow();
        mockMvc.perform(post("/api/sessions/" + pin1 + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"candidateId\":\"%s\",\"questionId\":%d,\"selectedOptionId\":%d,\"optionLabel\":\"B\"}",
                                s1_p1.getCandidateId(), q1.getId(), optB.getId())))
                .andExpect(status().isOk());
        liveSessionService.endQuiz(pin1);

        // Create Session 2 from the same quiz
        LiveSession session2 = liveSessionService.createLiveSession(quiz.getId());
        String pin2 = session2.getPin();
        assertNotEquals(pin1, pin2, "Pins must be distinct");

        Participant s2_p1 = liveSessionService.joinSession(new JoinRequest(pin2, "Student1_S2", "cand-s2-1"));
        Participant s2_p2 = liveSessionService.joinSession(new JoinRequest(pin2, "Student2_S2", "cand-s2-2"));
        liveSessionService.acknowledgeRules(pin2, s2_p1.getCandidateId());
        liveSessionService.acknowledgeRules(pin2, s2_p2.getCandidateId());
        liveSessionService.startQuestion(pin2, 0);
        liveSessionService.endQuiz(pin2);

        // Check Session 1 participants
        assertEquals(1, participantRepository.countBySessionPin(pin1));
        // Check Session 2 participants
        assertEquals(2, participantRepository.countBySessionPin(pin2));

        // Check history returns both sessions
        mockMvc.perform(get("/api/sessions/quiz/" + quiz.getId() + "/history")
                        .header("Authorization", facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}

