-- Prepares the catalogue to hold tens of thousands of real products with
-- hybrid (lexical + vector) search, faceted filters and price sorting.
--
-- The central change is where vectors live. V2 added product.embedding and an
-- HNSW index on it, then nothing ever wrote to it: every embedding went to the
-- separate product_embeddings table through LangChain4j's EmbeddingStore. That
-- split caused three problems at once - the store's query wraps the distance in
-- a CTE so pgvector cannot use an ANN index (every search scanned every row),
-- results needed one SELECT per hit to get back to the product, and filtering
-- by price or category during search was impossible because the filters live on
-- a different table. Putting the vector on the product row lets a single
-- statement filter, rank by ANN distance, sort and paginate.

-- ---------------------------------------------------------------------------
-- Product attributes needed for a realistic catalogue
-- ---------------------------------------------------------------------------

-- Real product copy does not fit in 255 characters.
ALTER TABLE product ALTER COLUMN description TYPE TEXT;

ALTER TABLE product ADD COLUMN IF NOT EXISTS brand VARCHAR(120);
ALTER TABLE product ADD COLUMN IF NOT EXISTS rating numeric(2, 1);
ALTER TABLE product ADD COLUMN IF NOT EXISTS rating_count INT DEFAULT 0;

-- Identifier from the source dataset. Unique so a re-import updates rows
-- instead of duplicating them; NULL for the hand-written seed products, and
-- Postgres allows many NULLs under a unique constraint.
ALTER TABLE product ADD COLUMN IF NOT EXISTS external_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_product_external_id ON product(external_id);

ALTER TABLE product ADD CONSTRAINT chk_product_rating_range
    CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5));
ALTER TABLE product ADD CONSTRAINT chk_product_rating_count_non_negative
    CHECK (rating_count IS NULL OR rating_count >= 0);

-- ---------------------------------------------------------------------------
-- Lexical half of hybrid search
-- ---------------------------------------------------------------------------
-- Vector search is weakest at exactly the queries shoppers type most: a brand
-- and model string like "Sony WH-1000XM5". Embeddings blur rare tokens toward
-- their semantic neighbourhood, so the exact product can rank below generic
-- headphones. A tsvector restores exact-token matching, and the two are fused
-- at query time rather than one replacing the other.
--
-- Weighting: name A, brand B, description C, so a term in the title outranks
-- the same term buried in a paragraph.
ALTER TABLE product ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(brand, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'C')
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_product_search_vector ON product USING gin(search_vector);

-- ---------------------------------------------------------------------------
-- Vector half
-- ---------------------------------------------------------------------------
-- V2 built this index with pgvector's defaults (m = 16, ef_construction = 64)
-- on an empty, never-populated column. Rebuild it with a higher build-time
-- candidate list: ef_construction trades one-off index build time for better
-- recall on every subsequent query, which is the right trade for a catalogue
-- written rarely and searched constantly.
DROP INDEX IF EXISTS idx_product_embedding_hnsw;
CREATE INDEX idx_product_embedding_hnsw ON product
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 128);

-- Partial index: browse and filter queries never look at unembedded rows, and
-- during a bulk import most rows have no vector yet.
CREATE INDEX IF NOT EXISTS idx_product_category_price ON product(category_id, price);

-- ---------------------------------------------------------------------------
-- Retire the separate embedding store
-- ---------------------------------------------------------------------------
-- Nothing reads this once retrieval moves to product.embedding. Dropping it
-- also removes the orphan-vector problem: it had no foreign key to product, so
-- deleting a product left its vector behind to be returned by future searches.
DROP TABLE IF EXISTS product_embeddings;

-- ---------------------------------------------------------------------------
-- Amazon-style departments
-- ---------------------------------------------------------------------------
-- The nine grocery categories from V1 stay; these sit alongside them so the
-- existing seed products keep their categories.
INSERT INTO category(name)
SELECT name FROM (VALUES
    ('Electronics'), ('Computers'), ('Mobile Phones'), ('Home & Kitchen'),
    ('Furniture'), ('Clothing'), ('Shoes'), ('Beauty & Personal Care'),
    ('Health & Household'), ('Sports & Outdoors'), ('Toys & Games'),
    ('Books'), ('Movies & TV'), ('Video Games'), ('Automotive'),
    ('Tools & Home Improvement'), ('Office Products'), ('Pet Supplies'),
    ('Baby'), ('Garden & Outdoor'), ('Musical Instruments'), ('Luggage')
) AS incoming(name)
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.name = incoming.name);
