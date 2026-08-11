package com.apigentest.mapper;

import com.apigentest.entity.ScheduledTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {
}