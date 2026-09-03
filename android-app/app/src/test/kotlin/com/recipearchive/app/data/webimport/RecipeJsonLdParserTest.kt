package com.recipearchive.app.data.webimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeJsonLdParserTest {

    @Test
    fun `parses a direct Recipe type`() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "Recipe",
              "name": "Simple Pancakes",
              "recipeIngredient": ["2 cups flour", "1 cup milk", "1 egg"],
              "recipeInstructions": ["Mix dry ingredients.", "Whisk in milk and egg.", "Cook on a griddle."],
              "image": "https://example.com/pancakes.jpg",
              "recipeYield": "4 servings"
            }
            </script>
            </head><body></body></html>
        """.trimIndent()

        val parsed = RecipeJsonLdParser.parse(html)

        assertNotNull(parsed)
        assertEquals("Simple Pancakes", parsed!!.title)
        assertEquals(listOf("2 cups flour", "1 cup milk", "1 egg"), parsed.ingredients)
        assertEquals(
            listOf("Mix dry ingredients.", "Whisk in milk and egg.", "Cook on a griddle."),
            parsed.instructions,
        )
        assertEquals("https://example.com/pancakes.jpg", parsed.imageUrl)
        assertEquals("4 servings", parsed.recipeYield)
    }

    @Test
    fun `finds a Recipe nested under @graph`() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@graph": [
                {"@type": "WebPage", "name": "Some page"},
                {
                  "@type": "Recipe",
                  "name": "Graph Recipe",
                  "recipeIngredient": ["1 cup sugar"],
                  "recipeInstructions": "Combine everything.\nBake for 20 minutes."
                }
              ]
            }
            </script>
            </head><body></body></html>
        """.trimIndent()

        val parsed = RecipeJsonLdParser.parse(html)

        assertNotNull(parsed)
        assertEquals("Graph Recipe", parsed!!.title)
        assertEquals(listOf("1 cup sugar"), parsed.ingredients)
        assertEquals(listOf("Combine everything.", "Bake for 20 minutes."), parsed.instructions)
    }

    @Test
    fun `flattens HowToStep and HowToSection instructions`() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {
              "@type": "Recipe",
              "name": "Sectioned Recipe",
              "recipeIngredient": ["1 onion"],
              "recipeInstructions": [
                {
                  "@type": "HowToSection",
                  "name": "Prep",
                  "itemListElement": [
                    {"@type": "HowToStep", "text": "Chop the onion."},
                    {"@type": "HowToStep", "text": "Heat the pan."}
                  ]
                },
                {"@type": "HowToStep", "text": "Cook until soft."}
              ]
            }
            </script>
            </head><body></body></html>
        """.trimIndent()

        val parsed = RecipeJsonLdParser.parse(html)

        assertNotNull(parsed)
        assertEquals(
            listOf("Chop the onion.", "Heat the pan.", "Cook until soft."),
            parsed!!.instructions,
        )
    }

    @Test
    fun `returns null when the page has no recipe data`() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {"@context": "https://schema.org", "@type": "Organization", "name": "Acme Corp"}
            </script>
            </head><body><p>No recipe here.</p></body></html>
        """.trimIndent()

        assertNull(RecipeJsonLdParser.parse(html))
    }

    @Test
    fun `returns null when there is no ld+json at all`() {
        val html = "<html><head><title>Plain page</title></head><body>Hello</body></html>"

        assertNull(RecipeJsonLdParser.parse(html))
    }
}
