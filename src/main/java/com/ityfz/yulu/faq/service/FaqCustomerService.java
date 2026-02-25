package com.ityfz.yulu.faq.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.faq.dto.FaqFeedbackDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;

import java.util.List;

public interface FaqCustomerService {

    // 获取常见问题分类列表
    List<FaqCategoryVO> listCategories(Long tenantId);

    // 获取常见问题列表
    IPage<FaqItemVO> listFaq(Long tenantId, FaqListQueryDTO query);

    // 获取热门常见问题列表
    List<FaqItemVO> hotFaq(Long tenantId, Integer limit);

    // 反馈
    void feedback(Long tenantId, Long userId, FaqFeedbackDTO dto);

    // 浏览计算增加
    void incrViewCount(Long tenantId, Long faqId);
}
