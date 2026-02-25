package com.ityfz.yulu.faq.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.faq.dto.FaqFeedbackDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.service.FaqCustomerService;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/customer/faq")
@RequiredArgsConstructor
@RequireRole("USER")
@Validated
@Tag(name = "C端-FAQ（Customer/FAQ）", description = "用户常见问题：分类、搜索、热门、反馈")
public class CustomerFAQController {

    private final FaqCustomerService faqCustomerService;

    @GetMapping("/categories")
    @Operation(summary = "获取FAQ分类", description = "返回当前租户下启用状态的FAQ分类，按sort升序")
    public ApiResponse<List<FaqCategoryVO>> categories() {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqCustomerService.listCategories(tenantId));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询FAQ列表", description = "支持按categoryId和keyword检索FAQ")
    public ApiResponse<IPage<FaqItemVO>> list(FaqListQueryDTO query) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqCustomerService.listFaq(tenantId, query));
    }

    @GetMapping("/hot")
    @Operation(summary = "获取热门FAQ", description = "按帮助数和浏览数综合排序，返回TopN")
    public ApiResponse<List<FaqItemVO>> hot(@RequestParam(required = false) Integer limit) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqCustomerService.hotFaq(tenantId, limit));
    }

    @PostMapping("/feedback")
    @Operation(summary = "提交FAQ反馈", description = "对FAQ提交有帮助/无帮助反馈，同一用户同一FAQ可更新反馈类型")
    public ApiResponse<Void> feedback(@Valid @RequestBody FaqFeedbackDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        faqCustomerService.feedback(tenantId, userId, dto);
        return ApiResponse.success("反馈成功");
    }

    @PostMapping("/view/{faqId}")
    @Operation(summary = "记录FAQ浏览", description = "FAQ被展开查看时，浏览数+1")
    public ApiResponse<Void> view(
            @Parameter(description = "FAQ ID") @PathVariable Long faqId
    ) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqCustomerService.incrViewCount(tenantId, faqId);
        return ApiResponse.success("记录成功");
    }
}
