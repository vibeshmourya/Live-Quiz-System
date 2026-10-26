package com.quizlive.dto;

import com.quizlive.model.AnswerChangePolicy;
import com.quizlive.model.ExamStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuizDto {
    private Long id;
    private String title;
    private ExamStatus examStatus = ExamStatus.DRAFT;
    private AnswerChangePolicy answerChangePolicy = AnswerChangePolicy.SINGLE_SELECTION;
    private String facultyUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int totalMarks = 0;
    private int questionCount = 0;
    private List<QuestionDto> questions = new ArrayList<>();

    public QuizDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ExamStatus getExamStatus() {
        return examStatus;
    }

    public void setExamStatus(ExamStatus examStatus) {
        this.examStatus = examStatus;
    }

    public AnswerChangePolicy getAnswerChangePolicy() {
        return answerChangePolicy != null ? answerChangePolicy : AnswerChangePolicy.SINGLE_SELECTION;
    }

    public void setAnswerChangePolicy(AnswerChangePolicy answerChangePolicy) {
        this.answerChangePolicy = answerChangePolicy;
    }

    public String getFacultyUsername() {
        return facultyUsername;
    }

    public void setFacultyUsername(String facultyUsername) {
        this.facultyUsername = facultyUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(int totalMarks) {
        this.totalMarks = totalMarks;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public List<QuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDto> questions) {
        this.questions = questions;
    }
}
