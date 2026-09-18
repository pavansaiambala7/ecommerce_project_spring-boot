-- Real photographs for the hand-written grocery products from V8, which have
-- shown coloured text placeholders since they were seeded.
--
-- Photos come from DummyJSON (https://dummyjson.com), a catalogue published for
-- demo shops. Only exact matches are used - a product with no photo of the same
-- thing keeps its placeholder rather than getting a merely similar one (no beef
-- photo for mutton, no branded coffee jar for unbranded instant coffee).
--
-- Only placeholders are replaced, so an image an administrator has already set
-- is left alone.

UPDATE product p
   SET image = photo.url
  FROM (VALUES
    ('Fresh Red Apples (1kg)',        'https://cdn.dummyjson.com/product-images/groceries/apple/1.webp'),
    ('Royal Gala Apples (1kg)',       'https://cdn.dummyjson.com/product-images/groceries/apple/1.webp'),
    ('Kiwi Fruit (6 pcs)',            'https://cdn.dummyjson.com/product-images/groceries/kiwi/1.webp'),
    ('Cucumber (400g)',               'https://cdn.dummyjson.com/product-images/groceries/cucumber/1.webp'),
    ('Green Capsicum (500g)',         'https://cdn.dummyjson.com/product-images/groceries/green-bell-pepper/1.webp'),
    ('Potatoes (1kg)',                'https://cdn.dummyjson.com/product-images/groceries/potatoes/1.webp'),
    ('Red Onions (1kg)',              'https://cdn.dummyjson.com/product-images/groceries/red-onions/1.webp'),
    ('Chicken Breast Boneless (500g)', 'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/1.webp'),
    ('Chicken Thighs (500g)',         'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/2.webp'),
    ('Whole Chicken Skinless (1kg)',  'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/1.webp'),
    ('Salmon Fillet (300g)',          'https://cdn.dummyjson.com/product-images/groceries/fish-steak/1.webp'),
    ('Tuna Steaks (300g)',            'https://cdn.dummyjson.com/product-images/groceries/fish-steak/1.webp'),
    ('Full Cream Milk (1L)',          'https://cdn.dummyjson.com/product-images/groceries/milk/1.webp'),
    ('Toned Milk (1L)',               'https://cdn.dummyjson.com/product-images/groceries/milk/1.webp'),
    ('Chocolate Ice Cream Tub (1L)',  'https://cdn.dummyjson.com/product-images/groceries/ice-cream/2.webp'),
    ('Vanilla Ice Cream Tub (1L)',    'https://cdn.dummyjson.com/product-images/groceries/ice-cream/1.webp'),
    ('Mixed Fruit Juice (1L)',        'https://cdn.dummyjson.com/product-images/groceries/juice/1.webp'),
    ('Orange Juice (1L)',             'https://cdn.dummyjson.com/product-images/groceries/juice/1.webp'),
    ('Cola Soft Drink (750ml)',       'https://cdn.dummyjson.com/product-images/groceries/soft-drinks/1.webp'),
    ('Packaged Drinking Water (1L)',  'https://cdn.dummyjson.com/product-images/groceries/water/1.webp'),
    ('Basmati Rice (5kg)',            'https://cdn.dummyjson.com/product-images/groceries/rice/1.webp'),
    ('Brown Eggs (12 pack)',          'https://cdn.dummyjson.com/product-images/groceries/eggs/1.webp'),
    ('Farm Fresh Eggs (6 pack)',      'https://cdn.dummyjson.com/product-images/groceries/eggs/1.webp'),
    ('Sunflower Cooking Oil (1L)',    'https://cdn.dummyjson.com/product-images/groceries/cooking-oil/1.webp')
  ) AS photo(name, url)
 WHERE p.name = photo.name
   AND p.external_id IS NULL
   AND p.image LIKE 'https://placehold.co/%';
