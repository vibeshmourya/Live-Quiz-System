package com.quizlive.dto;

import java.time.LocalDateTime;

public class ParticipantDto {
    private Long id;
    private String candidateId;
    private String nickname;
    private Integer score;
    private Boolean connected;
    private Boolean rulesAcknowledged;
    private LocalDateTime joinedAt;

    public ParticipantDto() {}

    public ParticipantDto(Long id, String candidateId, String nickname, Integer score, Boolean connected, Boolean rulesAcknowledged, LocalDateTime joinedAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.nickname = nickname;
        this.score = score;
        this.connected = connected;
        this.rulesAcknowledged = rulesAcknowledged;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Boolean getRulesAcknowledged() {
        return rulesAcknowledged;
    }

    public boolean isRulesAcknowledged() {
        return Boolean.TRUE.equals(rulesAcknowledged);
    }

    public void setRulesAcknowledged(Boolean rulesAcknowledged) {
        this.rulesAcknowledged = rulesAcknowledged;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
