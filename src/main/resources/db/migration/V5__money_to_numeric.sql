-- Money was stored as INT (prices) and DOUBLE PRECISION (totals). Binary floating
-- point cannot represent most decimal currency values exactly, so order totals
-- could drift from the sum of their lines. Move every monetary column to
-- numeric(12,2), which the entities now map as BigDecimal.

ALTER TABLE product
    ALTER COLUMN price TYPE numeric(12, 2) USING price::numeric(12, 2);
ALTER TABLE product
    ALTER COLUMN price SET DEFAULT 0;
UPDATE product SET price = 0 WHERE price IS NULL;
ALTER TABLE product
    ALTER COLUMN price SET NOT NULL;

ALTER TABLE order_items
    ALTER COLUMN price TYPE numeric(12, 2) USING price::numeric(12, 2);

ALTER TABLE orders
    ALTER COLUMN total_amount TYPE numeric(12, 2) USING total_amount::numeric(12, 2);

ALTER TABLE payments
    ALTER COLUMN amount TYPE numeric(12, 2) USING amount::numeric(12, 2);

-- Money is never negative in this domain.
ALTER TABLE product
    ADD CONSTRAINT chk_product_price_non_negative CHECK (price >= 0);
ALTER TABLE product
    ADD CONSTRAINT chk_product_quantity_non_negative CHECK (quantity IS NULL OR quantity >= 0);
ALTER TABLE order_items
    ADD CONSTRAINT chk_order_item_price_non_negative CHECK (price >= 0);
ALTER TABLE order_items
    ADD CONSTRAINT chk_order_item_quantity_positive CHECK (quantity > 0);
ALTER TABLE orders
    ADD CONSTRAINT chk_order_total_non_negative CHECK (total_amount >= 0);
ALTER TABLE payments
    ADD CONSTRAINT chk_payment_amount_non_negative CHECK (amount >= 0);
