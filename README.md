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

## Android App (Phase 3)

A native Android tablet app lives in [`android-app/`](android-app/). It imports the
Phase 2 archive's `recipe-app-import.json` bundle into an offline Room database and
provides a searchable recipe library and detail view. See
[`android-app/ARCHITECTURE.md`](android-app/ARCHITECTURE.md) for the internal layering.

### Prerequisites

- JDK 17+ (built and tested against OpenJDK 21).
- Android SDK with `platform-tools`, `platforms;android-34`, and
  `build-tools;34.0.0` installed. Point `android-app/local.properties` at it with
  `sdk.dir=/path/to/Android/Sdk` (this file is machine-specific and not committed).
- No system-wide Gradle install is required — the project uses the Gradle wrapper
  (`./gradlew`), committed under `android-app/gradle/wrapper/`.
- To run instrumented UI tests: a connected device or emulator (AVD) with API 26+.
  An x86_64 tablet AVD (e.g. `google_apis` system image, API 34) works well and only
  needs `/dev/kvm` access for hardware acceleration.

### Build

```bash
cd android-app
./gradlew assembleDebug
```

The debug APK is written to `android-app/app/build/outputs/apk/debug/app-debug.apk`.

### Test

```bash
cd android-app
./gradlew testDebugUnitTest        # JUnit + Robolectric: import, DAO, search, ViewModel tests
./gradlew lintDebug                # Android lint
./gradlew connectedDebugAndroidTest  # Compose UI tests; requires a connected device/emulator
```

### How the import bundle is generated and packaged

`recipe_export.py` (this directory) reads the archive SQLite database
(`/media/nas/RecipeScans/.processed/archive/recipe-archive.sqlite`) and writes
`recipe-app-import.json` next to it:

```bash
python3 recipe_export.py /media/nas/RecipeScans/.processed/archive/recipe-archive.sqlite
```

The Android app never reads that path directly and never bundles a second checked-in
copy of the JSON. Instead, a Gradle task (`copyImportBundle`, wired into `preBuild`)
copies the current bundle into a generated, gitignored assets directory
(`app/build/generated/assets/importBundle/`) at build time, so `./gradlew assembleDebug`
or `./gradlew testDebugUnitTest` always package whatever the archive last exported. To
point at a different bundle path (e.g. a snapshot for testing), pass:

```bash
./gradlew assembleDebug -PrecipeBundleSource=/path/to/other-import.json
```

### How to force or rerun an import

The app re-imports the bundled `recipe-app-import.json` automatically on every cold
start (idempotent — see [`ImportService`](android-app/app/src/main/kotlin/com/recipearchive/app/data/import/ImportService.kt)).
From the Library screen, an import failure shows a "Retry import" button that reruns
the same import. There is currently no in-app "pick a different file" flow; to import
a different bundle during development, change `-PrecipeBundleSource` (above) and
rebuild.

### Database/schema overview

Room database `RecipeDatabase` (schema version 5, exported to `android-app/app/schemas/`
for migration testing):

- **Import-owned** (fully replaced per recipe on every import): `recipes`,
  `ingredients`, `instructions`, `recipe_pages`, `handwritten_notes`,
  `source_evidence`, `recipe_review_flags`, and the FTS4 search index
  (`recipe_search_documents` / `recipe_search_fts`).
- **App-owned** (preserved across imports): `recipe_app_state` — favorite, personal
  rating, personal notes, review completion, and editable category;
  `collections` / `recipe_collection_cross_ref` — user-created recipe collections;
  `cooking_sessions`, `pantry_items`, `shopping_items` /
  `shopping_item_sources`, and `meal_plan_entries` — the persistent cooking workflow.
- **Audit**: `import_runs` — one row per import attempt with counts and status.

Cooking timers can be paused and resumed across navigation or app restarts. The
History destination shows confirmed sessions from the current calendar week or the
complete cooking history; active and discarded sessions are excluded.

Recipes are upserted by their stable `id` using an explicit insert-or-update (not
`INSERT OR REPLACE`), because SQLite implements `REPLACE` as a physical
delete-then-insert that would cascade-delete every foreign-key child row — including
`recipe_app_state` — on every reimport.

### Known Phase 3 limitations

- Scan images and PDFs stay on the NAS; only their path references are stored. The
  app does not copy or display scan imagery yet (see "Original OCR" and "Scan pages"
  in the detail screen for the text-only provenance that is shown).
- Cooking-session inference, recommendations, multi-user profiles, sync, auth, and
  nutrition calculation are not implemented. Cooking sessions currently start
  manually and only confirmed sessions count toward history.
- Two-pane (list + detail) tablet layout was not built; the detail screen is
  full-screen even on large tablets, reached via standard back/forward navigation.
- Compose UI tests were only run against `connectedDebugAndroidTest` on a single
  local x86_64 AVD; no CI device matrix exists yet.

### Future phases

Future work can add confirmed inferred-session detection, per-step timers, richer
calendar planning, nutrition, and optional sync. These features must continue to
respect the same importer/app-owned boundary as sessions, pantry, and planning data.
