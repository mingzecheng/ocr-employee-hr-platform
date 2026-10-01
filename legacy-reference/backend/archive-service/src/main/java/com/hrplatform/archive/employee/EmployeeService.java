package com.hrplatform.archive.employee;

import com.hrplatform.archive.api.ArchiveDtos;
import com.hrplatform.archive.api.EmployeeExistsException;
import com.hrplatform.archive.api.ArchiveNotFoundException;
import com.hrplatform.archive.api.DataScopeDeniedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.cache.CacheKeys;
import com.hrplatform.common.security.DataScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class EmployeeService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);
    private static final Duration LIST_CACHE_TTL = Duration.ofSeconds(60);
    private static final TypeReference<ArchiveDtos.EmployeePageData> PAGE_TYPE = new TypeReference<>() {};

    private final EmployeeMapper employeeMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public EmployeeService(EmployeeMapper employeeMapper, StringRedisTemplate redis,
                           ObjectMapper objectMapper) {
        this.employeeMapper = employeeMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ArchiveDtos.EmployeeData create(ArchiveDtos.EmployeeCreateRequest request, DataScope scope) {
        String employeeNo = request.employeeNo().trim();
        if (!scope.allowsDepartment(request.departmentId())) {
            throw new DataScopeDeniedException();
        }
        if (employeeMapper.countByEmployeeNo(employeeNo) > 0) {
            throw new EmployeeExistsException(employeeNo);
        }
        Employee employee = new Employee();
        employee.setEmployeeNo(employeeNo);
        employee.setName(request.name().trim());
        employee.setIdCardNo(blankToNull(request.idCardNo()));
        employee.setDepartmentId(request.departmentId());
        employee.setStatus("ACTIVE");
        employeeMapper.insert(employee);
        evictArchiveListCacheAfterCommit();
        return toData(employee);
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.EmployeePageData list(int page, int pageSize, DataScope scope) {
        return list(page, pageSize, null, "ALL", scope);
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.EmployeePageData list(int page, int pageSize, String keyword, String status, DataScope scope) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedStatus = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? null : status.trim().toUpperCase();
        StringBuilder queryKey = new StringBuilder("page=").append(page).append("&pageSize=").append(pageSize);
        if (normalizedKeyword != null) queryKey.append("&keyword=").append(normalizedKeyword);
        if (normalizedStatus != null) queryKey.append("&status=").append(normalizedStatus);
        String cacheKey = CacheKeys.archiveList(scope.cacheKey(), queryKey.toString());
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, PAGE_TYPE);
            }
        } catch (JsonProcessingException | RuntimeException exception) {
            log.debug("Archive list cache read failed; falling back to database", exception);
        }

        List<ArchiveDtos.EmployeeData> items = employeeMapper.listInScope((page - 1) * pageSize, pageSize,
                        scope.type().name(), scope.employeeId(), scope.departmentId(), normalizedKeyword,
                        normalizedStatus).stream()
                .map(this::toListData)
                .toList();
        ArchiveDtos.EmployeePageData result = new ArchiveDtos.EmployeePageData(
                items, employeeMapper.countInScope(scope.type().name(), scope.employeeId(), scope.departmentId(),
                        normalizedKeyword, normalizedStatus),
                page, pageSize);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), LIST_CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.debug("Archive list cache write failed", exception);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.ResourceAuthorizationData authorize(long employeeId, DataScope scope) {
        Employee employee = employeeMapper.findByIdInScope(employeeId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (employee == null) {
            throw new DataScopeDeniedException();
        }
        return new ArchiveDtos.ResourceAuthorizationData(employee.getId(), employee.getDepartmentId());
    }

    private ArchiveDtos.EmployeeData toData(Employee employee) {
        return new ArchiveDtos.EmployeeData(employee.getId(), employee.getEmployeeNo(), employee.getName(),
                employee.getIdCardNo(), employee.getDepartmentId(), employee.getStatus());
    }

    private ArchiveDtos.EmployeeData toListData(Employee employee) {
        return new ArchiveDtos.EmployeeData(employee.getId(), employee.getEmployeeNo(), employee.getName(),
                maskIdCard(employee.getIdCardNo()), employee.getDepartmentId(), employee.getStatus());
    }

    private String maskIdCard(String idCardNo) {
        if (idCardNo == null || idCardNo.isBlank()) {
            return null;
        }
        String normalized = idCardNo.trim();
        if (normalized.length() <= 4) {
            return "*".repeat(normalized.length());
        }
        if (normalized.length() <= 8) {
            return normalized.substring(0, 2) + "*".repeat(normalized.length() - 4)
                    + normalized.substring(normalized.length() - 2);
        }
        return normalized.substring(0, 4) + "*".repeat(normalized.length() - 8)
                + normalized.substring(normalized.length() - 4);
    }

    private void evictArchiveListCacheAfterCommit() {
        Runnable evict = () -> {
            try {
                Set<String> keys = redis.keys(CacheKeys.archiveListPrefix() + "*");
                if (keys != null && !keys.isEmpty()) {
                    redis.delete(keys);
                }
            } catch (RuntimeException exception) {
                log.debug("Archive list cache eviction failed", exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
