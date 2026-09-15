-- Real payments through Razorpay, replacing a service that generated a random
-- UUID and declared every payment successful.
--
-- The flow has three parties and the server trusts only one of them:
--
--   1. Server creates a Razorpay order and returns its id to the browser.
--   2. Browser completes payment in Razorpay's widget and posts back a
--      payment id and a signature.
--   3. Server verifies that signature with the key secret, which only it and
--      Razorpay hold. A browser cannot forge it.
--
-- The webhook is the authoritative confirmation, because the browser may be
-- closed, crash, or lose its network between paying and telling us.

ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_order_id   VARCHAR(64);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_payment_id VARCHAR(64);
-- Kept for audit: it is the proof the confirmation genuinely came from
-- Razorpay, and a disputed charge is exactly when that evidence is wanted.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_signature  VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_reason      VARCHAR(255);

-- One Razorpay order maps to one payment. A unique index turns any attempt to
-- attach the same gateway order to two payments into an error instead of a
-- silent double-credit.
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_razorpay_order
    ON payments(razorpay_order_id) WHERE razorpay_order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_razorpay_payment
    ON payments(razorpay_payment_id) WHERE razorpay_payment_id IS NOT NULL;

-- Razorpay delivers webhooks at least once and retries on any non-2xx, so the
-- same event will arrive again. Recording the event id and refusing duplicates
-- is what stops a retry from refunding twice or marking an order paid twice.
CREATE TABLE IF NOT EXISTS razorpay_webhook_event (
    event_id     VARCHAR(80) PRIMARY KEY,
    event_type   VARCHAR(80)  NOT NULL,
    payload      TEXT         NOT NULL,
    received_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    error        TEXT
);

CREATE INDEX IF NOT EXISTS idx_webhook_received ON razorpay_webhook_event(received_at);
