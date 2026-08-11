package com.apigentest.mapper;

import com.apigentest.entity.ApiInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiInfoMapper extends BaseMapper<ApiInfo> {
}