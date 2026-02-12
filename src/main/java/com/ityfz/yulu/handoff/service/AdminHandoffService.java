package com.ityfz.yulu.handoff.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ityfz.yulu.handoff.dto.HandoffRecordQueryDTO;
import com.ityfz.yulu.handoff.vo.HandoffRecordVO;

public interface AdminHandoffService {

    /**
     * 分页查询本租户转人工记录
     */
    public Page<HandoffRecordVO> queryRecords(Long tenantId, HandoffRecordQueryDTO query);


}
