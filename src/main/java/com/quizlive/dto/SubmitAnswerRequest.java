package com.quizlive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SubmitAnswerRequest {

    private String sessionPin;

    @NotBlank(message = "Candidate ID is required")
    private String candidateId;

    @NotNull(message = "Question ID is required")
    private Long questionId;

    @NotBlank(message = "Option label is required")
    private String optionLabel; // "A", "B", "C", "D"

    public SubmitAnswerRequest() {}

    public SubmitAnswerRequest(String sessionPin, String candidateId, Long questionId, String optionLabel) {
        this.sessionPin = sessionPin;
        this.candidateId = candidateId;
        this.questionId = questionId;
        this.optionLabel = optionLabel;
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

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public void setOptionLabel(String optionLabel) {
        this.optionLabel = optionLabel;
    }
}
