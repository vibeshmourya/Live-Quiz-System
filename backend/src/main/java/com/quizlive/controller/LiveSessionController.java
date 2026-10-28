package com.quizlive.controller;

import com.quizlive.dto.*;
import com.quizlive.model.LiveSession;
import com.quizlive.model.Participant;
import com.quizlive.model.Submission;
import com.quizlive.service.AuthService;
import com.quizlive.service.IncidentService;
import com.quizlive.service.LiveSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;
    private final AuthService authService;
    private final IncidentService incidentService;

    public LiveSessionController(
            LiveSessionService liveSessionService,
            AuthService authService,
            IncidentService incidentService
    ) {
        this.liveSessionService = liveSessionService;
        this.authService = authService;
        this.incidentService = incidentService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createSession(
            @RequestBody Map<String, Long> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            Long quizId = payload.get("quizId");
            if (quizId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "quizId is required"));
            }
            LiveSession session = liveSessionService.createLiveSession(quizId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "sessionId", session.getId(),
                    "pin", session.getPin(),
                    "quizTitle", session.getQuiz().getTitle()
            ));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinSession(@Valid @RequestBody JoinRequest request) {
        try {
            Participant participant = liveSessionService.joinSession(request);
            return ResponseEntity.ok(Map.of(
                    "sessionPin", participant.getSessionPin(),
                    "candidateId", participant.getCandidateId(),
                    "nickname", participant.getNickname(),
                    "score", participant.getScore(),
                    "rulesAcknowledged", Boolean.TRUE.equals(participant.getRulesAcknowledged())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{pin}/participants")
    public ResponseEntity<?> getParticipants(@PathVariable String pin) {
        try {
            List<ParticipantDto> participants = liveSessionService.getSessionParticipants(pin);
            return ResponseEntity.ok(participants);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{pin}/acknowledge-rules")
    public ResponseEntity<?> acknowledgeRules(
            @PathVariable String pin,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String candidateId = payload.get("candidateId");
            if (candidateId == null || candidateId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "candidateId is required"));
            }
            Participant participant = liveSessionService.acknowledgeRules(pin, candidateId);
            return ResponseEntity.ok(Map.of(
                    "candidateId", participant.getCandidateId(),
                    "rulesAcknowledged", true
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{pin}/host")
    public ResponseEntity<?> getHostState(@PathVariable String pin) {
        try {
            SessionStateResponse state = liveSessionService.getHostSessionState(pin);
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{pin}/candidate")
    public ResponseEntity<?> getCandidateState(
            @PathVariable String pin,
            @RequestParam(required = false) String candidateId
    ) {
        try {
            CandidateStateResponse state = liveSessionService.getCandidateSessionState(pin, candidateId);
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{pin}/start-question")
    public ResponseEntity<?> startQuestion(
            @PathVariable String pin,
            @RequestBody(required = false) Map<String, Integer> payload
    ) {
        try {
            int questionIndex = (payload != null && payload.get("questionIndex") != null)
                    ? payload.get("questionIndex")
                    : 0;
            SessionStateResponse response = liveSessionService.startQuestion(pin, questionIndex);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{pin}/submit")
    public ResponseEntity<?> submitAnswer(
            @PathVariable String pin,
            @Valid @RequestBody SubmitAnswerRequest request
    ) {
        try {
            request.setSessionPin(pin);
            Submission submission = liveSessionService.submitAnswer(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "submittedLabel", submission.getSelectedLabel(),
                    "submittedAt", submission.getSubmittedAt()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{pin}/reveal")
    public ResponseEntity<?> revealAnswer(@PathVariable String pin) {
        try {
            SessionStateResponse response = liveSessionService.revealAnswer(pin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{pin}/next")
    public ResponseEntity<?> advanceOrEnd(@PathVariable String pin) {
        try {
            SessionStateResponse response = liveSessionService.advanceOrEnd(pin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{pin}/end")
    public ResponseEntity<?> endQuiz(@PathVariable String pin) {
        try {
            SessionStateResponse response = liveSessionService.endQuiz(pin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- SECURE EXAMINATION INCIDENT MONITORING ENDPOINTS ---

    @PostMapping("/{pin}/incidents")
    public ResponseEntity<?> reportIncident(
            @PathVariable String pin,
            @Valid @RequestBody IncidentReportRequest request
    ) {
        try {
            ExamIncidentDto incident = incidentService.reportIncident(pin, request);
            return ResponseEntity.ok(incident);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{pin}/incidents")
    public ResponseEntity<?> getIncidents(
            @PathVariable String pin,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            List<ExamIncidentDto> incidents = incidentService.getIncidentsForSession(pin);
            return ResponseEntity.ok(incidents);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{pin}/incidents/summary")
    public ResponseEntity<?> getIncidentSummary(
            @PathVariable String pin,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            Map<String, Object> summary = incidentService.getIncidentSummary(pin);
            return ResponseEntity.ok(summary);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{pin}/incidents/{incidentId}/review")
    public ResponseEntity<?> reviewIncident(
            @PathVariable String pin,
            @PathVariable Long incidentId,
            @Valid @RequestBody IncidentReviewRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            ExamIncidentDto updated = incidentService.reviewIncident(incidentId, request);
            return ResponseEntity.ok(updated);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{pin}/incidents/my-count")
    public ResponseEntity<?> getMyIncidentCount(
            @PathVariable String pin,
            @RequestParam String candidateId
    ) {
        try {
            long count = incidentService.getIncidentCountForParticipant(pin, candidateId);
            return ResponseEntity.ok(Map.of("incidentCount", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
