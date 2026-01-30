package com.ityfz.yulu.handoff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.handoff.entity.HandoffEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 转人工事件记录Mapper接口
 */
@Mapper
public interface HandoffEventMapper extends BaseMapper<HandoffEvent> {


}
