-- Loads the CSV produced by tools/generate_catalogue.py into the product table.
--
-- Run against the target database, from the directory holding catalogue.csv:
--     psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f load_catalogue.sql
--
-- \copy is a client-side command: it reads the file from whichever machine runs
-- psql, relative to that machine's working directory. It also does not expand
-- psql variables, so the filename below is deliberately fixed. For a database
-- in a container, put the CSV inside the container and point psql's working
-- directory at it with docker exec -w:
--
--     docker cp catalogue.csv ecommerce-db:/tmp/
--     docker exec -w /tmp -i ecommerce-db \
--         psql -U postgres -d ecommjava -v ON_ERROR_STOP=1 < load_catalogue.sql
--
-- Idempotent: re-running updates existing rows by external_id rather than
-- inserting duplicates, so an interrupted load can simply be repeated.

BEGIN;

CREATE TEMP TABLE staging (
    external_id   varchar(64),
    name          varchar(255),
    description   text,
    image         varchar(255),
    price         numeric(12,2),
    quantity      int,
    weight        int,
    brand         varchar(120),
    rating        numeric(2,1),
    rating_count  int,
    category_name text
) ON COMMIT DROP;

\copy staging FROM 'catalogue.csv' WITH (FORMAT csv, HEADER true)

-- Drop anything whose department is not in the category table rather than
-- letting it land under a wrong or null category.
DELETE FROM staging s
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.name = s.category_name);

INSERT INTO product (external_id, name, description, image, price, quantity,
                     weight, brand, rating, rating_count, category_id)
SELECT s.external_id, s.name, s.description, s.image, s.price, s.quantity,
       s.weight, s.brand, s.rating, s.rating_count, c.category_id
FROM staging s
JOIN category c ON c.name = s.category_name
WHERE s.external_id IS NOT NULL
ON CONFLICT (external_id) DO UPDATE SET
    name         = EXCLUDED.name,
    description  = EXCLUDED.description,
    image        = EXCLUDED.image,
    price        = EXCLUDED.price,
    quantity     = EXCLUDED.quantity,
    brand        = EXCLUDED.brand,
    rating       = EXCLUDED.rating,
    rating_count = EXCLUDED.rating_count,
    category_id  = EXCLUDED.category_id,
    -- Force re-embedding: the text changed, so the stored vector no longer
    -- describes this product. The incremental reindex picks up NULLs.
    embedding    = NULL;

COMMIT;

ANALYZE product;

SELECT count(*) AS products,
       count(embedding) AS embedded,
       count(*) - count(embedding) AS awaiting_embedding
FROM product;
