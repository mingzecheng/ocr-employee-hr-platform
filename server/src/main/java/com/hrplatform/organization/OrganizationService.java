package com.hrplatform.organization;

import com.hrplatform.audit.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OrganizationService {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,31}");
    private final OrganizationMapper mapper;
    private final OperationLogService operationLogService;

    @Autowired
    public OrganizationService(OrganizationMapper mapper, OperationLogService operationLogService) {
        this.mapper = mapper;
        this.operationLogService = operationLogService;
    }

    public OrganizationService(OrganizationMapper mapper) {
        this(mapper, null);
    }

    @Transactional(readOnly = true)
    public List<DepartmentNode> departmentTree() {
        List<DepartmentNode> flat = mapper.listEnabledDepartments();
        Map<Long, MutableDepartment> nodes = new LinkedHashMap<>();
        for (DepartmentNode item : flat) {
            nodes.put(item.id(), new MutableDepartment(item.id(), item.code(), item.name(), item.parentId()));
        }
        List<MutableDepartment> roots = new ArrayList<>();
        for (MutableDepartment node : nodes.values()) {
            if (node.parentId == null || !nodes.containsKey(node.parentId)) {
                roots.add(node);
            } else {
                nodes.get(node.parentId).children.add(node);
            }
        }
        return roots.stream().map(MutableDepartment::toImmutable).toList();
    }

    public List<PositionOption> positions(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            throw new IllegalArgumentException("departmentId 必须为正数");
        }
        return mapper.listEnabledPositions(departmentId);
    }

    @Transactional(readOnly = true)
    public List<DepartmentAdminView> adminDepartments() {
        return mapper.listAllDepartments();
    }

    @Transactional(readOnly = true)
    public List<PositionAdminView> adminPositions(Long departmentId) {
        requireDepartment(departmentId);
        return mapper.listDepartmentPositions(departmentId);
    }

    @Transactional
    public DepartmentAdminView createDepartment(DepartmentMutationRequest input, Long operatorId) {
        DepartmentMutationRequest request = normalize(input);
        ensureDepartmentCodeAvailable(request.code(), null);
        ensureParentAllowed(null, request.parentId());
        ensureManagerActive(request.managerEmployeeId());
        if (mapper.insertDepartment(request) != 1) {
            throw new OrganizationStateException("部门创建失败");
        }
        DepartmentAdminView created = mapper.findDepartmentByCode(request.code());
        if (created == null) {
            throw new OrganizationStateException("部门已创建但无法读取");
        }
        record(operatorId, "CREATE", "ORG_DEPARTMENT", created.id());
        return created;
    }

    @Transactional
    public DepartmentAdminView updateDepartment(Long id, DepartmentMutationRequest input, Long operatorId) {
        requireDepartment(id);
        DepartmentMutationRequest request = normalize(input);
        ensureDepartmentCodeAvailable(request.code(), id);
        ensureParentAllowed(id, request.parentId());
        ensureManagerActive(request.managerEmployeeId());
        if (!request.enabled()) {
            if (mapper.hasEnabledChildren(id)) {
                throw new OrganizationStateException("仍有启用子部门，不能停用部门");
            }
            if (mapper.hasActiveEmployees(id)) {
                throw new OrganizationStateException("仍有在职员工，不能停用部门");
            }
        }
        if (mapper.updateDepartment(id, request) != 1) {
            throw new OrganizationStateException("部门已被其他操作修改或不存在");
        }
        DepartmentAdminView updated = mapper.findDepartment(id);
        if (updated == null) {
            throw new OrganizationStateException("部门已更新但无法读取");
        }
        record(operatorId, "UPDATE", "ORG_DEPARTMENT", id);
        return updated;
    }

    @Transactional
    public PositionAdminView createPosition(Long departmentId, PositionMutationRequest input, Long operatorId) {
        DepartmentAdminView department = requireDepartment(departmentId);
        if (!department.enabled()) {
            throw new OrganizationStateException("停用部门不能新增岗位");
        }
        PositionMutationRequest request = normalize(input);
        ensurePositionCodeAvailable(departmentId, request.code(), null);
        if (mapper.insertPosition(departmentId, request) != 1) {
            throw new OrganizationStateException("岗位创建失败");
        }
        PositionAdminView created = mapper.findPositionByDepartmentAndCode(departmentId, request.code());
        if (created == null) {
            throw new OrganizationStateException("岗位已创建但无法读取");
        }
        record(operatorId, "CREATE", "ORG_POSITION", created.id());
        return created;
    }

    @Transactional
    public PositionAdminView updatePosition(Long departmentId, Long positionId,
                                            PositionMutationRequest input, Long operatorId) {
        PositionMutationRequest request = normalize(input);
        DepartmentAdminView department = requireDepartment(departmentId);
        PositionAdminView current = mapper.findPosition(positionId);
        if (current == null || !departmentId.equals(current.departmentId())) {
            throw new OrganizationStateException("岗位不存在或不属于指定部门");
        }
        if (!department.enabled() && request.enabled()) {
            throw new OrganizationStateException("停用部门不能启用岗位");
        }
        ensurePositionCodeAvailable(departmentId, request.code(), positionId);
        if (mapper.updatePosition(departmentId, positionId, request) != 1) {
            throw new OrganizationStateException("岗位已被其他操作修改或不存在");
        }
        PositionAdminView updated = mapper.findPosition(positionId);
        if (updated == null) {
            throw new OrganizationStateException("岗位已更新但无法读取");
        }
        record(operatorId, "UPDATE", "ORG_POSITION", positionId);
        return updated;
    }

    private DepartmentMutationRequest normalize(DepartmentMutationRequest request) {
        if (request == null || !StringUtils.hasText(request.code()) || !CODE.matcher(request.code().trim()).matches()
                || !StringUtils.hasText(request.name()) || request.name().trim().length() > 128
                || request.sortOrder() == null || request.sortOrder() < 0 || request.sortOrder() > 100000
                || request.enabled() == null || request.parentId() != null && request.parentId() <= 0
                || request.managerEmployeeId() != null && request.managerEmployeeId() <= 0) {
            throw new IllegalArgumentException("部门字段格式错误");
        }
        return new DepartmentMutationRequest(request.code().trim().toUpperCase(java.util.Locale.ROOT),
                request.name().trim(), request.parentId(), request.managerEmployeeId(), request.sortOrder(),
                request.enabled());
    }

    private PositionMutationRequest normalize(PositionMutationRequest request) {
        if (request == null || !StringUtils.hasText(request.code()) || !CODE.matcher(request.code().trim()).matches()
                || !StringUtils.hasText(request.name()) || request.name().trim().length() > 128
                || request.enabled() == null) {
            throw new IllegalArgumentException("岗位字段格式错误");
        }
        return new PositionMutationRequest(request.code().trim().toUpperCase(java.util.Locale.ROOT),
                request.name().trim(), request.enabled());
    }

    private void ensureDepartmentCodeAvailable(String code, Long excludeId) {
        if (mapper.departmentCodeExists(code, excludeId)) {
            throw new OrganizationStateException("部门编码已存在");
        }
    }

    private void ensurePositionCodeAvailable(Long departmentId, String code, Long excludeId) {
        if (mapper.positionCodeExists(departmentId, code, excludeId)) {
            throw new OrganizationStateException("该部门岗位编码已存在");
        }
    }

    private void ensureManagerActive(Long employeeId) {
        if (employeeId != null && !mapper.isActiveEmployee(employeeId)) {
            throw new OrganizationStateException("部门负责人必须绑定在职员工");
        }
    }

    private void ensureParentAllowed(Long departmentId, Long parentId) {
        if (parentId == null) {
            return;
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(departmentId) || !visited.add(cursor)) {
                throw new OrganizationStateException("部门层级循环");
            }
            DepartmentAdminView parent = requireDepartment(cursor);
            if (!parent.enabled()) {
                throw new OrganizationStateException("上级部门必须处于启用状态");
            }
            cursor = parent.parentId();
        }
    }

    private DepartmentAdminView requireDepartment(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("departmentId 必须为正数");
        }
        DepartmentAdminView department = mapper.findDepartment(id);
        if (department == null) {
            throw new OrganizationStateException("部门不存在: " + id);
        }
        return department;
    }

    private void record(Long operatorId, String action, String objectType, Long objectId) {
        if (operationLogService != null) {
            operationLogService.record(operatorId, action, objectType, objectId, "SUCCESS");
        }
    }

    private static final class MutableDepartment {
        private final Long id;
        private final String code;
        private final String name;
        private final Long parentId;
        private final List<MutableDepartment> children = new ArrayList<>();

        private MutableDepartment(Long id, String code, String name, Long parentId) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.parentId = parentId;
        }

        private DepartmentNode toImmutable() {
            return new DepartmentNode(id, code, name, parentId, children.stream().map(MutableDepartment::toImmutable).toList());
        }
    }
}
