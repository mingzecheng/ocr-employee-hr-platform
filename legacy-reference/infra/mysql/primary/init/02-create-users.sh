#!/usr/bin/env bash
set -Eeuo pipefail

escape_sql_literal() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf '%s' "$value"
}

app_user="${MYSQL_USER:-${MYSQL_USERNAME:-hr}}"
app_password="${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
replication_user="${MYSQL_REPLICATION_USER:-repl}"
replication_password="${MYSQL_REPLICATION_PASSWORD:?MYSQL_REPLICATION_PASSWORD is required}"
app_password_sql="$(escape_sql_literal "$app_password")"
replication_password_sql="$(escape_sql_literal "$replication_password")"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password_sql}';
ALTER USER '${app_user}'@'%' IDENTIFIED BY '${app_password_sql}';
GRANT ALL PRIVILEGES ON identity_db.* TO '${app_user}'@'%';
GRANT ALL PRIVILEGES ON archive_db.* TO '${app_user}'@'%';
GRANT ALL PRIVILEGES ON workflow_db.* TO '${app_user}'@'%';

CREATE USER IF NOT EXISTS '${replication_user}'@'%' IDENTIFIED BY '${replication_password_sql}';
ALTER USER '${replication_user}'@'%' IDENTIFIED BY '${replication_password_sql}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '${replication_user}'@'%';
FLUSH PRIVILEGES;
SQL
