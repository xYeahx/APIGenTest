package com.apigentest.mapper;

import com.apigentest.entity.Execution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExecutionMapper extends BaseMapper<Execution> {
}