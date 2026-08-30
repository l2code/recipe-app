package com.recipearchive.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookingTimeParserTest {
    @Test
    fun `extracts common cook time labels`() {
        assertEquals("10 min", CookingTimeParser.extract("Prep: 5 mins Cook: 10 mins Total Time: 15 mins"))
        assertEquals("45 min", CookingTimeParser.extract("Prep Time: 15 Cook Time: 45 mins"))
        assertEquals("8 min", CookingTimeParser.extract("PREP TIME: 20 mins COOKTIME: 8 minutes"))
    }

    @Test
    fun `normalizes ranges and hours`() {
        assertEquals("20–25 min", CookingTimeParser.extract("Cooking Time : 20-25 minutes"))
        assertEquals("1 hr 30 min", CookingTimeParser.extract("Cook Time: 1 hour, 30 minutes"))
    }

    @Test
    fun `assumes minutes for a bare cook time number`() {
        assertEquals("25 min", CookingTimeParser.extract("Cook Time: 25"))
    }

    @Test
    fun `ignores empty or narrative cook labels`() {
        assertNull(CookingTimeParser.extract("Prep Time: Cook Time: Total Time:"))
        assertNull(CookingTimeParser.extract("Today we cook: fat, flaky biscuits"))
    }
}
