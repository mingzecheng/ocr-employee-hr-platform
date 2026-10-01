package com.hrplatform.identity.role;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper {

    List<String> findCodesByUserId(@Param("userId") long userId);

    List<String> findPermissionCodesByUserId(@Param("userId") long userId);

    Long findIdByCode(@Param("code") String code);

    int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);
}
