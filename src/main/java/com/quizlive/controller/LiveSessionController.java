package com.quizlive.controller;

import com.quizlive.dto.*;
import com.quizlive.model.LiveSession;
import com.quizlive.model.Participant;
import com.quizlive.model.Submission;
import com.quizlive.service.AuthService;
import com.quizlive.service.LiveSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;
    private final AuthService authService;

    public LiveSessionController(LiveSessionService liveSessionService, AuthService authService) {
        this.liveSessionService = liveSessionService;
        this.authService = authService;
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
                    "score", participant.getScore()
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
}
