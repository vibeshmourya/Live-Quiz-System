package com.quizlive.dto;

import com.quizlive.model.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IncidentReportRequest {

    @NotBlank(message = "Candidate ID is required")
    private String candidateId;

    @NotNull(message = "Incident type is required")
    private IncidentType eventType;

    private String incidentDetails;

    public IncidentReportRequest() {}

    public IncidentReportRequest(String candidateId, IncidentType eventType, String incidentDetails) {
        this.candidateId = candidateId;
        this.eventType = eventType;
        this.incidentDetails = incidentDetails;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
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
}
