package com.hrplatform.organization;

import java.util.List;

public record DepartmentNode(Long id, String code, String name, Long parentId, List<DepartmentNode> children) {
}
