#!/usr/bin/env python3
"""Merge a manually-confirmed ingredients/instructions override for one recipe.

Used when a recipe's OCR-derived ingredients/instructions are missing or poor
but the original published source (e.g. NYT Cooking) is available to transcribe
from directly. Never touches rawText -- only supplies better structured data
and source confirmation for the next `recipe_export.py` run.

Usage:
    python3 recipe_confirm_content.py R0334 --data pizza_dough_override.json

Where the --data file looks like:
    {
      "title": "Optional cleaned-up title, overrides the raw OCR'd title",
      "ingredients": [
        {"rawText": "...", "quantity": "153", "unit": "grams", "item": "00 flour", "parseStatus": "confirmed"}
      ],
      "instructions": [
        {"order": 1, "text": "...", "parseStatus": "confirmed"}
      ],
      "sourceUrl": "https://cooking.nytimes.com/recipes/...",
      "sourceStatus": "confirmed",
      "note": "Confirmed against NYT Cooking on 2026-08-30."
    }

All fields are optional; only the ones with a real value need to be
supplied. `title`, `sourceUrl`, `sourceStatus`, and `note` can each be set
independently of ingredients/instructions. This merges keys into any
existing override for the recipe rather than replacing it wholesale, so
running this again with just e.g. {"ingredients": [...]} won't wipe out
a `title` or `reviewFlags` set by an earlier call.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recipe_id")
    parser.add_argument("--data", type=Path, required=True, help="JSON file with the override body")
    parser.add_argument(
        "--overrides",
        type=Path,
        default=Path(__file__).with_name("recipe_archive_overrides.json"),
    )
    args = parser.parse_args()

    overrides_path = args.overrides.resolve()
    overrides: dict[str, Any] = json.loads(overrides_path.read_text(encoding="utf-8"))
    overrides.setdefault("recipe_content_overrides", {})

    override_body = json.loads(args.data.read_text(encoding="utf-8"))
    existing = overrides["recipe_content_overrides"].setdefault(args.recipe_id, {})
    existing.update(override_body)

    overrides_path.write_text(json.dumps(overrides, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Merged content override for {args.recipe_id} into {overrides_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
