package com.quizlive.dto;

public class AnswerOptionDto {
    private Long id;
    private String optionLabel;
    private String optionText;
    private Boolean isCorrect;

    public AnswerOptionDto() {}

    public AnswerOptionDto(Long id, String optionLabel, String optionText, Boolean isCorrect) {
        this.id = id;
        this.optionLabel = optionLabel;
        this.optionText = optionText;
        this.isCorrect = isCorrect;
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

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
}
