-- Moves rate-limit state out of application memory and into the database.
--
-- Buckets previously lived in a Caffeine cache on the heap, which had two
-- consequences nobody wants from a limiter:
--
--   * Every deploy reset every bucket to full. An attacker being throttled
--     simply had to wait for the next release - and the deploy that reset
--     them is the one the limiter was protecting.
--   * A second application instance would keep its own separate counts, so
--     the effective limit silently became N times the configured one.
--
-- bucket4j's PostgreSQL backend stores each bucket as a row and updates it
-- under SELECT ... FOR UPDATE, so concurrent requests serialise correctly
-- whether they arrive at one instance or several.

CREATE TABLE IF NOT EXISTS rate_limit_bucket (
    -- bucket4j's PostgreSQLadvisoryLockBasedProxyManager requires a bigint id;
    -- the textual bucket key is hashed into it by RateLimitService.
    id    BIGINT PRIMARY KEY,
    state BYTEA
);

-- Abandoned buckets accumulate: every distinct client IP that ever hit a
-- limited endpoint leaves a row behind. Without this index the periodic
-- cleanup would sequentially scan the whole table.
ALTER TABLE rate_limit_bucket ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_rate_limit_updated ON rate_limit_bucket(updated_at);
