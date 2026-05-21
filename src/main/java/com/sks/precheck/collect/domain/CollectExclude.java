package com.sks.precheck.collect.domain;

import java.time.LocalDateTime;

/**
 * TB_COLLECT_EXCLUDE 테이블과 1:1로 매핑되는 영구 제외 대상 DTO.
 */
public class CollectExclude {

    private Long collectExcludeId;
    private String serverId;
    private String serverIp;
    private String sourceFilePath;
    private String excludeReason;
    private Long excludeFileSize;
    private String excludeDetail;
    private String restoreYn;
    private LocalDateTime restoreAt;
    private String restoreMemo;
    private String excludeDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CollectExclude() {
    }

    public Long getCollectExcludeId() {
        return collectExcludeId;
    }

    public void setCollectExcludeId(Long collectExcludeId) {
        this.collectExcludeId = collectExcludeId;
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

    public String getExcludeReason() {
        return excludeReason;
    }

    public void setExcludeReason(String excludeReason) {
        this.excludeReason = excludeReason;
    }

    public Long getExcludeFileSize() {
        return excludeFileSize;
    }

    public void setExcludeFileSize(Long excludeFileSize) {
        this.excludeFileSize = excludeFileSize;
    }

    public String getExcludeDetail() {
        return excludeDetail;
    }

    public void setExcludeDetail(String excludeDetail) {
        this.excludeDetail = excludeDetail;
    }

    public String getRestoreYn() {
        return restoreYn;
    }

    public void setRestoreYn(String restoreYn) {
        this.restoreYn = restoreYn;
    }

    public LocalDateTime getRestoreAt() {
        return restoreAt;
    }

    public void setRestoreAt(LocalDateTime restoreAt) {
        this.restoreAt = restoreAt;
    }

    public String getRestoreMemo() {
        return restoreMemo;
    }

    public void setRestoreMemo(String restoreMemo) {
        this.restoreMemo = restoreMemo;
    }

    public String getExcludeDate() {
        return excludeDate;
    }

    public void setExcludeDate(String excludeDate) {
        this.excludeDate = excludeDate;
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
}

