#!/usr/bin/env python3
"""Add human-readable batch numbers to scan PDFs and processed outputs."""

from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path
from typing import Any

from recipe_preprocess import write_catalog, write_markdown_index


BATCH_PREFIX = re.compile(r"^Batch_(\d+)__")
REPEATED_BATCH_PREFIX = re.compile(r"(Batch_(\d+)__)(?:Batch_\2__)+")


def replace_strings(value: Any, replacements: list[tuple[str, str]]) -> Any:
    if isinstance(value, str):
        for old, new in replacements:
            if old in value:
                value = value.replace(old, new)
                break
        return REPEATED_BATCH_PREFIX.sub(r"\1", value)
    if isinstance(value, list):
        return [replace_strings(item, replacements) for item in value]
    if isinstance(value, dict):
        return {
            key: replace_strings(item, replacements) for key, item in value.items()
        }
    return value


def selected_pdfs(
    scan_dir: Path,
    first_filename: str,
    count: int,
) -> list[Path]:
    pdfs = sorted(scan_dir.glob("*.pdf"), key=lambda path: path.stat().st_mtime_ns)
    first_path = scan_dir / first_filename
    try:
        start = pdfs.index(first_path)
    except ValueError as exc:
        raise ValueError(f"Starting PDF not found: {first_path}") from exc

    selected = pdfs[start : start + count]
    if len(selected) != count:
        raise ValueError(f"Expected {count} PDFs from {first_filename}, found {len(selected)}")
    already_tagged = [path.name for path in selected if BATCH_PREFIX.match(path.name)]
    if already_tagged:
        raise ValueError("Selected PDFs are already tagged: " + ", ".join(already_tagged))
    return selected


def build_mapping(
    pdfs: list[Path],
    labels: list[str],
) -> list[tuple[Path, Path]]:
    if len(pdfs) != len(labels):
        raise ValueError("Each selected PDF must have one label")
    mapping = []
    for old_path, label in zip(pdfs, labels):
        filename_label = re.sub(r"[^A-Za-z0-9]+", "_", label).strip("_")
        if not filename_label:
            raise ValueError(f"Invalid empty label: {label!r}")
        new_path = old_path.with_name(f"{filename_label}__{old_path.name}")
        if new_path.exists():
            raise FileExistsError(f"Destination already exists: {new_path}")
        mapping.append((old_path, new_path))
    return mapping


def migrate_processed_outputs(
    out_root: Path,
    mapping: list[tuple[Path, Path]],
) -> None:
    for old_pdf, new_pdf in mapping:
        old_dir = out_root / old_pdf.stem
        new_dir = out_root / new_pdf.stem
        if old_dir.exists():
            if new_dir.exists():
                raise FileExistsError(f"Processed destination already exists: {new_dir}")
            old_dir.rename(new_dir)

    replacements: list[tuple[str, str]] = []
    for old_pdf, new_pdf in mapping:
        replacements.extend(
            [
                (str(old_pdf), str(new_pdf)),
                (old_pdf.name, new_pdf.name),
                (old_pdf.stem, new_pdf.stem),
            ]
        )
    replacements.sort(key=lambda pair: len(pair[0]), reverse=True)

    for index_path in sorted(out_root.glob("*/index.json")):
        data = json.loads(index_path.read_text(encoding="utf-8"))
        updated = replace_strings(data, replacements)
        index_path.write_text(
            json.dumps(updated, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        try:
            write_markdown_index(updated, index_path.with_name("index.md"))
        except KeyError:
            # Early test indexes predate some summary fields used by the
            # current Markdown renderer. Their JSON still gets migrated.
            pass


def repair_processed_indexes(out_root: Path) -> None:
    """Normalize batch prefixes in indexes after an interrupted migration."""
    for index_path in sorted(out_root.glob("*/index.json")):
        data = json.loads(index_path.read_text(encoding="utf-8"))
        updated = replace_strings(data, [])
        index_path.write_text(
            json.dumps(updated, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        try:
            write_markdown_index(updated, index_path.with_name("index.md"))
        except KeyError:
            pass


def update_batch_map(
    map_path: Path,
    mapping: list[tuple[Path, Path]],
    labels: list[str],
) -> None:
    rows: list[dict[str, str]] = []
    if map_path.exists():
        with map_path.open(newline="", encoding="utf-8") as handle:
            rows.extend(csv.DictReader(handle))

    for label, (old_pdf, new_pdf) in zip(labels, mapping):
        rows.append(
            {
                "batch": label,
                "filename": new_pdf.name,
                "scanner_filename": old_pdf.name,
            }
        )

    with map_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["batch", "filename", "scanner_filename"],
        )
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scan_dir", type=Path)
    parser.add_argument("--first", help="First scanner PDF filename")
    parser.add_argument("--count", type=int)
    parser.add_argument("--start-number", type=int, default=1)
    parser.add_argument("--label", help="Custom label for one PDF, such as PreBatch")
    parser.add_argument("--repair-only", action="store_true", help=argparse.SUPPRESS)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    scan_dir = args.scan_dir.expanduser().resolve()
    out_root = scan_dir / ".processed"

    if args.repair_only:
        repair_processed_indexes(out_root)
        write_catalog(out_root)
        print(f"Processed indexes repaired: {out_root}")
        return 0

    if not args.first:
        raise ValueError("--first is required")
    if args.label:
        if args.count not in (None, 1):
            raise ValueError("--label can only be used with one PDF")
        count = 1
        labels = [args.label]
    else:
        if args.count is None:
            raise ValueError("--count is required unless --label is used")
        if args.count < 1 or args.start_number < 1:
            raise ValueError("--count and --start-number must be positive")
        count = args.count
        width = max(2, len(str(args.start_number + count - 1)))
        labels = [
            f"Batch {number:0{width}d}"
            for number in range(args.start_number, args.start_number + count)
        ]

    pdfs = selected_pdfs(scan_dir, args.first, count)
    mapping = build_mapping(pdfs, labels)
    migrate_processed_outputs(out_root, mapping)

    for old_pdf, new_pdf in mapping:
        old_pdf.rename(new_pdf)
        print(f"{old_pdf.name} -> {new_pdf.name}")

    update_batch_map(out_root / "batch-labels.csv", mapping, labels)
    write_catalog(out_root)
    print(f"Batch map updated: {out_root / 'batch-labels.csv'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
