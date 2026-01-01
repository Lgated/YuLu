package com.ityfz.yulu.ticket.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ityfz.yulu.ticket.entity.TicketComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单跟进记录Mapper接口
 */
@Mapper
public interface TicketCommentMapper extends BaseMapper<TicketComment> {
}
