package com.tsuk1.itsmygo.common.product;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 商品归档与清理（长期下架自动处理）。默认关闭 + dry-run，确认后才真删。 */
@ConfigurationProperties(prefix = "itsmygo.product-cleanup")
public class ProductCleanupProperties {

    /** 是否启用定时清理任务 */
    private boolean enabled = false;

    /** true = 只扫描出报告，不归档/不删除 */
    private boolean dryRun = true;

    /** 下架超过多少天视为“长期未上架” */
    private int offShelfDays = 180;

    /** 归档后再过多少天才允许物理删除（给恢复窗口） */
    private int archiveGraceDays = 30;

    /** 扫描间隔（默认 24h） */
    private long scanIntervalMs = 86_400_000L;

    /** 扫描锁 TTL */
    private long scanLockTtlSeconds = 3600L;

    /** 每批最多处理的商品数 */
    private int batchLimit = 200;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public int getOffShelfDays() { return offShelfDays; }
    public void setOffShelfDays(int offShelfDays) { this.offShelfDays = offShelfDays; }
    public int getArchiveGraceDays() { return archiveGraceDays; }
    public void setArchiveGraceDays(int archiveGraceDays) { this.archiveGraceDays = archiveGraceDays; }
    public long getScanIntervalMs() { return scanIntervalMs; }
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }
    public long getScanLockTtlSeconds() { return scanLockTtlSeconds; }
    public void setScanLockTtlSeconds(long scanLockTtlSeconds) { this.scanLockTtlSeconds = scanLockTtlSeconds; }
    public int getBatchLimit() { return batchLimit; }
    public void setBatchLimit(int batchLimit) { this.batchLimit = batchLimit; }
}
