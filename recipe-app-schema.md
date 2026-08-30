# Recipe app data contract

The Android app imports `recipe-app-import.json`. The archive SQLite database
remains the review/source database; the import bundle is disposable and can be
regenerated.

## Recipe

- `id`: stable canonical recipe ID
- `title`: curated display title; use `Untitled recipe` only as a fallback
- `rawText`: original OCR text, never overwritten
- `ingredients[]`: conservative parsed candidates with `rawText`, `quantity`,
  `unit`, `item`, and `parseStatus` (`candidate` or `needs_review`)
- `instructions[]`: ordered candidates with `order`, `text`, and `parseStatus`
- `pageRefs[]`: immutable references to source scan pages
- `handwrittenNotes[]`: transcription, OCR draft, crop path, page, and status
- `source`: publisher/domain/URL/evidence/status
- `reviewFlags[]`: unresolved archive or parsing concerns
- `arrangementStatus`, `duplicateStatus`, `wordCount`: provenance metadata

## Rules

1. Never discard `rawText` or original scan references.
2. Never merge duplicate printouts destructively; preserve their provenance.
3. Treat parsed ingredients and instructions as candidates until reviewed.
4. Imports must be idempotent by stable `id`.
5. Unknown values use an empty string/list plus an explicit review status, not a
   fabricated value.

## App-owned data

The Android database should add app-owned tables for ratings, favorites,
personal notes, cook history, meal plans, meal-plan entries, shopping lists,
shopping-list items, pantry items, tags, and import runs. These must not be
written back into the archive staging database automatically.
