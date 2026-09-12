-- V1 seeded the demo accounts with plain-text passwords ('123' / '765'), which the
-- application then re-hashed lazily on every lookup -- on the unauthenticated login
-- path, before credentials were ever verified. Hash them once here so that runtime
-- migration hack can be removed.
--
-- These remain WELL-KNOWN DEMO CREDENTIALS. Delete or rotate them before exposing
-- this application to anything resembling production.

UPDATE customer
SET password = '$2b$10$aqmPdKKyM0IzUcRqvDjh6O24Uqz7tGhTzAOpklwHfWyMm/SQkRdpu'
WHERE username = 'admin'
  AND password = '123';

UPDATE customer
SET password = '$2b$10$pFwHGuBODZ9Ve3nvwHLMveYA/GUXzTzxKzkNk0fxhkNFFUlErMxCi'
WHERE username = 'lisa'
  AND password = '765';

-- Identity and authorisation columns must always be present.
UPDATE customer SET role = 'ROLE_NORMAL' WHERE role IS NULL;

ALTER TABLE customer ALTER COLUMN username SET NOT NULL;
ALTER TABLE customer ALTER COLUMN password SET NOT NULL;
ALTER TABLE customer ALTER COLUMN role SET NOT NULL;

-- Case-insensitive username lookups used by authentication.
CREATE INDEX IF NOT EXISTS idx_customer_username_lower ON customer (LOWER(username));
