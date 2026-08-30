# Recipe Scan Preprocessor

This folder contains a local preprocessing script for scanned recipe PDFs. It is
intended to do the repetitive work before any recipe app/database import:

- render each PDF page to an image
- detect blank or near-blank pages
- run Tesseract orientation detection and OCR
- save per-page OCR text
- build a contact sheet for quick visual review
- suggest recipe groups and title candidates
- flag possible duplicates, partials, and pages with colored handwriting
- generate `catalog.csv` and `catalog.md`

The original scanner PDFs are never modified.

## Quick Start

Check dependencies:

```bash
python3 recipe_preprocess.py check
```

Process only the newest scan in the NAS scan folder:

```bash
python3 recipe_preprocess.py process /media/nas/RecipeScans --latest
```

The script uses 4 page workers by default. To use a different level:

```bash
python3 recipe_preprocess.py process /media/nas/RecipeScans --latest --workers 2
```

If you run this immediately after scanning, the script waits for the PDF to be
fully readable before starting OCR. For unusually large batches, increase that
wait:

```bash
python3 recipe_preprocess.py process /media/nas/RecipeScans --latest --pdf-ready-timeout 300
```

Reprocess the newest scan after changing script settings:

```bash
python3 recipe_preprocess.py process /media/nas/RecipeScans --latest --force
```

Audit already-processed scans for likely partial recipes or split pages:

```bash
python3 recipe_audit_partials.py /media/nas/RecipeScans/.processed
```

That writes:

- `/media/nas/RecipeScans/.processed/partial-audit.md`
- `/media/nas/RecipeScans/.processed/partial-audit.csv`

Add a human-readable batch number to a run of scans after preprocessing:

```bash
python3 recipe_tag_batches.py /media/nas/RecipeScans \
  --first FIRST_SCANNER_FILENAME.pdf \
  --count 1 \
  --start-number 13
```

This prefixes each PDF with `Batch_XX__`, migrates its processed folder and
embedded paths, refreshes the catalog, and updates
`/media/nas/RecipeScans/.processed/batch-labels.csv`. Use the scanner filename
of the first PDF in the run and the number of consecutive PDFs to label.

Process a specific PDF:

```bash
python3 recipe_preprocess.py process /media/nas/RecipeScans/BRW5CF370D986CA_08282026_204304_000057.pdf
```

Outputs are written to:

```text
/media/nas/RecipeScans/.processed/<pdf-name>/
```

Important generated files:

- `index.md`: human-readable review notes
- `index.json`: structured data for later app/database import
- `contact-sheet.jpg`: quick page overview
- `ocr/page-###.txt`: raw OCR text for each page
- `/media/nas/RecipeScans/.processed/catalog.csv`: all suggested recipe groups
- `/media/nas/RecipeScans/.processed/catalog.md`: Markdown version of the catalog

## Notes

The script is deliberately conservative. It can flag likely duplicate recipes
and likely partial pages, but those decisions still need human review. Handwritten
notes are detected as "possible color notes" when the scan has colored ink; pencil
or black pen notes may still need visual review from the contact sheet.

For your current Brother scan workflow, keep scanning in 10-15 sheet batches to
`/media/nas/RecipeScans`, then run:

```bash
python3 recipe_preprocess.py process /media/nas/RecipeScans --latest
```
