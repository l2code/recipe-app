package com.recipearchive.app.data.repository

/**
 * Turns free-text user input into a safe FTS4 MATCH expression.
 *
 * SQLite's default tokenizer splits on every non-alphanumeric character,
 * including apostrophes and hyphens ("grandma's" indexes as "grandma" + "s").
 * We extract the same letter/digit runs from the query so quotes, colons,
 * apostrophes etc. can never be interpreted as FTS query syntax *and* so a
 * word like "grandma's" still matches what was actually indexed. Each run
 * becomes an independent prefix match.
 */
object SearchQuerySanitizer {
    private val tokenRegex = Regex("[\\p{L}\\p{Nd}]+")

    fun sanitize(input: String): String? {
        val tokens = tokenRegex.findAll(input).map { it.value }.filter { it.isNotEmpty() }.toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
