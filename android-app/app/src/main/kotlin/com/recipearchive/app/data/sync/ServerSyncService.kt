package com.recipearchive.app.data.sync

import com.recipearchive.app.data.local.RecipeDatabase
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class MirrorRecipeDto(
    val id: String,
    val title: String,
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val sourceDomain: String? = null,
    val sourcePublisher: String? = null,
    val personalRating: Int? = null,
    val isFavorite: Boolean = false,
    val category: String? = null,
    val nytRating: Int? = null,
    val nytReviewCount: Int? = null,
)

@Serializable
private data class MirrorPushRequestDto(val recipes: List<MirrorRecipeDto>)

@Serializable
private data class MirrorPushResponseDto(val mirroredCount: Int)

/** Shaped to match the pi5 server's ParsedRecipe-like GET /api/sync/pending response. */
@Serializable
data class PendingRecipeDto(
    val id: String,
    val title: String,
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val imageUrl: String? = null,
    val sourceUrl: String = "",
    val sourceDomain: String = "",
    val sourcePublisher: String = "",
)

@Serializable
private data class AckRequestDto(val ids: List<String>)

@Serializable
private data class AckResponseDto(val ackedCount: Int)

sealed interface PushMirrorOutcome {
    data class Success(val count: Int) : PushMirrorOutcome
    data class NotConfigured(val message: String) : PushMirrorOutcome
    data class Failure(val message: String) : PushMirrorOutcome
}

sealed interface PullPendingOutcome {
    data class Success(val recipes: List<PendingRecipeDto>) : PullPendingOutcome
    data class NotConfigured(val message: String) : PullPendingOutcome
    data class Failure(val message: String) : PullPendingOutcome
}

sealed interface AckOutcome {
    data class Success(val count: Int) : AckOutcome
    data class Failure(val message: String) : AckOutcome
}

private data class ServerCredentials(val serverUrl: String, val username: String, val password: String)

/**
 * Pushes the full local library to the home-server "mirror" (see the pi5 sync
 * server's POST /api/sync/mirror) and, in a later stage, pulls recipes added
 * from the web-add page back down via [com.recipearchive.app.data.webimport.WebRecipeImportService].
 * The server is always the one being reached out to -- it has no way to push
 * to the tablet, since the tablet isn't reliably reachable or always on.
 */
class ServerSyncService(
    private val database: RecipeDatabase,
    private val serverCredentialStore: ServerCredentialStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pushMirror(): PushMirrorOutcome = withContext(ioDispatcher) {
        val creds = credentials() ?: return@withContext PushMirrorOutcome.NotConfigured(NOT_CONFIGURED_MESSAGE)

        val recipes = buildMirrorPayload()
        val body = json.encodeToString(MirrorPushRequestDto.serializer(), MirrorPushRequestDto(recipes))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${creds.serverUrl}/api/sync/mirror")
            .header("Authorization", Credentials.basic(creds.username, creds.password))
            .post(body)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext PushMirrorOutcome.Failure(httpErrorMessage(response.code))
                }
                val responseBody = response.body?.string() ?: return@withContext PushMirrorOutcome.Failure("Empty response")
                val parsed = json.decodeFromString<MirrorPushResponseDto>(responseBody)
                PushMirrorOutcome.Success(parsed.mirroredCount)
            }
        } catch (e: IOException) {
            PushMirrorOutcome.Failure(e.message ?: "Network error")
        } catch (e: Exception) {
            PushMirrorOutcome.Failure(e.message ?: "Unexpected error")
        }
    }

    /** Recipes added from the web-add page that the app hasn't imported yet. */
    suspend fun pullPending(): PullPendingOutcome = withContext(ioDispatcher) {
        val creds = credentials() ?: return@withContext PullPendingOutcome.NotConfigured(NOT_CONFIGURED_MESSAGE)

        val request = Request.Builder()
            .url("${creds.serverUrl}/api/sync/pending")
            .header("Authorization", Credentials.basic(creds.username, creds.password))
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext PullPendingOutcome.Failure(httpErrorMessage(response.code))
                }
                val responseBody = response.body?.string() ?: return@withContext PullPendingOutcome.Failure("Empty response")
                val recipes = json.decodeFromString<List<PendingRecipeDto>>(responseBody)
                PullPendingOutcome.Success(recipes)
            }
        } catch (e: IOException) {
            PullPendingOutcome.Failure(e.message ?: "Network error")
        } catch (e: Exception) {
            PullPendingOutcome.Failure(e.message ?: "Unexpected error")
        }
    }

    /** Marks web-added recipes the app just imported as synced, by their server-side id. */
    suspend fun ackImported(ids: List<String>): AckOutcome = withContext(ioDispatcher) {
        val creds = credentials() ?: return@withContext AckOutcome.Failure(NOT_CONFIGURED_MESSAGE)
        if (ids.isEmpty()) return@withContext AckOutcome.Success(0)

        val body = json.encodeToString(AckRequestDto.serializer(), AckRequestDto(ids))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${creds.serverUrl}/api/sync/ack")
            .header("Authorization", Credentials.basic(creds.username, creds.password))
            .post(body)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AckOutcome.Failure(httpErrorMessage(response.code))
                }
                val responseBody = response.body?.string() ?: return@withContext AckOutcome.Failure("Empty response")
                val parsed = json.decodeFromString<AckResponseDto>(responseBody)
                AckOutcome.Success(parsed.ackedCount)
            }
        } catch (e: IOException) {
            AckOutcome.Failure(e.message ?: "Network error")
        } catch (e: Exception) {
            AckOutcome.Failure(e.message ?: "Unexpected error")
        }
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401 -> "Invalid username or password."
        else -> "Server returned HTTP $code"
    }

    private fun credentials(): ServerCredentials? {
        val serverUrl = serverCredentialStore.getServerUrl()
        val username = serverCredentialStore.getUsername()
        val password = serverCredentialStore.getPassword()
        if (serverUrl.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) return null
        return ServerCredentials(serverUrl, username, password)
    }

    private suspend fun buildMirrorPayload(): List<MirrorRecipeDto> {
        val recipeDao = database.recipeDao()
        val ingredientDao = database.ingredientDao()
        val instructionDao = database.instructionDao()
        val appStateDao = database.recipeAppStateDao()

        return recipeDao.getAll().map { recipe ->
            val appState = appStateDao.getForRecipe(recipe.id)
            MirrorRecipeDto(
                id = recipe.id,
                title = recipe.title,
                ingredients = ingredientDao.getForRecipe(recipe.id).map { it.rawText },
                instructions = instructionDao.getForRecipe(recipe.id).map { it.text },
                sourceUrl = recipe.sourceUrl.ifBlank { null },
                sourceDomain = recipe.sourceDomain.ifBlank { null },
                sourcePublisher = recipe.sourcePublisher.ifBlank { null },
                personalRating = appState?.personalRating,
                isFavorite = appState?.isFavorite ?: false,
                category = appState?.category,
                nytRating = appState?.nytRating,
                nytReviewCount = appState?.nytReviewCount,
            )
        }
    }

    private companion object {
        const val NOT_CONFIGURED_MESSAGE = "Connect to a home server in Settings first."
    }
}
