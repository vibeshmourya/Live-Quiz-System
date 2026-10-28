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
    private Boolean requireFullscreen = false;
    private Boolean monitorTabSwitch = false;
    private Boolean restrictCopyPaste = false;
    private Integer warningThreshold = 3;
    private Boolean shuffleQuestions = false;
    private Boolean shuffleOptions = false;
    private Boolean autoSubmitAtDeadline = true;
    private Boolean resultsPublished = true;
    private Boolean showCorrectAnswers = true;
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

    public Boolean getRequireFullscreen() {
        return requireFullscreen != null ? requireFullscreen : false;
    }

    public void setRequireFullscreen(Boolean requireFullscreen) {
        this.requireFullscreen = requireFullscreen != null ? requireFullscreen : false;
    }

    public Boolean getMonitorTabSwitch() {
        return monitorTabSwitch != null ? monitorTabSwitch : false;
    }

    public void setMonitorTabSwitch(Boolean monitorTabSwitch) {
        this.monitorTabSwitch = monitorTabSwitch != null ? monitorTabSwitch : false;
    }

    public Boolean getRestrictCopyPaste() {
        return restrictCopyPaste != null ? restrictCopyPaste : false;
    }

    public void setRestrictCopyPaste(Boolean restrictCopyPaste) {
        this.restrictCopyPaste = restrictCopyPaste != null ? restrictCopyPaste : false;
    }

    public Integer getWarningThreshold() {
        return warningThreshold != null ? warningThreshold : 3;
    }

    public void setWarningThreshold(Integer warningThreshold) {
        this.warningThreshold = warningThreshold != null ? warningThreshold : 3;
    }

    public Boolean getShuffleQuestions() {
        return shuffleQuestions != null ? shuffleQuestions : false;
    }

    public void setShuffleQuestions(Boolean shuffleQuestions) {
        this.shuffleQuestions = shuffleQuestions != null ? shuffleQuestions : false;
    }

    public Boolean getShuffleOptions() {
        return shuffleOptions != null ? shuffleOptions : false;
    }

    public void setShuffleOptions(Boolean shuffleOptions) {
        this.shuffleOptions = shuffleOptions != null ? shuffleOptions : false;
    }

    public Boolean getAutoSubmitAtDeadline() {
        return autoSubmitAtDeadline != null ? autoSubmitAtDeadline : true;
    }

    public void setAutoSubmitAtDeadline(Boolean autoSubmitAtDeadline) {
        this.autoSubmitAtDeadline = autoSubmitAtDeadline != null ? autoSubmitAtDeadline : true;
    }

    public Boolean getResultsPublished() {
        return resultsPublished != null ? resultsPublished : true;
    }

    public void setResultsPublished(Boolean resultsPublished) {
        this.resultsPublished = resultsPublished != null ? resultsPublished : true;
    }

    public Boolean getShowCorrectAnswers() {
        return showCorrectAnswers != null ? showCorrectAnswers : true;
    }

    public void setShowCorrectAnswers(Boolean showCorrectAnswers) {
        this.showCorrectAnswers = showCorrectAnswers != null ? showCorrectAnswers : true;
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
