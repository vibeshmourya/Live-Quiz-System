package com.quizlive.dto;

import com.quizlive.model.IncidentType;
import com.quizlive.model.ReviewStatus;
import java.time.LocalDateTime;

public class ExamIncidentDto {
    private Long id;
    private Long liveSessionId;
    private String sessionPin;
    private Long participantId;
    private String candidateId;
    private String nickname;
    private IncidentType eventType;
    private String incidentDetails;
    private LocalDateTime serverTimestamp;
    private ReviewStatus reviewStatus;
    private String reviewNotes;

    public ExamIncidentDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLiveSessionId() {
        return liveSessionId;
    }

    public void setLiveSessionId(Long liveSessionId) {
        this.liveSessionId = liveSessionId;
    }

    public String getSessionPin() {
        return sessionPin;
    }

    public void setSessionPin(String sessionPin) {
        this.sessionPin = sessionPin;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public void setParticipantId(Long participantId) {
        this.participantId = participantId;
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

    public IncidentType getEventType() {
        return eventType;
    }

    public void setEventType(IncidentType eventType) {
        this.eventType = eventType;
    }

    public String getIncidentDetails() {
        return incidentDetails;
    }

    public void setIncidentDetails(String incidentDetails) {
        this.incidentDetails = incidentDetails;
    }

    public LocalDateTime getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(LocalDateTime serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
