package com.quizlive.dto;

import java.time.LocalDateTime;

public class AnswerVerificationResponse {
    private boolean success;
    private boolean verified;
    private Long questionId;
    private Long selectedOptionId;
    private String submittedLabel;
    private Boolean isCorrect;
    private Integer marksAwarded;
    private String status;
    private String message;
    private LocalDateTime submittedAt;

    public AnswerVerificationResponse() {}

    public AnswerVerificationResponse(boolean success, boolean verified, Long questionId,
                                      Long selectedOptionId, String submittedLabel,
                                      Boolean isCorrect, Integer marksAwarded,
                                      String status, String message, LocalDateTime submittedAt) {
        this.success = success;
        this.verified = verified;
        this.questionId = questionId;
        this.selectedOptionId = selectedOptionId;
        this.submittedLabel = submittedLabel;
        this.isCorrect = isCorrect;
        this.marksAwarded = marksAwarded;
        this.status = status;
        this.message = message;
        this.submittedAt = submittedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getSelectedOptionId() {
        return selectedOptionId;
    }

    public void setSelectedOptionId(Long selectedOptionId) {
        this.selectedOptionId = selectedOptionId;
    }

    public String getSubmittedLabel() {
        return submittedLabel;
    }

    public void setSubmittedLabel(String submittedLabel) {
        this.submittedLabel = submittedLabel;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public Integer getMarksAwarded() {
        return marksAwarded;
    }

    public void setMarksAwarded(Integer marksAwarded) {
        this.marksAwarded = marksAwarded;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}

