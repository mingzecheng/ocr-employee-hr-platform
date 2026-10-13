package com.hrplatform.contract;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.DataScopeDeniedException;
import com.hrplatform.employee.EmployeeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ContractService {
    private static final DateTimeFormatter CONTRACT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ContractMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final ArchiveCatalog archiveCatalog;
    private final OperationLogService operationLogService;

    public ContractService(ContractMapper mapper, EmployeeMapper employeeMapper,
                           ArchiveCatalog archiveCatalog, OperationLogService operationLogService) {
        this.mapper = mapper;
        this.employeeMapper = employeeMapper;
        this.archiveCatalog = archiveCatalog;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public ContractRecord create(ContractCreateRequest request, DataScope scope, Long operatorId) {
        validateDates(request.signedOn(), request.startsOn(), request.endsOn());
        var employee = employeeMapper.findByIdWithScope(request.employeeId(), scope.employeeId(),
                scope.departmentId(), scope.type().name());
        if (employee == null) {
            throw new DataScopeDeniedException(request.employeeId());
        }
        if (request.sourceVersionId() != null) {
            ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(request.sourceVersionId(),
                    scope.employeeId(), scope.departmentId(), scope.type().name());
            if (source == null || !request.employeeId().equals(source.employeeId())
                    || !"contract".equalsIgnoreCase(source.documentType())) {
                throw new IllegalArgumentException("合同附件必须是该员工的合同档案版本");
            }
        }

        ContractRecord previous = null;
        if (request.renewedFromContractId() != null) {
            previous = mapper.findByIdWithScope(request.renewedFromContractId(), scope.employeeId(),
                    scope.departmentId(), scope.type().name());
            if (previous == null || !request.employeeId().equals(previous.employeeId())) {
                throw new DataScopeDeniedException(request.renewedFromContractId());
            }
            if ("RENEWED".equals(previous.renewalStatus()) || "NOT_RENEWING".equals(previous.renewalStatus())) {
                throw new IllegalArgumentException("该合同已完成续签决定");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String contractNo = "CTR-" + CONTRACT_TIME.format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ContractRecord draft = new ContractRecord(null, contractNo, request.employeeId(),
                request.contractType().trim().toUpperCase(Locale.ROOT), request.signedOn(), request.startsOn(),
                request.endsOn(), statusFor(request.endsOn()), "NOT_DUE", request.sourceVersionId(),
                request.renewedFromContractId(), trimToNull(request.notes()), 0, operatorId, now, now);
        if (mapper.insert(draft) != 1) {
            throw new IllegalStateException("合同台账写入失败");
        }
        ContractRecord created = mapper.findByContractNo(contractNo);
        if (created == null) {
            throw new IllegalStateException("合同已写入但无法读取");
        }
        if (previous != null && mapper.markRenewed(previous.id(), previous.versionNo(), operatorId) != 1) {
            throw new IllegalStateException("原合同已被更新，请刷新后重试");
        }
        operationLogService.record(operatorId, previous == null ? "CREATE" : "RENEW",
                "EMPLOYEE_CONTRACT", created.id(), "SUCCESS");
        LocalDate dueFrom = LocalDate.now(BUSINESS_ZONE);
        return withDerivedRenewalStatus(created, dueFrom, dueFrom.plusDays(60));
    }

    @Transactional(readOnly = true)
    public ContractPage list(Long employeeId, String status, String renewalStatus,
                             int page, int pageSize, DataScope scope) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        if (status != null && !List.of("ACTIVE", "EXPIRED").contains(status.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("合同状态只能是 ACTIVE 或 EXPIRED");
        }
        if (renewalStatus != null && !List.of("NOT_DUE", "DUE", "RENEWED", "NOT_RENEWING")
                .contains(renewalStatus.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("续签状态无效");
        }
        Long scopedEmployee = scope.type() == DataScope.Type.EMPLOYEE ? scope.employeeId() : null;
        Long scopedDepartment = scope.type() == DataScope.Type.DEPARTMENT ? scope.departmentId() : null;
        LocalDate dueFrom = LocalDate.now(BUSINESS_ZONE);
        LocalDate dueThrough = dueFrom.plusDays(60);
        List<ContractRecord> items = mapper.list(employeeId, normalize(status), normalize(renewalStatus),
                scopedEmployee, scopedDepartment, scope.type().name(), dueFrom, dueThrough,
                (safePage - 1) * safeSize, safeSize).stream()
                .map(contract -> withDerivedRenewalStatus(contract, dueFrom, dueThrough)).toList();
        return new ContractPage(items,
                mapper.count(employeeId, normalize(status), normalize(renewalStatus), scopedEmployee,
                        scopedDepartment, scope.type().name(), dueFrom, dueThrough), safePage, safeSize);
    }

    @Transactional
    public ContractRecord markNotRenewing(Long id, DataScope scope, Long operatorId) {
        ContractRecord current = require(id, scope);
        if ("RENEWED".equals(current.renewalStatus()) || "NOT_RENEWING".equals(current.renewalStatus())) {
            throw new IllegalArgumentException("该合同已完成续签决定");
        }
        if (mapper.markNotRenewing(id, current.versionNo(), operatorId) != 1) {
            throw new IllegalStateException("合同已被更新，请刷新后重试");
        }
        operationLogService.record(operatorId, "MARK_NOT_RENEWING", "EMPLOYEE_CONTRACT", id, "SUCCESS");
        return mapper.findByIdWithScope(id, scope.employeeId(), scope.departmentId(), scope.type().name());
    }

    private ContractRecord require(Long id, DataScope scope) {
        ContractRecord contract = mapper.findByIdWithScope(id, scope.employeeId(), scope.departmentId(),
                scope.type().name());
        if (contract == null) throw new DataScopeDeniedException(id);
        return contract;
    }

    private void validateDates(LocalDate signedOn, LocalDate startsOn, LocalDate endsOn) {
        if (startsOn.isBefore(signedOn) || endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("合同日期必须满足签署日期 ≤ 生效日期 ≤ 到期日期");
        }
    }

    private String statusFor(LocalDate endsOn) {
        return endsOn.isBefore(LocalDate.now(BUSINESS_ZONE)) ? "EXPIRED" : "ACTIVE";
    }

    private ContractRecord withDerivedRenewalStatus(ContractRecord contract, LocalDate dueFrom, LocalDate dueThrough) {
        boolean due = "NOT_DUE".equals(contract.renewalStatus())
                && !contract.endsOn().isBefore(dueFrom) && !contract.endsOn().isAfter(dueThrough);
        if (!due) {
            return contract;
        }
        return new ContractRecord(contract.id(), contract.contractNo(), contract.employeeId(), contract.contractType(),
                contract.signedOn(), contract.startsOn(), contract.endsOn(), contract.status(), "DUE",
                contract.sourceVersionId(), contract.renewedFromContractId(), contract.notes(), contract.versionNo(),
                contract.createdBy(), contract.createdAt(), contract.updatedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
