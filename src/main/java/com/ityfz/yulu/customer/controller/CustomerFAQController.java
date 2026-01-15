package com.ityfz.yulu.customer.controller;

import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端FAQ Controller
 */
@RestController
@RequestMapping("/api/customer/faq")
@RequiredArgsConstructor
@RequireRole("USER")
public class CustomerFAQController {

    /**
     * 获取FAQ列表
     * GET /api/customer/faq/list?category=xxx&keyword=xxx
     */
    @GetMapping("/list")
    public ApiResponse<List<Object>> listFAQ(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword
    ) {
        // TODO: 实现知识库查询逻辑
        return ApiResponse.success("OK", List.of());
    }

}
