#!/usr/bin/env python3
"""Local preprocessing for scanned recipe PDFs.

This script keeps the scanner's original PDF files untouched. Generated OCR,
page previews, contact sheets, and catalog indexes are written into a
`.processed` folder next to the scan files by default.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import datetime as dt
import difflib
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    from PIL import Image, ImageOps
except ImportError:  # pragma: no cover - handled by dependency check
    Image = None  # type: ignore[assignment]
    ImageOps = None  # type: ignore[assignment]


SCRIPT_VERSION = "0.2.0"
DEFAULT_RENDER_DPI = 300
DEFAULT_PDF_READY_TIMEOUT = 180
PDF_READY_POLL_SECONDS = 5
DEFAULT_WORKERS = 4
BLANK_DARK_RATIO = 0.0015
BLANK_INK_RATIO = 0.006
COLOR_NOTE_RATIO = 0.0005
MIN_OSD_CONFIDENCE = 1.0

UNIT_WORDS = {
    "cup",
    "cups",
    "tablespoon",
    "tablespoons",
    "tbsp",
    "teaspoon",
    "teaspoons",
    "tsp",
    "ounce",
    "ounces",
    "oz",
    "pound",
    "pounds",
    "lb",
    "lbs",
    "gram",
    "grams",
    "g",
    "kg",
    "ml",
    "liter",
    "liters",
    "quart",
    "quarts",
    "qt",
    "pint",
    "pints",
    "pinch",
    "clove",
    "cloves",
}

BOILERPLATE_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"^ingredients?$",
        r"^directions?$",
        r"^instructions?$",
        r"^method$",
        r"^preparation$",
        r"^notes?$",
        r"^private notes?$",
        r"^by\s+",
        r"^print$",
        r"^save$",
        r"^share$",
        r"^advertisement$",
        r"^https?://",
        r"www\.",
        r"copyright",
        r"all rights reserved",
    )
]


@dataclass
class CommandResult:
    stdout: str
    stderr: str
    returncode: int


def run_command(
    args: list[str],
    *,
    timeout: int | None = None,
    check: bool = True,
) -> CommandResult:
    proc = subprocess.run(
        args,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if check and proc.returncode != 0:
        rendered = " ".join(args)
        raise RuntimeError(
            f"Command failed ({proc.returncode}): {rendered}\n{proc.stderr.strip()}"
        )
    return CommandResult(proc.stdout, proc.stderr, proc.returncode)


def require_tools() -> list[str]:
    missing: list[str] = []
    for tool in ("pdfinfo", "pdftoppm", "tesseract"):
        if not shutil.which(tool):
            missing.append(tool)
    if Image is None:
        missing.append("python3-pil/Pillow")
    return missing


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_fingerprint(path: Path) -> dict[str, Any]:
    stat = path.stat()
    return {
        "size_bytes": stat.st_size,
        "mtime_ns": stat.st_mtime_ns,
        "sha256": sha256_file(path),
    }


def parse_pdfinfo(path: Path, *, ready_timeout: int = DEFAULT_PDF_READY_TIMEOUT) -> dict[str, Any]:
    deadline = time.monotonic() + max(0, ready_timeout)
    last_error = ""
    result: CommandResult | None = None

    while True:
        result = run_command(["pdfinfo", str(path)], timeout=30, check=False)
        if result.returncode == 0:
            break
        last_error = result.stderr.strip() or result.stdout.strip()
        if time.monotonic() >= deadline:
            raise RuntimeError(
                "PDF is not readable yet or is corrupt after waiting "
                f"{ready_timeout} seconds: {path}\n{last_error}"
            )
        print(
            "  waiting for PDF to finish writing "
            f"({PDF_READY_POLL_SECONDS}s): {path.name}",
            flush=True,
        )
        time.sleep(PDF_READY_POLL_SECONDS)

    assert result is not None
    info: dict[str, Any] = {}
    for line in result.stdout.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip().lower().replace(" ", "_")
        value = value.strip()
        if key == "pages":
            try:
                info[key] = int(value)
            except ValueError:
                info[key] = value
        else:
            info[key] = value
    return info


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def is_current_index(
    index_path: Path,
    source: dict[str, Any],
    render_dpi: int,
) -> bool:
    existing = load_json(index_path)
    if not existing:
        return False
    options = existing.get("processing_options", {})
    return (
        existing.get("script_version") == SCRIPT_VERSION
        and existing.get("source", {}).get("fingerprint") == source["fingerprint"]
        and options.get("render_dpi") == render_dpi
    )


def normalize_rendered_name(path: Path, page_number: int) -> Path:
    desired = path.parent / f"page-{page_number:03d}.png"
    if path == desired:
        return desired
    if desired.exists():
        desired.unlink()
    path.rename(desired)
    return desired


def render_page(pdf_path: Path, pages_dir: Path, page_number: int, dpi: int) -> Path:
    prefix = pages_dir / f"page-{page_number:03d}"
    output_path = prefix.with_suffix(".png")
    if output_path.exists():
        output_path.unlink()
    run_command(
        [
            "pdftoppm",
            "-r",
            str(dpi),
            "-f",
            str(page_number),
            "-l",
            str(page_number),
            "-singlefile",
            "-png",
            str(pdf_path),
            str(prefix),
        ],
        timeout=120,
    )
    if not output_path.exists():
        generated = sorted(pages_dir.glob(f"page-{page_number:03d}*.png"))
        if not generated:
            raise RuntimeError(f"pdftoppm did not render page {page_number}")
        output_path = normalize_rendered_name(generated[0], page_number)
    return output_path


def histogram_count_below(histogram: list[int], threshold: int) -> int:
    return sum(histogram[:threshold])


def analyze_image(image_path: Path) -> dict[str, Any]:
    assert Image is not None
    with Image.open(image_path) as original:
        rgb = original.convert("RGB")
        rgb.thumbnail((1200, 1200))
        gray = ImageOps.grayscale(rgb)
        histogram = gray.histogram()
        total = max(1, gray.width * gray.height)
        dark_ratio = histogram_count_below(histogram, 205) / total
        ink_ratio = histogram_count_below(histogram, 245) / total

        color_pixels = 0
        for red, green, blue in rgb.getdata():
            high = max(red, green, blue)
            low = min(red, green, blue)
            if high < 245 and high - low > 35:
                color_pixels += 1
        color_ratio = color_pixels / total

    is_blank = dark_ratio < BLANK_DARK_RATIO and ink_ratio < BLANK_INK_RATIO
    return {
        "dark_ratio": round(dark_ratio, 6),
        "ink_ratio": round(ink_ratio, 6),
        "color_ink_ratio": round(color_ratio, 6),
        "is_blank": is_blank,
        "color_notes_possible": color_ratio >= COLOR_NOTE_RATIO,
    }


def parse_tesseract_osd(output: str) -> dict[str, Any]:
    rotate = 0
    orientation_confidence = 0.0
    script = None
    script_confidence = 0.0
    for line in output.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip().lower()
        value = value.strip()
        if key == "rotate":
            try:
                rotate = int(value)
            except ValueError:
                pass
        elif key == "orientation confidence":
            try:
                orientation_confidence = float(value)
            except ValueError:
                pass
        elif key == "script":
            script = value
        elif key == "script confidence":
            try:
                script_confidence = float(value)
            except ValueError:
                pass
    return {
        "rotate": rotate,
        "orientation_confidence": round(orientation_confidence, 3),
        "script": script,
        "script_confidence": round(script_confidence, 3),
    }


def detect_orientation(image_path: Path) -> dict[str, Any]:
    result = run_command(
        ["tesseract", str(image_path), "stdout", "--psm", "0", "-l", "osd"],
        timeout=90,
        check=False,
    )
    combined = f"{result.stdout}\n{result.stderr}"
    parsed = parse_tesseract_osd(combined)
    parsed["ok"] = result.returncode == 0 or parsed["orientation_confidence"] > 0
    if not parsed["ok"]:
        parsed["warning"] = combined.strip()[-500:]
    return parsed


def rotate_if_needed(
    image_path: Path,
    normalized_dir: Path,
    page_number: int,
    orientation: dict[str, Any],
) -> tuple[Path, bool]:
    rotate = int(orientation.get("rotate") or 0)
    confidence = float(orientation.get("orientation_confidence") or 0.0)
    if rotate == 0 or confidence < MIN_OSD_CONFIDENCE:
        return image_path, False

    assert Image is not None
    normalized_path = normalized_dir / f"page-{page_number:03d}.png"
    with Image.open(image_path) as img:
        corrected = img.rotate(-rotate, expand=True)
        corrected.save(normalized_path)
    return normalized_path, True


def ocr_image(image_path: Path) -> str:
    result = run_command(
        [
            "tesseract",
            str(image_path),
            "stdout",
            "-l",
            "eng",
            "--oem",
            "1",
            "--psm",
            "3",
            "-c",
            "preserve_interword_spaces=1",
        ],
        timeout=180,
        check=False,
    )
    text = result.stdout.replace("\x0c", "").strip()
    if result.returncode != 0 and not text:
        return ""
    return text


def clean_ocr_text(text: str) -> str:
    lines = []
    for raw in text.replace("\r\n", "\n").splitlines():
        line = re.sub(r"[ \t]+", " ", raw).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


def cleanup_title_line(line: str) -> str:
    line = re.sub(r"^[^A-Za-z0-9]+", "", line).strip()
    line = re.sub(r"^(cooking|recipe)\s+", "", line, flags=re.IGNORECASE).strip()
    return line.strip(" -_*|")


def line_has_units(line: str) -> bool:
    lower = line.lower()
    return any(re.search(rf"\b{re.escape(unit)}\b", lower) for unit in UNIT_WORDS)


def is_boilerplate(line: str) -> bool:
    stripped = line.strip()
    return any(pattern.search(stripped) for pattern in BOILERPLATE_PATTERNS)


def title_score(line: str, index: int) -> float:
    stripped = cleanup_title_line(line)
    if len(stripped) < 4 or len(stripped) > 90:
        return -10.0
    if is_boilerplate(stripped):
        return -10.0

    lower = stripped.lower()
    words = re.findall(r"[A-Za-z][A-Za-z'&-]*", stripped)
    if not words:
        return -10.0
    score = max(0.0, 18.0 - index)
    if 2 <= len(words) <= 10:
        score += 8.0
    if len(words) > 14:
        score -= 8.0
    if any(char.isdigit() for char in stripped):
        score -= 6.0
    if line_has_units(stripped):
        score -= 12.0
    if lower.startswith("for ") and stripped.endswith(":"):
        score -= 14.0
    elif stripped.endswith(":") and len(words) <= 5:
        score -= 8.0
    if re.search(r"\b(preheat|bake|stir|add|cook|serve|combine|mix|whisk)\b", lower):
        score -= 8.0
    if stripped.istitle():
        score += 6.0
    if stripped.isupper() and len(words) > 1:
        score += 3.0
    if re.search(r"\b(recipe|cake|cookies?|muffins?|pasta|curry|chili|tacos?|potatoes|lamb|chicken|bread|pie|soup|salad)\b", lower):
        score += 4.0
    return score


def title_candidate(text: str) -> dict[str, Any]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    best_line = ""
    best_score = -10.0
    for index, line in enumerate(lines[:24]):
        score = title_score(line, index)
        if score > best_score:
            best_line = cleanup_title_line(line)
            best_score = score
    return {
        "title": best_line,
        "score": round(best_score, 2),
        "strong": best_score >= 13.0,
    }


def normalized_text(value: str) -> str:
    value = value.lower()
    value = re.sub(r"[^a-z0-9\s]", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def recipe_signal(text: str) -> dict[str, Any]:
    lower = text.lower()
    return {
        "has_ingredients": bool(re.search(r"\bingredients?\b", lower)),
        "has_directions": bool(
            re.search(r"\b(directions?|instructions?|preparation|method)\b", lower)
        ),
        "has_times": bool(re.search(r"\b\d+\s*(minutes?|mins?|hours?|hrs?)\b", lower)),
        "has_temperature": bool(re.search(r"\b\d{3}\s*(f|deg|degrees)\b", lower)),
        "has_units": any(unit in lower for unit in UNIT_WORDS),
    }


def first_words(text: str, limit: int = 420) -> str:
    words = normalized_text(text).split()
    return " ".join(words[:limit])


def refine_blank_decision(analysis: dict[str, Any], word_count: int) -> bool:
    if analysis["is_blank"]:
        return True
    if analysis["color_notes_possible"]:
        return False
    return (
        analysis["dark_ratio"] < 0.0002
        and analysis["ink_ratio"] < 0.02
        and word_count <= 12
    )


def title_needs_review(title: str) -> bool:
    stripped = title.strip()
    lower = stripped.lower()
    return bool(
        not stripped
        or (lower.startswith("for ") and stripped.endswith(":"))
        or lower in {"ingredients:", "ingredients", "method:", "method"}
    )


def handwriting_review_possible(
    page_number: int,
    analysis: dict[str, Any],
    title: dict[str, Any],
    word_count: int,
) -> bool:
    if analysis["is_blank"]:
        return False
    if analysis["color_notes_possible"]:
        return True
    return (
        page_number % 2 == 0
        and word_count <= 90
        and not title.get("strong")
        and analysis["dark_ratio"] < 0.04
    )


def suggest_groups(pages: list[dict[str, Any]], duplex: bool = True) -> list[dict[str, Any]]:
    groups: list[dict[str, Any]] = []
    current: dict[str, Any] | None = None

    for page in pages:
        page_number = int(page["page_number"])
        if page["analysis"]["is_blank"]:
            if current is not None:
                current["end_page"] = page_number - 1
                groups.append(current)
                current = None
            continue

        title = page.get("title_candidate", {})
        starts_new = current is None
        if current is not None:
            if duplex and page_number % 2 == 1 and title.get("strong"):
                starts_new = True
            elif title.get("strong") and page_number - int(current["start_page"]) >= 2:
                starts_new = True

        if starts_new:
            if current is not None:
                current["end_page"] = page_number - 1
                groups.append(current)
            current = {
                "group_id": f"recipe-{len(groups) + 1:03d}",
                "start_page": page_number,
                "end_page": page_number,
                "pages": [],
                "title": title.get("title") or f"Untitled page {page_number}",
                "title_score": title.get("score", 0),
            }

        assert current is not None
        current["pages"].append(page_number)
        current["end_page"] = page_number

    if current is not None:
        groups.append(current)

    by_page = {int(page["page_number"]): page for page in pages}
    for group in groups:
        group_text_parts = [
            by_page[page_number].get("ocr_text", "")
            for page_number in group["pages"]
            if page_number in by_page
        ]
        group_text = "\n".join(group_text_parts)
        title = title_candidate(group_text)
        if title["strong"]:
            group["title"] = title["title"]
            group["title_score"] = title["score"]
        signal = recipe_signal(group_text)
        group["signals"] = signal
        group["review_flags"] = review_flags_for_group(group, group_text, signal)
        handwriting_pages = [
            page_number
            for page_number in group["pages"]
            if by_page[page_number]["analysis"].get("handwriting_review_possible")
        ]
        group["handwriting_review_pages"] = handwriting_pages
        if handwriting_pages and "handwriting_review" not in group["review_flags"]:
            group["review_flags"].append("handwriting_review")
        group["text_signature"] = first_words(group_text)
        group["web_search_query"] = web_search_query(group["title"])

    return groups


def review_flags_for_group(
    group: dict[str, Any],
    text: str,
    signal: dict[str, Any],
) -> list[str]:
    flags: list[str] = []
    if float(group.get("title_score") or 0) < 13.0:
        flags.append("partial_or_title_review")
    elif title_needs_review(str(group.get("title", ""))):
        flags.append("partial_or_title_review")
    if len(normalized_text(text).split()) < 80:
        flags.append("short_text_review")
    if not (signal["has_ingredients"] or signal["has_units"]):
        flags.append("ingredients_review")
    if not (signal["has_directions"] or signal["has_times"] or signal["has_temperature"]):
        flags.append("instructions_review")
    return flags


def web_search_query(title: str) -> str:
    clean = re.sub(r"\s+", " ", title).strip(" -")
    if not clean or clean.lower().startswith("untitled"):
        return ""
    return f'"{clean}" recipe'


def sheet_pairs(page_count: int) -> list[dict[str, Any]]:
    pairs = []
    for first in range(1, page_count + 1, 2):
        second = first + 1 if first + 1 <= page_count else None
        pairs.append(
            {
                "sheet": (first + 1) // 2,
                "front_page": first,
                "back_page": second,
            }
        )
    return pairs


def collect_existing_groups(out_root: Path, current_index: Path | None = None) -> list[dict[str, Any]]:
    groups: list[dict[str, Any]] = []
    if not out_root.exists():
        return groups
    for index_path in sorted(out_root.glob("*/index.json")):
        if current_index is not None and index_path == current_index:
            continue
        data = load_json(index_path)
        if not data:
            continue
        source_name = data.get("source", {}).get("filename", index_path.parent.name)
        for group in data.get("groups", []):
            groups.append(
                {
                    "source": source_name,
                    "group_id": group.get("group_id"),
                    "title": group.get("title", ""),
                    "signature": group.get("text_signature", ""),
                }
            )
    return groups


def find_duplicate_candidates(
    groups: list[dict[str, Any]],
    existing_groups: list[dict[str, Any]],
) -> dict[str, list[dict[str, Any]]]:
    candidates: dict[str, list[dict[str, Any]]] = {}
    all_previous = existing_groups.copy()
    for group in groups:
        group_id = str(group["group_id"])
        title_norm = normalized_text(str(group.get("title", "")))
        signature = str(group.get("text_signature", ""))
        matches = []
        for other in all_previous:
            other_title_norm = normalized_text(str(other.get("title", "")))
            title_match = title_norm and title_norm == other_title_norm
            text_score = difflib.SequenceMatcher(
                None,
                signature[:3500],
                str(other.get("signature", ""))[:3500],
            ).ratio()
            title_score_value = difflib.SequenceMatcher(
                None,
                title_norm,
                other_title_norm,
            ).ratio()
            score = max(text_score, title_score_value if title_match else title_score_value * 0.92)
            if title_match or score >= 0.86:
                matches.append(
                    {
                        "source": other.get("source"),
                        "group_id": other.get("group_id"),
                        "title": other.get("title"),
                        "score": round(score, 3),
                        "reason": "same_title" if title_match else "similar_text",
                    }
                )
        if matches:
            candidates[group_id] = sorted(matches, key=lambda item: item["score"], reverse=True)
        all_previous.append(
            {
                "source": "current_pdf",
                "group_id": group_id,
                "title": group.get("title", ""),
                "signature": signature,
            }
        )
    return candidates


def create_thumbnail(source: Path, thumbs_dir: Path, page_number: int) -> Path:
    assert Image is not None
    output = thumbs_dir / f"page-{page_number:03d}.jpg"
    with Image.open(source) as img:
        img = img.convert("RGB")
        img.thumbnail((420, 560))
        img.save(output, quality=82, optimize=True)
    return output


def create_contact_sheet(page_records: list[dict[str, Any]], output_path: Path) -> None:
    assert Image is not None
    thumbs: list[tuple[Image.Image, dict[str, Any]]] = []
    for page in page_records:
        thumb_path = Path(page["paths"]["thumbnail"])
        if not thumb_path.exists():
            continue
        img = Image.open(thumb_path).convert("RGB")
        thumbs.append((img, page))

    if not thumbs:
        return

    columns = 4
    label_height = 52
    padding = 16
    cell_width = max(img.width for img, _ in thumbs) + padding * 2
    cell_height = max(img.height for img, _ in thumbs) + label_height + padding * 2
    rows = (len(thumbs) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell_width, rows * cell_height), "white")

    for index, (img, page) in enumerate(thumbs):
        row = index // columns
        column = index % columns
        x = column * cell_width + padding + (cell_width - padding * 2 - img.width) // 2
        y = row * cell_height + padding
        sheet.paste(img, (x, y))
        label = f"p{page['page_number']:03d}"
        if page["analysis"]["is_blank"]:
            label += " blank"
        if page.get("orientation", {}).get("rotation_applied"):
            label += f" rotated {page['orientation']['rotate']}"
        draw_label(sheet, label, column * cell_width + padding, y + img.height + 8)

    sheet.save(output_path, quality=88, optimize=True)

    for img, _ in thumbs:
        img.close()


def draw_label(image: Image.Image, text: str, x: int, y: int) -> None:
    from PIL import ImageDraw

    draw = ImageDraw.Draw(image)
    draw.text((x, y), text, fill=(40, 40, 40))


def make_relative(path: Path, base: Path) -> str:
    try:
        return str(path.relative_to(base))
    except ValueError:
        return str(path)


def process_page(
    pdf_path: Path,
    pages_dir: Path,
    normalized_dir: Path,
    thumbs_dir: Path,
    ocr_dir: Path,
    page_number: int,
    page_count: int,
    render_dpi: int,
    skip_osd: bool,
    skip_ocr: bool,
) -> dict[str, Any]:
    print(f"  page {page_number}/{page_count}", flush=True)
    rendered = render_page(pdf_path, pages_dir, page_number, render_dpi)
    analysis = analyze_image(rendered)
    thumbnail = create_thumbnail(rendered, thumbs_dir, page_number)
    orientation = {"rotate": 0, "orientation_confidence": 0.0, "ok": False}
    ocr_input = rendered

    if not analysis["is_blank"] and not skip_osd:
        orientation = detect_orientation(rendered)
        ocr_input, rotation_applied = rotate_if_needed(
            rendered,
            normalized_dir,
            page_number,
            orientation,
        )
        orientation["rotation_applied"] = rotation_applied
    else:
        orientation["rotation_applied"] = False

    text = ""
    if not analysis["is_blank"] and not skip_ocr:
        text = clean_ocr_text(ocr_image(ocr_input))

    ocr_path = ocr_dir / f"page-{page_number:03d}.txt"
    ocr_path.write_text(text + ("\n" if text else ""), encoding="utf-8")
    word_count = len(normalized_text(text).split())
    analysis["is_blank"] = refine_blank_decision(analysis, word_count)
    title = (
        title_candidate(text)
        if text and not analysis["is_blank"]
        else {"title": "", "score": 0, "strong": False}
    )
    analysis["handwriting_review_possible"] = handwriting_review_possible(
        page_number,
        analysis,
        title,
        word_count,
    )

    return {
        "page_number": page_number,
        "analysis": analysis,
        "orientation": orientation,
        "title_candidate": title,
        "ocr_word_count": word_count,
        "ocr_text": text,
        "paths": {
            "rendered": str(rendered),
            "ocr": str(ocr_path),
            "thumbnail": str(thumbnail),
            "ocr_input": str(ocr_input),
        },
    }


def process_pdf(
    pdf_path: Path,
    out_root: Path,
    *,
    render_dpi: int,
    pdf_ready_timeout: int,
    workers: int,
    force: bool,
    duplex: bool,
    skip_osd: bool,
    skip_ocr: bool,
) -> dict[str, Any]:
    pdf_path = pdf_path.resolve()
    out_dir = out_root / pdf_path.stem
    pages_dir = out_dir / "pages"
    normalized_dir = out_dir / "normalized-pages"
    thumbs_dir = out_dir / "thumbs"
    ocr_dir = out_dir / "ocr"
    index_path = out_dir / "index.json"

    pdfinfo = parse_pdfinfo(pdf_path, ready_timeout=pdf_ready_timeout)
    page_count = int(pdfinfo.get("pages") or 0)
    if page_count <= 0:
        raise RuntimeError(f"Could not determine page count for {pdf_path}")

    source = {
        "filename": pdf_path.name,
        "path": str(pdf_path),
        "fingerprint": file_fingerprint(pdf_path),
        "pdfinfo": pdfinfo,
    }

    if not force and is_current_index(index_path, source, render_dpi):
        data = load_json(index_path)
        assert data is not None
        data["_skipped_current"] = True
        return data

    for directory in (pages_dir, normalized_dir, thumbs_dir, ocr_dir):
        directory.mkdir(parents=True, exist_ok=True)

    page_records: list[dict[str, Any]] = []
    worker_count = max(1, workers)
    if worker_count == 1:
        for page_number in range(1, page_count + 1):
            page_records.append(
                process_page(
                    pdf_path,
                    pages_dir,
                    normalized_dir,
                    thumbs_dir,
                    ocr_dir,
                    page_number,
                    page_count,
                    render_dpi,
                    skip_osd,
                    skip_ocr,
                )
            )
    else:
        with concurrent.futures.ThreadPoolExecutor(max_workers=worker_count) as executor:
            futures = {
                executor.submit(
                    process_page,
                    pdf_path,
                    pages_dir,
                    normalized_dir,
                    thumbs_dir,
                    ocr_dir,
                    page_number,
                    page_count,
                    render_dpi,
                    skip_osd,
                    skip_ocr,
                ): page_number
                for page_number in range(1, page_count + 1)
            }
            for future in concurrent.futures.as_completed(futures):
                page_records.append(future.result())

        page_records.sort(key=lambda page: int(page["page_number"]))

    contact_sheet = out_dir / "contact-sheet.jpg"
    create_contact_sheet(page_records, contact_sheet)

    groups = suggest_groups(page_records, duplex=duplex)
    duplicate_candidates = find_duplicate_candidates(
        groups,
        collect_existing_groups(out_root, current_index=index_path),
    )
    for group in groups:
        matches = duplicate_candidates.get(str(group["group_id"]), [])
        if matches:
            group["duplicate_candidates"] = matches
            if "duplicate_review" not in group["review_flags"]:
                group["review_flags"].append("duplicate_review")

    index = {
        "script_version": SCRIPT_VERSION,
        "processed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "source": source,
        "processing_options": {
            "render_dpi": render_dpi,
            "workers": worker_count,
            "duplex": duplex,
            "skip_osd": skip_osd,
            "skip_ocr": skip_ocr,
        },
        "outputs": {
            "output_dir": str(out_dir),
            "contact_sheet": str(contact_sheet),
        },
        "sheet_pairs": sheet_pairs(page_count),
        "pages": page_records,
        "groups": groups,
        "summary": {
            "page_count": page_count,
            "blank_pages": [
                page["page_number"] for page in page_records if page["analysis"]["is_blank"]
            ],
            "color_note_pages": [
                page["page_number"]
                for page in page_records
                if page["analysis"]["color_notes_possible"]
            ],
            "handwriting_review_pages": [
                page["page_number"]
                for page in page_records
                if page["analysis"].get("handwriting_review_possible")
            ],
            "recipe_group_count": len(groups),
            "duplicate_group_count": sum(
                1 for group in groups if group.get("duplicate_candidates")
            ),
            "review_group_count": sum(1 for group in groups if group.get("review_flags")),
        },
    }

    index_path.write_text(json.dumps(index, indent=2, ensure_ascii=False), encoding="utf-8")
    write_markdown_index(index, out_dir / "index.md")
    return index


def write_markdown_index(index: dict[str, Any], output_path: Path) -> None:
    out_dir = Path(index["outputs"]["output_dir"])
    lines = [
        f"# {index['source']['filename']}",
        "",
        f"- Processed: {index['processed_at']}",
        f"- Pages: {index['summary']['page_count']}",
        f"- Blank pages: {format_list(index['summary']['blank_pages'])}",
        f"- Possible color-note pages: {format_list(index['summary']['color_note_pages'])}",
        f"- Pages needing handwriting review: {format_list(index['summary']['handwriting_review_pages'])}",
        f"- Contact sheet: `{make_relative(Path(index['outputs']['contact_sheet']), out_dir)}`",
        "",
        "## Suggested Recipe Groups",
        "",
    ]

    for group in index["groups"]:
        page_range = format_page_range(group["pages"])
        lines.append(f"### {group['group_id']}: {group['title']}")
        lines.append(f"- Pages: {page_range}")
        if group.get("review_flags"):
            lines.append(f"- Review flags: {', '.join(group['review_flags'])}")
        if group.get("handwriting_review_pages"):
            lines.append(
                f"- Handwriting review pages: {format_list(group['handwriting_review_pages'])}"
            )
        if group.get("web_search_query"):
            lines.append(f"- Web search: `{group['web_search_query']}`")
        if group.get("duplicate_candidates"):
            lines.append("- Duplicate candidates:")
            for candidate in group["duplicate_candidates"][:5]:
                lines.append(
                    "  - "
                    f"{candidate['title']} "
                    f"({candidate['source']} / {candidate['group_id']}, "
                    f"score {candidate['score']})"
                )
        lines.append("")

    lines.extend(["## Page Details", ""])
    for page in index["pages"]:
        title = page["title_candidate"].get("title") or "(no title detected)"
        flags = []
        if page["analysis"]["is_blank"]:
            flags.append("blank")
        if page["analysis"]["color_notes_possible"]:
            flags.append("color notes possible")
        if page.get("orientation", {}).get("rotation_applied"):
            flags.append(f"rotated {page['orientation']['rotate']} for OCR")
        if page["analysis"].get("handwriting_review_possible"):
            flags.append("handwriting review")
        suffix = f" - {', '.join(flags)}" if flags else ""
        lines.append(f"- Page {page['page_number']:03d}: {title}{suffix}")

    output_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def format_list(values: list[Any]) -> str:
    if not values:
        return "none"
    return ", ".join(str(value) for value in values)


def format_page_range(pages: list[int]) -> str:
    if not pages:
        return "none"
    if len(pages) == 1:
        return str(pages[0])
    return f"{pages[0]}-{pages[-1]}"


def discover_pdfs(target: Path, latest: bool, limit: int | None) -> list[Path]:
    if target.is_file():
        pdfs = [target]
    else:
        pdfs = sorted(target.glob("*.pdf"), key=lambda path: path.stat().st_mtime)
    if latest and pdfs:
        pdfs = [max(pdfs, key=lambda path: path.stat().st_mtime)]
    if limit is not None:
        pdfs = pdfs[:limit]
    return pdfs


def default_out_root(target: Path) -> Path:
    if target.is_file():
        return target.parent / ".processed"
    return target / ".processed"


def process_command(args: argparse.Namespace) -> int:
    missing = require_tools()
    if missing:
        print("Missing dependencies: " + ", ".join(missing), file=sys.stderr)
        return 2

    target = Path(args.target).expanduser()
    if not target.exists():
        print(f"Target does not exist: {target}", file=sys.stderr)
        return 2

    out_root = Path(args.out_root).expanduser() if args.out_root else default_out_root(target)
    out_root.mkdir(parents=True, exist_ok=True)

    pdfs = discover_pdfs(target, latest=args.latest, limit=args.limit)
    if not pdfs:
        print(f"No PDF files found in {target}")
        return 0

    results = []
    for pdf in pdfs:
        print(f"Processing {pdf}")
        try:
            result = process_pdf(
                pdf,
                out_root,
                render_dpi=args.render_dpi,
                pdf_ready_timeout=args.pdf_ready_timeout,
                workers=args.workers,
                force=args.force,
                duplex=not args.no_duplex,
                skip_osd=args.skip_osd,
                skip_ocr=args.skip_ocr,
            )
            results.append(result)
            if result.get("_skipped_current"):
                print("  current index already exists; skipped")
            else:
                summary = result["summary"]
                print(
                    "  done: "
                    f"{summary['page_count']} pages, "
                    f"{summary['recipe_group_count']} groups, "
                    f"{len(summary['blank_pages'])} blank pages"
                )
        except Exception as exc:
            print(f"  ERROR: {exc}", file=sys.stderr)
            if not args.keep_going:
                return 1

    write_catalog(out_root)
    print(f"Catalog updated: {out_root / 'catalog.csv'}")
    return 0


def write_catalog(out_root: Path) -> None:
    rows = []
    for index_path in sorted(out_root.glob("*/index.json")):
        data = load_json(index_path)
        if not data:
            continue
        source = data.get("source", {})
        for group in data.get("groups", []):
            rows.append(
                {
                    "source_pdf": source.get("filename", ""),
                    "group_id": group.get("group_id", ""),
                    "title": group.get("title", ""),
                    "pages": format_page_range(group.get("pages", [])),
                    "review_flags": ";".join(group.get("review_flags", [])),
                    "handwriting_review_pages": format_list(
                        group.get("handwriting_review_pages", [])
                    ),
                    "duplicate_candidates": len(group.get("duplicate_candidates", [])),
                    "web_search_query": group.get("web_search_query", ""),
                    "index_path": str(index_path),
                }
            )

    csv_path = out_root / "catalog.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as fh:
        fieldnames = [
            "source_pdf",
            "group_id",
            "title",
            "pages",
            "review_flags",
            "handwriting_review_pages",
            "duplicate_candidates",
            "web_search_query",
            "index_path",
        ]
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    md_lines = ["# Recipe Scan Catalog", ""]
    if rows:
        md_lines.append("| Source PDF | Group | Title | Pages | Flags | Duplicates |")
        md_lines.append("| --- | --- | --- | --- | --- | --- |")
        for row in rows:
            md_lines.append(
                "| "
                + " | ".join(
                    markdown_cell(str(row[key]))
                    for key in (
                        "source_pdf",
                        "group_id",
                        "title",
                        "pages",
                        "review_flags",
                        "duplicate_candidates",
                    )
                )
                + " |"
            )
    else:
        md_lines.append("No processed recipe groups yet.")
    (out_root / "catalog.md").write_text("\n".join(md_lines) + "\n", encoding="utf-8")


def markdown_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def report_command(args: argparse.Namespace) -> int:
    target = Path(args.target).expanduser()
    out_root = Path(args.out_root).expanduser() if args.out_root else default_out_root(target)
    write_catalog(out_root)
    data = load_catalog(out_root / "catalog.csv")
    print(f"Catalog: {out_root / 'catalog.csv'}")
    print(f"Recipe groups: {len(data)}")
    review = sum(1 for row in data if row.get("review_flags"))
    duplicates = sum(1 for row in data if row.get("duplicate_candidates") not in ("", "0"))
    print(f"Groups needing review: {review}")
    print(f"Duplicate candidates: {duplicates}")
    return 0


def load_catalog(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def check_command(_: argparse.Namespace) -> int:
    missing = require_tools()
    if missing:
        print("Missing dependencies: " + ", ".join(missing))
        return 1
    print("All dependencies available: pdfinfo, pdftoppm, tesseract, Pillow")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Preprocess scanned recipe PDFs into OCR/index files.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    check = subparsers.add_parser("check", help="Verify required local tools.")
    check.set_defaults(func=check_command)

    process = subparsers.add_parser("process", help="Process a PDF or folder of PDFs.")
    process.add_argument("target", help="PDF file or folder containing scanned PDFs.")
    process.add_argument("--out-root", help="Output folder. Defaults to TARGET/.processed.")
    process.add_argument("--render-dpi", type=int, default=DEFAULT_RENDER_DPI)
    process.add_argument(
        "--workers",
        type=int,
        default=DEFAULT_WORKERS,
        help="Pages to OCR in parallel. Use 1 for serial processing.",
    )
    process.add_argument(
        "--pdf-ready-timeout",
        type=int,
        default=DEFAULT_PDF_READY_TIMEOUT,
        help="Seconds to wait for a newly scanned PDF to become readable.",
    )
    process.add_argument("--latest", action="store_true", help="Process only the newest PDF.")
    process.add_argument("--limit", type=int, help="Process at most N PDFs from a folder.")
    process.add_argument("--force", action="store_true", help="Reprocess even when current.")
    process.add_argument("--keep-going", action="store_true", help="Continue after errors.")
    process.add_argument("--no-duplex", action="store_true", help="Do not use duplex page hints.")
    process.add_argument("--skip-osd", action="store_true", help="Skip orientation detection.")
    process.add_argument("--skip-ocr", action="store_true", help="Render/analyze pages without OCR.")
    process.set_defaults(func=process_command)

    report = subparsers.add_parser("report", help="Regenerate and summarize the catalog.")
    report.add_argument("target", help="PDF scan folder or a PDF inside it.")
    report.add_argument("--out-root", help="Output folder. Defaults to TARGET/.processed.")
    report.set_defaults(func=report_command)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
