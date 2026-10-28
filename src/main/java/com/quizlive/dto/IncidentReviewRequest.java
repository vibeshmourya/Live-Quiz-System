package com.quizlive.dto;

import com.quizlive.model.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public class IncidentReviewRequest {

    @NotNull(message = "Review status is required")
    private ReviewStatus reviewStatus;

    private String reviewNotes;

    public IncidentReviewRequest() {}

    public IncidentReviewRequest(ReviewStatus reviewStatus, String reviewNotes) {
        this.reviewStatus = reviewStatus;
        this.reviewNotes = reviewNotes;
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
