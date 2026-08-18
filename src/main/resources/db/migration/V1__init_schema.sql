-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
-- Enable pg_trgm for trigram-based text search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create the category table
CREATE TABLE IF NOT EXISTS category (
    category_id SERIAL PRIMARY KEY,
    name VARCHAR(255)
);

-- Create the customer table
CREATE TABLE IF NOT EXISTS customer (
    id SERIAL PRIMARY KEY,
    address VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(255),
    username VARCHAR(255) UNIQUE
);

-- Create the product table
CREATE TABLE IF NOT EXISTS product (
    product_id SERIAL PRIMARY KEY,
    description VARCHAR(255),
    image VARCHAR(255),
    name VARCHAR(255),
    price INT,
    quantity INT,
    weight INT,
    category_id INT REFERENCES category(category_id),
    customer_id INT REFERENCES customer(id)
);

-- Create the cart table
CREATE TABLE IF NOT EXISTS cart (
    id SERIAL PRIMARY KEY,
    customer_id INT REFERENCES customer(id)
);

-- Create the cart_product table
CREATE TABLE IF NOT EXISTS cart_product (
    cart_id INT REFERENCES cart(id),
    product_id INT REFERENCES product(product_id),
    PRIMARY KEY (cart_id, product_id)
);

-- Create indexes for product lookups
CREATE INDEX idx_product_category ON product(category_id);
CREATE INDEX idx_product_customer ON product(customer_id);

-- Insert default categories
INSERT INTO category(name) VALUES
    ('Fruits'), ('Vegetables'), ('Meat'), ('Fish'),
    ('Dairy'), ('Bakery'), ('Drinks'), ('Sweets'), ('Other');

-- Insert default customers (passwords will be BCrypt encoded at runtime)
INSERT INTO customer(address, email, password, role, username) VALUES
    ('123, Albany Street', 'admin@nyan.cat', '123', 'ROLE_ADMIN', 'admin'),
    ('765, 5th Avenue', 'lisa@gmail.com', '765', 'ROLE_NORMAL', 'lisa');

-- Insert default products
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Fresh and juicy', 'https://freepngimg.com/save/9557-apple-fruit-transparent/744x744', 'Apple', 3, 40, 76, 1),
    ('Woops! There goes the eggs...', 'https://www.nicepng.com/png/full/813-8132637_poiata-bunicii-cracked-egg.png', 'Cracked Eggs', 1, 90, 43, 9);
