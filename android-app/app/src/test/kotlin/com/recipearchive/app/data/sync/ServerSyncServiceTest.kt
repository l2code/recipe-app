package com.recipearchive.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerSyncServiceTest {

    private lateinit var database: RecipeDatabase
    private lateinit var server: MockWebServer
    private lateinit var credentialStore: ServerCredentialStore
    private lateinit var service: ServerSyncService

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        server = MockWebServer()
        server.start()
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_server_sync_service_credentials", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        credentialStore = ServerCredentialStore(prefs)
        service = ServerSyncService(database, credentialStore, httpClient = OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    private suspend fun insertRecipe(id: String, title: String, personalRating: Int? = null, isFavorite: Boolean = false) {
        database.recipeDao().insert(
            RecipeEntity(
                id = id,
                title = title,
                rawText = title,
                wordCount = 1,
                arrangementStatus = "web_import",
                duplicateStatus = "",
                sourcePublisher = "Example",
                sourceDomain = "example.com",
                sourceUrl = "https://example.com/$id",
                sourceStatus = "confirmed",
                importSchemaVersion = 0,
                importGeneratedAt = "",
                createdAt = 0,
                lastImportedAt = 0,
            ),
        )
        database.ingredientDao().insertAll(
            listOf(IngredientEntity(recipeId = id, displayOrder = 0, rawText = "1 egg", quantity = "", unit = "", item = "egg", parseStatus = "confirmed")),
        )
        database.instructionDao().insertAll(
            listOf(InstructionEntity(recipeId = id, displayOrder = 1, text = "Cook it.", parseStatus = "confirmed")),
        )
        database.recipeAppStateDao().insertDefaultIfMissing(
            RecipeAppStateEntity(recipeId = id, personalRating = personalRating, isFavorite = isFavorite),
        )
    }

    @Test
    fun `pushMirror reports NotConfigured when no server credentials are saved`() = runTest {
        val outcome = service.pushMirror()

        assertTrue(outcome is PushMirrorOutcome.NotConfigured)
    }

    @Test
    fun `pushMirror sends the full library with app state and auth header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"mirroredCount": 1}""").setResponseCode(200))
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")
        insertRecipe("r1", "Tacos", personalRating = 5, isFavorite = true)

        val outcome = service.pushMirror()

        assertTrue(outcome is PushMirrorOutcome.Success)
        assertEquals(1, (outcome as PushMirrorOutcome.Success).count)

        val request = server.takeRequest()
        assertEquals("/api/sync/mirror", request.path)
        assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"title\":\"Tacos\""))
        assertTrue(body.contains("\"1 egg\""))
        assertTrue(body.contains("\"Cook it.\""))
        assertTrue(body.contains("\"personalRating\":5"))
        assertTrue(body.contains("\"isFavorite\":true"))
    }

    @Test
    fun `pushMirror reports a friendly message on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "wrong")

        val outcome = service.pushMirror()

        assertTrue(outcome is PushMirrorOutcome.Failure)
        assertEquals("Invalid username or password.", (outcome as PushMirrorOutcome.Failure).message)
    }

    @Test
    fun `pushMirror reports Failure on another non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        val outcome = service.pushMirror()

        assertTrue(outcome is PushMirrorOutcome.Failure)
        assertTrue((outcome as PushMirrorOutcome.Failure).message.contains("500"))
    }

    @Test
    fun `pushMirror reports Failure when the server is unreachable`() = runTest {
        credentialStore.saveCredentials("http://127.0.0.1:1", "rissac", "hunter2")

        val outcome = service.pushMirror()

        assertTrue(outcome is PushMirrorOutcome.Failure)
    }

    @Test
    fun `pushMirror reports Failure without crashing on a malformed response body`() = runTest {
        server.enqueue(MockResponse().setBody("not json").setResponseCode(200))
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        val outcome = service.pushMirror()

        assertTrue(outcome is PushMirrorOutcome.Failure)
    }

    @Test
    fun `pullPending reports NotConfigured when no server credentials are saved`() = runTest {
        val outcome = service.pullPending()

        assertTrue(outcome is PullPendingOutcome.NotConfigured)
    }

    @Test
    fun `pullPending parses the server's pending list including the pi5 synthetic url`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"w1","title":"Manual Chili","ingredients":["beans"],"instructions":["Simmer."],"sourceUrl":"pi5://w1","sourceDomain":"pi5","sourcePublisher":"Added manually"}]""",
            ).setResponseCode(200),
        )
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        val outcome = service.pullPending()

        assertTrue(outcome is PullPendingOutcome.Success)
        val recipes = (outcome as PullPendingOutcome.Success).recipes
        assertEquals(1, recipes.size)
        assertEquals("pi5://w1", recipes.first().sourceUrl)

        val request = server.takeRequest()
        assertEquals("/api/sync/pending", request.path)
        assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun `pullPending reports Failure on a non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        val outcome = service.pullPending()

        assertTrue(outcome is PullPendingOutcome.Failure)
    }

    @Test
    fun `ackImported sends the given ids and returns the acked count`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ackedCount": 2}""").setResponseCode(200))
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        val outcome = service.ackImported(listOf("w1", "w2"))

        assertTrue(outcome is AckOutcome.Success)
        assertEquals(2, (outcome as AckOutcome.Success).count)

        val request = server.takeRequest()
        assertEquals("/api/sync/ack", request.path)
        assertTrue(request.body.readUtf8().contains("w1"))
    }

    @Test
    fun `ackImported with no ids does not make a network call`() = runTest {
        credentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        val outcome = service.ackImported(emptyList())

        assertTrue(outcome is AckOutcome.Success)
        assertEquals(0, server.requestCount)
    }
}
