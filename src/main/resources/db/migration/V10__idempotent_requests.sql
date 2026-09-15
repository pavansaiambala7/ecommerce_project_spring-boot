-- Makes checkout and payment safe to retry.
--
-- Both were previously unguarded: a double-tap on a phone, an impatient
-- second click, or a network layer retrying a request it never saw a response
-- to would each create a second order and a second payment. The customer is
-- charged twice and the stock is decremented twice, and neither the client nor
-- the server has any way to tell the duplicate from a genuine second purchase.
--
-- The client sends an Idempotency-Key header. The first request with a given
-- key does the work and stores its response; any repeat of that key returns
-- the stored response without re-running anything.

CREATE TABLE IF NOT EXISTS idempotent_request (
    idempotency_key VARCHAR(120) NOT NULL,
    customer_id     INT          NOT NULL REFERENCES customer(id),
    endpoint        VARCHAR(160) NOT NULL,
    -- Fingerprint of the request body. A client reusing one key for a
    -- genuinely different request is a bug on their side, and silently
    -- returning the first response would hide it; this lets us reject it.
    request_hash    VARCHAR(64)  NOT NULL,
    status_code     INT,
    response_body   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,

    -- Scoped per customer so one user's key can never collide with or replay
    -- another's, whatever they send.
    PRIMARY KEY (customer_id, idempotency_key)
);

-- The race this table exists to stop is two simultaneous requests, not two
-- sequential ones. Both check for an existing row, both find nothing, and both
-- proceed. The primary key above is what actually serialises them: the second
-- INSERT fails, and that failure is the signal to wait for the first result
-- rather than duplicate the work.

CREATE INDEX IF NOT EXISTS idx_idempotent_created ON idempotent_request(created_at);
