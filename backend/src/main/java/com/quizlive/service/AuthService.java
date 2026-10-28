package com.quizlive.service;

import com.quizlive.dto.LoginRequest;
import com.quizlive.dto.LoginResponse;
import com.quizlive.model.Faculty;
import com.quizlive.repository.FacultyRepository;
import com.quizlive.util.PasswordUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final FacultyRepository facultyRepository;
    private final Map<String, FacultySession> activeSessions = new ConcurrentHashMap<>();

    public AuthService(FacultyRepository facultyRepository) {
        this.facultyRepository = facultyRepository;
    }

    public static class FacultySession {
        private final Faculty faculty;
        private final long createdAtEpochMs;

        public FacultySession(Faculty faculty) {
            this.faculty = faculty;
            this.createdAtEpochMs = System.currentTimeMillis();
        }

        public Faculty getFaculty() {
            return faculty;
        }

        public long getCreatedAtEpochMs() {
            return createdAtEpochMs;
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getIdentifier();
        if (identifier.isEmpty() || request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email/username and password are required");
        }

        Faculty faculty = facultyRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        boolean matches = PasswordUtil.verifyPassword(request.getPassword(), faculty.getPasswordHash());
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();
        activeSessions.put(token, new FacultySession(faculty));

        return new LoginResponse(token, faculty.getUsername(), faculty.getEmail(), faculty.getName(), faculty.getRole());
    }

    @Transactional
    public LoginResponse register(com.quizlive.dto.RegisterRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
        }
        if (request.getEmail() == null || !request.getEmail().trim().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters long");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (facultyRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A faculty account with this email already exists");
        }

        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        if (baseUsername.isEmpty()) {
            baseUsername = "faculty";
        }
        String username = baseUsername;
        int counter = 1;
        while (facultyRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }

        String passwordHash = PasswordUtil.hashPassword(request.getPassword());
        Faculty faculty = new Faculty(username, email, passwordHash, request.getName().trim());
        faculty = facultyRepository.save(faculty);

        String token = UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();
        activeSessions.put(token, new FacultySession(faculty));

        return new LoginResponse(token, faculty.getUsername(), faculty.getEmail(), faculty.getName(), faculty.getRole());
    }

    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            activeSessions.remove(token);
        }
    }

    public Faculty validateFaculty(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication token required for faculty operations");
        }
        String token = authHeader.substring(7).trim();
        FacultySession session = activeSessions.get(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired faculty session");
        }
        // Optional session expiration (e.g. 24 hours)
        long maxAge = 24 * 60 * 60 * 1000L;
        if (System.currentTimeMillis() - session.getCreatedAtEpochMs() > maxAge) {
            activeSessions.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faculty session has expired. Please log in again.");
        }
        return session.getFaculty();
    }

    public Faculty getFacultyOptional(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        FacultySession session = activeSessions.get(token);
        return session != null ? session.getFaculty() : null;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultFacultyIfEmpty() {
        if (facultyRepository.count() == 0) {
            String defaultPassword = "password123";
            String hash = PasswordUtil.hashPassword(defaultPassword);
            Faculty faculty = new Faculty("faculty", "faculty@quizora.edu", hash, "Prof. Examination Controller");
            facultyRepository.save(faculty);
            System.out.println("Default faculty account initialized: email='faculty@quizora.edu', username='faculty', password='password123'");
        }
    }
}
