#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${PLATFORM_COMPOSE_FILE:-$PROJECT_ROOT/infra/docker-compose.production.yml}"
ENV_FILE="${PLATFORM_ENV_FILE:-$PROJECT_ROOT/infra/.env.production}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
BACKUP_AGE_RECIPIENT="${BACKUP_AGE_RECIPIENT:-}"

for command in docker age tar grep awk; do
  command -v "$command" >/dev/null || { printf '%s is required\n' "$command" >&2; exit 1; }
done
if [[ "$COMPOSE_FILE" != /* ]]; then COMPOSE_FILE="$PROJECT_ROOT/$COMPOSE_FILE"; fi
if [[ "$ENV_FILE" != /* ]]; then ENV_FILE="$PROJECT_ROOT/$ENV_FILE"; fi
if [[ "$BACKUP_DIR" != /* ]]; then BACKUP_DIR="$PROJECT_ROOT/$BACKUP_DIR"; fi
[[ -f "$COMPOSE_FILE" ]] || { printf 'compose file not found: %s\n' "$COMPOSE_FILE" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { printf 'environment file not found: %s\n' "$ENV_FILE" >&2; exit 1; }
[[ -n "$BACKUP_AGE_RECIPIENT" ]] || { printf 'BACKUP_AGE_RECIPIENT is required\n' >&2; exit 1; }

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
running_services="$("${compose[@]}" ps --services --status running)"
for service in mysql minio server ocr web; do
  printf '%s\n' "$running_services" | grep -qx "$service" \
    || { printf 'required service is not running: %s\n' "$service" >&2; exit 1; }
done

mkdir -p "$BACKUP_DIR"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/hr-platform-backup.XXXXXX")"
mkdir -m 700 "$work_dir/minio" "$work_dir/ocr"
archive_name="hr-platform_$(date -u '+%Y%m%dT%H%M%SZ').tar.gz.age"
archive_path="$BACKUP_DIR/$archive_name"
archive_temp="$archive_path.tmp"

cleanup() {
  local status="$?"
  trap - EXIT
  rm -rf "$work_dir" "$archive_temp"
  if ! "${compose[@]}" up -d --no-build --wait ocr server web >/dev/null; then
    printf 'backup finished but application health checks did not recover\n' >&2
    status=1
  else
    printf 'application health checks passed after backup\n'
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Stop writes before taking the database, object-store, and OCR metadata snapshots.
"${compose[@]}" stop --timeout 120 web server
"${compose[@]}" stop --timeout 120 ocr
snapshot_code='
import os
import shutil
import sqlite3
import sys
import tempfile

source = sqlite3.connect("/var/lib/hr-platform/ocr/ocr_tasks.sqlite3")
handle, snapshot_path = tempfile.mkstemp(suffix=".sqlite3")
os.close(handle)
try:
    target = sqlite3.connect(snapshot_path)
    source.backup(target)
    target.close()
    source.close()
    with open(snapshot_path, "rb") as snapshot:
        shutil.copyfileobj(snapshot, sys.stdout.buffer)
finally:
    source.close()
    os.unlink(snapshot_path)
'
"${compose[@]}" run -T --rm --no-deps --entrypoint python ocr -c "$snapshot_code" \
  > "$work_dir/ocr/ocr_tasks.sqlite3"

"${compose[@]}" exec -T mysql sh -c \
  'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump --single-transaction --routines --events --triggers --no-tablespaces --databases "$MYSQL_DATABASE"' \
  > "$work_dir/mysql.sql"
"${compose[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" -v "$work_dir/minio:/backup" \
  --entrypoint mc minio-bootstrap mirror --overwrite --preserve platform/hr-platform /backup

if command -v sha256sum >/dev/null; then
  hash_command=(sha256sum)
else
  hash_command=(shasum -a 256)
fi
(
  cd "$work_dir"
  "${hash_command[@]}" mysql.sql ocr/ocr_tasks.sqlite3
  find minio -type f -exec "${hash_command[@]}" {} +
) > "$work_dir/SHA256SUMS"
printf 'format=hr-platform-backup-v1\ncreated_at_utc=%s\nsource_revision=%s\n' \
  "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
  "$(git -C "$PROJECT_ROOT" rev-parse HEAD 2>/dev/null || printf unknown)" \
  > "$work_dir/manifest.txt"

tar -czf - -C "$work_dir" manifest.txt SHA256SUMS mysql.sql ocr minio \
  | age -r "$BACKUP_AGE_RECIPIENT" -o "$archive_temp"
mv "$archive_temp" "$archive_path"
(
  cd "$BACKUP_DIR"
  "${hash_command[@]}" "$archive_name" > "$archive_name.sha256"
)
printf 'encrypted backup created: %s; waiting for services to recover\n' "$archive_path"
