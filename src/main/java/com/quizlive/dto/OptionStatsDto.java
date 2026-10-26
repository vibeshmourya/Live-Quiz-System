package com.quizlive.dto;

import java.util.HashMap;
import java.util.Map;

public class OptionStatsDto {
    private Map<String, Long> counts = new HashMap<>();
    private long totalSubmissions = 0;

    public OptionStatsDto() {
        counts.put("A", 0L);
        counts.put("B", 0L);
        counts.put("C", 0L);
        counts.put("D", 0L);
    }

    public OptionStatsDto(Map<String, Long> counts, long totalSubmissions) {
        this.counts = counts;
        this.totalSubmissions = totalSubmissions;
    }

    public Map<String, Long> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Long> counts) {
        this.counts = counts;
    }

    public long getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(long totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }
}
