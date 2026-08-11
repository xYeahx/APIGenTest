package com.apigentest.mapper;

import com.apigentest.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}