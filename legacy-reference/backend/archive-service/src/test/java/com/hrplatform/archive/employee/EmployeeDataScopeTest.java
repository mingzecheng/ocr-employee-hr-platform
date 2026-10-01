package com.hrplatform.archive.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.cache.CacheKeys;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeDataScopeTest {

    @Test
    void listUsesScopeSpecificCacheKeyAndMapperArguments() {
        DataScope scope = DataScope.employee(9L, 7L);
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(null);
        when(mapper.listInScope(0, 20, "EMPLOYEE", 9L, null, null, null)).thenReturn(List.of());
        when(mapper.countInScope("EMPLOYEE", 9L, null, null, null)).thenReturn(0L);

        new EmployeeService(mapper, redis, new ObjectMapper()).list(1, 20, scope);

        verify(values).get(argThat(key -> key.equals(
                CacheKeys.archiveList(scope.cacheKey(), "page=1&pageSize=20"))));
        verify(mapper).listInScope(0, 20, "EMPLOYEE", 9L, null, null, null);
        verify(mapper).countInScope("EMPLOYEE", 9L, null, null, null);
        assertThat(scope.cacheKey()).isEqualTo("employee:9");
    }
}
