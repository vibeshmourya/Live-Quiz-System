package com.quizlive.controller;

import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
import com.quizlive.model.Faculty;
import com.quizlive.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody com.quizlive.dto.RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Faculty faculty = authService.validateFaculty(authHeader);
        return ResponseEntity.ok(Map.of(
                "username", faculty.getUsername(),
                "email", faculty.getEmail() != null ? faculty.getEmail() : "",
                "name", faculty.getName(),
                "role", faculty.getRole()
        ));
    }
}
