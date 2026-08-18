-- Add vector embedding column to product for RAG search
ALTER TABLE product ADD COLUMN IF NOT EXISTS embedding vector(768);

-- Create HNSW index for fast cosine similarity search on embeddings
CREATE INDEX IF NOT EXISTS idx_product_embedding_hnsw
    ON product USING hnsw (embedding vector_cosine_ops);

-- Create product_embeddings table for LangChain4j embedding store
CREATE TABLE IF NOT EXISTS product_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(768),
    text TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create HNSW index on the embedding store table
CREATE INDEX IF NOT EXISTS idx_product_embeddings_hnsw
    ON product_embeddings USING hnsw (embedding vector_cosine_ops);
