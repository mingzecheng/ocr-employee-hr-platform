package com.hrplatform.organization;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationService {
    private final OrganizationMapper mapper;

    public OrganizationService(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

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
