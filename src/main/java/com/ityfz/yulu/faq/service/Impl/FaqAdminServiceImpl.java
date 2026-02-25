package com.ityfz.yulu.faq.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.faq.dto.AdminFaqCategorySaveDTO;
import com.ityfz.yulu.faq.dto.AdminFaqItemSaveDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.entity.FaqCategory;
import com.ityfz.yulu.faq.entity.FaqItem;
import com.ityfz.yulu.faq.mapper.FaqCategoryMapper;
import com.ityfz.yulu.faq.mapper.FaqItemMapper;
import com.ityfz.yulu.faq.service.FaqAdminService;
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
@RequiredArgsConstructor
public class FaqAdminServiceImpl implements FaqAdminService {

    private final FaqCategoryMapper categoryMapper;
    private final FaqItemMapper itemMapper;

    @Override
    @Transactional
    public Long createCategory(Long tenantId, AdminFaqCategorySaveDTO dto) {
        FaqCategory c = new FaqCategory();
        c.setTenantId(tenantId);
        c.setName(dto.getName());
        c.setSort(dto.getSort());
        c.setStatus(dto.getStatus());
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        categoryMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional
    public void updateCategory(Long tenantId, Long id, AdminFaqCategorySaveDTO dto) {
        FaqCategory c = categoryMapper.selectById(id);
        if (c == null || !tenantId.equals(c.getTenantId())) return;
        c.setName(dto.getName());
        c.setSort(dto.getSort());
        c.setStatus(dto.getStatus());
        c.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(c);
    }

    @Override
    @Transactional
    public void deleteCategory(Long tenantId, Long id) {
        categoryMapper.delete(new LambdaQueryWrapper<FaqCategory>()
                .eq(FaqCategory::getTenantId, tenantId)
                .eq(FaqCategory::getId, id));
    }

    @Override
    public List<FaqCategoryVO> listCategories(Long tenantId) {
        List<FaqCategory> list = categoryMapper.selectList(new LambdaQueryWrapper<FaqCategory>()
                .eq(FaqCategory::getTenantId, tenantId)
                .orderByAsc(FaqCategory::getSort)
                .orderByAsc(FaqCategory::getId));
        return list.stream().map(c -> FaqCategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .sort(c.getSort())
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long createFaq(Long tenantId, AdminFaqItemSaveDTO dto) {
        FaqItem i = new FaqItem();
        i.setTenantId(tenantId);
        i.setCategoryId(dto.getCategoryId());
        i.setQuestion(dto.getQuestion());
        i.setAnswer(dto.getAnswer());
        i.setKeywords(dto.getKeywords());
        i.setSort(dto.getSort());
        i.setStatus(dto.getStatus());
        i.setViewCount(0L);
        i.setHelpfulCount(0L);
        i.setUnhelpfulCount(0L);
        i.setCreateTime(LocalDateTime.now());
        i.setUpdateTime(LocalDateTime.now());
        itemMapper.insert(i);
        return i.getId();
    }

    @Override
    @Transactional
    public void updateFaq(Long tenantId, Long id, AdminFaqItemSaveDTO dto) {
        FaqItem i = itemMapper.selectById(id);
        if (i == null || !tenantId.equals(i.getTenantId())) return;
        i.setCategoryId(dto.getCategoryId());
        i.setQuestion(dto.getQuestion());
        i.setAnswer(dto.getAnswer());
        i.setKeywords(dto.getKeywords());
        i.setSort(dto.getSort());
        i.setStatus(dto.getStatus());
        i.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(i);
    }

    @Override
    @Transactional
    public void updateFaqStatus(Long tenantId, Long id, Integer status) {
        FaqItem i = itemMapper.selectById(id);
        if (i == null || !tenantId.equals(i.getTenantId())) return;
        i.setStatus(status);
        i.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(i);
    }

    @Override
    @Transactional
    public void deleteFaq(Long tenantId, Long id) {
        itemMapper.delete(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getId, id));
    }


    @Override
    public IPage<FaqItemVO> pageFaq(Long tenantId, FaqListQueryDTO query) {
        LambdaQueryWrapper<FaqItem> qw = new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(query.getCategoryId() != null, FaqItem::getCategoryId, query.getCategoryId())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(FaqItem::getQuestion, query.getKeyword())
                        .or().like(FaqItem::getAnswer, query.getKeyword())
                        .or().like(FaqItem::getKeywords, query.getKeyword()))
                .orderByAsc(FaqItem::getSort)
                .orderByDesc(FaqItem::getId);

        IPage<FaqItem> page = itemMapper.selectPage(new Page<>(query.getPage(), query.getSize()), qw);
        Page<FaqItemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(i -> FaqItemVO.builder()
                .id(i.getId())
                .categoryId(i.getCategoryId())
                .question(i.getQuestion())
                .answer(i.getAnswer())
                .keywords(i.getKeywords())
                .viewCount(i.getViewCount())
                .helpfulCount(i.getHelpfulCount())
                .unhelpfulCount(i.getUnhelpfulCount())
                .build()).collect(Collectors.toList()));
        return voPage;
    }
}
