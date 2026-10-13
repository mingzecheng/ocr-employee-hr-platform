# 员工自助注册 Implementation Plan

> **For agentic workers:** Inline execution in this session. Steps use checkbox syntax for tracking.

**Goal:** Add public self-registration for existing active employees and grant only the employee role immediately.

**Architecture:** The auth API matches submitted name and stored phone against exactly one locked active employee row, rejects an existing account, then uses the system-generated employee number as the login username and inserts an enabled account with a BCrypt password and the fixed `EMPLOYEE` role in one transaction. It checks the employee binding after acquiring the employee lock so concurrent registrations cannot both bind accounts to the same profile. The Vue registration page calls this endpoint, stores the returned auth response, and routes the new session to the employee portal.

**Tech Stack:** Spring Boot 3, Spring Security, MyBatis, MySQL, BCrypt, Vue 3, TypeScript, Vue Router, Element Plus.

## Global Constraints

- Public registration must not accept employee ID, department ID, or role codes.
- Only one existing `ACTIVE` employee matching name and stored phone may register; ambiguous matches are rejected.
- HR-created employee records receive a server-generated employee number.
- The generated account is enabled and receives only `EMPLOYEE`.
- Passwords are stored as BCrypt hashes and never logged.
- Do not create a new employee record or migration for registration.

---

### Task 1: Registration API

**Files:**
- Create: `server/src/main/java/com/hrplatform/auth/RegisterRequest.java`
- Modify: `server/src/main/java/com/hrplatform/auth/AuthController.java`
- Modify: `server/src/main/java/com/hrplatform/auth/AuthService.java`
- Modify: `server/src/main/java/com/hrplatform/auth/SysUserMapper.java`
- Modify: `server/src/main/resources/mapper/SysUserMapper.xml`
- Modify: `server/src/main/java/com/hrplatform/common/security/SecurityConfiguration.java`

**Interfaces:**
- `RegisterRequest(String password, String name, String phone)`
- `AuthService.register(RegisterRequest request) -> AuthResponse`
- `SysUserMapper.findActiveEmployeesForRegistration(name, phone) -> List<EmployeeRegistrationBinding>`
- `SysUserMapper.countUsersByEmployeeId(employeeId) -> long`

- [x] Validate username and identity fields; constrain the BCrypt password to 8-72 UTF-8 bytes.
- [x] Lock matching active employee rows with `SELECT ... FOR UPDATE` and reject when no unique row matches or an account is already bound.
- [x] Insert an enabled account with the employee ID and department from the locked row, encode the password, and assign only `EMPLOYEE` in the same transaction.
- [x] Build the same `AuthResponse` used by login and map username conflicts to a client-visible conflict response.
- [x] Permit unauthenticated access to only `POST /api/auth/register` in addition to the existing login and health routes.

### Task 2: Registration UI

**Files:**
- Create: `web/src/views/RegisterView.vue`
- Modify: `web/src/views/LoginView.vue`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/api/http.ts`

**Interfaces:**
- `auth.register(input) -> AuthResponse` stores the returned token and user using the same session keys as login.
- Public route `/register` renders the registration form.

- [x] Add password, confirmation, name, and stored-phone inputs with client-side required/length checks; omit employee number and username.
- [x] Add an `auth.register` action that posts to `/auth/register` and persists the returned session.
- [x] Add `/register` as a public route and a registration link on the existing login page.
- [x] On success, route to the same authorized return URL/employee portal logic as login; on failure show the API message and preserve safe form state.

### Task 3: Documentation and Build Validation

**Files:**
- Modify: `README.md`

- [x] Document the registration fields, the requirement for an existing active employee record, and immediate employee-only access.
- [x] Run `mvn -DskipTests package` in `server` and `npm run build` in `web` to catch compile and bundle errors without executing test suites.
- [x] Review the final diff for accidental changes to credentials, employee data, and unrelated files.

### Task 4: Generated Employee Number

- [x] Remove the employee number from the HR create form and request DTO.
- [x] Generate the employee number in the service and return it in the normal employee response.
