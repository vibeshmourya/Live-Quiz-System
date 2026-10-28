package com.quizlive.dto;

import java.util.ArrayList;
import java.util.List;

public class CandidateQuestionDto {
    private Long id;
    private String questionText;
    private Integer timeLimit;
    private Integer marks;
    private Integer orderIndex;
    private List<CandidateOptionDto> options = new ArrayList<>();

    public CandidateQuestionDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public Integer getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(Integer timeLimit) {
        this.timeLimit = timeLimit;
    }

    public Integer getMarks() {
        return marks;
    }

    public void setMarks(Integer marks) {
        this.marks = marks;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public List<CandidateOptionDto> getOptions() {
        return options;
    }

    public void setOptions(List<CandidateOptionDto> options) {
        this.options = options;
    }
}
