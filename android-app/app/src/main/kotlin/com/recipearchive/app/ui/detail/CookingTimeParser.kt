package com.recipearchive.app.ui.detail

internal object CookingTimeParser {
    private val labelPattern = Regex(
        """\b(?:cook(?:ing)?\s*time|cooktime|cook)\s*:\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val valuePattern = Regex(
        """^(\d+(?:\s*-\s*\d+)?)(?:\s*(hours?|hrs?|minutes?|mins?|min))?(?:\s*,?\s*(?:and\s*)?(\d+)\s*(minutes?|mins?|min))?""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(rawText: String): String? = labelPattern.findAll(rawText).firstNotNullOfOrNull { label ->
        val value = valuePattern.find(rawText.substring(label.range.last + 1)) ?: return@firstNotNullOfOrNull null
        val primaryNumber = value.groupValues[1].replace(Regex("\\s*-\\s*"), "–")
        val primaryUnit = value.groupValues[2]
        val secondaryNumber = value.groupValues[3]
        val primary = if (primaryUnit.startsWith("h", ignoreCase = true)) {
            "$primaryNumber hr"
        } else {
            "$primaryNumber min"
        }
        if (secondaryNumber.isBlank()) primary else "$primary $secondaryNumber min"
    }
}
