#!/usr/bin/env python3
"""Apply confirmed page joins and build canonical recipe candidates."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sqlite3
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

from recipe_build_archive import batch_sort_key, nearest_text_pairs
from recipe_preprocess import normalized_text


GENERIC_TITLES = re.compile(
    r"^(?:ingredients?|directions?|preparation|method|cooking|recipe|total time|"
    r"salt|about recipe|by:|go back print|e cooking)\b",
    re.IGNORECASE,
)


def load_rows(database: sqlite3.Connection, table: str) -> list[dict[str, Any]]:
    database.row_factory = sqlite3.Row
    return [dict(row) for row in database.execute(f"SELECT * FROM {table}")]


def write_csv(path: Path, rows: list[dict[str, Any]], fields: list[str]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def page_refs(group: dict[str, Any], page_orders: dict[str, list[int]]) -> list[str]:
    order = page_orders.get(group["group_key"])
    pages = order or [int(value) for value in str(group["pages"]).split(",") if value]
    return [f"{group['scan']}#p{page:03d}" for page in pages]


def title_quality(title: str) -> tuple[int, int]:
    normalized = normalized_text(title)
    generic = bool(GENERIC_TITLES.search(title.strip()))
    looks_sentence = len(normalized.split()) > 12
    return (0 if generic or looks_sentence else 1, -abs(len(normalized.split()) - 5))


def union_find(items: list[str]) -> tuple[dict[str, str], Any]:
    parent = {item: item for item in items}

    def find(item: str) -> str:
        while parent[item] != item:
            parent[item] = parent[parent[item]]
            item = parent[item]
        return item

    def union(left: str, right: str) -> None:
        left_root, right_root = find(left), find(right)
        if left_root != right_root:
            parent[right_root] = left_root

    return parent, (find, union)


def arrange(scan_dir: Path, override_path: Path) -> dict[str, Any]:
    archive_dir = scan_dir / ".processed" / "archive"
    database_path = archive_dir / "recipe-archive.sqlite"
    overrides = json.loads(override_path.read_text(encoding="utf-8"))
    page_orders = overrides.get("confirmed_page_order", {})
    merge_rules = overrides.get("confirmed_group_merges", {})
    group_overrides = overrides.get("group_overrides", {})
    duplicate_rules = overrides.get("confirmed_recipe_duplicates", {})
    source_confirmations = overrides.get("source_confirmations", {})
    manual_duplicate_pairs = {
        tuple(sorted((canonical, member)))
        for rule in duplicate_rules.values()
        for canonical in [rule.get("canonical", "")]
        for member in rule.get("members", [])
        if canonical and member != canonical
    }
    rejected_duplicate_pairs = {
        tuple(sorted(pair))
        for pair in overrides.get("rejected_recipe_duplicates", [])
        if len(pair) == 2
    }

    with sqlite3.connect(database_path) as database:
        groups = [
            row for row in load_rows(database, "recipe_groups")
            if row["scan_status"] == "retained"
        ]

    by_key = {row["group_key"]: row for row in groups}
    used_members: set[str] = set()
    arranged: list[dict[str, Any]] = []

    for merge_key, rule in merge_rules.items():
        member_keys = list(rule.get("members", []))
        missing = [key for key in member_keys if key not in by_key]
        if missing:
            raise ValueError(f"Unknown group keys in {merge_key}: {missing}")
        members = [by_key[key] for key in member_keys]
        used_members.update(member_keys)
        refs = [ref for member in members for ref in page_refs(member, page_orders)]
        arranged.append(
            {
                "arrangement_key": f"merged::{merge_key}",
                "title": rule["title"],
                "member_groups": ";".join(member_keys),
                "page_refs": ";".join(refs),
                "text": "\n".join(str(member["text"]) for member in members),
                "word_count": sum(int(member["word_count"]) for member in members),
                "review_flags": ";".join(
                    sorted(
                        {
                            flag
                            for member in members
                            for flag in str(member["review_flags"]).split(";")
                            if flag
                        }
                    )
                ),
                "handwriting_pages": ";".join(
                    f"{member['scan']}#p{int(page):03d}"
                    for member in members
                    for page in str(member["handwriting_pages"]).split(",")
                    if page
                ),
                "publisher": next(
                    (
                        source_confirmations.get(member["group_key"], {}).get("source")
                        or str(member["publisher"])
                        for member in members
                        if source_confirmations.get(member["group_key"], {}).get("source")
                        or member["publisher"]
                    ),
                    "",
                ),
                "domain": next(
                    (str(member["domain"]) for member in members if member["domain"]), ""
                ),
                "source_url": next(
                    (
                        source_confirmations.get(member["group_key"], {}).get("url")
                        or str(member["source_url"])
                        for member in members
                        if source_confirmations.get(member["group_key"], {}).get("url")
                        or member["source_url"]
                    ),
                    "",
                ),
                "source_status": "confirmed"
                if any(member["group_key"] in source_confirmations for member in members)
                else (
                    "printed_url"
                    if any(member["source_url"] for member in members)
                    else "printed_source"
                    if any(member["domain"] or member["publisher"] for member in members)
                    else "unknown"
                ),
                "arrangement_status": "confirmed_merge",
                "canonical_arrangement_key": "",
            }
        )

    for group in groups:
        if group["group_key"] in used_members:
            continue
        group_override = group_overrides.get(group["group_key"], {})
        refs = page_refs(group, page_orders)
        flags = str(group["review_flags"])
        needs_review = bool(
            GENERIC_TITLES.search(str(group["title"]).strip())
            or "partial_or_title_review" in flags
            or (int(group["word_count"]) < 80 and flags)
        )
        arranged.append(
            {
                "arrangement_key": group["group_key"],
                "title": group_override.get("title", group["title"]),
                "member_groups": group["group_key"],
                "page_refs": ";".join(refs),
                "text": group["text"],
                "word_count": group["word_count"],
                "review_flags": flags,
                "handwriting_pages": ";".join(
                    f"{group['scan']}#p{int(page):03d}"
                    for page in str(group["handwriting_pages"]).split(",")
                    if page
                ),
                "publisher": source_confirmations.get(group["group_key"], {}).get(
                    "source", group["publisher"]
                ),
                "domain": group["domain"],
                "source_url": source_confirmations.get(group["group_key"], {}).get(
                    "url", group["source_url"]
                ),
                "source_status": "confirmed"
                if group["group_key"] in source_confirmations
                else "printed_url"
                if group["source_url"]
                else "printed_source"
                if group["domain"] or group["publisher"]
                else "unknown",
                "arrangement_status": group_override.get(
                    "status",
                    "needs_page_review" if needs_review else "single_group",
                ),
                "canonical_arrangement_key": group_override.get(
                    "canonical_arrangement_key", ""
                ),
            }
        )

    def arranged_sort_key(row: dict[str, Any]) -> tuple[Any, ...]:
        first_group = str(row["member_groups"]).split(";", 1)[0]
        scan, _, group_id = first_group.partition("::")
        number = int(re.search(r"(\d+)$", group_id).group(1)) if group_id else 0
        return (*batch_sort_key(scan), number, row["title"])

    arranged.sort(key=arranged_sort_key)
    for number, row in enumerate(arranged, 1):
        row["recipe_id"] = f"R{number:04d}"

    duplicate_rows: list[dict[str, Any]] = []
    parent, operations = union_find([row["recipe_id"] for row in arranged])
    find, union = operations
    active_arranged = [
        row for row in arranged
        if row["arrangement_status"] not in {"non_recipe", "duplicate_fragment"}
    ]
    for left_index, right_index, text_score in nearest_text_pairs(
        active_arranged, text_field="text", minimum_words=35, threshold=0.68
    ):
        left, right = active_arranged[left_index], active_arranged[right_index]
        arrangement_pair = tuple(
            sorted((left["arrangement_key"], right["arrangement_key"]))
        )
        if (
            arrangement_pair in rejected_duplicate_pairs
            or arrangement_pair in manual_duplicate_pairs
        ):
            continue
        title_score = SequenceMatcher(
            None, normalized_text(left["title"]), normalized_text(right["title"])
        ).ratio()
        same_title = normalized_text(left["title"]) == normalized_text(right["title"])
        confirmed = bool(
            (text_score >= 0.94 and title_score >= 0.60)
            or (same_title and text_score >= 0.80)
        )
        if not confirmed and not (text_score >= 0.78 and title_score >= 0.72):
            continue
        status = "confirmed_duplicate_printout" if confirmed else "review"
        duplicate_rows.append(
            {
                "status": status,
                "recipe_a": left["recipe_id"],
                "recipe_b": right["recipe_id"],
                "title_a": left["title"],
                "title_b": right["title"],
                "text_similarity": round(text_score, 4),
                "title_similarity": round(title_score, 4),
                "handwriting_a": left["handwriting_pages"],
                "handwriting_b": right["handwriting_pages"],
            }
        )
        if confirmed:
            union(left["recipe_id"], right["recipe_id"])

    by_arrangement_key = {row["arrangement_key"]: row for row in arranged}
    preferred_canonical_ids: set[str] = set()
    for rule_name, rule in duplicate_rules.items():
        member_keys = list(rule.get("members", []))
        missing = [key for key in member_keys if key not in by_arrangement_key]
        if missing:
            raise ValueError(f"Unknown duplicate members in {rule_name}: {missing}")
        canonical_key = rule.get("canonical", member_keys[0] if member_keys else "")
        if canonical_key not in by_arrangement_key:
            raise ValueError(f"Unknown canonical duplicate member in {rule_name}")
        canonical_row = by_arrangement_key[canonical_key]
        preferred_canonical_ids.add(canonical_row["recipe_id"])
        for member_key in member_keys:
            if member_key == canonical_key:
                continue
            member_row = by_arrangement_key[member_key]
            union(canonical_row["recipe_id"], member_row["recipe_id"])
            duplicate_rows.append(
                {
                    "status": "confirmed_duplicate_printout_manual",
                    "recipe_a": canonical_row["recipe_id"],
                    "recipe_b": member_row["recipe_id"],
                    "title_a": canonical_row["title"],
                    "title_b": member_row["title"],
                    "text_similarity": "",
                    "title_similarity": "",
                    "handwriting_a": canonical_row["handwriting_pages"],
                    "handwriting_b": member_row["handwriting_pages"],
                }
            )

    by_recipe_id = {row["recipe_id"]: row for row in arranged}
    clusters: dict[str, list[str]] = defaultdict(list)
    for recipe_id in by_recipe_id:
        clusters[find(recipe_id)].append(recipe_id)
    canonical_by_member: dict[str, str] = {}
    for members in clusters.values():
        preferred = [member for member in members if member in preferred_canonical_ids]
        candidates = preferred or members
        canonical = max(
            candidates,
            key=lambda recipe_id: (
                title_quality(str(by_recipe_id[recipe_id]["title"])),
                int(by_recipe_id[recipe_id]["word_count"]),
                bool(by_recipe_id[recipe_id]["handwriting_pages"]),
            ),
        )
        for member in members:
            canonical_by_member[member] = canonical

    for row in arranged:
        if row["arrangement_status"] == "non_recipe":
            row["canonical_recipe_id"] = ""
            row["duplicate_status"] = "excluded_non_recipe"
            continue
        if row["arrangement_status"] == "duplicate_fragment":
            canonical_key = row["canonical_arrangement_key"]
            canonical_row = by_arrangement_key.get(canonical_key)
            if not canonical_row:
                raise ValueError(
                    f"Unknown canonical arrangement for {row['arrangement_key']}: "
                    f"{canonical_key}"
                )
            row["canonical_recipe_id"] = canonical_row["recipe_id"]
            row["duplicate_status"] = "duplicate_fragment"
            continue
        canonical = canonical_by_member[row["recipe_id"]]
        row["canonical_recipe_id"] = canonical
        row["duplicate_status"] = (
            "canonical" if canonical == row["recipe_id"] else "duplicate_printout"
        )

    arranged_fields = [
        "recipe_id", "canonical_recipe_id", "duplicate_status", "title",
        "arrangement_status", "member_groups", "page_refs", "word_count",
        "review_flags", "handwriting_pages", "publisher", "domain",
        "source_url", "source_status",
    ]
    duplicate_fields = [
        "status", "recipe_a", "recipe_b", "title_a", "title_b",
        "text_similarity", "title_similarity", "handwriting_a", "handwriting_b",
    ]
    write_csv(archive_dir / "arranged-recipes.csv", arranged, arranged_fields)
    write_csv(archive_dir / "duplicate-printouts.csv", duplicate_rows, duplicate_fields)
    (archive_dir / "arranged-recipes.json").write_text(
        json.dumps(arranged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    with sqlite3.connect(database_path) as database:
        database.executescript(
            """
            DROP TABLE IF EXISTS arranged_recipes;
            DROP TABLE IF EXISTS duplicate_printouts;
            CREATE TABLE arranged_recipes (
                recipe_id TEXT PRIMARY KEY, canonical_recipe_id TEXT,
                duplicate_status TEXT, title TEXT, arrangement_status TEXT,
                member_groups TEXT, page_refs TEXT, text TEXT, word_count INTEGER,
                review_flags TEXT, handwriting_pages TEXT, publisher TEXT, domain TEXT,
                source_url TEXT, source_status TEXT
            );
            CREATE TABLE duplicate_printouts (
                status TEXT, recipe_a TEXT, recipe_b TEXT, title_a TEXT, title_b TEXT,
                text_similarity REAL, title_similarity REAL,
                handwriting_a TEXT, handwriting_b TEXT
            );
            """
        )
        database.executemany(
            """INSERT INTO arranged_recipes VALUES
            (:recipe_id,:canonical_recipe_id,:duplicate_status,:title,:arrangement_status,
             :member_groups,:page_refs,:text,:word_count,:review_flags,
             :handwriting_pages,:publisher,:domain,:source_url,:source_status)""",
            arranged,
        )
        database.executemany(
            """INSERT INTO duplicate_printouts VALUES
            (:status,:recipe_a,:recipe_b,:title_a,:title_b,:text_similarity,
             :title_similarity,:handwriting_a,:handwriting_b)""",
            duplicate_rows,
        )
        database.commit()

    summary = {
        "arranged_recipe_records": len(arranged),
        "confirmed_page_merges": len(merge_rules),
        "reversed_page_orders": len(page_orders),
        "confirmed_duplicate_links": sum(
            str(row["status"]).startswith("confirmed_duplicate_printout")
            for row in duplicate_rows
        ),
        "duplicate_links_needing_review": sum(
            row["status"] == "review" for row in duplicate_rows
        ),
        "canonical_recipes_after_confirmed_duplicates": sum(
            row["duplicate_status"] == "canonical"
            and row["arrangement_status"] not in {"non_recipe", "duplicate_fragment"}
            for row in arranged
        ),
        "arrangements_needing_page_review": sum(
            row["arrangement_status"] == "needs_page_review" for row in arranged
        ),
    }
    (archive_dir / "arrangement-summary.json").write_text(
        json.dumps(summary, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(summary, indent=2))
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scan_dir", type=Path)
    parser.add_argument(
        "--overrides",
        type=Path,
        default=Path(__file__).with_name("recipe_archive_overrides.json"),
    )
    args = parser.parse_args()
    arrange(args.scan_dir.expanduser().resolve(), args.overrides.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
