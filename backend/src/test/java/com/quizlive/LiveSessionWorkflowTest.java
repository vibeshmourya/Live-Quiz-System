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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LiveSessionWorkflowTest {

    @Autowired
    private LiveSessionService liveSessionService;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    private Quiz quiz;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        quiz = new Quiz("Operating Systems Midterm");
        quiz.setFacultyUsername("prof_vibesh");
        quiz.setExamStatus(ExamStatus.SCHEDULED);
        quiz.setAnswerChangePolicy(AnswerChangePolicy.SINGLE_SELECTION);
        quiz.setRequireFullscreen(true);
        quiz.setShowCorrectAnswers(true);

        List<Question> questions = new ArrayList<>();
        Question q1 = new Question();
        q1.setQuestionText("What is thrashing in Virtual Memory?");
        q1.setTimeLimit(20);
        q1.setMarks(2);
        q1.setOrderIndex(0);
        q1.setQuiz(quiz);

        List<AnswerOption> options = new ArrayList<>();
        AnswerOption optA = new AnswerOption("A", "High page fault rate and low CPU utilization", true);
        optA.setQuestion(q1);

        AnswerOption optB = new AnswerOption("B", "Excessive disk fragmentation", false);
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
        session.setStatus(SessionStatus.WAITING);
        session = liveSessionRepository.save(session);
    }

    @Test
    @DisplayName("Participant join should register candidate, list in lobby roster, and require rules acknowledgment")
    void testParticipantLobbyAndRulesWorkflow() {
        JoinRequest joinReq = new JoinRequest("652074", "John Doe", null);

        Participant participant = liveSessionService.joinSession(joinReq);
        assertNotNull(participant);
        assertEquals("652074", participant.getSessionPin());
        assertNotNull(participant.getCandidateId());

        // Check participant in lobby
        List<ParticipantDto> participants = liveSessionService.getSessionParticipants(session.getPin());
        assertEquals(1, participants.size());
        assertEquals("John Doe", participants.get(0).getNickname());
        assertFalse(participants.get(0).isRulesAcknowledged(), "Rules should initially be unacknowledged");

        // Candidate state should reflect unacknowledged rules
        CandidateStateResponse candidateState = liveSessionService.getCandidateSessionState(session.getPin(), participant.getCandidateId());
        assertFalse(candidateState.isRulesAcknowledged());

        // Acknowledge rules
        Participant acked = liveSessionService.acknowledgeRules(session.getPin(), participant.getCandidateId());
        assertNotNull(acked);
        assertTrue(Boolean.TRUE.equals(acked.getRulesAcknowledged()));

        // Verify roster updated
        participants = liveSessionService.getSessionParticipants(session.getPin());
        assertTrue(participants.get(0).isRulesAcknowledged());
    }

    @Test
    @DisplayName("Student question payload must NOT leak correct answer before faculty reveal")
    void testCorrectAnswerConfidentiality() {
        JoinRequest joinReq = new JoinRequest("652074", "Alice", null);
        Participant participant = liveSessionService.joinSession(joinReq);
        liveSessionService.acknowledgeRules(session.getPin(), participant.getCandidateId());

        // Start Question 0
        liveSessionService.startQuestion(session.getPin(), 0);

        CandidateStateResponse activeState = liveSessionService.getCandidateSessionState(session.getPin(), participant.getCandidateId());
        assertEquals(SessionStatus.QUESTION_ACTIVE, activeState.getStatus());
        assertNull(activeState.getRevealedCorrectLabel(), "Revealed label must be null while question is active");
        assertNotNull(activeState.getCurrentQuestion());

        // Check option DTOs
        for (CandidateOptionDto opt : activeState.getCurrentQuestion().getOptions()) {
            assertNotNull(opt.getOptionLabel());
            assertNotNull(opt.getOptionText());
        }

        // Faculty reveals answer
        liveSessionService.revealAnswer(session.getPin());

        CandidateStateResponse revealedState = liveSessionService.getCandidateSessionState(session.getPin(), participant.getCandidateId());
        assertEquals(SessionStatus.QUESTION_REVEALED, revealedState.getStatus());
        assertEquals("A", revealedState.getRevealedCorrectLabel(), "Correct label should be revealed only after faculty reveal");
    }

    @Test
    @DisplayName("Submissions should be rejected if rules not acknowledged or if server deadline expired")
    void testAnswerRejectionEnforcement() {
        JoinRequest joinReq = new JoinRequest("652074", "Bob", null);
        Participant participant = liveSessionService.joinSession(joinReq);

        liveSessionService.startQuestion(session.getPin(), 0);
        Question q = quiz.getQuestions().get(0);

        // Attempt submission without acknowledging rules
        SubmitAnswerRequest unackReq = new SubmitAnswerRequest();
        unackReq.setSessionPin(session.getPin());
        unackReq.setCandidateId(participant.getCandidateId());
        unackReq.setQuestionId(q.getId());
        unackReq.setOptionLabel("A");

        IllegalStateException exRules = assertThrows(IllegalStateException.class, () -> {
            liveSessionService.submitAnswer(unackReq);
        });
        assertTrue(exRules.getMessage().contains("Mandatory examination rules must be acknowledged"));

        // Acknowledge rules
        liveSessionService.acknowledgeRules(session.getPin(), participant.getCandidateId());

        // Now simulate expired server deadline
        LiveSession activeSession = liveSessionRepository.findByPin(session.getPin()).orElseThrow();
        activeSession.setQuestionEndsAtEpochMs(System.currentTimeMillis() - 5000); // 5 seconds in the past
        liveSessionRepository.save(activeSession);

        IllegalStateException exTime = assertThrows(IllegalStateException.class, () -> {
            liveSessionService.submitAnswer(unackReq);
        });
        assertTrue(exTime.getMessage().contains("Time has expired for this question"));
    }
}
