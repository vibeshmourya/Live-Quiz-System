package com.quizlive.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "participants", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"live_session_id", "candidateId"})
})
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "live_session_id", nullable = false)
    @JsonIgnore
    private LiveSession liveSession;

    @Column(nullable = false, length = 10)
    private String sessionPin;

    @Column(nullable = false, length = 100)
    private String candidateId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false)
    private Boolean connected = true;

    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public Participant() {}

    public Participant(LiveSession liveSession, String sessionPin, String candidateId, String nickname) {
        this.liveSession = liveSession;
        this.sessionPin = sessionPin;
        this.candidateId = candidateId;
        this.nickname = nickname;
        this.score = 0;
        this.connected = true;
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LiveSession getLiveSession() {
        return liveSession;
    }

    public void setLiveSession(LiveSession liveSession) {
        this.liveSession = liveSession;
    }

    public String getSessionPin() {
        return sessionPin;
    }

    public void setSessionPin(String sessionPin) {
        this.sessionPin = sessionPin;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Boolean getConnected() {
        return connected;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
