# Platform Administration Implementation Plan

> **For agentic workers:** Follow this plan inline in the current authorized project task. Steps use checkboxes for progress; commits are omitted because the existing user instruction keeps this work uncommitted.

**Goal:** Close the approved P0 gaps for department/position maintenance and administrator-managed account role assignment.

**Architecture:** Extend the existing modular monolith with organization write APIs and a small admin account domain that reuses the current identity tables. Add two Vue management pages, with backend authorization as the enforcement boundary and route/menu checks as user-interface controls.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, MyBatis, MySQL/Flyway, Vue 3, TypeScript, Element Plus, JUnit 5, Vitest.

## Global Constraints

- Preserve the existing `ocr-service/` implementation and its API contract.
- Keep employee and archive data scope enforced on the server from JWT claims.
- Use soft disable for departments, positions, and user accounts; do not delete historical business relations.
- Do not return password hashes, OCR object keys, or unrelated personal data.
- Keep all local changes uncommitted and unpushed.

---

### Task 0: Privacy and Scope Corrections

**Files:**
- Modify `server/src/main/java/com/hrplatform/employee/EmployeeController.java` and add response DTOs/tests under `server/src/main/java/com/hrplatform/employee/` and `server/src/test/java/com/hrplatform/employee/`.
- Modify `server/src/main/java/com/hrplatform/access/ArchiveAccessService.java`, add an expiry scheduler, extend `AccessMapper.java` / `AccessMapper.xml`, and test under `server/src/test/java/com/hrplatform/access/`.
- Modify `server/src/main/java/com/hrplatform/inventory/InventoryService.java`, `InventoryMapper.java`, `InventoryMapper.xml`, and inventory service tests.
- Modify `docs/api-contract.md` and the rebuild spec to state the automatic overdue transition and employee phone masking contract.

**Interfaces:**
- Employee list/detail responses mask phone numbers for employee and department-manager roles; HR/system administrators may read the full business phone.
- Expired in-use archive applications transition to `ABNORMAL` periodically; an abnormal handover never stores a recipient employee.
- Department managers may transition an inventory task only when every task item is inside their department scope.

- [ ] Write failing tests for role-aware phone masking, overdue handover recipient suppression, automatic overdue state transition, and cross-department inventory state-operation denial.
- [ ] Run the focused tests and confirm each fails on the current implementation.
- [ ] Implement response projection, idempotent scheduled expiry with audit, recipient suppression based on effective abnormal state, and whole-task scope checks.
- [ ] Run the focused tests and the complete Java suite.

### Task 1: Organization and Position Write APIs

**Files:**
- Modify `server/src/main/java/com/hrplatform/organization/OrganizationController.java`, `OrganizationService.java`, and `OrganizationMapper.java`.
- Create request records under `server/src/main/java/com/hrplatform/organization/` for department and position mutations.
- Modify `server/src/main/resources/mapper/OrganizationMapper.xml` and `server/src/main/resources/mapper/EmployeeMapper.xml` only where active-option filtering is required.
- Add service and MyBatis persistence tests under `server/src/test/java/com/hrplatform/organization/`.
- Modify `docs/api-contract.md` after endpoints are fixed.

**Interfaces:**
- `POST /api/departments`, `PUT /api/departments/{id}` create/update department.
- `POST /api/departments/{departmentId}/positions`, `PUT /api/departments/{departmentId}/positions/{positionId}` create/update position.
- `GET /api/departments/admin` returns all departments including disabled rows to authorized maintainers.
- HR and system administrators may write; only system administrators may not be required for these operations.

- [ ] Write failing service/persistence tests for duplicate codes, parent cycles, inactive manager binding, disabling a department with active children or employees, and position reassignment across departments.
- [ ] Run focused tests and confirm failures are caused by the missing mutation behavior.
- [ ] Implement bean validation, enabled-option filtering, department hierarchy checks, SQL writes, transactions, and audit events.
- [ ] Run focused tests, then the complete Java suite.

### Task 2: Account and Role Administration APIs

**Files:**
- Create `server/src/main/java/com/hrplatform/auth/AdminUserController.java`, request/view records, service, and mapper methods.
- Modify `SysUserMapper.java` and `server/src/main/resources/mapper/SysUserMapper.xml`.
- Add `server/src/test/java/com/hrplatform/auth/AdminUserServiceTest.java` and a mapper persistence test.
- Modify `server/src/main/resources/mapper/OperationLogMapper.xml` only if event details require an existing supported field.
- Modify `docs/api-contract.md` after the API signatures are fixed.

**Interfaces:**
- `GET /api/admin/users?page=&pageSize=` returns minimal account rows and assigned role codes.
- `GET /api/admin/users/roles` returns enabled role options.
- `POST /api/admin/users` creates a BCrypt-backed account and initial roles.
- `PUT /api/admin/users/{id}` updates employee binding, department, enabled state, optional password, and the complete role set.
- All endpoints require `SYSTEM_ADMIN`.

- [ ] Write failing tests for unauthorized roles, non-active or cross-department employee bindings, invalid role codes, password hashing, role replacement, audit records, and last-enabled-system-admin protection.
- [ ] Run focused tests and verify the expected failures.
- [ ] Implement transactions; lock the `SYSTEM_ADMIN` role row before checking a removal/disable operation; never return `password_hash`.
- [ ] Run focused persistence/security tests, then the complete Java suite.

### Task 3: Vue Management Pages and Access Guards

**Files:**
- Create `web/src/views/OrganizationAdminView.vue` and `web/src/views/AdminUsersView.vue` with focused Vitest files.
- Modify `web/src/api/http.ts`, `web/src/router/index.ts`, and `web/src/layouts/AppLayout.vue`.
- Modify `web/src/styles.css` only for shared styles not already represented by Element Plus.
- Modify `docs/demo-runbook.md` to include the management screens.

**Interfaces:**
- `/organization` is visible to HR and system administrators.
- `/admin/users` is visible only to system administrators.
- Page API methods use the typed `request<T>` wrapper and display loading, empty, success, validation, and API error states.

- [ ] Write failing view tests for role visibility, create/update submissions, empty state, and service failure.
- [ ] Run focused Vitest files and confirm missing routes/views fail for the expected reasons.
- [ ] Implement department tree/list editing, position management, and account/role forms with password confirmation rules.
- [ ] Run all Vitest tests and the production Vue build.

### Task 4: End-to-End Verification and Documentation

**Files:**
- Extend `scripts/verify-platform.sh` with reversible DEMO-prefixed organization/account checks only if they can avoid changing shared baseline records.
- Append findings and verification evidence to `docs/开发验收记录-重构版.md` and `docs/代码审查.md`.
- Update the current rebuild spec with the final administrative scope.

- [ ] Run `git diff --check`, Java 17 Maven tests, Web tests/build, OCR tests, shell syntax checks, and Compose configuration validation.
- [ ] Exercise admin API authorization and ordinary-user denial against the running local stack without resetting existing data.
- [ ] Record actual results, sample limitations, warnings, and unsynchronized local Git state.
