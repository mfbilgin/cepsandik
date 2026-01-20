-- Initialize databases for CepSandık
CREATE DATABASE userdb;
CREATE DATABASE communitydb;
CREATE DATABASE electiondb;

-- Alternative naming (if Spring Boot uses different names)
CREATE DATABASE IF NOT EXISTS cepsandik_user;
CREATE DATABASE IF NOT EXISTS cepsandik_community;
CREATE DATABASE IF NOT EXISTS cepsandik_election;
