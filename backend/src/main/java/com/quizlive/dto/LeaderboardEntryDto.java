package com.quizlive.dto;

public class LeaderboardEntryDto {
    private int rank;
    private String nickname;
    private int score;
    private String candidateId;

    public LeaderboardEntryDto() {}

    public LeaderboardEntryDto(int rank, String nickname, int score, String candidateId) {
        this.rank = rank;
        this.nickname = nickname;
        this.score = score;
        this.candidateId = candidateId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }
}
