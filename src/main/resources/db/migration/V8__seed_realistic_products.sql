-- Replace the original 2-item demo catalog (Apple, Cracked Eggs) with a much
-- larger, realistic grocery catalog spanning every category created in
-- V1__init_schema.sql, so the storefront looks like a real Amazon/Flipkart-
-- style grocery app instead of a toy demo.
--
-- Images point at placehold.co (a text-on-color placeholder service) rather
-- than hotlinked photos of real branded products: it never 404s, needs no
-- API key, and avoids any question of reusing another retailer's product
-- photography. Swap the `image` column for real product photography later
-- if/when the project has its own image hosting.
--
-- category_id values below match the insertion order in V1: 1 Fruits,
-- 2 Vegetables, 3 Meat, 4 Fish, 5 Dairy, 6 Bakery, 7 Drinks, 8 Sweets, 9 Other.

-- Fruits (category_id = 1)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Crisp and sweet Shimla apples, hand-picked and ready to eat.', 'https://placehold.co/500x500/e53935/ffffff?text=Red+Apples', 'Fresh Red Apples (1kg)', 2.99, 150, 1000, 1),
    ('Juicy Royal Gala apples with a mild, sweet flavor.', 'https://placehold.co/500x500/e53935/ffffff?text=Gala+Apples', 'Royal Gala Apples (1kg)', 3.49, 120, 1000, 1),
    ('Naturally ripened Robusta bananas, rich in potassium.', 'https://placehold.co/500x500/e53935/ffffff?text=Bananas', 'Robusta Bananas (1kg)', 1.49, 200, 1000, 1),
    ('Premium Alphonso mangoes, the king of fruits, in season.', 'https://placehold.co/500x500/e53935/ffffff?text=Alphonso+Mangoes', 'Alphonso Mangoes (1kg)', 8.99, 60, 1000, 1),
    ('Sweet, crunchy seedless green grapes.', 'https://placehold.co/500x500/e53935/ffffff?text=Green+Grapes', 'Seedless Green Grapes (500g)', 3.99, 90, 500, 1),
    ('Plump, deep-red cherries sourced from Kashmir orchards.', 'https://placehold.co/500x500/e53935/ffffff?text=Red+Cherries', 'Kashmiri Red Cherries (500g)', 9.99, 40, 500, 1),
    ('Nagpur oranges, juicy and rich in vitamin C.', 'https://placehold.co/500x500/e53935/ffffff?text=Oranges', 'Sweet Oranges (1kg)', 2.79, 130, 1000, 1),
    ('Fragrant, sweet musk melon, chilled and ready to slice.', 'https://placehold.co/500x500/e53935/ffffff?text=Musk+Melon', 'Musk Melon (1 pc)', 2.49, 70, 1200, 1),
    ('Whole seedless watermelon, red and refreshing.', 'https://placehold.co/500x500/e53935/ffffff?text=Watermelon', 'Watermelon (whole)', 4.99, 50, 4000, 1),
    ('Bhagwa pomegranates bursting with ruby-red arils.', 'https://placehold.co/500x500/e53935/ffffff?text=Pomegranate', 'Pomegranate (500g)', 4.49, 100, 500, 1),
    ('Imported New Zealand kiwis, tangy and vitamin-rich.', 'https://placehold.co/500x500/e53935/ffffff?text=Kiwi', 'Kiwi Fruit (6 pcs)', 5.99, 80, 300, 1),
    ('Golden Queen pineapple, hand-selected for sweetness.', 'https://placehold.co/500x500/e53935/ffffff?text=Pineapple', 'Pineapple (1 pc)', 3.29, 45, 1200, 1);

-- Vegetables (category_id = 2)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Vine-ripened hybrid tomatoes, perfect for cooking.', 'https://placehold.co/500x500/43a047/ffffff?text=Tomatoes', 'Fresh Tomatoes (1kg)', 1.29, 180, 1000, 2),
    ('Pukhraj potatoes, ideal for curries and fries.', 'https://placehold.co/500x500/43a047/ffffff?text=Potatoes', 'Potatoes (1kg)', 0.99, 250, 1000, 2),
    ('Nashik red onions, sharp and flavorful.', 'https://placehold.co/500x500/43a047/ffffff?text=Red+Onions', 'Red Onions (1kg)', 1.09, 220, 1000, 2),
    ('Crisp green bell peppers, great for stir-fries.', 'https://placehold.co/500x500/43a047/ffffff?text=Capsicum', 'Green Capsicum (500g)', 1.99, 100, 500, 2),
    ('Ooty carrots, sweet and crunchy.', 'https://placehold.co/500x500/43a047/ffffff?text=Carrots', 'Carrots (500g)', 1.49, 140, 500, 2),
    ('Fresh white cauliflower head, farm-picked.', 'https://placehold.co/500x500/43a047/ffffff?text=Cauliflower', 'Cauliflower (1 pc)', 1.79, 90, 800, 2),
    ('Tender broccoli florets, rich in fiber.', 'https://placehold.co/500x500/43a047/ffffff?text=Broccoli', 'Broccoli (400g)', 2.99, 70, 400, 2),
    ('Fresh green spinach leaves, washed and bunched.', 'https://placehold.co/500x500/43a047/ffffff?text=Spinach', 'Spinach Bunch (250g)', 0.89, 150, 250, 2),
    ('English cucumbers, crisp and hydrating.', 'https://placehold.co/500x500/43a047/ffffff?text=Cucumber', 'Cucumber (400g)', 1.19, 160, 400, 2),
    ('Shelled fresh green peas, sweet and tender.', 'https://placehold.co/500x500/43a047/ffffff?text=Green+Peas', 'Green Peas (500g)', 2.49, 85, 500, 2),
    ('Tender baby corn, peeled and ready to cook.', 'https://placehold.co/500x500/43a047/ffffff?text=Baby+Corn', 'Baby Corn (250g)', 2.29, 60, 250, 2),
    ('Fresh lauki, light and easy to digest.', 'https://placehold.co/500x500/43a047/ffffff?text=Bottle+Gourd', 'Bottle Gourd (700g)', 1.39, 75, 700, 2);

-- Meat (category_id = 3)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Skinless, boneless chicken breast fillets.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Chicken+Breast', 'Chicken Breast Boneless (500g)', 6.99, 80, 500, 3),
    ('Bone-in chicken thigh cuts.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Chicken+Thighs', 'Chicken Thighs (500g)', 5.49, 90, 500, 3),
    ('Fresh chicken drumsticks, skin-on.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Drumsticks', 'Chicken Drumsticks (500g)', 5.29, 100, 500, 3),
    ('Whole skinless chicken, cleaned and cut.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Whole+Chicken', 'Whole Chicken Skinless (1kg)', 7.99, 50, 1000, 3),
    ('Bone-in goat meat, curry cut pieces.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Mutton+Curry+Cut', 'Mutton Curry Cut (500g)', 12.99, 40, 500, 3),
    ('Tender boneless mutton, trimmed of fat.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Mutton+Boneless', 'Mutton Boneless (500g)', 15.99, 30, 500, 3),
    ('Thick-cut pork chops, bone-in.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Pork+Chops', 'Pork Chops (500g)', 8.49, 35, 500, 3),
    ('Lean turkey breast, thin sliced.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Turkey+Breast', 'Turkey Breast Slices (300g)', 9.99, 25, 300, 3),
    ('Smoked chicken sausages, ready to grill.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Chicken+Sausages', 'Chicken Sausages (400g)', 4.99, 60, 400, 3),
    ('Freshly ground chicken keema.', 'https://placehold.co/500x500/8d6e63/ffffff?text=Chicken+Keema', 'Chicken Mince Keema (500g)', 6.49, 70, 500, 3);

-- Fish (category_id = 4)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Cleaned and cut Rohu fish steaks.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Rohu+Fish', 'Rohu Fish (1kg)', 6.99, 50, 1000, 4),
    ('Whole cleaned silver pomfret.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Pomfret', 'Pomfret Whole (500g)', 9.99, 40, 500, 4),
    ('Boneless basa fish fillets, individually packed.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Basa+Fillet', 'Basa Fillet (500g)', 7.49, 60, 500, 4),
    ('Deveined medium-sized prawns, peeled.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Prawns', 'Prawns Medium (400g)', 11.99, 45, 400, 4),
    ('Fresh Norwegian salmon fillet, skin-on.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Salmon+Fillet', 'Salmon Fillet (300g)', 14.99, 30, 300, 4),
    ('Fresh yellowfin tuna steaks.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Tuna+Steaks', 'Tuna Steaks (300g)', 13.49, 25, 300, 4),
    ('Boneless tilapia fillets, mild flavor.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Tilapia+Fillet', 'Tilapia Fillet (400g)', 6.49, 55, 400, 4),
    ('Fresh Bombil, cleaned and ready to fry.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Bombay+Duck', 'Bombay Duck (500g)', 5.99, 35, 500, 4),
    ('Cleaned mud crab, whole.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Crab', 'Crab Cleaned (600g)', 10.99, 20, 600, 4),
    ('Cleaned squid, cut into rings.', 'https://placehold.co/500x500/1e88e5/ffffff?text=Squid+Rings', 'Squid Rings (400g)', 8.99, 30, 400, 4);

-- Dairy (category_id = 5)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Pasteurized full cream milk, 1 litre pouch.', 'https://placehold.co/500x500/fdd835/333333?text=Full+Cream+Milk', 'Full Cream Milk (1L)', 1.19, 200, 1000, 5),
    ('Toned milk, lighter and lower in fat.', 'https://placehold.co/500x500/fdd835/333333?text=Toned+Milk', 'Toned Milk (1L)', 0.99, 220, 1000, 5),
    ('Thick, fresh dairy curd.', 'https://placehold.co/500x500/fdd835/333333?text=Curd', 'Curd / Yogurt (400g)', 1.29, 150, 400, 5),
    ('Soft, fresh cottage cheese block.', 'https://placehold.co/500x500/fdd835/333333?text=Paneer', 'Paneer (200g)', 3.49, 90, 200, 5),
    ('Creamy salted table butter.', 'https://placehold.co/500x500/fdd835/333333?text=Butter', 'Salted Butter (100g)', 2.99, 100, 100, 5),
    ('Processed cheddar cheese slices, pack of 10.', 'https://placehold.co/500x500/fdd835/333333?text=Cheddar+Slices', 'Cheddar Cheese Slices (200g)', 3.99, 80, 200, 5),
    ('Shreddable mozzarella cheese block.', 'https://placehold.co/500x500/fdd835/333333?text=Mozzarella', 'Mozzarella Cheese Block (200g)', 5.49, 60, 200, 5),
    ('Dairy cream for cooking and desserts.', 'https://placehold.co/500x500/fdd835/333333?text=Fresh+Cream', 'Fresh Cream (200ml)', 1.99, 70, 200, 5),
    ('Thick and creamy plain Greek yogurt.', 'https://placehold.co/500x500/fdd835/333333?text=Greek+Yogurt', 'Greek Yogurt (400g)', 2.79, 65, 400, 5),
    ('Pure clarified butter, traditionally made.', 'https://placehold.co/500x500/fdd835/333333?text=Ghee', 'Ghee (500ml)', 8.99, 55, 500, 5);

-- Bakery (category_id = 6)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Soft whole wheat sandwich loaf.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Wheat+Bread', 'Whole Wheat Bread (400g)', 1.99, 100, 400, 6),
    ('Multigrain loaf with seeds and oats.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Multigrain+Bread', 'Multigrain Bread (400g)', 2.49, 90, 400, 6),
    ('Classic soft white sandwich bread.', 'https://placehold.co/500x500/fb8c00/ffffff?text=White+Bread', 'White Sandwich Bread (400g)', 1.49, 110, 400, 6),
    ('Flaky, buttery croissants, pack of 4.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Croissants', 'Butter Croissant (4 pcs)', 3.99, 60, 240, 6),
    ('Rich chocolate chip muffins, pack of 4.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Choco+Muffin', 'Chocolate Muffin (4 pcs)', 4.49, 55, 280, 6),
    ('Ready-to-bake garlic bread loaf.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Garlic+Bread', 'Garlic Bread Loaf (300g)', 2.99, 65, 300, 6),
    ('Soft sesame burger buns, pack of 4.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Burger+Buns', 'Burger Buns (4 pcs)', 1.99, 80, 240, 6),
    ('New York style plain bagels, pack of 4.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Bagels', 'Plain Bagels (4 pcs)', 3.49, 50, 320, 6),
    ('Soft dinner rolls, pack of 6.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Dinner+Rolls', 'Dinner Rolls (6 pcs)', 2.29, 70, 300, 6),
    ('Light vanilla sponge cake, whole.', 'https://placehold.co/500x500/fb8c00/ffffff?text=Sponge+Cake', 'Vanilla Sponge Cake (500g)', 6.99, 30, 500, 6);

-- Drinks (category_id = 7)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('100% orange juice, no added sugar.', 'https://placehold.co/500x500/00acc1/ffffff?text=Orange+Juice', 'Orange Juice (1L)', 2.99, 100, 1000, 7),
    ('Blend of mixed fruit juices.', 'https://placehold.co/500x500/00acc1/ffffff?text=Fruit+Juice', 'Mixed Fruit Juice (1L)', 2.79, 95, 1000, 7),
    ('Classic carbonated cola drink.', 'https://placehold.co/500x500/00acc1/ffffff?text=Cola', 'Cola Soft Drink (750ml)', 1.49, 150, 750, 7),
    ('Refreshing carbonated lemon soda.', 'https://placehold.co/500x500/00acc1/ffffff?text=Lemon+Soda', 'Lemon Soda (750ml)', 1.29, 140, 750, 7),
    ('Purified packaged drinking water.', 'https://placehold.co/500x500/00acc1/ffffff?text=Drinking+Water', 'Packaged Drinking Water (1L)', 0.49, 300, 1000, 7),
    ('Box of 25 green tea bags.', 'https://placehold.co/500x500/00acc1/ffffff?text=Green+Tea', 'Green Tea Bags (25 ct)', 3.49, 80, 50, 7),
    ('Rich roast instant coffee granules.', 'https://placehold.co/500x500/00acc1/ffffff?text=Instant+Coffee', 'Instant Coffee (100g)', 5.99, 70, 100, 7),
    ('Caffeinated energy drink can.', 'https://placehold.co/500x500/00acc1/ffffff?text=Energy+Drink', 'Energy Drink (250ml)', 2.49, 120, 250, 7),
    ('Natural tender coconut water, tetra pack.', 'https://placehold.co/500x500/00acc1/ffffff?text=Coconut+Water', 'Coconut Water (200ml)', 1.79, 110, 200, 7),
    ('Spiced buttermilk, chilled and ready to drink.', 'https://placehold.co/500x500/00acc1/ffffff?text=Masala+Chaas', 'Masala Chaas (200ml)', 1.09, 90, 200, 7);

-- Sweets (category_id = 8)
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Rich cashew fudge diamonds, 250g box.', 'https://placehold.co/500x500/d81b60/ffffff?text=Kaju+Katli', 'Kaju Katli (250g)', 7.99, 50, 250, 8),
    ('Soft milk-solid dumplings in sugar syrup, tin.', 'https://placehold.co/500x500/d81b60/ffffff?text=Gulab+Jamun', 'Gulab Jamun Tin (1kg)', 4.99, 60, 1000, 8),
    ('Creamy milk chocolate bar.', 'https://placehold.co/500x500/d81b60/ffffff?text=Milk+Chocolate', 'Milk Chocolate Bar (100g)', 1.99, 150, 100, 8),
    ('Intense dark chocolate, 70 percent cocoa.', 'https://placehold.co/500x500/d81b60/ffffff?text=Dark+Chocolate', 'Dark Chocolate 70pct (100g)', 2.99, 100, 100, 8),
    ('Spongy cottage cheese balls in syrup, tin.', 'https://placehold.co/500x500/d81b60/ffffff?text=Rasgulla', 'Rasgulla Tin (1kg)', 4.49, 55, 1000, 8),
    ('Butter, chocolate chip, and oatmeal cookie mix.', 'https://placehold.co/500x500/d81b60/ffffff?text=Cookies', 'Assorted Cookies (300g)', 3.49, 90, 300, 8),
    ('Classic vanilla ice cream tub, 1 litre.', 'https://placehold.co/500x500/d81b60/ffffff?text=Vanilla+Ice+Cream', 'Vanilla Ice Cream Tub (1L)', 4.99, 60, 1000, 8),
    ('Rich chocolate ice cream tub, 1 litre.', 'https://placehold.co/500x500/d81b60/ffffff?text=Choco+Ice+Cream', 'Chocolate Ice Cream Tub (1L)', 4.99, 60, 1000, 8),
    ('Fine gram-flour pearl ladoos, 500g box.', 'https://placehold.co/500x500/d81b60/ffffff?text=Motichoor+Ladoo', 'Motichoor Ladoo (500g)', 5.49, 45, 500, 8),
    ('Moist fruit cake, sliced pack.', 'https://placehold.co/500x500/d81b60/ffffff?text=Fruit+Cake', 'Fruit Cake Slices (350g)', 3.99, 40, 350, 8);

-- Other (category_id = 9) -- pantry staples and eggs; the existing "Cracked Eggs" row already lives here
INSERT INTO product(description, image, name, price, quantity, weight, category_id) VALUES
    ('Half-dozen farm fresh eggs.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Eggs+6-Pack', 'Farm Fresh Eggs (6 pack)', 1.99, 120, 360, 9),
    ('Dozen nutrient-rich brown eggs.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Brown+Eggs', 'Brown Eggs (12 pack)', 3.49, 100, 720, 9),
    ('Long-grain aged basmati rice.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Basmati+Rice', 'Basmati Rice (5kg)', 9.99, 70, 5000, 9),
    ('Split pigeon peas, unpolished.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Toor+Dal', 'Toor Dal (1kg)', 2.49, 90, 1000, 9),
    ('Refined sunflower oil, 1 litre.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Sunflower+Oil', 'Sunflower Cooking Oil (1L)', 3.99, 100, 1000, 9),
    ('Cold-pressed mustard oil, 1 litre.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Mustard+Oil', 'Mustard Oil (1L)', 4.49, 80, 1000, 9),
    ('Whole wheat flour for rotis and bread.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Wheat+Atta', 'Wheat Flour Atta (5kg)', 5.99, 85, 5000, 9),
    ('Refined white granulated sugar.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Sugar', 'Granulated Sugar (1kg)', 1.29, 130, 1000, 9),
    ('Free-flowing iodized table salt.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Salt', 'Iodized Salt (1kg)', 0.59, 150, 1000, 9),
    ('Assorted whole and ground spice masala box.', 'https://placehold.co/500x500/6d4c41/ffffff?text=Masala+Box', 'Mixed Spices Masala Box (200g)', 6.49, 45, 200, 9);
