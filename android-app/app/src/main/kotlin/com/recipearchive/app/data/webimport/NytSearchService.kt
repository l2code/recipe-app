package com.recipearchive.app.data.webimport

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

data class NytSearchResult(
    val title: String,
    val url: String,
    val imageUrl: String?,
    val byline: String?,
)

sealed class NytSearchOutcome {
    data class Success(val results: List<NytSearchResult>) : NytSearchOutcome()
    data class NetworkError(val message: String) : NytSearchOutcome()
}

/**
 * Fetches NYT Cooking's public search results and homepage, and scrapes the
 * server-rendered recipe cards from each. Like [RecipeJsonLdParser], this only reads
 * what the page already sends an anonymous visitor -- no sign-in involved, and nothing
 * to do with the credentials saved in Settings.
 *
 * Neither page embeds JSON-LD the way individual recipe pages do, so this parses the
 * rendered card markup directly: every recipe card on both pages -- search results,
 * "Recipe of the Day", and the homepage's various carousels -- is built from the same
 * component, rendered as an `<a href="/recipes/...">` wrapping an image, an `<h3>`
 * title, and a `<p>` byline, in that order. Card class names are CSS-module hashes
 * that can change on any NYT deploy, so this deliberately selects by tag/position
 * within the anchor rather than by class name. The one exception is the homepage's
 * hero "Recipe of the Day" card, which uses a different layout with no `<h3>`; that
 * one is still captured via its `<img alt>` as a title fallback.
 */
class NytSearchService(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://cooking.nytimes.com",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(query: String): NytSearchOutcome = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext NytSearchOutcome.Success(emptyList())

        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmed)
            .build()

        fetchAndParse(url.toString())
    }

    /** Fetches the NYT Cooking homepage and returns the recipe cards it highlights right now. */
    suspend fun fetchFeatured(limit: Int = 15): NytSearchOutcome = withContext(ioDispatcher) {
        when (val outcome = fetchAndParse(baseUrl)) {
            is NytSearchOutcome.Success -> NytSearchOutcome.Success(outcome.results.take(limit))
            is NytSearchOutcome.NetworkError -> outcome
        }
    }

    private fun fetchAndParse(url: String): NytSearchOutcome {
        val html = try {
            fetch(url)
        } catch (e: IOException) {
            return NytSearchOutcome.NetworkError(e.message ?: "Network error")
        }
        return NytSearchOutcome.Success(parse(html))
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun parse(html: String): List<NytSearchResult> {
        val document = Jsoup.parse(html, baseUrl)
        val seenUrls = LinkedHashSet<String>()
        val results = mutableListOf<NytSearchResult>()
        for (anchor in document.select("a[href~=^/recipes/[0-9]]")) {
            val href = anchor.attr("abs:href")
            if (href.isBlank() || !seenUrls.add(href)) continue
            val title = anchor.selectFirst("h3")?.text()?.trim()
                ?: anchor.selectFirst("img")?.attr("alt")?.trim()
                ?: ""
            if (title.isBlank()) continue
            val imageUrl = anchor.selectFirst("img")?.attr("abs:src")?.takeIf { it.isNotBlank() }
            val byline = anchor.selectFirst("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
            results.add(NytSearchResult(title, href, imageUrl, byline))
        }
        return results
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (compatible; RecipeArchiveApp/1.0)"
    }
}
