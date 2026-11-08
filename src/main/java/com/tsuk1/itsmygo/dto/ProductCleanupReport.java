package com.tsuk1.itsmygo.dto;

/** 商品清理一次执行的统计（dry-run 也返回候选数）。 */
public record ProductCleanupReport(
        boolean dryRun,
        int archiveCandidates,
        int archived,
        int purgeCandidates,
        int purged,
        int skippedReferenced) {
}
