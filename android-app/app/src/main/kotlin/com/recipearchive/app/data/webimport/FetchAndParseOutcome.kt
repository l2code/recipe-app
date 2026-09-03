package com.recipearchive.app.data.webimport

/**
 * Result of fetching + parsing a URL without saving anything -- the
 * "Review Before Import" preview step. [WebRecipeImportService.importFromUrl]
 * saves immediately instead; this is only used when the user wants to look
 * at (and possibly edit) the parsed recipe before it's written to the library.
 */
sealed class FetchAndParseOutcome {
    data class Success(val parsed: ParsedRecipe, val url: String, val domain: String, val publisher: String) :
        FetchAndParseOutcome()

    object NotFound : FetchAndParseOutcome()

    data class NetworkError(val message: String) : FetchAndParseOutcome()

    data class ParseError(val message: String) : FetchAndParseOutcome()
}
