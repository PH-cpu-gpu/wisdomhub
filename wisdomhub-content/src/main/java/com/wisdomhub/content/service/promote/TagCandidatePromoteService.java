package com.wisdomhub.content.service.promote;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.content.config.TagPromoteProperties;
import com.wisdomhub.content.constants.TagProposalConstants;
import com.wisdomhub.content.constants.TagProposalIssueCodes;
import com.wisdomhub.content.domain.dto.TagProposalSubmitDTO;
import com.wisdomhub.content.domain.entity.TagProposal;
import com.wisdomhub.content.domain.entity.UnknownEntity;
import com.wisdomhub.content.domain.vo.TagProposalIssueVO;
import com.wisdomhub.content.domain.vo.TagProposalSubmitResult;
import com.wisdomhub.content.domain.vo.TagProposalVO;
import com.wisdomhub.content.mapper.TagProposalMapper;
import com.wisdomhub.content.mapper.UnknownEntityMapper;
import com.wisdomhub.content.service.TagProposalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 只按文章证据把未收录实体升级成「新增标签」提案。
 * 查询次数不参与判断；已拒绝的不再提；仍在待审的不重复提；提案落库后回写 promoted_proposal_id，
 * 任务重复跑同一批候选也只会产出一条提案（待审唯一键兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagCandidatePromoteService {

    private static final int MAX_BATCH_SIZE = 200;

    private final UnknownEntityMapper unknownEntityMapper;
    private final TagProposalMapper tagProposalMapper;
    private final TagProposalService tagProposalService;
    private final TagPromoteProperties tagPromoteProperties;
    private final ObjectMapper objectMapper;

    /** 一轮只处理一批候选，剩下的留给下一次调度 */
    public TagCandidatePromoteResult promote() {
        int minArticles = Math.max(tagPromoteProperties.getMinArticles(), 1);
        int limit = Math.min(Math.max(tagPromoteProperties.getBatchSize(), 1), MAX_BATCH_SIZE);
        List<UnknownEntity> candidates = unknownEntityMapper.selectPromotable(
                minArticles, TagProposalConstants.STATUS_EXPIRED, limit);

        TagCandidatePromoteResult result = new TagCandidatePromoteResult();
        result.setScanned(candidates.size());
        for (UnknownEntity candidate : candidates) {
            try {
                promoteOne(candidate, result);
            } catch (Exception e) {
                // 单条失败不拖垮整批，下一轮还会重试
                result.failed();
                log.error("未收录候选升级失败: name={} nameNorm={}",
                        candidate.getName(), candidate.getNameNorm(), e);
            }
        }
        return result;
    }

    private void promoteOne(UnknownEntity candidate, TagCandidatePromoteResult result) {
        TagProposal rejected = findRejected(candidate.getNameNorm());
        if (rejected != null) {
            // 拒绝过的词不再打扰管理员；写上标记后下一轮连扫都扫不到
            markPromoted(candidate.getId(), rejected.getId());
            result.rejectedSkipped();
            log.info("候选曾被拒绝，跳过: name={} proposalId={}", candidate.getName(), rejected.getId());
            return;
        }

        TagProposalSubmitResult submit;
        try {
            submit = tagProposalService.submit(buildDto(candidate));
        } catch (BusinessException e) {
            // 提交瞬间被别人抢先落了同目标待审提案（pending_key 唯一键）
            if (e.getErrorCode() == CommonErrorCode.CONFLICT) {
                result.pendingSkipped();
                log.info("候选已有待审提案，跳过: name={}", candidate.getName());
                return;
            }
            throw e;
        }

        if (!submit.isValid()) {
            classifyInvalid(candidate, submit.getIssues(), result);
            return;
        }

        TagProposalVO proposal = submit.getProposal();
        if (proposal == null || proposal.getId() == null) {
            result.invalidSkipped();
            log.warn("候选提交返回空提案: name={}", candidate.getName());
            return;
        }
        markPromoted(candidate.getId(), proposal.getId());
        result.promoted();
        log.info("未收录候选已升级成提案: name={} extractCount={} proposalId={}",
                candidate.getName(), candidate.getExtractCount(), proposal.getId());
    }

    private void classifyInvalid(UnknownEntity candidate, List<TagProposalIssueVO> issues,
                                 TagCandidatePromoteResult result) {
        String code = issues == null || issues.isEmpty() ? null : issues.get(0).getCode();
        if (TagProposalIssueCodes.PENDING_EXISTS.equals(code)) {
            result.pendingSkipped();
            return;
        }
        if (TagProposalIssueCodes.TAG_EXISTS.equals(code)) {
            // 标签或别名刚被建出来：任务把候选写成已升级没有意义，下一轮靠 tag 关联过滤掉
            result.existsSkipped();
            log.info("候选已是标签/别名，跳过: name={}", candidate.getName());
            return;
        }
        result.invalidSkipped();
        log.warn("候选未通过提交校验: name={} issues={}", candidate.getName(), issues);
    }

    private TagProposalSubmitDTO buildDto(UnknownEntity candidate) {
        TagProposalSubmitDTO dto = new TagProposalSubmitDTO();
        dto.setProposalType(TagProposalConstants.TYPE_TAG);
        dto.setName(candidate.getName());
        dto.setProposerType(TagProposalConstants.PROPOSER_SYSTEM);
        dto.setEvidence(evidence(candidate));
        return dto;
    }

    /** 把升级依据一并落进 evidence，审批时有据可查 */
    private JsonNode evidence(UnknownEntity candidate) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", "unknown_entity");
        node.put("extractCount", candidate.getExtractCount());
        node.put("queryCount", candidate.getQueryCount());
        node.put("sampleArticleId", candidate.getSampleArticleId());
        node.put("firstSeen", candidate.getFirstSeen() == null ? null : candidate.getFirstSeen().toString());
        node.put("lastSeen", candidate.getLastSeen() == null ? null : candidate.getLastSeen().toString());
        return node;
    }

    private TagProposal findRejected(String targetKey) {
        List<TagProposal> rejected = tagProposalMapper.selectList(new LambdaQueryWrapper<TagProposal>()
                .eq(TagProposal::getTargetKey, targetKey)
                .eq(TagProposal::getStatus, TagProposalConstants.STATUS_REJECTED)
                .orderByDesc(TagProposal::getId)
                .last("LIMIT 1"));
        return rejected.isEmpty() ? null : rejected.get(0);
    }

    private void markPromoted(Long id, Long proposalId) {
        if (proposalId == null) {
            return;
        }
        unknownEntityMapper.update(null, new LambdaUpdateWrapper<UnknownEntity>()
                .set(UnknownEntity::getPromotedProposalId, proposalId)
                .set(UnknownEntity::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(UnknownEntity::getId, id));
    }
}
