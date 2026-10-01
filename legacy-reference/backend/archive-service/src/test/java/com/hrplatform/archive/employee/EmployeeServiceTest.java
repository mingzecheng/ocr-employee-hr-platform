package com.hrplatform.archive.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.archive.api.ArchiveDtos;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {
    private static final DataScope ADMIN_SCOPE = DataScope.all(0L);
    private static final String LIST_KEY = "hr:list:archive:"
            + "9cfb7c4d8f7bd3c6eb7b3c38f2c8e5c6b5f2ad60f0f8e9b8b6e6c2d91b1f8d35";

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void returnsCachedListWithoutQueryingDatabase() throws Exception {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(new ObjectMapper().writeValueAsString(
                new ArchiveDtos.EmployeePageData(List.of(
                        new ArchiveDtos.EmployeeData(7L, "E-007", "张三", "1101********1234", 2L, "ACTIVE")),
                        1L, 1, 20)));

        ArchiveDtos.EmployeePageData result = new EmployeeService(mapper, redis, new ObjectMapper())
                .list(1, 20, ADMIN_SCOPE);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).idCardNo()).isEqualTo("1101********1234");
        assertThat(result.total()).isEqualTo(1L);
        verify(mapper, never()).listInScope(any(Integer.class), any(Integer.class), any(), any(), any(), any(), any());
    }

    @Test
    void loadsFromDatabaseAndCachesSanitizedListOnMiss() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Employee employee = employee("110101199001011234");
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(null);
        when(mapper.listInScope(0, 20, "ALL", null, null, null, null)).thenReturn(List.of(employee));
        when(mapper.countInScope("ALL", null, null, null, null)).thenReturn(21L);

        ArchiveDtos.EmployeePageData result = new EmployeeService(mapper, redis, new ObjectMapper())
                .list(1, 20, ADMIN_SCOPE);

        assertThat(result.items().get(0).idCardNo()).isEqualTo("1101**********1234");
        assertThat(result.total()).isEqualTo(21L);
        verify(values).set(any(), contains("1101**********1234"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void appliesKeywordAndStatusToScopedPagination() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(null);
        when(mapper.listInScope(0, 20, "ALL", null, null, "张", "INACTIVE")).thenReturn(List.of());
        when(mapper.countInScope("ALL", null, null, "张", "INACTIVE")).thenReturn(0L);

        ArchiveDtos.EmployeePageData result = new EmployeeService(mapper, redis, new ObjectMapper())
                .list(1, 20, "张", "INACTIVE", ADMIN_SCOPE);

        assertThat(result.items()).isEmpty();
        verify(mapper).listInScope(0, 20, "ALL", null, null, "张", "INACTIVE");
        verify(mapper).countInScope("ALL", null, null, "张", "INACTIVE");
    }

    @Test
    void cachesEmptyListSoRepeatedEmptyQueriesDoNotHitDatabase() throws Exception {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(null);
        when(mapper.listInScope(20, 20, "ALL", null, null, null, null)).thenReturn(List.of());
        when(mapper.countInScope("ALL", null, null, null, null)).thenReturn(0L);

        ArchiveDtos.EmployeePageData result = new EmployeeService(mapper, redis, new ObjectMapper())
                .list(2, 20, ADMIN_SCOPE);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(values).set(any(), contains("\"items\""), eq(Duration.ofSeconds(60)));
    }

    @Test
    void evictsAllArchiveListKeysAfterEmployeeCreationCommits() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.keys("hr:list:archive:*")).thenReturn(Set.of(LIST_KEY));
        when(mapper.countByEmployeeNo("E-008")).thenReturn(0);

        TransactionSynchronizationManager.initSynchronization();
        new EmployeeService(mapper, redis, new ObjectMapper()).create(
                new ArchiveDtos.EmployeeCreateRequest(" E-008 ", "李四", null, null), ADMIN_SCOPE);

        verify(redis, never()).keys("hr:list:archive:*");
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(redis).delete(Set.of(LIST_KEY));
    }

    private Employee employee(String idCardNo) {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setEmployeeNo("E-007");
        employee.setName("张三");
        employee.setIdCardNo(idCardNo);
        employee.setDepartmentId(2L);
        employee.setStatus("ACTIVE");
        return employee;
    }
}
