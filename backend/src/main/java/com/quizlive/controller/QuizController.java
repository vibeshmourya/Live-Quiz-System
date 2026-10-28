package com.quizlive.controller;

import com.quizlive.dto.QuizDto;
import com.quizlive.model.ExamStatus;
import com.quizlive.model.Faculty;
import com.quizlive.service.AuthService;
import com.quizlive.service.QuizService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final QuizService quizService;
    private final AuthService authService;

    public QuizController(QuizService quizService, AuthService authService) {
        this.quizService = quizService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<QuizDto>> getAllQuizzes(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Faculty faculty = authService.getFacultyOptional(authHeader);
        boolean isFaculty = (faculty != null);
        return ResponseEntity.ok(quizService.getAllQuizzes(isFaculty));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getQuizById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            Faculty faculty = authService.getFacultyOptional(authHeader);
            boolean isFaculty = (faculty != null);
            return ResponseEntity.ok(quizService.getQuizById(id, isFaculty));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<?> getFacultyPreview(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            return ResponseEntity.ok(quizService.getFacultyPreview(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createQuiz(
            @RequestBody QuizDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            Faculty faculty = authService.validateFaculty(authHeader);
            QuizDto saved = quizService.saveOrUpdateDraft(dto, faculty.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/draft")
    public ResponseEntity<?> saveDraft(
            @RequestBody QuizDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            Faculty faculty = authService.validateFaculty(authHeader);
            dto.setExamStatus(ExamStatus.DRAFT);
            QuizDto saved = quizService.saveOrUpdateDraft(dto, faculty.getUsername());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateQuiz(
            @PathVariable Long id,
            @RequestBody QuizDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            Faculty faculty = authService.validateFaculty(authHeader);
            dto.setId(id);
            QuizDto saved = quizService.saveOrUpdateDraft(dto, faculty.getUsername());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/questions/{qId}/duplicate")
    public ResponseEntity<?> duplicateQuestion(
            @PathVariable Long id,
            @PathVariable Long qId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            QuizDto updated = quizService.duplicateQuestion(id, qId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/questions/{qId}")
    public ResponseEntity<?> deleteQuestion(
            @PathVariable Long id,
            @PathVariable Long qId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            QuizDto updated = quizService.deleteQuestion(id, qId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/questions/reorder")
    public ResponseEntity<?> reorderQuestions(
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            List<Long> questionIds = payload.get("questionIds");
            if (questionIds == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "questionIds list required"));
            }
            QuizDto updated = quizService.reorderQuestions(id, questionIds);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            String statusStr = payload.get("status");
            ExamStatus status = ExamStatus.valueOf(statusStr.toUpperCase());
            QuizDto updated = quizService.updateExamStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/publication")
    public ResponseEntity<?> updatePublication(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            Boolean published = payload.get("resultsPublished");
            if (published == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "resultsPublished boolean required"));
            }
            QuizDto updated = quizService.updateResultPublication(id, published);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<?> duplicateQuiz(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            Faculty faculty = authService.validateFaculty(authHeader);
            QuizDto duplicated = quizService.duplicateQuiz(id, faculty.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(duplicated);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuiz(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            authService.validateFaculty(authHeader);
            quizService.deleteQuiz(id);
            return ResponseEntity.ok(Map.of("success", true, "deletedId", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
