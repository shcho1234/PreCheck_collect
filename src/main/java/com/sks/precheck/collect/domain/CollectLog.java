package com.sks.precheck.collect.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TB_COLLECT_LOG 테이블과 1:1로 매핑되는 수집 로그 DTO.
 */
public class CollectLog {

    private Long collectLogId;
    private String serverId;
    private String serverIp;
    private String sourceFilePath;
    private String logType;
    private String logId;
    private LocalDateTime logTimestamp;
    private String logContent;
    private BigDecimal logValue;
    private String rawLog;
    private String collectDate;
    private LocalDateTime collectDatetime;
    private String scheduleType;
    private Long lineNumber;
    private LocalDateTime createdAt;  

    public CollectLog() {
    }

    public Long getCollectLogId() {
        return collectLogId;
    }

    public void setCollectLogId(Long collectLogId) {
        this.collectLogId = collectLogId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public void setSourceFilePath(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public LocalDateTime getLogTimestamp() {
        return logTimestamp;
    }

    public void setLogTimestamp(LocalDateTime logTimestamp) {
        this.logTimestamp = logTimestamp;
    }

    public String getLogContent() {
        return logContent;
    }

    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    public BigDecimal getLogValue() {
        return logValue;
    }

    public void setLogValue(BigDecimal logValue) {
        this.logValue = logValue;
    }

    public String getRawLog() {
        return rawLog;
    }

    public void setRawLog(String rawLog) {
        this.rawLog = rawLog;
    }

    public String getCollectDate() {
        return collectDate;
    }

    public void setCollectDate(String collectDate) {
        this.collectDate = collectDate;
    }

    public LocalDateTime getCollectDatetime() {
        return collectDatetime;
    }

    public void setCollectDatetime(LocalDateTime collectDatetime) {
        this.collectDatetime = collectDatetime;
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public Long getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Long lineNumber) {
        this.lineNumber = lineNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

