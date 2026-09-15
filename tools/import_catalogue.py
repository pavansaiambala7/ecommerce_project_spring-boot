#!/usr/bin/env python3
"""Build a product catalogue CSV from the Amazon Reviews 2023 metadata.

The dataset (McAuley Lab, UCSD) ships one gzipped JSONL file of product
metadata per department, with real titles, brands, prices, categories and
image URLs. This script samples a fixed number of usable rows from each
department, normalises them onto our schema, and writes a CSV that Postgres
COPY can load.

Why this is a standalone script rather than a Flyway migration:

  * Tens of thousands of INSERT statements in a migration would run on every
    fresh database, including every Testcontainers-backed test, turning a
    fast suite into a slow one for no benefit.
  * The dataset is licensed for research use, so redistributing it inside the
    repository would not be appropriate. Only this loader is committed.

V8's hand-written products stay as the seed for tests and local development.

Usage:
    python import_catalogue.py --out catalogue.csv --per-category 2500

Then load it, and embed:
    \\copy product (external_id,name,description,image,price,quantity,weight,brand,rating,rating_count,category_id) \\
        FROM 'catalogue.csv' WITH (FORMAT csv, HEADER true)
    POST /api/search/reindex        # embeds everything with no vector yet
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import random
import re
import sys
from pathlib import Path
from urllib.request import urlopen

BASE_URL = ("https://mcauleylab.ucsd.edu/public_datasets/data/amazon_2023/"
            "raw/meta_categories/meta_{category}.jsonl.gz")

# Dataset category -> the category name seeded by V9. Anything not listed here
# is skipped rather than guessed at, so nothing lands in the wrong department.
CATEGORY_MAP = {
    "Electronics": "Electronics",
    "Computers": "Computers",
    "Cell_Phones_and_Accessories": "Mobile Phones",
    "Home_and_Kitchen": "Home & Kitchen",
    "Furniture": "Furniture",
    "Clothing_Shoes_and_Jewelry": "Clothing",
    "Beauty_and_Personal_Care": "Beauty & Personal Care",
    "Health_and_Household": "Health & Household",
    "Sports_and_Outdoors": "Sports & Outdoors",
    "Toys_and_Games": "Toys & Games",
    "Books": "Books",
    "Movies_and_TV": "Movies & TV",
    "Video_Games": "Video Games",
    "Automotive": "Automotive",
    "Tools_and_Home_Improvement": "Tools & Home Improvement",
    "Office_Products": "Office Products",
    "Pet_Supplies": "Pet Supplies",
    "Baby_Products": "Baby",
    "Patio_Lawn_and_Garden": "Garden & Outdoor",
    "Musical_Instruments": "Musical Instruments",
}

PRICE_PATTERN = re.compile(r"\d+(?:\.\d+)?")
MAX_PRICE = 5000.0
MAX_DESCRIPTION = 2000


def parse_price(raw) -> float | None:
    """Prices arrive as '$19.99', 'from $10', floats, or absent."""
    if raw is None:
        return None
    if isinstance(raw, (int, float)):
        value = float(raw)
    else:
        match = PRICE_PATTERN.search(str(raw))
        if not match:
            return None
        value = float(match.group())
    # Reject nonsense rather than letting a $2,000,000 listing distort every
    # price filter and sort in the storefront.
    if value <= 0 or value > MAX_PRICE:
        return None
    return round(value, 2)


def clean_text(value, limit: int) -> str:
    if isinstance(value, list):
        value = " ".join(str(v) for v in value)
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    return text[:limit]


def first_image(entry) -> str | None:
    for image in entry.get("images") or []:
        for key in ("large", "hi_res", "thumb"):
            if image.get(key):
                return image[key]
    return None


def rows_from(category_key: str, wanted: int, rng: random.Random):
    """Yield normalised rows for one department."""
    url = BASE_URL.format(category=category_key)
    kept = 0
    seen_titles = set()

    print(f"  fetching {category_key}...", file=sys.stderr)
    with urlopen(url) as response, gzip.open(response, "rt", encoding="utf-8") as stream:
        for line in stream:
            if kept >= wanted:
                break
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue

            title = clean_text(entry.get("title"), 255)
            price = parse_price(entry.get("price"))
            image = first_image(entry)

            # A product with no title, price or image is not presentable in a
            # storefront, and a catalogue full of blanks looks broken.
            if not title or price is None or not image:
                continue

            key = title.lower()
            if key in seen_titles:
                continue
            seen_titles.add(key)

            rating = entry.get("average_rating")
            yield {
                "external_id": entry.get("parent_asin") or entry.get("asin"),
                "name": title,
                "description": clean_text(entry.get("description"), MAX_DESCRIPTION) or title,
                "image": image,
                "price": price,
                # The dataset carries no inventory, so stock is synthesised.
                # A tenth out of stock keeps the "in stock only" filter and the
                # disabled Add to Cart path visible in a demo.
                "quantity": 0 if rng.random() < 0.1 else rng.randint(1, 400),
                "weight": rng.randint(50, 5000),
                "brand": clean_text(entry.get("store"), 120) or None,
                "rating": round(float(rating), 1) if rating else None,
                "rating_count": int(entry.get("rating_number") or 0),
                "category_name": CATEGORY_MAP[category_key],
            }
            kept += 1

    print(f"    kept {kept}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path("catalogue.csv"))
    parser.add_argument("--per-category", type=int, default=2500,
                        help="rows to keep per department (20 departments)")
    parser.add_argument("--seed", type=int, default=20240914,
                        help="fixes the synthesised stock and weights so reruns match")
    parser.add_argument("--categories", nargs="*", default=sorted(CATEGORY_MAP),
                        help="subset of dataset categories to pull")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    fields = ["external_id", "name", "description", "image", "price", "quantity",
              "weight", "brand", "rating", "rating_count", "category_name"]

    total = 0
    with args.out.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for category_key in args.categories:
            if category_key not in CATEGORY_MAP:
                print(f"  skipping unknown category {category_key}", file=sys.stderr)
                continue
            try:
                for row in rows_from(category_key, args.per_category, rng):
                    writer.writerow(row)
                    total += 1
            except Exception as error:
                # One unavailable department should not lose the whole run.
                print(f"  {category_key} failed: {error}", file=sys.stderr)

    print(f"\nWrote {total} products to {args.out}", file=sys.stderr)
    print("\nLoad with psql:\n"
          "  CREATE TEMP TABLE staging (LIKE product INCLUDING DEFAULTS);\n"
          "  -- then map category_name -> category_id on insert; see tools/load_catalogue.sql",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
