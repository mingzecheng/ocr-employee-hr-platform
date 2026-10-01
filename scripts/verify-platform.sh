#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
OCR_BASE_URL="${OCR_BASE_URL:-http://127.0.0.1:8000}"
WEB_URL="${WEB_URL:-http://127.0.0.1:5173}"
LOGIN_USERNAME="${LOGIN_USERNAME:-demo-admin}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password}"
SMOKE_IMAGE="${SMOKE_IMAGE:-$PROJECT_ROOT/ocr-service/tests/fixtures/platform-smoke.png}"

command -v curl >/dev/null || { printf 'curl is required\n' >&2; exit 1; }
command -v jq >/dev/null || { printf 'jq is required\n' >&2; exit 1; }

check() {
  local label="$1" url="$2"
  curl --fail --silent --show-error "$url" >/dev/null
  printf '%s: OK\n' "$label"
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
printf '%s' "$me_response" | jq -e '.code == "0" and (.data.id | type == "number")' >/dev/null
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

if [[ "${RUN_OCR_SMOKE:-0}" == "1" ]]; then
  [[ -f "$SMOKE_IMAGE" ]] || { printf 'smoke image not found: %s\n' "$SMOKE_IMAGE" >&2; exit 1; }
  department_id="$(printf '%s' "$departments" | jq -er '.data[0].id')"
  position_id="${POSITION_ID:?POSITION_ID is required when RUN_OCR_SMOKE=1}"
  employee_no="DEMO-VERIFY-$(date +%s)"
  employee="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/employees" "${auth_header[@]}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg no "$employee_no" --argjson departmentId "$department_id" --argjson positionId "$position_id" \
      '{employeeNo:$no,name:"脱敏验证员工",departmentId:$departmentId,positionId:$positionId,status:"ACTIVE"}')")"
  employee_id="$(printf '%s' "$employee" | jq -er '.data.id')"
  upload="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive/employees/$employee_id/documents" "${auth_header[@]}" \
    -F documentType=employee_profile -F "file=@$SMOKE_IMAGE")"
  version_id="$(printf '%s' "$upload" | jq -er '.data.id')"
  ocr="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/archive/versions/$version_id/ocr?documentType=employee_profile" "${auth_header[@]}")"
  printf '%s' "$ocr" | jq -e '.code == "0" and (.data.bindingId | type == "number")' >/dev/null
  printf 'archive upload and OCR trigger: OK\n'
fi

printf 'platform verification complete\n'
