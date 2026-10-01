package com.hrplatform.identity.org;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    List<DepartmentNode> findActiveTree();
}
