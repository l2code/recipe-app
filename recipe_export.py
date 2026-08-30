#!/usr/bin/env python3
"""Export the canonical recipe archive as a versioned app-import bundle.

The archive SQLite database remains the review/source database. This exporter
creates a stable, read-only handoff for the Android app without discarding raw
OCR, scan provenance, duplicate relationships, or unresolved review states.
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1

SECTION_RE = re.compile(r"^\s*(ingredients?|directions?|instructions?|method|preparation)\s*:?\s*$", re.I)
STEP_RE = re.compile(r"^\s*(?:step\s*)?(\d{1,2})[.)-]\s*(.+)$", re.I)
QUANTITY_RE = re.compile(
    r"^\s*(?P<quantity>(?:\d+\s+)?\d+(?:[./]\d+)?|\d+\s*/\s*\d+|[¼½¾⅓⅔⅛⅜⅝⅞])"
    r"\s*(?P<unit>cups?|tbsps?|tablespoons?|tsps?|teaspoons?|ounces?|oz|pounds?|lbs?|g|kg|ml|l|cloves?|cans?|packages?|pieces?)?\s*(?P<item>.*)$",
    re.I,
)


def rows(database: sqlite3.Connection, query: str) -> list[dict[str, Any]]:
    database.row_factory = sqlite3.Row
    return [dict(row) for row in database.execute(query)]


def split_refs(value: Any) -> list[str]:
    if value is None:
        return []
    return [item for item in str(value).split(";") if item]


def split_csv(value: Any) -> list[str]:
    if value is None:
        return []
    return [item.strip() for item in str(value).split(",") if item.strip()]


def structured_recipe_text(raw_text: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Make conservative ingredient/step candidates from OCR text."""
    ingredients: list[dict[str, Any]] = []
    instructions: list[dict[str, Any]] = []
    section = ""
    step_order = 1
    for raw_line in raw_text.splitlines():
        line = " ".join(raw_line.split()).strip(" :-")
        if not line:
            continue
        heading = SECTION_RE.match(line)
        if heading:
            section = heading.group(1).lower()
            continue
        if section.startswith("ingredient"):
            match = QUANTITY_RE.match(line)
            if match:
                ingredients.append({
                    "rawText": line,
                    "quantity": match.group("quantity").replace(" ", ""),
                    "unit": (match.group("unit") or "").lower(),
                    "item": match.group("item").strip(),
                    "parseStatus": "candidate",
                })
            elif len(line) > 2:
                ingredients.append({"rawText": line, "quantity": "", "unit": "", "item": line, "parseStatus": "needs_review"})
        elif section in {"directions", "instructions", "method", "preparation"}:
            match = STEP_RE.match(line)
            if match:
                step_order = int(match.group(1))
                text = match.group(2).strip()
            else:
                text = line
            instructions.append({"order": step_order, "text": text, "parseStatus": "candidate"})
            step_order += 1
    return ingredients, instructions


def load_recipe_content_overrides(overrides_path: Path | None) -> dict[str, dict[str, Any]]:
    """Manually confirmed ingredients/instructions, keyed by recipe id.

    These come from the recipe's original published source (e.g. NYT Cooking)
    when OCR of a scanned printout produced poor or empty structured data.
    Never used to overwrite `rawText`; only to supply better `ingredients`,
    `instructions`, and source confirmation for the app-import bundle.
    """
    if overrides_path is None or not overrides_path.exists():
        return {}
    data = json.loads(overrides_path.read_text(encoding="utf-8"))
    return data.get("recipe_content_overrides", {})


def export_bundle(
    database_path: Path,
    output_path: Path,
    overrides_path: Path | None = None,
) -> dict[str, Any]:
    content_overrides = load_recipe_content_overrides(overrides_path)
    with sqlite3.connect(database_path) as database:
        recipes = rows(
            database,
            """
            SELECT recipe_id, canonical_recipe_id, title, arrangement_status,
                   duplicate_status, member_groups, page_refs, text, word_count,
                   review_flags, handwriting_pages, publisher, domain, source_url,
                   source_status
            FROM arranged_recipes
            WHERE duplicate_status = 'canonical'
            ORDER BY lower(title), recipe_id
            """,
        )
        source_review = {
            row["group_key"]: row
            for row in rows(
                database,
                """
                SELECT group_key, title, page_number, publisher_candidate,
                       domain_candidate, evidence, url_candidate, search_query,
                       confirmed_source, confirmed_url, status
                FROM source_review
                """,
            )
        }
        handwriting = rows(
            database,
            """
            SELECT page_id, label, scan, page, reasons, word_count, image_path,
                   ocr_draft, transcription, status
            FROM handwriting_review
            ORDER BY scan, page
            """,
        )

    notes_by_page = {item["page_id"]: item for item in handwriting}
    exported: list[dict[str, Any]] = []
    for row in recipes:
        member_groups = split_csv(row["member_groups"])
        source_rows = [source_review[key] for key in member_groups if key in source_review]
        page_refs = split_refs(row["page_refs"])
        page_notes = [
            notes_by_page[page_ref]
            for page_ref in page_refs
            if page_ref in notes_by_page
        ]
        ingredients, instructions = structured_recipe_text(row["text"] or "")
        override = content_overrides.get(row["recipe_id"], {})
        if override.get("ingredients"):
            ingredients = override["ingredients"]
        if override.get("instructions"):
            instructions = override["instructions"]
        evidence = [item["evidence"] for item in source_rows if item["evidence"]]
        if override.get("note"):
            evidence = evidence + [override["note"]]
        exported.append(
            {
                "id": row["recipe_id"],
                "title": row["title"] or "Untitled recipe",
                "rawText": row["text"] or "",
                "ingredients": ingredients,
                "instructions": instructions,
                "wordCount": row["word_count"] or 0,
                "pageRefs": page_refs,
                "arrangementStatus": row["arrangement_status"],
                "duplicateStatus": row["duplicate_status"],
                "reviewFlags": split_csv(row["review_flags"]),
                "handwritingPageRefs": split_csv(row["handwriting_pages"]),
                "source": {
                    "publisher": row["publisher"] or "",
                    "domain": row["domain"] or "",
                    "url": override.get("sourceUrl") or row["source_url"] or "",
                    "status": override.get("sourceStatus") or row["source_status"] or "needs_lookup",
                    "evidence": evidence,
                },
                "handwrittenNotes": [
                    {
                        "pageId": item["page_id"],
                        "scan": item["scan"],
                        "page": item["page"],
                        "imagePath": item["image_path"],
                        "ocrDraft": item["ocr_draft"] or "",
                        "transcription": item["transcription"] or "",
                        "status": item["status"],
                        "reasons": split_csv(item["reasons"]),
                    }
                    for item in page_notes
                ],
            }
        )

    bundle = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": {
            "database": str(database_path),
            "canonicalRecipeCount": len(exported),
            "contract": "Raw OCR and scan references are retained; structured parsing is a later import step.",
        },
        "recipes": exported,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return bundle


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--overrides",
        type=Path,
        default=Path(__file__).with_name("recipe_archive_overrides.json"),
    )
    args = parser.parse_args()
    database = args.database.expanduser().resolve()
    output = (args.output or database.parent / "recipe-app-import.json").expanduser().resolve()
    bundle = export_bundle(database, output, args.overrides.resolve())
    print(f"Exported {bundle['source']['canonicalRecipeCount']} canonical recipes to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
