package com.apigentest.mapper;

import com.apigentest.entity.Project;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}