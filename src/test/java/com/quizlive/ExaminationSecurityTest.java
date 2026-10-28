package com.quizlive;

import com.quizlive.dto.ExamIncidentDto;
import com.quizlive.dto.IncidentReportRequest;
import com.quizlive.dto.IncidentReviewRequest;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ExaminationSecurityTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private ExamIncidentRepository incidentRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    private LiveSession session;
    private Participant participant;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        quiz = new Quiz();
        quiz.setTitle("Computer Networks Security Exam");
        quiz.setFacultyUsername("prof_vibesh");
        quiz.setRequireFullscreen(true);
        quiz.setMonitorTabSwitch(true);
        quiz.setRestrictCopyPaste(true);
        quiz.setWarningThreshold(3);
        quiz.setResultsPublished(false);
        quiz.setShowCorrectAnswers(false);
        quiz = quizRepository.save(quiz);

        session = new LiveSession();
        session.setPin("998877");
        session.setQuiz(quiz);
        session.setStatus(SessionStatus.QUESTION_ACTIVE);
        session = liveSessionRepository.save(session);

        participant = new Participant();
        participant.setLiveSession(session);
        participant.setSessionPin("998877");
        participant.setCandidateId("cand-test-001");
        participant.setNickname("Alice Walker");
        participant.setScore(80);
        participant = participantRepository.save(participant);
    }

    @Test
    @DisplayName("Should successfully record an examination incident with authoritative server timestamp")
    void testRecordIncidentSuccess() {
        IncidentReportRequest req = new IncidentReportRequest();
        req.setCandidateId(participant.getCandidateId());
        req.setEventType(IncidentType.FULLSCREEN_EXIT);
        req.setIncidentDetails("Browser window exited full-screen");

        ExamIncidentDto recorded = incidentService.reportIncident(session.getPin(), req);

        assertNotNull(recorded);
        assertNotNull(recorded.getId());
        assertEquals("Alice Walker", recorded.getNickname());
        assertEquals(IncidentType.FULLSCREEN_EXIT, recorded.getEventType());
        assertEquals(ReviewStatus.PENDING, recorded.getReviewStatus());
        assertNotNull(recorded.getServerTimestamp());
    }

    @Test
    @DisplayName("Should debounce and suppress duplicate incident reports within 3 seconds")
    void testIncidentDeduplication() {
        IncidentReportRequest req = new IncidentReportRequest();
        req.setCandidateId(participant.getCandidateId());
        req.setEventType(IncidentType.TAB_SWITCH);
        req.setIncidentDetails("Candidate navigated away from tab");

        ExamIncidentDto first = incidentService.reportIncident(session.getPin(), req);
        assertNotNull(first);

        // Immediate duplicate trigger
        ExamIncidentDto second = incidentService.reportIncident(session.getPin(), req);
        assertNotNull(second);
        assertEquals(first.getId(), second.getId(), "Duplicate incident within 3 seconds should return existing record without duplicate insertion");

        long count = incidentRepository.countByLiveSessionIdAndParticipantId(session.getId(), participant.getId());
        assertEquals(1, count, "Only one incident entity should exist in repository");
    }

    @Test
    @DisplayName("Faculty should be able to review and update incident status")
    void testFacultyIncidentReview() {
        IncidentReportRequest req = new IncidentReportRequest();
        req.setCandidateId(participant.getCandidateId());
        req.setEventType(IncidentType.COPY_PASTE_ATTEMPT);
        req.setIncidentDetails("Clipboard paste intercepted");

        ExamIncidentDto recorded = incidentService.reportIncident(session.getPin(), req);

        IncidentReviewRequest reviewReq = new IncidentReviewRequest();
        reviewReq.setReviewStatus(ReviewStatus.FLAGGED);
        reviewReq.setReviewNotes("Repeated paste attempt on question 2");

        ExamIncidentDto reviewed = incidentService.reviewIncident(recorded.getId(), reviewReq);

        assertEquals(ReviewStatus.FLAGGED, reviewed.getReviewStatus());
        assertEquals("Repeated paste attempt on question 2", reviewed.getReviewNotes());
    }

    @Test
    @DisplayName("Should reject incident reporting for invalid candidate or invalid session PIN")
    void testIncidentValidationSecurity() {
        IncidentReportRequest req = new IncidentReportRequest();
        req.setCandidateId("non-existent-candidate");
        req.setEventType(IncidentType.TAB_SWITCH);

        assertThrows(ResponseStatusException.class, () -> {
            incidentService.reportIncident(session.getPin(), req);
        });

        assertThrows(ResponseStatusException.class, () -> {
            incidentService.reportIncident("000000", req);
        });
    }
}
