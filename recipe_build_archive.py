#!/usr/bin/env python3
"""Build a canonical review archive from processed recipe scans."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sqlite3
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image, ImageOps
from scipy.fft import dctn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neighbors import NearestNeighbors

from recipe_preprocess import normalized_text


PUBLISHERS = {
    "NYT Cooking": [
        r"\bnyt cooking\b",
        r"\bthe new york times\b",
        r"\bt cooking\b",
        r"\bprivate notes\b",
        r"\bleave a private note on this recipe\b",
    ],
    "Allrecipes": [r"\ballrecipes\b"],
    "BBC Good Food": [r"\bbbc good food\b"],
    "Bon Appetit": [r"\bbon app[eé]tit\b"],
    "Epicurious": [r"\bepicurious\b"],
    "Food Network": [r"\bfood network\b"],
    "RecipeTin Eats": [r"\brecipetin eats\b", r"\brecipetineats\b"],
    "Serious Eats": [r"\bserious eats\b"],
    "The Mediterranean Dish": [r"\bmediterranean dish\b"],
    "Tasty": [r"\btasty\.co\b", r"\btasty recipe\b"],
}

DOMAIN_RE = re.compile(
    r"\b(?:https?://)?(?:www\.)?([a-z0-9][a-z0-9.-]+\.(?:com|org|net|co|io|tv|in|uk))\b",
    re.IGNORECASE,
)

URL_RE = re.compile(
    r"h(?:tt|ti|it)p[s]?\s*:\s*/\s*/\s*[^\s<>\])}]+",
    re.IGNORECASE,
)

UTILITY_DOMAINS = {
    "facebook.com", "google.com", "instagram.com", "pinterest.com",
    "t.co", "twitter.com", "x.com", "youtube.com",
}


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def batch_sort_key(filename: str) -> tuple[int, int, str]:
    match = re.match(r"Batch_(\d+)__", filename)
    if match:
        return (1, int(match.group(1)), filename)
    if filename.startswith("PreBatch__"):
        return (0, 0, filename)
    return (-1, 0, filename)


def batch_label(filename: str) -> str:
    match = re.match(r"Batch_(\d+)__", filename)
    if match:
        return f"Batch {int(match.group(1)):02d}"
    if filename.startswith("PreBatch__"):
        return "PreBatch"
    return "Setup-era"


def write_csv(path: Path, rows: list[dict[str, Any]], fields: list[str]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def image_phash(path: str) -> np.ndarray | None:
    image_path = Path(path)
    if not image_path.exists():
        return None
    with Image.open(image_path) as source:
        image = source.convert("L")
        pixels = np.asarray(image)
        ink = pixels < 245
        ys, xs = np.where(ink)
        if len(xs):
            pad = 20
            image = image.crop(
                (
                    max(0, int(xs.min()) - pad),
                    max(0, int(ys.min()) - pad),
                    min(pixels.shape[1], int(xs.max()) + pad + 1),
                    min(pixels.shape[0], int(ys.max()) + pad + 1),
                )
            )
        image = ImageOps.autocontrast(image).resize(
            (32, 32), Image.Resampling.LANCZOS
        )
        values = np.asarray(image, dtype=float)
    coefficients = dctn(values, norm="ortho")[:8, :8]
    return (coefficients > np.median(coefficients.flat[1:])).flatten()


def hash_similarity(left: np.ndarray | None, right: np.ndarray | None) -> float:
    if left is None or right is None:
        return 0.0
    return 1.0 - float(np.count_nonzero(left != right)) / float(len(left))


def nearest_text_pairs(
    records: list[dict[str, Any]],
    *,
    text_field: str,
    minimum_words: int,
    threshold: float,
    neighbors: int = 8,
) -> list[tuple[int, int, float]]:
    eligible = [
        index
        for index, record in enumerate(records)
        if len(normalized_text(str(record.get(text_field, ""))).split()) >= minimum_words
    ]
    if len(eligible) < 2:
        return []
    texts = [str(records[index].get(text_field, "")) for index in eligible]
    vectors = TfidfVectorizer(
        lowercase=True,
        analyzer="char_wb",
        ngram_range=(3, 5),
        min_df=2,
        max_features=180000,
    ).fit_transform(texts)
    model = NearestNeighbors(
        metric="cosine", n_neighbors=min(neighbors, len(eligible))
    ).fit(vectors)
    distances, indices = model.kneighbors(vectors)
    pairs: dict[tuple[int, int], float] = {}
    for row, candidates in enumerate(indices):
        for offset, candidate in enumerate(candidates):
            if candidate == row:
                continue
            similarity = 1.0 - float(distances[row, offset])
            if similarity < threshold:
                continue
            left, right = sorted((eligible[row], eligible[int(candidate)]))
            pairs[(left, right)] = max(similarity, pairs.get((left, right), 0.0))
    return [(left, right, score) for (left, right), score in pairs.items()]


def extract_url(text: str) -> str:
    candidates: list[str] = []
    for line in text.splitlines():
        match = URL_RE.search(line)
        if not match:
            continue
        candidate = re.sub(r"\s+", "", match.group(0))
        candidate = re.sub(r"^h(?:ti|it)p", "http", candidate, flags=re.IGNORECASE)
        candidates.append(candidate.rstrip(".,;:'\""))
    if not candidates:
        return ""

    def url_quality(candidate: str) -> tuple[int, int, int]:
        domain_match = DOMAIN_RE.search(candidate)
        domain = domain_match.group(1).lower() if domain_match else ""
        utility = any(domain == item or domain.endswith(f".{item}") for item in UTILITY_DOMAINS)
        media = bool(re.search(r"\.(?:jpe?g|png|gif|webp)(?:\?|$)", candidate, re.I))
        recipe_path = bool(re.search(r"recipe|cook|food|kitchen", candidate, re.I))
        return (0 if utility or media else 1, 1 if recipe_path else 0, -len(candidate))

    return max(candidates, key=url_quality)


def source_evidence(text: str) -> tuple[str, str, str, str]:
    lower = text.lower()
    publishers = [
        name
        for name, patterns in PUBLISHERS.items()
        if any(re.search(pattern, lower, re.IGNORECASE) for pattern in patterns)
    ]
    domains = list(dict.fromkeys(match.group(1).lower() for match in DOMAIN_RE.finditer(text)))
    domain_positions = {domain: index for index, domain in enumerate(domains)}
    domains.sort(
        key=lambda domain: (
            any(domain == item or domain.endswith(f".{item}") for item in UTILITY_DOMAINS),
            domain_positions[domain],
        )
    )
    publisher = publishers[0] if publishers else ""
    domain = domains[0] if domains else ""
    url = extract_url(text)
    evidence = url or publisher or domain
    return publisher, domain, url, evidence


def build_archive(scan_dir: Path, override_path: Path) -> dict[str, Any]:
    processed_root = scan_dir / ".processed"
    archive_dir = processed_root / "archive"
    archive_dir.mkdir(parents=True, exist_ok=True)
    overrides = load_json(override_path)
    scan_overrides = overrides.get("scan_overrides", {})
    prior_source_confirmations: dict[str, dict[str, str]] = {}
    prior_source_csv = archive_dir / "source-review.csv"
    if prior_source_csv.exists():
        try:
            with prior_source_csv.open(newline="", encoding="utf-8") as handle:
                for row in csv.DictReader(handle):
                    if row.get("status") == "confirmed" and row.get("group_key"):
                        prior_source_confirmations[row["group_key"]] = row
        except (OSError, csv.Error):
            prior_source_confirmations = {}

    indexes: dict[str, dict[str, Any]] = {}
    for index_path in processed_root.glob("*/index.json"):
        data = load_json(index_path)
        filename = str(data.get("source", {}).get("filename", ""))
        if filename:
            indexes[filename] = data

    scan_rows: list[dict[str, Any]] = []
    for pdf in sorted(scan_dir.glob("*.pdf"), key=lambda path: path.stat().st_mtime_ns):
        index = indexes.get(pdf.name)
        override = scan_overrides.get(pdf.name, {})
        scan_rows.append(
            {
                "filename": pdf.name,
                "label": batch_label(pdf.name),
                "status": override.get("status", "retained"),
                "canonical_scan": override.get("canonical_scan", ""),
                "reason": override.get("reason", ""),
                "page_count": (index or {}).get("summary", {}).get("page_count", ""),
                "processed": bool(index),
                "processed_at": (index or {}).get("processed_at", ""),
                "size_bytes": pdf.stat().st_size,
            }
        )

    page_rows: list[dict[str, Any]] = []
    group_rows: list[dict[str, Any]] = []
    for filename, index in sorted(indexes.items(), key=lambda item: batch_sort_key(item[0])):
        status = scan_overrides.get(filename, {}).get("status", "retained")
        pages_by_number = {int(page["page_number"]): page for page in index.get("pages", [])}
        for page in index.get("pages", []):
            number = int(page["page_number"])
            analysis = page.get("analysis", {})
            page_rows.append(
                {
                    "page_id": f"{filename}#p{number:03d}",
                    "scan": filename,
                    "label": batch_label(filename),
                    "scan_status": status,
                    "page": number,
                    "is_blank": bool(analysis.get("is_blank")),
                    "word_count": int(page.get("ocr_word_count") or 0),
                    "color_notes": bool(analysis.get("color_notes_possible")),
                    "handwriting_review": bool(
                        analysis.get("handwriting_review_possible")
                    ),
                    "ink_ratio": float(analysis.get("ink_ratio") or 0.0),
                    "ocr_text": str(page.get("ocr_text", "")),
                    "image_path": str(page.get("paths", {}).get("rendered", "")),
                }
            )
        for group in index.get("groups", []):
            page_numbers = [int(value) for value in group.get("pages", [])]
            text = "\n".join(
                str(pages_by_number[number].get("ocr_text", ""))
                for number in page_numbers
                if number in pages_by_number
            )
            publisher, domain, source_url, evidence = source_evidence(text)
            group_rows.append(
                {
                    "group_key": f"{filename}::{group.get('group_id', '')}",
                    "scan": filename,
                    "label": batch_label(filename),
                    "scan_status": status,
                    "group_id": group.get("group_id", ""),
                    "title": group.get("title", ""),
                    "pages": ",".join(str(value) for value in page_numbers),
                    "review_flags": ";".join(group.get("review_flags", [])),
                    "handwriting_pages": ",".join(
                        str(value) for value in group.get("handwriting_review_pages", [])
                    ),
                    "text": text,
                    "word_count": len(normalized_text(text).split()),
                    "publisher": publisher,
                    "domain": domain,
                    "source_url": source_url,
                    "source_evidence": evidence,
                    "web_search_query": group.get("web_search_query", ""),
                }
            )

    hash_cache: dict[str, np.ndarray | None] = {}
    page_duplicate_rows: list[dict[str, Any]] = []
    for left_index, right_index, text_score in nearest_text_pairs(
        page_rows, text_field="ocr_text", minimum_words=20, threshold=0.88
    ):
        left, right = page_rows[left_index], page_rows[right_index]
        if left["scan"] == right["scan"] and left["page"] == right["page"]:
            continue
        for record in (left, right):
            path = str(record["image_path"])
            if path not in hash_cache:
                hash_cache[path] = image_phash(path)
        image_score = hash_similarity(
            hash_cache[str(left["image_path"])], hash_cache[str(right["image_path"])]
        )
        known_rescan = (
            left["scan_status"] == "superseded"
            or right["scan_status"] == "superseded"
        )
        if known_rescan and text_score >= 0.90:
            classification = "scan_duplicate"
        elif text_score >= 0.965 and image_score >= 0.90:
            classification = "exact_page_duplicate_review"
        elif text_score >= 0.90:
            classification = "duplicate_printout_page_review"
        else:
            continue
        page_duplicate_rows.append(
            {
                "classification": classification,
                "page_a": left["page_id"],
                "page_b": right["page_id"],
                "text_similarity": round(text_score, 4),
                "image_similarity": round(image_score, 4),
            }
        )

    retained_groups = [row for row in group_rows if row["scan_status"] == "retained"]
    recipe_duplicate_rows: list[dict[str, Any]] = []
    for left_index, right_index, text_score in nearest_text_pairs(
        retained_groups, text_field="text", minimum_words=35, threshold=0.62
    ):
        left, right = retained_groups[left_index], retained_groups[right_index]
        title_score = SequenceMatcher(
            None, normalized_text(left["title"]), normalized_text(right["title"])
        ).ratio()
        if text_score >= 0.90:
            confidence = "high"
        elif text_score >= 0.78 and title_score >= 0.72:
            confidence = "medium"
        elif title_score >= 0.90 and text_score >= 0.62:
            confidence = "medium"
        else:
            continue
        canonical = max(
            (left, right),
            key=lambda row: (
                row["word_count"],
                -len(str(row["review_flags"]).split(";")),
                row["group_key"],
            ),
        )
        duplicate = right if canonical is left else left
        recipe_duplicate_rows.append(
            {
                "confidence": confidence,
                "canonical_group": canonical["group_key"],
                "duplicate_group": duplicate["group_key"],
                "title_a": left["title"],
                "title_b": right["title"],
                "text_similarity": round(text_score, 4),
                "title_similarity": round(title_score, 4),
                "handwriting_a": left["handwriting_pages"],
                "handwriting_b": right["handwriting_pages"],
            }
        )

    handwriting_rows = []
    handwriting_statuses = overrides.get("handwriting_reviews", {})
    handwriting_transcriptions = overrides.get("handwriting_transcriptions", {})
    for page in page_rows:
        if page["scan_status"] != "retained" or page["is_blank"]:
            continue
        reasons = []
        if page["page_id"] in handwriting_transcriptions:
            reasons.append("manual_transcription")
        if page["page_id"] in handwriting_statuses:
            reasons.append("manual_review")
        if page["color_notes"]:
            reasons.append("colored_ink")
        if page["handwriting_review"]:
            reasons.append("handwriting_detector")
        if int(page["page"]) % 2 == 0 and int(page["word_count"]) <= 120:
            reasons.append("possible_note_back")
        if reasons:
            handwriting_rows.append(
                {
                    "page_id": page["page_id"],
                    "label": page["label"],
                    "scan": page["scan"],
                    "page": page["page"],
                "reasons": ";".join(reasons),
                "word_count": page["word_count"],
                "image_path": page["image_path"],
                "ocr_draft": page["ocr_text"],
                "transcription": handwriting_transcriptions.get(page["page_id"], ""),
                "status": "transcribed"
                if page["page_id"] in handwriting_transcriptions
                else handwriting_statuses.get(page["page_id"], "needs_review"),
            }
        )

    source_rows = []
    for group in retained_groups:
        group_override = overrides.get("group_overrides", {}).get(group["group_key"], {})
        confirmation = overrides.get("source_confirmations", {}).get(
            group["group_key"], {}
        )
        if not confirmation:
            prior = prior_source_confirmations.get(group["group_key"])
            if prior:
                confirmation = {
                    "source": prior.get("confirmed_source")
                    or prior.get("publisher_candidate")
                    or prior.get("domain_candidate")
                    or "Confirmed by user",
                    "url": prior.get("confirmed_url", ""),
                }
        # NYT Cooking pages have a distinctive, consistently detected layout;
        # treat a local NYT publisher match as a confirmed source unless a
        # stronger explicit confirmation already exists.
        if not confirmation and group.get("publisher") == "NYT Cooking":
            confirmation = {"source": "NYT Cooking"}
        page_numbers = [int(value) for value in str(group.get("pages", "")).split(",") if str(value).strip()]
        source_rows.append(
            {
                "group_key": group["group_key"],
                "title": group_override.get("title") or group["title"],
                "page_number": page_numbers[0] if page_numbers else "",
                "publisher_candidate": group["publisher"],
                "domain_candidate": group["domain"],
                "url_candidate": group["source_url"],
                "evidence": group["source_evidence"],
                "search_query": group["web_search_query"],
                "confirmed_source": confirmation.get("source", ""),
                "confirmed_url": confirmation.get("url", ""),
                "status": "confirmed" if confirmation else "needs_lookup",
            }
        )

    write_csv(
        archive_dir / "scan-manifest.csv",
        scan_rows,
        [
            "filename", "label", "status", "canonical_scan", "reason",
            "page_count", "processed", "processed_at", "size_bytes",
        ],
    )
    write_csv(
        archive_dir / "page-duplicate-candidates.csv",
        page_duplicate_rows,
        ["classification", "page_a", "page_b", "text_similarity", "image_similarity"],
    )
    write_csv(
        archive_dir / "recipe-duplicate-candidates.csv",
        recipe_duplicate_rows,
        [
            "confidence", "canonical_group", "duplicate_group", "title_a", "title_b",
            "text_similarity", "title_similarity", "handwriting_a", "handwriting_b",
        ],
    )
    write_csv(
        archive_dir / "handwriting-review.csv",
        handwriting_rows,
        [
            "page_id", "label", "scan", "page", "reasons", "word_count",
            "image_path", "ocr_draft", "transcription", "status",
        ],
    )
    write_csv(
        archive_dir / "source-review.csv",
        source_rows,
        [
            "group_key", "title", "page_number", "publisher_candidate", "domain_candidate", "url_candidate", "evidence",
            "search_query", "confirmed_source", "confirmed_url", "status",
        ],
    )

    database_path = archive_dir / "recipe-archive.sqlite"
    with sqlite3.connect(database_path) as database:
        database.executescript(
            """
            DROP TABLE IF EXISTS scans;
            DROP TABLE IF EXISTS pages;
            DROP TABLE IF EXISTS recipe_groups;
            DROP TABLE IF EXISTS page_duplicate_candidates;
            DROP TABLE IF EXISTS recipe_duplicate_candidates;
            DROP TABLE IF EXISTS handwriting_review;
            DROP TABLE IF EXISTS source_review;
            CREATE TABLE scans (filename TEXT PRIMARY KEY, label TEXT, status TEXT,
                canonical_scan TEXT, reason TEXT, page_count INTEGER, processed INTEGER,
                processed_at TEXT, size_bytes INTEGER);
            CREATE TABLE pages (page_id TEXT PRIMARY KEY, scan TEXT, label TEXT,
                scan_status TEXT, page INTEGER, is_blank INTEGER, word_count INTEGER,
                color_notes INTEGER, handwriting_review INTEGER, ink_ratio REAL,
                ocr_text TEXT, image_path TEXT);
            CREATE TABLE recipe_groups (group_key TEXT PRIMARY KEY, scan TEXT, label TEXT,
                scan_status TEXT, group_id TEXT, title TEXT, pages TEXT, review_flags TEXT,
                handwriting_pages TEXT, text TEXT, word_count INTEGER, publisher TEXT,
                domain TEXT, source_url TEXT, source_evidence TEXT, web_search_query TEXT);
            CREATE TABLE page_duplicate_candidates (classification TEXT, page_a TEXT,
                page_b TEXT, text_similarity REAL, image_similarity REAL);
            CREATE TABLE recipe_duplicate_candidates (confidence TEXT,
                canonical_group TEXT, duplicate_group TEXT, title_a TEXT, title_b TEXT,
                text_similarity REAL, title_similarity REAL, handwriting_a TEXT,
                handwriting_b TEXT);
            CREATE TABLE handwriting_review (page_id TEXT PRIMARY KEY, label TEXT,
                scan TEXT, page INTEGER, reasons TEXT, word_count INTEGER,
                image_path TEXT, ocr_draft TEXT, transcription TEXT, status TEXT);
            CREATE TABLE source_review (group_key TEXT PRIMARY KEY, title TEXT, page_number INTEGER,
                publisher_candidate TEXT, domain_candidate TEXT, evidence TEXT,
                url_candidate TEXT, search_query TEXT, confirmed_source TEXT,
                confirmed_url TEXT, status TEXT);
            """
        )
        tables = [
            ("scans", scan_rows),
            ("pages", page_rows),
            ("recipe_groups", group_rows),
            ("page_duplicate_candidates", page_duplicate_rows),
            ("recipe_duplicate_candidates", recipe_duplicate_rows),
            ("handwriting_review", handwriting_rows),
            ("source_review", source_rows),
        ]
        for table, rows in tables:
            if not rows:
                continue
            columns = list(rows[0])
            placeholders = ",".join("?" for _ in columns)
            database.executemany(
                f"INSERT INTO {table} ({','.join(columns)}) VALUES ({placeholders})",
                [[row.get(column) for column in columns] for row in rows],
            )
        database.commit()

    summary = {
        "pdfs": len(scan_rows),
        "retained_scans": sum(row["status"] == "retained" for row in scan_rows),
        "superseded_scans": sum(row["status"] == "superseded" for row in scan_rows),
        "indexed_pages": len(page_rows),
        "retained_recipe_groups": len(retained_groups),
        "page_duplicate_candidates": len(page_duplicate_rows),
        "recipe_duplicate_candidates": len(recipe_duplicate_rows),
        "handwriting_review_pages": len(handwriting_rows),
        "sources_with_local_evidence": sum(bool(row["evidence"]) for row in source_rows),
        "source_review_groups": len(source_rows),
    }
    (archive_dir / "summary.json").write_text(
        json.dumps(summary, indent=2) + "\n", encoding="utf-8"
    )
    lines = [
        "# Recipe Archive Reconciliation",
        "",
        f"- PDFs inventoried: {summary['pdfs']}",
        f"- Retained scans: {summary['retained_scans']}",
        f"- Superseded scans: {summary['superseded_scans']}",
        f"- Indexed PDF pages: {summary['indexed_pages']}",
        f"- Preliminary retained recipe groups: {summary['retained_recipe_groups']}",
        f"- Page duplicate candidates: {summary['page_duplicate_candidates']}",
        f"- Recipe duplicate candidates: {summary['recipe_duplicate_candidates']}",
        f"- Handwriting review pages: {summary['handwriting_review_pages']}",
        f"- Groups with local source evidence: {summary['sources_with_local_evidence']}",
        "",
        "Original PDFs are preserved. Superseded scans are excluded only in the archive database.",
    ]
    (archive_dir / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
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
    summary = build_archive(args.scan_dir.expanduser().resolve(), args.overrides.resolve())
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
