package com.quizlive.dto;

import com.quizlive.model.SessionStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionStateResponse {
    private String pin;
    private Long quizId;
    private String quizTitle;
    private SessionStatus status;
    private int currentQuestionIndex;
    private int totalQuestions;
    private QuestionDto currentQuestion;
    private String correctOptionLabel; // Revealed only when QUESTION_REVEALED or FINISHED
    private double correctPercentage;  // Revealed only when QUESTION_REVEALED or FINISHED
    private Long questionStartedAtEpochMs;
    private Long questionEndsAtEpochMs;
    private long remainingSeconds;
    private long participantCount;
    private long submissionCount;
    private long totalIncidents = 0;
    private boolean requireFullscreen;
    private boolean monitorTabSwitch;
    private boolean restrictCopyPaste;
    private Map<String, Long> optionCounts = new HashMap<>();
    private List<LeaderboardEntryDto> leaderboard;

    public SessionStateResponse() {}

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public Long getQuizId() {
        return quizId;
    }

    public void setQuizId(Long quizId) {
        this.quizId = quizId;
    }

    public String getQuizTitle() {
        return quizTitle;
    }

    public void setQuizTitle(String quizTitle) {
        this.quizTitle = quizTitle;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public QuestionDto getCurrentQuestion() {
        return currentQuestion;
    }

    public void setCurrentQuestion(QuestionDto currentQuestion) {
        this.currentQuestion = currentQuestion;
    }

    public String getCorrectOptionLabel() {
        return correctOptionLabel;
    }

    public void setCorrectOptionLabel(String correctOptionLabel) {
        this.correctOptionLabel = correctOptionLabel;
    }

    public double getCorrectPercentage() {
        return correctPercentage;
    }

    public void setCorrectPercentage(double correctPercentage) {
        this.correctPercentage = correctPercentage;
    }

    public Long getQuestionStartedAtEpochMs() {
        return questionStartedAtEpochMs;
    }

    public void setQuestionStartedAtEpochMs(Long questionStartedAtEpochMs) {
        this.questionStartedAtEpochMs = questionStartedAtEpochMs;
    }

    public Long getQuestionEndsAtEpochMs() {
        return questionEndsAtEpochMs;
    }

    public void setQuestionEndsAtEpochMs(Long questionEndsAtEpochMs) {
        this.questionEndsAtEpochMs = questionEndsAtEpochMs;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(long remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public long getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(long participantCount) {
        this.participantCount = participantCount;
    }

    public long getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(long submissionCount) {
        this.submissionCount = submissionCount;
    }

    public long getTotalIncidents() {
        return totalIncidents;
    }

    public void setTotalIncidents(long totalIncidents) {
        this.totalIncidents = totalIncidents;
    }

    public boolean isRequireFullscreen() {
        return requireFullscreen;
    }

    public void setRequireFullscreen(boolean requireFullscreen) {
        this.requireFullscreen = requireFullscreen;
    }

    public boolean isMonitorTabSwitch() {
        return monitorTabSwitch;
    }

    public void setMonitorTabSwitch(boolean monitorTabSwitch) {
        this.monitorTabSwitch = monitorTabSwitch;
    }

    public boolean isRestrictCopyPaste() {
        return restrictCopyPaste;
    }

    public void setRestrictCopyPaste(boolean restrictCopyPaste) {
        this.restrictCopyPaste = restrictCopyPaste;
    }

    public Map<String, Long> getOptionCounts() {
        return optionCounts;
    }

    public void setOptionCounts(Map<String, Long> optionCounts) {
        this.optionCounts = optionCounts;
    }

    public List<LeaderboardEntryDto> getLeaderboard() {
        return leaderboard;
    }

    public void setLeaderboard(List<LeaderboardEntryDto> leaderboard) {
        this.leaderboard = leaderboard;
    }
}
