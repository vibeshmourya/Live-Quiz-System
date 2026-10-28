package com.quizlive.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "live_sessions")
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String pin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status = SessionStatus.WAITING;

    @Column(nullable = false)
    private Integer currentQuestionIndex = 0;

    private Long questionStartedAtEpochMs;
    private Long questionEndsAtEpochMs;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "liveSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Participant> participants = new ArrayList<>();

    public LiveSession() {}

    public LiveSession(String pin, Quiz quiz) {
        this.pin = pin;
        this.quiz = quiz;
        this.status = SessionStatus.WAITING;
        this.currentQuestionIndex = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Integer getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void setCurrentQuestionIndex(Integer currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    public Long getQuestionStartedAtEpochMs() {
        return questionStartedAtEpochMs;
    }

    public void setQuestionStartedAtEpochMs(Long questionStartedAtEpochMs) {
        this.questionStartedAtEpochMs = questionStartedAtEpochMs;
    }

    public Long getQuestionEndsAtEpochMs() {
        return questionEndsAtEpochMs;
    }

    public void setQuestionEndsAtEpochMs(Long questionEndsAtEpochMs) {
        this.questionEndsAtEpochMs = questionEndsAtEpochMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }
}
