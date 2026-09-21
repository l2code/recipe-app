package com.recipearchive.app.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.settings.SettingsStore
import com.recipearchive.app.data.settings.ThemeMode
import com.recipearchive.app.data.sync.ServerCredentialStore
import com.recipearchive.app.data.sync.ServerSyncService
import com.recipearchive.app.data.webimport.CredentialStore
import com.recipearchive.app.data.webimport.WebRecipeImportService
import com.recipearchive.app.testutil.MainDispatcherRule
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var settingsStore: SettingsStore
    private lateinit var credentialStore: CredentialStore
    private lateinit var serverCredentialStore: ServerCredentialStore
    private lateinit var database: RecipeDatabase
    private lateinit var server: MockWebServer
    private lateinit var webRecipeImportService: WebRecipeImportService
    private lateinit var serverSyncService: ServerSyncService
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val credentialPrefs = context.getSharedPreferences("test_settings_vm_credentials", Context.MODE_PRIVATE)
        credentialPrefs.edit().clear().commit()
        val serverCredentialPrefs = context.getSharedPreferences("test_settings_vm_server_credentials", Context.MODE_PRIVATE)
        serverCredentialPrefs.edit().clear().commit()

        settingsStore = SettingsStore(context)
        credentialStore = CredentialStore(credentialPrefs)
        serverCredentialStore = ServerCredentialStore(serverCredentialPrefs)
        database = TestDatabaseFactory.create()
        server = MockWebServer()
        server.start()
        webRecipeImportService = WebRecipeImportService(database, httpClient = OkHttpClient(), ioDispatcher = testDispatcher)
        serverSyncService = ServerSyncService(database, serverCredentialStore, httpClient = OkHttpClient(), ioDispatcher = testDispatcher)
        viewModel = SettingsViewModel(
            settingsStore,
            credentialStore,
            webRecipeImportService,
            serverCredentialStore,
            serverSyncService,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    private suspend fun insertNytRecipe(id: String, sourceUrl: String) {
        database.recipeDao().insert(
            RecipeEntity(
                id = id,
                title = "Test Recipe",
                rawText = "Test Recipe",
                wordCount = 1,
                arrangementStatus = "web_import",
                duplicateStatus = "",
                sourcePublisher = "NYT Cooking",
                sourceDomain = "cooking.nytimes.com",
                sourceUrl = sourceUrl,
                sourceStatus = "confirmed",
                importSchemaVersion = 0,
                importGeneratedAt = "",
                createdAt = 0,
                lastImportedAt = 0,
            ),
        )
        database.recipeAppStateDao().insertDefaultIfMissing(RecipeAppStateEntity(recipeId = id))
    }

    @Test
    fun `changing theme mode updates the store`() {
        viewModel.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        assertEquals(ThemeMode.LIGHT, settingsStore.themeMode.value)
    }

    @Test
    fun `toggling nav labels updates the store`() {
        viewModel.setShowNavLabels(false)

        assertEquals(false, viewModel.showNavLabels.value)
        assertEquals(false, settingsStore.showNavLabels.value)
    }

    @Test
    fun `saving nyt credentials persists them and flips the saved flag`() {
        viewModel.onNytEmailChanged("cook@example.com")
        viewModel.onNytPasswordChanged("hunter2")
        viewModel.saveNytCredentials()

        assertTrue(viewModel.nytAccountState.value.isSaved)
        assertTrue(credentialStore.hasCredentials())
        assertEquals("cook@example.com", credentialStore.getEmail())
    }

    @Test
    fun `saving with a blank field surfaces a status message and does not save`() {
        viewModel.onNytEmailChanged("cook@example.com")
        viewModel.saveNytCredentials()

        assertEquals(false, viewModel.nytAccountState.value.isSaved)
        assertEquals(false, credentialStore.hasCredentials())
        assertEquals("Enter both an email and a password to save.", viewModel.nytAccountState.value.statusMessage)
    }

    @Test
    fun `test login never contacts NYT, only validates field shape`() {
        viewModel.onNytEmailChanged("not-an-email")
        viewModel.onNytPasswordChanged("hunter2")
        viewModel.testNytLogin()
        assertEquals("Enter a valid email and password first.", viewModel.nytAccountState.value.statusMessage)

        viewModel.onNytEmailChanged("cook@example.com")
        viewModel.testNytLogin()
        assertTrue(viewModel.nytAccountState.value.statusMessage!!.contains("don't sign in"))
    }

    @Test
    fun `removing nyt credentials clears the saved state`() {
        credentialStore.saveCredentials("cook@example.com", "hunter2")
        val reloaded = SettingsViewModel(settingsStore, credentialStore, webRecipeImportService, serverCredentialStore, serverSyncService)
        assertTrue(reloaded.nytAccountState.value.isSaved)

        reloaded.removeNytCredentials()

        assertEquals(false, reloaded.nytAccountState.value.isSaved)
        assertEquals(false, credentialStore.hasCredentials())
    }

    @Test
    fun `syncing with no NYT recipes in the library surfaces a friendly message`() = runTest(testDispatcher) {
        viewModel.syncNytRatings()
        advanceUntilIdle()

        assertEquals(false, viewModel.nytRatingSyncState.value.isSyncing)
        assertEquals("No NYT Cooking recipes in your library yet.", viewModel.nytRatingSyncState.value.resultMessage)
    }

    @Test
    fun `syncing updates ratings for NYT recipes and reports the result`() = runTest(testDispatcher) {
        val ratedHtml = """
            <html><head>
            <script type="application/ld+json">
            {
              "@type": "Recipe",
              "name": "Test Recipe",
              "recipeIngredient": ["1 onion"],
              "recipeInstructions": ["Cook it."],
              "aggregateRating": {"@type": "AggregateRating", "ratingValue": 5, "ratingCount": 999}
            }
            </script>
            </head><body></body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(ratedHtml).setResponseCode(200))
        val url = server.url("/recipe").toString()
        insertNytRecipe("r1", url)

        viewModel.syncNytRatings()
        advanceUntilIdle()

        val state = viewModel.nytRatingSyncState.value
        assertEquals(false, state.isSyncing)
        assertEquals("Updated 1 of 1 recipes.", state.resultMessage)
        assertEquals(5, database.recipeAppStateDao().getForRecipe("r1")?.nytRating)
    }

    @Test
    fun `saving server credentials persists them, trims the URL, and flips the saved flag`() {
        viewModel.onServerUrlChanged("https://pi5.example.ts.net:8444/")
        viewModel.onServerUsernameChanged("rissac")
        viewModel.onServerPasswordChanged("hunter2")

        viewModel.saveServerCredentials()

        val state = viewModel.serverCredentialsState.value
        assertTrue(state.isSaved)
        assertEquals("https://pi5.example.ts.net:8444", state.serverUrl)
        assertTrue(serverCredentialStore.hasCredentials())
        assertEquals("rissac", serverCredentialStore.getUsername())
    }

    @Test
    fun `saving server credentials with a blank field surfaces a status message and does not save`() {
        viewModel.onServerUrlChanged("https://pi5.example.ts.net:8444")
        viewModel.onServerUsernameChanged("rissac")

        viewModel.saveServerCredentials()

        assertEquals(false, viewModel.serverCredentialsState.value.isSaved)
        assertEquals(false, serverCredentialStore.hasCredentials())
        assertEquals(
            "Enter a server URL, username, and password to save.",
            viewModel.serverCredentialsState.value.statusMessage,
        )
    }

    @Test
    fun `removing server credentials clears the saved state`() {
        serverCredentialStore.saveCredentials("https://pi5.example.ts.net:8444", "rissac", "hunter2")
        val reloaded = SettingsViewModel(settingsStore, credentialStore, webRecipeImportService, serverCredentialStore, serverSyncService)
        assertTrue(reloaded.serverCredentialsState.value.isSaved)

        reloaded.removeServerCredentials()

        assertEquals(false, reloaded.serverCredentialsState.value.isSaved)
        assertEquals(false, serverCredentialStore.hasCredentials())
    }

    @Test
    fun `syncing with the server without a saved connection surfaces a friendly message`() = runTest(testDispatcher) {
        viewModel.syncWithServer()
        advanceUntilIdle()

        assertEquals(false, viewModel.serverSyncState.value.isSyncing)
        assertEquals("Connect to a home server below first.", viewModel.serverSyncState.value.resultMessage)
    }

    @Test
    fun `syncing with the server pushes the library and reports the result`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody("""{"mirroredCount": 1}""").setResponseCode(200))
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        val serverUrl = server.url("/").toString().trimEnd('/')
        serverCredentialStore.saveCredentials(serverUrl, "rissac", "hunter2")
        insertNytRecipe("r1", "https://example.com/recipe")

        viewModel.syncWithServer()
        advanceUntilIdle()

        val state = viewModel.serverSyncState.value
        assertEquals(false, state.isSyncing)
        assertEquals("Pushed 1 recipes.", state.resultMessage)

        val mirrorRequest = server.takeRequest()
        assertEquals("/api/sync/mirror", mirrorRequest.path)
        assertTrue(mirrorRequest.getHeader("Authorization")!!.startsWith("Basic "))
        assertEquals("/api/sync/pending", server.takeRequest().path)
    }

    @Test
    fun `syncing with the server imports pending web recipes and acks them`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody("""{"mirroredCount": 0}""").setResponseCode(200))
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"w1","title":"Web Soup","ingredients":["1 onion"],"instructions":["Simmer."],"sourceUrl":"https://example.com/soup","sourceDomain":"example.com","sourcePublisher":"example.com"}]""",
            ).setResponseCode(200),
        )
        server.enqueue(MockResponse().setBody("""{"ackedCount": 1}""").setResponseCode(200))
        serverCredentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")

        viewModel.syncWithServer()
        advanceUntilIdle()

        val state = viewModel.serverSyncState.value
        assertEquals(false, state.isSyncing)
        assertEquals("Pushed 0 recipes. Imported 1 new recipe.", state.resultMessage)

        val saved = database.recipeDao().getBySourceUrl("https://example.com/soup")
        assertEquals("Web Soup", saved?.title)

        server.takeRequest()
        server.takeRequest()
        val ackRequest = server.takeRequest()
        assertEquals("/api/sync/ack", ackRequest.path)
        assertTrue(ackRequest.body.readUtf8().contains("w1"))
    }

    @Test
    fun `retrying a sync after a lost ack does not duplicate the imported recipe`() = runTest(testDispatcher) {
        serverCredentialStore.saveCredentials(server.url("/").toString().trimEnd('/'), "rissac", "hunter2")
        val pendingBody = """[{"id":"w2","title":"Manual Chili","ingredients":["beans"],"instructions":["Simmer."],"sourceUrl":"pi5://w2","sourceDomain":"pi5","sourcePublisher":"Added manually"}]"""

        // First sync: the server still reports "w2" as pending on the next pull, simulating an
        // ack that never made it (network blip after the app already imported it locally).
        server.enqueue(MockResponse().setBody("""{"mirroredCount": 0}""").setResponseCode(200))
        server.enqueue(MockResponse().setBody(pendingBody).setResponseCode(200))
        server.enqueue(MockResponse().setBody("""{"ackedCount": 1}""").setResponseCode(200))
        viewModel.syncWithServer()
        advanceUntilIdle()

        server.enqueue(MockResponse().setBody("""{"mirroredCount": 1}""").setResponseCode(200))
        server.enqueue(MockResponse().setBody(pendingBody).setResponseCode(200))
        server.enqueue(MockResponse().setBody("""{"ackedCount": 1}""").setResponseCode(200))
        viewModel.syncWithServer()
        advanceUntilIdle()

        assertEquals(1, database.recipeDao().count())
        val saved = database.recipeDao().getBySourceUrl("pi5://w2")
        assertEquals("Manual Chili", saved?.title)
    }
}
