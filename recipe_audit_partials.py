#!/usr/bin/env python3
"""Audit processed recipe scans for likely split or partial recipes.

This reads the `.processed/*/index.json` files produced by recipe_preprocess.py.
It does not OCR, render, modify, or delete scan PDFs.
"""

from __future__ import annotations

import argparse
import csv
import difflib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_PROCESSED_ROOT = Path("/media/nas/RecipeScans/.processed")

COMMON_WORDS = {
    "about",
    "after",
    "also",
    "and",
    "are",
    "bake",
    "baked",
    "baking",
    "boil",
    "bowl",
    "but",
    "can",
    "cook",
    "cooked",
    "cooking",
    "cup",
    "cups",
    "directions",
    "for",
    "from",
    "heat",
    "hot",
    "ingredients",
    "into",
    "large",
    "medium",
    "method",
    "minutes",
    "mix",
    "more",
    "oil",
    "one",
    "pan",
    "pepper",
    "preparation",
    "recipe",
    "salt",
    "sauce",
    "serve",
    "set",
    "small",
    "step",
    "stir",
    "tablespoon",
    "tablespoons",
    "taste",
    "teaspoon",
    "teaspoons",
    "the",
    "then",
    "this",
    "time",
    "to",
    "until",
    "water",
    "with",
}

GENERIC_TITLE_PATTERNS = [
    r"^by[:\s]",
    r"^by\s+",
    r"^chef\s+",
    r"^cooking$",
    r"^cook\b",
    r"^days better taste\.?$",
    r"^directions?$",
    r"^ingredients directions$",
    r"^ingredients?$",
    r"^method$",
    r"^my other recipes$",
    r"^prep ready in$",
    r"^rate recipe$",
    r"^ready in$",
    r"^salt[- ]*to taste",
    r"^the rest recipe is same\.?$",
    r"^water\. mix well",
]

CONTINUATION_START_PATTERNS = [
    r"^add\b",
    r"^bake\b",
    r"^cook\b",
    r"^cover\b",
    r"^directions?\b",
    r"^drain\b",
    r"^fold\b",
    r"^garnish\b",
    r"^heat\b",
    r"^meanwhile\b",
    r"^method\b",
    r"^mix\b",
    r"^pour\b",
    r"^preheat\b",
    r"^preparation\b",
    r"^remove\b",
    r"^season\b",
    r"^serve\b",
    r"^step\s+\d+",
    r"^stir\b",
    r"^the rest\b",
    r"^transfer\b",
    r"^whisk\b",
]


@dataclass
class GroupRecord:
    batch_index: int
    group_index: int
    source_pdf: str
    index_path: Path
    page_count: int
    first_nonblank_page: int | None
    last_nonblank_page: int | None
    group_id: str
    pages: list[int]
    title: str
    review_flags: list[str]
    signals: dict[str, Any]
    text: str
    word_count: int
    first_lines: list[str]
    last_lines: list[str]
    handwriting_pages: list[int]
    duplicate_count: int
    page_markers: list[dict[str, Any]]
    page_texts: dict[int, str]

    @property
    def ref(self) -> str:
        return f"{self.source_pdf} / {self.group_id}"

    @property
    def page_ref(self) -> str:
        return f"{self.source_pdf} pages {format_page_range(self.pages)}"

    @property
    def starts_batch(self) -> bool:
        return bool(self.pages and self.first_nonblank_page == self.pages[0])

    @property
    def ends_batch(self) -> bool:
        return bool(self.pages and self.last_nonblank_page == self.pages[-1])

    @property
    def normalized_title(self) -> str:
        return normalize(self.title)


def normalize(value: str) -> str:
    value = value.lower()
    value = value.replace("’", "'").replace("—", "-").replace("–", "-")
    value = re.sub(r"[^a-z0-9\s-]", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip(" -")


def title_tokens(title: str) -> set[str]:
    return {
        word
        for word in normalize(title).split()
        if len(word) > 2 and word not in COMMON_WORDS
    }


def content_tokens(text: str) -> set[str]:
    return {
        word
        for word in normalize(text).split()
        if len(word) > 3 and word not in COMMON_WORDS and not word.isdigit()
    }


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def group_text(index: dict[str, Any], group: dict[str, Any]) -> str:
    pages = {int(page["page_number"]): page for page in index.get("pages", [])}
    return "\n".join(
        pages[page_number].get("ocr_text", "")
        for page_number in group.get("pages", [])
        if page_number in pages
    ).strip()


def nonblank_bounds(index: dict[str, Any]) -> tuple[int | None, int | None]:
    nonblank = [
        int(page["page_number"])
        for page in index.get("pages", [])
        if not page.get("analysis", {}).get("is_blank")
    ]
    if not nonblank:
        return None, None
    return min(nonblank), max(nonblank)


def meaningful_lines(text: str) -> list[str]:
    lines = []
    for raw in text.splitlines():
        line = re.sub(r"\s+", " ", raw).strip()
        if len(line) >= 3:
            lines.append(line)
    return lines


def page_markers(lines: list[str], scan_page: int | None = None) -> list[dict[str, Any]]:
    markers: list[dict[str, Any]] = []
    page_word = re.compile(r"(?i)\bpage\s*(\d+)\s*(?:of\s*)?(\d+)\b")
    slash_marker = re.compile(r"^\s*([1-9])\s*/\s*([1-9])\s*$")
    for line in lines:
        clean = re.sub(r"\s+", " ", line).strip()
        lower = clean.lower()
        if re.search(r"\bcont\.?\b|\bcontinued\b", lower):
            markers.append({"kind": "continued", "scan_page": scan_page, "line": clean})
        for match in page_word.finditer(clean):
            current = int(match.group(1))
            total = int(match.group(2))
            if 1 <= current <= total <= 10:
                markers.append(
                    {
                        "kind": "page_marker",
                        "scan_page": scan_page,
                        "current": current,
                        "total": total,
                        "line": clean,
                    }
                )
        slash = slash_marker.match(clean)
        if slash:
            current = int(slash.group(1))
            total = int(slash.group(2))
            if 1 <= current <= total <= 10:
                markers.append(
                    {
                        "kind": "page_marker",
                        "scan_page": scan_page,
                        "current": current,
                        "total": total,
                        "line": clean,
                    }
                )
    return markers


def load_groups(processed_root: Path) -> list[GroupRecord]:
    groups: list[GroupRecord] = []
    for batch_index, index_path in enumerate(sorted(processed_root.glob("*/index.json")), 1):
        index = load_json(index_path)
        source_pdf = index.get("source", {}).get("filename", index_path.parent.name)
        page_count = int(index.get("summary", {}).get("page_count") or 0)
        first_nonblank, last_nonblank = nonblank_bounds(index)
        pages_by_number = {
            int(page["page_number"]): page for page in index.get("pages", [])
        }
        for group_index, group in enumerate(index.get("groups", []), 1):
            text = group_text(index, group)
            lines = meaningful_lines(text)
            markers: list[dict[str, Any]] = []
            page_texts: dict[int, str] = {}
            for page_number in group.get("pages", []):
                page = pages_by_number.get(int(page_number))
                if page:
                    page_texts[int(page_number)] = page.get("ocr_text", "")
                    markers.extend(
                        page_markers(
                            meaningful_lines(page.get("ocr_text", "")),
                            scan_page=int(page_number),
                        )
                    )
            groups.append(
                GroupRecord(
                    batch_index=batch_index,
                    group_index=group_index,
                    source_pdf=source_pdf,
                    index_path=index_path,
                    page_count=page_count,
                    first_nonblank_page=first_nonblank,
                    last_nonblank_page=last_nonblank,
                    group_id=str(group.get("group_id", f"group-{group_index:03d}")),
                    pages=[int(page) for page in group.get("pages", [])],
                    title=str(group.get("title", "")).strip(),
                    review_flags=list(group.get("review_flags", [])),
                    signals=dict(group.get("signals", {})),
                    text=text,
                    word_count=len(normalize(text).split()),
                    first_lines=lines[:4],
                    last_lines=lines[-4:],
                    handwriting_pages=list(group.get("handwriting_review_pages", [])),
                    duplicate_count=len(group.get("duplicate_candidates", [])),
                    page_markers=markers,
                    page_texts=page_texts,
                )
            )
    return groups


def generic_title(title: str) -> bool:
    normalized = normalize(title)
    if not normalized:
        return True
    meaningful = title_tokens(title)
    if not meaningful:
        return True
    return any(re.search(pattern, normalized) for pattern in GENERIC_TITLE_PATTERNS)


def first_line_looks_continuation(group: GroupRecord) -> bool:
    if not group.first_lines:
        return False
    first = normalize(group.first_lines[0])
    if not first:
        return False
    return any(re.search(pattern, first) for pattern in CONTINUATION_START_PATTERNS)


def last_line_looks_open(group: GroupRecord) -> bool:
    if not group.last_lines:
        return False
    last = group.last_lines[-1].strip()
    if re.search(r"\bcontinued\b|\bnext page\b", last, re.IGNORECASE):
        return True
    return bool(last.endswith((",", ";", ":", "and", "or")))


def partial_reasons(group: GroupRecord) -> list[str]:
    reasons: list[str] = []
    flags = set(group.review_flags)
    for flag in ("partial_or_title_review", "short_text_review", "instructions_review", "ingredients_review"):
        if flag in flags:
            reasons.append(flag)
    if generic_title(group.title):
        reasons.append("generic_or_bad_title")
    if first_line_looks_continuation(group):
        reasons.append("starts_like_continuation")
    if has_printed_continuation_marker(group):
        reasons.append("printed_page_continuation")
    if group.starts_batch and (
        generic_title(group.title)
        or "partial_or_title_review" in flags
        or first_line_looks_continuation(group)
    ):
        reasons.append("first_item_in_batch")
    if group.ends_batch and (
        "instructions_review" in flags
        or "ingredients_review" in flags
        or last_line_looks_open(group)
        or group.word_count < 120
    ):
        reasons.append("last_item_in_batch")
    if not group.signals.get("has_ingredients") and not group.signals.get("has_units"):
        reasons.append("weak_ingredients_signal")
    if not (
        group.signals.get("has_directions")
        or group.signals.get("has_times")
        or group.signals.get("has_temperature")
    ):
        reasons.append("weak_instructions_signal")
    return unique(reasons)


def has_printed_continuation_marker(group: GroupRecord) -> bool:
    if not group.pages:
        return False
    first_scan_page = group.pages[0]
    for marker in group.page_markers:
        if marker.get("scan_page") != first_scan_page:
            continue
        if marker.get("kind") == "continued":
            return True
        if marker.get("kind") == "page_marker" and int(marker.get("current") or 0) > 1:
            return True
    return False


def printed_page_numbers(group: GroupRecord) -> set[int]:
    return {
        int(marker["current"])
        for marker in group.page_markers
        if marker.get("kind") == "page_marker" and marker.get("current")
    }


def printed_page_total(group: GroupRecord) -> int | None:
    totals = [
        int(marker["total"])
        for marker in group.page_markers
        if marker.get("kind") == "page_marker" and marker.get("total")
    ]
    if not totals:
        return None
    return max(totals)


def marker_title_tokens(marker: dict[str, Any]) -> set[str]:
    line = marker.get("line", "")
    line = re.sub(r"(?i)\bpage\s*\d+\s*(?:of\s*)?\d+\b", " ", line)
    line = re.sub(r"(?i)\(?\bcont\.?\b\)?|\bcontinued\b", " ", line)
    return title_tokens(line)


def internal_continuation_matches(group: GroupRecord) -> list[dict[str, Any]]:
    if not group.pages:
        return []
    first_scan_page = group.pages[0]
    matches: list[dict[str, Any]] = []
    seen: set[tuple[int, int, str]] = set()
    for marker in group.page_markers:
        if marker.get("scan_page") != first_scan_page:
            continue
        is_continuation = marker.get("kind") == "continued" or int(marker.get("current") or 0) > 1
        if not is_continuation:
            continue
        marker_tokens = marker_title_tokens(marker)
        if not marker_tokens:
            continue
        for page_number in group.pages:
            if page_number <= first_scan_page:
                continue
            page_tokens = content_tokens(group.page_texts.get(page_number, ""))
            overlap = len(marker_tokens & page_tokens) / max(1, len(marker_tokens))
            if overlap >= 0.6:
                key = (first_scan_page, page_number, marker.get("line", ""))
                if key in seen:
                    continue
                seen.add(key)
                matches.append(
                    {
                        "continuation_page": first_scan_page,
                        "likely_first_page": page_number,
                        "score": round(overlap, 3),
                        "marker_line": marker.get("line", ""),
                    }
                )
    return matches


def unique(values: list[str]) -> list[str]:
    seen = set()
    result = []
    for value in values:
        if value not in seen:
            seen.add(value)
            result.append(value)
    return result


def complementary(a: GroupRecord, b: GroupRecord) -> bool:
    a_ing = bool(a.signals.get("has_ingredients") or a.signals.get("has_units"))
    b_ing = bool(b.signals.get("has_ingredients") or b.signals.get("has_units"))
    a_inst = bool(
        a.signals.get("has_directions")
        or a.signals.get("has_times")
        or a.signals.get("has_temperature")
    )
    b_inst = bool(
        b.signals.get("has_directions")
        or b.signals.get("has_times")
        or b.signals.get("has_temperature")
    )
    return (a_ing and not a_inst and b_inst) or (b_ing and not b_inst and a_inst)


def adjacency_bonus(a: GroupRecord, b: GroupRecord) -> tuple[float, str]:
    if a.batch_index == b.batch_index and abs(a.group_index - b.group_index) == 1:
        if has_printed_continuation_marker(a) or has_printed_continuation_marker(b):
            return 0.45, "printed_continuation_neighbor"
        if a.group_index < b.group_index and first_line_looks_continuation(b):
            return 0.35, "neighbor_continuation"
        if b.group_index < a.group_index and first_line_looks_continuation(a):
            return 0.35, "neighbor_continuation"
    if a.batch_index == b.batch_index:
        if abs(a.group_index - b.group_index) == 1:
            return 0.12, "neighbor_group"
        return 0.0, ""
    if abs(a.batch_index - b.batch_index) != 1:
        return 0.0, ""
    if a.batch_index < b.batch_index and a.ends_batch and b.starts_batch:
        return 0.35, "batch_boundary"
    if b.batch_index < a.batch_index and b.ends_batch and a.starts_batch:
        return 0.35, "batch_boundary"
    return 0.0, ""


def match_score(a: GroupRecord, b: GroupRecord) -> dict[str, Any]:
    title_ratio = difflib.SequenceMatcher(None, a.normalized_title, b.normalized_title).ratio()
    a_title_tokens = title_tokens(a.title)
    b_title_tokens = title_tokens(b.title)
    title_overlap = (
        len(a_title_tokens & b_title_tokens) / max(1, min(len(a_title_tokens), len(b_title_tokens)))
    )
    a_tokens = content_tokens(a.text)
    b_tokens = content_tokens(b.text)
    content_overlap = len(a_tokens & b_tokens) / max(1, min(len(a_tokens), len(b_tokens)))
    bonus, bonus_reason = adjacency_bonus(a, b)

    score = max(title_ratio * 0.55, title_overlap * 0.7, content_overlap * 0.55) + bonus
    reasons = []
    if title_ratio >= 0.82:
        reasons.append("similar_title")
    if title_overlap >= 0.55 and a_title_tokens and b_title_tokens:
        reasons.append("shared_title_words")
    if content_overlap >= 0.28:
        reasons.append("shared_content_words")
    if bonus_reason:
        reasons.append(bonus_reason)
    if complementary(a, b):
        score += 0.15
        reasons.append("complementary_signals")
    if a.duplicate_count or b.duplicate_count:
        reasons.append("duplicate_context")

    return {
        "score": round(min(score, 1.0), 3),
        "title_ratio": round(title_ratio, 3),
        "title_overlap": round(title_overlap, 3),
        "content_overlap": round(content_overlap, 3),
        "reasons": unique(reasons),
    }


def candidate_matches(group: GroupRecord, groups: list[GroupRecord]) -> list[dict[str, Any]]:
    matches = []
    for other in groups:
        if other.ref == group.ref:
            continue
        scored = match_score(group, other)
        strong_evidence = any(
            reason in scored["reasons"]
            for reason in (
                "printed_continuation_neighbor",
                "neighbor_continuation",
                "similar_title",
                "shared_content_words",
            )
        )
        if "batch_boundary" in scored["reasons"]:
            strong_evidence = strong_evidence or complementary(group, other)
        if not strong_evidence:
            continue
        if "batch_boundary" in scored["reasons"]:
            threshold = 0.48
        elif "neighbor_continuation" in scored["reasons"]:
            threshold = 0.50
        else:
            threshold = 0.55
        if scored["score"] >= threshold and scored["reasons"]:
            matches.append(
                {
                    **scored,
                    "source_pdf": other.source_pdf,
                    "group_id": other.group_id,
                    "title": other.title,
                    "pages": other.pages,
                    "page_ref": other.page_ref,
                }
            )
    matches.sort(key=lambda item: item["score"], reverse=True)
    return matches[:5]


def classify_confidence(score: float) -> str:
    if score >= 0.75:
        return "high"
    if score >= 0.55:
        return "medium"
    return "low"


def likely_match(item: dict[str, Any]) -> bool:
    if item.get("internal_matches"):
        return True
    if not item["matches"]:
        return False
    top = item["matches"][0]
    if top["score"] >= 0.55:
        return True
    return "neighbor_continuation" in top.get("reasons", []) and top["score"] >= 0.50


def build_audit(groups: list[GroupRecord]) -> dict[str, Any]:
    partials = []
    for group in groups:
        reasons = partial_reasons(group)
        if not reasons:
            continue
        internal_matches = internal_continuation_matches(group)
        if internal_matches and "internal_page_order_review" not in reasons:
            reasons.append("internal_page_order_review")
        matches = [] if internal_matches else candidate_matches(group, groups)
        partials.append(
            {
                "source_pdf": group.source_pdf,
                "group_id": group.group_id,
                "title": group.title,
                "pages": group.pages,
                "page_ref": group.page_ref,
                "batch_index": group.batch_index,
                "group_index": group.group_index,
                "reasons": reasons,
                "review_flags": group.review_flags,
                "page_markers": group.page_markers,
                "internal_matches": internal_matches,
                "word_count": group.word_count,
                "first_lines": group.first_lines,
                "last_lines": group.last_lines,
                "matches": [
                    {**match, "confidence": classify_confidence(match["score"])}
                    for match in matches
                ],
            }
        )

    partials.sort(
        key=lambda item: (
            0 if item["matches"] and item["matches"][0]["score"] >= 0.75 else 1,
            0 if item["matches"] else 1,
            item["batch_index"],
            item["group_index"],
        )
    )

    return {
        "summary": {
            "batches": len({group.source_pdf for group in groups}),
            "groups": len(groups),
            "partial_candidates": len(partials),
            "with_possible_matches": sum(1 for item in partials if item["matches"]),
            "with_internal_page_order_matches": sum(
                1 for item in partials if item.get("internal_matches")
            ),
            "high_confidence_matches": sum(
                1
                for item in partials
                if item["matches"] and item["matches"][0]["score"] >= 0.75
            ),
            "medium_confidence_matches": sum(
                1
                for item in partials
                if item["matches"] and 0.55 <= item["matches"][0]["score"] < 0.75
            ),
        },
        "partials": partials,
    }


def write_markdown(audit: dict[str, Any], output_path: Path) -> None:
    summary = audit["summary"]
    lines = [
        "# Partial Recipe Audit",
        "",
        f"- Batches: {summary['batches']}",
        f"- Recipe groups: {summary['groups']}",
        f"- Partial candidates: {summary['partial_candidates']}",
        f"- Candidates with possible matches: {summary['with_possible_matches']}",
        f"- Internal page-order matches: {summary['with_internal_page_order_matches']}",
        f"- High-confidence matches: {summary['high_confidence_matches']}",
        f"- Medium-confidence matches: {summary['medium_confidence_matches']}",
        "",
        "## Likely Matches",
        "",
    ]

    likely = [
        item
        for item in audit["partials"]
        if likely_match(item)
    ]
    if not likely:
        lines.append("No medium/high-confidence partial matches found.")
        lines.append("")
    for item in likely:
        lines.extend(format_partial_item(item, include_lines=False))
        if item.get("internal_matches"):
            for internal in item["internal_matches"]:
                lines.append(
                    "- Internal page-order clue: "
                    f"scan page {internal['continuation_page']} appears to be a continuation; "
                    f"scan page {internal['likely_first_page']} looks like its first page "
                    f"(score {internal['score']})."
                )
        if item["matches"]:
            top = item["matches"][0]
            lines.append(
                f"- Best match: {top['confidence']} {top['score']} - "
                f"{top['title']} ({top['page_ref']})"
            )
            lines.append(f"- Match reasons: {', '.join(top['reasons'])}")
        if len(item["matches"]) > 1:
            lines.append("- Other possible matches:")
            for match in item["matches"][1:4]:
                lines.append(
                    f"  - {match['confidence']} {match['score']} - "
                    f"{match['title']} ({match['page_ref']})"
                )
        lines.append("")

    lines.extend(["## Needs Manual Review", ""])
    manual = [
        item
        for item in audit["partials"]
        if not likely_match(item)
    ]
    if not manual:
        lines.append("No unmatched partial candidates.")
        lines.append("")
    for item in manual:
        lines.extend(format_partial_item(item, include_lines=True))
        if item["matches"]:
            top = item["matches"][0]
            lines.append(
                f"- Weak possible match: {top['score']} - {top['title']} ({top['page_ref']})"
            )
        else:
            lines.append("- No corresponding page found by deterministic matching.")
        lines.append("")

    output_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def format_partial_item(item: dict[str, Any], *, include_lines: bool) -> list[str]:
    lines = [
        f"### {item['title']}",
        f"- Current: {item['page_ref']}",
        f"- Reasons: {', '.join(item['reasons'])}",
    ]
    if item.get("page_markers"):
        marker_text = [
            f"scan p{marker.get('scan_page')}: {marker.get('line', '')}"
            for marker in item["page_markers"]
            if marker.get("line")
        ]
        lines.append(f"- Printed page clues: {snippet(marker_text)}")
    if include_lines:
        if item["first_lines"]:
            lines.append(f"- First OCR lines: {snippet(item['first_lines'])}")
        if item["last_lines"]:
            lines.append(f"- Last OCR lines: {snippet(item['last_lines'])}")
    return lines


def snippet(lines: list[str], limit: int = 180) -> str:
    text = " / ".join(lines)
    if len(text) <= limit:
        return text
    return text[: limit - 3].rstrip() + "..."


def format_page_range(pages: list[int]) -> str:
    if not pages:
        return "none"
    if len(pages) == 1:
        return str(pages[0])
    return f"{pages[0]}-{pages[-1]}"


def write_csv(audit: dict[str, Any], output_path: Path) -> None:
    with output_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(
            fh,
            fieldnames=[
                "source_pdf",
                "group_id",
                "title",
                "pages",
                "reasons",
                "best_match_confidence",
                "best_match_score",
                "best_match_title",
                "best_match_pages",
                "best_match_reasons",
            ],
        )
        writer.writeheader()
        for item in audit["partials"]:
            best = item["matches"][0] if item["matches"] else {}
            writer.writerow(
                {
                    "source_pdf": item["source_pdf"],
                    "group_id": item["group_id"],
                    "title": item["title"],
                    "pages": format_page_range(item["pages"]),
                    "reasons": ";".join(item["reasons"]),
                    "best_match_confidence": best.get("confidence", ""),
                    "best_match_score": best.get("score", ""),
                    "best_match_title": best.get("title", ""),
                    "best_match_pages": best.get("page_ref", ""),
                    "best_match_reasons": ";".join(best.get("reasons", [])),
                }
            )


def run_audit(args: argparse.Namespace) -> int:
    processed_root = Path(args.processed_root).expanduser()
    if not processed_root.exists():
        print(f"Processed root does not exist: {processed_root}")
        return 2
    groups = load_groups(processed_root)
    audit = build_audit(groups)
    json_path = processed_root / "partial-audit.json"
    md_path = processed_root / "partial-audit.md"
    csv_path = processed_root / "partial-audit.csv"
    json_path.write_text(json.dumps(audit, indent=2, ensure_ascii=False), encoding="utf-8")
    write_markdown(audit, md_path)
    write_csv(audit, csv_path)

    summary = audit["summary"]
    print(f"Wrote {md_path}")
    print(f"Wrote {csv_path}")
    print(f"Partial candidates: {summary['partial_candidates']}")
    print(f"With possible matches: {summary['with_possible_matches']}")
    print(f"Internal page-order matches: {summary['with_internal_page_order_matches']}")
    print(f"High confidence: {summary['high_confidence_matches']}")
    print(f"Medium confidence: {summary['medium_confidence_matches']}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Audit processed recipe scan indexes for split/partial recipes.",
    )
    parser.add_argument(
        "processed_root",
        nargs="?",
        default=str(DEFAULT_PROCESSED_ROOT),
        help="Folder containing .processed scan outputs.",
    )
    parser.set_defaults(func=run_audit)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
