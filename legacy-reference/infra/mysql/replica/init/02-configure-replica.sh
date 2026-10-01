#!/usr/bin/env bash
set -Eeuo pipefail

escape_sql_literal() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf '%s' "$value"
}

replication_user="${MYSQL_REPLICATION_USER:-repl}"
replication_password="${MYSQL_REPLICATION_PASSWORD:?MYSQL_REPLICATION_PASSWORD is required}"
replication_password_sql="$(escape_sql_literal "$replication_password")"

mysql_exec() {
  MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql \
    --protocol=tcp \
    --host="${MYSQL_REPLICA_HOST:-mysql-replica}" \
    --port="${MYSQL_REPLICA_PORT:-3306}" \
    -uroot "$@"
}

replica_status="$(mysql_exec -B -e 'SHOW REPLICA STATUS' 2>/dev/null || true)"
source_host="$(printf '%s\n' "$replica_status" | awk -F '\t' 'NR == 2 { print $2 }')"
if [[ -z "$replica_status" || "$source_host" != "mysql-primary" ]]; then
  if [[ -n "$replica_status" ]]; then
    mysql_exec -e 'STOP REPLICA; RESET REPLICA ALL'
  fi
  mysql_exec <<SQL
SET GLOBAL super_read_only = OFF;
SET GLOBAL read_only = OFF;

CHANGE REPLICATION SOURCE TO
  SOURCE_HOST = 'mysql-primary',
  SOURCE_PORT = 3306,
  SOURCE_USER = '${replication_user}',
  SOURCE_PASSWORD = '${replication_password_sql}',
  GET_SOURCE_PUBLIC_KEY = 1,
  SOURCE_AUTO_POSITION = 1;
START REPLICA;
SQL
else
  mysql_exec -e 'START REPLICA'
fi

replica_ready=0
for _attempt in {1..60}; do
  replica_status="$(mysql_exec -B -e 'SHOW REPLICA STATUS' 2>/dev/null || true)"
  if printf '%s\n' "$replica_status" | awk -F '\t' '
      NR == 1 {
        for (i = 1; i <= NF; i++) {
          if ($i == "Replica_IO_Running") io = i
          if ($i == "Replica_SQL_Running") sql = i
          if ($i == "Seconds_Behind_Source") lag = i
        }
      }
      NR == 2 && $io == "Yes" && $sql == "Yes" && $lag == "0" { print "ready"; exit }
    ' | grep -qx 'ready'; then
    replica_ready=1
    break
  fi
  sleep 1
done

if (( replica_ready == 0 )); then
  printf 'replica did not catch up with the primary\n' >&2
  mysql_exec -B \
    -e 'SHOW REPLICA STATUS' 2>/dev/null \
    | awk -F '\t' 'NR == 1 { for (i = 1; i <= NF; i++) { if ($i == "Replica_IO_Running") io = i; if ($i == "Replica_SQL_Running") sql = i; if ($i == "Seconds_Behind_Source") lag = i; if ($i == "Last_IO_Error") lie = i; if ($i == "Last_SQL_Error") lse = i } } NR == 2 { print "io=" $io; print "sql=" $sql; print "lag=" $lag; print "last_io=" $lie; print "last_sql=" $lse }' >&2
  exit 1
fi

mysql_exec <<SQL
# Replica writes are blocked at the server level; replicated schema grants remain available for reads.
SET GLOBAL read_only = ON;
SET GLOBAL super_read_only = ON;
SET PERSIST read_only = ON;
SET PERSIST super_read_only = ON;
FLUSH PRIVILEGES;
SQL
