package com.campus.charity.module.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.notice.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_status = 0")
    long countUnread(@Param("userId") Long userId);
}
