# FAQ功能完善实现文档（分类/搜索/热门/反馈 + 管理端CRUD）

本文档给出一套可直接落地到当前项目（Spring Boot + MyBatis-Plus + React）的 FAQ 完整实现方案，覆盖：

- 三张核心表：`faq_category`、`faq_item`、`faq_feedback`
- 用户端 4 个接口：分类、列表搜索、热门、反馈
- 管理端分类/FAQ CRUD
- 详细业务规则、实现步骤、完整后端代码（可按包落地）

---

## 1. 业务目标与边界

### 1.1 业务目标

1. 用户可以按分类浏览 FAQ。
2. 用户可以按关键词搜索 FAQ（问题 + 答案 + 关键词）。
3. 用户可以看到热门问题推荐。
4. 用户可对 FAQ 做反馈（有帮助/无帮助）。
5. 管理员可维护 FAQ 分类和 FAQ 内容（CRUD、上下架、排序）。

### 1.2 边界约束

- 多租户隔离：所有表和查询均带 `tenant_id`。
- 反馈防刷：同一用户对同一 FAQ 仅保留一条反馈记录（可更新）。
- 热门问题第一版使用 SQL 排序，不引入 ES。

---

## 2. 数据库设计

> 建议放入 `docs/sql/faq_init.sql` 并执行。

```sql
-- FAQ分类表
CREATE TABLE IF NOT EXISTS faq_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 100,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_name (tenant_id, name),
  KEY idx_tenant_status_sort (tenant_id, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- FAQ条目表
CREATE TABLE IF NOT EXISTS faq_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  question VARCHAR(255) NOT NULL,
  answer TEXT NOT NULL,
  keywords VARCHAR(512) DEFAULT NULL COMMENT '逗号分隔关键词',
  sort INT NOT NULL DEFAULT 100,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  view_count BIGINT NOT NULL DEFAULT 0,
  helpful_count BIGINT NOT NULL DEFAULT 0,
  unhelpful_count BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant_category_status_sort (tenant_id, category_id, status, sort),
  KEY idx_tenant_status (tenant_id, status),
  FULLTEXT KEY ft_qak (question, answer, keywords)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- FAQ反馈表
CREATE TABLE IF NOT EXISTS faq_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  faq_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  feedback_type TINYINT NOT NULL COMMENT '1有帮助 0无帮助',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_faq_user (tenant_id, faq_id, user_id),
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_tenant_faq (tenant_id, faq_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 3. 包结构建议

```text
src/main/java/com/ityfz/yulu/faq/
  controller/
    CustomerFAQController.java
    AdminFAQController.java
  dto/
    FaqListQueryDTO.java
    FaqFeedbackDTO.java
    AdminFaqCategorySaveDTO.java
    AdminFaqItemSaveDTO.java
  vo/
    FaqCategoryVO.java
    FaqItemVO.java
  entity/
    FaqCategory.java
    FaqItem.java
    FaqFeedback.java
  mapper/
    FaqCategoryMapper.java
    FaqItemMapper.java
    FaqFeedbackMapper.java
  service/
    FaqCustomerService.java
    FaqAdminService.java
  service/impl/
    FaqCustomerServiceImpl.java
    FaqAdminServiceImpl.java
```

---

## 4. 完整后端代码（可直接改造落地）

### 4.1 Entity

#### `FaqCategory.java`

```java
package com.ityfz.yulu.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("faq_category")
public class FaqCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

#### `FaqItem.java`

```java
package com.ityfz.yulu.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("faq_item")
public class FaqItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long categoryId;
    private String question;
    private String answer;
    private String keywords;
    private Integer sort;
    private Integer status;
    private Long viewCount;
    private Long helpfulCount;
    private Long unhelpfulCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

#### `FaqFeedback.java`

```java
package com.ityfz.yulu.faq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("faq_feedback")
public class FaqFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long faqId;
    private Long userId;
    private Integer feedbackType; // 1 helpful, 0 unhelpful
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 4.2 Mapper

#### `FaqCategoryMapper.java`

```java
package com.ityfz.yulu.faq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.faq.entity.FaqCategory;

public interface FaqCategoryMapper extends BaseMapper<FaqCategory> {
}
```

#### `FaqItemMapper.java`

```java
package com.ityfz.yulu.faq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.faq.entity.FaqItem;

public interface FaqItemMapper extends BaseMapper<FaqItem> {
}
```

#### `FaqFeedbackMapper.java`

```java
package com.ityfz.yulu.faq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.faq.entity.FaqFeedback;

public interface FaqFeedbackMapper extends BaseMapper<FaqFeedback> {
}
```

### 4.3 DTO / VO

#### `FaqListQueryDTO.java`

```java
package com.ityfz.yulu.faq.dto;

import lombok.Data;

@Data
public class FaqListQueryDTO {
    private Long categoryId;
    private String keyword;
    private Integer page = 1;
    private Integer size = 10;
}
```

#### `FaqFeedbackDTO.java`

```java
package com.ityfz.yulu.faq.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class FaqFeedbackDTO {
    @NotNull
    private Long faqId;

    @NotNull
    private Integer feedbackType; // 1 有帮助, 0 无帮助
}
```

#### `AdminFaqCategorySaveDTO.java`

```java
package com.ityfz.yulu.faq.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AdminFaqCategorySaveDTO {
    @NotBlank
    private String name;
    private Integer sort = 100;
    private Integer status = 1;
}
```

#### `AdminFaqItemSaveDTO.java`

```java
package com.ityfz.yulu.faq.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AdminFaqItemSaveDTO {
    @NotNull
    private Long categoryId;

    @NotBlank
    private String question;

    @NotBlank
    private String answer;

    private String keywords;
    private Integer sort = 100;
    private Integer status = 1;
}
```

#### `FaqCategoryVO.java`

```java
package com.ityfz.yulu.faq.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaqCategoryVO {
    private Long id;
    private String name;
    private Integer sort;
}
```

#### `FaqItemVO.java`

```java
package com.ityfz.yulu.faq.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaqItemVO {
    private Long id;
    private Long categoryId;
    private String question;
    private String answer;
    private String keywords;
    private Long viewCount;
    private Long helpfulCount;
    private Long unhelpfulCount;
}
```

### 4.4 Service接口

#### `FaqCustomerService.java`

```java
package com.ityfz.yulu.faq.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.faq.dto.FaqFeedbackDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;

import java.util.List;

public interface FaqCustomerService {
    List<FaqCategoryVO> listCategories(Long tenantId);

    IPage<FaqItemVO> listFaq(Long tenantId, FaqListQueryDTO query);

    List<FaqItemVO> hotFaq(Long tenantId, Integer limit);

    void feedback(Long tenantId, Long userId, FaqFeedbackDTO dto);
}
```

#### `FaqAdminService.java`

```java
package com.ityfz.yulu.faq.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.faq.dto.AdminFaqCategorySaveDTO;
import com.ityfz.yulu.faq.dto.AdminFaqItemSaveDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;

import java.util.List;

public interface FaqAdminService {
    Long createCategory(Long tenantId, AdminFaqCategorySaveDTO dto);

    void updateCategory(Long tenantId, Long id, AdminFaqCategorySaveDTO dto);

    void deleteCategory(Long tenantId, Long id);

    List<FaqCategoryVO> listCategories(Long tenantId);

    Long createFaq(Long tenantId, AdminFaqItemSaveDTO dto);

    void updateFaq(Long tenantId, Long id, AdminFaqItemSaveDTO dto);

    void updateFaqStatus(Long tenantId, Long id, Integer status);

    void deleteFaq(Long tenantId, Long id);

    IPage<FaqItemVO> pageFaq(Long tenantId, FaqListQueryDTO query);
}
```

### 4.5 Service实现

#### `FaqCustomerServiceImpl.java`

```java
package com.ityfz.yulu.faq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
@RequiredArgsConstructor
public class FaqCustomerServiceImpl implements FaqCustomerService {

    private final FaqCategoryMapper categoryMapper;
    private final FaqItemMapper itemMapper;
    private final FaqFeedbackMapper feedbackMapper;

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
        FaqItem item = itemMapper.selectOne(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getId, dto.getFaqId())
                .last("limit 1"));
        if (item == null) {
            return;
        }

        FaqFeedback old = feedbackMapper.selectOne(new LambdaQueryWrapper<FaqFeedback>()
                .eq(FaqFeedback::getTenantId, tenantId)
                .eq(FaqFeedback::getFaqId, dto.getFaqId())
                .eq(FaqFeedback::getUserId, userId)
                .last("limit 1"));

        LocalDateTime now = LocalDateTime.now();
        if (old == null) {
            FaqFeedback fb = new FaqFeedback();
            fb.setTenantId(tenantId);
            fb.setFaqId(dto.getFaqId());
            fb.setUserId(userId);
            fb.setFeedbackType(dto.getFeedbackType());
            fb.setCreateTime(now);
            fb.setUpdateTime(now);
            feedbackMapper.insert(fb);

            if (dto.getFeedbackType() == 1) {
                item.setHelpfulCount(item.getHelpfulCount() + 1);
            } else {
                item.setUnhelpfulCount(item.getUnhelpfulCount() + 1);
            }
        } else if (!old.getFeedbackType().equals(dto.getFeedbackType())) {
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
```

#### `FaqAdminServiceImpl.java`

```java
package com.ityfz.yulu.faq.service.impl;

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
```

### 4.6 Controller

#### `CustomerFAQController.java`（替换现有占位）

```java
package com.ityfz.yulu.customer.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.faq.dto.FaqFeedbackDTO;
import com.ityfz.yulu.faq.dto.FaqListQueryDTO;
import com.ityfz.yulu.faq.service.FaqCustomerService;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;
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
public class CustomerFAQController {

    private final FaqCustomerService faqCustomerService;

    @GetMapping("/categories")
    public ApiResponse<List<FaqCategoryVO>> categories() {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqCustomerService.listCategories(tenantId));
    }

    @GetMapping("/list")
    public ApiResponse<IPage<FaqItemVO>> list(FaqListQueryDTO query) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqCustomerService.listFaq(tenantId, query));
    }

    @GetMapping("/hot")
    public ApiResponse<List<FaqItemVO>> hot(@RequestParam(required = false) Integer limit) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqCustomerService.hotFaq(tenantId, limit));
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody FaqFeedbackDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        faqCustomerService.feedback(tenantId, userId, dto);
        return ApiResponse.success("反馈成功");
    }
}
```

#### `AdminFAQController.java`

```java
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
public class AdminFAQController {

    private final FaqAdminService faqAdminService;

    @PostMapping("/category")
    public ApiResponse<Long> createCategory(@Valid @RequestBody AdminFaqCategorySaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.createCategory(tenantId, dto));
    }

    @PutMapping("/category/{id}")
    public ApiResponse<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody AdminFaqCategorySaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.updateCategory(tenantId, id, dto);
        return ApiResponse.success("更新成功");
    }

    @DeleteMapping("/category/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.deleteCategory(tenantId, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/categories")
    public ApiResponse<List<FaqCategoryVO>> categories() {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.listCategories(tenantId));
    }

    @PostMapping("/item")
    public ApiResponse<Long> createItem(@Valid @RequestBody AdminFaqItemSaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.createFaq(tenantId, dto));
    }

    @PutMapping("/item/{id}")
    public ApiResponse<Void> updateItem(@PathVariable Long id, @Valid @RequestBody AdminFaqItemSaveDTO dto) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.updateFaq(tenantId, id, dto);
        return ApiResponse.success("更新成功");
    }

    @PutMapping("/item/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.updateFaqStatus(tenantId, id, status);
        return ApiResponse.success("状态更新成功");
    }

    @DeleteMapping("/item/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        Long tenantId = SecurityUtil.currentTenantId();
        faqAdminService.deleteFaq(tenantId, id);
        return ApiResponse.success("删除成功");
    }

    @GetMapping("/item/list")
    public ApiResponse<IPage<FaqItemVO>> page(FaqListQueryDTO query) {
        Long tenantId = SecurityUtil.currentTenantId();
        return ApiResponse.success(faqAdminService.pageFaq(tenantId, query));
    }
}
```

---

## 5. 实现步骤（按落地顺序）

1. 建表并初始化测试数据（分类 + FAQ）。
2. 新建 FAQ 领域层代码（entity/mapper/dto/vo/service）。
3. 替换用户端 FAQ 控制器占位实现。
4. 新增管理端 FAQ 控制器。
5. 联调前端：
   - 用户端 FAQ 页面接 `/api/customer/faq/categories|list|hot|feedback`
   - 管理端 FAQ 管理页接 `/api/admin/faq/*`
6. 回归测试多租户隔离与权限。

---

## 6. 关键技术点与思路

### 6.1 搜索实现

第一版用 `LIKE`，满足中小数据量：
- `question like`
- `answer like`
- `keywords like`

后续大数据量可平滑切 ES。

### 6.2 热门实现

第一版：`helpful_count desc, view_count desc`。

可扩展为评分公式：
`score = helpful_count * 1.0 + view_count * 0.1 - unhelpful_count * 0.5`

### 6.3 反馈幂等

- 同用户同 FAQ 仅一条反馈（DB 唯一键保障）。
- 重复点击同反馈类型直接忽略。
- 反馈类型变化时做计数对冲。

### 6.4 多租户与权限

- 所有查询带 `tenant_id`。
- 用户端 `@RequireRole("USER")`。
- 管理端 `@RequireRole("ADMIN")`。

---

## 7. 测试用例清单

1. 用户端分类列表返回启用分类。
2. 用户端关键词搜索命中 question/answer/keywords。
3. 用户端热门列表排序正确。
4. 用户端同一 FAQ 重复反馈不重复计数。
5. 管理端分类 CRUD 生效。
6. 管理端 FAQ CRUD + 上下架生效。
7. 不同租户数据不可见。

---

## 8. 与现有代码对接说明

- 你当前已有 `CustomerFAQController` 占位实现，直接替换为本文版本。
- 前端 `CustomerFaqPage` 当前展示 `Empty`，需接四个接口。
- OpenAPI 已有 `customer-faq` 分组，无需新增分组配置。

---

## 9. 后续增强建议

- FAQ浏览埋点（view_count 实时增量）
- Redis 热门缓存（定时刷新）
- 同义词搜索/拼写纠错
- 后台“无结果关键词”报表

