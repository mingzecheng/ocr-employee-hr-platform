#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

usage() {
  printf 'Usage: %s --backup <archive.tar.gz.age> --identity <age-private-key> --confirm\n' "$0" >&2
}

backup_path=""
identity_path=""
confirmed=false
while (($#)); do
  case "$1" in
    --backup) (($# >= 2)) || { usage; exit 2; }; backup_path="$2"; shift 2 ;;
    --identity) (($# >= 2)) || { usage; exit 2; }; identity_path="$2"; shift 2 ;;
    --confirm) confirmed=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$confirmed" == true ]] || { printf 'restore overwrites database and object-store contents; pass --confirm to continue\n' >&2; exit 2; }

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${PLATFORM_COMPOSE_FILE:-$PROJECT_ROOT/infra/docker-compose.production.yml}"
ENV_FILE="${PLATFORM_ENV_FILE:-$PROJECT_ROOT/infra/.env.production}"
if [[ "$COMPOSE_FILE" != /* ]]; then COMPOSE_FILE="$PROJECT_ROOT/$COMPOSE_FILE"; fi
if [[ "$ENV_FILE" != /* ]]; then ENV_FILE="$PROJECT_ROOT/$ENV_FILE"; fi
if [[ "$backup_path" != /* ]]; then backup_path="$PROJECT_ROOT/$backup_path"; fi
if [[ "$identity_path" != /* ]]; then identity_path="$PROJECT_ROOT/$identity_path"; fi

for command in docker age python3 grep awk; do
  command -v "$command" >/dev/null || { printf '%s is required\n' "$command" >&2; exit 1; }
done
[[ -f "$COMPOSE_FILE" ]] || { printf 'compose file not found: %s\n' "$COMPOSE_FILE" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { printf 'environment file not found: %s\n' "$ENV_FILE" >&2; exit 1; }
[[ -f "$backup_path" ]] || { printf 'backup archive not found: %s\n' "$backup_path" >&2; exit 1; }
[[ -f "$identity_path" ]] || { printf 'age identity not found: %s\n' "$identity_path" >&2; exit 1; }
[[ -f "$backup_path.sha256" ]] || { printf 'backup checksum file not found: %s.sha256\n' "$backup_path" >&2; exit 1; }

if command -v sha256sum >/dev/null; then
  expected_hash="$(awk '{print $1}' "$backup_path.sha256")"
  actual_hash="$(sha256sum "$backup_path" | awk '{print $1}')"
else
  expected_hash="$(awk '{print $1}' "$backup_path.sha256")"
  actual_hash="$(shasum -a 256 "$backup_path" | awk '{print $1}')"
fi
[[ -n "$expected_hash" && "$actual_hash" == "$expected_hash" ]] \
  || { printf 'backup checksum mismatch\n' >&2; exit 1; }

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
running_services="$("${compose[@]}" ps --services --status running)"
for service in mysql minio; do
  printf '%s\n' "$running_services" | grep -qx "$service" \
    || { printf '%s must be running before restore\n' "$service" >&2; exit 1; }
done
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/hr-platform-restore.XXXXXX")"
restore_started=false
restore_completed=false
cleanup() {
  local status="$?"
  trap - EXIT
  if [[ "$restore_started" == true && "$restore_completed" == true ]]; then
    :
  elif [[ "$restore_started" == true ]]; then
    "${compose[@]}" stop web server ocr >/dev/null 2>&1 || true
    printf 'restore did not finish; application services remain stopped for inspection\n' >&2
  fi
  rm -rf "$work_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

extract_code='
import shutil
import sys
import tarfile
from pathlib import Path

root = Path(sys.argv[1]).resolve()
required = {"manifest.txt", "SHA256SUMS", "mysql.sql", "ocr/ocr_tasks.sqlite3"}
seen = set()
with tarfile.open(fileobj=sys.stdin.buffer, mode="r|gz") as archive:
    for member in archive:
        raw_parts = member.name.split("/")
        if member.name.startswith("/") or "\\" in member.name or "\x00" in member.name or ".." in raw_parts:
            raise SystemExit(f"unsafe archive path: {member.name}")
        parts = [part for part in raw_parts if part not in ("", ".")]
        if not parts:
            raise SystemExit(f"invalid archive path: {member.name}")
        name = "/".join(parts)
        if name in seen:
            raise SystemExit(f"duplicate archive path: {name}")
        seen.add(name)
        allowed = name in required or name in {"ocr", "minio"} or name.startswith("minio/")
        if not allowed:
            raise SystemExit(f"unexpected archive path: {name}")
        target = root.joinpath(*parts)
        if member.isdir():
            target.mkdir(parents=True, exist_ok=True, mode=0o700)
        elif member.isfile():
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            source = archive.extractfile(member)
            if source is None:
                raise SystemExit(f"archive file has no data: {name}")
            with source, target.open("xb") as output:
                shutil.copyfileobj(source, output)
        else:
            raise SystemExit(f"unsupported archive entry type: {name}")

if not required.issubset(seen):
    raise SystemExit("backup archive is missing required files")
'
age -d -i "$identity_path" "$backup_path" | python3 -c "$extract_code" "$work_dir"
[[ -s "$work_dir/mysql.sql" && -s "$work_dir/ocr/ocr_tasks.sqlite3" && -d "$work_dir/minio" ]] \
  || { printf 'backup archive is missing required data\n' >&2; exit 1; }
grep -qx 'format=hr-platform-backup-v1' "$work_dir/manifest.txt" \
  || { printf 'unsupported backup format\n' >&2; exit 1; }
if command -v sha256sum >/dev/null; then
  (cd "$work_dir" && sha256sum -c SHA256SUMS)
else
  (cd "$work_dir" && shasum -a 256 -c SHA256SUMS)
fi
python3 -c 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); result=c.execute("PRAGMA integrity_check").fetchone()[0]; c.close(); sys.exit(0 if result == "ok" else 1)' \
  "$work_dir/ocr/ocr_tasks.sqlite3"

printf 'Restoring encrypted backup; current database and bucket contents will be replaced.\n'
restore_started=true
"${compose[@]}" stop --timeout 120 web server ocr
"${compose[@]}" exec -T mysql sh -c \
  'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot' < "$work_dir/mysql.sql"
"${compose[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" -v "$work_dir/minio:/restore:ro" \
  --entrypoint mc minio-bootstrap mirror --overwrite --remove --preserve /restore platform/hr-platform
restore_code='import os,sqlite3; source=sqlite3.connect("file:/restore/ocr_tasks.sqlite3?mode=ro",uri=True); target_path="/var/lib/hr-platform/ocr/ocr_tasks.restore.sqlite3"; target=sqlite3.connect(target_path); source.backup(target); target.close(); source.close(); database="/var/lib/hr-platform/ocr/ocr_tasks.sqlite3"; [os.path.exists(database + suffix) and os.remove(database + suffix) for suffix in ("-wal", "-shm")]; os.replace(target_path, database); os.chown(database,10001,10001)'
"${compose[@]}" run --rm --no-deps --user 0:0 -v "$work_dir/ocr:/restore:ro" \
  --entrypoint python ocr -c "$restore_code"
rm -rf "$work_dir"
"${compose[@]}" up -d --no-build --wait ocr server web
restore_completed=true
printf 'restore completed; application health checks passed\n'
