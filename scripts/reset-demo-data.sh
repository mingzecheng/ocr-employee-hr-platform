#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" != "--confirm" ]]; then
  printf 'Usage: %s --confirm\n' "$0" >&2
  exit 2
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-hr}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
MYSQL_DATABASE="${MYSQL_DATABASE:-hr_platform}"

MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=tcp --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_USERNAME" "$MYSQL_DATABASE" <<'SQL'
START TRANSACTION;
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM exception_record WHERE inventory_item_id IN (SELECT id FROM inventory_item WHERE task_id IN (SELECT id FROM inventory_task WHERE task_no LIKE 'DEMO-%'));
DELETE FROM inventory_item WHERE task_id IN (SELECT id FROM inventory_task WHERE task_no LIKE 'DEMO-%');
DELETE FROM inventory_task WHERE task_no LIKE 'DEMO-%';
DELETE FROM archive_use_record WHERE application_id IN (SELECT id FROM archive_access_application WHERE application_no LIKE 'DEMO-%');
DELETE FROM archive_access_item WHERE application_id IN (SELECT id FROM archive_access_application WHERE application_no LIKE 'DEMO-%');
DELETE FROM archive_access_application WHERE application_no LIKE 'DEMO-%';
DELETE FROM approval_record WHERE request_id IN (SELECT id FROM hr_request WHERE request_no LIKE 'DEMO-%');
DELETE FROM onboarding_detail WHERE request_id IN (SELECT id FROM hr_request WHERE request_no LIKE 'DEMO-%');
DELETE FROM transfer_detail WHERE request_id IN (SELECT id FROM hr_request WHERE request_no LIKE 'DEMO-%');
DELETE FROM offboarding_detail WHERE request_id IN (SELECT id FROM hr_request WHERE request_no LIKE 'DEMO-%');
DELETE FROM hr_request WHERE request_no LIKE 'DEMO-%';
DELETE FROM ocr_field_revision WHERE binding_id IN (SELECT id FROM ocr_binding WHERE task_id LIKE 'DEMO-%');
DELETE FROM ocr_binding WHERE task_id LIKE 'DEMO-%';
DELETE FROM archive_version WHERE document_id IN (SELECT id FROM archive_document WHERE title LIKE 'DEMO-%');
DELETE FROM archive_document WHERE title LIKE 'DEMO-%';
DELETE FROM employee_status_history WHERE employee_id IN (SELECT id FROM employee WHERE employee_no LIKE 'DEMO-%');
DELETE FROM employee_archive WHERE employee_id IN (SELECT id FROM employee WHERE employee_no LIKE 'DEMO-%');
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'demo-%');
DELETE FROM sys_user WHERE username LIKE 'demo-%';
DELETE FROM employee WHERE employee_no LIKE 'DEMO-%';
DELETE FROM org_position WHERE code LIKE 'DEMO-%';
DELETE FROM org_department WHERE code LIKE 'DEMO-%';
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
SQL

printf 'demo data reset complete\n'
