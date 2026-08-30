package com.recipearchive.app.testutil

/** Small, hand-written bundle fragments used across import/search/DAO tests. */
object TestBundleFixtures {

    fun envelope(schemaVersion: Int = 1, recipesJson: String): String = """
        {
          "schemaVersion": $schemaVersion,
          "generatedAt": "2026-01-01T00:00:00Z",
          "source": {"database": "test", "canonicalRecipeCount": 1, "contract": "test fixture"},
          "recipes": [$recipesJson]
        }
    """.trimIndent()

    val chickenSoup = """
        {
          "id": "R0001",
          "title": "Chicken Soup",
          "rawText": "Ingredients:\n1 cup chicken\nInstructions:\n1. Boil water",
          "wordCount": 10,
          "ingredients": [
            {"rawText": "1 cup chicken", "quantity": "1", "unit": "cup", "item": "chicken", "parseStatus": "candidate"},
            {"rawText": "2 carrots", "quantity": "2", "unit": "", "item": "carrots", "parseStatus": "candidate"}
          ],
          "instructions": [
            {"order": 2, "text": "Simmer for an hour", "parseStatus": "candidate"},
            {"order": 1, "text": "Boil water", "parseStatus": "candidate"}
          ],
          "pageRefs": ["Batch_01__scan.pdf#p002", "Batch_01__scan.pdf#p001"],
          "arrangementStatus": "single_group",
          "duplicateStatus": "canonical",
          "reviewFlags": ["handwriting_review;duplicate_review"],
          "handwritingPageRefs": ["Batch_01__scan.pdf#p001"],
          "source": {
            "publisher": "Grandma's Notebook",
            "domain": "",
            "url": "",
            "status": "confirmed",
            "evidence": ["handwritten recipe card"]
          },
          "handwrittenNotes": [
            {
              "pageId": "Batch_01__scan.pdf#p001",
              "scan": "Batch_01__scan.pdf",
              "page": 1,
              "imagePath": "/nas/scan/page-001.png",
              "ocrDraft": "chikn soop, add lots of luv",
              "transcription": "Add lots of love",
              "status": "reviewed",
              "reasons": ["color_ink"]
            }
          ]
        }
    """.trimIndent()

    val applePie = """
        {
          "id": "R0002",
          "title": "Grandma's Apple Pie",
          "rawText": "A family favorite dessert.",
          "wordCount": 4,
          "ingredients": [],
          "instructions": [],
          "pageRefs": ["Batch_02__scan.pdf#p010"],
          "arrangementStatus": "confirmed_single",
          "duplicateStatus": "canonical",
          "reviewFlags": [],
          "handwritingPageRefs": [],
          "source": {
            "publisher": "Food Network",
            "domain": "foodnetwork.com",
            "url": "https://foodnetwork.com/apple-pie",
            "status": "printed_url",
            "evidence": []
          },
          "handwrittenNotes": []
        }
    """.trimIndent()

    val malformedMissingId = """
        {
          "title": "No id recipe",
          "rawText": "Should be skipped"
        }
    """.trimIndent()
}
