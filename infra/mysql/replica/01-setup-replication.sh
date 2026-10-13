#!/bin/bash
# Automatically configure this replica to stream from the primary MySQL.
# Runs once on first container start (when the data directory is empty).
set -e

echo "[replica-setup] Waiting for primary MySQL to accept connections..."
until mysqladmin ping -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; do
  sleep 2
done
echo "[replica-setup] Primary is reachable."

# Fetch the primary's current binlog coordinates (traditional file/pos position, no GTID).
STATUS=$(mysql -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -B -e "SHOW MASTER STATUS;" 2>/dev/null)
BINLOG_FILE=$(echo "$STATUS" | awk '{print $1}')
BINLOG_POS=$(echo "$STATUS" | awk '{print $2}')

if [ -z "$BINLOG_FILE" ] || [ -z "$BINLOG_POS" ]; then
  echo "[replica-setup] ERROR: could not read primary binlog coordinates. Raw output: $STATUS" >&2
  exit 1
fi
echo "[replica-setup] Primary binlog: ${BINLOG_FILE} / ${BINLOG_POS}"

# Point this replica at the primary and start the replication thread.
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql',
  SOURCE_USER='repl',
  SOURCE_PASSWORD='repl-password',
  SOURCE_LOG_FILE='${BINLOG_FILE}',
  SOURCE_LOG_POS=${BINLOG_POS},
  SOURCE_CONNECT_RETRY=10;
START REPLICA;
EOSQL

echo "[replica-setup] Replication started. Current replica status:"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW REPLICA STATUS\G" 2>/dev/null | grep -E "Replica_IO_Running|Replica_SQL_Running|Last_Error" || true
