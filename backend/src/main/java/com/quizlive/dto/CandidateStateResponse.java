package com.quizlive.dto;

import com.quizlive.model.SessionStatus;
import java.util.List;

public class CandidateStateResponse {
    private String pin;
    private String quizTitle;
    private SessionStatus status;
    private int currentQuestionIndex;
    private int totalQuestions;
    private String nickname;
    private CandidateQuestionDto currentQuestion;
    private Long questionStartedAtEpochMs;
    private Long questionEndsAtEpochMs;
    private long remainingSeconds;
    private boolean hasSubmitted;
    private boolean allowReselection;
    private String mySelectedLabel;
    private String revealedCorrectLabel; // Only non-null if revealed or finished
    private Boolean wasCorrect;           // Only non-null if revealed or finished
    private int myScore;
    private Integer myRank;              // When finished
    private Long totalCorrect;           // When finished
    private Long totalIncorrect;         // When finished
    private List<LeaderboardEntryDto> leaderboard; // When finished

    // Security & Examination Mode attributes
    private boolean requireFullscreen;
    private boolean monitorTabSwitch;
    private boolean restrictCopyPaste;
    private int warningThreshold = 3;
    private long myIncidentCount = 0;
    private boolean resultsPublished = true;
    private boolean showCorrectAnswers = true;
    private boolean rulesAcknowledged = false;

    public CandidateStateResponse() {}

    public boolean isRulesAcknowledged() {
        return rulesAcknowledged;
    }

    public void setRulesAcknowledged(boolean rulesAcknowledged) {
        this.rulesAcknowledged = rulesAcknowledged;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public CandidateQuestionDto getCurrentQuestion() {
        return currentQuestion;
    }

    public void setCurrentQuestion(CandidateQuestionDto currentQuestion) {
        this.currentQuestion = currentQuestion;
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

    public boolean isHasSubmitted() {
        return hasSubmitted;
    }

    public void setHasSubmitted(boolean hasSubmitted) {
        this.hasSubmitted = hasSubmitted;
    }

    public boolean isAllowReselection() {
        return allowReselection;
    }

    public void setAllowReselection(boolean allowReselection) {
        this.allowReselection = allowReselection;
    }

    public String getMySelectedLabel() {
        return mySelectedLabel;
    }

    public void setMySelectedLabel(String mySelectedLabel) {
        this.mySelectedLabel = mySelectedLabel;
    }

    public String getRevealedCorrectLabel() {
        return revealedCorrectLabel;
    }

    public void setRevealedCorrectLabel(String revealedCorrectLabel) {
        this.revealedCorrectLabel = revealedCorrectLabel;
    }

    public Boolean getWasCorrect() {
        return wasCorrect;
    }

    public void setWasCorrect(Boolean wasCorrect) {
        this.wasCorrect = wasCorrect;
    }

    public int getMyScore() {
        return myScore;
    }

    public void setMyScore(int myScore) {
        this.myScore = myScore;
    }

    public Integer getMyRank() {
        return myRank;
    }

    public void setMyRank(Integer myRank) {
        this.myRank = myRank;
    }

    public Long getTotalCorrect() {
        return totalCorrect;
    }

    public void setTotalCorrect(Long totalCorrect) {
        this.totalCorrect = totalCorrect;
    }

    public Long getTotalIncorrect() {
        return totalIncorrect;
    }

    public void setTotalIncorrect(Long totalIncorrect) {
        this.totalIncorrect = totalIncorrect;
    }

    public List<LeaderboardEntryDto> getLeaderboard() {
        return leaderboard;
    }

    public void setLeaderboard(List<LeaderboardEntryDto> leaderboard) {
        this.leaderboard = leaderboard;
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

    public int getWarningThreshold() {
        return warningThreshold;
    }

    public void setWarningThreshold(int warningThreshold) {
        this.warningThreshold = warningThreshold;
    }

    public long getMyIncidentCount() {
        return myIncidentCount;
    }

    public void setMyIncidentCount(long myIncidentCount) {
        this.myIncidentCount = myIncidentCount;
    }

    public boolean isResultsPublished() {
        return resultsPublished;
    }

    public void setResultsPublished(boolean resultsPublished) {
        this.resultsPublished = resultsPublished;
    }

    public boolean isShowCorrectAnswers() {
        return showCorrectAnswers;
    }

    public void setShowCorrectAnswers(boolean showCorrectAnswers) {
        this.showCorrectAnswers = showCorrectAnswers;
    }
}
