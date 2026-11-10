package com.tsuk1.itsmygo.dto.policy;

/** 政策章节：heading 是章节标题，content 是不含标题的正文。 */
public record PlatformPolicySectionView(
        int sectionNo,
        String heading,
        String content) {
}
