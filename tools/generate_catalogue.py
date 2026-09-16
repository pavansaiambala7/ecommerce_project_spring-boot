#!/usr/bin/env python3
"""Generates a large, realistic Indian product catalogue as CSV.

Prices are in whole rupees, which is how Indian retail actually quotes them -
nobody sells a phone at 79,999.50. Categories, brands and model naming come
from tools/catalogue_taxonomy.py; Faker supplies only the variation, because
Faker alone produces things like "Handcrafted Steel Chair" that read as fake
the moment anyone looks at them.

Why this is a standalone script rather than a Flyway migration: fifty thousand
INSERT statements would run on every fresh database, including every
Testcontainers-backed test, turning a fast suite into a slow one for no
benefit. V8's hand-written grocery products stay as the seed for tests and
local development; this is an operational load against a deployed database.

    pip install faker
    python generate_catalogue.py --out catalogue.csv --count 50000

Then load it with tools/load_catalogue.sql and embed the new rows with
POST /api/search/reindex.
"""

from __future__ import annotations

import argparse
import csv
import random
import sys
from pathlib import Path

try:
    from faker import Faker
except ImportError:
    sys.exit("faker is not installed. Run: pip install faker")

from catalogue_taxonomy import CATALOGUE, SERIES

# Placeholder images, colour-coded per department. Real product photography
# would have to come from somewhere licensed; these never 404 and make the
# grid look deliberate rather than broken.
DEPARTMENT_COLOURS = {
    "Mobile Phones": "1e88e5", "Electronics": "3949ab", "Computers": "5e35b1",
    "Clothing": "d81b60", "Shoes": "8d6e63", "Home & Kitchen": "00897b",
    "Furniture": "6d4c41", "Beauty & Personal Care": "ec407a",
    "Sports & Outdoors": "43a047", "Toys & Games": "fb8c00", "Books": "795548",
    "Automotive": "455a64", "Office Products": "0277bd", "Pet Supplies": "7cb342",
    "Health & Household": "00acc1", "Baby": "f06292", "Luggage": "5d4037",
    "Musical Instruments": "ad1457", "Tools & Home Improvement": "f57c00",
    "Garden & Outdoor": "2e7d32", "Movies & TV": "424242", "Video Games": "6a1b9a",
}


def rupee_price(low: int, high: int, rng: random.Random) -> int:
    """Prices a product the way Indian retail does.

    Two things make a price list look real. It is log-uniform, because a
    department has many cheap items and few expensive ones - drawing uniformly
    would put as many phones above a lakh as below fifteen thousand. And the
    result lands on a charm price: 1,299 and 24,999, never 1,304.
    """
    # Log-uniform draw.
    exponent = rng.uniform(0, 1)
    raw = low * ((high / low) ** exponent)

    if raw < 500:
        rounded = round(raw / 10) * 10 - 1          # 149, 249, 349
    elif raw < 5000:
        rounded = round(raw / 100) * 100 - 1        # 1299, 2499
    elif raw < 50000:
        rounded = round(raw / 500) * 500 - 1        # 14999, 24999
    else:
        rounded = round(raw / 1000) * 1000 - 100    # 79900, 129900

    return max(low, min(int(rounded), high))


def build_product(department: str, spec: dict, rng: random.Random, fake: Faker,
                  index: int) -> dict:
    # Departments whose product lines belong to a specific brand declare
    # brand_lines; the rest share one pool, which is correct for them - any
    # clothing label can sell a cotton saree, and both Prestige and Hawkins
    # genuinely sell pressure cookers.
    branded_lines = "brand_lines" in spec
    if branded_lines:
        brand = rng.choice(list(spec["brand_lines"]))
        line = rng.choice(spec["brand_lines"][brand])
    else:
        brand = rng.choice(spec["brands"])
        line = rng.choice(spec["lines"])

    # Some attributes only make sense for one brand - Apple has not shipped an
    # Intel chip in years, so the shared pool would describe a machine that
    # does not exist.
    attribute_pool = spec.get("brand_attributes", {}).get(brand, spec["attributes"])
    variant = rng.choice(spec["variants"])
    attribute = rng.choice(attribute_pool)
    colour = rng.choice(spec["colours"])
    # A real phone or laptop has no invented series word between brand and
    # line - "Apple Orbit iPhone" reads as generated. Departments with branded
    # lines already have enough combinations without it.
    series = "" if branded_lines else rng.choice(SERIES)

    # Model naming follows each category's conventions: phones get a numeric
    # generation, clothing does not.
    # A line that already starts with its brand should not repeat it:
    # "realme realme P" reads as a bug, not a product.
    prefix = "" if line.lower().startswith(brand.lower()) else brand

    if department == "Mobile Phones":
        # Phones carry a generation number; laptops do not - a MacBook is a
        # "MacBook Pro 14", never a "MacBook Pro 8 14".
        model = f"{rng.randint(3, 15)}"
        name_parts = [prefix, line, model, variant, f"({attribute}, {colour})"]
    elif department in ("Computers", "Video Games"):
        name_parts = [prefix, series, line, variant, f"({attribute}, {colour})"]
    elif department == "Books":
        name_parts = [fake.sentence(nb_words=rng.randint(2, 5)).rstrip("."), "-", series, line]
    elif colour:
        name_parts = [prefix, series, colour, line, variant, f"({attribute})"]
    else:
        name_parts = [prefix, series, line, variant, f"({attribute})"]

    name = " ".join(part for part in name_parts if part).replace("  ", " ").strip()
    name = name[:255]

    fields = {"brand": brand, "line": line, "variant": variant,
              "attribute": attribute, "colour": colour, "series": series}
    for key, options in spec.get("extras", {}).items():
        fields[key] = rng.choice(options)
    description = spec["blurb"].format(**fields)[:2000]

    # A line-specific band wins where one exists: a department band wide enough
    # for televisions also permits absurd earbuds.
    low, high = spec.get("line_prices", {}).get(line, spec["price"])
    price = rupee_price(low, high, rng)
    label = line.replace(" ", "+")[:22]
    colour_hex = DEPARTMENT_COLOURS.get(department, "555555")

    # Ratings cluster high, as they do on every real marketplace, and a
    # product with few ratings should not look as trusted as one with
    # thousands - so the count is skewed too.
    rating = round(min(5.0, rng.gauss(4.1, 0.5)), 1)
    rating = max(2.5, rating)
    rating_count = int(rng.paretovariate(1.3) * 30)

    return {
        "external_id": f"gen-{index:07d}",
        "name": name,
        "description": description,
        "image": f"https://placehold.co/500x500/{colour_hex}/ffffff?text={label}",
        "price": price,
        # A tenth out of stock keeps the "in stock only" filter and the
        # disabled Add to Cart path visible in a demo.
        "quantity": 0 if rng.random() < 0.1 else rng.randint(1, 500),
        "weight": rng.randint(spec["weight"][0], spec["weight"][1]),
        "brand": brand,
        "rating": rating,
        "rating_count": rating_count,
        "category_name": department,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path("catalogue.csv"))
    parser.add_argument("--count", type=int, default=50000,
                        help="total products across all departments")
    parser.add_argument("--seed", type=int, default=20260916,
                        help="fixed so a rerun reproduces the same catalogue")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    fake = Faker("en_IN")
    Faker.seed(args.seed)

    departments = list(CATALOGUE)
    per_department = args.count // len(departments)
    remainder = args.count - per_department * len(departments)

    fields = ["external_id", "name", "description", "image", "price", "quantity",
              "weight", "brand", "rating", "rating_count", "category_name"]

    written = 0
    seen_names: set[str] = set()

    with args.out.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()

        for position, department in enumerate(departments):
            spec = CATALOGUE[department]
            target = per_department + (1 if position < remainder else 0)
            made = 0
            attempts = 0

            # Duplicate names look careless in a grid and make search results
            # ambiguous, so collisions are retried rather than accepted. The
            # attempt cap stops a small department from looping forever once
            # its combinations are exhausted.
            while made < target and attempts < target * 12:
                attempts += 1
                product = build_product(department, spec, rng, fake, written + 1)
                if product["name"].lower() in seen_names:
                    continue
                seen_names.add(product["name"].lower())
                writer.writerow(product)
                written += 1
                made += 1

            print(f"  {department:<28} {made:>6}", file=sys.stderr)

    print(f"\nWrote {written} products to {args.out}", file=sys.stderr)
    if written < args.count:
        print(f"Short of {args.count} - some departments ran out of distinct "
              f"combinations. Add brands or lines to the taxonomy to go higher.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
