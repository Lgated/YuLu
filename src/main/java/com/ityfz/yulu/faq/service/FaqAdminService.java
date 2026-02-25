package com.ityfz.yulu.faq.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.faq.dto.AdminFaqCategorySaveDTO;
import com.ityfz.yulu.faq.dto.AdminFaqItemSaveDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;

import java.util.List;

public interface FaqAdminService {

    // 创建常见问题分类
    Long createCategory(Long tenantId, AdminFaqCategorySaveDTO dto);

    // 更新常见问题分类
    void updateCategory(Long tenantId, Long id, AdminFaqCategorySaveDTO dto);

    // 删除常见问题分类
    void deleteCategory(Long tenantId, Long id);

    // 获取常见问题分类列表
    List<FaqCategoryVO> listCategories(Long tenantId);

    // 创建常见问题
    Long createFaq(Long tenantId, AdminFaqItemSaveDTO dto);

    // 更新常见问题
    void updateFaq(Long tenantId, Long id, AdminFaqItemSaveDTO dto);

    // 更新常见问题状态
    void updateFaqStatus(Long tenantId, Long id, Integer status);

    // 删除常见问题
    void deleteFaq(Long tenantId, Long id);

    // 获取常见问题列表
    IPage<FaqItemVO> pageFaq(Long tenantId, FaqListQueryDTO query);
}
