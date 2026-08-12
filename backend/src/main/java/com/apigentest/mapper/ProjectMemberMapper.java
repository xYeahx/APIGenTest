package com.apigentest.mapper;

import com.apigentest.entity.ProjectMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {
}