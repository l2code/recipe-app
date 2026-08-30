# Architecture

Phase 3 native Android app for browsing the digitized recipe archive offline. No
DI framework is used — dependencies are constructed by hand in [`AppContainer`](app/src/main/kotlin/com/recipearchive/app/AppContainer.kt)
and held on [`RecipeApplication`](app/src/main/kotlin/com/recipearchive/app/RecipeApplication.kt).

```
UI (Compose)  ->  ViewModel  ->  Repository  ->  Room (DAOs)
                                     |
                                Import service  ->  Android assets (JSON bundle)
```

## UI layer

`ui/library/LibraryScreen.kt` and `ui/detail/DetailScreen.kt` hold the Compose UI.
Each screen is split into a stateful `*Screen` composable (wires a ViewModel) and a
stateless `*Content` composable (pure function of a UI-state data class). The
stateless composables are what the Compose UI tests under `src/androidTest` exercise
directly, without needing a real database or ViewModel.

`ui/nav/RecipeNavHost.kt` wires two routes (`library`, `detail/{recipeId}`) with
Navigation Compose. `ui/MainActivity.kt` computes the window size class once via
`calculateWindowSizeClass` and threads it down so the library can switch between a
single-column list (compact/medium width) and an adaptive grid (expanded width).

A two-pane (list + detail side by side) layout for very large tablets was left as a
documented limitation rather than built now — see the README's "Known Phase 3
limitations" section.

## ViewModel layer

`LibraryViewModel` owns three pieces of state combined into one `LibraryUiState`:
the current search query, the import phase (loading/error/done), and the live
recipe list for that query (via `RecipeRepository.observeLibrary`). Search input is
debounced (250ms) before hitting the database. `DetailViewModel` exposes a single
`RecipeDetailUi?` StateFlow for one recipe ID.

Both ViewModels take their `RecipeRepository` (and any other dependencies) as
constructor parameters and use a small `ViewModelProvider.Factory` for wiring —
no reflection-based DI.

## Repository layer

`RecipeRepository` is the only class that talks to `RecipeDatabase` on the read/write
path used by the UI. It:

- Exposes `observeLibrary(query)`, which returns the full recipe list for a blank
  query or, for a non-blank query, the FTS-matched subset in title-first order.
- Exposes `observeRecipeDetail(recipeId)`, combining the recipe row with its
  ordered children (ingredients, instructions, pages, handwritten notes, source
  evidence, review flags) and its app-owned state into one `RecipeDetailUi`.
- Exposes the app-owned mutations (`setFavorite`, `setPersonalNotes`,
  `setPersonalRating`).
- Exposes `importBundle(context, assetName)`, delegating to `ImportService`.

Ingredient/instruction/page ordering is resolved by explicit `ORDER BY displayOrder`
queries in the DAOs and combined in the repository, rather than relying on Room's
`@Relation` (which doesn't guarantee child ordering) — this is why the repository
builds `RecipeDetailUi` from several small Flows instead of one relation query.

## Room layer

See the root [`README.md`](../README.md#databaseschema-overview) for the table
list and the import-owned vs. app-owned split. Notable implementation details:

- `RecipeEntity` is upserted with an explicit insert-or-update, not
  `@Insert(onConflict = REPLACE)`. SQLite's `REPLACE` is a physical
  delete-then-insert, and because `recipe_app_state` has a `ON DELETE CASCADE`
  foreign key to `recipes.id`, using `REPLACE` on the recipe row would silently
  wipe favorites/notes/ratings on every reimport. This was caught by
  `ImportServiceTest`'s "app-owned favorite state survives reimport" test.
- Import-owned child tables (ingredients, instructions, pages, handwritten notes,
  source evidence, review flags) are replaced wholesale per recipe on every import
  (`DELETE ... WHERE recipeId = :id` then bulk insert) rather than diffed — this
  makes idempotency trivial to reason about and test.
- Full-text search uses Room's `@Fts4(contentEntity = ...)` against a denormalized
  `RecipeSearchDocumentEntity` (one row per recipe, concatenating title/raw
  OCR/ingredients/instructions/handwriting/source text). Room's own triggers keep
  the FTS shadow table in sync whenever the importer writes to the content table.
- Schema is exported (`room.schemaLocation`) to `app/schemas/` and the v1→v2→v3→v4
  path is covered with `MigrationTestHelper` (see `RecipeDatabaseMigrationTest`).

## Import service

`ImportService.import(jsonText)` is the deterministic core (no `Context` dependency,
so it's directly unit-testable with fixture strings); `importFromAssets` is a thin
wrapper that reads the asset file and delegates to it. It:

1. Decodes only the envelope (`schemaVersion`, `generatedAt`, and `recipes` as raw
   `JsonElement`s) first, so a schema-version rejection never touches the database.
2. Decodes each recipe element individually, catching per-element failures so one
   malformed recipe is skipped and reported rather than failing the whole import.
3. Runs all writes for the valid recipes inside a single `withTransaction` block, so
   a failure partway through leaves the previous data intact.
4. Records every attempt (success, partial, or failure) as an `ImportRunEntity`,
   written *outside* the transaction so it survives even a rolled-back import.

## Search indexing

`SearchQuerySanitizer` turns free-text input into a safe FTS4 `MATCH` expression by
extracting letter/digit runs (`[\p{L}\p{Nd}]+`) and making each one a prefix term.
This mirrors SQLite's own tokenizer, which splits on every non-alphanumeric
character (so `grandma's` indexes as `grandma` + `s`) — using the same splitting
rule means user queries with apostrophes, hyphens, or stray punctuation match what
was actually indexed instead of crashing or silently failing to match.

`RecipeRepository.searchRecipeIds` runs the sanitized query twice — once restricted
to the `title` column, once unrestricted — and unions the results with title matches
first, giving "title matches ranked first" without needing FTS5/bm25 ranking.

## Ownership boundary

Anything the importer can compute from the bundle (recipe content, provenance,
review flags, search index) is import-owned and is unconditionally replaced on
every import. Anything a person enters in the app (favorite, rating, personal
notes, review-completion) lives in `recipe_app_state`, is only ever created with
defaults by the importer (`INSERT OR IGNORE`), and is never deleted or overwritten
by a reimport. Future app-owned tables (cook history, meal plans, shopping lists —
see the README) should follow the same rule: reference `recipeId`, never get
written to by `ImportService`.
