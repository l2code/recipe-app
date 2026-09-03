package com.recipearchive.app.data.webimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedTextParserTest {

    @Test
    fun `splits on explicit Ingredients and Instructions headers`() {
        val text = """
            Weeknight Chili

            Ingredients:
            1 lb ground beef
            1 can kidney beans
            2 tbsp chili powder

            Instructions:
            Brown the beef.
            Add beans and chili powder.
            Simmer for 20 minutes.
        """.trimIndent()

        val parsed = PastedTextParser.parse(text)

        assertEquals("Weeknight Chili", parsed.title)
        assertEquals(listOf("1 lb ground beef", "1 can kidney beans", "2 tbsp chili powder"), parsed.ingredients)
        assertEquals(
            listOf("Brown the beef.", "Add beans and chili powder.", "Simmer for 20 minutes."),
            parsed.instructions,
        )
    }

    @Test
    fun `handles Directions as an alias for Instructions`() {
        val text = """
            Toast
            Ingredients
            1 slice bread
            Directions
            Toast it.
        """.trimIndent()

        val parsed = PastedTextParser.parse(text)

        assertEquals(listOf("1 slice bread"), parsed.ingredients)
        assertEquals(listOf("Toast it."), parsed.instructions)
    }

    @Test
    fun `falls back to quantity heuristic when there are no headers`() {
        val text = """
            Fried Rice
            2 cups cooked rice
            1 egg
            2 tbsp soy sauce
            Heat oil in a wok over high heat.
            Scramble the egg, then add rice.
            Stir in soy sauce and serve.
        """.trimIndent()

        val parsed = PastedTextParser.parse(text)

        assertEquals("Fried Rice", parsed.title)
        assertEquals(listOf("2 cups cooked rice", "1 egg", "2 tbsp soy sauce"), parsed.ingredients)
        assertEquals(
            listOf(
                "Heat oil in a wok over high heat.",
                "Scramble the egg, then add rice.",
                "Stir in soy sauce and serve.",
            ),
            parsed.instructions,
        )
    }

    @Test
    fun `blank input produces an empty parsed recipe`() {
        val parsed = PastedTextParser.parse("   \n  \n")

        assertTrue(parsed.isEmpty)
    }
}
