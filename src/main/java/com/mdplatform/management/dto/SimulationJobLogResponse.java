package com.mdplatform.management.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SimulationJobLogResponse {

    private Long jobId;
    private List<LogEntry> logEntries;
    private int totalLines;
    private boolean hasMore;

    @Data
    public static class LogEntry {
        private String logLevel;
        private String logContent;
        private LocalDateTime logTime;

        public LogEntry() {}

        public LogEntry(String logLevel, String logContent, LocalDateTime logTime) {
            this.logLevel = logLevel;
            this.logContent = logContent;
            this.logTime = logTime;
        }
    }
}
