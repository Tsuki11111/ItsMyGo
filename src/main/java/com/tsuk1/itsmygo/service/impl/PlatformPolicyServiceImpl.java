package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.policy.PlatformPolicyParser;
import com.tsuk1.itsmygo.dto.policy.PlatformPolicyDetailView;
import com.tsuk1.itsmygo.dto.policy.PlatformPolicySectionView;
import com.tsuk1.itsmygo.dto.policy.PlatformPolicySummaryView;
import com.tsuk1.itsmygo.entity.PlatformPolicy;
import com.tsuk1.itsmygo.entity.PlatformPolicySection;
import com.tsuk1.itsmygo.mapper.PlatformPolicyMapper;
import com.tsuk1.itsmygo.mapper.PlatformPolicySectionMapper;
import com.tsuk1.itsmygo.service.PlatformPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlatformPolicyServiceImpl implements PlatformPolicyService {

    private final PlatformPolicyMapper policyMapper;
    private final PlatformPolicySectionMapper sectionMapper;

    public PlatformPolicyServiceImpl(PlatformPolicyMapper policyMapper,
                                     PlatformPolicySectionMapper sectionMapper) {
        this.policyMapper = policyMapper;
        this.sectionMapper = sectionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformPolicySummaryView> list() {
        return policyMapper.selectList(Wrappers.<PlatformPolicy>lambdaQuery()
                        .eq(PlatformPolicy::getStatus, "ACTIVE")
                        .orderByAsc(PlatformPolicy::getId))
                .stream()
                .map(policy -> new PlatformPolicySummaryView(
                        policy.getPolicyKey(),
                        policy.getTitle(),
                        policy.getCategory(),
                        policy.getVersion(),
                        sectionMapper.selectCount(Wrappers.<PlatformPolicySection>lambdaQuery()
                                .eq(PlatformPolicySection::getPolicyId, policy.getId())),
                        policy.getUpdatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformPolicyDetailView get(String policyKey) {
        PlatformPolicy policy = policyMapper.selectOne(Wrappers.<PlatformPolicy>lambdaQuery()
                .eq(PlatformPolicy::getPolicyKey, policyKey)
                .eq(PlatformPolicy::getStatus, "ACTIVE"));
        if (policy == null) {
            throw new BizException(ResultCode.NOT_FOUND, "政策不存在：" + policyKey);
        }
        List<PlatformPolicySectionView> sections = sectionMapper.selectList(
                        Wrappers.<PlatformPolicySection>lambdaQuery()
                                .eq(PlatformPolicySection::getPolicyId, policy.getId())
                                .orderByAsc(PlatformPolicySection::getSectionNo))
                .stream()
                .map(section -> new PlatformPolicySectionView(
                        section.getSectionNo(), section.getHeading(), section.getContent()))
                .toList();
        return new PlatformPolicyDetailView(policy.getPolicyKey(), policy.getTitle(),
                policy.getCategory(), policy.getVersion(), policy.getEffectiveAt(),
                policy.getUpdatedAt(), sections);
    }

    @Override
    @Transactional
    public void importMarkdown(String policyKey, String markdown) {
        if (policyKey == null || !PlatformPolicyParser.POLICY_KEYS.contains(policyKey)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的政策标识：" + policyKey);
        }
        if (markdown == null || markdown.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "政策文件内容为空");
        }
        PlatformPolicyParser.PolicyMeta meta = PlatformPolicyParser.meta(policyKey);
        PlatformPolicyParser.ParsedPolicy parsed =
                PlatformPolicyParser.parse(markdown, meta.defaultTitle());

        PlatformPolicy policy = policyMapper.selectOne(Wrappers.<PlatformPolicy>lambdaQuery()
                .eq(PlatformPolicy::getPolicyKey, policyKey));
        if (policy == null) {
            policy = new PlatformPolicy();
            policy.setPolicyKey(policyKey);
            policy.setVersion("1.0");
            policy.setStatus("ACTIVE");
            policy.setEffectiveAt(LocalDateTime.now());
        }
        policy.setTitle(parsed.title());
        policy.setCategory(meta.category());
        policy.setContentMd(markdown);
        policy.setUpdatedAt(LocalDateTime.now());
        if (policy.getId() == null) {
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
        }

        sectionMapper.delete(Wrappers.<PlatformPolicySection>lambdaQuery()
                .eq(PlatformPolicySection::getPolicyId, policy.getId()));
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
