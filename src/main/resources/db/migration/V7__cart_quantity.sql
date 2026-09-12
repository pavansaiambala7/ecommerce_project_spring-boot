-- The cart tables from V1 could not represent a usable cart: cart_product held
-- only (cart_id, product_id), so a customer could not have two of anything, and
-- nothing stopped a customer accumulating multiple carts.

ALTER TABLE cart_product
    ADD COLUMN IF NOT EXISTS quantity INT NOT NULL DEFAULT 1;

ALTER TABLE cart_product
    ADD CONSTRAINT chk_cart_product_quantity_positive CHECK (quantity > 0);

-- Before the uniqueness constraint can be added, existing data has to satisfy it.
-- Carts are transient, pre-checkout state, so surplus rows are discarded rather
-- than merged: keep the lowest-id cart per customer and drop the rest.

DELETE FROM cart_product
WHERE cart_id IN (SELECT id FROM cart WHERE customer_id IS NULL);

DELETE FROM cart WHERE customer_id IS NULL;

DELETE FROM cart_product
WHERE cart_id NOT IN (SELECT MIN(id) FROM cart GROUP BY customer_id);

DELETE FROM cart
WHERE id NOT IN (SELECT MIN(id) FROM cart GROUP BY customer_id);

ALTER TABLE cart
    ALTER COLUMN customer_id SET NOT NULL;

ALTER TABLE cart
    ADD CONSTRAINT uq_cart_customer UNIQUE (customer_id);

CREATE INDEX IF NOT EXISTS idx_cart_product_product ON cart_product(product_id);
