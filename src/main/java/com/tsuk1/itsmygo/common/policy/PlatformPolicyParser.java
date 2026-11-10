package com.tsuk1.itsmygo.common.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台政策 Markdown 解析：H1 作标题，## / ### 作章节。
 *
 * <p>章节是 MySQL 与 Milvus 共用的最小引用单位，因此解析规则放在这里，
 * 种子导入与后台上传必须走同一份实现。</p>
 */
public final class PlatformPolicyParser {

    public static final Set<String> POLICY_KEYS = Set.of("terms", "privacy", "refund", "merchant");

    private static final Pattern HEADING = Pattern.compile("^(#{2,3})\\s+(.+?)\\s*$");

    /** 文件名与展示用元信息；标题优先取 Markdown 第一行 # 标题。 */
    private static final Map<String, PolicyMeta> META = new LinkedHashMap<>();

    static {
        META.put("terms", new PolicyMeta("用户协议", "账户与服务"));
        META.put("privacy", new PolicyMeta("隐私政策", "隐私与数据"));
        META.put("refund", new PolicyMeta("退换货与售后政策", "售后与退款"));
        META.put("merchant", new PolicyMeta("商家入驻与经营规范", "商家经营"));
    }

    private PlatformPolicyParser() {
    }

    public static PolicyMeta meta(String policyKey) {
        return META.get(policyKey);
    }

    /** 解析标题与章节；没有二级标题时退化为“全文”一章。 */
    public static ParsedPolicy parse(String markdown, String fallbackTitle) {
        String title = fallbackTitle;
        List<ParsedSection> sections = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String heading = null;
        String[] lines = markdown.split("\\R", -1);
        for (String line : lines) {
            if (line.startsWith("# ")) {
                if (title.equals(fallbackTitle)) {
                    title = line.substring(2).trim();
                }
                continue;
            }
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                flush(sections, heading, body);
                heading = matcher.group(2).trim();
                continue;
            }
            body.append(line).append('\n');
        }
        flush(sections, heading, body);
        if (sections.isEmpty()) {
            sections.add(new ParsedSection(1, "全文", markdown.trim()));
        }
        return new ParsedPolicy(title, sections);
    }

    private static void flush(List<ParsedSection> sections, String heading, StringBuilder body) {
        String content = body.toString().trim();
        body.setLength(0);
        if (heading != null) {
            sections.add(new ParsedSection(sections.size() + 1, heading, content));
        } else if (!content.isEmpty()) {
            sections.add(new ParsedSection(1, "总则", content));
        }
    }

    public record PolicyMeta(String defaultTitle, String category) {
    }

    public record ParsedPolicy(String title, List<ParsedSection> sections) {
    }

    public record ParsedSection(int sectionNo, String heading, String content) {
    }
}
