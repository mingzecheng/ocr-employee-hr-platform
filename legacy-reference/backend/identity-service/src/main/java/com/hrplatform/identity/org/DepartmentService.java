package com.hrplatform.identity.org;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.cache.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Duration;

@Service
public class DepartmentService {
    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<DepartmentNode>> TREE_TYPE = new TypeReference<>() {};

    private final DepartmentMapper departmentMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DepartmentService(DepartmentMapper departmentMapper,
                             StringRedisTemplate redis,
                             ObjectMapper objectMapper) {
        this.departmentMapper = departmentMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DepartmentNode> findActiveTree() {
        String cacheKey = CacheKeys.departmentTree();
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, TREE_TYPE);
            }
        } catch (JsonProcessingException | RuntimeException exception) {
            log.debug("Department tree cache read failed; falling back to database", exception);
        }

        List<DepartmentNode> tree = departmentMapper.findActiveTree();
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(tree), CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.debug("Department tree cache write failed", exception);
        }
        return tree;
    }
}
