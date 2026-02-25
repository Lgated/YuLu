package com.ityfz.yulu.faq.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.faq.dto.AdminFaqCategorySaveDTO;
import com.ityfz.yulu.faq.dto.AdminFaqItemSaveDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.service.FaqAdminService;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/faq")
@RequiredArgsConstructor
@RequireRole("ADMIN")
@Validated
@Tag(name = "B端-FAQ（Admin/FAQ）", description = "管理员FAQ管理：分类CRUD、FAQ CRUD、上下架")
public class AdminFAQController {

    private final FaqAdminService faqAdminService;

    @PostMapping("/category")
    @Operation(summary = "创建FAQ分类")
    public ApiResponse<Long> createCategory(@Valid @RequestBody AdminFaqCategorySaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.createCategory(tenantId, dto));
    }

    @PutMapping("/category/{id}")
    @Operation(summary = "更新FAQ分类")
    public ApiResponse<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody AdminFaqCategorySaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.updateCategory(tenantId, id, dto);
        return ApiResponse.success("更新成功");
    }

    @DeleteMapping("/category/{id}")
    @Operation(summary = "删除FAQ分类")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.deleteCategory(tenantId, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/categories")
    @Operation(summary = "查询FAQ分类列表")
    public ApiResponse<List<FaqCategoryVO>> categories() {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.listCategories(tenantId));
    }

    @PostMapping("/item")
    @Operation(summary = "创建FAQ条目")
    public ApiResponse<Long> createItem(@Valid @RequestBody AdminFaqItemSaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.createFaq(tenantId, dto));
    }

    @PutMapping("/item/{id}")
    @Operation(summary = "更新FAQ条目")
    public ApiResponse<Void> updateItem(@PathVariable Long id, @Valid @RequestBody AdminFaqItemSaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.updateFaq(tenantId, id, dto);
        return ApiResponse.success("更新成功");
    }

    @PutMapping("/item/{id}/status")
    @Operation(summary = "更新FAQ条目上下架状态")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.updateFaqStatus(tenantId, id, status);
        return ApiResponse.success("状态更新成功");
    }

    @DeleteMapping("/item/{id}")
    @Operation(summary = "删除FAQ条目")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.deleteFaq(tenantId, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/item/list")
    @Operation(summary = "分页查询FAQ条目")
    public ApiResponse<IPage<FaqItemVO>> page(FaqListQueryDTO query) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.pageFaq(tenantId, query));
    }
}
