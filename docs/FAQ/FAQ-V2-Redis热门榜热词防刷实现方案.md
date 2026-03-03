# FAQ V2 完善方案（Redis 热门榜 + 热词 + 防刷频控）

本文档基于你当前已落地的 FAQ V1（分类/搜索/热门/反馈/浏览）继续升级，目标：

1. 热门榜从“纯 DB 排序”升级为 Redis 排行榜（低延迟）
2. 增加热词统计（用于推荐搜索词）
3. 增加同用户频控防刷（浏览/反馈）

---

## 0. 当前状态确认

你当前项目里，反馈功能已经实现：

- 接口：`POST /api/customer/faq/feedback`
- 实现位置：
  - `src/main/java/com/ityfz/yulu/faq/controller/CustomerFAQController.java`
  - `src/main/java/com/ityfz/yulu/faq/service/Impl/FaqCustomerServiceImpl.java`
- 逻辑：
  - 首次反馈：插入 `faq_feedback` + 对应计数 +1
  - 重复同类型反馈：忽略
  - 反馈类型变更：旧计数-1，新计数+1

所以“反馈”已具备业务闭环，不需要从零重做。

---

## 1. 设计目标与策略

### 1.1 热门榜策略

- Redis ZSet 存“热度分”
- 读热门优先 Redis
- Redis miss 时回源 DB 并回填

热度分推荐：

```text
score = helpful_count * 3 + view_count * 1 - unhelpful_count * 2
```

### 1.2 热词策略

- 用户每次搜索 keyword 时写 Redis ZSet 计数
- 提供热词 TopN 接口
- 短词、无效词过滤

### 1.3 防刷策略

- 浏览防刷：同用户同 FAQ 在 N 秒内只计一次（建议 60s）
- 反馈防刷：同用户同 FAQ 在 N 秒内只允许提交一次（建议 5s）
- 使用 Redis `SETNX + EXPIRE`（或 `setIfAbsent`）

---

## 2. Redis Key 设计

```text
# 热门榜（按租户）
faq:hot:zset:{tenantId}                     -> ZSET(member=faqId, score=hotScore)

# 热词榜（按租户）
faq:search:hotword:{tenantId}               -> ZSET(member=keyword, score=count)

# 浏览频控（按租户/用户/FAQ）
faq:view:dedup:{tenantId}:{userId}:{faqId}  -> STRING(1), TTL=60s

# 反馈频控（按租户/用户/FAQ）
faq:feedback:lock:{tenantId}:{userId}:{faqId} -> STRING(1), TTL=5s
```

---

## 3. 代码改造（完整示例）

> 说明：以下代码按你当前项目风格写，包名保持 `com.ityfz.yulu.faq`。

### 3.1 新增 Redis 操作组件

文件：`src/main/java/com/ityfz/yulu/faq/service/FaqRedisService.java`

```java
package com.ityfz.yulu.faq.service;

import java.util.List;

public interface FaqRedisService {

    void updateHotScore(Long tenantId, Long faqId, long score);

    List<Long> topHotFaqIds(Long tenantId, int limit);

    void incrHotword(Long tenantId, String keyword);

    List<String> topHotwords(Long tenantId, int limit);

    boolean tryDedupView(Long tenantId, Long userId, Long faqId, int ttlSeconds);

    boolean tryLockFeedback(Long tenantId, Long userId, Long faqId, int ttlSeconds);
}
```

文件：`src/main/java/com/ityfz/yulu/faq/service/impl/FaqRedisServiceImpl.java`

```java
package com.ityfz.yulu.faq.service.impl;

import com.ityfz.yulu.faq.service.FaqRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FaqRedisServiceImpl implements FaqRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static String hotZsetKey(Long tenantId) {
        return "faq:hot:zset:" + tenantId;
    }

    private static String hotwordKey(Long tenantId) {
        return "faq:search:hotword:" + tenantId;
    }

    private static String viewDedupKey(Long tenantId, Long userId, Long faqId) {
        return "faq:view:dedup:" + tenantId + ":" + userId + ":" + faqId;
    }

    private static String feedbackLockKey(Long tenantId, Long userId, Long faqId) {
        return "faq:feedback:lock:" + tenantId + ":" + userId + ":" + faqId;
    }

    @Override
    public void updateHotScore(Long tenantId, Long faqId, long score) {
        redisTemplate.opsForZSet().add(hotZsetKey(tenantId), String.valueOf(faqId), score);
    }

    @Override
    public List<Long> topHotFaqIds(Long tenantId, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Set<Object> set = redisTemplate.opsForZSet().reverseRange(hotZsetKey(tenantId), 0, size - 1);
        List<Long> ids = new ArrayList<>();
        if (set != null) {
            for (Object o : set) {
                try {
                    ids.add(Long.parseLong(String.valueOf(o)));
                } catch (Exception ignore) {
                }
            }
        }
        return ids;
    }

    @Override
    public void incrHotword(Long tenantId, String keyword) {
        if (!StringUtils.hasText(keyword)) return;
        String kw = keyword.trim();
        if (kw.length() < 2 || kw.length() > 30) return;
        redisTemplate.opsForZSet().incrementScore(hotwordKey(tenantId), kw, 1D);
    }

    @Override
    public List<String> topHotwords(Long tenantId, int limit) {
        int size = Math.max(1, Math.min(limit, 20));
        Set<Object> set = redisTemplate.opsForZSet().reverseRange(hotwordKey(tenantId), 0, size - 1);
        List<String> list = new ArrayList<>();
        if (set != null) {
            for (Object o : set) {
                list.add(String.valueOf(o));
            }
        }
        return list;
    }

    @Override
    public boolean tryDedupView(Long tenantId, Long userId, Long faqId, int ttlSeconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                viewDedupKey(tenantId, userId, faqId),
                "1",
                ttlSeconds,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean tryLockFeedback(Long tenantId, Long userId, Long faqId, int ttlSeconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                feedbackLockKey(tenantId, userId, faqId),
                "1",
                ttlSeconds,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(ok);
    }
}
```

---

### 3.2 扩展 Customer Service 接口

文件：`src/main/java/com/ityfz/yulu/faq/service/FaqCustomerService.java`

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

    void incrViewCount(Long tenantId, Long faqId);

    List<String> hotKeywords(Long tenantId, Integer limit);
}
```

---

### 3.3 改造 FaqCustomerServiceImpl（核心）

文件：`src/main/java/com/ityfz/yulu/faq/service/Impl/FaqCustomerServiceImpl.java`

关键改造点：

1) 注入 `FaqRedisService`
2) `listFaq` 统计热词
3) `hotFaq` 优先走 Redis
4) `feedback` 增加频控 + 回写热门分
5) `incrViewCount` 增加去重 + 回写热门分
6) 新增 `hotKeywords`

可直接替换的方法片段如下：

```java
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
import com.ityfz.yulu.faq.service.FaqRedisService;
import com.ityfz.yulu.faq.vo.FaqCategoryVO;
import com.ityfz.yulu.faq.vo.FaqItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FaqCustomerServiceImpl implements FaqCustomerService {

    private final FaqCategoryMapper categoryMapper;
    private final FaqItemMapper itemMapper;
    private final FaqFeedbackMapper feedbackMapper;
    private final FaqRedisService faqRedisService;

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
        if (StringUtils.hasText(query.getKeyword())) {
            faqRedisService.incrHotword(tenantId, query.getKeyword());
        }

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

        // 1) Redis优先
        List<Long> ids = faqRedisService.topHotFaqIds(tenantId, size);
        if (!ids.isEmpty()) {
            List<FaqItem> list = itemMapper.selectBatchIds(ids);
            Map<Long, FaqItem> map = list.stream().collect(Collectors.toMap(FaqItem::getId, i -> i));
            List<FaqItemVO> ordered = new ArrayList<>();
            for (Long id : ids) {
                FaqItem i = map.get(id);
                if (i != null && Objects.equals(i.getTenantId(), tenantId) && Objects.equals(i.getStatus(), 1)) {
                    ordered.add(toVO(i));
                }
            }
            if (!ordered.isEmpty()) {
                return ordered;
            }
        }

        // 2) DB回源
        List<FaqItem> db = itemMapper.selectList(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getStatus, 1)
                .orderByDesc(FaqItem::getHelpfulCount)
                .orderByDesc(FaqItem::getViewCount)
                .last("limit " + size));

        // 3) 回填Redis
        for (FaqItem i : db) {
            faqRedisService.updateHotScore(tenantId, i.getId(), calcHotScore(i));
        }

        return db.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void feedback(Long tenantId, Long userId, FaqFeedbackDTO dto) {
        // 反馈频控：同用户同FAQ 5秒只允许一次提交
        boolean allowed = faqRedisService.tryLockFeedback(tenantId, userId, dto.getFaqId(), 5);
        if (!allowed) {
            return;
        }

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

        // 回写热门分
        faqRedisService.updateHotScore(tenantId, item.getId(), calcHotScore(item));
    }

    @Override
    @Transactional
    public void incrViewCount(Long tenantId, Long faqId) {
        // 浏览去重：同用户同FAQ 60秒只记一次
        // 用户ID建议从controller传进来；如当前方法签名未带userId可改为 incrViewCount(tenantId,userId,faqId)
        // 这里先按“无用户去重”演示时，不调用 tryDedupView。

        itemMapper.update(
                null,
                new LambdaUpdateWrapper<FaqItem>()
                        .eq(FaqItem::getTenantId, tenantId)
                        .eq(FaqItem::getId, faqId)
                        .setSql("view_count = IFNULL(view_count, 0) + 1")
        );

        FaqItem latest = itemMapper.selectOne(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getTenantId, tenantId)
                .eq(FaqItem::getId, faqId)
                .last("limit 1"));
        if (latest != null) {
            faqRedisService.updateHotScore(tenantId, faqId, calcHotScore(latest));
        }
    }

    @Override
    public List<String> hotKeywords(Long tenantId, Integer limit) {
        int size = (limit == null || limit <= 0 || limit > 20) ? 10 : limit;
        return faqRedisService.topHotwords(tenantId, size);
    }

    private long calcHotScore(FaqItem i) {
        long helpful = i.getHelpfulCount() == null ? 0 : i.getHelpfulCount();
        long views = i.getViewCount() == null ? 0 : i.getViewCount();
        long unhelpful = i.getUnhelpfulCount() == null ? 0 : i.getUnhelpfulCount();
        return helpful * 3 + views - unhelpful * 2;
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

> 建议你把 `incrViewCount` 方法签名改成 `incrViewCount(Long tenantId, Long userId, Long faqId)`，这样可用 `tryDedupView` 真正实现同用户去重。

---

### 3.4 Controller 新增热词接口 + 浏览传 userId

文件：`src/main/java/com/ityfz/yulu/faq/controller/CustomerFAQController.java`

```java
@GetMapping("/hot-keywords")
@Operation(summary = "获取FAQ热词", description = "返回当前租户TopN搜索热词")
public ApiResponse<List<String>> hotKeywords(@RequestParam(required = false) Integer limit) {
    Long tenantId = SecurityUtil.currentTenantId();
    return ApiResponse.success(faqCustomerService.hotKeywords(tenantId, limit));
}

@PostMapping("/view/{faqId}")
@Operation(summary = "记录FAQ浏览", description = "FAQ被展开查看时，浏览数+1（可加同用户去重）")
public ApiResponse<Void> view(@PathVariable Long faqId) {
    Long tenantId = SecurityUtil.currentTenantId();
    // 如你改了签名：Long userId = SecurityUtil.currentUserId(); faqCustomerService.incrViewCount(tenantId, userId, faqId);
    faqCustomerService.incrViewCount(tenantId, faqId);
    return ApiResponse.success("记录成功");
}
```

---

## 4. 前端配合改造要点

1. 搜索框触发 `/api/customer/faq/list` 时，后端自动统计热词。
2. 新增热词展示区，调用 `/api/customer/faq/hot-keywords`。
3. FAQ 展开继续调用 `/api/customer/faq/view/{faqId}`。
4. 反馈继续调用 `/api/customer/faq/feedback`（你已完成）。

---

## 5. 实施步骤（建议顺序）

1. 增加 `FaqRedisService` 与 `FaqRedisServiceImpl`。
2. 扩展 `FaqCustomerService` 接口方法。
3. 改造 `FaqCustomerServiceImpl`（热点读写、热词统计、防刷）。
4. 增加 `CustomerFAQController` 热词接口。
5. 联调前端热词展示。
6. 压测与风控验证（重复点击/重复反馈）。

---

## 6. 测试清单（必须跑）

### 6.1 热门榜

- 初次调用 `/hot`：Redis为空 -> 回源DB -> 回填Redis。
- 再次调用 `/hot`：直接命中Redis排序。
- 浏览/反馈后，热门排序应变化。

### 6.2 热词

- 多次搜索“退款”，`/hot-keywords` 中“退款”分数上升。
- 无效词（长度<2）不应入榜。

### 6.3 防刷

- 同用户连续快速提交反馈：5秒内只生效一次。
- 同用户短时间连续展开同FAQ：60秒内只记一次浏览（你签名带userId后测试）。

### 6.4 多租户隔离

- tenant A 的热榜/热词不应出现在 tenant B。

---

## 7. 注意事项

1. `RedisTemplate<String, Object>` 场景下 ZSet member 建议统一 String（faqId 转字符串）。
2. 防刷是“软防刷”，不是绝对防作弊；必要时可叠加设备指纹/IP。
3. 热门分是策略参数，后续可按业务调整权重。
4. 若热榜要求强一致，需引入异步任务或消息队列，当前方案是“最终一致”。

---

## 8. 你现在是否有反馈功能？

有，且已完成基础幂等逻辑。当前建议在 V2 仅增加“反馈频控锁（5秒）”，防止恶意连点。

