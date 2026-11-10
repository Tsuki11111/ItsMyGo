package com.tsuk1.itsmygo.common.policy;

import com.tsuk1.itsmygo.entity.PlatformPolicy;
import com.tsuk1.itsmygo.entity.PlatformPolicySection;
import com.tsuk1.itsmygo.mapper.PlatformPolicyMapper;
import com.tsuk1.itsmygo.mapper.PlatformPolicySectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首次启动把 4 份正式政策种入 MySQL。
 *
 * <p>政策原文以 MySQL 为准：已有政策时不再覆盖，避免以后管理端编辑被重启冲掉。
 * 章节按 Markdown 的 ## / ### 切分；### 单独成章节，便于向量召回与精确引用。</p>
 */
@Component
public class PlatformPolicySeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PlatformPolicySeeder.class);

    private static final Pattern HEADING = Pattern.compile("^(#{2,3})\\s+(.+?)\\s*$");

    /** 文件名与展示用元信息；标题优先取 Markdown 第一行 # 标题。 */
    private static final Map<String, PolicyMeta> META = new LinkedHashMap<>();

    static {
        META.put("terms", new PolicyMeta("用户协议", "账户与服务"));
        META.put("privacy", new PolicyMeta("隐私政策", "隐私与数据"));
        META.put("refund", new PolicyMeta("退换货与售后政策", "售后与退款"));
        META.put("merchant", new PolicyMeta("商家入驻与经营规范", "商家经营"));
    }

    private final PlatformPolicyMapper policyMapper;
    private final PlatformPolicySectionMapper sectionMapper;

    @Value("${itsmygo.policy.seed-enabled:true}")
    private boolean seedEnabled;

    public PlatformPolicySeeder(PlatformPolicyMapper policyMapper,
                                PlatformPolicySectionMapper sectionMapper) {
        this.policyMapper = policyMapper;
        this.sectionMapper = sectionMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        if (policyMapper.selectCount(null) > 0) {
            return;
        }
        List<Resource> resources;
        try {
            resources = new ArrayList<>(List.of(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:policies/*.md")));
        } catch (Exception e) {
            logger.warn("读取政策种子失败，跳过初始化：{}", e.getMessage());
            return;
        }
        resources.sort(Comparator.comparing(resource -> resource.getFilename() == null
                ? "" : resource.getFilename()));
        int seeded = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".md")) {
                continue;
            }
            String key = filename.substring(0, filename.length() - 3);
            PolicyMeta meta = META.get(key);
            if (meta == null) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                String markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                seedOne(key, meta, markdown);
                seeded++;
            } catch (Exception e) {
                logger.error("政策种子导入失败：{}", filename, e);
            }
        }
        logger.info("平台政策初始化完成，共 {} 份", seeded);
    }

    private void seedOne(String key, PolicyMeta meta, String markdown) {
        ParsedPolicy parsed = parse(markdown, meta.defaultTitle());
        PlatformPolicy policy = new PlatformPolicy();
        policy.setPolicyKey(key);
        policy.setTitle(parsed.title());
        policy.setCategory(meta.category());
        policy.setVersion("1.0");
        policy.setStatus("ACTIVE");
        policy.setContentMd(markdown);
        policy.setEffectiveAt(LocalDateTime.of(2026, 11, 3, 0, 0));
        policyMapper.insert(policy);
        for (ParsedSection section : parsed.sections()) {
            PlatformPolicySection row = new PlatformPolicySection();
            row.setPolicyId(policy.getId());
            row.setSectionNo(section.sectionNo());
            row.setHeading(section.heading());
            row.setContent(section.content());
            sectionMapper.insert(row);
        }
    }

    /** 解析标题与章节；没有二级标题时退化为“全文”一章。 */
    ParsedPolicy parse(String markdown, String fallbackTitle) {
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

    private void flush(List<ParsedSection> sections, String heading, StringBuilder body) {
        String content = body.toString().trim();
        body.setLength(0);
        if (heading != null) {
            sections.add(new ParsedSection(sections.size() + 1, heading, content));
        } else if (!content.isEmpty()) {
            sections.add(new ParsedSection(1, "总则", content));
        }
    }

    record PolicyMeta(String defaultTitle, String category) {
    }

    record ParsedPolicy(String title, List<ParsedSection> sections) {
    }

    record ParsedSection(int sectionNo, String heading, String content) {
    }
}
