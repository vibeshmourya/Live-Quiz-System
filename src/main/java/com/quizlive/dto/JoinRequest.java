package com.quizlive.dto;

import jakarta.validation.constraints.NotBlank;

public class JoinRequest {

    @NotBlank(message = "Session PIN is required")
    private String sessionPin;

    @NotBlank(message = "Nickname is required")
    private String nickname;

    private String candidateId;

    public JoinRequest() {}

    public JoinRequest(String sessionPin, String nickname, String candidateId) {
        this.sessionPin = sessionPin;
        this.nickname = nickname;
        this.candidateId = candidateId;
    }

    public String getSessionPin() {
        return sessionPin;
    }

    public void setSessionPin(String sessionPin) {
        this.sessionPin = sessionPin;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }
}
