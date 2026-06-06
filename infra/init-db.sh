#!/bin/sh
set -eu

APP_USER="${DATABASE_USERNAME:-cepsandik_user}"
APP_PASSWORD="${DATABASE_PASSWORD:?DATABASE_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname postgres \
  --set app_user="$APP_USER" \
  --set app_password="$APP_PASSWORD" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')\gexec

SELECT format('ALTER ROLE %I WITH PASSWORD %L', :'app_user', :'app_password')\gexec

SELECT 'CREATE DATABASE userdb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'userdb')\gexec

SELECT 'CREATE DATABASE communitydb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'communitydb')\gexec

SELECT 'CREATE DATABASE electiondb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'electiondb')\gexec

SELECT 'CREATE DATABASE notificationdb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notificationdb')\gexec

SELECT format('GRANT ALL PRIVILEGES ON DATABASE userdb TO %I', :'app_user')\gexec
SELECT format('GRANT ALL PRIVILEGES ON DATABASE communitydb TO %I', :'app_user')\gexec
SELECT format('GRANT ALL PRIVILEGES ON DATABASE electiondb TO %I', :'app_user')\gexec
SELECT format('GRANT ALL PRIVILEGES ON DATABASE notificationdb TO %I', :'app_user')\gexec
EOSQL

for db in userdb communitydb electiondb notificationdb; do
  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$db" \
    --set app_user="$APP_USER" <<'EOSQL'
SELECT format('GRANT ALL ON SCHEMA public TO %I', :'app_user')\gexec
EOSQL
done
