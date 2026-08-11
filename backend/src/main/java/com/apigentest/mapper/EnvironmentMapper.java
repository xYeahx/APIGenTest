package com.apigentest.mapper;

import com.apigentest.entity.Environment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnvironmentMapper extends BaseMapper<Environment> {
}