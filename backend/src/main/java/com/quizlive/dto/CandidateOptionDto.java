package com.quizlive.dto;

public class CandidateOptionDto {
    private Long id;
    private String optionLabel;
    private String optionText;

    public CandidateOptionDto() {}

    public CandidateOptionDto(Long id, String optionLabel, String optionText) {
        this.id = id;
        this.optionLabel = optionLabel;
        this.optionText = optionText;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public void setOptionLabel(String optionLabel) {
        this.optionLabel = optionLabel;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }
}
