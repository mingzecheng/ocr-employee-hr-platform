package com.hrplatform.audit;

import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import com.hrplatform.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/operation-logs")
public class AuditController {
    private final OperationLogMapper mapper;
    private final SysUserMapper userMapper;

    /** 测试兼容构造：不解析 operatorName。 */
    public AuditController(OperationLogMapper mapper) {
        this(mapper, null);
    }

    @Autowired
    public AuditController(OperationLogMapper mapper, SysUserMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<AuditPage> list(@RequestParam(required = false) Long operatorId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String objectType,
                                       @RequestParam(required = false) String result,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        LocalDateTime fromInclusive = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        List<OperationLogListItem> items = mapper.list(operatorId, action, objectType, result,
                fromInclusive, toExclusive,
                (safePage - 1) * safeSize, safeSize).stream()
                .map(AuditController::toListItem)
                .toList();
        long total = mapper.count(operatorId, action, objectType, result, fromInclusive, toExclusive);
        return ApiResponse.success(new AuditPage(items, total, safePage, safeSize), "unknown");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<OperationLogDetail> detail(@PathVariable Long id) {
        OperationLogRecord record = mapper.findById(id);
        if (record == null) {
            throw new OperationLogNotFoundException(id);
        }
        String operatorName = resolveOperatorName(record.operatorId());
        boolean hasChanges = record.beforeSummary() != null || record.afterSummary() != null;
        OperationLogDetail detail = new OperationLogDetail(record.id(), record.operatorId(), operatorName,
                record.action(), record.objectType(), record.objectId(), record.result(), record.createdAt(),
                record.traceId(), record.clientIp(), record.userAgent(), record.requestMethod(),
                record.requestUri(), record.sessionId(), record.beforeSummary(), record.afterSummary(), hasChanges);
        return ApiResponse.success(detail, "unknown");
    }

    private static OperationLogListItem toListItem(OperationLogRecord record) {
        return new OperationLogListItem(record.id(), record.operatorId(), record.action(), record.objectType(),
                record.objectId(), record.result(), record.createdAt(), maskIp(record.clientIp()));
    }

    private String resolveOperatorName(Long operatorId) {
        if (userMapper == null || operatorId == null) {
            return null;
        }
        try {
            SysUser user = userMapper.findById(operatorId);
            return user == null ? null : user.username();
        } catch (RuntimeException ignored) {
            // operatorName 为增强展示字段，用户表不可用（如最小化测试 schema）时降级为 null，不影响详情主体。
            return null;
        }
    }

    /**
     * 列表 IP 脱敏：IPv4 保留前三段（192.168.1.x），IPv6 保留前两组。
     */
    static String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String value = ip.trim();
        if (value.contains(":")) {
            String[] groups = value.split(":");
            if (groups.length >= 2 && StringUtils.hasText(groups[0]) && StringUtils.hasText(groups[1])) {
                return groups[0] + ":" + groups[1] + ":x";
            }
            return "x";
        }
        if (value.contains(".")) {
            String[] parts = value.split("\\.");
            if (parts.length >= 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".x";
            }
            return "x";
        }
        return "x";
    }
}
