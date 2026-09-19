package com.recipearchive.app.data.webimport

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NytSearchServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: NytSearchService

    // Mirrors the real card markup: an <a href="/recipes/..."> wrapping an <img>, an
    // <h3> title, and a <p> byline, in that order -- see NytSearchService's kdoc. Real
    // cards also carry an accessible rating caption sentence like the one reproduced
    // here when a rating/review count is supplied.
    private fun cardHtml(id: String, title: String, byline: String, reviewCount: Int? = null, rating: Int? = null) = """
        <li><div class="recipecard_recipeCard"><article>
          <a href="/recipes/$id"><figure><img alt="$title" src="https://static01.nyt.com/images/$id.jpg"/></figure>
          <section><h3 class="atoms_cardTitle">$title</h3><p class="recipecard_byline">$byline</p>
          ${
        if (reviewCount != null && rating != null) {
            """<div class="recipecard_recipeCardRating"><span class="recipecard_ratingCaption">$reviewCount ratings with an average rating of $rating out of 5 stars</span></div>"""
        } else {
            ""
        }
    }
          </section></a>
        </article></div></li>
    """.trimIndent()

    // The homepage's "Recipe of the Day" hero has no <h3> -- title only via img alt.
    private val heroOnlyHtml = """
        <div><a href="/recipes/999-hero-recipe"><img alt="Hero Recipe" src="https://static01.nyt.com/images/999.jpg"/></a></div>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = NytSearchService(httpClient = OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search parses title, url, image, and byline from result cards`() = runTest {
        val html = "<html><body>" + cardHtml("101-roast-chicken", "Roast Chicken", "Mark Bittman") + "</body></html>"
        server.enqueue(MockResponse().setBody(html).setResponseCode(200))

        val outcome = service.search("chicken")

        assertTrue(outcome is NytSearchOutcome.Success)
        val results = (outcome as NytSearchOutcome.Success).results
        assertEquals(1, results.size)
        assertEquals("Roast Chicken", results[0].title)
        assertTrue(results[0].url.endsWith("/recipes/101-roast-chicken"))
        assertEquals("Mark Bittman", results[0].byline)
        assertTrue(results[0].imageUrl!!.contains("101-roast-chicken.jpg"))
    }

    @Test
    fun `search parses review count and star rating from the rating caption`() = runTest {
        val html = "<html><body>" +
            cardHtml("101-roast-chicken", "Roast Chicken", "Mark Bittman", reviewCount = 13880, rating = 5) +
            "</body></html>"
        server.enqueue(MockResponse().setBody(html).setResponseCode(200))

        val outcome = service.search("chicken") as NytSearchOutcome.Success

        assertEquals(1, outcome.results.size)
        assertEquals(5, outcome.results[0].rating)
        assertEquals(13880, outcome.results[0].reviewCount)
    }

    @Test
    fun `a card with no rating caption leaves rating and reviewCount null`() = runTest {
        val html = "<html><body>" + cardHtml("101-roast-chicken", "Roast Chicken", "Mark Bittman") + "</body></html>"
        server.enqueue(MockResponse().setBody(html).setResponseCode(200))

        val outcome = service.search("chicken") as NytSearchOutcome.Success

        assertEquals(null, outcome.results[0].rating)
        assertEquals(null, outcome.results[0].reviewCount)
    }

    @Test
    fun `search request includes the query parameter`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body></body></html>").setResponseCode(200))

        service.search("chicken tagine")

        val request = server.takeRequest()
        assertEquals("/search?q=chicken%20tagine", request.path)
    }

    @Test
    fun `duplicate hrefs on the page are deduped to one result`() = runTest {
        val duplicated = cardHtml("101-roast-chicken", "Roast Chicken", "Mark Bittman") +
            cardHtml("101-roast-chicken", "Roast Chicken", "Mark Bittman")
        server.enqueue(MockResponse().setBody("<html><body>$duplicated</body></html>").setResponseCode(200))

        val outcome = service.search("chicken") as NytSearchOutcome.Success

        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `falls back to the image alt text when there is no h3 title`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>$heroOnlyHtml</body></html>").setResponseCode(200))

        val outcome = service.fetchFeatured() as NytSearchOutcome.Success

        assertEquals(1, outcome.results.size)
        assertEquals("Hero Recipe", outcome.results[0].title)
    }

    @Test
    fun `fetchFeatured caps results at the requested limit`() = runTest {
        val many = (1..20).joinToString("") { cardHtml("$it-recipe-$it", "Recipe $it", "Author") }
        server.enqueue(MockResponse().setBody("<html><body>$many</body></html>").setResponseCode(200))

        val outcome = service.fetchFeatured(limit = 5) as NytSearchOutcome.Success

        assertEquals(5, outcome.results.size)
    }

    @Test
    fun `a server error surfaces as a NetworkError outcome`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val outcome = service.search("chicken")

        assertTrue(outcome is NytSearchOutcome.NetworkError)
    }

    @Test
    fun `blank query returns an empty result list without a network call`() = runTest {
        val outcome = service.search("   ")

        assertTrue(outcome is NytSearchOutcome.Success)
        assertEquals(0, (outcome as NytSearchOutcome.Success).results.size)
        assertEquals(0, server.requestCount)
    }
}
