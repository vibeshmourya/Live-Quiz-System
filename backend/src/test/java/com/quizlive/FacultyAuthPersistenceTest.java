package com.quizlive;

import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
import com.quizlive.dto.RegisterRequest;
import com.quizlive.model.Faculty;
import com.quizlive.repository.FacultyRepository;
import com.quizlive.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FacultyAuthPersistenceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private FacultyRepository facultyRepository;

    @Test
    @DisplayName("Faculty should be able to log in with username")
    void testFacultyLoginWithUsername() {
        LoginRequest req = new LoginRequest("faculty", "password123");
        LoginResponse resp = authService.login(req);

        assertNotNull(resp);
        assertNotNull(resp.getToken());
        assertEquals("faculty", resp.getUsername());
        assertEquals("faculty@quizora.edu", resp.getEmail());
    }

    @Test
    @DisplayName("Faculty should be able to log in with case-insensitive email")
    void testFacultyLoginWithCaseInsensitiveEmail() {
        // Uppercase / mixed-case email
        LoginRequest req = new LoginRequest();
        req.setEmail("FACULTY@QUIZORA.EDU");
        req.setPassword("password123");

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("faculty", resp.getUsername());
        assertEquals("faculty@quizora.edu", resp.getEmail());
    }

    @Test
    @DisplayName("Invalid credentials must produce clear unauthorized error")
    void testInvalidLoginCredentials() {
        LoginRequest req = new LoginRequest("faculty", "wrongpassword");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.login(req);
        });
        assertEquals(401, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid credentials"));
    }

    @Test
    @DisplayName("Faculty session token must survive simulated server restart via stateless HMAC verification")
    void testTokenValidationAfterSimulatedRestart() throws Exception {
        LoginRequest req = new LoginRequest("faculty", "password123");
        LoginResponse resp = authService.login(req);
        String token = resp.getToken();
        String authHeader = "Bearer " + token;

        // Verify valid before restart
        Faculty facultyBefore = authService.validateFaculty(authHeader);
        assertNotNull(facultyBefore);
        assertEquals("faculty", facultyBefore.getUsername());

        // Simulate application restart by clearing in-memory activeSessions cache
        authService.clearActiveSessionsCacheForTesting();

        // After restart: token should still be securely restored and validated via stateless HMAC
        Faculty facultyAfter = authService.validateFaculty(authHeader);
        assertNotNull(facultyAfter, "Faculty must be recognized after server restart");
        assertEquals("faculty", facultyAfter.getUsername());

        Faculty optionalFaculty = authService.getFacultyOptional(authHeader);
        assertNotNull(optionalFaculty, "getFacultyOptional must recognize faculty after server restart");
    }

    @Test
    @DisplayName("Logout must invalidate session token")
    void testLogoutInvalidation() {
        LoginRequest req = new LoginRequest("faculty", "password123");
        LoginResponse resp = authService.login(req);
        String authHeader = "Bearer " + resp.getToken();

        // Valid before logout
        assertNotNull(authService.validateFaculty(authHeader));

        // Logout
        authService.logout(authHeader);

        // Rejected after logout
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.validateFaculty(authHeader);
        });
        assertEquals(401, ex.getStatusCode().value());
    }
}
