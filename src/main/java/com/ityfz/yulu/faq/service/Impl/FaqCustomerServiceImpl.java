package com.ityfz.yulu.faq.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.faq.dto.FaqFeedbackDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.entity.FaqCategory;
import com.ityfz.yulu.faq.entity.FaqFeedback;
import com.ityfz.yulu.faq.entity.FaqItem;
import com.ityfz.yulu.faq.mapper.FaqCategoryMapper;
import com.ityfz.yulu.faq.mapper.FaqFeedbackMapper;
import com.ityfz.yulu.faq.mapper.FaqItemMapper;
import com.ityfz.yulu.faq.service.FaqCustomerService;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class FaqCustomerServiceImpl implements FaqCustomerService {


    private final FaqCategoryMapper categoryMapper;
    private final FaqItemMapper itemMapper;
    private final FaqFeedbackMapper feedbackMapper;

    public FaqCustomerServiceImpl(FaqCategoryMapper categoryMapper, FaqItemMapper itemMapper, FaqFeedbackMapper feedbackMapper) {
        this.categoryMapper = categoryMapper;
        this.itemMapper = itemMapper;
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public List<FaqCategoryVO> listCategories(Long tenantId) {
        List<FaqCategory> list = categoryMapper.selectList(new LambdaQueryWrapper<FaqCategory>()
                .eq(FaqCategory::getTenantId, tenantId)
                .eq(FaqCategory::getStatus, 1)
                .orderByAsc(FaqCategory::getSort)
                .orderByAsc(FaqCategory::getId));

        return list.stream().map(c -> FaqCategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .sort(c.getSort())
                .build()).collect(Collectors.toList());
    }

    @Override
    public IPage<FaqItemVO> listFaq(Long tenantId, FaqListQueryDTO query) {

        LambdaQueryWrapper<FaqItem> qw = new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getStatus, 1)
                .eq(query.getCategoryId() != null, FaqItem::getCategoryId, query.getCategoryId())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(FaqItem::getQuestion, query.getKeyword())
                        .or().like(FaqItem::getAnswer, query.getKeyword())
                        .or().like(FaqItem::getKeywords, query.getKeyword()))
                .orderByAsc(FaqItem::getSort)
                .orderByDesc(FaqItem::getId);
        // TODO: 可以优化为先查询ID列表，再批量查询详情，避免大文本字段的性能问题

        IPage<FaqItem> page = itemMapper.selectPage(new Page<>(query.getPage(), query.getSize()), qw);

        Page<FaqItemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<FaqItemVO> hotFaq(Long tenantId, Integer limit) {
        int size = (limit == null || limit <= 0 || limit > 20) ? 10 : limit;
        List<FaqItem> list = itemMapper.selectList(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getStatus, 1)
                .orderByDesc(FaqItem::getHelpfulCount)
                .orderByDesc(FaqItem::getViewCount)
                .last("limit " + size));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void feedback(Long tenantId, Long userId, FaqFeedbackDTO dto) {

        // 1、检查反馈是否存在
        FaqItem item = itemMapper.selectOne(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getId, dto.getFaqId())
                .last("limit 1"));
        if (item == null) {
            return;
        }

        // 2、查询用户是否已反馈过
        FaqFeedback old = feedbackMapper.selectOne(new LambdaQueryWrapper<FaqFeedback>()
                .eq(FaqFeedback::getTenantId, tenantId)
                .eq(FaqFeedback::getFaqId, dto.getFaqId())
                .eq(FaqFeedback::getUserId, userId)
                .last("limit 1"));

        LocalDateTime now = LocalDateTime.now();
        // 当前用户没有反馈过
        if (old == null) {
            FaqFeedback fb = new FaqFeedback();
            fb.setTenantId(tenantId);
            fb.setFaqId(dto.getFaqId());
            fb.setUserId(userId);
            fb.setFeedbackType(dto.getFeedbackType());
            fb.setCreateTime(now);
            fb.setUpdateTime(now);
            feedbackMapper.insert(fb);

            // 有帮助/没有帮助的话
            if (dto.getFeedbackType() == 1) {
                item.setHelpfulCount(item.getHelpfulCount() + 1);
            } else {
                item.setUnhelpfulCount(item.getUnhelpfulCount() + 1);
            }
        } // 当前用户已反馈过，但这次反馈类型不同了
        else if (!old.getFeedbackType().equals(dto.getFeedbackType())) {
            if (old.getFeedbackType() == 1) {
                item.setHelpfulCount(Math.max(0, item.getHelpfulCount() - 1));
                item.setUnhelpfulCount(item.getUnhelpfulCount() + 1);
            } else {
                item.setUnhelpfulCount(Math.max(0, item.getUnhelpfulCount() - 1));
                item.setHelpfulCount(item.getHelpfulCount() + 1);
            }
            old.setFeedbackType(dto.getFeedbackType());
            old.setUpdateTime(now);
            feedbackMapper.updateById(old);
        }

        item.setUpdateTime(now);
        itemMapper.updateById(item);
    }

    @Override
    @Transactional
    public void incrViewCount(Long tenantId, Long faqId) {
        itemMapper.update(
                null,
                new LambdaUpdateWrapper<FaqItem>()
                        .eq(FaqItem::getTenantId, tenantId)
                        .eq(FaqItem::getId, faqId)
                        // 原子自增
                        .setSql("view_count = IFNULL(view_count, 0) + 1")
        );
    }

    private FaqItemVO toVO(FaqItem i) {
        return FaqItemVO.builder()
                .id(i.getId())
                .categoryId(i.getCategoryId())
                .question(i.getQuestion())
                .answer(i.getAnswer())
                .keywords(i.getKeywords())
                .viewCount(i.getViewCount())
                .helpfulCount(i.getHelpfulCount())
                .unhelpfulCount(i.getUnhelpfulCount())
                .build();
    }
}
