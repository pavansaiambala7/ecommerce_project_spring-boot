-- Storefront features that need schema: a department tree for Amazon-style
-- navigation, list prices so deals can exist, a delivery address book, and a
-- vocabulary for search-as-you-type suggestions.

-- ---------------------------------------------------------------------------
-- Department tree
-- ---------------------------------------------------------------------------
-- A flat list of thirty categories cannot be a navigation bar. Two levels are
-- enough: "Fashion" groups men's, women's and footwear the way a shopper
-- thinks about them, while each product still belongs to exactly one leaf.
ALTER TABLE category ADD COLUMN IF NOT EXISTS parent_id INT REFERENCES category(category_id);
ALTER TABLE category ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 1000;
-- Featured departments appear in the bar under the search box; the rest are
-- one click away in the full department menu.
ALTER TABLE category ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_category_parent ON category(parent_id);

-- Names shoppers actually look for. Each rename is guarded so a database where
-- an administrator already created the target name keeps both rows intact
-- rather than failing the migration.
UPDATE category SET name = 'Mobiles'
 WHERE name = 'Mobile Phones' AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'Mobiles');
UPDATE category SET name = 'Laptops'
 WHERE name = 'Computers' AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'Laptops');
UPDATE category SET name = 'Footwear'
 WHERE name = 'Shoes' AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'Footwear');
UPDATE category SET name = 'Bags & Luggage'
 WHERE name = 'Luggage' AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'Bags & Luggage');
-- V9's single Clothing department mixed sarees with men's chinos, which makes
-- "deals for men" impossible to answer. It becomes the women's department and
-- men's clothing gets its own.
UPDATE category SET name = 'Women''s Fashion'
 WHERE name = 'Clothing' AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'Women''s Fashion');
-- V8 filed pantry staples and eggs under "Other", which reads as a leftover.
UPDATE category SET name = 'Pantry Staples'
 WHERE name = 'Other' AND NOT EXISTS (SELECT 1 FROM category WHERE name = 'Pantry Staples');

INSERT INTO category(name)
SELECT name FROM (VALUES ('Men''s Fashion'), ('Fashion'), ('Grocery')) AS incoming(name)
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.name = incoming.name);

UPDATE category child SET parent_id = parent.category_id
FROM category parent
WHERE parent.name = 'Fashion'
  AND child.name IN ('Men''s Fashion', 'Women''s Fashion', 'Footwear', 'Bags & Luggage');

UPDATE category child SET parent_id = parent.category_id
FROM category parent
WHERE parent.name = 'Grocery'
  AND child.name IN ('Fruits', 'Vegetables', 'Dairy', 'Bakery', 'Meat', 'Fish',
                     'Drinks', 'Sweets', 'Pantry Staples');

UPDATE category c SET sort_order = o.sort_order, featured = o.featured
FROM (VALUES
    ('Mobiles', 10, true), ('Electronics', 20, true), ('Laptops', 30, true),
    ('Fashion', 40, true), ('Grocery', 50, true), ('Home & Kitchen', 60, true),
    ('Beauty & Personal Care', 70, true), ('Sports & Outdoors', 80, true),
    ('Books', 90, true), ('Toys & Games', 100, true),
    ('Furniture', 110, false), ('Health & Household', 120, false),
    ('Baby', 130, false), ('Video Games', 140, false), ('Movies & TV', 150, false),
    ('Musical Instruments', 160, false), ('Automotive', 170, false),
    ('Tools & Home Improvement', 180, false), ('Garden & Outdoor', 190, false),
    ('Office Products', 200, false), ('Pet Supplies', 210, false),
    ('Men''s Fashion', 1, false), ('Women''s Fashion', 2, false),
    ('Footwear', 3, false), ('Bags & Luggage', 4, false),
    ('Fruits', 1, false), ('Vegetables', 2, false), ('Dairy', 3, false),
    ('Bakery', 4, false), ('Meat', 5, false), ('Fish', 6, false),
    ('Drinks', 7, false), ('Sweets', 8, false), ('Pantry Staples', 9, false)
) AS o(name, sort_order, featured)
WHERE c.name = o.name;

-- ---------------------------------------------------------------------------
-- List price and discount
-- ---------------------------------------------------------------------------
-- Indian retail law requires an MRP on packaged goods, and every "deal" a
-- marketplace shows is the gap between it and the selling price. Without the
-- column there is nothing honest to put in a "40% off" badge.
ALTER TABLE product ADD COLUMN IF NOT EXISTS mrp numeric(12, 2);
ALTER TABLE product ADD CONSTRAINT chk_product_mrp_not_below_price
    CHECK (mrp IS NULL OR mrp >= price);

-- Stored rather than computed per query so "sort by discount" and "deals in
-- this department" can use an index instead of evaluating every row.
ALTER TABLE product ADD COLUMN IF NOT EXISTS discount_percent smallint
    GENERATED ALWAYS AS (
        CASE WHEN mrp IS NOT NULL AND mrp > 0 AND mrp > price
             THEN round((mrp - price) * 100 / mrp)::smallint
             ELSE 0::smallint END
    ) STORED;
CREATE INDEX IF NOT EXISTS idx_product_category_discount
    ON product(category_id, discount_percent DESC);

-- Give roughly two thirds of the hand-written grocery seed a modest MRP, so
-- the grocery department has deals too. The rounding step grows with the
-- price, as printed MRPs do: rounding a Rs 20 bottle of water up to the next
-- ten rupees would advertise it at a fanciful 33% off.
UPDATE product
   SET mrp = CASE
                 WHEN price < 100 THEN ceil(price * (1.08 + (product_id % 4) * 0.05))
                 WHEN price < 500 THEN ceil(price * (1.08 + (product_id % 4) * 0.05) / 5) * 5
                 ELSE ceil(price * (1.08 + (product_id % 4) * 0.05) / 10) * 10
             END
 WHERE external_id IS NULL AND mrp IS NULL AND product_id % 3 <> 0 AND price > 0;

-- ---------------------------------------------------------------------------
-- Address book
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS address (
    address_id  SERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    full_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(15)  NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    landmark    VARCHAR(120),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    pincode     VARCHAR(6)   NOT NULL,
    latitude    numeric(9, 6),
    longitude   numeric(9, 6),
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    -- Indian PIN codes are six digits and never start with 0; mobile numbers
    -- are ten digits starting 6-9. Enforced here as well as in the API so a
    -- bug in one path cannot store an undeliverable address.
    CONSTRAINT chk_address_pincode CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT chk_address_phone CHECK (phone ~ '^[6-9][0-9]{9}$')
);
CREATE INDEX IF NOT EXISTS idx_address_customer ON address(customer_id);
-- At most one default per customer, guaranteed by the database rather than by
-- hoping two concurrent "make default" requests never interleave.
CREATE UNIQUE INDEX IF NOT EXISTS uq_address_one_default_per_customer
    ON address(customer_id) WHERE is_default;

-- ---------------------------------------------------------------------------
-- Where each order ships
-- ---------------------------------------------------------------------------
-- Copied onto the order, not referenced. A customer editing or deleting an
-- address next month must not change where last month's parcel was sent.
-- Nullable because orders placed before this migration have no address.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_name     VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_phone    VARCHAR(15);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_line1    VARCHAR(200);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_line2    VARCHAR(200);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_landmark VARCHAR(120);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_city     VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_state    VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ship_pincode  VARCHAR(6);

-- ---------------------------------------------------------------------------
-- Search suggestions
-- ---------------------------------------------------------------------------
-- Typeahead runs on every keystroke, so it cannot tokenise fifty thousand
-- product names per request. This view does that once: departments, brands,
-- the leading words of product titles ("samsung galaxy") and single words
-- ("apples"), each with how many products it would find. It is refreshed
-- after catalogue imports and on a schedule.
--
-- Two things are kept out of the vocabulary because nobody searches for them:
-- author names ("The Silent River by Kavya Iyer" would otherwise offer "iyer"
-- to someone typing "iy"), and colour-led phrases ("samsung black").
CREATE MATERIALIZED VIEW IF NOT EXISTS search_term AS
WITH source AS (
    SELECT p.product_id,
           trim(split_part(regexp_replace(p.name, ' by [^(]+', ' '), '(', 1)) AS title
    FROM product p
),
words AS (
    SELECT w AS term, 'word'::text AS kind, 0 AS category_id,
           count(DISTINCT s.product_id)::int AS hits
    FROM source s, regexp_split_to_table(lower(s.title), '[^[:alnum:]]+') AS w
    WHERE length(w) >= 3
      AND w !~ '^[0-9]'
      AND w NOT IN ('and', 'for', 'the', 'with', 'set', 'pack', 'pcs', 'from', 'new')
    GROUP BY w
),
phrases AS (
    SELECT v.phrase AS term, 'phrase'::text AS kind, 0 AS category_id,
           count(DISTINCT s.product_id)::int AS hits
    FROM source s
    CROSS JOIN LATERAL (SELECT regexp_split_to_array(lower(s.title), '\s+') AS parts) t
    CROSS JOIN LATERAL (VALUES (array_to_string(t.parts[1:2], ' ')),
                               (array_to_string(t.parts[1:3], ' '))) AS v(phrase)
    WHERE array_length(t.parts, 1) >= 2
      AND v.phrase !~ '\m(black|white|blue|grey|gray|red|green|pink|beige|navy|tan|olive|silver|gold|maroon|charcoal|peach|lavender|teal|brown|yellow|orange|coral|nude|berry|natural|sunburst|wenge|terracotta|assorted|steel|multicolour|midnight|starlight|platinum)\M'
    GROUP BY v.phrase
),
brands AS (
    SELECT lower(brand) AS term, 'brand'::text AS kind, 0 AS category_id,
           count(*)::int AS hits
    FROM product
    WHERE brand IS NOT NULL AND brand <> ''
    GROUP BY lower(brand)
),
departments AS (
    SELECT lower(c.name) AS term, 'category'::text AS kind,
           min(c.category_id) AS category_id,
           max((SELECT count(*) FROM product p
                WHERE p.category_id = c.category_id
                   OR p.category_id IN (SELECT ch.category_id FROM category ch
                                        WHERE ch.parent_id = c.category_id)))::int AS hits
    FROM category c
    GROUP BY lower(c.name)
)
SELECT term, kind, category_id, hits FROM words
UNION ALL SELECT term, kind, category_id, hits FROM phrases
UNION ALL SELECT term, kind, category_id, hits FROM brands
UNION ALL SELECT term, kind, category_id, hits FROM departments;

-- Required for REFRESH ... CONCURRENTLY, which keeps suggestions readable
-- while a refresh runs.
CREATE UNIQUE INDEX IF NOT EXISTS uq_search_term ON search_term(term, kind, category_id);
-- Prefix lookups ("ap" -> "apple", "apples"). text_pattern_ops makes LIKE
-- 'ap%' indexable regardless of the database collation.
CREATE INDEX IF NOT EXISTS idx_search_term_prefix ON search_term(term text_pattern_ops);
-- Typo tolerance ("aple" -> "apple") via trigram similarity.
CREATE INDEX IF NOT EXISTS idx_search_term_trgm ON search_term USING gin(term gin_trgm_ops);
