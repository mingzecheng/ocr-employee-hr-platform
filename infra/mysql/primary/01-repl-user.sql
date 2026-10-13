-- Create the replication account that the replica uses to stream binlog events.
-- Only runs on first initialization of the primary data volume.
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl-password';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
