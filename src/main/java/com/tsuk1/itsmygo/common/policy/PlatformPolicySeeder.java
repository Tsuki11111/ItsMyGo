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
import java.util.List;

/**
 * 首次启动把 4 份正式政策种入 MySQL。
 *
 * <p>政策原文以 MySQL 为准：已有政策时不再覆盖，避免以后管理端编辑被重启冲掉。
 * 章节按 Markdown 的 ## / ### 切分；### 单独成章节，便于向量召回与精确引用。</p>
 */
@Component
public class PlatformPolicySeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PlatformPolicySeeder.class);

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
            PlatformPolicyParser.PolicyMeta meta = PlatformPolicyParser.meta(key);
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

    private void seedOne(String key, PlatformPolicyParser.PolicyMeta meta, String markdown) {
        PlatformPolicyParser.ParsedPolicy parsed =
                PlatformPolicyParser.parse(markdown, meta.defaultTitle());
        PlatformPolicy policy = new PlatformPolicy();
        policy.setPolicyKey(key);
        policy.setTitle(parsed.title());
        policy.setCategory(meta.category());
        policy.setVersion("1.0");
        policy.setStatus("ACTIVE");
        policy.setContentMd(markdown);
        policy.setEffectiveAt(LocalDateTime.of(2026, 11, 3, 0, 0));
        policyMapper.insert(policy);
        for (PlatformPolicyParser.ParsedSection section : parsed.sections()) {
            PlatformPolicySection row = new PlatformPolicySection();
            row.setPolicyId(policy.getId());
            row.setSectionNo(section.sectionNo());
            row.setHeading(section.heading());
            row.setContent(section.content());
            sectionMapper.insert(row);
        }
    }
}
