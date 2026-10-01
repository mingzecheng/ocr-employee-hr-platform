package com.hrplatform.organization;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrganizationMapper {
    List<DepartmentNode> listEnabledDepartments();
}
