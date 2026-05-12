-- Initialize databases and application user for CepSandık

-- Create application databases
CREATE DATABASE userdb;
CREATE DATABASE communitydb;
CREATE DATABASE electiondb;
CREATE DATABASE bulletindb;

-- Create application user and grant privileges
CREATE USER cepsandik_user WITH PASSWORD 'REDACTED_DB_PASSWORD';

GRANT ALL PRIVILEGES ON DATABASE userdb TO cepsandik_user;
GRANT ALL PRIVILEGES ON DATABASE communitydb TO cepsandik_user;
GRANT ALL PRIVILEGES ON DATABASE electiondb TO cepsandik_user;
GRANT ALL PRIVILEGES ON DATABASE bulletindb TO cepsandik_user;

-- Grant schema privileges (required for PostgreSQL 15+)
\c userdb
GRANT ALL ON SCHEMA public TO cepsandik_user;

\c communitydb
GRANT ALL ON SCHEMA public TO cepsandik_user;

\c electiondb
GRANT ALL ON SCHEMA public TO cepsandik_user;

\c bulletindb
GRANT ALL ON SCHEMA public TO cepsandik_user;
