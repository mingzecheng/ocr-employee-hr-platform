#!/bin/sh
set -eu

case "$MINIO_ROOT_USER" in
  minioadmin|change-me-*)
    printf 'MINIO_ROOT_USER must be a production identity\n' >&2
    exit 1
    ;;
esac
if [ "${#MINIO_ROOT_PASSWORD}" -lt 16 ] || [ "$MINIO_ROOT_PASSWORD" = "change-me-minio-root-password" ]; then
  printf 'MINIO_ROOT_PASSWORD must be a production credential\n' >&2
  exit 1
fi
if [ "${#MINIO_ACCESS_KEY}" -lt 8 ] || [ "$MINIO_ACCESS_KEY" = "minioadmin" ] \
  || [ "$MINIO_ACCESS_KEY" = "$MINIO_ROOT_USER" ]; then
  printf 'MINIO_ACCESS_KEY must be a distinct production identity\n' >&2
  exit 1
fi
if [ "${#MINIO_SECRET_KEY}" -lt 16 ] || [ "$MINIO_SECRET_KEY" = "change-me-minio-password" ] \
  || [ "$MINIO_SECRET_KEY" = "$MINIO_ROOT_PASSWORD" ]; then
  printf 'MINIO_SECRET_KEY must be a distinct production credential\n' >&2
  exit 1
fi

mc mb --ignore-existing platform/hr-platform

if ! mc admin user info platform "$MINIO_ACCESS_KEY" >/dev/null 2>&1; then
  mc admin user add platform "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
fi

if ! mc admin policy info platform hr-platform-access >/dev/null 2>&1; then
  mc admin policy create platform hr-platform-access /bootstrap/minio-policy.json
fi

mc admin user enable platform "$MINIO_ACCESS_KEY"
mc admin policy attach platform hr-platform-access --user "$MINIO_ACCESS_KEY"
