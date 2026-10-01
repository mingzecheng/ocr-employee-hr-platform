package com.hrplatform.identity.org;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {

    private static final String DEPARTMENT_TREE_KEY = "hr:org:department:tree";

    @Test
    void returnsCachedTreeWithoutQueryingDatabase() {
        DepartmentMapper mapper = mock(DepartmentMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(DEPARTMENT_TREE_KEY)).thenReturn("""
                [{"id":1,"parentId":null,"code":"HQ","name":"总部","sortNo":1,"active":true}]
                """);

        List<DepartmentNode> result = new DepartmentService(mapper, redis, new ObjectMapper())
                .findActiveTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("HQ");
        verify(mapper, never()).findActiveTree();
    }

    @Test
    void loadsFromDatabaseAndCachesOnMiss() {
        DepartmentMapper mapper = mock(DepartmentMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        DepartmentNode root = new DepartmentNode();
        root.setId(1L);
        root.setCode("HQ");
        root.setName("总部");
        root.setSortNo(1);
        root.setActive(true);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(DEPARTMENT_TREE_KEY)).thenReturn(null);
        when(mapper.findActiveTree()).thenReturn(List.of(root));

        List<DepartmentNode> result = new DepartmentService(mapper, redis, new ObjectMapper())
                .findActiveTree();

        assertThat(result).containsExactly(root);
        verify(values).set(eq(DEPARTMENT_TREE_KEY), contains("\"code\":\"HQ\""),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        DepartmentMapper mapper = mock(DepartmentMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DepartmentNode root = new DepartmentNode();
        root.setCode("HQ");
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        when(mapper.findActiveTree()).thenReturn(List.of(root));

        assertThat(new DepartmentService(mapper, redis, new ObjectMapper()).findActiveTree())
                .containsExactly(root);
    }
}
