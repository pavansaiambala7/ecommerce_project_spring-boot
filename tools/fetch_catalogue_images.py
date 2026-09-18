#!/usr/bin/env python3
"""Builds tools/catalogue_images.json: real photographs for every product line.

Why not Amazon's product images: they belong to Amazon and the brands, and
Amazon's Conditions of Use forbid reusing them. These two sources are free to use:

* DummyJSON (https://dummyjson.com) - product shots on white backgrounds, published
  for exactly this purpose: demo shops and prototypes. Used where a line has a
  close match (phones, laptops, shirts, dresses, sneakers, kitchenware).
* StockSnap via Openverse (https://openverse.org) - modern stock photographs
  released under CC0, so free for any use without attribution. Openverse is
  queried because it is a public API meant for programmatic search; StockSnap
  alone is used because its images are web-sized and photographic, where other
  CC0 sources return scans, clip art or multi-megabyte originals.

Search results are not trustworthy on their own: a stock search for "baby wipes"
returned a bull, and "car battery" a clock. The output must be reviewed by eye and
photos that do not show the product removed - the committed file records that
review, and re-running this script discards it.

A photo shows the kind of product, not the exact model: a "Redmi Note 13" gets a
real smartphone photo, not that phone. The file stores URLs only - no image is
copied into the repository.

    python fetch_catalogue_images.py
    python fetch_catalogue_images.py --merge "line:Baby|Diaper Pants=diapers"   # retry a few

Anonymous Openverse access allows 200 requests a day, so lines that look alike
share one query and each distinct query is sent once.
"""

from __future__ import annotations

import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).with_name("catalogue_images.json")
USER_AGENT = "ShopKart-catalogue-builder/1.0 (+https://github.com/pavansaiambala7/ecommerce_project_spring-boot)"
PHOTOS_PER_QUERY = 6

# DummyJSON product titles, by what they can illustrate.
DUMMY = {
    "brand:Mobiles|Apple": ["iPhone 5s", "iPhone 6", "iPhone 13 Pro", "iPhone X"],
    "brand:Mobiles|Samsung": ["Samsung Galaxy S7", "Samsung Galaxy S8", "Samsung Galaxy S10"],
    "brand:Mobiles|OPPO": ["Oppo A57", "Oppo F19 Pro Plus", "Oppo K1"],
    "brand:Mobiles|realme": ["Realme C35", "Realme X", "Realme XT"],
    "brand:Mobiles|vivo": ["Vivo X21"],
    "dept:Mobiles": ["Oppo A57", "Oppo F19 Pro Plus", "Oppo K1", "Realme C35", "Realme X",
                     "Realme XT", "Vivo X21", "Samsung Galaxy S10"],
    "brand:Laptops|Apple": ["Apple MacBook Pro 14 Inch Space Grey"],
    "dept:Laptops": ["Asus Zenbook Pro Dual Screen Laptop", "Huawei Matebook X Pro",
                     "Lenovo Yoga 920", "New DELL XPS 13 9300 Laptop"],
    "line:Men's Fashion|Slim Fit Shirt": ["Blue & Black Check Shirt", "Men Check Shirt"],
    "line:Men's Fashion|Casual Shirt": ["Man Plaid Shirt", "Man Short Sleeve Shirt"],
    "line:Women's Fashion|Maxi Dress": ["Black Women's Gown", "Gray Dress"],
    "line:Women's Fashion|A-Line Dress": ["Blue Frock", "Short Frock", "Tartan Dress"],
    "line:Women's Fashion|Wrap Dress": ["Dress Pea", "Girl Summer Dress"],
    "line:Women's Fashion|Bodycon Dress": ["Corset With Black Skirt", "Corset Leather With Skirt"],
    "line:Footwear|Running Shoes": ["Puma Future Rider Trainers", "Sports Sneakers Off White & Red"],
    "line:Footwear|Sneakers": ["Nike Air Jordan 1 Red And Black", "Sports Sneakers Off White Red"],
    "line:Footwear|Walking Shoes": ["Puma Future Rider Trainers"],
    "line:Footwear|Flip Flops": ["Black & Brown Slipper"],
    "line:Home & Kitchen|Mixer Grinder": ["Boxed Blender", "Hand Blender"],
    "line:Home & Kitchen|Kadai": ["Carbon Steel Wok"],
    "line:Home & Kitchen|Non-Stick Tawa": ["Pan"],
    "line:Home & Kitchen|Casserole Set": ["Silver Pot With Glass Cap"],
    "line:Home & Kitchen|Induction Cooktop": ["Electric Stove"],
    "line:Home & Kitchen|Lunch Box": ["Lunch Box"],
    "line:Home & Kitchen|Dinner Set": ["Plate", "Tray"],
    "line:Furniture|Office Chair": ["Knoll Saarinen Executive Conference Chair"],
    "line:Furniture|Queen Mattress": ["Annibale Colombo Bed"],
    "line:Beauty & Personal Care|Lipstick": ["Red Lipstick"],
    "line:Beauty & Personal Care|Kajal": ["Essence Mascara Lash Princess"],
    "line:Sports & Outdoors|Cricket Bat": ["Cricket Bat", "Cricket Ball"],
    "line:Sports & Outdoors|Football": ["Football"],
    "line:Pet Supplies|Cat Food": ["Cat Food"],
    "line:Pet Supplies|Adult Dry Food": ["Dog Food"],
    "line:Pet Supplies|Puppy Food": ["Dog Food"],
    "line:Bags & Luggage|Laptop Backpack": ["White Faux Leather Backpack"],
}

# Stock photo queries. A line appearing here and in DUMMY gets both.
QUERIES = {
    # Electronics
    "line:Electronics|WH Headphones": "headphones",
    "line:Electronics|Bravia Smart TV": "television",
    "line:Electronics|Smart TV": "television",
    "line:Electronics|SRS Speaker": "bluetooth speaker",
    "line:Electronics|Speaker": "bluetooth speaker",
    "line:Electronics|Stone Speaker": "bluetooth speaker",
    "line:Electronics|Flip": "bluetooth speaker",
    "line:Electronics|Go": "bluetooth speaker",
    "line:Electronics|Charge": "bluetooth speaker",
    "line:Electronics|SoundLink": "bluetooth speaker",
    "line:Electronics|Emberton": "bluetooth speaker",
    "line:Electronics|Acton": "bluetooth speaker",
    "line:Electronics|Partybox": "bluetooth speaker",
    "line:Electronics|Soundbar": "soundbar",
    "line:Electronics|Rockerz": "headphones",
    "line:Electronics|Tune": "headphones",
    "line:Electronics|Headphone": "headphones",
    "line:Electronics|Major": "headphones",
    "line:Electronics|QuietComfort": "headphones",
    "line:Electronics|Airdopes": "earbuds",
    "line:Electronics|Buds": "earbuds",
    "line:Electronics|Air Buds": "earbuds",
    "line:Electronics|Galaxy Buds": "earbuds",
    "line:Electronics|Wave Smartwatch": "smartwatch",
    "line:Electronics|ColorFit": "smartwatch",
    "line:Electronics|Smart Band": "smartwatch",
    "line:Electronics|Luna Ring": "smartwatch",
    "line:Electronics|Trimmer": "beard trimmer",
    "line:Electronics|Air Fryer": "air fryer",
    "line:Electronics|Steam Iron": "clothes iron",
    "line:Electronics|Audio System": "bluetooth speaker",
    "line:Electronics|Monitor": "computer monitor",
    "line:Electronics|Air Purifier": "air purifier",
    "line:Electronics|Power Bank": "electronics gadgets",
    "line:Electronics|Keyboard": "mechanical keyboard",
    "line:Electronics|Charger": "electronics gadgets",
    "line:Electronics|Webcam": "electronics gadgets",
    # Men's fashion
    "line:Men's Fashion|Slim Fit Shirt": "shirt",
    "line:Men's Fashion|Casual Shirt": "shirt",
    "line:Men's Fashion|Polo T-Shirt": "t-shirt",
    "line:Men's Fashion|Round Neck T-Shirt": "t-shirt",
    "line:Men's Fashion|Formal Trousers": "trousers",
    "line:Men's Fashion|Chinos": "trousers",
    "line:Men's Fashion|Regular Fit Jeans": "jeans",
    "line:Men's Fashion|Slim Fit Jeans": "jeans",
    "line:Men's Fashion|Kurta Pyjama Set": "suit",
    "line:Men's Fashion|Nehru Jacket": "suit",
    "line:Men's Fashion|Blazer": "suit",
    "line:Men's Fashion|Hoodie": "hoodie",
    "line:Men's Fashion|Track Pants": "trousers",
    "line:Men's Fashion|Cargo Shorts": "shorts men",
    # Women's fashion
    "line:Women's Fashion|Anarkali Kurta": "indian woman dress",
    "line:Women's Fashion|Straight Kurta": "indian woman dress",
    "line:Women's Fashion|Printed Kurti": "indian woman dress",
    "line:Women's Fashion|Banarasi Saree": "saree",
    "line:Women's Fashion|Cotton Saree": "saree",
    "line:Women's Fashion|Sharara Set": "saree",
    "line:Women's Fashion|Lehenga Choli": "saree",
    "line:Women's Fashion|Palazzo Set": "indian woman dress",
    "line:Women's Fashion|Crop Top": "crop top",
    "line:Women's Fashion|Wide Leg Jeans": "jeans",
    "line:Women's Fashion|Co-ord Set": "women fashion",
    # Footwear
    "line:Footwear|Formal Derby": "shoes",
    "line:Footwear|Sports Sandals": "sandals",
    "line:Footwear|Trekking Boots": "hiking boots",
    "line:Footwear|Loafers": "shoes",
    "line:Footwear|Walking Shoes": "shoes",
    # Home & Kitchen
    "line:Home & Kitchen|Pressure Cooker": "pressure cooker",
    "line:Home & Kitchen|Water Bottle": "steel water bottle",
    "line:Home & Kitchen|Electric Kettle": "electric kettle",
    "line:Home & Kitchen|Dinner Set": "kitchen utensils",
    "line:Home & Kitchen|Casserole Set": "kitchen utensils",
    # Furniture
    "line:Furniture|Office Chair": "office chair",
    "line:Furniture|Study Table": "study desk",
    "line:Furniture|Queen Mattress": "bed mattress bedroom",
    "line:Furniture|Shoe Rack": "shoe rack",
    "line:Furniture|Bookshelf": "bookshelf",
    "line:Furniture|Folding Bed": "day bed",
    "line:Furniture|TV Unit": "tv stand living room",
    "line:Furniture|Wardrobe": "wardrobe",
    # Beauty
    "line:Beauty & Personal Care|Face Wash": "cosmetics",
    "line:Beauty & Personal Care|Vitamin C Serum": "cosmetics",
    "line:Beauty & Personal Care|Sunscreen SPF 50": "cosmetics",
    "line:Beauty & Personal Care|Body Lotion": "cosmetics",
    "line:Beauty & Personal Care|Hair Oil": "cosmetics",
    "line:Beauty & Personal Care|Lipstick": "lipstick",
    "line:Beauty & Personal Care|Kajal": "makeup",
    "line:Beauty & Personal Care|Face Cream": "cosmetics",
    "line:Beauty & Personal Care|Shampoo": "cosmetics",
    "line:Beauty & Personal Care|Ubtan Face Mask": "cosmetics",
    # Sports
    "line:Sports & Outdoors|Badminton Racket": "badminton racket",
    "line:Sports & Outdoors|Yoga Mat": "yoga mat",
    "line:Sports & Outdoors|Dumbbell Set": "dumbbells",
    "line:Sports & Outdoors|Skipping Rope": "fitness equipment",
    "line:Sports & Outdoors|Gym Gloves": "fitness equipment",
    "line:Sports & Outdoors|Cycling Helmet": "bicycle helmet",
    "line:Sports & Outdoors|Football": "football ball",
    # Toys
    "line:Toys & Games|Building Blocks": "toys",
    "line:Toys & Games|Board Game": "board game",
    "line:Toys & Games|Jigsaw Puzzle": "jigsaw puzzle",
    "line:Toys & Games|Remote Control Car": "toys",
    "line:Toys & Games|Soft Toy": "teddy bear",
    "line:Toys & Games|Learning Tablet": "toys",
    "line:Toys & Games|Play Kitchen": "toys",
    "line:Toys & Games|Card Game": "playing cards",
    # Books
    "line:Books|Paperback Edition": "books",
    "line:Books|Hardcover Edition": "books",
    "line:Books|Illustrated Edition": "books",
    # Automotive
    "line:Automotive|Engine Oil": "car",
    "line:Automotive|Car Battery": "car",
    "line:Automotive|Wiper Blades": "car",
    "line:Automotive|Car Cover": "car",
    "line:Automotive|Tyre Inflator": "car",
    "line:Automotive|Dashboard Polish": "car",
    "line:Automotive|Riding Helmet": "motorcycle helmet",
    # Office
    "line:Office Products|Notebook": "notebook stationery",
    "line:Office Products|Gel Pen Set": "pens",
    "line:Office Products|Sticky Notes": "office supplies",
    "line:Office Products|File Folder": "office supplies",
    "line:Office Products|Whiteboard Marker": "office supplies",
    "line:Office Products|Stapler": "office supplies",
    "line:Office Products|Desk Organiser": "office supplies",
    # Pets
    "line:Pet Supplies|Adult Dry Food": "dog",
    "line:Pet Supplies|Puppy Food": "dog",
    "line:Pet Supplies|Cat Food": "cat",
    "line:Pet Supplies|Chew Toy": "dog",
    "line:Pet Supplies|Pet Shampoo": "dog",
    "line:Pet Supplies|Collar": "dog",
    "line:Pet Supplies|Feeding Bowl": "dog",
    # Health & household
    "line:Health & Household|Disinfectant Liquid": "cleaning supplies",
    "line:Health & Household|Floor Cleaner": "cleaning supplies",
    "line:Health & Household|Toilet Cleaner": "cleaning supplies",
    "line:Health & Household|Detergent Powder": "laundry",
    "line:Health & Household|Dishwash Gel": "cleaning supplies",
    "line:Health & Household|Hand Sanitiser": "cleaning supplies",
    "line:Health & Household|Glass Cleaner": "cleaning supplies",
    # Baby
    "line:Baby|Diaper Pants": "baby",
    "line:Baby|Baby Wipes": "baby",
    "line:Baby|Baby Lotion": "baby",
    "line:Baby|Feeding Bottle": "baby",
    "line:Baby|Baby Carrier": "baby",
    "line:Baby|Sterilizer": "baby",
    "line:Baby|Baby Powder": "baby",
    # Bags & luggage
    "line:Bags & Luggage|Cabin Trolley": "luggage",
    "line:Bags & Luggage|Check-in Trolley": "luggage",
    "line:Bags & Luggage|Laptop Backpack": "backpack",
    "line:Bags & Luggage|Duffel Bag": "luggage",
    "line:Bags & Luggage|Travel Organiser": "luggage",
    # Musical instruments
    "line:Musical Instruments|Acoustic Guitar": "acoustic guitar",
    "line:Musical Instruments|Digital Piano": "digital piano",
    "line:Musical Instruments|Tabla Set": "musical instruments",
    "line:Musical Instruments|Harmonium": "musical instruments",
    "line:Musical Instruments|Ukulele": "ukulele",
    "line:Musical Instruments|Cajon": "musical instruments",
    "line:Musical Instruments|Electric Guitar": "electric guitar",
    # Tools
    "line:Tools & Home Improvement|Cordless Drill": "cordless drill",
    "line:Tools & Home Improvement|Screwdriver Set": "tools",
    "line:Tools & Home Improvement|Angle Grinder": "tools",
    "line:Tools & Home Improvement|Measuring Tape": "measuring tape",
    "line:Tools & Home Improvement|Tool Kit": "tools",
    "line:Tools & Home Improvement|Hammer": "tools",
    "line:Tools & Home Improvement|Wrench Set": "tools",
    # Garden
    "line:Garden & Outdoor|Seed Kit": "gardening",
    "line:Garden & Outdoor|Ceramic Planter": "ceramic planter",
    "line:Garden & Outdoor|Garden Hose": "gardening",
    "line:Garden & Outdoor|Pruning Shears": "gardening",
    "line:Garden & Outdoor|Organic Fertiliser": "gardening",
    "line:Garden & Outdoor|Grow Bag": "gardening",
    "line:Garden & Outdoor|Watering Can": "watering can",
    # Movies & TV
    "line:Movies & TV|Blu-ray": "cinema",
    "line:Movies & TV|DVD Collection": "cinema",
    "line:Movies & TV|Limited Edition Box Set": "cinema",
    "line:Movies & TV|4K Ultra HD": "cinema",
    "line:Movies & TV|Collectors Edition": "cinema",
    "line:Movies & TV|Complete Season Set": "cinema",
    # Video games
    "line:Video Games|PS5 Game": "video games",
    "line:Video Games|Xbox Series X Game": "video games",
    "line:Video Games|Nintendo Switch Game": "video games",
    "line:Video Games|Wireless Controller": "game controller",
    "line:Video Games|Gaming Headset": "gaming headset",
}

# Every department also gets general photos, used for any line whose own query
# found too little. Broad subjects, because stock libraries cover them well.
DEPARTMENT_QUERIES = {
    "dept:Electronics": "electronics gadgets",
    "dept:Men's Fashion": "men fashion",
    "dept:Women's Fashion": "women fashion",
    "dept:Footwear": "shoes",
    "dept:Home & Kitchen": "kitchen utensils",
    "dept:Furniture": "furniture",
    "dept:Beauty & Personal Care": "cosmetics",
    "dept:Sports & Outdoors": "fitness equipment",
    "dept:Toys & Games": "toys",
    "dept:Books": "books",
    "dept:Automotive": "car",
    "dept:Office Products": "office supplies",
    "dept:Pet Supplies": "dog",
    "dept:Health & Household": "cleaning supplies",
    "dept:Baby": "baby",
    "dept:Bags & Luggage": "luggage",
    "dept:Musical Instruments": "musical instruments",
    "dept:Tools & Home Improvement": "tools",
    "dept:Garden & Outdoor": "gardening",
    "dept:Movies & TV": "cinema",
    "dept:Video Games": "video games",
}

# Fewer results than this and a line uses its department's photos instead:
# two photos repeated across two thousand products look like a bug.
MIN_PHOTOS = 3


def get_json(url: str, headers: dict | None = None):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **(headers or {})})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def dummyjson_images() -> dict[str, list[str]]:
    data = get_json("https://dummyjson.com/products?limit=0&select=title,images")
    by_title = {p["title"]: p["images"] for p in data["products"]}
    out = {}
    for key, titles in DUMMY.items():
        images = []
        for title in titles:
            if title not in by_title:
                sys.exit(f"DummyJSON has no product titled {title!r} (for {key})")
            images.extend(by_title[title])
        out[key] = images
    return out


def stock_search(query: str) -> list[dict]:
    params = urllib.parse.urlencode({
        "q": query, "license": "cc0", "source": "stocksnap",
        "page_size": 20, "mature": "false",
    })
    data = get_json(f"https://api.openverse.org/v1/images/?{params}")
    photos = []
    for result in data.get("results", []):
        url = result.get("url") or ""
        if not url.startswith("https://cdn.stocksnap.io/"):
            continue
        photos.append({"url": url, "credit": f'{result.get("title", "")} - StockSnap, CC0 '
                                            f'({result.get("foreign_landing_url", "")})'})
        if len(photos) == PHOTOS_PER_QUERY:
            break
    return photos


def merge(pairs: list[str]) -> int:
    """Runs only the given key=query pairs and adds their photos to the existing file.

    For retrying lines that found nothing, without spending the day's request
    allowance re-running every query.
    """
    data = json.loads(OUT.read_text(encoding="utf-8"))
    for pair in pairs:
        key, _, query = pair.partition("=")
        photos = stock_search(query)
        time.sleep(3.2)
        if len(photos) < MIN_PHOTOS:
            print(f"  {key}: only {len(photos)} for {query!r}, unchanged", file=sys.stderr)
            continue
        existing = data["images"].setdefault(key, [])
        for photo in photos:
            if photo["url"] not in existing:
                existing.append(photo["url"])
                data["credits"][photo["url"]] = photo["credit"]
        print(f"  {key}: now {len(existing)} photos", file=sys.stderr)
    OUT.write_text(json.dumps(data, indent=1, ensure_ascii=False), encoding="utf-8")
    return 0


def main() -> int:
    if len(sys.argv) > 2 and sys.argv[1] == "--merge":
        return merge(sys.argv[2:])

    result: dict[str, list[str]] = {}
    credits: dict[str, str] = {}

    for key, images in dummyjson_images().items():
        result.setdefault(key, []).extend(images)
    print(f"DummyJSON: {len(DUMMY)} keys", file=sys.stderr)

    wanted = {**DEPARTMENT_QUERIES, **QUERIES}
    cache: dict[str, list[dict]] = {}
    for i, (key, query) in enumerate(wanted.items(), 1):
        if query not in cache:
            try:
                cache[query] = stock_search(query)
            except Exception as error:  # the line falls back to its department
                print(f"  [{i}/{len(wanted)}] {query!r}: FAILED {error}", file=sys.stderr)
                cache[query] = []
            # Openverse allows 20 anonymous requests a minute.
            time.sleep(3.2)
        photos = cache[query]
        if len(photos) < MIN_PHOTOS:
            print(f"  [{i}/{len(wanted)}] {key}: only {len(photos)} for {query!r}, using fallback",
                  file=sys.stderr)
            continue
        for photo in photos:
            result.setdefault(key, []).append(photo["url"])
            credits[photo["url"]] = photo["credit"]

    OUT.write_text(json.dumps({"images": result, "credits": credits}, indent=1, ensure_ascii=False),
                   encoding="utf-8")
    print(f"Wrote {sum(map(len, result.values()))} image URLs for {len(result)} keys "
          f"({len(cache)} distinct queries) to {OUT}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
