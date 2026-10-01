package com.hrplatform.identity.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUser findEnabledByUsername(@Param("username") String username);

    int countByUsername(@Param("username") String username);

    int insert(SysUser user);
}
