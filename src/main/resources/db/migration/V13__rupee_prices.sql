-- Moves the hand-written seed catalogue onto rupee pricing.
--
-- V8 priced groceries in dollars: apples at 2.99, salmon at 14.99. Next to a
-- generated catalogue quoting phones at 79,900 those rows read as a bug -
-- either apples cost three rupees or phones cost eighty thousand dollars, and
-- both readings are wrong.
--
-- Not a currency conversion: these are what the items actually cost in Indian
-- retail, which is not the dollar figure times a rate. Milk is far cheaper
-- here than the dollar price implies; imported salmon is not.

UPDATE product SET price = CASE name
    WHEN 'Apple'                          THEN 180
    WHEN 'Cracked Eggs'                   THEN 70
    WHEN 'Fresh Red Apples (1kg)'         THEN 180
    WHEN 'Royal Gala Apples (1kg)'        THEN 320
    WHEN 'Robusta Bananas (1kg)'          THEN 60
    WHEN 'Alphonso Mangoes (1kg)'         THEN 650
    WHEN 'Seedless Green Grapes (500g)'   THEN 120
    WHEN 'Kashmiri Red Cherries (500g)'   THEN 450
    WHEN 'Sweet Oranges (1kg)'            THEN 90
    WHEN 'Musk Melon (1 pc)'              THEN 80
    WHEN 'Watermelon (whole)'             THEN 120
    WHEN 'Pomegranate (500g)'             THEN 160
    WHEN 'Kiwi Fruit (6 pcs)'             THEN 220
    WHEN 'Pineapple (1 pc)'               THEN 90
    WHEN 'Fresh Tomatoes (1kg)'           THEN 40
    WHEN 'Potatoes (1kg)'                 THEN 35
    WHEN 'Red Onions (1kg)'               THEN 45
    WHEN 'Green Capsicum (500g)'          THEN 50
    WHEN 'Carrots (500g)'                 THEN 40
    WHEN 'Cauliflower (1 pc)'             THEN 45
    WHEN 'Broccoli (400g)'                THEN 90
    WHEN 'Spinach Bunch (250g)'           THEN 25
    WHEN 'Cucumber (400g)'                THEN 30
    WHEN 'Green Peas (500g)'              THEN 70
    WHEN 'Baby Corn (250g)'               THEN 60
    WHEN 'Bottle Gourd (700g)'            THEN 35
    WHEN 'Chicken Breast Boneless (500g)' THEN 320
    WHEN 'Chicken Thighs (500g)'          THEN 240
    WHEN 'Chicken Drumsticks (500g)'      THEN 220
    WHEN 'Whole Chicken Skinless (1kg)'   THEN 380
    WHEN 'Mutton Curry Cut (500g)'        THEN 480
    WHEN 'Mutton Boneless (500g)'         THEN 620
    WHEN 'Pork Chops (500g)'              THEN 420
    WHEN 'Turkey Breast Slices (300g)'    THEN 550
    WHEN 'Chicken Sausages (400g)'        THEN 260
    WHEN 'Chicken Mince Keema (500g)'     THEN 300
    WHEN 'Rohu Fish (1kg)'                THEN 320
    WHEN 'Pomfret Whole (500g)'           THEN 640
    WHEN 'Basa Fillet (500g)'             THEN 380
    WHEN 'Prawns Medium (400g)'           THEN 520
    WHEN 'Salmon Fillet (300g)'           THEN 980
    WHEN 'Tuna Steaks (300g)'             THEN 820
    WHEN 'Tilapia Fillet (400g)'          THEN 340
    WHEN 'Bombay Duck (500g)'             THEN 280
    WHEN 'Crab Cleaned (600g)'            THEN 560
    WHEN 'Squid Rings (400g)'             THEN 440
    WHEN 'Full Cream Milk (1L)'           THEN 70
    WHEN 'Toned Milk (1L)'                THEN 56
    WHEN 'Curd / Yogurt (400g)'           THEN 45
    WHEN 'Paneer (200g)'                  THEN 95
    WHEN 'Salted Butter (100g)'           THEN 62
    WHEN 'Cheddar Cheese Slices (200g)'   THEN 180
    WHEN 'Mozzarella Cheese Block (200g)' THEN 240
    WHEN 'Fresh Cream (200ml)'            THEN 75
    WHEN 'Greek Yogurt (400g)'            THEN 130
    WHEN 'Ghee (500ml)'                   THEN 340
    WHEN 'Whole Wheat Bread (400g)'       THEN 55
    WHEN 'Multigrain Bread (400g)'        THEN 70
    WHEN 'White Sandwich Bread (400g)'    THEN 45
    WHEN 'Butter Croissant (4 pcs)'       THEN 160
    WHEN 'Chocolate Muffin (4 pcs)'       THEN 140
    WHEN 'Garlic Bread Loaf (300g)'       THEN 110
    WHEN 'Burger Buns (4 pcs)'            THEN 45
    WHEN 'Plain Bagels (4 pcs)'           THEN 150
    WHEN 'Dinner Rolls (6 pcs)'           THEN 60
    WHEN 'Vanilla Sponge Cake (500g)'     THEN 320
    WHEN 'Orange Juice (1L)'              THEN 140
    WHEN 'Mixed Fruit Juice (1L)'         THEN 120
    WHEN 'Cola Soft Drink (750ml)'        THEN 45
    WHEN 'Lemon Soda (750ml)'             THEN 40
    WHEN 'Packaged Drinking Water (1L)'   THEN 20
    WHEN 'Green Tea Bags (25 ct)'         THEN 180
    WHEN 'Instant Coffee (100g)'          THEN 320
    WHEN 'Energy Drink (250ml)'           THEN 110
    WHEN 'Coconut Water (200ml)'          THEN 35
    WHEN 'Masala Chaas (200ml)'           THEN 20
    WHEN 'Kaju Katli (250g)'              THEN 420
    WHEN 'Gulab Jamun Tin (1kg)'          THEN 280
    WHEN 'Milk Chocolate Bar (100g)'      THEN 90
    WHEN 'Dark Chocolate 70pct (100g)'    THEN 160
    WHEN 'Rasgulla Tin (1kg)'             THEN 260
    WHEN 'Assorted Cookies (300g)'        THEN 150
    WHEN 'Vanilla Ice Cream Tub (1L)'     THEN 220
    WHEN 'Chocolate Ice Cream Tub (1L)'   THEN 240
    WHEN 'Motichoor Ladoo (500g)'         THEN 300
    WHEN 'Fruit Cake Slices (350g)'       THEN 180
    WHEN 'Farm Fresh Eggs (6 pack)'       THEN 45
    WHEN 'Brown Eggs (12 pack)'           THEN 110
    WHEN 'Basmati Rice (5kg)'             THEN 720
    WHEN 'Toor Dal (1kg)'                 THEN 165
    WHEN 'Sunflower Cooking Oil (1L)'     THEN 145
    WHEN 'Mustard Oil (1L)'               THEN 175
    WHEN 'Wheat Flour Atta (5kg)'         THEN 260
    WHEN 'Granulated Sugar (1kg)'         THEN 48
    WHEN 'Iodized Salt (1kg)'             THEN 28
    WHEN 'Mixed Spices Masala Box (200g)' THEN 260
    ELSE price
END
WHERE external_id IS NULL;
