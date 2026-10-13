#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
OCR_BASE_URL="${OCR_BASE_URL:-http://127.0.0.1:8000}"
WEB_URL="${WEB_URL:-http://127.0.0.1:5173}"
LOGIN_USERNAME="${LOGIN_USERNAME:-demo-hr}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password}"
SMOKE_IMAGE="${SMOKE_IMAGE:-$PROJECT_ROOT/ocr-service/tests/fixtures/platform-smoke.png}"

command -v curl >/dev/null || { printf 'curl is required\n' >&2; exit 1; }
command -v jq >/dev/null || { printf 'jq is required\n' >&2; exit 1; }

if [[ "${RUN_OCR_RETRY_SMOKE:-0}" == "1" && "${RUN_OCR_SMOKE:-0}" != "1" ]]; then
  printf 'RUN_OCR_RETRY_SMOKE requires RUN_OCR_SMOKE=1\n' >&2
  exit 1
fi
if [[ "${RUN_EXPIRY_SMOKE:-0}" == "1" && "${RUN_OCR_SMOKE:-0}" != "1" ]]; then
  printf 'RUN_EXPIRY_SMOKE requires RUN_OCR_SMOKE=1\n' >&2
  exit 1
fi

check() {
  local label="$1" url="$2"
  curl --fail --silent --show-error "$url" >/dev/null
  printf '%s: OK\n' "$label"
}

expect_state_conflict_as() {
  local url="$1" payload="$2" bearer_token="$3" response status body
  response="$(curl --silent --show-error -w $'\n%{http_code}' -X POST "$url" \
    -H "Authorization: Bearer $bearer_token" \
    -H 'Content-Type: application/json' -d "$payload")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [[ "$status" == "409" ]] && printf '%s' "$body" | jq -e '.code == "STATE_CONFLICT"' >/dev/null
}

expect_state_conflict() {
  expect_state_conflict_as "$1" "$2" "$token"
}

expect_state_conflict_public() {
  local url="$1" payload="$2" response status body
  response="$(curl --silent --show-error -w $'\n%{http_code}' -X POST "$url" \
    -H 'Content-Type: application/json' -d "$payload")"
  status="$(printf '%s\n' "$response" | tail -n 1)"
  body="$(printf '%s\n' "$response" | sed '$d')"
  [[ "$status" == "409" ]] && printf '%s' "$body" | jq -e '.code == "STATE_CONFLICT"' >/dev/null
}

expect_forbidden_as() {
  local url="$1" bearer_token="$2" response status body
  response="$(curl --silent --show-error -w $'\n%{http_code}' "$url" \
    -H "Authorization: Bearer $bearer_token")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [[ "$status" == "403" ]] && printf '%s' "$body" | jq -e '.code == "FORBIDDEN"' >/dev/null
}

expect_forbidden_post_as() {
  local url="$1" payload="$2" bearer_token="$3" response status body
  response="$(curl --silent --show-error -w $'\n%{http_code}' -X POST "$url" \
    -H "Authorization: Bearer $bearer_token" \
    -H 'Content-Type: application/json' -d "$payload")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [[ "$status" == "403" ]] && printf '%s' "$body" | jq -e '.code == "FORBIDDEN"' >/dev/null
}

assert_todo_target() {
  local response="$1" type="$2" resource_id="$3" target_path="$4"
  printf '%s' "$response" | jq -e --arg type "$type" --arg targetPath "$target_path" \
    --argjson resourceId "$resource_id" \
    '.code == "0" and (.data.items | any(.type == $type and .resourceId == $resourceId and .targetPath == $targetPath))' >/dev/null
}

assert_hr_request_todo_detail() {
  local bearer_token="$1" request_id="$2" request_type="$3" expected_status="$4" todos detail
  todos="$(curl --fail --silent --show-error "$BASE_URL/api/todos?limit=100" \
    -H "Authorization: Bearer $bearer_token")"
  assert_todo_target "$todos" HR_REQUEST_APPROVAL "$request_id" "/todos/hr-request/$request_id"
  detail="$(curl --fail --silent --show-error "$BASE_URL/api/hr-requests/$request_id" \
    -H "Authorization: Bearer $bearer_token")"
  printf '%s' "$detail" | jq -e --argjson requestId "$request_id" \
    --arg requestType "$request_type" --arg status "$expected_status" \
    '.code == "0" and .data.request.id == $requestId and
     .data.request.requestType == $requestType and .data.request.status == $status and
     (.data.approvals | type == "array")' >/dev/null
}

expect_validation_error_as() {
  local url="$1" payload="$2" bearer_token="$3" expected_message="$4" response status body
  response="$(curl --silent --show-error -w $'\n%{http_code}' -X POST "$url" \
    -H "Authorization: Bearer $bearer_token" \
    -H 'Content-Type: application/json' -d "$payload")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [[ "$status" == "400" ]] && printf '%s' "$body" | jq -e --arg message "$expected_message" \
    '.code == "VALIDATION_ERROR" and (.message | contains($message))' >/dev/null
}

date_after_seconds() {
  local seconds="$1"
  if date -v+1S '+%Y-%m-%dT%H:%M:%S' >/dev/null 2>&1; then
    date -v+"${seconds}"S '+%Y-%m-%dT%H:%M:%S'
  else
    date -d "+${seconds} seconds" '+%Y-%m-%dT%H:%M:%S'
  fi
}

check "server health" "$BASE_URL/actuator/health"
check "web health" "$WEB_URL"
if [[ "${SKIP_OCR_HEALTH:-0}" != "1" ]]; then
  check "ocr health" "$OCR_BASE_URL/health"
fi

login_response="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg username "$LOGIN_USERNAME" --arg password "$LOGIN_PASSWORD" \
    '{username: $username, password: $password}')")"
token="$(printf '%s' "$login_response" | jq -er '.data.accessToken')"
printf 'login (%s): OK\n' "$LOGIN_USERNAME"

auth_header=( -H "Authorization: Bearer $token" )
me_response="$(curl --fail --silent --show-error "$BASE_URL/api/auth/me" "${auth_header[@]}")"
printf '%s' "$me_response" | jq -e '.code == "0" and (.data.userId | type == "number")' >/dev/null
printf 'current user: OK\n'

departments="$(curl --fail --silent --show-error "$BASE_URL/api/departments/tree" "${auth_header[@]}")"
printf '%s' "$departments" | jq -e '.code == "0" and (.data | type == "array")' >/dev/null
printf 'department tree: OK\n'

employees="$(curl --fail --silent --show-error "$BASE_URL/api/employees?page=1&pageSize=20" "${auth_header[@]}")"
printf '%s' "$employees" | jq -e '.code == "0" and (.data.items | type == "array") and (.data.total | type == "number")' >/dev/null
printf 'employee pagination: OK\n'

statistics="$(curl --fail --silent --show-error "$BASE_URL/api/statistics/overview" "${auth_header[@]}")"
printf '%s' "$statistics" | jq -e '.code == "0" and (.data.employeeCount | type == "number") and (.data.archiveCompleteness | type == "number")' >/dev/null
printf 'statistics overview: OK\n'

audit="$(curl --fail --silent --show-error "$BASE_URL/api/operation-logs?page=1&pageSize=5" "${auth_header[@]}")"
printf '%s' "$audit" | jq -e '.code == "0" and (.data.items | type == "array")' >/dev/null
printf 'audit pagination: OK\n'

if [[ "${RUN_ADMIN_SMOKE:-0}" == "1" ]]; then
  admin_login="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"demo-admin","password":"password"}')"
  admin_token="$(printf '%s' "$admin_login" | jq -er '.data.accessToken')"
  admin_auth_header=( -H "Authorization: Bearer $admin_token" )
  admin_users="$(curl --fail --silent --show-error "$BASE_URL/api/admin/users?page=1&pageSize=20" "${admin_auth_header[@]}")"
  printf '%s' "$admin_users" | jq -e '.code == "0" and (.data.items | type == "array")' >/dev/null
  printf 'system administrator account list: OK\n'

  hr_login="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"demo-hr","password":"password"}')"
  hr_token="$(printf '%s' "$hr_login" | jq -er '.data.accessToken')"
  expect_forbidden_as "$BASE_URL/api/admin/users" "$hr_token"
  printf 'HR account denied from account administration: OK\n'

  employee_login="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"demo-employee","password":"password"}')"
  employee_token="$(printf '%s' "$employee_login" | jq -er '.data.accessToken')"
  expect_forbidden_as "$BASE_URL/api/admin/users" "$employee_token"
  printf 'ordinary account denied from account administration: OK\n'

  admin_departments="$(curl --fail --silent --show-error "$BASE_URL/api/departments/admin" "${admin_auth_header[@]}")"
  printf '%s' "$admin_departments" | jq -e '.code == "0" and (.data | type == "array")' >/dev/null
  printf 'system administrator organization list: OK\n'

  verify_suffix="$(date +%s)-$RANDOM"
  verify_department_code="DEMO-VERIFY-$verify_suffix"
  verify_department="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/departments" "${admin_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg code "$verify_department_code" \
      '{code:$code,name:"脱敏验收部门",parentId:null,managerEmployeeId:null,sortOrder:900,enabled:true}')")"
  verify_department_id="$(printf '%s' "$verify_department" | jq -er '.data.id')"
  printf '%s' "$verify_department" | jq -e --arg code "$verify_department_code" \
    '.code == "0" and .data.code == $code and .data.enabled == true' >/dev/null
  verify_position_code="DEMO-POS-$verify_suffix"
  verify_position="$(curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/departments/$verify_department_id/positions" "${admin_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg code "$verify_position_code" \
      '{code:$code,name:"脱敏验收岗位",enabled:true}')")"
  printf '%s' "$verify_position" | jq -e --arg code "$verify_position_code" \
    '.code == "0" and .data.code == $code and .data.departmentId > 0' >/dev/null
  printf 'DEMO organization and position creation: OK\n'

  verify_username="DEMO-VERIFY-ADMIN-$verify_suffix"
  verify_password="DemoPass-$verify_suffix"
  verify_account="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/admin/users" "${admin_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg username "$verify_username" --arg password "$verify_password" \
      '{username:$username,password:$password,employeeId:null,departmentId:null,enabled:true,roleCodes:["SYSTEM_ADMIN"]}')")"
  printf '%s' "$verify_account" | jq -e --arg username "$verify_username" \
    '.code == "0" and .data.username == $username and (.data.roleCodes | index("SYSTEM_ADMIN")) != null and .data.passwordHash == null' >/dev/null
  printf 'DEMO system administrator account creation: OK\n'
fi

if [[ "${RUN_OCR_SMOKE:-0}" == "1" ]]; then
  [[ -f "$SMOKE_IMAGE" ]] || { printf 'smoke image not found: %s\n' "$SMOKE_IMAGE" >&2; exit 1; }
  department_id="$(printf '%s' "$departments" | jq -er '.data[0].id')"
  position_id="${POSITION_ID:?POSITION_ID is required when RUN_OCR_SMOKE=1}"
  phone_epoch="$(date +%s)"
  printf -v phone_suffix '%08d' "$((phone_epoch % 100000000))"
  smoke_employee_name="脱敏验收员工-${phone_suffix}"
  smoke_employee_phone="139${phone_suffix}"
  employee="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/employees" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg name "$smoke_employee_name" --arg phone "$smoke_employee_phone" \
      --argjson departmentId "$department_id" --argjson positionId "$position_id" \
      '{name:$name,phone:$phone,departmentId:$departmentId,positionId:$positionId,status:"ACTIVE"}')")"
  employee_id="$(printf '%s' "$employee" | jq -er '.data.id')"
  employee_no="$(printf '%s' "$employee" | jq -er '.data.employeeNo')"
  registration="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg name "$smoke_employee_name" --arg phone "$smoke_employee_phone" \
      '{name:$name,phone:$phone,password:"VerifyPass-2026"}')")"
  printf '%s' "$registration" | jq -e --arg username "$employee_no" --argjson employeeId "$employee_id" \
    '.code == "0" and .data.user.username == $username and .data.user.employeeId == $employeeId and .data.user.roles == ["EMPLOYEE"]' >/dev/null
  employee_token="$(printf '%s' "$registration" | jq -er '.data.accessToken')"
  employee_auth_header=( -H "Authorization: Bearer $employee_token" )
  employee_me="$(curl --fail --silent --show-error "$BASE_URL/api/auth/me" "${employee_auth_header[@]}")"
  printf '%s' "$employee_me" | jq -e --argjson employeeId "$employee_id" \
    '.data.employeeId == $employeeId and (.data.roles | length) == 1 and (.data.roles | index("EMPLOYEE")) != null' >/dev/null
  duplicate_registration="$(jq -nc --arg name "$smoke_employee_name" --arg phone "$smoke_employee_phone" \
    '{name:$name,phone:$phone,password:"VerifyPass-2026"}')"
  expect_state_conflict_public "$BASE_URL/api/auth/register" "$duplicate_registration"
  printf 'employee registration and generated login account: OK\n'
  printf 'duplicate employee registration guard: OK\n'
  upload="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive/employees/$employee_id/documents" "${auth_header[@]}" \
    -F documentType=employee_profile -F "file=@$SMOKE_IMAGE")"
  version_id="$(printf '%s' "$upload" | jq -er '.data.id')"
  ocr="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive/versions/$version_id/ocr" "${auth_header[@]}")"
  if [[ "${RUN_OCR_RETRY_SMOKE:-0}" == "1" ]]; then
    printf '%s' "$ocr" | jq -e '.code == "0" and (.data.bindingId | type == "number") and .data.status == "FAILED" and .data.errorCode == "OCR_UNAVAILABLE"' >/dev/null
    binding_id="$(printf '%s' "$ocr" | jq -er '.data.bindingId')"
    failed_result="$(curl --fail --silent --show-error "$BASE_URL/api/archive/versions/$version_id/ocr-result" "${auth_header[@]}")"
    printf '%s' "$failed_result" | jq -e '.data.status == "FAILED" and (.data.fields | type == "array") and (.data.textBlocks | type == "array") and (.data.revisions | type == "array")' >/dev/null
    ocr_todos="$(curl --fail --silent --show-error "$BASE_URL/api/todos?limit=100" "${auth_header[@]}")"
    assert_todo_target "$ocr_todos" OCR_FAILED "$version_id" "/todos/ocr/$version_id"
    ocr="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive/versions/$version_id/ocr" "${auth_header[@]}")"
    printf '%s' "$ocr" | jq -e --argjson bindingId "$binding_id" \
      '.code == "0" and .data.status == "SUCCEEDED" and .data.bindingId == $bindingId and (.data.taskId | type == "string" and length > 0)' >/dev/null
    printf 'OCR failure todo and same-version retry: OK\n'
  else
    printf '%s' "$ocr" | jq -e '.code == "0" and (.data.bindingId | type == "number") and (.data.taskId | type == "string" and length > 0) and .data.status == "SUCCEEDED"' >/dev/null
  fi
  binding_id="$(printf '%s' "$ocr" | jq -er '.data.bindingId')"
  result="$(curl --fail --silent --show-error "$BASE_URL/api/archive/versions/$version_id/ocr-result" "${auth_header[@]}")"
  printf '%s' "$result" | jq -e '.data.status == "SUCCEEDED" and (.data.fields | type == "array") and (.data.textBlocks | type == "array") and (.data.revisions | type == "array")' >/dev/null
  preview_stats="$(curl --fail --silent --show-error -o /dev/null -w '%{http_code} %{size_download} %{content_type}' \
    "$BASE_URL/api/archive/ocr-bindings/$binding_id/preview" "${auth_header[@]}")"
  read -r preview_status preview_size preview_type <<< "$preview_stats"
  [[ "$preview_status" == "200" && "$preview_size" -gt 0 && "$preview_type" == "image/png" ]]
  download_stats="$(curl --fail --silent --show-error -o /dev/null -w '%{http_code} %{size_download}' \
    "$BASE_URL/api/archive/versions/$version_id/download" "${auth_header[@]}")"
  read -r download_status download_size <<< "$download_stats"
  [[ "$download_status" == "200" && "$download_size" -gt 0 ]]
  revision="$(curl --fail --silent --show-error -X PUT \
    "$BASE_URL/api/archive/ocr-bindings/$binding_id/fields/employee_id" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"value":"EMP-VERIFY-001","reason":"验收人工修正"}')"
  printf '%s' "$revision" | jq -e \
    '.data.fields | any(.fieldCode == "employee_id" and .value == "EMP-VERIFY-001" and .validationStatus == "PASSED")' >/dev/null
  printf '%s' "$revision" | jq -e \
    '.data.revisions | any(.fieldCode == "employee_id" and .reason == "验收人工修正")' >/dev/null
  printf 'archive upload, OCR evidence, preview, download, and field revision: OK\n'

  access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access" "${employee_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson versionId "$version_id" \
      '{purpose:"验收档案借用",useType:"READ",dueAt:"2099-01-01T00:00:00",versionIds:[$versionId]}')")"
  access_id="$(printf '%s' "$access" | jq -er '.data.id')"
  access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$access_id/submit" "${employee_auth_header[@]}")"
  printf '%s' "$access" | jq -e '.data.status == "PENDING_APPROVAL"' >/dev/null
  access_todos="$(curl --fail --silent --show-error "$BASE_URL/api/todos?limit=100" "${auth_header[@]}")"
  assert_todo_target "$access_todos" ARCHIVE_ACCESS_APPROVAL "$access_id" "/todos/archive-access/$access_id"
  access_detail="$(curl --fail --silent --show-error "$BASE_URL/api/archive-access/$access_id" "${auth_header[@]}")"
  printf '%s' "$access_detail" | jq -e --argjson applicationId "$access_id" --argjson versionId "$version_id" \
    '.data.application.id == $applicationId and .data.application.status == "PENDING_APPROVAL" and any(.data.items[]; .versionId == $versionId)' >/dev/null
  expect_forbidden_post_as "$BASE_URL/api/archive-access/$access_id/approve" '{}' "$employee_token"
  access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$access_id/approve" "${auth_header[@]}")"
  printf '%s' "$access" | jq -e '.data.status == "APPROVED"' >/dev/null
  access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$access_id/use" "${employee_auth_header[@]}")"
  printf '%s' "$access" | jq -e '.data.status == "IN_USE"' >/dev/null
  access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$access_id/return" "${employee_auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"abnormal":false,"remark":"验收归还"}')"
  printf '%s' "$access" | jq -e '.data.status == "RETURNED"' >/dev/null
  printf 'employee archive request, HR todo approval, use, and return: OK\n'

  if [[ "${RUN_EXPIRY_SMOKE:-0}" == "1" ]]; then
    expiry_due_at="$(date_after_seconds 8)"
    expiry_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access" "${employee_auth_header[@]}" \
      -H 'Content-Type: application/json' \
      -d "$(jq -nc --argjson versionId "$version_id" --arg dueAt "$expiry_due_at" \
        '{purpose:"验收未开始使用的授权过期",useType:"READ",dueAt:$dueAt,versionIds:[$versionId]}')")"
    expiry_access_id="$(printf '%s' "$expiry_access" | jq -er '.data.id')"
    curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$expiry_access_id/submit" "${employee_auth_header[@]}" >/dev/null
    curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$expiry_access_id/approve" "${auth_header[@]}" >/dev/null
    expiry_status=""
    for attempt in $(seq 1 90); do
      expiry_detail="$(curl --fail --silent --show-error "$BASE_URL/api/archive-access/$expiry_access_id" "${auth_header[@]}")"
      expiry_status="$(printf '%s' "$expiry_detail" | jq -er '.data.application.status')"
      [[ "$expiry_status" == "EXPIRED" ]] && break
      sleep 1
    done
    [[ "$expiry_status" == "EXPIRED" ]]
    expect_state_conflict_as "$BASE_URL/api/archive-access/$expiry_access_id/use" '{}' "$employee_token"
    printf 'approved archive authorization expires without use and blocks start: OK\n'
  fi

  abnormal_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access" "${employee_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson versionId "$version_id" \
      '{purpose:"验收异常归还",useType:"READ",dueAt:"2099-01-01T00:00:00",versionIds:[$versionId]}')")"
  abnormal_access_id="$(printf '%s' "$abnormal_access" | jq -er '.data.id')"
  abnormal_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$abnormal_access_id/submit" "${employee_auth_header[@]}")"
  printf '%s' "$abnormal_access" | jq -e '.data.status == "PENDING_APPROVAL"' >/dev/null
  abnormal_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$abnormal_access_id/approve" "${auth_header[@]}")"
  printf '%s' "$abnormal_access" | jq -e '.data.status == "APPROVED"' >/dev/null
  abnormal_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$abnormal_access_id/use" "${employee_auth_header[@]}")"
  printf '%s' "$abnormal_access" | jq -e '.data.status == "IN_USE"' >/dev/null
  abnormal_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$abnormal_access_id/return" "${employee_auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"abnormal":true,"remark":"验收异常：材料待补齐"}')"
  printf '%s' "$abnormal_access" | jq -e '.data.status == "ABNORMAL"' >/dev/null
  abnormal_detail="$(curl --fail --silent --show-error "$BASE_URL/api/archive-access/$abnormal_access_id" "${auth_header[@]}")"
  printf '%s' "$abnormal_detail" | jq -e \
    '.data.application.status == "ABNORMAL" and .data.uses[0].returnStatus == "ABNORMAL" and (.data.uses[0].returnedAt | type == "string")' >/dev/null
  abnormal_todos="$(curl --fail --silent --show-error "$BASE_URL/api/todos?limit=100" "${auth_header[@]}")"
  assert_todo_target "$abnormal_todos" ARCHIVE_RETURN_DUE "$abnormal_access_id" "/todos/archive-return/$abnormal_access_id"
  expect_state_conflict_as "$BASE_URL/api/archive-access/$abnormal_access_id/return" '{"abnormal":true,"remark":"重复归还"}' "$employee_token"
  abnormal_access="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$abnormal_access_id/resolve" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"remark":"验收异常已补齐材料并复核"}')"
  printf '%s' "$abnormal_access" | jq -e '.data.status == "RETURNED"' >/dev/null
  printf 'archive abnormal return, duplicate-action guard, and HR closure: OK\n'

  handover="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access" "${employee_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson versionId "$version_id" \
      '{purpose:"验收档案移交",useType:"HANDOVER",dueAt:"2099-01-01T00:00:00",versionIds:[$versionId]}')")"
  handover_id="$(printf '%s' "$handover" | jq -er '.data.id')"
  handover="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$handover_id/submit" "${employee_auth_header[@]}")"
  printf '%s' "$handover" | jq -e '.data.status == "PENDING_APPROVAL"' >/dev/null
  handover="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$handover_id/approve" "${auth_header[@]}")"
  printf '%s' "$handover" | jq -e '.data.status == "APPROVED"' >/dev/null
  handover="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$handover_id/use" "${employee_auth_header[@]}")"
  printf '%s' "$handover" | jq -e '.data.status == "IN_USE"' >/dev/null
  hr_employee_id="$(printf '%s' "$me_response" | jq -er '.data.employeeId')"
  handover="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive-access/$handover_id/return" "${employee_auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$hr_employee_id" \
      '{abnormal:false,remark:"验收移交",handoverToEmployeeId:$employeeId}')")"
  printf '%s' "$handover" | jq -e '.data.status == "RETURNED"' >/dev/null
  handover_detail="$(curl --fail --silent --show-error "$BASE_URL/api/archive-access/$handover_id" "${auth_header[@]}")"
  printf '%s' "$handover_detail" | jq -e --argjson employeeId "$hr_employee_id" \
    --argjson operatorId "$(printf '%s' "$employee_me" | jq -er '.data.userId')" \
    '.code == "0" and .data.items[0].versionId > 0 and
     .data.uses[0].returnStatus == "HANDED_OVER" and
     .data.uses[0].returnedByUserId == $operatorId and
     .data.uses[0].handoverToEmployeeId == $employeeId' >/dev/null
  printf 'archive handover recipient and custody history: OK\n'

  inventory="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/inventory-tasks" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d "$(jq -nc --argjson versionId "$version_id" '{versionIds:[$versionId]}')")"
  inventory_id="$(printf '%s' "$inventory" | jq -er '.data.id')"
  inventory_page="$(curl --fail --silent --show-error \
    "$BASE_URL/api/inventory-tasks?page=1&pageSize=10" "${auth_header[@]}")"
  printf '%s' "$inventory_page" | jq -e --argjson inventoryId "$inventory_id" \
    '.code == "0" and (.data.items | any(.id == $inventoryId)) and .data.total >= 1' >/dev/null
  printf 'inventory task pagination: OK\n'
  inventory="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/inventory-tasks/$inventory_id/start" "${auth_header[@]}")"
  printf '%s' "$inventory" | jq -e '.data.status == "IN_PROGRESS"' >/dev/null
  items="$(curl --fail --silent --show-error "$BASE_URL/api/inventory-tasks/$inventory_id/items" "${auth_header[@]}")"
  item_id="$(printf '%s' "$items" | jq -er '.data[0].id')"
  printf '%s' "$items" | jq -e --argjson versionId "$version_id" \
    'any(.data[]; .expectedVersionId == $versionId and (.expectedVersionNo | type == "number" and . > 0))' >/dev/null
  printf 'inventory expected version number: OK\n'
  version_options="$(curl --fail --silent --show-error \
    "$BASE_URL/api/inventory-tasks/$inventory_id/items/$item_id/versions" "${auth_header[@]}")"
  printf '%s' "$version_options" | jq -e --argjson expectedVersionId "$version_id" \
    '.code == "0" and any(.data[]; .id == $expectedVersionId)' >/dev/null
  checked="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/inventory-tasks/$inventory_id/items/$item_id/check" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"actualStatus":"MISSING","note":"验收异常：现场未找到原件"}')"
  printf '%s' "$checked" | jq -e '.data.differenceType == "MISSING" and .data.resolutionStatus == "OPEN"' >/dev/null
  expect_state_conflict "$BASE_URL/api/inventory-tasks/$inventory_id/complete" '{}'
  printf 'inventory completion blocked with open difference: OK\n'
  resolved="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/inventory-tasks/$inventory_id/items/$item_id/resolve" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"resolution":"验收异常已补件并完成复核"}')"
  printf '%s' "$resolved" | jq -e '.data.resolutionStatus == "RESOLVED" and (.data.resolution | length > 0)' >/dev/null
  inventory="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/inventory-tasks/$inventory_id/complete" "${auth_header[@]}")"
  printf '%s' "$inventory" | jq -e '.data.status == "COMPLETED"' >/dev/null
  printf 'inventory difference resolution and completion: OK\n'
fi

if [[ "${RUN_HR_WORKFLOW_SMOKE:-0}" == "1" ]]; then
  printf '%s' "$me_response" | jq -e '.data.roles | index("HR_ADMIN") != null' >/dev/null \
    || { printf 'HR workflow smoke requires an HR_ADMIN login\n' >&2; exit 1; }

  today="$(date +%F)"
  hr_department_id="$(printf '%s' "$departments" | jq -er '.data[0].id')"
  hr_position_id="$(curl --fail --silent --show-error "$BASE_URL/api/departments/$hr_department_id/positions" "${auth_header[@]}" \
    | jq -er '.data[0].id')"
  target_department_id="$(printf '%s' "$departments" \
    | jq -er --argjson current "$hr_department_id" '.data | map(select(.id != $current)) | first | .id')"
  target_position_id="$(curl --fail --silent --show-error "$BASE_URL/api/departments/$target_department_id/positions" "${auth_header[@]}" \
    | jq -er '.data[0].id')"
  workflow_employee="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/employees" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson departmentId "$target_department_id" \
      --argjson positionId "$target_position_id" \
      '{name:"脱敏流程验收员工",departmentId:$departmentId,positionId:$positionId,status:"PENDING_ONBOARD"}')")"
  workflow_employee_id="$(printf '%s' "$workflow_employee" | jq -er '.data.id')"
  manager_login="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"demo-manager","password":"password"}')"
  manager_token="$(printf '%s' "$manager_login" | jq -er '.data.accessToken')"

  onboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --argjson departmentId "$target_department_id" \
      --argjson positionId "$target_position_id" --arg effectiveDate "$today" \
      '{requestType:"ONBOARDING",employeeId:$employeeId,targetDepartmentId:$departmentId,targetPositionId:$positionId,effectiveDate:$effectiveDate,contractType:"FIXED_TERM"}')")"
  onboarding_id="$(printf '%s' "$onboarding" | jq -er '.data.id')"
  printf '%s' "$onboarding" | jq -e '.data.status == "DRAFT"' >/dev/null
  expect_state_conflict "$BASE_URL/api/hr-requests/$onboarding_id/approve" '{}'
  onboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$onboarding_id/submit" "${auth_header[@]}")"
  printf '%s' "$onboarding" | jq -e '.data.status == "PENDING_DEPT_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$manager_token" "$onboarding_id" ONBOARDING PENDING_DEPT_APPROVAL
  expect_state_conflict_as "$BASE_URL/api/hr-requests/$onboarding_id/approve" '{}' "$token"
  onboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$onboarding_id/approve" \
    -H "Authorization: Bearer $manager_token" -H 'Content-Type: application/json' \
    -d '{"comment":"流程验收部门审批"}')"
  printf '%s' "$onboarding" | jq -e '.data.status == "PENDING_HR_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$token" "$onboarding_id" ONBOARDING PENDING_HR_APPROVAL
  onboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$onboarding_id/approve" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"comment":"流程验收人事复核"}')"
  printf '%s' "$onboarding" | jq -e '.data.status == "APPROVED" and (.data.employeeChangeAppliedAt | type == "string")' >/dev/null
  employee_state="$(curl --fail --silent --show-error "$BASE_URL/api/employees/$workflow_employee_id" "${auth_header[@]}")"
  printf '%s' "$employee_state" | jq -e '.data.status == "ACTIVE"' >/dev/null
  printf 'onboarding, two-step approval, and invalid-state guard: OK\n'

  cancelled_draft="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --arg startDate "$today" \
      --arg endDate "$today" '{requestType:"LEAVE",employeeId:$employeeId,leaveType:"ANNUAL",leaveStartDate:$startDate,leaveEndDate:$endDate,reason:"脱敏验收草稿取消"}')")"
  cancelled_draft_id="$(printf '%s' "$cancelled_draft" | jq -er '.data.id')"
  cancelled_draft="$(curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/hr-requests/$cancelled_draft_id/cancel" "${auth_header[@]}")"
  printf '%s' "$cancelled_draft" | jq -e '.data.status == "CANCELLED"' >/dev/null
  expect_state_conflict "$BASE_URL/api/hr-requests/$cancelled_draft_id/submit" '{}'
  printf 'draft cancellation and resubmission guard: OK\n'

  dept_rejection="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --argjson departmentId "$hr_department_id" \
      --argjson positionId "$hr_position_id" --arg effectiveDate "$today" \
      '{requestType:"TRANSFER",employeeId:$employeeId,targetDepartmentId:$departmentId,targetPositionId:$positionId,effectiveDate:$effectiveDate,reason:"脱敏验收部门驳回"}')")"
  dept_rejection_id="$(printf '%s' "$dept_rejection" | jq -er '.data.id')"
  curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/hr-requests/$dept_rejection_id/submit" "${auth_header[@]}" >/dev/null
  assert_hr_request_todo_detail "$manager_token" "$dept_rejection_id" TRANSFER PENDING_DEPT_APPROVAL
  dept_rejection="$(curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/hr-requests/$dept_rejection_id/reject" \
    -H "Authorization: Bearer $manager_token" -H 'Content-Type: application/json' \
    -d '{"comment":"流程验收部门驳回原因"}')"
  printf '%s' "$dept_rejection" | jq -e '.data.status == "REJECTED"' >/dev/null
  dept_rejection_detail="$(curl --fail --silent --show-error \
    "$BASE_URL/api/hr-requests/$dept_rejection_id" "${auth_header[@]}")"
  printf '%s' "$dept_rejection_detail" | jq -e \
    'any(.data.approvals[]; .decision == "REJECTED" and .comment == "流程验收部门驳回原因")' >/dev/null
  printf 'department rejection and approval history: OK\n'

  contract="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/contracts" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --arg signedOn "$today" \
      --arg startsOn "$today" --arg endsOn "$today" \
      '{employeeId:$employeeId,contractType:"LABOR",signedOn:$signedOn,startsOn:$startsOn,endsOn:$endsOn,notes:"脱敏验收合同"}')")"
  contract_id="$(printf '%s' "$contract" | jq -er '.data.id')"
  printf '%s' "$contract" | jq -e '.data.renewalStatus == "DUE"' >/dev/null
  renewal_payload="$(jq -nc --argjson employeeId "$workflow_employee_id" \
    --argjson renewedFromContractId "$contract_id" --arg signedOn "$today" --arg startsOn "$today" \
    '{employeeId:$employeeId,contractType:"LABOR",signedOn:$signedOn,startsOn:$startsOn,endsOn:"2099-01-01",renewedFromContractId:$renewedFromContractId,notes:"脱敏验收续签合同"}')"
  renewed_contract="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/contracts" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d "$renewal_payload")"
  printf '%s' "$renewed_contract" | jq -e --argjson sourceId "$contract_id" \
    '.data.renewedFromContractId == $sourceId and .data.status == "ACTIVE"' >/dev/null
  contracts="$(curl --fail --silent --show-error \
    "$BASE_URL/api/contracts?employeeId=$workflow_employee_id&page=1&pageSize=100" "${auth_header[@]}")"
  printf '%s' "$contracts" | jq -e --argjson sourceId "$contract_id" \
    'any(.data.items[]; .id == $sourceId and .renewalStatus == "RENEWED") and
     any(.data.items[]; .renewedFromContractId == $sourceId)' >/dev/null
  expect_validation_error_as "$BASE_URL/api/contracts" "$renewal_payload" "$token" '该合同已完成续签决定'
  printf 'contract renewal, source linkage, and duplicate-renewal guard: OK\n'

  no_renewal="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/contracts" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --arg signedOn "$today" \
      --arg startsOn "$today" --arg endsOn "$today" \
      '{employeeId:$employeeId,contractType:"INTERNSHIP",signedOn:$signedOn,startsOn:$startsOn,endsOn:$endsOn,notes:"脱敏验收不续签合同"}')")"
  no_renewal_id="$(printf '%s' "$no_renewal" | jq -er '.data.id')"
  no_renewal="$(curl --fail --silent --show-error -X PUT \
    "$BASE_URL/api/contracts/$no_renewal_id/renewal-status" "${auth_header[@]}")"
  printf '%s' "$no_renewal" | jq -e '.data.renewalStatus == "NOT_RENEWING"' >/dev/null
  printf 'contract register and non-renewal decision: OK\n'

  transfer="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --argjson departmentId "$hr_department_id" \
      --argjson positionId "$hr_position_id" --arg effectiveDate "$today" \
      '{requestType:"TRANSFER",employeeId:$employeeId,targetDepartmentId:$departmentId,targetPositionId:$positionId,effectiveDate:$effectiveDate,reason:"脱敏验收岗位调整"}')")"
  transfer_id="$(printf '%s' "$transfer" | jq -er '.data.id')"
  transfer="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$transfer_id/submit" "${auth_header[@]}")"
  printf '%s' "$transfer" | jq -e '.data.status == "PENDING_DEPT_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$manager_token" "$transfer_id" TRANSFER PENDING_DEPT_APPROVAL
  expect_state_conflict_as "$BASE_URL/api/hr-requests/$transfer_id/approve" '{}' "$token"
  transfer="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$transfer_id/approve" \
    -H "Authorization: Bearer $manager_token" -H 'Content-Type: application/json' \
    -d '{"comment":"流程验收部门审批"}')"
  printf '%s' "$transfer" | jq -e '.data.status == "PENDING_HR_APPROVAL"' >/dev/null
  transfer="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$transfer_id/approve" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"comment":"流程验收人事复核"}')"
  printf '%s' "$transfer" | jq -e '.data.status == "APPROVED" and (.data.employeeChangeAppliedAt | type == "string")' >/dev/null
  employee_state="$(curl --fail --silent --show-error "$BASE_URL/api/employees/$workflow_employee_id" "${auth_header[@]}")"
  printf '%s' "$employee_state" | jq -e --argjson departmentId "$hr_department_id" \
    --argjson positionId "$hr_position_id" \
    '.data.status == "ACTIVE" and .data.departmentId == $departmentId and .data.positionId == $positionId' >/dev/null
  printf 'transfer, source-department authorization, and employee update: OK\n'

  hr_manager_login="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"demo-hr-manager","password":"password"}')"
  hr_manager_token="$(printf '%s' "$hr_manager_login" | jq -er '.data.accessToken')"

  leave="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --arg startDate "$today" \
      --arg endDate "$today" '{requestType:"LEAVE",employeeId:$employeeId,leaveType:"ANNUAL",leaveStartDate:$startDate,leaveEndDate:$endDate,reason:"脱敏验收请假"}')")"
  leave_id="$(printf '%s' "$leave" | jq -er '.data.id')"
  leave="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$leave_id/submit" "${auth_header[@]}")"
  printf '%s' "$leave" | jq -e '.data.status == "PENDING_DEPT_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$hr_manager_token" "$leave_id" LEAVE PENDING_DEPT_APPROVAL
  leave="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$leave_id/approve" \
    -H "Authorization: Bearer $hr_manager_token" -H 'Content-Type: application/json' \
    -d '{"comment":"流程验收部门审批"}')"
  printf '%s' "$leave" | jq -e '.data.status == "PENDING_HR_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$token" "$leave_id" LEAVE PENDING_HR_APPROVAL
  leave="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$leave_id/approve" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"comment":"流程验收人事复核"}')"
  printf '%s' "$leave" | jq -e '.data.status == "APPROVED"' >/dev/null
  printf 'leave request and department-scoped approval: OK\n'

  hr_rejection="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --arg startDate "$today" \
      --arg endDate "$today" '{requestType:"LEAVE",employeeId:$employeeId,leaveType:"ANNUAL",leaveStartDate:$startDate,leaveEndDate:$endDate,reason:"脱敏验收人事驳回"}')")"
  hr_rejection_id="$(printf '%s' "$hr_rejection" | jq -er '.data.id')"
  curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/hr-requests/$hr_rejection_id/submit" "${auth_header[@]}" >/dev/null
  assert_hr_request_todo_detail "$hr_manager_token" "$hr_rejection_id" LEAVE PENDING_DEPT_APPROVAL
  curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/hr-requests/$hr_rejection_id/approve" \
    -H "Authorization: Bearer $hr_manager_token" -H 'Content-Type: application/json' \
    -d '{"comment":"流程验收部门审批"}' >/dev/null
  assert_hr_request_todo_detail "$token" "$hr_rejection_id" LEAVE PENDING_HR_APPROVAL
  hr_rejection="$(curl --fail --silent --show-error -X POST \
    "$BASE_URL/api/hr-requests/$hr_rejection_id/reject" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"comment":"流程验收人事驳回原因"}')"
  printf '%s' "$hr_rejection" | jq -e '.data.status == "REJECTED"' >/dev/null
  hr_rejection_detail="$(curl --fail --silent --show-error \
    "$BASE_URL/api/hr-requests/$hr_rejection_id" "${auth_header[@]}")"
  printf '%s' "$hr_rejection_detail" | jq -e \
    'any(.data.approvals[]; .decision == "REJECTED" and .comment == "流程验收人事驳回原因")' >/dev/null
  printf 'HR rejection and approval history: OK\n'

  offboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson employeeId "$workflow_employee_id" --arg leaveDate "$today" \
      '{requestType:"OFFBOARDING",employeeId:$employeeId,leaveDate:$leaveDate,handoverPerson:"脱敏验收交接人",reason:"脱敏验收离职"}')")"
  offboarding_id="$(printf '%s' "$offboarding" | jq -er '.data.id')"
  offboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$offboarding_id/submit" "${auth_header[@]}")"
  printf '%s' "$offboarding" | jq -e '.data.status == "PENDING_DEPT_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$hr_manager_token" "$offboarding_id" OFFBOARDING PENDING_DEPT_APPROVAL
  offboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$offboarding_id/approve" \
    -H "Authorization: Bearer $hr_manager_token" -H 'Content-Type: application/json' \
    -d '{"comment":"流程验收部门审批"}')"
  printf '%s' "$offboarding" | jq -e '.data.status == "PENDING_HR_APPROVAL"' >/dev/null
  assert_hr_request_todo_detail "$token" "$offboarding_id" OFFBOARDING PENDING_HR_APPROVAL
  expect_state_conflict "$BASE_URL/api/hr-requests/$offboarding_id/approve" '{}'
  offboarding="$(curl --fail --silent --show-error -X PUT "$BASE_URL/api/hr-requests/$offboarding_id/handover" "${auth_header[@]}")"
  printf '%s' "$offboarding" | jq -e '.data.handoverStatus == "COMPLETED"' >/dev/null
  offboarding="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/hr-requests/$offboarding_id/approve" "${auth_header[@]}" \
    -H 'Content-Type: application/json' -d '{"comment":"流程验收人事复核"}')"
  printf '%s' "$offboarding" | jq -e '.data.status == "APPROVED" and (.data.employeeChangeAppliedAt | type == "string")' >/dev/null
  employee_state="$(curl --fail --silent --show-error "$BASE_URL/api/employees/$workflow_employee_id" "${auth_header[@]}")"
  printf '%s' "$employee_state" | jq -e '.data.status == "INACTIVE"' >/dev/null
  printf 'offboarding handover guard, completion, and employee update: OK\n'
fi

printf 'platform verification complete\n'
